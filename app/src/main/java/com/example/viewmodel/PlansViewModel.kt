package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.database.AppDatabase
import com.example.database.PhotoShootPlan
import com.example.repository.PhotoShootPlanRepository
import com.example.utils.UiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class PlanOperationEvent {
    data class Message(val text: String) : PlanOperationEvent()
    data class Error(val text: String) : PlanOperationEvent()
}

class PlansViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoShootPlanRepository(
        AppDatabase.getInstance(application).photoShootPlanDao()
    )

    val plansState: StateFlow<UiState<List<PhotoShootPlan>>> = repository.observeAllPlans()
        .map<List<PhotoShootPlan>, UiState<List<PhotoShootPlan>>> { plans ->
            UiState.Success(plans)
        }
        .catch { e ->
            emit(
                UiState.Error(
                    e.localizedMessage ?: getApplication<Application>().getString(R.string.error_room_load)
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = UiState.Loading
        )

    private val _events = MutableSharedFlow<PlanOperationEvent>()
    val events: SharedFlow<PlanOperationEvent> = _events.asSharedFlow()

    fun deletePlan(plan: PhotoShootPlan) {
        viewModelScope.launch {
            val result = repository.deletePlan(plan)
            if (result.isSuccess) {
                _events.emit(
                    PlanOperationEvent.Message(
                        getApplication<Application>().getString(R.string.plan_deleted_message)
                    )
                )
            } else {
                _events.emit(
                    PlanOperationEvent.Error(
                        result.exceptionOrNull()?.localizedMessage
                            ?: getApplication<Application>().getString(R.string.error_room_delete)
                    )
                )
            }
        }
    }
}
