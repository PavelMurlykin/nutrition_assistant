package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.nutritionassistant.data.backup.*
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataModelTest {
    private lateinit var db: NutritionDatabase
    private lateinit var repository: NutritionRepository
    private val day = "2026-11-17"
    private val approved = ImportOptions(keepLocalConflicts = true, matchCatalog = true, restoreCatalog = true)
    @Before fun setup() = runBlocking {
        db = newDatabase(); repository = NutritionRepository(db); repository.initialize()
    }
    @After fun close() { db.close() }
    private fun newDatabase() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NutritionDatabase::class.java).build()
    private suspend fun product(name: String, components: List<ComponentSelection> = emptyList(), details: CatalogDetails = CatalogDetails()) =
        repository.foodCatalog.save(null, "product", name, "", details, components)
    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail("Invalid data accepted") } catch (_: IllegalArgumentException) { }
    }
    private suspend fun daily() = repository.observeDailyStats(1, day).first()
    private fun hash(database: NutritionDatabase = db): BackupStore = BackupStore(database, "test")

    @Test fun nestedSnapshotPreservesPathEvidenceAndQuantitiesAfterRenamesAndExplicitRefresh() = runBlocking {
        val garlic = product("Чеснок модели")
        val mince = product("Фарш модели", listOf(ComponentSelection(garlic.id, "2", "g")),
            CatalogDetails("prepared", "variant", "Марка", "С чесноком", "confirmed", "Упаковка", day, "Проверен"))
        val dish = repository.foodCatalog.save(null, "dish", "Котлета модели", "home", CatalogDetails(composition = "partial"), listOf(ComponentSelection(mince.id, "150", "g")))
        repository.saveMeal(day, MealType.LUNCH, null, listOf(dish.id), portions = mapOf(dish.id to Portion("1", "piece")), comments = mapOf(dish.id to "Порция"))
        val event = daily().meals.single()
        val original = event.items.single()
        val root = CompositionSnapshot.decode(requireNotNull(original.compositionSnapshot))
        assertEquals("Чеснок модели", root.components.single().components.single().name)
        assertEquals(Portion("150", "g"), root.components.single().portion)
        assertEquals("Упаковка", root.components.single().details.source)
        assertFalse(root.fullyKnown)
        repository.foodCatalog.save(mince.id, "product", mince.name, "", mince.entry.details().copy(composition = "unknown", source = null, checkedOn = null), emptyList())
        repository.saveCatalogEntry(garlic.id, "product", "Другое название", "", emptyList())
        repository.saveMeal(day, MealType.LUNCH, event.event.id, listOf(dish.id))
        assertEquals(original.compositionSnapshot, daily().meals.single().items.single().compositionSnapshot)
        repository.saveMeal(day, MealType.LUNCH, event.event.id, listOf(dish.id), refreshComposition = setOf(dish.id))
        val refreshed = daily().meals.single().items.single()
        assertEquals(original.publicId, refreshed.publicId)
        assertEquals("1", refreshed.quantity); assertEquals("Порция", refreshed.comment)
        assertTrue(CompositionSnapshot.decode(requireNotNull(refreshed.compositionSnapshot)).components.single().components.isEmpty())
    }

    @Test fun variantsCanShareNamesAndSimpleEditingKeepsMetadataAndComposition() = runBlocking {
        val garlic = product("Добавка вариантов")
        val one = product("Фарш вариантов", details = CatalogDetails("prepared", "variant", "А", "Без добавок"))
        val two = product("Фарш вариантов", listOf(ComponentSelection(garlic.id, "3", "g")), CatalogDetails("prepared", "variant", "Б", "С добавками", comment = "Этикетка"))
        assertNotEquals(one.entry.publicId, two.entry.publicId)
        val edited = repository.saveCatalogEntry(two.id, "product", "Фарш вариантов", "", emptyList())
        assertEquals(two.entry.details(), edited.entry.details())
        assertEquals(two.entry.revision + 1, edited.entry.revision)
        assertEquals(listOf(garlic.id), edited.productIds)
        assertEquals("3", db.catalogDao().allProductComponents().single { it.ownerId == two.id }.quantity)
        assertNotEquals(one.displayName, two.displayName)
        val confirmed = repository.foodCatalog.save(null, "dish", "Подтверждённый рецепт", "home",
            CatalogDetails(composition = "confirmed", source = "Рецепт", checkedOn = day), listOf(ComponentSelection(garlic.id)))
        val changed = repository.saveCatalogEntry(confirmed.id, "dish", confirmed.name, "home", listOf(one.id))
        assertEquals("partial", changed.entry.compositionState); assertNull(changed.entry.compositionSource)
    }

    @Test fun cyclesAndInvalidPortionsRollbackWithoutLosingPreviousComposition() = runBlocking {
        val a = product("Узел А"); val b = product("Узел Б", listOf(ComponentSelection(a.id)))
        val before = BackupCodec.fingerprint(hash().snapshot())
        rejected { repository.foodCatalog.save(a.id, "product", a.name, "", a.entry.details(), listOf(ComponentSelection(b.id))) }
        rejected { repository.foodCatalog.save(a.id, "product", a.name, "", a.entry.details(), listOf(ComponentSelection(a.id))) }
        rejected { repository.saveMeal(day, MealType.BREAKFAST, null, listOf(a.id), portions = mapOf(a.id to Portion("0", "g"))) }
        assertEquals(before, BackupCodec.fingerprint(hash().snapshot()))
    }

    @Test fun multiplePrimaryMealsKeepActualTimeSeparateAndPreserveItDuringSimpleEditing() = runBlocking {
        val rice = product("Рис времени")
        repository.saveMeal(day, MealType.BREAKFAST, null, listOf(rice.id), EventContext(RecordedTime("06:15", "+03:00", "Europe/Moscow"), "Ранний"))
        repository.saveMeal(day, MealType.BREAKFAST, null, listOf(rice.id))
        val events = daily().meals
        assertEquals(2, events.size)
        val timed = events.single { it.event.actualTime != null }
        assertEquals("06:15", timed.event.actualTime); assertEquals("date_time", timed.event.timePrecision)
        assertEquals("date_only", events.single { it.event.actualTime == null }.event.timePrecision)
        repository.saveMeal(day, MealType.BREAKFAST, timed.event.id, listOf(rice.id))
        val edited = daily().meals.single { it.event.id == timed.event.id }.event
        assertEquals(timed.event.createdAt, edited.createdAt); assertEquals("Ранний", edited.comment); assertEquals("06:15", edited.actualTime)
    }

    @Test fun completenessIsExplicitAndNewFoodOrSymptomsInvalidateOnlyTheirConfirmation() = runBlocking {
        assertNull(daily().dayStatus)
        repository.confirmDay(1, day, true, true, "no_stool")
        val rice = product("Рис полноты")
        repository.saveMeal(day, MealType.DINNER, null, listOf(rice.id))
        assertFalse(requireNotNull(daily().dayStatus).foodComplete)
        assertTrue(requireNotNull(daily().dayStatus).symptomsComplete)
        repository.addStool(1, day, 7, EventContext(RecordedTime("21:00", "+03:00"), "Эпизод"))
        assertFalse(requireNotNull(daily().dayStatus).symptomsComplete)
        assertNull(requireNotNull(daily().dayStatus).stoolSummary)
        rejected { repository.confirmDay(1, day, true, true, "no_diarrhea") }
        repository.confirmDay(1, day, true, true, "diarrhea")
        repository.upsertSleep(1, day, "07:00", "23:00", "Просыпался")
        assertEquals("2026-11-16", daily().sleep?.bedDate); assertEquals(day, daily().sleep?.wakeupDate)
        assertFalse(requireNotNull(daily().dayStatus).symptomsComplete)
    }

    @Test fun jsonTwoRoundTripRestoresNestedCatalogHistoricalTreeTimesAndCompleteness() = runBlocking {
        val garlic = product("JSON чеснок")
        val bread = product("JSON хлеб", listOf(ComponentSelection(garlic.id, "0.5", "g")), CatalogDetails("bread", "variant", "Тест", "Состав", "confirmed", "Этикетка", day))
        repository.saveMeal(day, MealType.BREAKFAST, null, listOf(bread.id), EventContext(RecordedTime("09:00", "+03:00"), "Поздняя запись"), mapOf(bread.id to Portion("2", "piece")))
        val medicine = repository.saveCatalogEntry(null, "medicine", "JSON лекарство", "", emptyList())
        repository.saveMedicines(day, null, listOf(MedicineSelection(medicine.id, "1 таблетка", Portion("1", "piece"), "После еды", true)), EventContext(RecordedTime("10:00", "+03:00")))
        repository.addStool(1, day, 3)
        repository.upsertSleep(1, day, "07:00", "22:30", "Хорошо")
        repository.confirmDay(1, day, true, true, "no_diarrhea")
        repository.archiveCatalogEntry(garlic.id)
        val bytes = hash().export()
        assertEquals(5, JSONObject(bytes.toString(Charsets.UTF_8)).getInt("schemaVersion"))
        val target = newDatabase()
        try {
            val targetRepo = NutritionRepository(target); targetRepo.initialize()
            val store = hash(target); val file = BackupCodec.decode(bytes)
            store.apply(store.preview(file), approved)
            val restored = targetRepo.observeDailyStats(1, day).first()
            assertEquals(daily().meals.single().items.single().compositionSnapshot, restored.meals.single().items.single().compositionSnapshot)
            assertEquals("2", restored.meals.single().items.single().quantity)
            assertEquals("09:00", restored.meals.single().event.actualTime)
            assertEquals("После еды", restored.medicines.single().items.single().comment)
            assertEquals("1", restored.medicines.single().items.single().quantity)
            assertTrue(requireNotNull(restored.dayStatus).foodComplete)
            assertEquals("no_diarrhea", requireNotNull(restored.dayStatus).stoolSummary)
            assertEquals(daily().sleep?.bedDate, restored.sleep?.bedDate)
            assertEquals(0, store.preview(file).added)
            assertTrue(store.preview(file).conflicts.isEmpty())
        } finally { target.close() }
    }

    @Test fun versionOneImportPreservesFlatHistoryAndDoesNotInventTimeEvidenceOrQuantities() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val file = context.assets.open("nutrition-backup-v1.json").use(BackupCodec::read)
        val item = file.records.single { it.type == "meal_items" }
        assertNull(item.values["composition_snapshot"]); assertNull(item.values["source_revision"])
        val event = file.records.single { it.type == "meals" }
        assertNull(event.values["actual_time"]); assertEquals("date_only", event.values["time_precision"])
        assertTrue(file.records.filter { it.type == "catalog" }.all { it.values["composition_state"] == "unknown" })
        hash().apply(hash().preview(file), approved)
        val restored = BackupCodec.decode(hash().export())
        assertEquals(item.values, restored.records.single { it.publicId == item.publicId }.values)
        val second = context.assets.open("nutrition-backup-v2.json").use(BackupCodec::read)
        assertNotNull(second.records.single { it.type == "meal_items" }.values["composition_snapshot"])
        assertEquals(second.records.size, BackupCodec.decode(BackupCodec.encode(second)).records.size)
    }

    @Test fun ambiguousCatalogNamesAreNeverSilentlyMerged() = runBlocking {
        product("Совпадение"); product("Совпадение")
        val target = newDatabase()
        try {
            val targetRepo = NutritionRepository(target); targetRepo.initialize()
            targetRepo.foodCatalog.save(null, "product", "Совпадение", "", CatalogDetails(), emptyList())
            val store = hash(target); val file = BackupCodec.decode(hash().export())
            val preview = store.preview(file)
            store.apply(preview, approved)
            assertEquals(3, target.catalogDao().allEntries().count { it.name == "Совпадение" })
            assertEquals(0, store.preview(file).added)
        } finally { target.close() }
    }

    @Test fun aliasesCannotMergeTwoSourceCatalogIdentitiesIntoOne() = runBlocking {
        val a = product("Граф А"); product("Граф Б", listOf(ComponentSelection(a.id)))
        product("Добавление перед ошибкой")
        val file = BackupCodec.decode(hash().export())
        val target = newDatabase()
        try {
            val repo = NutritionRepository(target); repo.initialize()
            val single = repo.foodCatalog.save(null, "product", "Общая цель", "", CatalogDetails(), emptyList())
            file.records.filter { it.type == "catalog" && it.values["name"] in setOf("Граф А", "Граф Б") }.forEach { entry ->
                target.openHelper.writableDatabase.execSQL("INSERT INTO backup_aliases VALUES (?, ?, 'catalog')", arrayOf(entry.publicId, single.entry.publicId))
            }
            val store = hash(target); val before = BackupCodec.fingerprint(store.snapshot())
            // Two source identities must never overwrite the same local catalog row.
            rejected { store.preview(file) }
            assertEquals(before, BackupCodec.fingerprint(store.snapshot()))
            assertFalse(target.catalogDao().allEntries().any { it.name == "Добавление перед ошибкой" })
        } finally { target.close() }
    }
    @Test fun malformedNewFieldsAndCyclicImportAreRejectedBeforeAnyWrite() = runBlocking {
        val a = product("Проверка графа А"); val b = product("Проверка графа Б", listOf(ComponentSelection(a.id)))
        repository.saveMeal(day, MealType.SNACK, null, listOf(b.id))
        val store = hash(); val file = BackupCodec.decode(store.export())
        val before = BackupCodec.fingerprint(store.snapshot())
        val cases = listOf(
            file.copy(records = file.records.map { if (it.publicId == a.entry.publicId) it.copy(values = it.values + ("component_links" to listOf(mapOf("product_id" to b.entry.publicId, "quantity" to null, "unit" to null, "position" to 0L)))) else it }),
            file.copy(records = file.records.map { if (it.type == "meals") it.copy(values = it.values + mapOf("actual_time" to "08:00", "time_precision" to "date_time")) else it }),
            file.copy(records = file.records.map { if (it.type == "meal_items") it.copy(values = it.values + mapOf("quantity" to "0", "unit" to "g")) else it }),
            file.copy(records = file.records.map { if (it.type == "meal_items") it.copy(values = it.values + ("ingredients" to listOf("Выдуманный ингредиент"))) else it }),
        )
        cases.forEach { rejected { store.preview(it) } }
        assertEquals(before, BackupCodec.fingerprint(store.snapshot()))
    }

}
