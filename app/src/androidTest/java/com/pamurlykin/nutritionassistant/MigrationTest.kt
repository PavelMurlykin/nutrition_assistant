package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.nutritionassistant.data.local.NutritionDatabase
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NutritionDatabase::class.java)
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @After fun cleanup() { context.deleteDatabase("migration-test") }

    @Test fun versionOneKeepsRecordsAndBackfillsCatalogWithoutGuessingFoodType() = runBlocking {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO users VALUES (1,'08:00','13:00','19:00','09:00','07:00','23:00','2026-10-17T08:00:00','2026-10-17T08:00:00')")
            execSQL("INSERT INTO meals VALUES (1,1,'2026-10-17','breakfast','Каша и чай','2026-10-17T08:00:00','2026-10-17T08:00:00')")
            execSQL("INSERT INTO medicines VALUES (1,1,'2026-10-17','Препарат','1 таблетка','2026-10-17T08:00:00','2026-10-17T08:00:00')")
            execSQL("INSERT INTO stools VALUES (1,1,'2026-10-17',0,'2026-10-17T08:00:00','2026-10-17T08:00:00')")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 8, true, NutritionDatabase.MIGRATION_1_2, NutritionDatabase.MIGRATION_2_3, NutritionDatabase.MIGRATION_3_4, NutritionDatabase.MIGRATION_4_5, NutritionDatabase.MIGRATION_5_6, NutritionDatabase.MIGRATION_6_7, NutritionDatabase.MIGRATION_7_8).close()
        val db = Room.databaseBuilder(context, NutritionDatabase::class.java, "migration-test")
            .addMigrations(NutritionDatabase.MIGRATION_1_2, NutritionDatabase.MIGRATION_2_3, NutritionDatabase.MIGRATION_3_4, NutritionDatabase.MIGRATION_4_5, NutritionDatabase.MIGRATION_5_6, NutritionDatabase.MIGRATION_6_7, NutritionDatabase.MIGRATION_7_8).build()
        try {
            val repository = NutritionRepository(db)
            repository.initialize()
            repository.initialize()
            val day = repository.observeDailyStats(1, "2026-10-17").first()
            assertEquals("Каша и чай", day.meals.single().items.single().name)
            assertEquals("unknown", day.meals.single().items.single().foodType)
            assertTrue(day.meals.single().items.single().ingredients.isEmpty())
            assertEquals("1 таблетка", day.medicines.single().items.single().dosage)
            assertEquals(0, day.stools.single().quality)
            assertTrue(day.user.breakfastEnabled)
        } finally { db.close() }
    }

    @Test fun versionTwoMovesStoreEntriesToProductsAndLinksRecipesWithoutChangingHistory() = runBlocking {
        helper.createDatabase("migration-test", 2).apply {
            execSQL("INSERT INTO users (user_id,breakfast_time,lunch_time,dinner_time,toilet_time,wakeup_time,bed_time,created_at,updated_at,breakfast_enabled,lunch_enabled,dinner_enabled,toilet_enabled) VALUES (1,'08:00','13:00','19:00','09:00','07:00','23:00','2026-10-17T08:00:00','2026-10-17T08:00:00',1,1,1,1)")
            execSQL("INSERT INTO meals VALUES (1,1,'2026-10-17','breakfast','Каша и йогурт','2026-10-17T08:00:00','2026-10-17T08:00:00')")
            execSQL("INSERT INTO catalog VALUES (1,'dish','Каша','каша','home','[\"Молоко\",\"ВОДА\",\"молоко\"]',0)")
            execSQL("INSERT INTO catalog VALUES (2,'dish','Йогурт','йогурт','store','[\"Молоко\"]',1)")
            execSQL("INSERT INTO catalog VALUES (3,'dish','Суп','суп','restaurant','[\"Вода\"]',0)")
            execSQL("INSERT INTO catalog VALUES (4,'dish','Хлеб','хлеб','home','[\"Мука\"]',0)")
            execSQL("INSERT INTO catalog VALUES (5,'dish','Хлеб','хлеб','store','[]',0)")
            execSQL("INSERT INTO meal_items VALUES (1,1,1,'Каша до редактирования','home','[\"Молоко\",\"Вода\"]')")
            execSQL("INSERT INTO meal_items VALUES (2,1,2,'Йогурт','store','[\"Молоко\"]')")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 8, true, NutritionDatabase.MIGRATION_2_3, NutritionDatabase.MIGRATION_3_4, NutritionDatabase.MIGRATION_4_5, NutritionDatabase.MIGRATION_5_6, NutritionDatabase.MIGRATION_6_7, NutritionDatabase.MIGRATION_7_8).apply {
            query("SELECT COUNT(*) FROM catalog WHERE food_type = 'store'").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            query("SELECT COUNT(*) FROM meal_items WHERE food_type = 'store'").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            close()
        }
        val db = Room.databaseBuilder(context, NutritionDatabase::class.java, "migration-test")
            .addMigrations(NutritionDatabase.MIGRATION_1_2, NutritionDatabase.MIGRATION_2_3, NutritionDatabase.MIGRATION_3_4, NutritionDatabase.MIGRATION_4_5, NutritionDatabase.MIGRATION_5_6, NutritionDatabase.MIGRATION_6_7, NutritionDatabase.MIGRATION_7_8).build()
        try {
            val repository = NutritionRepository(db)
            repository.initialize()
            val entries = repository.observeCatalog().first()
            val porridge = entries.single { it.id == 1L }
            val soup = entries.single { it.id == 3L }
            assertEquals(2, porridge.products.size)
            assertEquals(soup.products.single().id, porridge.products.single { it.normalizedName == "вода" }.id)
            assertEquals("product", requireNotNull(db.catalogDao().getItem(2)).kind)
            assertTrue(requireNotNull(db.catalogDao().getItem(2)).archived)
            assertEquals("dish", entries.single { it.id == 4L }.kind)
            assertEquals("product", entries.single { it.id == 5L }.kind)
            val before = repository.observeDailyStats(1, "2026-10-17").first().meals.single()
            assertEquals("Каша до редактирования", before.items.first().name)
            assertEquals(listOf("Молоко", "Вода"), before.items.first().ingredients)
            assertEquals("product", before.items.last().kind)
            assertEquals("", before.items.last().foodType)
            repository.saveMeal("2026-10-17", com.pamurlykin.nutritionassistant.data.model.MealType.BREAKFAST, 1, listOf(1, 2))
            assertEquals(before.items.map { it.name }, repository.observeDailyStats(1, "2026-10-17").first().meals.single().items.map { it.name })
        } finally { db.close() }
    }
    @Test fun versionThreeAssignsUniqueStableUuidsWithoutChangingRows() {
        helper.createDatabase("migration-test", 3).apply {
            execSQL("INSERT INTO catalog VALUES (1,'product','Рис','рис','','[]',1)")
            execSQL("INSERT INTO catalog VALUES (2,'product','Гречка','гречка','','[]',0)")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 8, true, NutritionDatabase.MIGRATION_3_4, NutritionDatabase.MIGRATION_4_5, NutritionDatabase.MIGRATION_5_6, NutritionDatabase.MIGRATION_6_7, NutritionDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT id, name, archived, public_id FROM catalog ORDER BY id").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(1L, cursor.getLong(0)); assertEquals("Рис", cursor.getString(1)); assertEquals(1, cursor.getInt(2))
                val first = java.util.UUID.fromString(cursor.getString(3))
                assertTrue(cursor.moveToNext()); assertNotEquals(first, java.util.UUID.fromString(cursor.getString(3)))
            }
        }
    }
    @Test fun versionFourKeepsUuidsAndFlatHistoryWithUnknownNewFields() {
        val user = "11111111-1111-4111-8111-111111111111"
        val product = "22222222-2222-4222-8222-222222222222"
        helper.createDatabase("migration-test", 4).apply {
            execSQL("INSERT INTO users (user_id,breakfast_time,lunch_time,dinner_time,toilet_time,wakeup_time,bed_time,created_at,updated_at,breakfast_enabled,lunch_enabled,dinner_enabled,toilet_enabled,public_id) VALUES (1,'08:00','13:00','19:00','09:00','07:00','23:00','2026-10-17T08:00:00','2026-10-17T08:00:00',1,1,1,1,?)", arrayOf(user))
            execSQL("INSERT INTO catalog (id,kind,name,normalized_name,food_type,ingredients,archived,public_id) VALUES (1,'product','Хлеб','хлеб','','[]',1,?)", arrayOf(product))
            execSQL("INSERT INTO meals (id,user_id,date,meal_type,description,created_at,updated_at,public_id) VALUES (1,1,'2026-10-17','breakfast','Хлеб','2026-10-17T08:00:00','2026-10-17T08:00:00','33333333-3333-4333-8333-333333333333')")
            execSQL("INSERT INTO meal_items (id,meal_id,catalog_id,name,food_type,ingredients,kind,public_id) VALUES (1,1,1,'Старый хлеб','','[\"Мука\"]','product','44444444-4444-4444-8444-444444444444')")
            execSQL("INSERT INTO sleeps (id,user_id,date,wakeup_time,bed_time,quality_description,created_at,updated_at,public_id) VALUES (1,1,'2026-10-17','07:00','23:00','Просыпался','2026-10-17T08:00:00','2026-10-17T08:00:00','55555555-5555-4555-8555-555555555555')")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 8, true, NutritionDatabase.MIGRATION_4_5, NutritionDatabase.MIGRATION_5_6, NutritionDatabase.MIGRATION_6_7, NutritionDatabase.MIGRATION_7_8).use { database ->
            database.query("SELECT public_id, category_code, composition_state, composition_source, revision FROM catalog").use {
                assertTrue(it.moveToFirst()); assertEquals(product, it.getString(0)); assertTrue(it.isNull(1)); assertEquals("unknown", it.getString(2)); assertTrue(it.isNull(3)); assertEquals(1, it.getInt(4))
            }
            database.query("SELECT name, ingredients, source_revision, composition_snapshot, quantity FROM meal_items").use {
                assertTrue(it.moveToFirst()); assertEquals("Старый хлеб", it.getString(0)); assertEquals("[\"Мука\"]", it.getString(1)); assertTrue(it.isNull(2)); assertTrue(it.isNull(3)); assertTrue(it.isNull(4))
            }
            database.query("SELECT actual_time, utc_offset, time_precision FROM meals").use {
                assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)); assertTrue(it.isNull(1)); assertEquals("date_only", it.getString(2))
            }
            database.query("SELECT bed_date, wakeup_date FROM sleeps").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)); assertTrue(it.isNull(1)) }
            database.query("SELECT COUNT(*) FROM day_status").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun versionFiveAddsFavoriteWithoutChangingIdentityOrHistoricalSnapshots() {
        helper.createDatabase("migration-test", 5).apply {
            execSQL("INSERT INTO catalog (id,kind,name,normalized_name,food_type,ingredients,archived,public_id,revision) VALUES (1,'product','Хлеб','хлеб','','[]',1,'11111111-1111-4111-8111-111111111111',7)")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 8, true, NutritionDatabase.MIGRATION_5_6, NutritionDatabase.MIGRATION_6_7, NutritionDatabase.MIGRATION_7_8).use { database ->
            database.query("SELECT public_id, name, archived, revision, favorite FROM catalog").use {
                assertTrue(it.moveToFirst()); assertEquals("11111111-1111-4111-8111-111111111111", it.getString(0)); assertEquals("Хлеб", it.getString(1)); assertEquals(1, it.getInt(2)); assertEquals(7, it.getInt(3)); assertEquals(0, it.getInt(4))
            }
        }
    }


    @Test fun versionSixPreservesLegacyTextIdentityAndUnknownValues() {
        helper.createDatabase("migration-test", 6).apply {
            execSQL("INSERT INTO feelings (id,user_id,date,description,created_at,updated_at,public_id) VALUES (7,1,'2026-11-17','Неважно, но без оценки','c','u','11111111-1111-4111-8111-111111111111')")
            execSQL("INSERT INTO sleeps (id,user_id,date,bed_time,wakeup_time,quality_description,created_at,updated_at,public_id) VALUES (8,1,'2026-11-17','23:00','07:00','Хороший сон','c','u','22222222-2222-4222-8222-222222222222')")
            execSQL("INSERT INTO stools (id,user_id,date,quality,created_at,updated_at,public_id) VALUES (9,1,'2026-11-17',7,'c','u','33333333-3333-4333-8333-333333333333')")
            execSQL("INSERT INTO stools (id,user_id,date,quality,created_at,updated_at,public_id) VALUES (10,1,'2026-11-17',0,'c','u','44444444-4444-4444-8444-444444444444')")
            execSQL("INSERT INTO backup_aliases VALUES ('55555555-5555-4555-8555-555555555555','11111111-1111-4111-8111-111111111111','feelings')")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 8, true, NutritionDatabase.MIGRATION_6_7, NutritionDatabase.MIGRATION_7_8).use { database ->
            database.query("SELECT id,comment,public_id,value_code,scale_version,actual_time,period,created_at FROM health_entries").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(7,c.getInt(0)); assertEquals("Неважно, но без оценки",c.getString(1))
                assertEquals("11111111-1111-4111-8111-111111111111",c.getString(2)); (3..5).forEach { assertTrue(c.isNull(it)) }
                assertEquals("moment",c.getString(6)); assertEquals("c",c.getString(7))
            }
            database.query("SELECT bed_time,wakeup_time,quality_description,quality_rating,scale_version,bed_date FROM sleeps").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("23:00",c.getString(0)); assertEquals("07:00",c.getString(1)); assertEquals("Хороший сон",c.getString(2))
                (3..5).forEach { assertTrue(c.isNull(it)) }
            }
            database.query("SELECT quality,scale_version,quality_label FROM stools ORDER BY id").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(7,c.getInt(0)); assertEquals(1,c.getInt(1)); assertFalse(c.isNull(2))
                assertTrue(c.moveToNext()); assertEquals(0,c.getInt(0)); assertTrue(c.isNull(1)); assertTrue(c.isNull(2))
            }
            database.query("SELECT type FROM backup_aliases").use { c -> assertTrue(c.moveToFirst()); assertEquals("health_entries",c.getString(0)) }
        }
    }

    @Test fun versionSevenAddsEmptyTrackingProfileWithoutChangingDiary() {
        helper.createDatabase("migration-test",7).apply {
            execSQL("INSERT INTO stools (id,user_id,date,quality,created_at,updated_at,public_id) VALUES (1,1,'2026-11-17',7,'2026-11-17T10:00:00','2026-11-17T10:00:00','11111111-1111-4111-8111-111111111111')")
            close()
        }
        helper.runMigrationsAndValidate("migration-test",8,true,NutritionDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT COUNT(*) FROM tracking_targets").use { assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0)) }
            db.query("SELECT quality,public_id FROM stools").use { assertTrue(it.moveToFirst());assertEquals(7,it.getInt(0));assertEquals("11111111-1111-4111-8111-111111111111",it.getString(1)) }
        }
    }
}
