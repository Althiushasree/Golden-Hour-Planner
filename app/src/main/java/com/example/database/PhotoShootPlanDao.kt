package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoShootPlanDao {

    @Query("SELECT * FROM photo_shoot_plans ORDER BY date ASC, id DESC")
    fun getAllPlans(): Flow<List<PhotoShootPlan>>

    @Query("SELECT * FROM photo_shoot_plans WHERE id = :planId LIMIT 1")
    suspend fun getPlanById(planId: Long): PhotoShootPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: PhotoShootPlan): Long

    @Update
    suspend fun updatePlan(plan: PhotoShootPlan)

    @Delete
    suspend fun deletePlan(plan: PhotoShootPlan)
}
