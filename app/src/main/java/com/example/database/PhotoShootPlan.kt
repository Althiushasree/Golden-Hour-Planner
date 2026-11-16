package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_shoot_plans")
data class PhotoShootPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val date: String,
    val placeName: String,
    val note: String,
    val createdAt: Long = System.currentTimeMillis()
)
