package com.pamurlykin.nutritionassistant

import com.pamurlykin.nutritionassistant.data.local.HealthEntryEntity
import com.pamurlykin.nutritionassistant.data.model.*
import org.junit.Test
import org.junit.Assert.*

class HealthRulesTest {
    private val day = "2026-11-17"
    private fun rejected(block: () -> Unit) { try { block(); fail("Invalid observation accepted") } catch (_: IllegalArgumentException) {} }
    @Test fun missingIsDifferentFromExplicitZeroNoneAndUnknown() {
        rejected { HealthDraft("stress",null).validated(day) }
        assertEquals("0",HealthDraft("stress","0").validated(day).value)
        assertEquals("none",HealthDraft("activity","none").validated(day).value)
        assertEquals("unknown",HealthDraft("illness","unknown").validated(day).value)
        rejected { HealthDraft("stress","5").validated(day) }
        rejected { HealthDraft("wellbeing","0").validated(day) }
    }
    @Test fun dependentFieldsCannotContradictMainAnswer() {
        rejected { HealthDraft("activity","none",durationMinutes=5).validated(day) }
        rejected { HealthDraft("activity","moderate",durationMinutes=0).validated(day) }
        rejected { HealthDraft("stress","1",activityKind="walking").validated(day) }
        rejected { HealthDraft("illness","unknown",illnessImpact="mild").validated(day) }
        rejected { HealthDraft("illness","none",symptomCodes=listOf("other")).validated(day) }
        assertEquals(listOf("digestive","fever"),HealthDraft("illness","present",symptomCodes=listOf("fever","digestive")).validated(day).symptomCodes)
    }
    @Test fun dailyAndMomentTimesHaveDifferentMeaning() {
        rejected { HealthDraft("stress","1",context=EventContext(RecordedTime("10:00","+03:00"))).validated(day) }
        assertEquals("10:00",HealthDraft("stress","1","moment",context=EventContext(RecordedTime("10:00","+03:00"))).validated(day).context.recordedTime.time)
        rejected { RecordedTime("02:30","+01:00","Europe/Berlin").validated("2026-03-29") }
        assertEquals("+02:00",RecordedTime("02:30","+02:00","Europe/Berlin").validated("2026-10-25").utcOffset)
        assertEquals("+01:00",RecordedTime("02:30","+01:00","Europe/Berlin").validated("2026-10-25").utcOffset)
    }
    @Test fun sleepQualityDoesNotInventTimesAndKnownZeroAwakeningsIsPreserved() {
        val quality=SleepDraft(4,awakenings=0).validated(day)
        assertNull(quality.bedTime); assertNull(quality.bedDate); assertEquals(0,quality.awakenings)
        val legacy=SleepDraft(comment="Прежний текст",bedTime="23:00",wakeupTime="07:00").validated(day)
        assertNull(legacy.quality); assertNull(legacy.bedDate)
        rejected { SleepDraft(quality=6).validated(day) }
        rejected { SleepDraft(quality=4,bedTime="23:00").validated(day) }
        rejected { SleepDraft(quality=4,bedTime="23:00",wakeupTime="07:00",bedDate=day,wakeupDate=day).validated(day) }
    }
    @Test fun activitySummaryUsesOnlyMomentsAndDoesNotReplaceDailyRating() {
        val rows=listOf(
            HealthEntryEntity(userId=1,date=day,metric="activity",valueCode="light",period="day",durationMinutes=120),
            HealthEntryEntity(userId=1,date=day,metric="activity",valueCode="moderate",durationMinutes=20),
            HealthEntryEntity(userId=1,date=day,metric="activity",valueCode="vigorous"),
            HealthEntryEntity(userId=1,date=day,metric="activity",valueCode="none"))
        assertEquals(ActivitySummary("vigorous",20,1),activitySummary(rows))
        assertEquals(ActivitySummary(null,0,0),activitySummary(emptyList()))
    }
    @Test fun legacyNoteCanBeRatedWithoutLosingUnchangedLongComment() {
        val text="Старая запись ".repeat(100)
        rejected { HealthDraft("wellbeing","3","moment",context=EventContext(comment=text)).validated(day) }
        assertEquals(text,HealthDraft("wellbeing","3","moment",context=EventContext(comment=text)).validated(day,preserveLongComment=true).context.comment)
        rejected { HealthDraft("stress",null,"moment",context=EventContext(comment="Текст")).validated(day,allowLegacy=true) }
    }
}
