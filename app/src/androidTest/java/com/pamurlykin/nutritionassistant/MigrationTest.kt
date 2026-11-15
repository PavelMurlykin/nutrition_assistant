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
        helper.runMigrationsAndValidate("migration-test", 3, true, NutritionDatabase.MIGRATION_1_2, NutritionDatabase.MIGRATION_2_3).close()
        val db = Room.databaseBuilder(context, NutritionDatabase::class.java, "migration-test")
            .addMigrations(NutritionDatabase.MIGRATION_1_2, NutritionDatabase.MIGRATION_2_3).build()
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
        helper.runMigrationsAndValidate("migration-test", 3, true, NutritionDatabase.MIGRATION_2_3).apply {
            query("SELECT COUNT(*) FROM catalog WHERE food_type = 'store'").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            query("SELECT COUNT(*) FROM meal_items WHERE food_type = 'store'").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            close()
        }
        val db = Room.databaseBuilder(context, NutritionDatabase::class.java, "migration-test")
            .addMigrations(NutritionDatabase.MIGRATION_1_2, NutritionDatabase.MIGRATION_2_3).build()
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
}
