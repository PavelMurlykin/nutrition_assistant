package com.pamurlykin.nutritionassistant.data.model

import com.pamurlykin.nutritionassistant.data.local.*
import java.time.*

const val ANALYSIS_RULE_VERSION = 1
const val MIN_COMPARISON_GROUP = 3

data class AnalysisHistory(val meals: List<MealWithItems> = emptyList(), val medicines: List<MedicineWithItems> = emptyList(),
    val stools: List<StoolEntity> = emptyList(), val health: List<HealthEntryEntity> = emptyList(), val sleeps: List<SleepEntity> = emptyList(),
    val statuses: List<DayStatusEntity> = emptyList(), val targets: List<TrackingTargetWithEntry> = emptyList())
enum class FactorGroup(val title: String) { ENTRY("Блюда и продукты"), INGREDIENT("Вложенные компоненты"), MEDICINE("Лекарства и дозировки"), HEALTH("Дневные оценки"), SLEEP("Сон за ночь") }
enum class AnalysisWindow(val fromHours: Int, val toHours: Int, val title: String) {
    SIX(0,6,"0–6 ч"), DAY(6,24,"6–24 ч"), TWO_DAYS(24,48,"24–48 ч");
    fun contains(before: Instant, episode: Instant): Boolean {
        val delay = Duration.between(before,episode)
        return delay > Duration.ofHours(fromHours.toLong()) && delay <= Duration.ofHours(toHours.toLong())
    }
}
data class AnalysisSource(val key: String, val date: String, val title: String, val details: List<String> = emptyList(), val comment: String? = null,
    val actualTime: String? = null, val utcOffset: String? = null, val timeZone: String? = null) {
    fun instant(): Instant? = if (actualTime != null && utcOffset != null) LocalDateTime.parse(date + "T" + actualTime).toInstant(ZoneOffset.of(utcOffset)) else null
}
data class AnalysisFactor(val key: String, val title: String, val group: FactorGroup, val category: String? = null,
    val publicId: String? = null, val catalogId: Long? = null, val scope: String? = null, val metric: String? = null, val amountOf: String? = null, val amountDimension: String? = null)
data class AnalysisDay(val date: String, val affected: Boolean, val sources: List<AnalysisSource>, val outcomes: List<AnalysisSource>,
    val illness: Boolean, val medicationChanged: Boolean?, val stoolSummary: String? = null, val factorSources: List<AnalysisSource> = emptyList(), val illnessKnown: Boolean = false)
data class FactorObservation(val factor: AnalysisFactor, val withFactor: List<AnalysisDay>, val withoutFactor: List<AnalysisDay>, val excluded: Int,
    val coFactors: List<String> = emptyList(), val coFactorCount: Int = coFactors.size) {
    val affectedWith get() = withFactor.count { it.affected }
    val affectedWithout get() = withoutFactor.count { it.affected }
    val sufficient get() = withFactor.size >= MIN_COMPARISON_GROUP && withoutFactor.size >= MIN_COMPARISON_GROUP
    val higher get() = affectedWith.toLong() * withoutFactor.size > affectedWithout.toLong() * withFactor.size
    val label get() = when { !sufficient -> "Недостаточно данных"; !higher -> "Частота с фактором не выше"; affectedWith < 2 -> "Недостаточно повторений"; else -> "Повторяющееся совпадение" }
}
data class AnalysisCoverage(val days: Int, val foodComplete: Int, val symptomsComplete: Int, val paired: Int,
    val looseDays: Int, val looseEpisodes: Int, val unknownComposition: Int, val missingTime: Int, val unknownNight: Int)
data class ExplainableReport(val selectedDate: String, val start: String, val end: String, val coverage: AnalysisCoverage,
    val observations: List<FactorObservation>, val allSources: List<AnalysisSource>, val episodeSources: List<AnalysisSource>, val requestedDays: Int = coverage.days)
data class EpisodeContext(val episode: AnalysisSource, val window: AnalysisWindow, val preceding: List<AnalysisSource>,
    val unknownTime: List<AnalysisSource>, val calendarContext: List<AnalysisSource>, val excludedLaterOrEqual: Int)

