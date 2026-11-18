package com.pamurlykin.nutritionassistant.ui.daily

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.ui.components.*
import com.pamurlykin.nutritionassistant.util.StorageFormats

@Composable
fun DailyStatsScreen(viewModel: DailyStatsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entries by viewModel.catalog.collectAsStateWithLifecycle()
    val recentIds by viewModel.recentIds.collectAsStateWithLifecycle()
    val recentMeals by viewModel.recentMeals.collectAsStateWithLifecycle()
    var compositionItem by rememberSaveable { mutableStateOf<Long?>(null) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val calendar by viewModel.calendar.collectAsStateWithLifecycle()
    var dialogKey by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogDate by rememberSaveable { mutableStateOf(state.selectedDate.toString()) }
    LaunchedEffect(state.selectedDate) {
        if (dialogDate != state.selectedDate.toString()) { dialogKey = null; dialogDate = state.selectedDate.toString() }
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.refreshToday() }
    val snapshot = state.snapshot
    val dialog = snapshot?.let { restoreDialog(dialogKey, it, recentMeals) }
    val openDialog: (DailyDialog) -> Unit = { dialogKey = it.storageKey() }
    CalendarDayLayout(calendar, state.selectedDate, busy, viewModel::previousMonth, viewModel::nextMonth,
        viewModel::setDate, viewModel::goToToday, { if (snapshot != null) openDialog(DailyDialog.Choice) }, addEnabled = snapshot != null) {
        item(key = "day-heading") {
            NutritionCard {
                Text(StorageFormats.formatDateForDisplay(state.selectedDate), style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = !busy && snapshot != null, onClick = { openDialog(DailyDialog.Date) }) { Text("Выбрать дату") }
                if (snapshot != null) {
                    Text("Приемов пищи: ${snapshot.meals.size} · Лекарств: ${snapshot.medicines.size} · Туалет: ${snapshot.stools.size}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val loose = snapshot.stools.count { it.quality in 6..7 }
                    Text("Жидкий стул, тип 6–7: $loose", modifier = Modifier.testTag("day-loose-count"),
                        color = if (loose > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text("Питание: " + (if (snapshot.dayStatus?.foodComplete == true) "подтверждено" else "не подтверждено") +
                        "; симптомы: " + (if (snapshot.dayStatus?.symptomsComplete == true) "подтверждены" else "не подтверждены"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (snapshot == null) item { CircularProgressIndicator() }
        else {
            item(key = "month-overview") { CalendarMonthOverview(calendar, !busy, viewModel::setDate) }
            item(key = "timeline") { DayTimelineCard(snapshot) }
            items(MealType.entries) { type ->
                val events = snapshot.meals.filter { it.event.mealType == type.storageValue }
                NutritionCard(modifier = Modifier.testTag("meal-" + type.storageValue)) {
                    CardHeadline(type.title)
                    if (events.isEmpty()) EmptyLabel("Записей нет.")
                    events.forEach { event ->
                        Text(recordedTimeLabel(event.event.actualTime, event.event.utcOffset, event.event.timeZone), style = MaterialTheme.typography.bodySmall)
                        event.event.comment?.let { Text(it) }
                        event.items.forEach { item ->
                            Text(item.historyDisplayName() + " · " + foodItemTypeTitle(item.kind, item.foodType), style = MaterialTheme.typography.titleMedium)
                            if (item.ingredients.isNotEmpty()) Text(item.ingredients.joinToString(", "))
                            if (item.quantity != null) Text(quantityTitle(item.quantity, item.unit))
                            item.comment?.let { Text(it) }
                            TextButton(onClick = { compositionItem = item.id }) { Text("Состав позиции") }
                        }
                        Actions(busy, { openDialog(DailyDialog.Meal(type, event)) }, { viewModel.deleteMeal(event.event.id) })
                    }
                    TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.Meal(type, null)) }) { Text("Добавить") }
                }
            }
            item {
                NutritionCard {
                    CardHeadline("Лекарства")
                    if (snapshot.medicines.isEmpty()) EmptyLabel("Записей нет.")
                    snapshot.medicines.forEach { event ->
                        Text(recordedTimeLabel(event.event.actualTime, event.event.utcOffset, event.event.timeZone), style = MaterialTheme.typography.bodySmall)
                        event.event.comment?.let { Text(it) }
                        event.items.forEach { Text(it.name + (it.dosage?.let { dose -> " · " + dose } ?: "")) }
                        Actions(busy, { openDialog(DailyDialog.Medicine(event)) }, { viewModel.deleteMedicine(event.event.id) })
                    }
                    TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.Medicine(null)) }) { Text("Добавить прием") }
                }
            }
            item {
                NutritionCard(modifier = Modifier.testTag("stool-card")) {
                    CardHeadline("Туалет")
                    snapshot.stools.forEach { entry ->
                        Text(entry.qualityLabel ?: StoolCondition.label(entry.quality))
                        Text(recordedTimeLabel(entry.actualTime, entry.utcOffset, entry.timeZone), style = MaterialTheme.typography.bodySmall)
                        entry.urgency?.let { Text("Срочность: " + HealthScales.label("urgency", it.toString())) }
                        entry.pain?.let { Text("Боль: " + HealthScales.label("pain", it.toString())) }
                        entry.comment?.let { Text(it) }
                        Actions(busy, { openDialog(DailyDialog.Stool(entry)) }, { viewModel.deleteStool(entry.id) })
                    }
                    if (snapshot.stools.isEmpty()) EmptyLabel("Записей нет.")
                    TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.Stool(null)) }) { Text("Добавить") }
                }
            }
            item { StoolAnalysisCard(snapshot.analysis) }
            item {
                NutritionCard(modifier = Modifier.testTag("health-day-card")) {
                    CardHeadline("Состояние за день")
                    val daily = snapshot.healthEntries.filter { it.period == "day" }.associateBy { it.metric }
                    Text("Качество сна: " + (snapshot.sleep?.qualityLabel ?: "Не заполнено"))
                    HealthScales.metrics.forEach { (key, title) ->
                        Text(title + ": " + (daily[key]?.valueLabel ?: "Не заполнено"))
                        daily[key]?.let { entry ->
                            entry.durationMinutes?.let { Text("Продолжительность: $it мин") }
                            entry.activityKind?.let { code -> Text(HealthScales.kinds.single { it.code == code }.label) }
                            entry.illnessImpact?.let { code -> Text("Влияние: " + HealthScales.impact.single { it.code == code }.label) }
                            if (entry.symptomCodes.isNotEmpty()) Text(entry.symptomCodes.joinToString(", ") { code -> HealthScales.symptoms.single { it.code == code }.label })
                            entry.comment?.let { Text(it) }
                        }
                    }
                    TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.HealthDay) }) { Text("Заполнить день") }
                    val status = snapshot.dayStatus
                    Text("Питание: " + if (status?.foodComplete == true) "подтверждено" else "не подтверждено")
                    Text("Симптомы: " + if (status?.symptomsComplete == true) "подтверждены" else "не подтверждены")
                    TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.Completeness) }) { Text("Проверить полноту дня") }
                }
            }
            item {
                NutritionCard(modifier = Modifier.testTag("health-events-card")) {
                    CardHeadline("Отдельные наблюдения")
                    val events = snapshot.healthEntries.filter { it.period == "moment" }
                    if (events.isEmpty()) EmptyLabel("Наблюдений нет.")
                    events.forEach { entry ->
                        Text(HealthScales.metrics.getValue(entry.metric) + ": " + (entry.valueLabel ?: "Без оценки (старая запись)"), style = MaterialTheme.typography.titleMedium)
                        Text(recordedTimeLabel(entry.actualTime, entry.utcOffset, entry.timeZone), style = MaterialTheme.typography.bodySmall)
                        entry.durationMinutes?.let { Text("Известная продолжительность: $it мин") }
                        entry.activityKind?.let { code -> Text(HealthScales.kinds.single { it.code == code }.label) }
                        entry.illnessImpact?.let { code -> Text("Влияние: " + HealthScales.impact.single { it.code == code }.label) }
                        if (entry.symptomCodes.isNotEmpty()) Text(entry.symptomCodes.joinToString(", ") { code -> HealthScales.symptoms.single { it.code == code }.label })
                        entry.comment?.let { Text(it) }
                        Actions(busy, { openDialog(DailyDialog.Health(entry.metric, entry)) }, { viewModel.deleteFeeling(entry.id) })
                    }
                    val activity = activitySummary(events)
                    if (activity.maximum != null) Text("События активности: максимальная отмеченная интенсивность — " + HealthScales.label("activity", activity.maximum) +
                        "; сумма известных минут — ${activity.knownMinutes}; без длительности — ${activity.unknownDurationCount}. Дневная оценка хранится отдельно.")
                    TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.HealthChoice) }) { Text("Добавить наблюдение") }
                }
            }
            item {
                NutritionCard {
                    CardHeadline("Сон")
                    val sleep = snapshot.sleep
                    if (sleep == null) EmptyLabel("Сон и его качество еще не записаны.")
                    else {
                        Text("Качество: " + (sleep.qualityLabel ?: "Не оценено"))
                        if (sleep.bedTime != null) Text("Отбой: " + (sleep.bedDate ?: "дата неизвестна") + " " + sleep.bedTime + " · Подъем: " + (sleep.wakeupDate ?: "дата неизвестна") + " " + sleep.wakeupTime)
                        sleep.bedIntervalMinutes()?.let { Text("В постели по местным часам: ${it / 60} ч ${it % 60} мин") }
                        sleep.awakenings?.let { Text("Пробуждения: $it") }
                        sleep.qualityDescription?.let { Text(it) }
                    }
                    Actions(busy, { openDialog(DailyDialog.Sleep(sleep)) }, if (sleep == null) null else ({ viewModel.deleteSleep() }))
                }
            }
            item {
                NutritionCard {
                    CardHeadline("Вода")
                    Text(snapshot.waterCount.toString() + " стаканов")
                    Row {
                        TextButton(enabled = !busy, onClick = { viewModel.incrementWater(-1) }) { Text("−1") }
                        TextButton(enabled = !busy, onClick = { viewModel.incrementWater(1) }) { Text("+1") }
                        TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.Water(snapshot.waterCount)) }) { Text("Указать") }
                    }
                }
            }
        }
    }
    val dismiss = { dialogKey = null }
    when (val current = dialog) {
        DailyDialog.Date -> AppDatePicker(state.selectedDate, dismiss, viewModel::setDate)
        DailyDialog.Choice -> AppDialog(onDismissRequest = dismiss,
            title = { Text("Добавить запись") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                MealType.entries.forEach { type ->
                    TextButton(onClick = { openDialog(DailyDialog.Meal(type, null)) }) {
                        Text(type.title)
                    }
                }
                if (recentMeals.isNotEmpty()) TextButton(onClick = { openDialog(DailyDialog.Recent) }) { Text("Повторить недавний приём") }
                TextButton(onClick = { openDialog(DailyDialog.HealthDay) }) { Text("Состояние за день") }
                TextButton(onClick = { openDialog(DailyDialog.HealthChoice) }) { Text("Отдельное наблюдение") }
                TextButton(onClick = { openDialog(DailyDialog.Completeness) }) { Text("Полнота дня") }
                TextButton(onClick = { openDialog(DailyDialog.Medicine(null)) }) { Text("Прием лекарств") }
                TextButton(onClick = { openDialog(DailyDialog.Stool(null)) }) { Text("Туалет") }
            } },
            confirmButton = { AppDismissButton() }, dismissButton = null)
        DailyDialog.Recent -> AppDialog(onDismissRequest = dismiss, title = { Text("Повторить недавний приём") }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                recentMeals.forEach { source ->
                    val type = MealType.entries.single { it.storageValue == source.event.mealType }
                    TextButton(onClick = { openDialog(DailyDialog.Meal(type, null, source)) }) { Text(source.event.date + " · " + type.title + " · " + source.items.joinToString(", ") { it.historyDisplayName() }) }
                }
                Text("Перед сохранением можно проверить состав, изменить порции и убрать позиции.")
            }
        }, confirmButton = { AppDismissButton() }, dismissButton = null)
        is DailyDialog.Meal -> {
            val source = current.event ?: current.repeatSource
            CatalogEventDialog(title = (if (current.repeatSource != null) "Повторить: " else "") + current.type.title, kind = CatalogKind.DISH,
                initial = source?.items?.map { SelectedCatalogItem(it.catalogId, it.historyDisplayName(), it.foodType, it.ingredients, kind = it.kind,
                    quantity = it.quantity.orEmpty(), unit = it.unit, comment = it.comment.orEmpty(), snapshot = it.compositionSnapshot) }.orEmpty(),
                entries = entries, busy = busy, onDismiss = dismiss, onCreate = viewModel::saveDraft,
                date = snapshot.date, initialTime = current.event?.event?.let { RecordedTime(it.actualTime, it.utcOffset, it.timeZone) } ?: RecordedTime(),
                initialComment = source?.event?.comment.orEmpty(), recentIds = recentIds, onFavorite = viewModel::favorite, repeating = current.repeatSource != null,
                onSave = { selected, comment, recorded -> viewModel.saveMeal(current.type, current.event?.event?.id, selected.map { it.id }, dismiss,
                    context = EventContext(recorded, comment),
                    portions = selected.associate { it.id to Portion(it.quantity.takeIf(String::isNotBlank), it.unit) },
                    comments = selected.associate { it.id to it.comment.takeIf(String::isNotBlank) }, refresh = selected.filter { it.refresh }.map { it.id }.toSet(),
                    sourceId = current.repeatSource?.event?.id, expectedHashes = selected.mapNotNull { item -> item.expectedHash?.let { item.id to it } }.toMap()) })
        }
        is DailyDialog.Medicine -> CatalogEventDialog(title = "Прием лекарств", kind = CatalogKind.MEDICINE,
            initial = current.event?.items?.map { SelectedCatalogItem(it.catalogId, it.name, dosage = it.dosage.orEmpty(), kind = CatalogKind.MEDICINE,
                quantity = it.quantity.orEmpty(), unit = it.unit, comment = it.comment.orEmpty()) }.orEmpty(),
            entries = entries, busy = busy, onDismiss = dismiss, onCreate = viewModel::saveDraft,
            date = snapshot.date, initialTime = current.event?.event?.let { RecordedTime(it.actualTime, it.utcOffset, it.timeZone) } ?: RecordedTime(),
            initialComment = current.event?.event?.comment.orEmpty(), recentIds = recentIds, onFavorite = viewModel::favorite,
            onSave = { selected, comment, recorded -> viewModel.saveMedicines(current.event?.event?.id,
                selected.map { MedicineSelection(it.id, it.dosage, Portion(it.quantity.takeIf(String::isNotBlank), it.unit), it.comment, true) }, dismiss,
                context = EventContext(recorded, comment)) })
        DailyDialog.HealthDay -> HealthDayDialog(snapshot, busy, dismiss) { values, quality, comment -> viewModel.saveDayHealth(values, quality, comment, dismiss) }
        DailyDialog.Completeness -> CompletenessDialog(snapshot.dayStatus, busy, dismiss) { food, symptoms, summary -> viewModel.confirmDay(food, symptoms, summary, dismiss) }
        DailyDialog.HealthChoice -> AppDialog(onDismissRequest = dismiss, title = { Text("Какое наблюдение добавить?") }, text = {
            Column { HealthScales.metrics.forEach { (code, title) -> TextButton(onClick = { openDialog(DailyDialog.Health(code, null)) }) { Text(title) } } }
        }, confirmButton = { AppDismissButton() }, dismissButton = null)
        is DailyDialog.Health -> HealthObservationDialog(snapshot.date, current.metric, current.entry, busy, dismiss) { viewModel.saveHealth(current.entry?.id, it, dismiss) }
        is DailyDialog.Stool -> StructuredStoolDialog(snapshot.date, current.entry, busy, dismiss) { quality, context, details -> viewModel.saveStool(current.entry?.id, quality, dismiss, context, details) }
        is DailyDialog.Water -> WaterDialog(current.count, busy, dismiss) { viewModel.setWater(it, dismiss) }
        is DailyDialog.Sleep -> StructuredSleepDialog(snapshot.date, current.entry, busy, dismiss) { viewModel.saveSleepDraft(it, dismiss) }
        null -> Unit
    }
    snapshot?.meals?.flatMap { it.items }?.find { it.id == compositionItem }?.let { item ->
        CompositionDialog(item.compositionSnapshot, item.ingredients) { compositionItem = null }
    }
}

