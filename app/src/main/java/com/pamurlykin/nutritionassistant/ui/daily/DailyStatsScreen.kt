package com.pamurlykin.nutritionassistant.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.nutritionassistant.data.local.FeelingEntity
import com.pamurlykin.nutritionassistant.data.local.MealEntity
import com.pamurlykin.nutritionassistant.data.local.MedicineEntity
import com.pamurlykin.nutritionassistant.data.local.SleepEntity
import com.pamurlykin.nutritionassistant.data.local.StoolEntity
import com.pamurlykin.nutritionassistant.data.model.MealType
import com.pamurlykin.nutritionassistant.ui.components.CardHeadline
import com.pamurlykin.nutritionassistant.ui.components.EmptyLabel
import com.pamurlykin.nutritionassistant.ui.components.NutritionCard
import com.pamurlykin.nutritionassistant.ui.components.showDatePicker
import com.pamurlykin.nutritionassistant.ui.components.showTimePicker
import com.pamurlykin.nutritionassistant.util.StorageFormats

@Composable
fun DailyStatsScreen(
    viewModel: DailyStatsViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = uiState.snapshot
    val context = LocalContext.current
    var dialog: DailyDialog? by remember { mutableStateOf(null) }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Дневная статистика",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Выбирай дату в календаре и редактируй записи по тем же категориям, что в боте.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.66f),
                )
            }
        }

        item {
            NutritionCard {
                CardHeadline(
                    title = "Выбранная дата",
                    trailing = {
                        FilledTonalButton(
                            onClick = {
                                showDatePicker(context, uiState.selectedDate, viewModel::setDate)
                            },
                        ) {
                            Text("Календарь")
                        }
                    },
                )
                Text(
                    text = StorageFormats.formatDateForDisplay(uiState.selectedDate),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (snapshot == null) {
            item {
                NutritionCard(modifier = Modifier.padding(bottom = 20.dp)) {
                    CardHeadline(title = "Загрузка")
                    Text(
                        text = "Подготавливаю запись сна и сводку по выбранной дате.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        } else {
            item {
                SingleEntryCard(
                    title = MealType.BREAKFAST.title,
                    value = snapshot.breakfast?.description,
                    highlight = true,
                    onEdit = {
                        dialog = DailyDialog.Meal(
                            mealType = MealType.BREAKFAST,
                            itemId = snapshot.breakfast?.id,
                            initialText = snapshot.breakfast?.description.orEmpty(),
                        )
                    },
                    onDelete = snapshot.breakfast?.let { { viewModel.deleteMeal(it.id) } },
                )
            }

            item {
                SingleEntryCard(
                    title = MealType.LUNCH.title,
                    value = snapshot.lunch?.description,
                    onEdit = {
                        dialog = DailyDialog.Meal(
                            mealType = MealType.LUNCH,
                            itemId = snapshot.lunch?.id,
                            initialText = snapshot.lunch?.description.orEmpty(),
                        )
                    },
                    onDelete = snapshot.lunch?.let { { viewModel.deleteMeal(it.id) } },
                )
            }

            item {
                SingleEntryCard(
                    title = MealType.DINNER.title,
                    value = snapshot.dinner?.description,
                    onEdit = {
                        dialog = DailyDialog.Meal(
                            mealType = MealType.DINNER,
                            itemId = snapshot.dinner?.id,
                            initialText = snapshot.dinner?.description.orEmpty(),
                        )
                    },
                    onDelete = snapshot.dinner?.let { { viewModel.deleteMeal(it.id) } },
                )
            }

            item {
                MultiEntryCard(
                    title = "Перекусы",
                    emptyText = "Перекусов пока нет.",
                    items = snapshot.snacks,
                    itemLabel = { it.description },
                    onAdd = {
                        dialog = DailyDialog.Meal(
                            mealType = MealType.SNACK,
                            itemId = null,
                            initialText = "",
                        )
                    },
                    onEdit = { snack ->
                        dialog = DailyDialog.Meal(
                            mealType = MealType.SNACK,
                            itemId = snack.id,
                            initialText = snack.description,
                        )
                    },
                    onDelete = { snack -> viewModel.deleteMeal(snack.id) },
                )
            }

            item {
                WaterCard(
                    count = snapshot.waterCount,
                    onIncrement = { viewModel.incrementWater(1) },
                    onDecrement = { viewModel.incrementWater(-1) },
                    onEdit = { dialog = DailyDialog.Water(snapshot.waterCount) },
                )
            }

            item {
                MultiEntryCard(
                    title = "Туалет",
                    emptyText = "Записей еще нет.",
                    items = snapshot.stools,
                    itemLabel = { "Оценка: ${it.quality}" },
                    onAdd = { dialog = DailyDialog.Stool(itemId = null, initialQuality = "") },
                    onEdit = { stool ->
                        dialog = DailyDialog.Stool(
                            itemId = stool.id,
                            initialQuality = stool.quality.toString(),
                        )
                    },
                    onDelete = { stool -> viewModel.deleteStool(stool.id) },
                )
            }

            item {
                SleepCard(
                    sleep = snapshot.sleep,
                    onEdit = {
                        dialog = DailyDialog.Sleep(
                            wakeupTime = snapshot.sleep.wakeupTime,
                            bedTime = snapshot.sleep.bedTime,
                            quality = snapshot.sleep.qualityDescription.orEmpty(),
                        )
                    },
                )
            }

            item {
                MultiEntryCard(
                    title = "Самочувствие",
                    emptyText = "Пока нет заметок о самочувствии.",
                    items = snapshot.feelings,
                    itemLabel = { it.description },
                    onAdd = { dialog = DailyDialog.Feeling(itemId = null, initialText = "") },
                    onEdit = { feeling ->
                        dialog = DailyDialog.Feeling(
                            itemId = feeling.id,
                            initialText = feeling.description,
                        )
                    },
                    onDelete = { feeling -> viewModel.deleteFeeling(feeling.id) },
                )
            }

            item {
                MultiEntryCard(
                    title = "Лекарства",
                    emptyText = "Раздел сохранен для совместимости с данными бота.",
                    items = snapshot.medicines,
                    itemLabel = {
                        if (it.dosage.isNullOrBlank()) it.name else "${it.name} · ${it.dosage}"
                    },
                    onAdd = { dialog = DailyDialog.Medicine(itemId = null, name = "", dosage = "") },
                    onEdit = { medicine ->
                        dialog = DailyDialog.Medicine(
                            itemId = medicine.id,
                            name = medicine.name,
                            dosage = medicine.dosage.orEmpty(),
                        )
                    },
                    onDelete = { medicine -> viewModel.deleteMedicine(medicine.id) },
                    bottomPadding = 20.dp,
                )
            }
        }
    }

    when (val currentDialog = dialog) {
        is DailyDialog.Meal -> MealDialog(
            mealType = currentDialog.mealType,
            initialText = currentDialog.initialText,
            onDismiss = { dialog = null },
            onConfirm = { value ->
                if (currentDialog.itemId == null || currentDialog.mealType.isPrimary) {
                    viewModel.upsertMeal(currentDialog.mealType, value)
                } else {
                    viewModel.updateMeal(currentDialog.itemId, value)
                }
                dialog = null
            },
        )

        is DailyDialog.Stool -> StoolDialog(
            initialQuality = currentDialog.initialQuality,
            onDismiss = { dialog = null },
            onConfirm = { value ->
                if (currentDialog.itemId == null) {
                    viewModel.addStool(value)
                } else {
                    viewModel.updateStool(currentDialog.itemId, value)
                }
                dialog = null
            },
        )

        is DailyDialog.Feeling -> FeelingDialog(
            initialText = currentDialog.initialText,
            onDismiss = { dialog = null },
            onConfirm = { value ->
                if (currentDialog.itemId == null) {
                    viewModel.addFeeling(value)
                } else {
                    viewModel.updateFeeling(currentDialog.itemId, value)
                }
                dialog = null
            },
        )

        is DailyDialog.Water -> WaterDialog(
            initialValue = currentDialog.initialValue,
            onDismiss = { dialog = null },
            onConfirm = { value ->
                viewModel.setWater(value)
                dialog = null
            },
        )

        is DailyDialog.Sleep -> SleepDialog(
            wakeupTime = currentDialog.wakeupTime,
            bedTime = currentDialog.bedTime,
            quality = currentDialog.quality,
            onDismiss = { dialog = null },
            onConfirm = { wakeupTime, bedTime, quality ->
                viewModel.upsertSleep(wakeupTime, bedTime, quality)
                dialog = null
            },
        )

        is DailyDialog.Medicine -> MedicineDialog(
            initialName = currentDialog.name,
            initialDosage = currentDialog.dosage,
            onDismiss = { dialog = null },
            onConfirm = { name, dosage ->
                if (currentDialog.itemId == null) {
                    viewModel.addMedicine(name, dosage)
                } else {
                    viewModel.updateMedicine(currentDialog.itemId, name, dosage)
                }
                dialog = null
            },
        )

        null -> Unit
    }
}

@Composable
private fun SingleEntryCard(
    title: String,
    value: String?,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    highlight: Boolean = false,
) {
    NutritionCard {
        CardHeadline(title = title)
        if (value.isNullOrBlank()) {
            EmptyLabel("Запись еще не добавлена.")
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        ActionRow(onEdit = onEdit, onDelete = onDelete)
    }
}

@Composable
private fun WaterCard(
    count: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEdit: () -> Unit,
) {
    NutritionCard {
        CardHeadline(title = "Вода")
        Text(
            text = "$count стак.",
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onDecrement) { Text("-1") }
            FilledTonalButton(onClick = onIncrement) { Text("+1") }
            FilledTonalButton(onClick = onEdit) { Text("Точно") }
        }
    }
}

@Composable
private fun SleepCard(
    sleep: SleepEntity,
    onEdit: () -> Unit,
) {
    NutritionCard {
        CardHeadline(title = "Сон")
        Text(
            text = "${sleep.wakeupTime} - ${sleep.bedTime}",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = sleep.qualityDescription ?: "Описание качества сна пока не добавлено.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        ActionRow(onEdit = onEdit, onDelete = null)
    }
}

@Composable
private fun <T> MultiEntryCard(
    title: String,
    emptyText: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onAdd: () -> Unit,
    onEdit: (T) -> Unit,
    onDelete: (T) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    NutritionCard(modifier = Modifier.padding(bottom = bottomPadding)) {
        CardHeadline(
            title = title,
            trailing = {
                FilledTonalButton(onClick = onAdd) {
                    Text("Добавить")
                }
            },
        )

        if (items.isEmpty()) {
            EmptyLabel(emptyText)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEachIndexed { index, item ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = itemLabel(item),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ActionRow(
                            onEdit = { onEdit(item) },
                            onDelete = { onDelete(item) },
                        )
                        if (index != items.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalButton(onClick = onEdit) {
            Text("Изменить")
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text("Удалить")
            }
        }
    }
}

@Composable
private fun MealDialog(
    mealType: MealType,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(mealType.title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Описание") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = value.trim()
                if (normalized.isNotEmpty()) onConfirm(normalized)
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun FeelingDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Самочувствие") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Что чувствуешь?") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = value.trim()
                if (normalized.isNotEmpty()) onConfirm(normalized)
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun StoolDialog(
    initialQuality: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember(initialQuality) { mutableStateOf(initialQuality) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Туалет") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Оценка 0..7") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = value.toIntOrNull()
                if (parsed != null && parsed in 0..7) onConfirm(parsed)
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun WaterDialog(
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вода") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Количество стаканов") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = value.toIntOrNull()
                if (parsed != null) onConfirm(parsed)
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun MedicineDialog(
    initialName: String,
    initialDosage: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var dosage by remember(initialDosage) { mutableStateOf(initialDosage) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Лекарство") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Дозировка") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = name.trim()
                if (normalized.isNotEmpty()) onConfirm(normalized, dosage.trim().ifBlank { null })
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun SleepDialog(
    wakeupTime: String,
    bedTime: String,
    quality: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit,
) {
    val context = LocalContext.current
    var wakeup by remember(wakeupTime) { mutableStateOf(wakeupTime) }
    var bed by remember(bedTime) { mutableStateOf(bedTime) }
    var sleepQuality by remember(quality) { mutableStateOf(quality) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сон") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Подъем: $wakeup")
                    TextButton(onClick = {
                        showTimePicker(context, wakeup) { wakeup = it }
                    }) {
                        Text("Изменить")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Отбой: $bed")
                    TextButton(onClick = {
                        showTimePicker(context, bed) { bed = it }
                    }) {
                        Text("Изменить")
                    }
                }
                OutlinedTextField(
                    value = sleepQuality,
                    onValueChange = { sleepQuality = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Качество сна") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(wakeup, bed, sleepQuality.trim().ifBlank { null })
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

private sealed interface DailyDialog {
    data class Meal(
        val mealType: MealType,
        val itemId: Long?,
        val initialText: String,
    ) : DailyDialog

    data class Stool(
        val itemId: Long?,
        val initialQuality: String,
    ) : DailyDialog

    data class Feeling(
        val itemId: Long?,
        val initialText: String,
    ) : DailyDialog

    data class Water(
        val initialValue: Int,
    ) : DailyDialog

    data class Sleep(
        val wakeupTime: String,
        val bedTime: String,
        val quality: String,
    ) : DailyDialog

    data class Medicine(
        val itemId: Long?,
        val name: String,
        val dosage: String,
    ) : DailyDialog
}
