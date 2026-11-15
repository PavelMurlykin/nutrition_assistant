package com.pamurlykin.nutritionassistant.ui.daily

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<DailyDialog?>(null) }
    LaunchedEffect(state.selectedDate) { dialog = null }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.refreshToday() }
    val snapshot = state.snapshot
    CalendarDayLayout(calendar, state.selectedDate, busy, viewModel::previousMonth, viewModel::nextMonth,
        viewModel::setDate, viewModel::goToToday, { if (snapshot != null) dialog = DailyDialog.Choice }) {
        item(key = "day-heading") {
            NutritionCard {
                Text(StorageFormats.formatDateForDisplay(state.selectedDate), style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = !busy, onClick = { showDatePicker(context, state.selectedDate, viewModel::setDate) }) { Text("Выбрать дату") }
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
                            Text(item.name + " · " + FoodType.titleFor(item.foodType), style = MaterialTheme.typography.titleMedium)
                            if (item.ingredients.isNotEmpty()) Text(item.ingredients.joinToString(", "))
                        }
                        Actions(busy, { dialog = DailyDialog.Meal(type, event) }, { viewModel.deleteMeal(event.event.id) })
                    }
                    if (!type.isPrimary || events.isEmpty()) TextButton(enabled = !busy, onClick = { dialog = DailyDialog.Meal(type, null) }) { Text("Добавить") }
                }
            }
            item {
                NutritionCard {
                    CardHeadline("Лекарства")
                    if (snapshot.medicines.isEmpty()) EmptyLabel("Записей нет.")
                    snapshot.medicines.forEach { event ->
                        event.items.forEach { Text(it.name + (it.dosage?.let { dose -> " · " + dose } ?: "")) }
                        Actions(busy, { dialog = DailyDialog.Medicine(event) }, { viewModel.deleteMedicine(event.event.id) })
                    }
                    TextButton(enabled = !busy, onClick = { dialog = DailyDialog.Medicine(null) }) { Text("Добавить прием") }
                }
            }
            item {
                NutritionCard(modifier = Modifier.testTag("stool-card")) {
                    CardHeadline("Туалет")
                    snapshot.stools.forEach { entry ->
                        Text(StoolCondition.label(entry.quality))
                        Actions(busy, { dialog = DailyDialog.Stool(entry) }, { viewModel.deleteStool(entry.id) })
                    }
                    if (snapshot.stools.isEmpty()) EmptyLabel("Записей нет.")
                    TextButton(enabled = !busy, onClick = { dialog = DailyDialog.Stool(null) }) { Text("Добавить") }
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
                    Actions(busy, { dialog = DailyDialog.Sleep(sleep) }, if (sleep == null) null else ({ viewModel.deleteSleep() }))
                }
            }
            item {
                NutritionCard {
                    CardHeadline("Самочувствие")
                    if (snapshot.feelings.isEmpty()) EmptyLabel("Записей нет.")
                    snapshot.feelings.forEach { entry ->
                        Text(entry.description)
                        Actions(busy, { dialog = DailyDialog.Feeling(entry) }, { viewModel.deleteFeeling(entry.id) })
                    }
                    TextButton(enabled = !busy, onClick = { dialog = DailyDialog.Feeling(null) }) { Text("Добавить") }
                }
            }
            item {
                NutritionCard {
                    CardHeadline("Вода")
                    Text(snapshot.waterCount.toString() + " стаканов")
                    Row {
                        TextButton(enabled = !busy, onClick = { viewModel.incrementWater(-1) }) { Text("−1") }
                        TextButton(enabled = !busy, onClick = { viewModel.incrementWater(1) }) { Text("+1") }
                        TextButton(enabled = !busy, onClick = { dialog = DailyDialog.Water(snapshot.waterCount) }) { Text("Указать") }
                    }
                }
            }
        }
    }
    val dismiss = { dialog = null }
    when (val current = dialog) {
        DailyDialog.Choice -> AlertDialog(onDismissRequest = dismiss,
            title = { Text("Добавить запись") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                MealType.entries.forEach { type ->
                    val existing = if (type.isPrimary) snapshot?.meals?.firstOrNull { it.event.mealType == type.storageValue } else null
                    TextButton(onClick = { dialog = DailyDialog.Meal(type, existing) }) {
                        Text(type.title + if (existing != null) " · изменить" else "")
                    }
                }
                TextButton(onClick = { dialog = DailyDialog.Medicine(null) }) { Text("Прием лекарств") }
                TextButton(onClick = { dialog = DailyDialog.Stool(null) }) { Text("Туалет") }
            } },
            confirmButton = { TextButton(onClick = dismiss) { Text("Отмена") } })
        is DailyDialog.Meal -> CatalogEventDialog(
            title = current.type.title, kind = CatalogKind.DISH,
            initial = current.event?.items?.map { SelectedCatalogItem(it.catalogId, it.name, it.foodType, it.ingredients) }.orEmpty(),
            entries = entries, busy = busy, onDismiss = dismiss,
            onCreate = { name, type, ingredients, success -> viewModel.saveCatalog(null, CatalogKind.DISH, name, type, ingredients, success) },
            onSave = { selected -> viewModel.saveMeal(current.type, current.event?.event?.id, selected.map { it.id }, dismiss) })
        is DailyDialog.Medicine -> CatalogEventDialog(
            title = "Прием лекарств", kind = CatalogKind.MEDICINE,
            initial = current.event?.items?.map { SelectedCatalogItem(it.catalogId, it.name, dosage = it.dosage.orEmpty()) }.orEmpty(),
            entries = entries, busy = busy, onDismiss = dismiss,
            onCreate = { name, type, ingredients, success -> viewModel.saveCatalog(null, CatalogKind.MEDICINE, name, type, ingredients, success) },
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = !busy, onClick = onEdit) { Text("Изменить") }
        if (onDelete != null) TextButton(enabled = !busy, onClick = onDelete) { Text("Удалить") }
    }
}

