package com.example.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.R
import com.example.database.PhotoShootPlan
import com.example.databinding.ActivityPlansBinding
import com.example.utils.UiState
import com.example.viewmodel.PlanOperationEvent
import com.example.viewmodel.PlansViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class PlansActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlansBinding
    private val plansViewModel: PlansViewModel by viewModels()

    private lateinit var adapter: PhotoShootPlanAdapter
    private var defaultDateArg: String? = null

    private val authManager = com.example.auth.AuthManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (authManager.currentUser() == null) {
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }
        enableEdgeToEdge()
        binding = ActivityPlansBinding.inflate(layoutInflater)
        setContentView(binding.root)

        defaultDateArg = intent.getStringExtra(AddEditPlanActivity.EXTRA_DEFAULT_DATE)

        ViewCompat.setOnApplyWindowInsetsListener(binding.plansRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.toolbarPlans.setNavigationOnClickListener {
            finish()
        }

        adapter = PhotoShootPlanAdapter(
            onEditClick = { plan ->
                val intent = Intent(this, AddEditPlanActivity::class.java).apply {
                    putExtra(AddEditPlanActivity.EXTRA_PLAN_ID, plan.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { plan ->
                confirmDeletePlan(plan)
            }
        )

        binding.rvPlans.layoutManager = LinearLayoutManager(this)
        binding.rvPlans.adapter = adapter

        binding.fabAddPlan.setOnClickListener {
            val intent = Intent(this, AddEditPlanActivity::class.java).apply {
                if (!defaultDateArg.isNullOrBlank()) {
                    putExtra(AddEditPlanActivity.EXTRA_DEFAULT_DATE, defaultDateArg)
                }
            }
            startActivity(intent)
        }

        observePlans()
    }

    private fun observePlans() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    plansViewModel.plansState.collect { state ->
                        when (state) {
                            is UiState.Idle,
                            is UiState.Loading -> {
                                binding.progressPlans.visibility = View.VISIBLE
                                binding.layoutEmptyPlans.visibility = View.GONE
                                binding.tvPlansError.visibility = View.GONE
                            }
                            is UiState.Error -> {
                                binding.progressPlans.visibility = View.GONE
                                binding.tvPlansError.visibility = View.VISIBLE
                                binding.tvPlansError.text = state.message
                            }
                            is UiState.Success -> {
                                binding.progressPlans.visibility = View.GONE
                                binding.tvPlansError.visibility = View.GONE
                                val plans = state.data
                                adapter.submitList(plans)
                                if (plans.isEmpty()) {
                                    binding.rvPlans.visibility = View.GONE
                                    binding.layoutEmptyPlans.visibility = View.VISIBLE
                                } else {
                                    binding.rvPlans.visibility = View.VISIBLE
                                    binding.layoutEmptyPlans.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
                launch {
                    plansViewModel.events.collect { event ->
                        when (event) {
                            is PlanOperationEvent.Message -> {
                                Snackbar.make(binding.plansRoot, event.text, Snackbar.LENGTH_SHORT).show()
                            }
                            is PlanOperationEvent.Error -> {
                                Snackbar.make(binding.plansRoot, event.text, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun confirmDeletePlan(plan: PhotoShootPlan) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_plan_title)
            .setMessage(getString(R.string.delete_plan_message, plan.placeName, plan.date))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                plansViewModel.deletePlan(plan)
            }
            .show()
    }
}
