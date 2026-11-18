package com.pamurlykin.nutritionassistant.ui.daily

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ANALYSIS_RULES = """Правило 1. Сравнения — по завершённым датам до выбранного дня; выбранный день рассматривается отдельно. Еда и лекарства относятся к предыдущему календарному дню, дневные оценки и сон — к дню исхода; взаимный порядок дневных оценок и эпизода не установлен. Нужны подтверждённые питание накануне и итог симптомов. Для лекарств также подтверждены наблюдения накануне. Отсутствие компонента учитывается только при полностью известном составе; известное присутствие сохраняется и при частичном составе. Пропущенные оценки и неизвестные признаки болезни не считаются отсутствием фактора. Сон без уточнённых дат старой ночи не сравнивается; новая оценка без часов относится к явно выбранной ночи.

Минимум 3 пригодных дня в каждой группе; повторяющееся совпадение требует минимум 2 дней с эпизодом в группе с фактором и более высокой частоты. Малые группы не получают рейтинг. Несколько эпизодов в день считаются одним днём. Совпадение не доказывает причину; равные наборы дней не позволяют разделить совместные факторы.

Часовые окна — исследовательские предположения, а не медицинские сроки: задержка больше нижней границы и до верхней включительно. Равное и более позднее время не считается предшествующим. Записи без часов и оценки дня/ночи остаются отдельно. Порции и дозы показываются как записаны, единицы не пересчитываются. Отдельные известные порции и дозы сравниваются как буквальные значения; это не сумма за день. Неизвестное количество/доза не означает другое количество/дозу. Категория служит фильтром, не означает общую переносимость. Комментарии показываются дословно и не интерпретируются автоматически."""

