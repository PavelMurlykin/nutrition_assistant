package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var database: NutritionDatabase
    private lateinit var repository: NutritionRepository
    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NutritionDatabase::class.java).build()
        repository = NutritionRepository(database)
        repository.initialize()
    }
    @After fun close() { database.close() }

    @Test fun installedApplicationRequestsOnlyNotificationsAndDisablesBackup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), info.requestedPermissions.orEmpty().toList())
        assertEquals(0, requireNotNull(info.applicationInfo).flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun multiDishEventPreservesCompositionAcrossCatalogEditsAndArchiving() = runBlocking {
        val home = repository.saveCatalogEntry(null, "dish", "Тестовая каша", "home", listOf("Молоко", "Овсянка"))
        val store = repository.saveCatalogEntry(null, "dish", "Тестовый йогурт", "store", emptyList())
        repository.saveMeal("2026-10-17", MealType.BREAKFAST, null, listOf(home.id, store.id))
        val event = repository.observeDailyStats(1, "2026-10-17").first().meals.single()
        repository.saveCatalogEntry(home.id, "dish", "Новая каша", "home", listOf("Вода"))
        repository.archiveCatalogEntry(store.id)
        repository.saveMeal("2026-10-17", MealType.BREAKFAST, event.event.id, listOf(home.id, store.id))
        val saved = repository.observeDailyStats(1, "2026-10-17").first()
        assertEquals(2, saved.meals.single().items.size)
        assertEquals(listOf("Молоко", "Овсянка"), saved.meals.single().items.first().ingredients)
        assertEquals("Тестовая каша", saved.meals.single().items.first().name)
        assertNull(saved.sleep) // Browsing a date must not fabricate a sleep record.
        repository.addStool(1, "2026-10-18", 7)
        assertTrue(repository.observeDailyStats(1, "2026-10-18").first().analysis.conditions.single().factors.any { it.factor == "Ингредиент: молоко" })
        repository.deleteMeal(1, event.event.id)
        assertTrue(database.catalogDao().mealItems(event.event.id).isEmpty())
    }

    @Test fun multipleMedicinesKeepIndividualDosesAndRejectWrongCatalogKind() = runBlocking {
        val first = repository.saveCatalogEntry(null, "medicine", "Лекарство А", "", emptyList())
        val second = repository.saveCatalogEntry(null, "medicine", "Лекарство Б", "", emptyList())
        repository.saveMedicines("2026-10-18", null, listOf(MedicineSelection(first.id, "1 таблетка"), MedicineSelection(second.id, "2 мл")))
        val event = repository.observeDailyStats(1, "2026-10-18").first().medicines.single()
        assertEquals(listOf("1 таблетка", "2 мл"), event.items.map { it.dosage })
        repository.archiveCatalogEntry(first.id)
        repository.saveMedicines("2026-10-18", event.event.id, listOf(MedicineSelection(first.id, "2 таблетки")))
        assertEquals("2 таблетки", repository.observeDailyStats(1, "2026-10-18").first().medicines.single().items.single().dosage)
        try {
            repository.saveMeal("2026-10-18", MealType.SNACK, null, listOf(second.id))
            fail("A medicine cannot be selected as a dish")
        } catch (_: IllegalArgumentException) { }
        assertTrue(repository.observeDailyStats(1, "2026-10-18").first().meals.isEmpty())
    }

    @Test fun catalogIsIdempotentAndHomeNeedsIngredients() = runBlocking {
        val first = repository.saveCatalogEntry(null, "dish", "Тест ёж", "home", listOf("Яйцо"))
        val duplicate = repository.saveCatalogEntry(null, "dish", "ТЕСТ ЕЖ", "home", listOf("Молоко"))
        assertEquals(first.id, duplicate.id)
        assertEquals(listOf("Яйцо"), duplicate.ingredients)
        repository.initialize()
        val entries = repository.observeCatalog().first()
        FoodType.entries.forEach { type -> assertTrue(entries.any { it.foodType == type.storageValue }) }
        try {
            repository.saveCatalogEntry(null, "dish", "Без состава", "home", emptyList())
            fail("Home food needs ingredients")
        } catch (_: IllegalArgumentException) { }
        repository.setReminderEnabled(ScheduleSlot.TOILET, false)
        assertFalse(repository.getUser().toiletEnabled)
        assertTrue(repository.getUser().breakfastEnabled)
    }
}
