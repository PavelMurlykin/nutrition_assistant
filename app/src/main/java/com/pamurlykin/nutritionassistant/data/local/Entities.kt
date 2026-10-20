package com.pamurlykin.nutritionassistant.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pamurlykin.nutritionassistant.util.StorageFormats

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: Long,
    @ColumnInfo(name = "breakfast_time")
    val breakfastTime: String = "08:00",
    @ColumnInfo(name = "lunch_time")
    val lunchTime: String = "13:00",
    @ColumnInfo(name = "dinner_time")
    val dinnerTime: String = "19:00",
    @ColumnInfo(name = "toilet_time")
    val toiletTime: String = "09:00",
    @ColumnInfo(name = "wakeup_time")
    val wakeupTime: String = "07:00",
    @ColumnInfo(name = "bed_time")
    val bedTime: String = "23:00",
    @ColumnInfo(name = "breakfast_enabled", defaultValue = "1")
    val breakfastEnabled: Boolean = true,
    @ColumnInfo(name = "lunch_enabled", defaultValue = "1")
    val lunchEnabled: Boolean = true,
    @ColumnInfo(name = "dinner_enabled", defaultValue = "1")
    val dinnerEnabled: Boolean = true,
    @ColumnInfo(name = "toilet_enabled", defaultValue = "1")
    val toiletEnabled: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = StorageFormats.nowTimestamp(),
)

@Entity(
    tableName = "meals",
    indices = [Index(value = ["user_id", "date"])],
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    val date: String,
    @ColumnInfo(name = "meal_type")
    val mealType: String,
    val description: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = StorageFormats.nowTimestamp(),
)

@Entity(
    tableName = "medicines",
    indices = [Index(value = ["user_id", "date"])],
)
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    val date: String,
    val name: String,
    val dosage: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = StorageFormats.nowTimestamp(),
)

@Entity(
    tableName = "stools",
    indices = [Index(value = ["user_id", "date"])],
)
data class StoolEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    val date: String,
    val quality: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = StorageFormats.nowTimestamp(),
)

@Entity(
    tableName = "feelings",
    indices = [Index(value = ["user_id", "date"])],
)
data class FeelingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    val date: String,
    val description: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = StorageFormats.nowTimestamp(),
)

@Entity(
    tableName = "water",
    indices = [Index(value = ["user_id", "date"], unique = true)],
)
data class WaterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    val date: String,
    @ColumnInfo(name = "glasses_count")
    val glassesCount: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = StorageFormats.nowTimestamp(),
)

@Entity(
    tableName = "sleeps",
    indices = [Index(value = ["user_id", "date"], unique = true)],
)
data class SleepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    val date: String,
    @ColumnInfo(name = "wakeup_time")
    val wakeupTime: String,
    @ColumnInfo(name = "bed_time")
    val bedTime: String,
    @ColumnInfo(name = "quality_description")
    val qualityDescription: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = StorageFormats.nowTimestamp(),
)

@Entity(
    tableName = "notifications_log",
    indices = [
        Index(value = ["user_id", "type", "date"], unique = true),
        Index(value = ["user_id", "date", "type"]),
    ],
)
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    @ColumnInfo(name = "type")
    val type: String,
    val date: String,
    @ColumnInfo(name = "sent_at")
    val sentAt: String = StorageFormats.nowTimestamp(),
)
