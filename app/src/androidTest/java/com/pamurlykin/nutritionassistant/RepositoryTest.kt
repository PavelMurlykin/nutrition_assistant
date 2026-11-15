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
    private suspend fun product(name: String) = repository.saveCatalogEntry(null, CatalogKind.PRODUCT, name, "", emptyList())

    @Test fun installedApplicationRequestsOnlyNotificationsAndDisablesBackup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), info.requestedPermissions.orEmpty().toList())
        assertEquals(0, requireNotNull(info.applicationInfo).flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun multiDishEventPreservesCompositionAcrossCatalogEditsAndArchiving() = runBlocking {
        val home = repository.saveCatalogEntry(null, "dish", "Тестовая каша", "home", listOf(product("Молоко").id, product("Овсянка").id))
        val yogurt = product("Тестовый йогурт")
        repository.saveMeal("2026-10-17", MealType.BREAKFAST, null, listOf(home.id, yogurt.id))
        val event = repository.observeDailyStats(1, "2026-10-17").first().meals.single()
        repository.saveCatalogEntry(home.id, "dish", "Новая каша", "home", listOf(product("Вода").id))
        repository.archiveCatalogEntry(yogurt.id)
        repository.saveMeal("2026-10-17", MealType.BREAKFAST, event.event.id, listOf(home.id, yogurt.id))
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
        val first = repository.saveCatalogEntry(null, "dish", "Тест ёж", "home", listOf(product("Яйцо").id))
        val duplicate = repository.saveCatalogEntry(null, "dish", "ТЕСТ ЕЖ", "home", listOf(product("Молоко").id))
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

    @Test fun productRenameUpdatesCurrentRecipesButPreservesLoggedComposition() = runBlocking {
        val milk = product("Тестовое молоко")
        val recipe = repository.saveCatalogEntry(null, CatalogKind.DISH, "Тестовый напиток", "restaurant", listOf(milk.id))
        repository.saveMeal("2026-11-15", MealType.SNACK, null, listOf(recipe.id))
        repository.saveCatalogEntry(milk.id, CatalogKind.PRODUCT, "Новое молоко", "", emptyList())
        assertEquals(listOf("Новое молоко"), repository.observeCatalog().first().single { it.id == recipe.id }.ingredients)
        assertEquals(listOf("Тестовое молоко"), repository.observeDailyStats(1, "2026-11-15").first().meals.single().items.single().ingredients)
        repository.archiveCatalogEntry(milk.id)
        repository.saveCatalogEntry(recipe.id, CatalogKind.DISH, recipe.name, "restaurant", listOf(milk.id))
        assertEquals(listOf(milk.id), repository.observeCatalog().first().single { it.id == recipe.id }.productIds)
        try {
            repository.saveCatalogEntry(null, CatalogKind.DISH, "Блюдо с удаленным продуктом", "home", listOf(milk.id))
            fail("An archived product must not be used in a new recipe")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun recipesRequireProductsAndRejectMedicinesAndRemovedCategory() = runBlocking {
        val medicine = repository.saveCatalogEntry(null, CatalogKind.MEDICINE, "Тестовое лекарство", "", emptyList())
        for (type in FoodType.entries) {
            try {
                repository.saveCatalogEntry(null, CatalogKind.DISH, "Пустое блюдо", type.storageValue, emptyList())
                fail("Every edited or new recipe requires products")
            } catch (_: IllegalArgumentException) { }
        }
        try {
            repository.saveCatalogEntry(null, CatalogKind.DISH, "Лекарство в блюде", "home", listOf(medicine.id))
            fail("Medicine is not a product")
        } catch (_: IllegalArgumentException) { }
        try {
            repository.saveCatalogEntry(null, CatalogKind.DISH, "Старая категория", "store", listOf(product("Вода").id))
            fail("The removed food category must not be persisted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun calendarCountsIntakesRatherThanItemsAndUpdatesAfterDeletion() = runBlocking {
        val day = "2026-11-15"
        val dishes = repository.observeCatalog().first().filter { it.kind == CatalogKind.DISH }.take(2)
        val medicine = repository.saveCatalogEntry(null, CatalogKind.MEDICINE, "Календарный тест", "", emptyList())
        repository.saveMeal(day, MealType.BREAKFAST, null, dishes.map { it.id })
        repository.saveMeal(day, MealType.SNACK, null, listOf(dishes.first().id))
        repository.saveMeal("2026-10-31", MealType.SNACK, null, listOf(dishes.first().id))
        repository.saveMedicines(day, null, listOf(MedicineSelection(medicine.id, "1")))
        repository.addStool(1, day, 4)
        repository.addStool(1, day, 6)
        repository.addStool(1, "2026-11-16", 3)
        val counts = repository.observeCalendarCounts(day, day).first().single()
        assertEquals(CalendarDayCounts(day, 2, 1, 2), counts)
        assertEquals(3, repository.observeCalendarCounts("2026-10-31", "2026-11-16").first().size)

        val snapshot = repository.observeDailyStats(1, day).first()
        snapshot.meals.forEach { repository.deleteMeal(1, it.event.id) }
        assertEquals(CalendarDayCounts(day, 0, 1, 2), repository.observeCalendarCounts(day, day).first().single())
        snapshot.medicines.forEach { repository.deleteMedicine(1, it.event.id) }
        snapshot.stools.forEach { repository.deleteStool(1, it.id) }
        assertTrue(repository.observeCalendarCounts(day, day).first().isEmpty())
    }
}
