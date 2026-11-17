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
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val calendar by viewModel.calendar.collectAsStateWithLifecycle()
    var dialogKey by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogDate by rememberSaveable { mutableStateOf(state.selectedDate.toString()) }
    LaunchedEffect(state.selectedDate) {
        if (dialogDate != state.selectedDate.toString()) { dialogKey = null; dialogDate = state.selectedDate.toString() }
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.refreshToday() }
    val snapshot = state.snapshot
    val dialog = snapshot?.let { restoreDialog(dialogKey, it) }
    val openDialog: (DailyDialog) -> Unit = { dialogKey = it.storageKey() }
    CalendarDayLayout(calendar, state.selectedDate, busy, viewModel::previousMonth, viewModel::nextMonth,
        viewModel::setDate, viewModel::goToToday, { if (snapshot != null) openDialog(DailyDialog.Choice) }) {
        item(key = "day-heading") {
            NutritionCard {
                Text(StorageFormats.formatDateForDisplay(state.selectedDate), style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.Date) }) { Text("Выбрать дату") }
                if (snapshot != null) {
                    Text("Приемов пищи: ${snapshot.meals.size} · Лекарств: ${snapshot.medicines.size} · Туалет: ${snapshot.stools.size}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (snapshot == null) item { CircularProgressIndicator() }
        else {
            items(MealType.entries) { type ->
                val events = snapshot.meals.filter { it.event.mealType == type.storageValue }
                NutritionCard(modifier = Modifier.testTag("meal-" + type.storageValue)) {
                    CardHeadline(type.title)
                    if (events.isEmpty()) EmptyLabel("Записей нет.")
                    events.forEach { event ->
                        event.items.forEach { item ->
                            Text(item.name + " · " + foodItemTypeTitle(item.kind, item.foodType), style = MaterialTheme.typography.titleMedium)
                            if (item.ingredients.isNotEmpty()) Text(item.ingredients.joinToString(", "))
                        }
                        Actions(busy, { openDialog(DailyDialog.Meal(type, event)) }, { viewModel.deleteMeal(event.event.id) })
                    }
                    if (!type.isPrimary || events.isEmpty()) TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.Meal(type, null)) }) { Text("Добавить") }
                }
            }
            item {
                NutritionCard {
                    CardHeadline("Лекарства")
                    if (snapshot.medicines.isEmpty()) EmptyLabel("Записей нет.")
                    snapshot.medicines.forEach { event ->
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
                        Text(StoolCondition.label(entry.quality))
                        Actions(busy, { openDialog(DailyDialog.Stool(entry)) }, { viewModel.deleteStool(entry.id) })
                    }
                    if (snapshot.stools.isEmpty()) EmptyLabel("Записей нет.")
                    TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.Stool(null)) }) { Text("Добавить") }
                }
            }
            item { StoolAnalysisCard(snapshot.analysis) }
            item {
                NutritionCard {
                    CardHeadline("Сон")
                    val sleep = snapshot.sleep
                    if (sleep == null) EmptyLabel("Сон и его качество еще не записаны.")
                    else {
                        Text("Отбой: " + sleep.bedTime + " · Подъем: " + sleep.wakeupTime)
                        Text(sleep.qualityDescription ?: "Качество не указано")
                    }
                    Actions(busy, { openDialog(DailyDialog.Sleep(sleep)) }, if (sleep == null) null else ({ viewModel.deleteSleep() }))
                }
            }
            item {
                NutritionCard {
                    CardHeadline("Самочувствие")
                    if (snapshot.feelings.isEmpty()) EmptyLabel("Записей нет.")
                    snapshot.feelings.forEach { entry ->
                        Text(entry.description)
                        Actions(busy, { openDialog(DailyDialog.Feeling(entry)) }, { viewModel.deleteFeeling(entry.id) })
                    }
                    TextButton(enabled = !busy, onClick = { openDialog(DailyDialog.Feeling(null)) }) { Text("Добавить") }
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
                    val existing = if (type.isPrimary) snapshot.meals.firstOrNull { it.event.mealType == type.storageValue } else null
                    TextButton(onClick = { openDialog(DailyDialog.Meal(type, existing)) }) {
                        Text(type.title + if (existing != null) " · изменить" else "")
                    }
                }
                TextButton(onClick = { openDialog(DailyDialog.Medicine(null)) }) { Text("Прием лекарств") }
                TextButton(onClick = { openDialog(DailyDialog.Stool(null)) }) { Text("Туалет") }
            } },
            confirmButton = { AppDismissButton() }, dismissButton = null)
        is DailyDialog.Meal -> CatalogEventDialog(
            title = current.type.title, kind = CatalogKind.DISH,
            initial = current.event?.items?.map { SelectedCatalogItem(it.catalogId, it.name, it.foodType, it.ingredients, kind = it.kind) }.orEmpty(),
            entries = entries, busy = busy, onDismiss = dismiss,
            onCreate = { kind, name, type, productIds, success -> viewModel.saveCatalog(null, kind, name, type, productIds, success) },
            onSave = { selected -> viewModel.saveMeal(current.type, current.event?.event?.id, selected.map { it.id }, dismiss) })
        is DailyDialog.Medicine -> CatalogEventDialog(
            title = "Прием лекарств", kind = CatalogKind.MEDICINE,
            initial = current.event?.items?.map { SelectedCatalogItem(it.catalogId, it.name, dosage = it.dosage.orEmpty()) }.orEmpty(),
            entries = entries, busy = busy, onDismiss = dismiss,
            onCreate = { kind, name, type, productIds, success -> viewModel.saveCatalog(null, kind, name, type, productIds, success) },
            onSave = { selected -> viewModel.saveMedicines(current.event?.event?.id, selected.map { MedicineSelection(it.id, it.dosage) }, dismiss) })
        is DailyDialog.Stool -> StoolDialog(current.entry?.quality, busy, dismiss) { viewModel.saveStool(current.entry?.id, it, dismiss) }
        is DailyDialog.Feeling -> TextEntryDialog("Самочувствие", current.entry?.description.orEmpty(), busy, dismiss) {
            viewModel.saveFeeling(current.entry?.id, it, dismiss)
        }
        is DailyDialog.Water -> WaterDialog(current.count, busy, dismiss) { viewModel.setWater(it, dismiss) }
        is DailyDialog.Sleep -> SleepDialog(current.entry, busy, dismiss) { wakeup, bed, quality ->
            viewModel.saveSleep(wakeup, bed, quality, dismiss)
        }
        null -> Unit
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
private fun TextEntryDialog(title: String, initial: String, busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, hasChanges = text != initial, busy = busy,
        text = { AppTextField(text, { text = it }, label = { Text("Описание") }, isError = text.length > 1000, singleLine = false, enabled = !busy) },
        confirmButton = { TextButton(enabled = !busy && text.isNotBlank() && text.length <= 1000, onClick = { onSave(text.trim()) }) { Text("Сохранить") } },
        dismissButton = { AppDismissButton(enabled = !busy) })
}

