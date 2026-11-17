package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamurlykin.nutritionassistant.data.backup.*
import com.pamurlykin.nutritionassistant.data.local.NutritionDatabase
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private lateinit var source: NutritionDatabase
    private lateinit var target: NutritionDatabase
    private lateinit var repository: NutritionRepository
    private lateinit var targetRepository: NutritionRepository
    private lateinit var store: BackupStore
    private lateinit var targetStore: BackupStore
    private val day = "2026-11-16"
    private val approved = ImportOptions(keepLocalConflicts = true, matchCatalog = true, restoreSettings = true, restoreCatalog = true)

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = Room.inMemoryDatabaseBuilder(context, NutritionDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, NutritionDatabase::class.java).build()
        repository = NutritionRepository(source); targetRepository = NutritionRepository(target)
        repository.initialize(); targetRepository.initialize()
        store = BackupStore(source, "test"); targetStore = BackupStore(target, "test")
    }
    @After fun close() { source.close(); target.close() }
    private suspend fun populate() {
        val garlic = repository.saveCatalogEntry(null, "product", "Чеснок для проверки", "", emptyList())
        val dish = repository.saveCatalogEntry(null, "dish", "Котлета для проверки", "home", listOf(garlic.id))
        repository.saveMeal(day, MealType.LUNCH, null, listOf(dish.id, garlic.id))
        repository.saveCatalogEntry(dish.id, "dish", "Новый рецепт котлеты", "home", listOf(repository.observeCatalog().first().first { it.name == "Вода" }.id))
        repository.archiveCatalogEntry(garlic.id)
        val medicine = repository.saveCatalogEntry(null, "medicine", "Тестовый препарат", "", emptyList())
        repository.saveMedicines(day, null, listOf(MedicineSelection(medicine.id, "1 таблетка")))
        repository.addStool(1, day, 7); repository.addFeeling(1, day, "Свободный комментарий")
        repository.setWaterForDay(1, day, 0)
        repository.upsertSleep(1, day, "07:10", "23:30", "Просыпался ночью")
        repository.updateScheduleSlot(1, ScheduleSlot.BREAKFAST, "09:15")
        repository.setReminderEnabled(ScheduleSlot.LUNCH, false)
        repository.markNotificationSent(1, "breakfast", day)
        repository.archiveCatalogEntry(repository.observeCatalog().first().single { it.name == "Йогурт" }.id)
    }
    private suspend fun hash(store: BackupStore) = BackupCodec.fingerprint(store.snapshot())
    private suspend fun expectFailure(action: suspend () -> Unit) {
        try { action(); fail("Expected operation to be rejected") } catch (_: IllegalArgumentException) { }
    }

    @Test fun completeRoundTripPreservesHistorySettingsArchiveAndRepeatImportIsIdempotent() = runBlocking {
        populate()
        val bytes = store.export()
        val file = BackupCodec.decode(bytes)
        assertTrue(file.records.none { it.type == "notifications_log" })
        val before = hash(targetStore)
        val preview = targetStore.preview(file)
        assertEquals(before, hash(targetStore)) // Preview and cancellation have no side effects.
        assertTrue(preview.catalogMatches.isNotEmpty())
        targetStore.apply(preview, approved)
        val original = repository.observeDailyStats(1, day).first()
        val restored = targetRepository.observeDailyStats(1, day).first()
        assertEquals(original.meals.single().event.publicId, restored.meals.single().event.publicId)
        assertEquals(original.meals.single().items.map { it.publicId }, restored.meals.single().items.map { it.publicId })
        assertEquals(listOf("Котлета для проверки", "Чеснок для проверки"), restored.meals.single().items.map { it.name })
        assertEquals(listOf("Чеснок для проверки"), restored.meals.single().items.first().ingredients)
        assertEquals("1 таблетка", restored.medicines.single().items.single().dosage)
        assertEquals(7, restored.stools.single().quality)
        assertEquals("Свободный комментарий", restored.feelings.single().description)
        assertEquals("Просыпался ночью", restored.sleep?.qualityDescription)
        assertNotNull(target.waterDao().getForDay(1, day)); assertEquals(0, restored.waterCount)
        assertEquals("09:15", restored.user.breakfastTime); assertFalse(restored.user.lunchEnabled)
        val archived = targetStore.snapshot().records.single { it.type == "catalog" && it.values["name"] == "Чеснок для проверки" }
        assertEquals(true, archived.values["archived"])
        assertEquals(true, targetStore.snapshot().records.single { it.type == "catalog" && it.values["name"] == "Йогурт" }.values["archived"])
        val next = targetStore.preview(file)
        assertEquals(0, next.added); assertTrue(next.conflicts.isEmpty()); assertTrue(next.catalogMatches.isEmpty())
        val restoredHash = hash(targetStore)
        targetStore.apply(next, approved)
        assertEquals(restoredHash, hash(targetStore))
        val reexport = BackupCodec.decode(targetStore.export())
        assertTrue(reexport.aliases.isNotEmpty())
        assertEquals(reexport.records.size, file.records.size)
    }

    @Test fun stableUuidsSurviveEditingAllMutableRecordTypes() = runBlocking {
        populate()
        val before = store.snapshot().records.groupBy { it.type }.mapValues { (_, rows) -> rows.map { it.publicId }.toSet() }
        val snapshot = repository.observeDailyStats(1, day).first()
        repository.saveMeal(day, MealType.LUNCH, snapshot.meals.single().event.id, snapshot.meals.single().items.map { it.catalogId })
        repository.saveMedicines(day, snapshot.medicines.single().event.id, snapshot.medicines.single().items.map { MedicineSelection(it.catalogId, "2 таблетки") })
        repository.setWaterForDay(1, day, 3); repository.upsertSleep(1, day, "08:00", "22:00", "Хорошо")
        repository.updateStool(1, snapshot.stools.single().id, 6); repository.updateFeeling(1, snapshot.feelings.single().id, "Лучше")
        val dish = repository.observeCatalog().first().single { it.name == "Новый рецепт котлеты" }
        repository.saveCatalogEntry(dish.id, "dish", "Ещё один рецепт", "home", dish.productIds)
        assertEquals(before, store.snapshot().records.groupBy { it.type }.mapValues { (_, rows) -> rows.map { it.publicId }.toSet() })
    }

    @Test fun changedDatabaseRejectsStalePreviewWithoutPartialImport() = runBlocking {
        populate()
        val preview = targetStore.preview(BackupCodec.decode(store.export()))
        targetRepository.addFeeling(1, day, "Изменение после предпросмотра")
        val beforeApply = hash(targetStore)
        expectFailure { targetStore.apply(preview, approved) }
        assertEquals(beforeApply, hash(targetStore))
    }

    @Test fun changedHistoricalChildConflictsWithWholeEventAndKeepsLocalSnapshot() = runBlocking {
        populate()
        val file = BackupCodec.decode(store.export())
        targetStore.apply(targetStore.preview(file), approved)
        val changed = file.copy(records = file.records.map {
            if (it.type == "meal_items") it.copy(values = it.values + ("comment" to "Изменённый комментарий позиции")) else it
        })
        val preview = targetStore.preview(changed)
        assertEquals(1, preview.conflicts.size)
        val before = hash(targetStore)
        expectFailure { targetStore.apply(preview, ImportOptions()) }
        targetStore.apply(preview, ImportOptions(keepLocalConflicts = true))
        assertEquals(before, hash(targetStore))
    }

    @Test fun catalogMatchesNeedExplicitApprovalAndSettingsStayLocalByDefault() = runBlocking {
        populate()
        val preview = targetStore.preview(BackupCodec.decode(store.export()))
        val before = hash(targetStore)
        expectFailure { targetStore.apply(preview, ImportOptions()) }
        assertEquals(before, hash(targetStore))
        targetStore.apply(preview, ImportOptions(matchCatalog = true, keepLocalConflicts = true))
        assertEquals("08:00", targetRepository.getUser().breakfastTime)
        assertTrue(targetRepository.getUser().lunchEnabled)
    }

    @Test fun databaseFailureRollsBackEveryInsertedRecordAndAlias() = runBlocking {
        populate()
        val preview = targetStore.preview(BackupCodec.decode(store.export()))
        target.openHelper.writableDatabase.execSQL("CREATE TRIGGER backup_test_failure BEFORE INSERT ON feelings BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val before = hash(targetStore)
        try { targetStore.apply(preview, approved); fail("Database failure must roll back") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, hash(targetStore))
    }

    @Test fun parserRejectsUnknownFieldsVersionsDuplicateKeysBadReferencesNumbersAndUtf8() = runBlocking {
        populate()
        val bytes = store.export()
        val text = bytes.toString(Charsets.UTF_8)
        val unknown = JSONObject(text).put("unexpected", true).toString()
        val wrongVersion = JSONObject(text).put("schemaVersion", 3).toString()
        val duplicate = text.replaceFirst("{", "{\"schemaVersion\":1,")
        val trailing = "$text {}"
        val invalidDate = text.replace(day, "2026-02-30")
        val fractional = text.replace("\"quality\":7", "\"quality\":7.5")
        val uuid = BackupCodec.decode(bytes).records.first { it.type == "meal_items" }.values["catalog_id"] as String
        val missingReference = text.replace(uuid, java.util.UUID.randomUUID().toString()).let { changed ->
            // Only remove the referenced catalog row while preserving a syntactically valid file.
            val json = JSONObject(changed)
            val records = json.getJSONArray("records")
            for (index in records.length() - 1 downTo 0) if (records.getJSONObject(index).getString("type") == "catalog") records.remove(index)
            json.toString()
        }
        listOf(unknown, wrongVersion, duplicate, trailing, invalidDate, fractional, missingReference).forEach { bad ->
            expectFailure { BackupCodec.decode(bad.toByteArray(Charsets.UTF_8)) }
        }
        expectFailure { BackupCodec.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
        expectFailure { BackupCodec.read(java.io.ByteArrayInputStream(ByteArray(BackupCodec.MAX_BYTES + 1))) }
        assertEquals(BackupCodec.fingerprint(BackupCodec.decode(bytes)), BackupCodec.fingerprint(BackupCodec.read(java.io.ByteArrayInputStream(bytes))))
    }

    @Test fun dailyWaterAndSleepConflictsNeverReplaceLocalValues() = runBlocking {
        populate(); targetRepository.setWaterForDay(1, day, 9); targetRepository.upsertSleep(1, day, "10:00", "01:00", "Локальный сон")
        val preview = targetStore.preview(BackupCodec.decode(store.export()))
        assertEquals(2, preview.conflicts.count { it.name.startsWith("Вода") || it.name.startsWith("Сон") })
        targetStore.apply(preview, approved)
        assertEquals(9, target.waterDao().getForDay(1, day)?.glassesCount)
        assertEquals("Локальный сон", target.sleepDao().getForDay(1, day)?.qualityDescription)
        assertEquals(0, targetStore.preview(BackupCodec.decode(store.export())).added)
    }

    @Test fun documentedExampleRestoresAndRepeatsWithoutDuplicates() = runBlocking {
        val file = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("nutrition-backup-v1.json").use(BackupCodec::read)
        targetStore.apply(targetStore.preview(file), approved)
        val restored = targetRepository.observeDailyStats(1, "2026-11-17").first()
        assertEquals("Пример: тестовое блюдо", restored.meals.single().items.single().name)
        assertEquals(listOf("Пример: молоко"), restored.meals.single().items.single().ingredients)
        assertEquals(0, targetStore.preview(file).added)
    }
}