/** Fixed descriptive rules; no probability, diagnosis or causal score is calculated. */
object ExplainableAnalyzer {
    private val firstDate = LocalDate.of(1,1,1)
    private val lastDate = LocalDate.of(9999,12,31)
    private data class Food(val factors: Map<String, Pair<AnalysisFactor,List<AnalysisSource>>>, val sources: List<AnalysisSource>, val fullyKnown: Boolean, val unknown: Int, val unknownAmounts: Set<String> = emptySet())
    private fun comment(vararg values: String?) = values.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }
    private fun food(meals: List<MealWithItems>): Food {
        val values = linkedMapOf<String, Pair<AnalysisFactor,MutableList<AnalysisSource>>>(); val sources = mutableListOf<AnalysisSource>();val unknownAmounts=mutableSetOf<String>();var unknown=0
        fun add(factor: AnalysisFactor, source: AnalysisSource) { values.getOrPut(factor.key) { factor to mutableListOf() }.second.add(source) }
        meals.forEach { meal -> meal.items.forEach { item ->
            val event=meal.event
            val root=item.compositionSnapshot?.let { CompositionSnapshot.decode(it) }
            if (root?.fullyKnown != true) unknown++
            val base=AnalysisSource("meal:${event.publicId}:${item.publicId}",event.date,item.historyDisplayName(),
                listOfNotNull(item.quantity?.let { "Порция: " + quantityTitle(it,item.unit) }, root?.let { "Версия позиции: ${it.revision}" } ?: "Версия состава неизвестна"),
                comment(event.comment,item.comment),event.actualTime,event.utcOffset,event.timeZone)
            sources+=base
            val identity=root?.publicId ?: "legacy:${item.catalogId}:${normalizeName(item.name)}"
            val entry=AnalysisFactor("entry:$identity:r${root?.revision ?: 0}",item.historyDisplayName() + " · " + (root?.let { "версия ${it.revision}" } ?: "версия неизвестна"),FactorGroup.ENTRY,
                root?.details?.categoryCode,root?.publicId,item.catalogId,"entry")
            add(entry,base)
            if(item.quantity==null) unknownAmounts+=entry.key
            else add(entry.copy(key=entry.key+":portion:"+JsonValues.stringify(listOf(item.quantity,item.unit)),title=entry.title+" · отдельная порция "+quantityTitle(item.quantity,item.unit),amountOf=entry.key,amountDimension="portion"),base)
            fun visit(node: CompositionNode,path: List<String>, identityPath: List<String>, rootNode: Boolean) {
                val title=node.name + listOf(node.details.brand,node.details.variant).filter { it.isNotBlank() }.joinToString(" · ").let { if(it.isEmpty()) "" else " · $it" }
                val next=path+title;val nextIds=identityPath+(node.publicId+":r"+node.revision)
                if(node.kind=="product") {
                    val source=base.copy(key=base.key+":"+nextIds.joinToString("/"),title=next.joinToString(" → "),
                        details=base.details+listOfNotNull("Компонент: версия ${node.revision}",if(!rootNode && node.portion.quantity!=null) "Количество в рецепте: "+quantityTitle(node.portion.quantity,node.portion.unit) else null,
                            node.details.source?.let { "Источник состава: $it" },node.details.checkedOn?.let { "Проверен: $it" }),comment=comment(base.comment,node.details.comment))
                    add(AnalysisFactor("ingredient:${node.publicId}:r${node.revision}",title+" · версия ${node.revision}",FactorGroup.INGREDIENT,node.details.categoryCode,node.publicId,if(rootNode) item.catalogId else null,"ingredient"),source)
                }
                node.components.forEach { visit(it,next,nextIds,false) }
                node.legacyIngredients.forEach { name -> add(AnalysisFactor("flat:${normalizeName(name)}",name+" · старый плоский состав",FactorGroup.INGREDIENT),base.copy(title=(next+name).joinToString(" → "))) }
            }
            if(root!=null) visit(root,emptyList(),emptyList(),true) else item.ingredients.forEach { name ->
                add(AnalysisFactor("flat:${normalizeName(name)}",name+" · старый плоский состав",FactorGroup.INGREDIENT),base.copy(title=item.name+" → "+name))
            }
        } }
        return Food(values.mapValues { it.value.first to it.value.second.toList() },sources,unknown==0,unknown,unknownAmounts)
    }
    private fun medicineSources(rows: List<MedicineWithItems>) = rows.flatMap { intake -> intake.items.map { item ->
        AnalysisSource("medicine:${intake.event.publicId}:${item.publicId}",intake.event.date,item.name,
            listOfNotNull(item.dosage?.let { "Доза: $it" },item.quantity?.let { "Количество: "+quantityTitle(it,item.unit) }),comment(intake.event.comment,item.comment),
            intake.event.actualTime,intake.event.utcOffset,intake.event.timeZone)
    } }
    private fun stoolSource(row: StoolEntity)=AnalysisSource("stool:${row.publicId}",row.date,row.qualityLabel ?: StoolCondition.label(row.quality),comment=row.comment,
        actualTime=row.actualTime,utcOffset=row.utcOffset,timeZone=row.timeZone)
    private fun healthSource(row: HealthEntryEntity)=AnalysisSource("health:${row.publicId}",row.date,HealthScales.metrics.getValue(row.metric)+": "+(row.valueLabel ?: "Без оценки"),
        listOfNotNull(if(row.period=="day") "За весь день; порядок относительно эпизода не установлен" else null,row.durationMinutes?.let { "$it мин" },
            row.activityKind?.let { code -> HealthScales.kinds.single { it.code==code }.label },row.illnessImpact?.let { code -> "Влияние: "+HealthScales.impact.single {it.code==code}.label },row.symptomCodes.takeIf { it.isNotEmpty() }?.joinToString(", ") { code -> HealthScales.symptoms.single {it.code==code}.label }),
        row.comment,row.actualTime,row.utcOffset,row.timeZone)
    private fun sleepSource(row: SleepEntity)=AnalysisSource("sleep:${row.publicId}",row.date,"Сон: "+(row.qualityLabel ?: "Без оценки"),
        listOf("Запись о ночи за ${row.date}; порядок относительно эпизода не установлен",
            "Отбой: ${row.bedDate ?: "дата неизвестна"} ${row.bedTime ?: "часы неизвестны"}; подъём: ${row.wakeupDate ?: "дата не уточнена"} ${row.wakeupTime ?: "часы неизвестны"}") + listOfNotNull(row.awakenings?.let { "Пробуждения: $it" }),row.qualityDescription)
    private fun nightKnown(row: SleepEntity)=row.qualityRating!=null && row.scaleVersion==1 &&
        (row.bedTime==null && row.wakeupTime==null || row.bedDate!=null && row.wakeupDate==row.date)
    private fun medicationSignature(rows: List<MedicineWithItems>)=rows.flatMap { it.items }.map { listOf(it.catalogId.toString(),it.name,it.dosage,it.quantity,it.unit) }.sortedBy { JsonValues.stringify(it) }

    fun analyze(history: AnalysisHistory, selectedDate: String, days: Int=90, includeSelectedDay: Boolean = false): ExplainableReport {
        val selected=validateDay(selectedDate); require(days in 1..365)
        // The interactive report excludes the selected day. Exports use a closed, user-selected range.
        val afterEnd = if (includeSelectedDay) selected.plusDays(1) else selected
        val availableDays=minOf(days.toLong(),java.time.temporal.ChronoUnit.DAYS.between(firstDate,afterEnd)).toInt()
        val end=afterEnd.minusDays(1);val start=afterEnd.minusDays(availableDays.toLong()); val earliest=start.minusDays(2).toString()
        val meals=history.meals.filter { it.event.date in earliest..end.toString() }.groupBy { it.event.date }
        val meds=history.medicines.filter { it.event.date in earliest..end.toString() }.groupBy { it.event.date }
        val stools=history.stools.filter { it.date in start.toString()..end.toString() }.groupBy { it.date }
        val health=history.health.filter { it.date in start.toString()..end.toString() }.groupBy { it.date }
        val sleeps=history.sleeps.filter { it.date in start.toString()..end.toString() }.associateBy { it.date }
        val statuses=history.statuses.filter { it.date in earliest..end.toString() }.associateBy { it.date }
        val foods=meals.mapValues { food(it.value) };val emptyFood=Food(emptyMap(),emptyList(),true,0)
        val definitions=linkedMapOf<String,AnalysisFactor>(); val present=linkedMapOf<String,Map<String,List<AnalysisSource>>>()
        val eligible=linkedMapOf<String,Boolean>(); val contexts=linkedMapOf<String,AnalysisDay>(); val metricKnown=hashMapOf<String,Set<String>>()
        var foodComplete=0;var symptomComplete=0
        repeat(availableDays) { index ->
            val date=start.plusDays(index.toLong()).toString();val previous=LocalDate.parse(date).minusDays(1).toString()
            val outcome=statuses[date]; val source=statuses[previous];val food=foods[previous] ?: emptyFood
            if(source?.foodComplete==true) foodComplete++
            if(outcome?.symptomsComplete==true) symptomComplete++
            val loose=stools[date].orEmpty().any { it.quality in 6..7 }
            val validOutcome=outcome?.symptomsComplete==true && when(outcome.stoolSummary) {
                "diarrhea" -> loose; "no_diarrhea" -> !loose; "no_stool" -> stools[date].orEmpty().isEmpty();else -> false
            }
            eligible[date]=source?.foodComplete==true && validOutcome
            val factors=linkedMapOf<String,List<AnalysisSource>>()
            food.factors.forEach { (key,value) -> definitions[key]=value.first;factors[key]=value.second }
            if(source?.symptomsComplete==true) meds[previous].orEmpty().forEach { intake -> intake.items.forEach { item ->
                val baseKey="medicine:${item.catalogId}"
                val sources=medicineSources(listOf(intake)).filter { it.key.endsWith(item.publicId) }
                definitions[baseKey]=AnalysisFactor(baseKey,item.name+" · факт приёма",FactorGroup.MEDICINE,catalogId=item.catalogId)
                factors[baseKey]=factors[baseKey].orEmpty()+sources
                if(item.dosage!=null || item.quantity!=null) {
                    val key=baseKey+":amount:"+JsonValues.stringify(listOf(item.dosage,item.quantity,item.unit))
                    definitions[key]=AnalysisFactor(key,item.name+" · доза "+(item.dosage ?: "не указана")+(item.quantity?.let { " · "+quantityTitle(it,item.unit) } ?: ""),FactorGroup.MEDICINE,catalogId=item.catalogId,amountOf=baseKey,
                        amountDimension=if(item.dosage!=null && item.quantity!=null) "both" else if(item.dosage!=null) "dose" else "quantity")
                    factors[key]=factors[key].orEmpty()+sources
                }
            } }
            val known=mutableSetOf<String>()
            health[date].orEmpty().filter { it.period=="day" && it.valueCode!=null && it.scaleVersion==1 && !(it.metric=="illness" && it.valueCode=="unknown") }.forEach { row ->
                known+=row.metric;val key="health:${row.metric}:${row.valueCode}:v${row.scaleVersion}"
                definitions[key]=AnalysisFactor(key,HealthScales.metrics.getValue(row.metric)+": "+row.valueLabel,FactorGroup.HEALTH,metric=row.metric)
                factors[key]=listOf(healthSource(row))
            }
            sleeps[date]?.takeIf(::nightKnown)?.let { row ->
                known+="sleep";val key="sleep:${row.qualityRating}:v1"
                definitions[key]=AnalysisFactor(key,"Качество ночи: "+row.qualityLabel,FactorGroup.SLEEP,metric="sleep");factors[key]=listOf(sleepSource(row))
            }
            metricKnown[date]=known
            val changed=if(outcome?.symptomsComplete==true && source?.symptomsComplete==true) medicationSignature(meds[date].orEmpty())!=medicationSignature(meds[previous].orEmpty()) else null
            val sources=food.sources+medicineSources(meds[previous].orEmpty())+medicineSources(meds[date].orEmpty())+health[date].orEmpty().map(::healthSource)+listOfNotNull(sleeps[date]?.let(::sleepSource))
            contexts[date]=AnalysisDay(date,loose,sources,stools[date].orEmpty().map(::stoolSource),health[date].orEmpty().any { it.metric=="illness" && it.valueCode=="present" },changed,outcome?.stoolSummary,illnessKnown=health[date].orEmpty().any { it.metric=="illness" && (it.valueCode=="present" || it.period=="day" && it.valueCode=="none") })
            present[date]=factors
        }
        fun known(date:String,factor:AnalysisFactor):Boolean {
            val previous=LocalDate.parse(date).minusDays(1).toString()
            return when(factor.group) {
                FactorGroup.ENTRY -> factor.amountOf==null || factor.key in present.getValue(date) || factor.amountOf !in (foods[previous] ?: emptyFood).unknownAmounts
                FactorGroup.INGREDIENT -> (foods[previous] ?: emptyFood).fullyKnown || factor.key in present.getValue(date)
                FactorGroup.MEDICINE -> statuses[previous]?.symptomsComplete==true && (factor.amountOf==null || factor.key in present.getValue(date) || meds[previous].orEmpty().flatMap { it.items }.filter { it.catalogId==factor.catalogId }.all {
                    (factor.amountDimension !in setOf("dose","both") || it.dosage!=null) && (factor.amountDimension !in setOf("quantity","both") || it.quantity!=null)
                })
                FactorGroup.HEALTH,FactorGroup.SLEEP -> factor.metric in metricKnown.getValue(date)
            }
        }
        val observations=definitions.values.map { factor ->
            val suitable=eligible.keys.filter { eligible[it]==true && known(it,factor) };val with=suitable.filter { factor.key in present.getValue(it) };val without=suitable-with.toSet()
            FactorObservation(factor,with.map { contexts.getValue(it).copy(factorSources=present.getValue(it).getValue(factor.key)) },without.map { contexts.getValue(it) },availableDays-suitable.size)
        }
        val signatures=observations.filter { it.withFactor.size>=2 }.groupBy { it.withFactor.map { day -> day.date } }
        val enriched=observations.map { row ->
            val peers=signatures[row.withFactor.map { it.date }].orEmpty()
            row.copy(coFactors=peers.asSequence().filter { it.factor.key!=row.factor.key }.map { it.factor.group.title+": "+it.factor.title }.distinct().take(8).toList(),coFactorCount=(peers.size-1).coerceAtLeast(0))
        }
        val comparisonMeals=meals.filterKeys { it in start.minusDays(1).toString()..end.minusDays(1).toString() }
        val comparisonMeds=meds.filterKeys { it in start.minusDays(1).toString()..end.toString() }
        val sourceList=comparisonMeals.keys.flatMap { foods.getValue(it).sources }+medicineSources(comparisonMeds.values.flatten())+
            stools.values.flatten().map(::stoolSource)+health.values.flatten().map(::healthSource)+sleeps.values.map(::sleepSource)
        val dailyHealthKeys=history.health.filter { it.period=="day" }.map { "health:${it.publicId}" }.toSet()
        val coverage=AnalysisCoverage(availableDays,foodComplete,symptomComplete,eligible.values.count { it },stools.count { (_,rows)->rows.any { it.quality in 6..7 } },
            stools.values.flatten().count { it.quality in 6..7 },comparisonMeals.keys.sumOf { foods.getValue(it).unknown },
            sourceList.filter { !it.key.startsWith("sleep:") && it.key !in dailyHealthKeys }.distinctBy { it.key.split(":").take(2).joinToString(":") }.count { it.instant()==null },
            sleeps.values.count { !nightKnown(it) })
        return ExplainableReport(selectedDate,start.toString(),end.coerceAtLeast(firstDate).toString(),coverage,enriched.sortedWith(compareBy({it.factor.group.ordinal},{it.factor.title},{it.factor.key})),sourceList,
            history.stools.filter { it.date==selectedDate && it.quality in 6..7 }.map(::stoolSource),requestedDays=days)
    }

    fun episode(history:AnalysisHistory,episodeKey:String,window:AnalysisWindow):EpisodeContext {
        val row=requireNotNull(history.stools.find { "stool:${it.publicId}"==episodeKey && it.quality in 6..7 });val source=stoolSource(row);val date=LocalDate.parse(row.date)
        val first=date.minusDays(4).coerceAtLeast(firstDate).toString();val last=date.plusDays(2).coerceAtMost(lastDate).toString();val rows=history.meals.filter { it.event.date in first..last }
        val mealSources=food(rows).factors.values.flatMap { it.second }.distinctBy { it.key }
        val pointHealth=history.health.filter { it.date in first..last && it.period=="moment" }.map(::healthSource)
        val candidates=mealSources+medicineSources(history.medicines.filter { it.event.date in first..last })+pointHealth
        val instant=source.instant()
        val before=if(instant==null) emptyList() else candidates.filter { it.instant()?.let { t->window.contains(t,instant) }==true }.sortedWith(compareBy<AnalysisSource> { it.instant() }.thenBy { it.key })
        val unknown=candidates.filter { it.instant()==null && it.date in date.minusDays(2).toString()..row.date }
        val previous=date.minusDays(1).toString()
        val calendar=food(rows.filter { it.event.date==previous }).factors.values.flatMap { it.second }.distinctBy { it.key }+
            medicineSources(history.medicines.filter { it.event.date==previous })+history.health.filter { it.date==row.date && it.period=="day" }.map(::healthSource)+
            history.sleeps.filter { it.date==row.date }.map(::sleepSource)
        return EpisodeContext(source,window,before,unknown,calendar.filter { instant==null || it.instant()?.let { t->t<instant }!=false },if(instant==null) 0 else candidates.filter { it.instant()?.let { t->t>=instant }==true }.distinctBy { it.key.split(":").take(2).joinToString(":") }.size)
    }
    fun targetMatches(factor:AnalysisFactor,target:TrackingTargetWithEntry)=factor.scope==target.target.scope && (factor.publicId==target.entry.publicId || factor.catalogId==target.target.catalogId)
    fun targetHistory(history:AnalysisHistory,target:TrackingTargetWithEntry,through:String):List<AnalysisSource> =
        food(history.meals.filter { it.event.date<=through }).factors.values.filter { targetMatches(it.first,target) }.flatMap { it.second }.distinctBy { it.key }.sortedByDescending { it.date }
}
