package com.pamurlykin.nutritionassistant

import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.util.validateStoolQuality
import org.junit.Assert.*
import org.junit.Test

class StoolAnalyzerTest {
    private fun meal(date: String, name: String, ingredients: List<String> = emptyList(), type: String = "home") =
        MealWithItems(MealEntity(userId = 1, date = date, mealType = "snack", description = name),
            listOf(MealItemEntity(mealId = 1, catalogId = 1, name = name, foodType = type, ingredients = ingredients)))
    private fun stool(date: String, quality: Int) = StoolEntity(userId = 1, date = date, quality = quality)

    @Test fun previousCalendarDayIncludesSnacksAndIngredientsOnly() {
        val yesterday = meal("2026-10-17", "Каша", listOf("Молоко"))
        val report = StoolAnalyzer.analyze("2026-10-18", listOf(meal("2026-10-16", "Суп"), yesterday,
            meal("2026-10-18", "Пицца")), listOf(stool("2026-10-18", 7)))
        assertEquals(listOf(yesterday), report.previousMeals)
        val factors = report.conditions.single().factors.map { it.factor }
        assertTrue(factors.any { it == "Ингредиент: молоко" })
        assertFalse(factors.any { "пицца" in it || "суп" in it })
        assertTrue(report.conditions.single().factors.none { it.repeated })
    }

    @Test fun repeatedAssociationUsesExposedAndUnexposedObservedDays() {
        val report = StoolAnalyzer.analyze("2026-10-18", listOf(
            meal("2026-10-13", "Суп"), meal("2026-10-14", "Суп"),
            meal("2026-10-15", "Каша", listOf("Молоко")), meal("2026-10-17", "Каша", listOf("молоко", "Молоко"))),
            listOf(stool("2026-10-14", 4), stool("2026-10-15", 3), stool("2026-10-16", 6),
                stool("2026-10-18", 7), stool("2026-10-18", 6)))
        val milk = report.conditions.single().factors.single { it.factor == "Ингредиент: молоко" }
        assertTrue(milk.repeated)
        assertEquals(2, milk.daysWith)
        assertEquals(2, milk.affectedWith)
        assertEquals(2, milk.daysWithout)
        assertEquals(0, milk.affectedWithout)
        assertEquals(2, report.diarrheaCount)
    }

    @Test fun missingAndUnknownDaysAreNotHealthyControlsAndFutureDoesNotLeak() {
        val report = StoolAnalyzer.analyze("2026-10-18", listOf(meal("2026-10-12", "Суп"), meal("2026-10-14", "Суп"),
            meal("2026-10-17", "Каша"), meal("2026-10-18", "Каша")),
            listOf(stool("2026-10-15", 0), stool("2026-10-16", 4), stool("2026-10-18", 1), stool("2026-10-19", 1)))
        report.conditions.single().factors.forEach {
            assertEquals(1, it.daysWith)
            assertEquals(0, it.daysWithout)
            assertFalse(it.repeated)
        }
    }

    @Test fun equallyCommonSymptomsDoNotSuggestAssociation() {
        val meals = listOf(meal("2026-10-13", "Суп"), meal("2026-10-14", "Суп"),
            meal("2026-10-15", "Каша"), meal("2026-10-17", "Каша"))
        val stools = listOf(stool("2026-10-14", 6), stool("2026-10-15", 6), stool("2026-10-16", 6), stool("2026-10-18", 6))
        assertTrue(StoolAnalyzer.analyze("2026-10-18", meals, stools).conditions.single().factors.none { it.repeated })
    }

    @Test fun constipationAndDiarrheaAreAnalyzedSeparatelyAndMissingMealsAreExplicit() {
        val report = StoolAnalyzer.analyze("2026-10-18", emptyList(), listOf(stool("2026-10-18", 1), stool("2026-10-18", 7)))
        assertEquals(2, report.conditions.size)
        assertTrue(report.previousMeals.isEmpty())
        assertTrue(report.conditions.all { it.factors.isEmpty() })
        assertEquals(StoolCondition.SOFT, StoolCondition.fromQuality(5))
        assertEquals(StoolCondition.NORMAL, StoolCondition.fromQuality(4))
        assertEquals(StoolCondition.UNKNOWN, StoolCondition.fromQuality(0))
    }

    @Test fun unicodePartialSearchNormalizesCaseWhitespaceAndYo() {
        val entry = CatalogEntryEntity(kind = "dish", name = "  Печёная   картошка ", normalizedName = "печеная картошка")
        assertTrue(catalogMatches(entry, "ЧЕНАЯ КАРТ"))
        assertFalse(catalogMatches(entry, "каша"))
        assertEquals("печеная картошка", normalizeName(entry.name))
    }

    @Test(expected = IllegalArgumentException::class) fun newStoolCannotUseLegacyZero() { validateStoolQuality(0) }
}
