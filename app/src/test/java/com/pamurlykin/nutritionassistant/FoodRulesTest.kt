package com.pamurlykin.nutritionassistant

import com.pamurlykin.nutritionassistant.data.model.*
import org.junit.Assert.*
import org.junit.Test

class FoodRulesTest {
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Invalid data accepted") } catch (_: IllegalArgumentException) { } catch (_: java.time.DateTimeException) { }
    }
    @Test fun quantitiesRemainOptionalAndUnitsAreNeverConverted() {
        assertEquals(Portion(), Portion().validated())
        assertEquals(Portion("1.25", "piece"), Portion("01.250", "piece").validated())
        listOf(Portion("0", "g"), Portion("-1", "g"), Portion("1", null), Portion(null, "ml"),
            Portion("1e2", "g"), Portion("1.0000001", "g"), Portion("1", "kg")).forEach { rejected { it.validated() } }
    }
    @Test fun compositionRequiresEvidenceAndKnownCategoryCodes() {
        rejected { CatalogDetails(composition = "confirmed").validated("product") }
        rejected { CatalogDetails(categoryCode = "invented").validated("product") }
        assertEquals("packaging", CatalogDetails(composition = "confirmed", source = " packaging ", checkedOn = "2026-11-17").validated("product").source)
        rejected { CatalogDetails(categoryCode = "meat").validated("dish") }
    }
    @Test fun graphRejectsSelfAndIndirectCyclesAndMissingReferences() {
        rejected { CompositionGraph.validate(mapOf("a" to listOf("a"))) }
        rejected { CompositionGraph.validate(mapOf("a" to listOf("b"), "b" to listOf("a"))) }
        rejected { CompositionGraph.validate(mapOf("a" to listOf("b"))) }
        CompositionGraph.validate(mapOf("dish" to listOf("a", "b"), "a" to listOf("garlic"), "b" to listOf("garlic"), "garlic" to emptyList()))
    }
    @Test fun graphLimitsExpandedSizeAndDepthEvenWhenSharedBranchesAreMemoized() {
        fun chain(size: Int) = (1..size).associate { it.toString() to if (it == size) emptyList() else listOf((it + 1).toString()) }
        CompositionGraph.validate(chain(16))
        rejected { CompositionGraph.validate(chain(17)) }
        val diamond = (1..15).associate { i -> i.toString() to if (i == 15) emptyList() else listOf((i + 1).toString(), (i + 1).toString()) }
        rejected { CompositionGraph.validate(diamond) }
    }
    @Test fun actualTimeRequiresOffsetAndRejectsDstGapOrWrongOffset() {
        assertEquals("date_only", RecordedTime().validated("2026-11-17").precision)
        assertEquals("+03:00", RecordedTime("08:30", "+03", "Europe/Moscow").validated("2026-11-17").utcOffset)
        rejected { RecordedTime("08:30").validated("2026-11-17") }
        rejected { RecordedTime("02:30", "+01:00", "Europe/Berlin").validated("2026-03-29") }
        rejected { RecordedTime("08:30", "+02:00", "Europe/Moscow").validated("2026-11-17") }
        rejected { RecordedTime(null, "+03:00").validated("2026-11-17") }
    }
    @Test fun sleepPeriodIdentifiesTheNightWithoutClaimingSleepDuration() {
        SleepPeriod("2026-11-16", "2026-11-17").validate("23:00", "07:00")
        rejected { SleepPeriod("2026-11-17", "2026-11-17").validate("23:00", "07:00") }
        rejected { validateDay("2026-02-30") }
    }
}
