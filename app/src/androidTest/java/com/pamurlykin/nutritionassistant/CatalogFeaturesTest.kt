package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamurlykin.nutritionassistant.data.backup.*
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogFeaturesTest {
    private lateinit var db: NutritionDatabase
    private lateinit var repo: NutritionRepository
    private val day = "2026-11-17"
    @Before fun setup() { db = database(); repo = NutritionRepository(db) }
    @After fun close() { db.close() }
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NutritionDatabase::class.java).build()
    private suspend fun hash() = BackupCodec.fingerprint(BackupStore(db, "test").snapshot())
    private suspend fun product(name: String, components: List<ComponentSelection> = emptyList(), details: CatalogDetails = CatalogDetails("other")) = repo.foodCatalog.save(null, "product", name, "", details, components)
    private suspend fun rejected(action: suspend () -> Unit) { try { action(); fail("Invalid operation accepted") } catch (_: IllegalArgumentException) { } }

    @Test fun hundredBuiltinsCoverCategoriesAndNeverGuessPackagedCompositionOrUndoEdits() = runBlocking {
        repo.initialize()
        assertEquals(100, BuiltinCatalog.products.size)
        assertEquals(ProductCategory.entries.map { it.code }.toSet(), BuiltinCatalog.products.map { it.category }.toSet())
        val entries = db.catalogDao().allItems()
        BuiltinCatalog.products.forEach { seed ->
            val entry = entries.single { it.entry.publicId == seed.publicId }
            assertEquals(seed.category, entry.entry.categoryCode); assertEquals("unknown", entry.entry.compositionState)
            assertTrue(entry.productIds.isEmpty())
        }
        val rice = entries.single { it.name == "Рис" }
        repo.saveCatalogEntry(rice.id, "product", "Мой рис", "", emptyList())
        repo.setFavorite(rice.id, true); repo.archiveCatalogEntry(rice.id)
        val before = hash(); repo.initialize(); repo.initialize()
        assertEquals(before, hash())
        val saved = requireNotNull(db.catalogDao().get(rice.id))
        assertEquals("Мой рис", saved.name); assertTrue(saved.archived); assertTrue(saved.favorite)
        assertFalse(db.catalogDao().allEntries().any { it.name == "Рис" })
        repo.restoreCatalogEntry(rice.id)
        assertFalse(requireNotNull(db.catalogDao().get(rice.id)).archived)
        assertTrue(requireNotNull(db.catalogDao().get(rice.id)).favorite)
        assertTrue(entries.filter { it.kind == "dish" }.all { it.entry.comment?.startsWith("Шаблон рецепта") == true })
    }

    @Test fun legacyAmbiguityRequiresChoiceAndKeepsBothUserCompositions() = runBlocking {
        repo.ensureUser()
        val first = product("Рис", details = CatalogDetails("grains", comment = "Мой первый"))
        val second = product("Рис", details = CatalogDetails("grains", comment = "Мой второй"))
        repo.archiveCatalogEntry(second.id)
        repo.initialize()
        val conflict = repo.observeSeedConflicts().first().single()
        assertEquals("rice", conflict.seed.key); assertEquals(2, conflict.candidates.size)
        assertEquals(2, db.catalogDao().allEntries().count { it.name == "Рис" })
        repo.resolveSeed("rice", second.id); repo.initialize()
        assertTrue(repo.observeSeedConflicts().first().isEmpty())
        assertEquals(second.entry.publicId, db.catalogDao().aliases().single { it.sourceId == conflict.seed.publicId }.targetId)
        assertTrue(requireNotNull(db.catalogDao().get(second.id)).archived)
        assertEquals("Мой первый", requireNotNull(db.catalogDao().get(first.id)).comment)
    }

    @Test fun ambiguousLegacyCanRemainSeparateFromNewBuiltin() = runBlocking {
        repo.ensureUser(); product("Рис"); product("Рис"); repo.initialize()
        repo.resolveSeed("rice", null); repo.initialize()
        assertEquals(3, db.catalogDao().allEntries().count { it.name == "Рис" })
        assertTrue(repo.observeSeedConflicts().first().isEmpty())
    }

    @Test fun nestedProductsMixedMealAndVariantsShareTheSamePreviewAndStoredTree() = runBlocking {
        repo.initialize()
        val base = db.catalogDao().allItems()
        val garlic = base.single { it.name == "Чеснок" }; val beef = base.single { it.name == "Говядина" }
        val flour = base.single { it.name == "Мука пшеничная" }; val water = base.single { it.name == "Вода" }
        val mince = product("Фарш", listOf(ComponentSelection(beef.id), ComponentSelection(garlic.id, "2", "g")),
            CatalogDetails("prepared", "variant", "Марка", "С чесноком", "confirmed", "Упаковка", day))
        val dumplings = product("Пельмени", listOf(ComponentSelection(mince.id), ComponentSelection(flour.id), ComponentSelection(water.id)), CatalogDetails("prepared", "variant", "Марка", "Конкретные", "partial"))
        val cutlet = repo.foodCatalog.save(null, "dish", "Котлета говяжья", "home", CatalogDetails(composition = "partial"), listOf(ComponentSelection(mince.id)))
        val mash = repo.foodCatalog.save(null, "dish", "Пюре", "home", CatalogDetails(composition = "partial"), listOf(ComponentSelection(base.single { it.name == "Картофель" }.id)))
        val cucumber = base.single { it.name == "Огурец" }
        val bread = product("Хлеб вариантов", details = CatalogDetails("bread", "variant", "А", "Неизвестный"))
        val otherBread = product("Хлеб вариантов", listOf(ComponentSelection(flour.id)), CatalogDetails("bread", "variant", "Б", "Известный частично", "partial"))
        assertNotEquals(bread.entry.publicId, otherBread.entry.publicId)
        assertNotEquals(bread.displayName, otherBread.displayName)
        val graph = db.catalogDao().allItems()
        val preview = CompositionSnapshot.encode(catalogSnapshot(dumplings.id, graph))
        assertEquals(preview, CompositionSnapshot.encode(repo.foodCatalog.snapshots(listOf(dumplings.id)).getValue(dumplings.id)))
        assertTrue(cutlet.matches("чеснок", graph, true)); assertFalse(cutlet.matches("чеснок", graph, false))
        repo.saveMeal(day, MealType.LUNCH, null, listOf(dumplings.id), expectedCompositionHashes = mapOf(dumplings.id to compositionFingerprint(preview)))
        repo.saveMeal(day, MealType.DINNER, null, listOf(cutlet.id, mash.id, cucumber.id))
        val meals = repo.observeDailyStats(1, day).first().meals
        assertEquals(3, meals.single { it.event.mealType == "dinner" }.items.size)
        val saved = meals.single { it.event.mealType == "lunch" }.items.single()
        assertEquals(preview, saved.compositionSnapshot); assertTrue(saved.ingredients.contains("Чеснок"))
    }

    @Test fun repeatingCreatesNewIdentitiesAndKeepsReviewedHistoryOrExplicitlyRefreshes() = runBlocking {
        repo.initialize()
        val old = product("Старый компонент"); val fresh = product("Новый компонент")
        val dish = repo.foodCatalog.save(null, "dish", "Повторяемое блюдо", "home", CatalogDetails(composition = "partial"), listOf(ComponentSelection(old.id)))
        repo.saveMeal(day, MealType.BREAKFAST, null, listOf(dish.id), EventContext(RecordedTime("08:00", "+03:00")), mapOf(dish.id to Portion("150", "g")))
        val source = repo.observeDailyStats(1, day).first().meals.single()
        repo.saveCatalogEntry(dish.id, "dish", dish.name, "home", listOf(fresh.id))
        repo.saveMeal("2026-11-18", MealType.BREAKFAST, null, listOf(dish.id), sourceMealId = source.event.id)
        val copy = repo.observeDailyStats(1, "2026-11-18").first().meals.single()
        assertNotEquals(source.event.publicId, copy.event.publicId); assertNotEquals(source.items.single().publicId, copy.items.single().publicId)
        assertEquals(source.items.single().compositionSnapshot, copy.items.single().compositionSnapshot)
        assertEquals("150", copy.items.single().quantity); assertNull(copy.event.actualTime)
        val now = CompositionSnapshot.encode(catalogSnapshot(dish.id, db.catalogDao().allItems()))
        repo.saveMeal("2026-11-18", MealType.BREAKFAST, null, listOf(dish.id), portions = mapOf(dish.id to Portion("200", "g")),
            refreshComposition = setOf(dish.id), sourceMealId = source.event.id, expectedCompositionHashes = mapOf(dish.id to compositionFingerprint(now)))
        assertEquals(2, repo.observeDailyStats(1, "2026-11-18").first().meals.size)
        assertEquals(source.items.single().compositionSnapshot, repo.observeDailyStats(1, day).first().meals.single().items.single().compositionSnapshot)
        val before = hash()
        rejected { repo.saveMeal(day, MealType.SNACK, null, listOf(dish.id), expectedCompositionHashes = mapOf(dish.id to "stale")) }
        assertEquals(before, hash())
    }

    @Test fun favoriteAndRenamedBuiltinRestoreWithoutDuplicatesAndOldJsonStaysReadable() = runBlocking {
        repo.initialize()
        val rice = db.catalogDao().allItems().single { it.name == "Рис" }
        repo.setFavorite(rice.id, true); repo.saveCatalogEntry(rice.id, "product", "Рис моей марки", "", emptyList())
        val example = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("nutrition-backup-v3.json").use(BackupCodec::read)
        assertTrue(example.records.any { it.type == "catalog" && it.values["favorite"] == true })
        val target = database()
        try {
            val targetRepo = NutritionRepository(target); targetRepo.initialize()
            val store = BackupStore(target, "test")
            val file = BackupCodec.decode(BackupStore(db, "test").export())
            store.apply(store.preview(file), ImportOptions(keepLocalConflicts = true, matchCatalog = true, restoreCatalog = true))
            targetRepo.initialize()
            val restored = target.catalogDao().allEntries().single { it.publicId == rice.entry.publicId }
            assertTrue(restored.favorite); assertEquals("Рис моей марки", restored.name)
            assertFalse(target.catalogDao().allEntries().any { it.name == "Рис" })
            assertEquals(0, store.preview(file).added)
        } finally { target.close() }
    }
}
