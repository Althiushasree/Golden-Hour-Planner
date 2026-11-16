package com.example.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.auth.AuthManager
import com.example.databinding.ActivityAddEditPlanBinding
import com.example.utils.SolarTimeUtils
import com.example.viewmodel.AddEditPlanUiState
import com.example.viewmodel.AddEditPlanViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AddEditPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditPlanBinding
    private val viewModel: AddEditPlanViewModel by viewModels()
    private val authManager = AuthManager()

    private var planId: Long = 0L
    private var hasPopulatedEditForm = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (authManager.currentUser() == null) {
            redirectToLogin()
            return
        }

        enableEdgeToEdge()
        binding = ActivityAddEditPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.addEditPlanRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        hasPopulatedEditForm = savedInstanceState?.getBoolean(KEY_FORM_POPULATED, false) == true
        planId = intent.getLongExtra(EXTRA_PLAN_ID, 0L)
        val defaultDate = intent.getStringExtra(EXTRA_DEFAULT_DATE)
            ?: SolarTimeUtils.formatApiDate(LocalDate.now())

        if (planId > 0L) {
            binding.toolbarAddEditPlan.setTitle(R.string.title_edit_plan)
            if (!hasPopulatedEditForm) {
                viewModel.loadPlan(planId)
            }
        } else {
            binding.toolbarAddEditPlan.setTitle(R.string.title_add_plan)
            if (savedInstanceState == null) {
                binding.etPlanDate.setText(defaultDate)
            }
        }

        binding.toolbarAddEditPlan.setNavigationOnClickListener {
            finish()
        }

        binding.etPlanDate.setOnClickListener {
            openPlanDatePicker()
        }

        binding.tilPlanDate.setStartIconOnClickListener {
            openPlanDatePicker()
        }

        binding.etPlanPlace.doAfterTextChanged {
            if (!it.isNullOrBlank()) {
                binding.tilPlanPlace.error = null
            }
        }

        binding.etPlanNote.doAfterTextChanged {
            if (!it.isNullOrBlank()) {
                binding.tilPlanNote.error = null
            }
        }

        binding.btnCancelPlan.setOnClickListener {
            finish()
        }

        binding.btnSavePlan.setOnClickListener {
            validateAndSave()
        }

        observeUiState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FORM_POPULATED, hasPopulatedEditForm)
    }

    private fun openPlanDatePicker() {
        val currentText = binding.etPlanDate.text?.toString()?.trim().orEmpty()
        val initialDate = try {
            if (currentText.isNotEmpty()) LocalDate.parse(currentText) else LocalDate.now()
        } catch (_: Exception) {
            LocalDate.now()
        }

        val initialMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.date_picker_title))
            .setSelection(initialMillis)
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            val pickedDate = Instant.ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            binding.etPlanDate.setText(SolarTimeUtils.formatApiDate(pickedDate))
            binding.tilPlanDate.error = null
        }
        picker.show(supportFragmentManager, "plan_date_picker")
    }

    private fun validateAndSave() {
        val date = binding.etPlanDate.text?.toString()?.trim().orEmpty()
        val place = binding.etPlanPlace.text?.toString()?.trim().orEmpty()
        val note = binding.etPlanNote.text?.toString()?.trim().orEmpty()

        binding.tilPlanDate.error = null
        binding.tilPlanPlace.error = null
        binding.tilPlanNote.error = null

        var hasError = false
        if (date.isEmpty()) {
            binding.tilPlanDate.error = getString(R.string.error_empty_date)
            hasError = true
        }
        if (place.isEmpty()) {
            binding.tilPlanPlace.error = getString(R.string.error_empty_place)
            hasError = true
        }
        if (note.isEmpty()) {
            binding.tilPlanNote.error = getString(R.string.error_empty_note)
            hasError = true
        }
        if (hasError) return

        viewModel.savePlan(planId, date, place, note)
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AddEditPlanUiState.Idle -> {
                            binding.progressSave.visibility = View.GONE
                            binding.btnSavePlan.isEnabled = true
                            binding.tvSaveError.visibility = View.GONE
                        }
                        is AddEditPlanUiState.Loading -> {
                            binding.progressSave.visibility = View.VISIBLE
                            binding.btnSavePlan.isEnabled = false
                            binding.tvSaveError.visibility = View.GONE
                        }
                        is AddEditPlanUiState.Loaded -> {
                            binding.progressSave.visibility = View.GONE
                            binding.btnSavePlan.isEnabled = true
                            if (!hasPopulatedEditForm) {
                                hasPopulatedEditForm = true
                                binding.etPlanDate.setText(state.plan.date)
                                binding.etPlanPlace.setText(state.plan.placeName)
                                binding.etPlanNote.setText(state.plan.note)
                            }
                        }
                        is AddEditPlanUiState.Saved -> {
                            binding.progressSave.visibility = View.GONE
                            Toast.makeText(
                                this@AddEditPlanActivity,
                                R.string.plan_saved_message,
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                        is AddEditPlanUiState.Error -> {
                            binding.progressSave.visibility = View.GONE
                            binding.btnSavePlan.isEnabled = true
                            binding.tvSaveError.visibility = View.VISIBLE
                            binding.tvSaveError.text = state.message
                        }
                    }
                }
            }
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_DEFAULT_DATE = "extra_default_date"
        private const val KEY_FORM_POPULATED = "key_form_populated"
    }
}