@Composable
internal fun ExplainableAnalysisCard(date: String, history: AnalysisHistory?, report: ExplainableReport?, days: Int,
    entries: List<CatalogItem>, busy: Boolean, onDays: (Int) -> Unit, onDate: (String) -> Unit,
    onSaveTarget: (Long?,Long,String,String?,String?,Boolean,()->Unit) -> Unit, onActive: (Long,Boolean) -> Unit) {
    var panel by rememberSaveable(date) { mutableStateOf<String?>(null) }
    var factorKey by rememberSaveable(date) { mutableStateOf<String?>(null) }
    var sourceDay by rememberSaveable(date) { mutableStateOf<String?>(null) }
    var sourceMode by rememberSaveable(date) { mutableStateOf<String?>(null) }
    var editId by rememberSaveable(date) { mutableStateOf<Long?>(null) }
    var targetHistoryId by rememberSaveable(date) { mutableStateOf<Long?>(null) }
    var caseKey by rememberSaveable(date) { mutableStateOf<String?>(null) }
    var windowName by rememberSaveable { mutableStateOf(AnalysisWindow.SIX.name) }
    val window=AnalysisWindow.valueOf(windowName)
    val cases=report?.episodeSources.orEmpty()
    val selected=cases.find { it.key==caseKey } ?: cases.firstOrNull()
    val activeTargets=history?.targets.orEmpty().filter { it.target.active }
    val navigate:(String)->Unit={ value -> panel=null;factorKey=null;sourceDay=null;sourceMode=null;targetHistoryId=null;onDate(value) }
    NutritionCard(modifier=Modifier.testTag("explainable-analysis")) {
        CardHeadline("Аналитика стула")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(30,90,365).forEach { count -> FilterChip(selected=days==count,onClick={ onDays(count) },enabled=!busy,label={ Text("$count дней") },modifier=Modifier.testTag("analysis-period-$count")) } }
        if(report==null) Text("Расчёт покрытия и наблюдений…") else {
            val c=report.coverage
            Text(if(c.days==0) "До выбранной даты нет доступных завершённых дней." else "Сравнения: ${report.start} — ${report.end}. Выбранный день отдельно.")
            Text("Подтверждены: питание накануне ${c.foodComplete}/${c.days}; симптомы ${c.symptomsComplete}/${c.days}; пригодных пар ${c.paired}/${c.days}.",modifier=Modifier.testTag("analysis-coverage"))
            Text("Жидкий стул: ${c.looseDays} дней, ${c.looseEpisodes} эпизодов. Неполный/неизвестный состав: ${c.unknownComposition} позиций; событий без часов: ${c.missingTime}; непригодных оценок ночи: ${c.unknownNight}.",style=MaterialTheme.typography.bodySmall)
            if(report.observations.none { it.sufficient }) Text("Недостаточно данных для сравнения групп.")
            TextButton(onClick={panel="factors"},modifier=Modifier.testTag("analysis-factors")) { Text("Наблюдения по факторам (${report.observations.size})") }
            TextButton(onClick={sourceMode="coverage"}) { Text("Записи выбранного периода") }
        }
        TextButton(onClick={panel="rules"}) { Text("Правила расчёта и ограничения") }
        TextButton(enabled=cases.isNotEmpty(),onClick={panel="cases"}) { Text("Эпизод для часового окна: "+(selected?.let { (it.actualTime ?: "время неизвестно")+" · "+it.title } ?: "нет записи 6–7")) }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { AnalysisWindow.entries.forEach { value -> FilterChip(selected=window==value,onClick={windowName=value.name},label={Text(value.title)},modifier=Modifier.testTag("analysis-window-${value.name}")) } }
        TextButton(enabled=selected!=null && history!=null,onClick={panel="episode"},modifier=Modifier.testTag("analysis-episode")) { Text("Перед эпизодом и календарный контекст") }
        TextButton(enabled=history!=null,onClick={panel="targets"},modifier=Modifier.testTag("analysis-targets")) { Text("Что хочу отслеживать (${activeTargets.size})") }
        Text("Это наблюдения дневника. Подтверждённая непереносимость и вероятность диагноза не определяются.",style=MaterialTheme.typography.bodySmall)
    }
    if(panel=="rules") AppDialog(onDismissRequest={panel=null},title={Text("Правила анализа, версия 1")},text={Text(ANALYSIS_RULES,Modifier.verticalScroll(rememberScrollState()))},confirmButton={AppDismissButton()},dismissButton=null)
    if(panel=="cases") AppDialog(onDismissRequest={panel=null},title={Text("Какой эпизод рассмотреть?")},text={LazyColumn(Modifier.heightIn(max=480.dp)) { items(cases,key={it.key}) { source -> TextButton(onClick={caseKey=source.key;panel=null}) { Text((source.actualTime ?: "Без часов")+" · "+source.title) } } } },confirmButton={AppDismissButton()},dismissButton=null)
    if(panel=="factors" && report!=null) FactorListDialog(report,activeTargets,{panel=null}) { factorKey=it }
    val observation=report?.observations?.find { it.factor.key==factorKey }
    if(observation!=null) ObservationDialog(observation,{factorKey=null}) { sourceDay=it }
    sourceDay?.let { day -> observation?.let { row -> (row.withFactor+row.withoutFactor).find { it.date==day }?.let { value ->
        SourceDialog("Исходы и записи $day",(value.factorSources+value.sources+value.outcomes).distinctBy {it.key},{sourceDay=null},navigate,
            "Итог: ${when(value.stoolSummary) {"diarrhea"->"записан жидкий стул";"no_stool"->"стула не было";else->"жидкого стула не было"}}. Питание накануне и симптомы подтверждены. Признаки болезни в этот день: ${when {value.illness -> "отмечены";value.illnessKnown -> "за день явно отмечено отсутствие";else -> "неизвестны: нет определённой оценки за день"}}. Различия записанных лекарств: ${value.medicationChanged?.let { if(it) "есть" else "нет" } ?: "неизвестны"}. Это сопутствующие обстоятельства, не доказанные причины.")
    } } }
    if(sourceMode=="coverage" && report!=null) SourceDialog("Записи периода",report.allSources,{sourceMode=null},navigate,"Пропуски видны в покрытии. Эти записи сами по себе не означают полного дня.")
    if(panel=="episode" && history!=null && selected!=null) {
        val context by produceState<EpisodeContext?>(null,history,selected.key,window) { value=withContext(Dispatchers.Default) { ExplainableAnalyzer.episode(history,selected.key,window) } }
        AppDialog(onDismissRequest={panel=null},title={Text("Контекст эпизода ${selected.actualTime ?: "без часов"}")},text={
            LazyColumn(Modifier.heightIn(max=480.dp)) {
                item { Text("Окно ${window.title}: исследовательское предположение, не срок реакции. "+(if(selected.instant()==null) "Время эпизода неизвестно — часовой анализ недоступен." else "Поздние и равные по времени события исключены из предшествий.")) }
                if(context==null) item {Text("Расчёт…")} else {
                    val c=requireNotNull(context)
                    item {Text("Не вошли из-за позднего/равного времени: событий ${c.excludedLaterOrEqual}.")}
                    sourceGroup("Перед эпизодом по указанному времени (${c.preceding.size})",c.preceding,navigate)
                    sourceGroup("Не распределены по часам: время неизвестно (${c.unknownTime.size})",c.unknownTime,navigate)
                    sourceGroup("Календарный контекст: еда/лекарства накануне, оценки дня/ночи; точный порядок не установлен (${c.calendarContext.size})",c.calendarContext,navigate)
                    item {Text("Записи после эпизода не объясняют его как предшествующие факторы. Комментарии показаны без смыслового разбора.",style=MaterialTheme.typography.bodySmall)}
                }
            }
        },confirmButton={AppDismissButton()},dismissButton=null)
    }
    if(panel=="targets" && history!=null) AppDialog(onDismissRequest={panel=null},title={Text("Что хочу отслеживать")},text={LazyColumn(Modifier.heightIn(max=480.dp)) {
        item {Text("Предположения пользователя, не подтверждённая непереносимость.");TextButton(onClick={editId=null;panel="edit-target"}) {Text("Добавить продукт или компонент")}}
        items(history.targets,key={it.target.publicId}) { value -> Column {Text(value.target.nameSnapshot+" · "+(if(value.target.scope=="ingredient") "компонент" else "позиция еды")+if(value.target.active) "" else " · не отслеживается")
            value.target.sourceNote?.let {Text("Источник предположения: $it")};value.target.comment?.let {Text(it)}
            FlowRow { TextButton(onClick={editId=value.target.id;panel="edit-target"}) {Text("Изменить")};TextButton(onClick={targetHistoryId=value.target.id}) {Text("История упоминаний")};TextButton(enabled=!busy,onClick={onActive(value.target.id,!value.target.active)}) {Text(if(value.target.active) "Не отслеживать" else "Отслеживать")} }
        } }
    }},confirmButton={AppDismissButton()},dismissButton=null)
    if(panel=="edit-target") TrackingTargetDialog(history?.targets?.find { it.target.id==editId },entries,busy,{panel="targets"}) { id,catalogId,scope,source,comment,active -> onSaveTarget(id,catalogId,scope,source,comment,active) {panel="targets"} }
    targetHistoryId?.let { id -> history?.targets?.find { it.target.id==id }?.let { target ->
        val sources by produceState<List<AnalysisSource>?>(null,history,id,date) { value=withContext(Dispatchers.Default) { ExplainableAnalyzer.targetHistory(history,target,date) } }
        SourceDialog("История: "+target.target.nameSnapshot,sources.orEmpty(),{targetHistoryId=null},navigate,if(sources==null) "Загрузка…" else "Упоминаний в питании до выбранной даты: ${sources?.size}. Это история записей, не доказательство связи с симптомом.")
    } }
}

