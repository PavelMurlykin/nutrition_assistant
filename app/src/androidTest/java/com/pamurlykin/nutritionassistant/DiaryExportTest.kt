package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pamurlykin.nutritionassistant.data.backup.BackupCodec
import com.pamurlykin.nutritionassistant.data.export.*
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DiaryExportTest {
    private val first = LocalDate.parse("2026-11-15")
    private val last = first.plusDays(1)
    private val firstDate = first.toString()
    private val lastDate = last.toString()

    private fun render(data: DiaryExportPackage, format: DiaryExportFormat) =
        requireNotNull(DiaryExportWriter.encode(data, format)).toString(Charsets.UTF_8)

    @Test fun formatsPreserveSelectedValuesAndMaskNestedNamesAndFreeText() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NutritionDatabase::class.java).build()
        try {
            val repo = NutritionRepository(db)
            repo.initialize()
            val component = repo.foodCatalog.save(null, "product", "Чеснок", "", CatalogDetails(
                "vegetables", composition = "confirmed", source = "Секретный магазин", checkedOn = firstDate,
                comment = "Комментарий компонента",
            ), emptyList())
            val food = repo.foodCatalog.save(null, "product", "Хлеб от Павла", "", CatalogDetails(
                "bread", "variant", "Секретная марка", "Секретный вариант", "partial", "Секретная этикетка",
                firstDate, "Комментарий состава",
            ), listOf(ComponentSelection(component.id, "1.5", "g")))
            repo.saveMeal(firstDate, MealType.DINNER, null, listOf(food.id),
                EventContext(RecordedTime("21:30", "+03:00", "Europe/Moscow"), "Комментарий еды"))
            repo.foodCatalog.save(food.id, "product", "Нынешнее имя", "", food.entry.details(), listOf(ComponentSelection(component.id)))
            val medicine = repo.saveCatalogEntry(null, "medicine", "Секретное лекарство", "", emptyList())
            repo.saveMedicines(firstDate, null, listOf(MedicineSelection(medicine.id, "полтаблетки", Portion("1", "piece"))),
                EventContext(RecordedTime("18:45", "+00:00"), "Комментарий лекарства"))
            repo.addStool(1, lastDate, 7, EventContext(comment = "Комментарий стула"))
            repo.addFeeling(1, lastDate, "Комментарий старого наблюдения")
            db.sleepDao().upsert(SleepEntity(userId = 1, date = lastDate, qualityRating = 3, scaleVersion = 1,
                qualityLabel = "Среднее", bedTime = "23:30", wakeupTime = "08:45", qualityDescription = "Комментарий сна"))
            repo.setWaterForDay(1, firstDate, 3)
            repo.setWaterForDay(1, first.minusDays(1).toString(), 99)
            repo.confirmDay(1, firstDate, true, true, "no_stool")
            repo.confirmDay(1, lastDate, true, true, "diarrhea")
            val options = DiaryExportOptions(first, last, DiarySection.entries.toSet(), true, false, "Пользовательский запрос")
            val (history, water) = repo.analytics.readExportData(options)
            val data = DiaryExportBuilder.build(history, water, options)
            val json = render(data, DiaryExportFormat.JSON)
            val markdown = render(data, DiaryExportFormat.MARKDOWN)
            val root = JsonValues.readObject(json)
            assertEquals("nutrition-diary-analysis", root["format"])
            assertEquals("Пользовательский запрос", root["analysis_request"])
            assertEquals(listOf(DiarySection.FOOD, DiarySection.MEDICINE), data.events.take(2).map { it.section })
            assertEquals(1, data.events.count { it.section == DiarySection.WATER })
            for (value in listOf("Чеснок", "Хлеб от Павла", "Секретный магазин", "Секретная марка", "1.5",
                "Europe/Moscow", "полтаблетки", "Комментарий компонента", "Комментарий сна")) {
                assertTrue(value, json.contains(value) && markdown.contains(value))
            }
            assertFalse(json.contains("Нынешнее имя"))
            val analytics = root["analytics"] as Map<*, *>
            val days = analytics["source_days"] as List<*>
            val ids = (days.single() as Map<*, *>)["event_ids"] as List<*>
            assertTrue(data.events.map { it.id }.containsAll(ids))
            assertTrue(ids.contains(history.stools.single().publicId))
            assertEquals(lastDate, analytics["last_outcome_date"])
            assertTrue(runCatching { BackupCodec.decode(json.toByteArray()) }.isFailure)

            val masked = DiaryExportBuilder.build(history, water, options.copy(hideNames = true, includeComments = false))
            for (format in DiaryExportFormat.entries) {
                val contents = render(masked, format)
                listOf("Павла", "Секретн", "Чеснок", "полтаблетки", "Комментарий компонента", "Комментарий сна")
                    .forEach { assertFalse(it, contents.contains(it)) }
                assertTrue(contents.contains(history.meals.single().event.publicId))
            }
            val limited = DiaryExportBuilder.build(history, water, options.copy(
                sections = setOf(DiarySection.FOOD), includeComments = false, prompt = "",
            ))
            val limitedJson = JsonValues.readObject(render(limited, DiaryExportFormat.JSON))
            assertFalse(limitedJson.containsKey("analytics"))
            assertFalse(limitedJson.containsKey("analysis_request"))
            assertEquals(emptyMap<String, Any?>(), limitedJson["scale_dictionary"])
            val items = ((limitedJson["events"] as List<*>).single() as Map<*, *>)["items"] as List<*>
            assertFalse((items.single() as Map<*, *>).containsKey("composition"))
        } finally {
            db.close()
        }
    }

    @Test fun legacyUnknownFieldsAndCommentTextRemainDistinguishableInBothFormats() {
        val original = "Примечание\n# заголовок `s` и <img src=x>"
        val history = AnalysisHistory(
            stools = listOf(StoolEntity(userId = 1, date = firstDate, quality = 0, comment = original)),
            sleeps = listOf(SleepEntity(userId = 1, date = firstDate, bedTime = "23:10", wakeupTime = "07:00")),
        )
        val options = DiaryExportOptions(first, first, setOf(DiarySection.STOOL, DiarySection.SLEEP), true, false, "")
        val data = DiaryExportBuilder.build(history, emptyList(), options)
        val decoded = JsonValues.readObject(render(data, DiaryExportFormat.JSON))
        val stool = (decoded["events"] as List<*>).first() as Map<*, *>
        assertEquals(original, stool["comment"])
        assertTrue(stool.containsKey("bristol"))
        assertNull(stool["bristol"])
        assertNull(stool["local_time"])
        assertNull(data.events.last().fields["bed_date"])
        val markdown = render(data, DiaryExportFormat.MARKDOWN)
        assertTrue(markdown.contains("\n#### Стул · время неизвестно\n"))
        assertFalse(markdown.contains("\n# заголовок"))
        assertTrue(markdown.contains("Примечание\\n# заголовок"))
    }
}
