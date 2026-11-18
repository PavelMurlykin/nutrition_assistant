package com.pamurlykin.nutritionassistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pamurlykin.nutritionassistant.data.model.normalizeName

@Database(
    entities = [
        UserEntity::class,
        MealEntity::class,
        MedicineEntity::class,
        StoolEntity::class,
        HealthEntryEntity::class,
        WaterEntity::class,
        SleepEntity::class,
        NotificationLogEntity::class,
        CatalogEntryEntity::class,
        MealItemEntity::class,
        MedicineItemEntity::class,
        DishProductEntity::class,
        BackupAliasEntity::class,
        ProductComponentEntity::class,
        DayStatusEntity::class,
        TrackingTargetEntity::class,
        ProductLinkEntity::class,
    ],
    version = 9,
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
    abstract fun dayStatusDao(): DayStatusDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun productLinkDao(): ProductLinkDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE product_links (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, catalog_id INTEGER NOT NULL, url TEXT NOT NULL, source TEXT NOT NULL, checked_on TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, public_id TEXT NOT NULL, FOREIGN KEY(catalog_id) REFERENCES catalog(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE UNIQUE INDEX index_product_links_catalog_id_url ON product_links (catalog_id, url)")
                db.execSQL("CREATE UNIQUE INDEX index_product_links_public_id ON product_links (public_id)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE tracking_targets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id INTEGER NOT NULL, catalog_id INTEGER NOT NULL, scope TEXT NOT NULL, name_snapshot TEXT NOT NULL, source_note TEXT, comment TEXT, active INTEGER NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, public_id TEXT NOT NULL, FOREIGN KEY(catalog_id) REFERENCES catalog(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX index_tracking_targets_catalog_id ON tracking_targets (catalog_id)")
                db.execSQL("CREATE UNIQUE INDEX index_tracking_targets_public_id ON tracking_targets (public_id)")
                db.execSQL("CREATE UNIQUE INDEX index_tracking_targets_user_id_catalog_id_scope ON tracking_targets (user_id,catalog_id,scope)")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE health_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id INTEGER NOT NULL, date TEXT NOT NULL, metric TEXT NOT NULL, value_code TEXT, value_label TEXT, scale_version INTEGER, period TEXT NOT NULL, day_slot INTEGER, duration_minutes INTEGER, activity_kind TEXT, illness_impact TEXT, symptom_codes TEXT NOT NULL, comment TEXT, actual_time TEXT, utc_offset TEXT, time_zone TEXT, time_precision TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, public_id TEXT NOT NULL)")
                db.execSQL("INSERT INTO health_entries (id,user_id,date,metric,period,symptom_codes,comment,time_precision,created_at,updated_at,public_id) SELECT id,user_id,date,'wellbeing','moment','[]',description,'date_only',created_at,updated_at,public_id FROM feelings")
                db.execSQL("DROP TABLE feelings")
                db.execSQL("CREATE INDEX index_health_entries_user_id_date ON health_entries (user_id,date)")
                db.execSQL("CREATE UNIQUE INDEX index_health_entries_public_id ON health_entries (public_id)")
                db.execSQL("CREATE UNIQUE INDEX index_health_entries_user_id_date_metric_day_slot ON health_entries (user_id,date,metric,day_slot)")
                db.execSQL("UPDATE backup_aliases SET type = 'health_entries' WHERE type = 'feelings'")
                listOf("scale_version INTEGER DEFAULT NULL", "quality_label TEXT DEFAULT NULL", "urgency INTEGER DEFAULT NULL", "pain INTEGER DEFAULT NULL").forEach { db.execSQL("ALTER TABLE stools ADD COLUMN $it") }
                (1..7).forEach { value -> db.execSQL("UPDATE stools SET scale_version = 1, quality_label = ? WHERE quality = ?", arrayOf<Any>(com.pamurlykin.nutritionassistant.data.model.StoolCondition.label(value), value)) }
                db.execSQL("CREATE TABLE sleeps_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id INTEGER NOT NULL, date TEXT NOT NULL, wakeup_time TEXT, bed_time TEXT, quality_description TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, public_id TEXT NOT NULL DEFAULT '', bed_date TEXT DEFAULT NULL, wakeup_date TEXT DEFAULT NULL, quality_rating INTEGER DEFAULT NULL, quality_label TEXT DEFAULT NULL, scale_version INTEGER DEFAULT NULL, awakenings INTEGER DEFAULT NULL)")
                db.execSQL("INSERT INTO sleeps_new (id,user_id,date,wakeup_time,bed_time,quality_description,created_at,updated_at,public_id,bed_date,wakeup_date) SELECT id,user_id,date,wakeup_time,bed_time,quality_description,created_at,updated_at,public_id,bed_date,wakeup_date FROM sleeps")
                db.execSQL("DROP TABLE sleeps")
                db.execSQL("ALTER TABLE sleeps_new RENAME TO sleeps")
                db.execSQL("CREATE UNIQUE INDEX index_sleeps_user_id_date ON sleeps (user_id,date)")
                db.execSQL("CREATE UNIQUE INDEX index_sleeps_public_id ON sleeps (public_id)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE catalog ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0") }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX index_catalog_kind_food_type_normalized_name")
                db.execSQL("CREATE INDEX index_catalog_kind_food_type_normalized_name ON catalog (kind, food_type, normalized_name)")
                listOf("category_code TEXT DEFAULT NULL", "product_kind TEXT NOT NULL DEFAULT 'basic'", "brand TEXT NOT NULL DEFAULT ''",
                    "variant_label TEXT NOT NULL DEFAULT ''", "composition_state TEXT NOT NULL DEFAULT 'unknown'", "composition_source TEXT DEFAULT NULL",
                    "composition_checked_on TEXT DEFAULT NULL", "comment TEXT DEFAULT NULL", "revision INTEGER NOT NULL DEFAULT 1").forEach {
                    db.execSQL("ALTER TABLE catalog ADD COLUMN $it")
                }
                listOf("quantity TEXT DEFAULT NULL", "unit TEXT DEFAULT NULL", "position INTEGER NOT NULL DEFAULT 0").forEach {
                    db.execSQL("ALTER TABLE dish_products ADD COLUMN $it")
                }
                db.execSQL("UPDATE dish_products SET position = (SELECT COUNT(*) FROM dish_products AS preceding WHERE preceding.dish_id = dish_products.dish_id AND preceding.product_id < dish_products.product_id)")
                db.execSQL("CREATE TABLE product_components (owner_id INTEGER NOT NULL, product_id INTEGER NOT NULL, quantity TEXT DEFAULT NULL, unit TEXT DEFAULT NULL, position INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(owner_id, product_id), FOREIGN KEY(owner_id) REFERENCES catalog(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(product_id) REFERENCES catalog(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX index_product_components_product_id ON product_components (product_id)")
                listOf("meals", "medicines", "stools").forEach { table ->
                    listOf("actual_time TEXT DEFAULT NULL", "utc_offset TEXT DEFAULT NULL", "time_zone TEXT DEFAULT NULL",
                        "time_precision TEXT NOT NULL DEFAULT 'date_only'", "comment TEXT DEFAULT NULL").forEach {
                        db.execSQL("ALTER TABLE $table ADD COLUMN $it")
                    }
                }
                listOf("meal_items", "medicine_items").forEach { table ->
                    listOf("source_revision INTEGER DEFAULT NULL", "quantity TEXT DEFAULT NULL", "unit TEXT DEFAULT NULL", "comment TEXT DEFAULT NULL").forEach {
                        db.execSQL("ALTER TABLE $table ADD COLUMN $it")
                    }
                }
                db.execSQL("ALTER TABLE meal_items ADD COLUMN composition_snapshot TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sleeps ADD COLUMN bed_date TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sleeps ADD COLUMN wakeup_date TEXT DEFAULT NULL")
                db.execSQL("CREATE TABLE day_status (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id INTEGER NOT NULL, date TEXT NOT NULL, food_complete INTEGER NOT NULL DEFAULT 0, symptoms_complete INTEGER NOT NULL DEFAULT 0, stool_summary TEXT DEFAULT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, public_id TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_day_status_user_id_date ON day_status (user_id, date)")
                db.execSQL("CREATE UNIQUE INDEX index_day_status_public_id ON day_status (public_id)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("users", "catalog", "meals", "medicines", "stools", "feelings", "water", "sleeps", "meal_items", "medicine_items").forEach { table ->
                    val key = if (table == "users") "user_id" else "id"
                    db.execSQL("ALTER TABLE $table ADD COLUMN public_id TEXT NOT NULL DEFAULT ''")
                    val ids = mutableListOf<Long>()
                    db.query("SELECT $key FROM $table").use { cursor -> while (cursor.moveToNext()) ids += cursor.getLong(0) }
                    ids.forEach { id -> db.execSQL("UPDATE $table SET public_id = ? WHERE $key = ?", arrayOf<Any>(java.util.UUID.randomUUID().toString(), id)) }
                    db.execSQL("CREATE UNIQUE INDEX index_${table}_public_id ON $table (public_id)")
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS backup_aliases (source_id TEXT NOT NULL PRIMARY KEY, target_id TEXT NOT NULL, type TEXT NOT NULL)")
            }
        }
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
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS dish_products (dish_id INTEGER NOT NULL, product_id INTEGER NOT NULL, PRIMARY KEY(dish_id, product_id), FOREIGN KEY(dish_id) REFERENCES catalog(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(product_id) REFERENCES catalog(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dish_products_product_id ON dish_products (product_id)")
                db.execSQL("ALTER TABLE meal_items ADD COLUMN kind TEXT NOT NULL DEFAULT 'dish'")
                // Preserve IDs and event links when moving the removed category into Products.
                db.execSQL("UPDATE catalog SET kind = 'product', food_type = '' WHERE kind = 'dish' AND food_type = 'store'")
                db.execSQL("UPDATE meal_items SET kind = 'product', food_type = '' WHERE food_type = 'store'")
                db.query("SELECT id, kind, ingredients FROM catalog ORDER BY id").use { cursor ->
                    val recipes = mutableListOf<Triple<Long, String, List<String>>>()
                    while (cursor.moveToNext()) recipes += Triple(cursor.getLong(0), cursor.getString(1), IngredientConverters().decode(cursor.getString(2)))
                    recipes.forEach { (dishId, kind, names) ->
                        names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(::normalizeName)
                            .forEach { name ->
                                val normalized = normalizeName(name)
                                db.execSQL("INSERT OR IGNORE INTO catalog (kind, name, normalized_name, food_type, ingredients, archived) VALUES ('product', ?, ?, '', '[]', 0)", arrayOf(name, normalized))
                                if (kind == "dish") db.execSQL("INSERT OR IGNORE INTO dish_products (dish_id, product_id) SELECT ?, id FROM catalog WHERE kind = 'product' AND food_type = '' AND normalized_name = ?", arrayOf<Any>(dishId, normalized))
                            }
                    }
                }
                db.execSQL("UPDATE catalog SET ingredients = '[]'")
            }
        }
        fun create(context: Context): NutritionDatabase =
            Room.databaseBuilder(
                context,
                NutritionDatabase::class.java,
                "nutrition_assistant.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build()
    }
}
