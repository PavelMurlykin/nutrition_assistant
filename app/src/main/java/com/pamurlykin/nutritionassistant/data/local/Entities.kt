package com.pamurlykin.nutritionassistant.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pamurlykin.nutritionassistant.util.StorageFormats

@Entity(tableName = "users", indices = [Index(value = ["public_id"], unique = true)])
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
    @ColumnInfo(name = "public_id", defaultValue = "''")
    val publicId: String = java.util.UUID.randomUUID().toString(),
)

@Entity(
    tableName = "meals",
    indices = [Index(value = ["user_id", "date"]), Index(value = ["public_id"], unique = true)],
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
    @ColumnInfo(name = "public_id", defaultValue = "''")
    val publicId: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "actual_time", defaultValue = "NULL") val actualTime: String? = null,
    @ColumnInfo(name = "utc_offset", defaultValue = "NULL") val utcOffset: String? = null,
    @ColumnInfo(name = "time_zone", defaultValue = "NULL") val timeZone: String? = null,
    @ColumnInfo(name = "time_precision", defaultValue = "'date_only'") val timePrecision: String = "date_only",
    @ColumnInfo(defaultValue = "NULL") val comment: String? = null,
)

@Entity(
    tableName = "medicines",
    indices = [Index(value = ["user_id", "date"]), Index(value = ["public_id"], unique = true)],
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
    @ColumnInfo(name = "public_id", defaultValue = "''")
    val publicId: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "actual_time", defaultValue = "NULL") val actualTime: String? = null,
    @ColumnInfo(name = "utc_offset", defaultValue = "NULL") val utcOffset: String? = null,
    @ColumnInfo(name = "time_zone", defaultValue = "NULL") val timeZone: String? = null,
    @ColumnInfo(name = "time_precision", defaultValue = "'date_only'") val timePrecision: String = "date_only",
    @ColumnInfo(defaultValue = "NULL") val comment: String? = null,
)

@Entity(
    tableName = "stools",
    indices = [Index(value = ["user_id", "date"]), Index(value = ["public_id"], unique = true)],
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
    @ColumnInfo(name = "public_id", defaultValue = "''")
    val publicId: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "actual_time", defaultValue = "NULL") val actualTime: String? = null,
    @ColumnInfo(name = "utc_offset", defaultValue = "NULL") val utcOffset: String? = null,
    @ColumnInfo(name = "time_zone", defaultValue = "NULL") val timeZone: String? = null,
    @ColumnInfo(name = "time_precision", defaultValue = "'date_only'") val timePrecision: String = "date_only",
    @ColumnInfo(defaultValue = "NULL") val comment: String? = null,
)

@Entity(
    tableName = "feelings",
    indices = [Index(value = ["user_id", "date"]), Index(value = ["public_id"], unique = true)],
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
    @ColumnInfo(name = "public_id", defaultValue = "''")
    val publicId: String = java.util.UUID.randomUUID().toString(),
)

@Entity(
    tableName = "water",
    indices = [Index(value = ["user_id", "date"], unique = true), Index(value = ["public_id"], unique = true)],
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
    @ColumnInfo(name = "public_id", defaultValue = "''")
    val publicId: String = java.util.UUID.randomUUID().toString(),
)

@Entity(
    tableName = "sleeps",
    indices = [Index(value = ["user_id", "date"], unique = true), Index(value = ["public_id"], unique = true)],
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
    @ColumnInfo(name = "public_id", defaultValue = "''")
    val publicId: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "bed_date", defaultValue = "NULL") val bedDate: String? = null,
    @ColumnInfo(name = "wakeup_date", defaultValue = "NULL") val wakeupDate: String? = null,
)

@Entity(tableName = "day_status", indices = [Index(value = ["user_id", "date"], unique = true), Index(value = ["public_id"], unique = true)])
data class DayStatusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val date: String,
    @ColumnInfo(name = "food_complete", defaultValue = "0") val foodComplete: Boolean = false,
    @ColumnInfo(name = "symptoms_complete", defaultValue = "0") val symptomsComplete: Boolean = false,
    @ColumnInfo(name = "stool_summary", defaultValue = "NULL") val stoolSummary: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "updated_at") val updatedAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "public_id") val publicId: String = java.util.UUID.randomUUID().toString(),
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
