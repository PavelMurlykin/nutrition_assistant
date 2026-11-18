package com.pamurlykin.nutritionassistant

import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import java.util.UUID

class ExplainableAnalyzerTest {
    private fun uuid(name:String)=UUID.nameUUIDFromBytes(name.toByteArray()).toString()
    private fun node(name:String,children:List<CompositionNode> = emptyList(),revision:Int=1,known:Boolean=true)=CompositionNode(uuid(name),name,"product",revision,
        CatalogDetails(categoryCode="vegetables",composition=if(known) "confirmed" else "unknown",source=if(known) "Этикетка" else null,checkedOn=if(known) "2026-01-01" else null),components=children)
    private val garlic get()=node("Чеснок")
    private fun meal(date:String,root:CompositionNode,time:String?=null,offset:String?=null,id:Long=1)=MealWithItems(
        MealEntity(id=id,userId=1,date=date,mealType="dinner",description=root.name,actualTime=time,utcOffset=offset,publicId=uuid("event$date$id"),comment="Комментарий еды"),
        listOf(MealItemEntity(mealId=id,catalogId=1,name=root.name,foodType="",ingredients=root.ingredientNames(),kind="product",compositionSnapshot=CompositionSnapshot.encode(root),sourceRevision=root.revision,quantity="150",unit="g",comment="Порция",publicId=uuid("item$date$id"))))
    private fun sample(known:Boolean=true,affectedWith:Boolean=true):AnalysisHistory {
        val start=LocalDate.of(2026,11,10)
        val meals=(0..5).map { i->meal(start.plusDays(i.toLong()).minusDays(1).toString(),if(i<3) node("Фарш",listOf(garlic)) else node("Рис",known=known)) }
        val statuses=(-1..5).map { i->val date=start.plusDays(i.toLong()).toString();DayStatusEntity(userId=1,date=date,foodComplete=true,symptomsComplete=true,
            stoolSummary=if(i in 0..2 && affectedWith) "diarrhea" else if(i in 3..5 && !affectedWith) "diarrhea" else "no_stool") }
        val stools=(0..5).filter { if(affectedWith) it<3 else it>=3 }.flatMap { i->listOf(6,7).map { quality->StoolEntity(id=(i*10+quality).toLong(),userId=1,date=start.plusDays(i.toLong()).toString(),quality=quality,publicId=uuid("stool$i$quality")) } }
        return AnalysisHistory(meals=meals,statuses=statuses,stools=stools)
    }
    private fun garlicRow(history:AnalysisHistory)=ExplainableAnalyzer.analyze(history,"2026-11-16",6).observations.single {it.factor.publicId==uuid("Чеснок")}
    @Test fun repeatedAssociationUsesCompleteDaysAndCountsEachDayOnce() {
        val history=sample();val report=ExplainableAnalyzer.analyze(history,"2026-11-16",6);val row=garlicRow(history)
        assertEquals(3,row.withFactor.size);assertEquals(3,row.withoutFactor.size);assertEquals(3,row.affectedWith);assertEquals(0,row.affectedWithout)
        assertEquals("Повторяющееся совпадение",row.label);assertEquals(3,report.coverage.looseDays);assertEquals(6,report.coverage.looseEpisodes)
        assertTrue(row.withFactor.first().factorSources.any {it.title.contains("Фарш → Чеснок")});assertTrue(row.coFactors.any {it.contains("Фарш")})
        assertTrue(row.withFactor.first().sources.any {it.details.contains("Порция: 150 г") && it.comment!!.contains("Порция")})
    }
    @Test fun unknownCompositionAndMissingFoodAreNotUnexposedControls() {
        val uncertain=garlicRow(sample(known=false));assertEquals(0,uncertain.withoutFactor.size);assertEquals("Недостаточно данных",uncertain.label)
        val h=sample();val incomplete=h.copy(statuses=h.statuses.map {it.copy(foodComplete=false)})
        val report=ExplainableAnalyzer.analyze(incomplete,"2026-11-16",6);assertEquals(0,report.coverage.paired);assertTrue(report.observations.all {it.withFactor.isEmpty() && it.withoutFactor.isEmpty()})
    }
    @Test fun lowFrequencyAndSmallSamplesReceiveNoConfidentRating() {
        assertEquals("Частота с фактором не выше",garlicRow(sample(affectedWith=false)).label)
        val h=sample();val small=h.copy(statuses=h.statuses.filter {it.date !in setOf("2026-11-10","2026-11-14")})
        assertEquals("Недостаточно данных",garlicRow(small).label)
    }
    @Test fun currentAndFutureEventsCannotChangeHistoricalComparisonsOrRecipes() {
        val h=sample();val before=garlicRow(h)
        val future=h.copy(meals=h.meals+meal("2026-11-16",node("Фарш",revision=2))+meal("2026-11-17",node("Фарш",listOf(garlic),revision=3)),
            stools=h.stools+StoolEntity(userId=1,date="2026-11-17",quality=7))
        assertEquals(before,garlicRow(future))
        val changed=h.copy(meals=h.meals.mapIndexed { index,row->if(index>=3) meal(row.event.date,node("Фарш",revision=2)) else row })
        val report=ExplainableAnalyzer.analyze(changed,"2026-11-16",6)
        assertTrue(report.observations.any {it.factor.key=="entry:${uuid("Фарш")}:r1"});assertTrue(report.observations.any {it.factor.key=="entry:${uuid("Фарш")}:r2"})
        assertEquals(3,garlicRow(changed).withoutFactor.size)
    }
    @Test fun hourlyWindowsUseOffsetsExcludeLaterAndEqualEventsAndSeparateUnknownTime() {
        val date="2026-11-18";val episode=StoolEntity(userId=1,date=date,quality=7,actualTime="02:00",utcOffset="+03:00",publicId=uuid("case"))
        val foods=listOf(meal("2026-11-17",node("6 часов"),"20:00","+03:00",1),meal("2026-11-17",node("24 часа"),"02:00","+03:00",2),
            meal(date,node("Потом"),"02:30","+03:00",3),meal(date,node("Одновременно"),"02:00","+03:00",4),meal(date,node("Без времени"),id=5),meal("2026-11-19",node("Будущее"),"00:01","+18:00",6))
        val medication=MedicineWithItems(MedicineEntity(id=1,userId=1,date=date,name="Позднее лекарство",dosage=null,actualTime="03:00",utcOffset="+03:00"),listOf(MedicineItemEntity(medicineId=1,catalogId=7,name="Позднее лекарство",dosage="1 таблетка")))
        val h=AnalysisHistory(meals=foods,medicines=listOf(medication),stools=listOf(episode))
        val six=ExplainableAnalyzer.episode(h,"stool:${episode.publicId}",AnalysisWindow.SIX)
        assertTrue(six.preceding.any {it.title.contains("6 часов")});assertFalse(six.preceding.any {it.title.contains("24 часа") || it.title.contains("Позднее") || it.title.contains("Потом") || it.title.contains("Будущее")})
        assertTrue(six.unknownTime.any {it.title.contains("Без времени")});assertEquals(4,six.excludedLaterOrEqual)
        val day=ExplainableAnalyzer.episode(h,"stool:${episode.publicId}",AnalysisWindow.DAY);assertTrue(day.preceding.any {it.title.contains("24 часа")});assertFalse(day.preceding.any {it.title.contains("6 часов")})
        assertTrue(AnalysisWindow.TWO_DAYS.contains(Instant.parse("2026-11-16T00:00:00Z"),Instant.parse("2026-11-18T00:00:00Z")))
    }
    @Test fun dayRatingsLegacyNightsAndUnknownIllnessNeverInventPrecedenceOrAbsence() {
        val h=sample();val health=(0..5).map { i->HealthEntryEntity(id=i.toLong(),userId=1,date=LocalDate.of(2026,11,10).plusDays(i.toLong()).toString(),metric="stress",valueCode=if(i<3) "4" else "0",valueLabel=if(i<3) "Очень сильный" else "Отсутствовал",scaleVersion=1,period="day",createdAt="2026-11-19T23:00:00") }
        val report=ExplainableAnalyzer.analyze(h.copy(health=health,sleeps=listOf(SleepEntity(userId=1,date="2026-11-10",bedTime="23:00",wakeupTime="07:00",qualityRating=4,scaleVersion=1))),"2026-11-16",6)
        assertEquals(1,report.coverage.unknownNight);assertFalse(report.observations.any {it.factor.group==FactorGroup.SLEEP})
        assertEquals(3,report.observations.single {it.factor.metric=="stress" && it.factor.title.contains("Очень")}.withoutFactor.size)
        val episode=h.stools.first().copy(actualTime="08:00",utcOffset="+03:00")
        val context=ExplainableAnalyzer.episode(h.copy(health=health,stools=listOf(episode)),"stool:${episode.publicId}",AnalysisWindow.SIX)
        assertFalse(context.preceding.any {it.key.startsWith("health:")});assertTrue(context.calendarContext.any {it.title.contains("Стресс") && it.details.any {text->text.contains("порядок")}})
        val illnesses=health.mapIndexed {i,row->row.copy(metric="illness",valueCode=if(i<3) "present" else "unknown",valueLabel=if(i<3) "Есть признаки" else "Не знаю")}
        val illnessReport=ExplainableAnalyzer.analyze(h.copy(health=illnesses),"2026-11-16",6);assertEquals(0,illnessReport.observations.single {it.factor.metric=="illness"}.withoutFactor.size)
    }
    @Test fun explicitNightAndKnownZeroStressAreComparedButMissingRatingsAreExcluded() {
        val h=sample();val nights=(0..5).map { i->SleepEntity(userId=1,date=LocalDate.of(2026,11,10).plusDays(i.toLong()).toString(),qualityRating=if(i<3) 1 else 5,qualityLabel=if(i<3) "Очень плохое" else "Очень хорошее",scaleVersion=1) }
        val report=ExplainableAnalyzer.analyze(h.copy(sleeps=nights),"2026-11-16",6)
        assertEquals(0,report.coverage.unknownNight);assertEquals(3,report.observations.single {it.factor.key=="sleep:1:v1"}.withoutFactor.size)
        assertEquals("Недостаточно данных",FactorObservation(AnalysisFactor("x","x",FactorGroup.ENTRY),emptyList(),emptyList(),6).label)
    }

