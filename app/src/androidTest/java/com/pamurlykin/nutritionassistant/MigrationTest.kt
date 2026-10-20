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
        helper.runMigrationsAndValidate("migration-test", 2, true, NutritionDatabase.MIGRATION_1_2).close()
        val db = Room.databaseBuilder(context, NutritionDatabase::class.java, "migration-test")
            .addMigrations(NutritionDatabase.MIGRATION_1_2).build()
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
}
