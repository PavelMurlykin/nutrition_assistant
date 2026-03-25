package com.pamurlykin.nutritionassistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        MealEntity::class,
        MedicineEntity::class,
        StoolEntity::class,
        FeelingEntity::class,
        WaterEntity::class,
        SleepEntity::class,
        NotificationLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NutritionDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun mealDao(): MealDao
    abstract fun medicineDao(): MedicineDao
    abstract fun stoolDao(): StoolDao
    abstract fun feelingDao(): FeelingDao
    abstract fun waterDao(): WaterDao
    abstract fun sleepDao(): SleepDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        fun create(context: Context): NutritionDatabase =
            Room.databaseBuilder(
                context,
                NutritionDatabase::class.java,
                "nutrition_assistant.db",
            ).build()
    }
}
