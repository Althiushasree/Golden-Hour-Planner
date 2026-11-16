package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.example.R
import com.example.auth.AuthManager
import com.example.databinding.ActivityDashboardBinding
import com.example.model.CountdownInfo
import com.example.model.SolarEventType
import com.example.model.SolarTimings
import com.example.utils.SolarTimeUtils
import com.example.utils.UiState
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.LocationUiState
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val authManager = AuthManager()

    private var hasAttemptedPermissionRequest = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            dashboardViewModel.refreshLocationAndSolarData()
        } else {
            dashboardViewModel.onPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (authManager.currentUser() == null) {
            redirectToLogin()
            return
        }

        enableEdgeToEdge()
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.dashboardRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindUserHeader()
        setupClickListeners()
        observeViewModel()
        startCountdownTicker()

        if (savedInstanceState == null) {
            if (dashboardViewModel.hasLocationPermission()) {
                dashboardViewModel.refreshLocationAndSolarData()
            } else {
                requestLocationPermissions()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (authManager.currentUser() == null) {
            redirectToLogin()
        }
    }

    override fun onResume() {
        super.onResume()
        val currentLocState = dashboardViewModel.locationState.value
        val shouldAutoRetryFromSettings =
            (currentLocState is LocationUiState.ServicesDisabled &&
                dashboardViewModel.hasLocationPermission() &&
                dashboardViewModel.isLocationEnabled()) ||
                (currentLocState is LocationUiState.PermissionDenied &&
                    dashboardViewModel.hasLocationPermission())

        if (shouldAutoRetryFromSettings) {
            dashboardViewModel.refreshLocationAndSolarData()
        }
    }

    private fun bindUserHeader() {
        val profile = authManager.currentUserProfile() ?: return
        binding.tvUserName.text = getString(R.string.dashboard_greeting_format, profile.displayName)
        if (!profile.email.isNullOrBlank()) {
            binding.tvUserEmail.text = profile.email
            binding.tvUserEmail.visibility = View.VISIBLE
        } else {
            binding.tvUserEmail.visibility = View.GONE
        }

        binding.ivUserAvatar.load(profile.photoUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_avatar_placeholder)
            error(R.drawable.ic_avatar_placeholder)
        }
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout_dialog_title)
                .setMessage(R.string.logout_dialog_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_sign_out) { _, _ ->
                    authViewModel.signOut(this) {
                        redirectToLogin()
                    }
                }
                .show()
        }

        binding.btnRetryLocation.setOnClickListener {
            if (dashboardViewModel.hasLocationPermission()) {
                dashboardViewModel.refreshLocationAndSolarData()
            } else {
                requestLocationPermissions()
            }
        }

        binding.btnSelectDate.setOnClickListener {
            openDatePicker()
        }

        binding.btnPhotoShootPlans.setOnClickListener {
            val intent = Intent(this, PlansActivity::class.java).apply {
                putExtra(
                    AddEditPlanActivity.EXTRA_DEFAULT_DATE,
                    SolarTimeUtils.formatApiDate(dashboardViewModel.selectedDate.value)
                )
            }
            startActivity(intent)
        }

        binding.btnRetrySolar.setOnClickListener {
            dashboardViewModel.retrySolarFetch()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dashboardViewModel.locationState.collect { state ->
                        renderLocationState(state)
                    }
                }
                launch {
                    dashboardViewModel.selectedDate.collect { date ->
                        binding.tvSelectedDate.text = SolarTimeUtils.formatDisplayDate(date)
                        binding.tvTodayBadge.visibility =
                            if (date == LocalDate.now()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    dashboardViewModel.solarState.collect { state ->
                        renderSolarState(state)
                    }
                }
                launch {
                    dashboardViewModel.countdownState.collect { countdown ->
                        renderCountdown(countdown)
                    }
                }
            }
        }
    }

    private fun startCountdownTicker() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    dashboardViewModel.recalculateCountdown()
                    delay(60_000L)
                }
            }
        }
    }

    private fun renderLocationState(state: LocationUiState) {
        when (state) {
            is LocationUiState.Loading -> {
                binding.progressLocation.visibility = View.VISIBLE
                binding.tvLocationTitle.setText(R.string.location_detecting)
                binding.tvCoordinates.visibility = View.GONE
                binding.layoutLocationError.visibility = View.GONE
            }
            is LocationUiState.Success -> {
                binding.progressLocation.visibility = View.GONE
                val coords = state.coordinates
                binding.tvLocationTitle.text = coords.localityName
                    ?: getString(R.string.label_current_location)
                binding.tvCoordinates.text = getString(
                    R.string.location_coords_format,
                    coords.latitude,
                    coords.longitude
                )
                binding.tvCoordinates.visibility = View.VISIBLE
                binding.layoutLocationError.visibility = View.GONE
            }
            is LocationUiState.PermissionDenied -> {
                binding.progressLocation.visibility = View.GONE
                showPermissionDeniedState()
            }
            is LocationUiState.ServicesDisabled -> {
                binding.progressLocation.visibility = View.GONE
                binding.tvLocationTitle.setText(R.string.label_current_location)
                binding.tvCoordinates.visibility = View.GONE
                binding.layoutLocationError.visibility = View.VISIBLE
                binding.tvLocationError.setText(R.string.location_services_disabled)
                binding.btnLocationAction.setText(R.string.btn_open_settings)
                binding.btnLocationAction.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            is LocationUiState.Unavailable -> {
                binding.progressLocation.visibility = View.GONE
                binding.tvLocationTitle.setText(R.string.label_current_location)
                binding.tvCoordinates.visibility = View.GONE
                binding.layoutLocationError.visibility = View.VISIBLE
                binding.tvLocationError.text = state.message
                binding.btnLocationAction.setText(R.string.btn_retry_location)
                binding.btnLocationAction.setOnClickListener {
                    dashboardViewModel.refreshLocationAndSolarData()
                }
            }
        }
    }

    private fun showPermissionDeniedState() {
        binding.tvLocationTitle.setText(R.string.label_current_location)
        binding.tvCoordinates.visibility = View.GONE
        binding.layoutLocationError.visibility = View.VISIBLE
        binding.tvLocationError.setText(R.string.location_permission_denied)

        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (!hasAttemptedPermissionRequest || shouldShowRationale) {
            binding.btnLocationAction.setText(R.string.btn_grant_permission)
            binding.btnLocationAction.setOnClickListener {
                requestLocationPermissions()
            }
        } else {
            binding.btnLocationAction.setText(R.string.btn_open_settings)
            binding.btnLocationAction.setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
    }

    private fun requestLocationPermissions() {
        hasAttemptedPermissionRequest = true
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun renderSolarState(state: UiState<SolarTimings>) {
        when (state) {
            is UiState.Idle -> {
                binding.layoutSolarLoading.visibility = View.GONE
                binding.cardSolarError.visibility = View.GONE
                binding.layoutSolarContent.visibility = View.GONE
            }
            is UiState.Loading -> {
                binding.layoutSolarLoading.visibility = View.VISIBLE
                binding.cardSolarError.visibility = View.GONE
                binding.layoutSolarContent.visibility = View.GONE
            }
            is UiState.Error -> {
                binding.layoutSolarLoading.visibility = View.GONE
                binding.cardSolarError.visibility = View.VISIBLE
                binding.layoutSolarContent.visibility = View.GONE
                binding.tvSolarError.text = state.message
            }
            is UiState.Success -> {
                binding.layoutSolarLoading.visibility = View.GONE
                binding.cardSolarError.visibility = View.GONE
                binding.layoutSolarContent.visibility = View.VISIBLE
                bindSolarTimings(state.data)
            }
        }
    }

    private fun bindSolarTimings(timings: SolarTimings) {
        val unavailable = getString(R.string.value_unavailable)
        val zone = timings.zoneId

        binding.tvSunrise.text =
            SolarTimeUtils.formatLocalTime(timings.sunrise, zone) ?: unavailable
        binding.tvSunset.text =
            SolarTimeUtils.formatLocalTime(timings.sunset, zone) ?: unavailable
        binding.tvSolarNoon.text =
            SolarTimeUtils.formatLocalTime(timings.solarNoon, zone) ?: unavailable
        binding.tvDayLength.text =
            SolarTimeUtils.formatDayLength(timings.dayLengthSeconds) ?: unavailable
        binding.tvCivilTwilightStart.text =
            SolarTimeUtils.formatLocalTime(timings.civilTwilightStart, zone) ?: unavailable
        binding.tvCivilTwilightEnd.text =
            SolarTimeUtils.formatLocalTime(timings.civilTwilightEnd, zone) ?: unavailable

        binding.tvMorningGoldenHour.text = SolarTimeUtils.formatLocalTimeRange(
            timings.morningGoldenHourStart,
            timings.morningGoldenHourEnd,
            zone
        ) ?: unavailable

        binding.tvEveningGoldenHour.text = SolarTimeUtils.formatLocalTimeRange(
            timings.eveningGoldenHourStart,
            timings.eveningGoldenHourEnd,
            zone
        ) ?: unavailable

        val updatedTime = SolarTimeUtils.formatLastUpdated(timings.lastUpdatedMillis, zone)
        binding.tvLastUpdated.text = getString(
            R.string.last_updated_format,
            updatedTime,
            zone.id
        )
    }

    private fun renderCountdown(countdown: CountdownInfo?) {
        if (countdown == null) {
            binding.cardCountdown.visibility = View.GONE
            return
        }
        binding.cardCountdown.visibility = View.VISIBLE

        when (countdown.eventType) {
            SolarEventType.NEXT_SUNRISE -> {
                binding.ivCountdownIcon.setImageResource(R.drawable.ic_sunrise)
                binding.tvCountdownTitle.setText(R.string.countdown_header_next_sunrise)
                binding.tvCountdownTimer.text = getString(
                    R.string.countdown_remaining_format,
                    countdown.hours,
                    countdown.minutes,
                    countdown.seconds
                )
                val targetTime = SolarTimeUtils.formatLocalTime(countdown.targetInstant)
                if (targetTime != null) {
                    binding.tvCountdownTargetTime.text = getString(R.string.countdown_at_format, targetTime)
                    binding.tvCountdownTargetTime.visibility = View.VISIBLE
                } else {
                    binding.tvCountdownTargetTime.visibility = View.GONE
                }
            }
            SolarEventType.NEXT_SUNSET -> {
                binding.ivCountdownIcon.setImageResource(R.drawable.ic_sunset)
                binding.tvCountdownTitle.setText(R.string.countdown_header_next_sunset)
                binding.tvCountdownTimer.text = getString(
                    R.string.countdown_remaining_format,
                    countdown.hours,
                    countdown.minutes,
                    countdown.seconds
                )
                val targetTime = SolarTimeUtils.formatLocalTime(countdown.targetInstant)
                if (targetTime != null) {
                    binding.tvCountdownTargetTime.text = getString(R.string.countdown_at_format, targetTime)
                    binding.tvCountdownTargetTime.visibility = View.VISIBLE
                } else {
                    binding.tvCountdownTargetTime.visibility = View.GONE
                }
            }
            SolarEventType.UNAVAILABLE -> {
                binding.ivCountdownIcon.setImageResource(R.drawable.ic_solar_noon)
                binding.tvCountdownTitle.setText(R.string.countdown_header_unavailable)
                binding.tvCountdownTimer.setText(R.string.value_unavailable)
                binding.tvCountdownTargetTime.visibility = View.GONE
            }
        }

        when {
            countdown.eventType == SolarEventType.UNAVAILABLE -> {
                binding.tvCountdownNote.setText(R.string.countdown_unavailable_note)
                binding.tvCountdownNote.visibility = View.VISIBLE
            }
            countdown.isSelectedDatePast -> {
                binding.tvCountdownNote.setText(R.string.countdown_past_date_note)
                binding.tvCountdownNote.visibility = View.VISIBLE
            }
            countdown.isSelectedDateFuture -> {
                binding.tvCountdownNote.setText(R.string.countdown_future_date_note)
                binding.tvCountdownNote.visibility = View.VISIBLE
            }
            else -> {
                binding.tvCountdownNote.visibility = View.GONE
            }
        }
    }

    private fun openDatePicker() {
        val currentSelectionMillis = dashboardViewModel.selectedDate.value
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.date_picker_title))
            .setSelection(currentSelectionMillis)
            .build()

        picker.addOnPositiveButtonClickListener { selectionMillis ->
            val pickedDate = Instant.ofEpochMilli(selectionMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            dashboardViewModel.selectDate(pickedDate)
        }
        picker.show(supportFragmentManager, "solar_date_picker")
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
