package com.pamurlykin.nutritionassistant.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.data.local.CatalogEntryEntity
import com.pamurlykin.nutritionassistant.data.model.*

@Composable
fun FoodTypePicker(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FoodType.entries.forEach { type ->
            FilterChip(selected = selected == type.storageValue, onClick = { onSelect(type.storageValue) }, label = { Text(type.title) })
        }
    }
}

@Composable
fun CatalogEditor(
    kind: String,
    initial: CatalogEntryEntity? = null,
    initialName: String = "",
    initialType: String = FoodType.HOME.storageValue,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, String, List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: initialName) }
    var type by remember { mutableStateOf(initial?.foodType?.takeIf { value -> FoodType.entries.any { it.storageValue == value } } ?: initialType) }
    var ingredients by remember { mutableStateOf(initial?.ingredients?.joinToString("\n").orEmpty()) }
    val parsed = ingredients.split('\n', ',', ';').map { it.trim() }.filter { it.isNotBlank() }
    val isDish = kind == CatalogKind.DISH
    val valid = name.isNotBlank() && name.trim().length <= 1000 &&
        (!isDish || type != FoodType.HOME.storageValue || parsed.isNotEmpty()) && parsed.all { it.length <= 1000 }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isDish) "Блюдо в справочнике" else "Лекарство в справочнике") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), isError = name.length > 1000)
                if (isDish) {
                    FoodTypePicker(type) { type = it }
                    OutlinedTextField(ingredients, { ingredients = it }, label = { Text("Ингредиенты, по одному на строку") },
                        supportingText = { Text(if (type == FoodType.HOME.storageValue) "Для домашнего блюда состав обязателен. Укажите свой рецепт." else "Можно указать известный состав.") },
                        modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
                if (initial != null) Text("Изменения применяются к новым событиям. Состав уже записанных событий сохраняется.")
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !busy, onClick = { onSave(initial?.id, kind, name, if (isDish) type else "", parsed) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } },
    )
}

data class SelectedCatalogItem(
    val id: Long,
    val name: String,
    val foodType: String = "",
    val ingredients: List<String> = emptyList(),
    val dosage: String = "",
)

@Composable
fun CatalogEventDialog(
    title: String,
    kind: String,
    initial: List<SelectedCatalogItem>,
    entries: List<CatalogEntryEntity>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, List<String>, (CatalogEntryEntity) -> Unit) -> Unit,
    onSave: (List<SelectedCatalogItem>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var foodType by remember { mutableStateOf(FoodType.HOME.storageValue) }
    var creating by remember { mutableStateOf(false) }
    val isDish = kind == CatalogKind.DISH
    if (creating) {
        CatalogEditor(kind = kind, initialName = query.trim(), initialType = foodType, busy = busy, onDismiss = { creating = false },
            onSave = { _, _, name, type, ingredients ->
                onCreate(name, type, ingredients) { entry ->
                    if (selected.none { it.id == entry.id }) selected = selected + SelectedCatalogItem(entry.id, entry.name, entry.foodType, entry.ingredients)
                    creating = false
                    query = ""
                }
            })
        return
    }
    val matching = entries.filter { it.kind == kind && (!isDish || it.foodType == foodType) && catalogMatches(it, query) }
    val exact = matching.any { it.normalizedName == normalizeName(query) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Выбрано: " + selected.size, style = MaterialTheme.typography.titleMedium)
                selected.forEach { item ->
                    Column {
                        Text(item.name + if (isDish) " · " + FoodType.titleFor(item.foodType) else "")
                        if (item.ingredients.isNotEmpty()) Text(item.ingredients.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        if (!isDish) OutlinedTextField(item.dosage, { value ->
                            selected = selected.map { if (it.id == item.id) it.copy(dosage = value) else it }
                        }, label = { Text("Дозировка / количество (необязательно)") }, modifier = Modifier.fillMaxWidth())
                        TextButton(enabled = !busy, onClick = { selected = selected.filterNot { it.id == item.id } }) { Text("Убрать из события") }
                    }
                    HorizontalDivider()
                }
                if (isDish) FoodTypePicker(foodType) { foodType = it }
                OutlinedTextField(query, { query = it }, label = { Text("Поиск по части названия") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                matching.take(40).forEach { entry ->
                    val chosen = selected.any { it.id == entry.id }
                    TextButton(enabled = !chosen && !busy, onClick = {
                        selected = selected + SelectedCatalogItem(entry.id, entry.name, entry.foodType, entry.ingredients)
                        query = ""
                    }) { Text(entry.name + if (chosen) " · выбрано" else "") }
                }
                if (matching.size > 40) Text("Показаны первые 40 совпадений. Уточните название.")
                if (matching.isEmpty()) Text("Совпадений нет.")
                if (query.isNotBlank() && !exact) TextButton(enabled = !busy, onClick = { creating = true }) {
                    Text("Добавить «" + query.trim() + "» в справочник и выбрать")
                }
                Text("Событие может включать несколько " + if (isDish) "блюд из разных типов еды." else "лекарств.")
            }
        },
        confirmButton = { TextButton(enabled = selected.isNotEmpty() && !busy, onClick = { onSave(selected) }) { Text("Сохранить событие") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } })
}