@Composable
private fun Actions(busy: Boolean, onEdit: () -> Unit, onDelete: (() -> Unit)?) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = !busy, onClick = onEdit) { Text("Изменить") }
        if (onDelete != null) TextButton(enabled = !busy, onClick = { confirming = true }) { Text("Удалить") }
    }
    if (confirming && onDelete != null) ConfirmActionDialog("Удалить запись?", "Запись будет удалена из дневника. Справочники останутся.", "Удалить",
        onDismiss = { confirming = false }, onConfirm = { confirming = false; onDelete() })
}

@Composable
private fun WaterDialog(initial: Int, busy: Boolean, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Вода") }, hasChanges = text != initial.toString(), busy = busy,
        text = { AppTextField(text, { text = it.filter(Char::isDigit).take(10) }, label = { Text("Стаканов") }, enabled = !busy, isError = value == null,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) },
        confirmButton = { TextButton(enabled = !busy && value != null && value >= 0, onClick = { value?.let(onSave) }) { Text("Сохранить") } },
        dismissButton = { AppDismissButton(enabled = !busy) })
}

private sealed interface DailyDialog {
    data object HealthDay : DailyDialog
    data object HealthChoice : DailyDialog
    data object Completeness : DailyDialog
    data class Health(val metric: String, val entry: HealthEntryEntity?) : DailyDialog
    data object Recent : DailyDialog
    data object Choice : DailyDialog
    data object Date : DailyDialog
    data class Meal(val type: MealType, val event: MealWithItems?, val repeatSource: MealWithItems? = null) : DailyDialog
    data class Medicine(val event: MedicineWithItems?) : DailyDialog
    data class Stool(val entry: StoolEntity?) : DailyDialog
    data class Sleep(val entry: SleepEntity?) : DailyDialog
    data class Water(val count: Int) : DailyDialog
}