@Composable
private fun TextEntryDialog(title: String, initial: String, busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text("Описание") }, isError = text.length > 1000) },
        confirmButton = { TextButton(enabled = !busy && text.isNotBlank() && text.length <= 1000, onClick = { onSave(text.trim()) }) { Text("Сохранить") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } })
}

@Composable
private fun StoolDialog(initial: Int?, busy: Boolean, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var quality by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Качество стула") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Выберите тип по Бристольской шкале.")
                (1..7).forEach { value ->
                    TextButton(enabled = !busy, onClick = { quality = value }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = quality == value, onClick = null)
                            Text(StoolCondition.label(value))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && quality in 1..7, onClick = { quality?.let(onSave) }) { Text("Сохранить") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } })
}

@Composable
private fun WaterDialog(initial: Int, busy: Boolean, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Вода") },
        text = { OutlinedTextField(text, { text = it.filter(Char::isDigit).take(9) }, label = { Text("Стаканов") }) },
        confirmButton = { TextButton(enabled = !busy && value != null && value >= 0, onClick = { value?.let(onSave) }) { Text("Сохранить") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } })
}

@Composable
private fun SleepDialog(initial: SleepEntity?, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit) {
    val context = LocalContext.current
    var wakeup by remember { mutableStateOf(initial?.wakeupTime ?: "07:00") }
    var bed by remember { mutableStateOf(initial?.bedTime ?: "23:00") }
    var quality by remember { mutableStateOf(initial?.qualityDescription.orEmpty()) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Сон") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Отбой предыдущим вечером, подъем в выбранный день.")
                TextButton(enabled = !busy, onClick = { showTimePicker(context, bed) { bed = it } }) { Text("Отбой: " + bed) }
                TextButton(enabled = !busy, onClick = { showTimePicker(context, wakeup) { wakeup = it } }) { Text("Подъем: " + wakeup) }
                OutlinedTextField(quality, { quality = it }, label = { Text("Качество сна") }, isError = quality.length > 1000)
            }
        },
        confirmButton = { TextButton(enabled = !busy && quality.length <= 1000, onClick = { onSave(wakeup, bed, quality.trim().ifBlank { null }) }) { Text("Сохранить") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } })
}

private sealed interface DailyDialog {
    data object Choice : DailyDialog
    data class Meal(val type: MealType, val event: MealWithItems?) : DailyDialog
    data class Medicine(val event: MedicineWithItems?) : DailyDialog
    data class Stool(val entry: StoolEntity?) : DailyDialog
    data class Feeling(val entry: FeelingEntity?) : DailyDialog
    data class Sleep(val entry: SleepEntity?) : DailyDialog
    data class Water(val count: Int) : DailyDialog
}
