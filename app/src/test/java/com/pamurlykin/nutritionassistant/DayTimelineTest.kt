package com.pamurlykin.nutritionassistant

import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import org.junit.Assert.*
import org.junit.Test

class DayTimelineTest {
    private val day="2026-11-17"
    private fun snapshot(meals:List<MealWithItems> = emptyList(),medicines:List<MedicineWithItems> = emptyList(),stools:List<StoolEntity> = emptyList(),health:List<HealthEntryEntity> = emptyList(),sleep:SleepEntity? = null)=
        DailyStatsSnapshot(day,UserEntity(1),meals,medicines,stools,emptyList(),0,sleep,StoolAnalyzer.analyze(day,meals,stools),healthEntries=health)
    @Test fun preciseEventsUseOffsetsAndNeverCreationTimeOrDateOnlyGuesses() {
        val meal=MealWithItems(MealEntity(id=1,userId=1,date=day,mealType="breakfast",description="",actualTime="10:00",utcOffset="+03:00",createdAt="2026-11-18T23:00:00"),emptyList())
        val medicine=MedicineWithItems(MedicineEntity(id=2,userId=1,date=day,name="Препарат",dosage=null,actualTime="09:00",utcOffset="+01:00",createdAt="2026-11-16T01:00:00"),emptyList())
        val stools=listOf(StoolEntity(id=3,userId=1,date=day,quality=7,actualTime="10:30",utcOffset="+03:00"),StoolEntity(id=4,userId=1,date=day,quality=4,createdAt="2026-11-17T00:01:00"))
        val rows=buildDayTimeline(snapshot(listOf(meal),listOf(medicine),stools))
        assertEquals(listOf("meal-1","stool-3","medicine-2"),rows.timed.map { it.key })
        assertEquals(listOf("stool-4"),rows.unknownTime.map { it.key });assertTrue(rows.wholePeriod.isEmpty())
    }
    @Test fun wholeDayAndNightAreSeparateFromMomentsAndLegacyUnratedNotes() {
        val rows=buildDayTimeline(snapshot(health=listOf(
            HealthEntryEntity(id=1,userId=1,date=day,metric="stress",valueCode="0",valueLabel="Отсутствовал",period="day",comment="За день"),
            HealthEntryEntity(id=2,userId=1,date=day,comment="Старая запись"),
            HealthEntryEntity(id=3,userId=1,date=day,metric="activity",valueCode="moderate",valueLabel="Умеренная",durationMinutes=20,activityKind="walking",actualTime="11:00",utcOffset="+03:00")),sleep=SleepEntity(userId=1,date=day,qualityRating=4,qualityLabel="Хорошее")))
        assertEquals(listOf("health-3"),rows.timed.map { it.key });assertEquals(listOf("health-2"),rows.unknownTime.map { it.key })
        assertEquals(listOf("health-1","sleep"),rows.wholePeriod.map { it.key });assertEquals("Старая запись",rows.unknownTime.single().comment)
        assertTrue(rows.unknownTime.single().title.contains("Без оценки"));assertTrue(rows.timed.single().details.contains("20 мин"))
    }
    @Test fun equalTimesHaveNoInferredCausalOrderAndEmptyDayHasNoNormalEvent() {
        val a=TimelineEntry("a","A",emptyList(),null,"02:30","+02:00")
        val b=TimelineEntry("b","B",emptyList(),null,"01:30","+01:00")
        assertEquals(a.instant("2026-10-25"),b.instant("2026-10-25"))
        val empty=buildDayTimeline(snapshot());assertTrue(empty.timed.isEmpty());assertTrue(empty.unknownTime.isEmpty());assertTrue(empty.wholePeriod.isEmpty())
    }
}