@Composable
private fun StoolDialog(initial: Int?, busy: Boolean, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var quality by rememberSaveable { mutableStateOf(initial) }
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Качество стула") }, hasChanges = quality != initial, busy = busy,
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                Text("Выберите тип по Бристольской шкале.")
                (1..7).forEach { value ->
                    AppRadioChoice(StoolCondition.label(value), quality == value, enabled = !busy) { quality = value }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && quality in 1..7, onClick = { quality?.let(onSave) }) { Text("Сохранить") } },
        dismissButton = { AppDismissButton(enabled = !busy) })
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

@Composable
private fun SleepDialog(initial: SleepEntity?, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit) {
    var wakeup by rememberSaveable { mutableStateOf(initial?.wakeupTime ?: "07:00") }
    var bed by rememberSaveable { mutableStateOf(initial?.bedTime ?: "23:00") }
    var quality by rememberSaveable { mutableStateOf(initial?.qualityDescription.orEmpty()) }
    var timeTarget by rememberSaveable { mutableStateOf<String?>(null) }
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Сон") }, busy = busy,
        hasChanges = wakeup != (initial?.wakeupTime ?: "07:00") || bed != (initial?.bedTime ?: "23:00") || quality != initial?.qualityDescription.orEmpty(),
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Отбой предыдущим вечером, подъем в выбранный день.")
                TextButton(enabled = !busy, onClick = { timeTarget = "bed" }) { Text("Отбой: " + bed) }
                TextButton(enabled = !busy, onClick = { timeTarget = "wakeup" }) { Text("Подъем: " + wakeup) }
                AppTextField(quality, { quality = it }, label = { Text("Качество сна") }, isError = quality.length > 1000, singleLine = false, enabled = !busy)
            }
        },
        confirmButton = { TextButton(enabled = !busy && quality.length <= 1000, onClick = { onSave(wakeup, bed, quality.trim().ifBlank { null }) }) { Text("Сохранить") } },
        dismissButton = { AppDismissButton(enabled = !busy) })
    timeTarget?.let { target -> AppTimePicker(if (target == "bed") bed else wakeup, onDismiss = { timeTarget = null },
        onSelected = { if (target == "bed") bed = it else wakeup = it }) }
}

private sealed interface DailyDialog {
    data object Choice : DailyDialog
    data object Date : DailyDialog
    data class Meal(val type: MealType, val event: MealWithItems?) : DailyDialog
    data class Medicine(val event: MedicineWithItems?) : DailyDialog
    data class Stool(val entry: StoolEntity?) : DailyDialog
    data class Feeling(val entry: FeelingEntity?) : DailyDialog
    data class Sleep(val entry: SleepEntity?) : DailyDialog
    data class Water(val count: Int) : DailyDialog
}

private fun DailyDialog.storageKey(): String = when (this) {
    DailyDialog.Choice -> "choice"
    DailyDialog.Date -> "date"
    is DailyDialog.Meal -> "meal:${type.name}:${event?.event?.id ?: 0}"
    is DailyDialog.Medicine -> "medicine:${event?.event?.id ?: 0}"
    is DailyDialog.Stool -> "stool:${entry?.id ?: 0}"
    is DailyDialog.Feeling -> "feeling:${entry?.id ?: 0}"
    is DailyDialog.Sleep -> "sleep"
    is DailyDialog.Water -> "water"
}

private fun restoreDialog(key: String?, snapshot: DailyStatsSnapshot): DailyDialog? {
    if (key == null) return null
    val parts = key.split(':')
    val id = parts.last().toLongOrNull() ?: 0
    return when (parts.first()) {
        "choice" -> DailyDialog.Choice
        "date" -> DailyDialog.Date
        "meal" -> DailyDialog.Meal(MealType.valueOf(parts[1]), snapshot.meals.find { it.event.id == id })
        "medicine" -> DailyDialog.Medicine(snapshot.medicines.find { it.event.id == id })
        "stool" -> DailyDialog.Stool(snapshot.stools.find { it.id == id })
        "feeling" -> DailyDialog.Feeling(snapshot.feelings.find { it.id == id })
        "sleep" -> DailyDialog.Sleep(snapshot.sleep)
        "water" -> DailyDialog.Water(snapshot.waterCount)
        else -> null
    }
}