@Composable
private fun FactorListDialog(report:ExplainableReport,targets:List<TrackingTargetWithEntry>,onDismiss:()->Unit,onSelect:(String)->Unit) {
    var query by rememberSaveable {mutableStateOf("")};var group by rememberSaveable {mutableStateOf<String?>(null)};var category by rememberSaveable {mutableStateOf<String?>(null)};var onlyTracked by rememberSaveable {mutableStateOf(false)}
    val rows=report.observations.filter { (group==null || it.factor.group.name==group) && (category==null || it.factor.category==category) && normalizeName(it.factor.title).contains(normalizeName(query)) && (!onlyTracked || targets.any { target->ExplainableAnalyzer.targetMatches(it.factor,target) }) }
    AppDialog(onDismissRequest=onDismiss,title={Text("Наблюдения по факторам")},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("analysis-factor-list")) {
        item {AppTextField(query,{query=it},label={Text("Поиск фактора")})}
        item {FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {FilterChip(selected=group==null,onClick={group=null},label={Text("Все")});FactorGroup.entries.forEach { value->FilterChip(selected=group==value.name,onClick={group=value.name},label={Text(value.title)})}}}
        item {CategoryPicker(category,allowAll=true) {category=it};FilterChip(selected=onlyTracked,onClick={onlyTracked=!onlyTracked},label={Text("Только отслеживаемые")});Text("Категории — навигация, не общая переносимость. Найдено ${rows.size}; алфавитный порядок, без рейтинга риска.",style=MaterialTheme.typography.bodySmall)}
        if(category!=null) item {
            val categoryDays=report.observations.filter {it.factor.category==category && it.factor.amountOf==null}.flatMap {it.withFactor}.distinctBy {it.date}
            Text("Категория отмечена в ${categoryDays.size} пригодных днях; в ${categoryDays.count {it.affected}} был тип 6–7. Это групповая сводка присутствия, не общая переносимость.")
        }
        if(rows.isEmpty()) item {Text("Нет подходящих наблюдений. Добавьте записи или измените фильтр.")}
        items(rows,key={it.factor.key}) { row -> TextButton(onClick={onSelect(row.factor.key)},modifier=Modifier.testTag("factor-${row.factor.key}")) {Text(row.factor.title+" · "+row.label+" · ${row.affectedWith}/${row.withFactor.size} и ${row.affectedWithout}/${row.withoutFactor.size}")} }
    }},confirmButton={AppDismissButton()},dismissButton=null)
}
@Composable
private fun ObservationDialog(row:FactorObservation,onDismiss:()->Unit,onDay:(String)->Unit) {
    AppDialog(onDismissRequest=onDismiss,title={Text(row.factor.title)},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("analysis-observation-days")) {
        item {Text(row.label);Text("С фактором: из ${row.withFactor.size} пригодных дней в ${row.affectedWith} был жидкий стул. Без фактора: из ${row.withoutFactor.size} в ${row.affectedWithout}. Исключено по полноте/неизвестности: ${row.excluded}.")
            Text("Правило 1: "+when(row.factor.group) {FactorGroup.ENTRY,FactorGroup.INGREDIENT,FactorGroup.MEDICINE->"фактор записан в предыдущий календарный день";else->"сопутствующая дневная оценка/ночь; порядок относительно эпизода не установлен"}+". Минимум 3 дня на группу и 2 дня с эпизодом для повторения. Это описательное сравнение, не причина и не вероятность непереносимости.")
            if(row.coFactors.isNotEmpty()) Text("В доступных пригодных записях встречались в те же дни: "+row.coFactors.joinToString("; ")+". Разделить влияние этих факторов пока нельзя. Всего совместных факторов: ${row.coFactorCount}; названий показано: ${row.coFactors.size}.")
            Text("Признаки болезни записаны: с фактором ${row.withFactor.count {it.illness}} из ${row.withFactor.size} дней, неизвестно ${row.withFactor.count {!it.illnessKnown}}; без фактора ${row.withoutFactor.count {it.illness}} из ${row.withoutFactor.size}, неизвестно ${row.withoutFactor.count {!it.illnessKnown}}. Различия записанных лекарств: ${(row.withFactor+row.withoutFactor).count {it.medicationChanged==true}} дней; неизвестно: ${(row.withFactor+row.withoutFactor).count {it.medicationChanged==null}}. Изменение записей не равно изменению назначения.",style=MaterialTheme.typography.bodySmall)}
        item {Text("Исходные дни с фактором")};items(row.withFactor,key={"with-"+it.date}) { day->TextButton(onClick={onDay(day.date)}) {Text(day.date+" · "+if(day.affected) "записан тип 6–7" else "подтверждено без жидкого стула")}}
        item {Text("Исходные дни без фактора")};items(row.withoutFactor,key={"without-"+it.date}) {day->TextButton(onClick={onDay(day.date)}) {Text(day.date+" · "+if(day.affected) "записан тип 6–7" else "подтверждено без жидкого стула")}}
    }},confirmButton={AppDismissButton()},dismissButton=null)
}
private fun LazyListScope.sourceGroup(title:String,sources:List<AnalysisSource>,onDate:(String)->Unit) {
    item {Text(title,style=MaterialTheme.typography.titleMedium)}
    if(sources.isEmpty()) item {Text("Записей нет.")}
    itemsIndexed(sources) {_,source->SourceRow(source,onDate)}
}
@Composable
private fun SourceDialog(title:String,sources:List<AnalysisSource>,onDismiss:()->Unit,onDate:(String)->Unit,description:String) {
    AppDialog(onDismissRequest=onDismiss,title={Text(title)},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("analysis-sources")) {item {Text(description)};itemsIndexed(sources) {_,source->SourceRow(source,onDate)}}},confirmButton={AppDismissButton()},dismissButton=null)
}
@Composable
private fun SourceRow(source:AnalysisSource,onDate:(String)->Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp).testTag("analysis-source-${source.key}")) {
        Text(source.date+" · "+recordedTimeLabel(source.actualTime,source.utcOffset,source.timeZone),style=MaterialTheme.typography.bodySmall)
        Text(source.title,style=MaterialTheme.typography.titleMedium);source.details.forEach {Text(it)};source.comment?.let {Text(it)}
        TextButton(onClick={onDate(source.date)}) {Text("Открыть дневник ${source.date}")};HorizontalDivider()
    }
}
@Composable
private fun TrackingTargetDialog(initial:TrackingTargetWithEntry?,entries:List<CatalogItem>,busy:Boolean,onDismiss:()->Unit,onSave:(Long?,Long,String,String?,String?,Boolean)->Unit) {
    var id by rememberSaveable {mutableStateOf(initial?.target?.catalogId)};var scope by rememberSaveable {mutableStateOf(initial?.target?.scope ?: "ingredient")};var query by rememberSaveable {mutableStateOf("")}
    var source by rememberSaveable {mutableStateOf(initial?.target?.sourceNote.orEmpty())};var comment by rememberSaveable {mutableStateOf(initial?.target?.comment.orEmpty())}
    val selected=entries.find {it.id==id};val kind=selected?.kind ?: initial?.entry?.kind
    val valid=id!=null && (kind=="product" || kind=="dish" && scope=="entry") && source.length<=1000 && comment.length<=1000
    AppDialog(onDismissRequest=onDismiss,title={Text("Предположение для отслеживания")},busy=busy,hasChanges=id!=initial?.target?.catalogId || scope!=(initial?.target?.scope ?: "ingredient") || source!=initial?.target?.sourceNote.orEmpty() || comment!=initial?.target?.comment.orEmpty(),text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState())) {
        Text("Выбор не подтверждает непереносимость. История берётся из сохранённого состава еды.")
        if(initial==null) Text("Повторный выбор использует прежнюю запись. Пустые поля сохранят её заметки; для очистки откройте «Изменить».",style=MaterialTheme.typography.bodySmall)
        Text("Выбрано: "+(selected?.displayName ?: initial?.target?.nameSnapshot ?: "не выбрано"))
        if(initial==null) {
            AppTextField(query,{query=it},label={Text("Поиск продукта или блюда")},enabled=!busy)
            entries.filter {it.kind in setOf("dish","product") && !it.archived && catalogMatches(it,query)}.take(20).forEach {entry->TextButton(enabled=!busy,onClick={id=entry.id;if(entry.kind=="dish") scope="entry"}) {Text(entry.displayName)}}
        }
        FlowRow {FilterChip(selected=scope=="entry",onClick={scope="entry"},enabled=!busy && initial==null,label={Text("Как позиция еды")});FilterChip(selected=scope=="ingredient",onClick={scope="ingredient"},enabled=!busy && kind=="product" && initial==null,label={Text("Как компонент")})}
        AppTextField(source,{source=it},label={Text("Источник предположения (необязательно)")},enabled=!busy,singleLine=false,isError=source.length>1000)
        AppTextField(comment,{comment=it},label={Text("Комментарий (необязательно)")},enabled=!busy,singleLine=false,isError=comment.length>1000)
    }},confirmButton={TextButton(enabled=valid && !busy,onClick={onSave(initial?.target?.id,requireNotNull(id),scope,source.takeIf {it.isNotBlank()},comment.takeIf {it.isNotBlank()},initial?.target?.active ?: true)}) {Text("Сохранить предположение")}})
}