    @Test fun portionComparisonsExcludeUnknownAmountsAndDoNotAddDifferentPortions() {
        val h=sample();val rows=h.meals.mapIndexed {index,row ->
            val base=meal(row.event.date,node("Фарш",listOf(garlic)))
            base.copy(items=base.items.map {it.copy(quantity=if(index<3) "150" else null,unit=if(index<3) "g" else null)})
        }
        val unknown=ExplainableAnalyzer.analyze(h.copy(meals=rows),"2026-11-16",6).observations.single {it.factor.amountOf=="entry:${uuid("Фарш")}:r1"}
        assertEquals(3,unknown.withFactor.size);assertEquals(0,unknown.withoutFactor.size)
        val known=ExplainableAnalyzer.analyze(h.copy(meals=rows.mapIndexed {index,row ->if(index<3) row else row.copy(items=row.items.map {it.copy(quantity="200",unit="g")})}),"2026-11-16",6)
        val compared=known.observations.single {it.factor.amountOf=="entry:${uuid("Фарш")}:r1" && it.factor.title.contains("150")}
        assertEquals(3,compared.withoutFactor.size);assertEquals("Повторяющееся совпадение",compared.label)
    }
    @Test fun medicinePresenceAndExactDosesUseIndependentKnownness() {
        val h=sample();val meds=h.meals.mapIndexed {index,row ->MedicineWithItems(MedicineEntity(userId=1,date=row.event.date,name="Препарат",dosage=null),
            listOf(MedicineItemEntity(medicineId=0,catalogId=7,name="Препарат",dosage=if(index<3) "10 мг" else null))) }
        val unknown=ExplainableAnalyzer.analyze(h.copy(medicines=meds),"2026-11-16",6).observations.single {it.factor.amountOf=="medicine:7"}
        assertEquals(3,unknown.withFactor.size);assertEquals(0,unknown.withoutFactor.size)
        val known=ExplainableAnalyzer.analyze(h.copy(medicines=meds.mapIndexed {index,row ->if(index<3) row else row.copy(items=row.items.map {it.copy(dosage="5 мг")})}),"2026-11-16",6)
        assertEquals(3,known.observations.single {it.factor.amountOf=="medicine:7" && it.factor.title.contains("10 мг")}.withoutFactor.size)
        assertEquals(6,known.observations.single {it.factor.key=="medicine:7"}.withFactor.size)
    }

    @Test fun exactWindowsUseUtcAcrossOppositeOffsetsAndOriginalLocalDates() {
        val episode=StoolEntity(userId=1,date="2026-11-18",quality=7,actualTime="23:00",utcOffset="-12:00",publicId=uuid("travel-case"))
        val earlier=meal("2026-11-19",node("Ранее по UTC"),"23:00","+14:00")
        val later=meal("2026-11-18",node("Позднее по UTC"),"23:59","-18:00",2)
        val context=ExplainableAnalyzer.episode(AnalysisHistory(meals=listOf(earlier,later),stools=listOf(episode)),"stool:${episode.publicId}",AnalysisWindow.SIX)
        assertTrue(context.preceding.any {it.title.contains("Ранее по UTC") && it.date=="2026-11-19"})
        assertFalse(context.preceding.any {it.title.contains("Позднее по UTC")})
    }
}