private fun DailyDialog.storageKey(): String = when (this) {
    DailyDialog.HealthDay -> "health-day"
    DailyDialog.HealthChoice -> "health-choice"
    DailyDialog.Completeness -> "completeness"
    is DailyDialog.Health -> "health:${metric}:${entry?.id ?: 0}"
    DailyDialog.Recent -> "recent"
    DailyDialog.Choice -> "choice"
    DailyDialog.Date -> "date"
    is DailyDialog.Meal -> if (repeatSource != null) "repeat:${repeatSource.event.id}" else "meal:${type.name}:${event?.event?.id ?: 0}"
    is DailyDialog.Medicine -> "medicine:${event?.event?.id ?: 0}"
    is DailyDialog.Stool -> "stool:${entry?.id ?: 0}"
    is DailyDialog.Sleep -> "sleep"
    is DailyDialog.Water -> "water"
}

private fun restoreDialog(key: String?, snapshot: DailyStatsSnapshot, recent: List<MealWithItems>): DailyDialog? {
    if (key == null) return null
    val parts = key.split(':')
    val id = parts.last().toLongOrNull() ?: 0
    return when (parts.first()) {
        "health-day" -> DailyDialog.HealthDay
        "health-choice" -> DailyDialog.HealthChoice
        "completeness" -> DailyDialog.Completeness
        "health" -> DailyDialog.Health(parts[1], snapshot.healthEntries.find { it.id == id })
        "recent" -> DailyDialog.Recent
        "repeat" -> recent.find { it.event.id == id }?.let { source -> DailyDialog.Meal(MealType.entries.single { it.storageValue == source.event.mealType }, null, source) }
        "choice" -> DailyDialog.Choice
        "date" -> DailyDialog.Date
        "meal" -> DailyDialog.Meal(MealType.valueOf(parts[1]), snapshot.meals.find { it.event.id == id })
        "medicine" -> DailyDialog.Medicine(snapshot.medicines.find { it.event.id == id })
        "stool" -> DailyDialog.Stool(snapshot.stools.find { it.id == id })
        "feeling" -> DailyDialog.Health("wellbeing", snapshot.healthEntries.find { it.id == id })
        "sleep" -> DailyDialog.Sleep(snapshot.sleep)
        "water" -> DailyDialog.Water(snapshot.waterCount)
        else -> null
    }
}
