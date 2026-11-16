package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.database.AppDatabase
import com.example.database.PhotoShootPlan
import com.example.repository.PhotoShootPlanRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoShootPlanRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: PhotoShootPlanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PhotoShootPlanRepository(database.photoShootPlanDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert update and delete photo shoot plans persist via Room`() = runTest {
        val plan = PhotoShootPlan(
            date = "2026-11-20",
            placeName = "Hawk Hill Overlook",
            note = "Bring 24-70mm and ND filter for sunset"
        )
        val insertedId = repository.insertPlan(plan).getOrThrow()
        assertTrue(insertedId > 0L)

        val allAfterInsert = repository.observeAllPlans().first()
        assertEquals(1, allAfterInsert.size)
        assertEquals("Hawk Hill Overlook", allAfterInsert.first().placeName)

        val loaded = repository.getPlanById(insertedId).getOrThrow()
        assertNotNull(loaded)

        val updated = loaded!!.copy(note = "Arrive 30 min early for blue hour")
        repository.updatePlan(updated).getOrThrow()

        val allAfterUpdate = repository.observeAllPlans().first()
        assertEquals("Arrive 30 min early for blue hour", allAfterUpdate.first().note)

        repository.deletePlan(allAfterUpdate.first()).getOrThrow()
        val allAfterDelete = repository.observeAllPlans().first()
        assertTrue(allAfterDelete.isEmpty())
        assertNull(repository.getPlanById(insertedId).getOrThrow())
    }
}
