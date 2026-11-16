package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.database.AppDatabase
import com.example.database.PhotoShootPlan
import com.example.repository.PhotoShootPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AddEditPlanUiState {
    data object Idle : AddEditPlanUiState()
    data object Loading : AddEditPlanUiState()
    data class Loaded(val plan: PhotoShootPlan) : AddEditPlanUiState()
    data object Saved : AddEditPlanUiState()
    data class Error(val message: String) : AddEditPlanUiState()
}

class AddEditPlanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoShootPlanRepository(
        AppDatabase.getInstance(application).photoShootPlanDao()
    )

    private val _uiState = MutableStateFlow<AddEditPlanUiState>(AddEditPlanUiState.Idle)
    val uiState: StateFlow<AddEditPlanUiState> = _uiState.asStateFlow()

    private var existingCreatedAt: Long? = null
    private var hasLoadedInitialPlan = false

    fun loadPlan(planId: Long) {
        if (planId <= 0L || hasLoadedInitialPlan) return
        hasLoadedInitialPlan = true
        viewModelScope.launch {
            _uiState.value = AddEditPlanUiState.Loading
            val result = repository.getPlanById(planId)
            val plan = result.getOrNull()
            if (plan != null) {
                existingCreatedAt = plan.createdAt
                _uiState.value = AddEditPlanUiState.Loaded(plan)
            } else {
                hasLoadedInitialPlan = false
                _uiState.value = AddEditPlanUiState.Error(
                    result.exceptionOrNull()?.localizedMessage
                        ?: getApplication<Application>().getString(R.string.error_room_load)
                )
            }
        }
    }

    fun savePlan(planId: Long, date: String, placeName: String, note: String) {
        viewModelScope.launch {
            _uiState.value = AddEditPlanUiState.Loading
            val trimmedDate = date.trim()
            val trimmedPlace = placeName.trim()
            val trimmedNote = note.trim()

            val result = if (planId > 0L) {
                repository.updatePlan(
                    PhotoShootPlan(
                        id = planId,
                        date = trimmedDate,
                        placeName = trimmedPlace,
                        note = trimmedNote,
                        createdAt = existingCreatedAt ?: System.currentTimeMillis()
                    )
                ).map { planId }
            } else {
                repository.insertPlan(
                    PhotoShootPlan(
                        date = trimmedDate,
                        placeName = trimmedPlace,
                        note = trimmedNote
                    )
                )
            }

            if (result.isSuccess) {
                _uiState.value = AddEditPlanUiState.Saved
            } else {
                _uiState.value = AddEditPlanUiState.Error(
                    result.exceptionOrNull()?.localizedMessage
                        ?: getApplication<Application>().getString(R.string.error_room_save)
                )
            }
        }
    }
}
