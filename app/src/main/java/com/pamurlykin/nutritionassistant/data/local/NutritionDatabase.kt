package com.pamurlykin.nutritionassistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        CatalogEntryEntity::class,
        MealItemEntity::class,
        MedicineItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(IngredientConverters::class)
abstract class NutritionDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun userDao(): UserDao
    abstract fun mealDao(): MealDao
    abstract fun medicineDao(): MedicineDao
    abstract fun stoolDao(): StoolDao
    abstract fun feelingDao(): FeelingDao
    abstract fun waterDao(): WaterDao
    abstract fun sleepDao(): SleepDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("breakfast", "lunch", "dinner", "toilet").forEach {
                    db.execSQL("ALTER TABLE users ADD COLUMN ${it}_enabled INTEGER NOT NULL DEFAULT 1")
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS catalog (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, normalized_name TEXT NOT NULL, food_type TEXT NOT NULL, ingredients TEXT NOT NULL, archived INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_catalog_kind_food_type_normalized_name ON catalog (kind, food_type, normalized_name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS meal_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, meal_id INTEGER NOT NULL, catalog_id INTEGER NOT NULL, name TEXT NOT NULL, food_type TEXT NOT NULL, ingredients TEXT NOT NULL, FOREIGN KEY(meal_id) REFERENCES meals(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(catalog_id) REFERENCES catalog(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE TABLE IF NOT EXISTS medicine_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, medicine_id INTEGER NOT NULL, catalog_id INTEGER NOT NULL, name TEXT NOT NULL, dosage TEXT, FOREIGN KEY(medicine_id) REFERENCES medicines(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(catalog_id) REFERENCES catalog(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                listOf("meal_items" to "meal_id", "medicine_items" to "medicine_id").forEach { (table, column) ->
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_$column ON $table ($column)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_catalog_id ON $table (catalog_id)")
                }
            }
        }
        fun create(context: Context): NutritionDatabase =
            Room.databaseBuilder(
                context,
                NutritionDatabase::class.java,
                "nutrition_assistant.db",
            ).addMigrations(MIGRATION_1_2).build()
    }
}
