package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pamurlykin.nutritionassistant.data.backup.*
import com.pamurlykin.nutritionassistant.data.export.*
import com.pamurlykin.nutritionassistant.data.local.NutritionDatabase
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ProductLinksTest {
    private val date = "2026-11-18"
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NutritionDatabase::class.java).build()

    @Test fun linksHaveIndependentIdentityAndDoNotChangeHistoryOrAnalysisExport() = runBlocking {
        val db = database()
        try {
            val repo = NutritionRepository(db)
            repo.initialize()
            val product = repo.foodCatalog.save(null, "product", "Продукт для ссылок", "",
                CatalogDetails("bread", composition = "confirmed", source = "Этикетка", checkedOn = date), emptyList())
            repo.saveMeal(date, MealType.DINNER, null, listOf(product.id))
            suspend fun historyComposition() = repo.observeDailyStats(1, date).first().meals.single().items.single().compositionSnapshot
            val before = historyComposition()
            val beforeRevision = product.entry.revision
            val first = ProductLinkDraft("https://shop.example/food/42", "Тестовая витрина", date)
            val id = repo.productLinks.save(product.id, null, first)
            val second = repo.productLinks.save(product.id, null, ProductLinkDraft("https://other.example/42", "", null))
            val original = repo.productLinks.observe(product.id).first().first { it.id == id }
            assertTrue(runCatching { repo.productLinks.save(product.id, null, first.copy(url = "HTTPS://SHOP.EXAMPLE:443/food/42")) }.isFailure)
            val dish = repo.observeCatalog().first().first { it.kind == "dish" }
            assertTrue(runCatching { repo.productLinks.save(dish.id, null, first) }.isFailure)
            repo.productLinks.save(product.id, id, first.copy(checkedOn = null))
            repo.productLinks.delete(product.id, second)
            val updated = repo.productLinks.observe(product.id).first().single()
            assertEquals(original.publicId, updated.publicId)
            assertNull(updated.checkedOn)
            val after = repo.observeCatalog().first().first { it.id == product.id }
            assertEquals(beforeRevision, after.entry.revision)
            assertEquals(before, historyComposition())
            repo.archiveCatalogEntry(product.id)
            assertEquals(id, repo.productLinks.observe(product.id).first().single().id)

            val options = DiaryExportOptions(LocalDate.parse(date), LocalDate.parse(date), DiarySection.defaults, true, false, "")
            val (history, water) = repo.analytics.readExportData(options)
            val packet = DiaryExportWriter.encode(DiaryExportBuilder.build(history, water, options), DiaryExportFormat.JSON)!!
            assertFalse(packet.toString(Charsets.UTF_8).contains("shop.example"))
            assertFalse(packet.toString(Charsets.UTF_8).contains("Тестовая витрина"))
        } finally { db.close() }
    }

    @Test fun backupRestoresLinksAndRetainsLocalConflictWithoutDuplicates() = runBlocking {
        val source = database()
        val target = database()
        try {
            val writer = NutritionRepository(source)
            val reader = NutritionRepository(target)
            writer.initialize()
            reader.initialize()
            val product = writer.observeCatalog().first().first { it.kind == "product" }
            val local = reader.observeCatalog().first().single { it.entry.publicId == product.entry.publicId }
            val coincident = ProductLinkDraft("https://shop.example/food/42", "Из файла", date)
            val additional = ProductLinkDraft("https://other.example/food/42", "Вторая ссылка", null)
            writer.productLinks.save(product.id, null, coincident)
            writer.productLinks.save(product.id, null, additional)
            reader.productLinks.save(local.id, null, coincident.copy(source = "Локальная подпись"))
            val store = BackupStore(target, "test")
            val bytes = BackupStore(source, "test").export()
            val file = BackupCodec.decode(bytes)
            val preview = store.preview(file)
            assertEquals(1, preview.conflicts.size)
            assertEquals(1, preview.added)
            assertTrue(runCatching { store.apply(preview, ImportOptions()) }.isFailure)
            store.apply(preview, ImportOptions(keepLocalConflicts = true, matchCatalog = true))
            val links = reader.productLinks.observe(local.id).first()
            assertEquals(setOf("Локальная подпись", "Вторая ссылка"), links.map { it.source }.toSet())
            assertEquals(0, store.preview(file).added)
            val wrong = file.copy(records = file.records.map { record ->
                if (record.type == "product_links") record.copy(values = record.values + ("url" to "http://shop.example/item")) else record
            })
            assertTrue(runCatching { store.preview(wrong) }.isFailure)
            val mislabeled = JSONObject(bytes.toString(Charsets.UTF_8)).put("schemaVersion", 5).toString().toByteArray()
            assertTrue(runCatching { BackupCodec.decode(mislabeled) }.isFailure)
        } finally {
            source.close()
            target.close()
        }
    }
}
