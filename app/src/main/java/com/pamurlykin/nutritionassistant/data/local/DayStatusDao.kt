package com.pamurlykin.nutritionassistant.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DayStatusDao {
    @Query("SELECT * FROM day_status WHERE user_id = :userId AND date = :date") suspend fun get(userId: Long, date: String): DayStatusEntity?
    @Query("SELECT * FROM day_status WHERE user_id = :userId AND date = :date") fun observe(userId: Long, date: String): Flow<DayStatusEntity?>
    @Insert suspend fun insert(value: DayStatusEntity): Long
    @Update suspend fun update(value: DayStatusEntity)
    @Query("UPDATE day_status SET food_complete = 0, updated_at = :updatedAt WHERE user_id = :userId AND date = :date")
    suspend fun invalidateFood(userId: Long, date: String, updatedAt: String)
    @Query("UPDATE day_status SET symptoms_complete = 0, stool_summary = NULL, updated_at = :updatedAt WHERE user_id = :userId AND date = :date")
    suspend fun invalidateSymptoms(userId: Long, date: String, updatedAt: String)
}
