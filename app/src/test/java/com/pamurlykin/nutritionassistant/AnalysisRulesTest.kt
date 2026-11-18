package com.pamurlykin.nutritionassistant

import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import java.time.Instant
import org.junit.Test
import org.junit.Assert.*

class AnalysisRulesTest {
    @Test fun hourlyWindowsHaveNonOverlappingBoundariesAndNoEqualOrFutureTimes() {
        val end=Instant.parse("2026-11-18T00:00:00Z")
        assertFalse(AnalysisWindow.SIX.contains(end,end));assertFalse(AnalysisWindow.SIX.contains(end.plusSeconds(1),end))
        listOf(1L,21600L,21601L,86400L,86401L,172800L).forEach { seconds ->
            assertEquals(1,AnalysisWindow.entries.count {it.contains(end.minusSeconds(seconds),end)})
        }
        assertFalse(AnalysisWindow.entries.any {it.contains(end.minusSeconds(172801),end)})
    }
    @Test fun clockOffsetsDetermineInstantAndUnknownTimeStaysUnknown() {
        val a=AnalysisSource("a","2026-10-25","A",actualTime="02:30",utcOffset="+02:00")
        val b=a.copy(actualTime="01:30",utcOffset="+01:00")
        assertEquals(a.instant(),b.instant());assertNull(a.copy(actualTime=null,utcOffset=null).instant())
    }
    private fun days(count:Int,affected:Int)=List(count) {i->AnalysisDay("2026-11-${i+1}",i<affected,emptyList(),emptyList(),false,null)}
    @Test fun minimumGroupsAndRepeatRuleAreExplicitAndNeverDivideByZero() {
        val factor=AnalysisFactor("a","A",FactorGroup.ENTRY)
        assertEquals("Недостаточно данных",FactorObservation(factor,emptyList(),emptyList(),10).label)
        assertEquals("Недостаточно данных",FactorObservation(factor,days(2,2),days(3,0),0).label)
        assertEquals("Повторяющееся совпадение",FactorObservation(factor,days(3,2),days(3,0),0).label)
        assertEquals("Частота с фактором не выше",FactorObservation(factor,days(3,2),days(3,2),0).label)
        assertEquals("Недостаточно повторений",FactorObservation(factor,days(3,1),days(3,0),0).label)
    }
    @Test fun absentDiaryAndUnknownIllnessDoNotGenerateFactorsOrHealthyDays() {
        val report=ExplainableAnalyzer.analyze(AnalysisHistory(),"2026-11-18",30)
        assertEquals(0,report.coverage.paired);assertEquals(0,report.coverage.symptomsComplete);assertTrue(report.observations.isEmpty());assertEquals("2026-11-17",report.end)
        val history=AnalysisHistory(health=listOf(HealthEntryEntity(userId=1,date="2026-11-17",metric="illness",valueCode="unknown",scaleVersion=1,period="day")))
        assertTrue(ExplainableAnalyzer.analyze(history,"2026-11-18",1).observations.isEmpty())
    }
    @Test fun firstSupportedDateHasNoPriorDaysAndRetainsRequestedPeriod() {
        val report=ExplainableAnalyzer.analyze(AnalysisHistory(),"0001-01-01",90)
        assertEquals(90,report.requestedDays);assertEquals(0,report.coverage.days)
        assertEquals("0001-01-01",report.start);assertEquals("0001-01-01",report.end)
        assertTrue(report.observations.isEmpty())
    }
    @Test fun illnessContextDistinguishesMissingOrMomentAbsenceFromWholeDayAbsence() {
        val statuses=(15..17).map { DayStatusEntity(userId=1,date="2026-11-$it",foodComplete=true,symptomsComplete=true,stoolSummary="no_stool") }
        val ratings=(16..17).map { HealthEntryEntity(userId=1,date="2026-11-$it",metric="stress",valueCode="3",valueLabel="Сильный",scaleVersion=1,period="day") }
        val moment=HealthEntryEntity(userId=1,date="2026-11-16",metric="illness",valueCode="none",scaleVersion=1,period="moment")
        val daily=moment.copy(date="2026-11-17",period="day")
        val history=AnalysisHistory(statuses=statuses,health=ratings+moment+daily)
        val row=ExplainableAnalyzer.analyze(history,"2026-11-18",2).observations.single {it.factor.metric=="stress"}
        assertFalse(row.withFactor.first {it.date==moment.date}.illnessKnown)
        assertTrue(row.withFactor.first {it.date==daily.date}.illnessKnown)
        val present=history.copy(health=ratings+moment.copy(valueCode="present")+daily)
        assertTrue(ExplainableAnalyzer.analyze(present,"2026-11-18",2).observations.single {it.factor.metric=="stress"}.withFactor.first {it.date==moment.date}.illness)
    }
    @Test fun episodeOnLastSupportedDateRetainsEarlierEvents() {
        val stool=StoolEntity(userId=1,date="9999-12-31",quality=7,actualTime="12:00",utcOffset="+03:00")
        val health=HealthEntryEntity(userId=1,date=stool.date,metric="stress",valueCode="3",valueLabel="Сильный",scaleVersion=1,period="moment",actualTime="10:00",utcOffset="+03:00")
        val context=ExplainableAnalyzer.episode(AnalysisHistory(stools=listOf(stool),health=listOf(health)),"stool:${stool.publicId}",AnalysisWindow.SIX)
        assertEquals(1,context.preceding.size);assertEquals("9999-12-31",context.preceding.single().date)
    }
}
