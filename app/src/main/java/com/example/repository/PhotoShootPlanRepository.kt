package com.example.repository

import com.example.database.PhotoShootPlan
import com.example.database.PhotoShootPlanDao
import kotlinx.coroutines.flow.Flow

class PhotoShootPlanRepository(
    private val dao: PhotoShootPlanDao
) {

    fun observeAllPlans(): Flow<List<PhotoShootPlan>> = dao.getAllPlans()

    suspend fun getPlanById(id: Long): Result<PhotoShootPlan?> = runCatching {
        dao.getPlanById(id)
    }

    suspend fun insertPlan(plan: PhotoShootPlan): Result<Long> = runCatching {
        dao.insertPlan(plan)
    }

    suspend fun updatePlan(plan: PhotoShootPlan): Result<Unit> = runCatching {
        dao.updatePlan(plan)
    }

    suspend fun deletePlan(plan: PhotoShootPlan): Result<Unit> = runCatching {
        dao.deletePlan(plan)
    }
}
