package com.pamurlykin.nutritionassistant.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.nutritionassistant.data.local.CatalogEntryEntity
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.ui.components.*
import com.pamurlykin.nutritionassistant.ui.daily.DailyStatsViewModel

@Composable
fun CatalogScreen(viewModel: DailyStatsViewModel) {
    val entries by viewModel.catalog.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(CatalogKind.DISH) }
    var type by remember { mutableStateOf(FoodType.HOME.storageValue) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<CatalogEntryEntity?>(null) }
    val visible = entries.filter { it.kind == kind && (kind == CatalogKind.MEDICINE || it.foodType == type || it.foodType == "unknown") && catalogMatches(it, query) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Справочники", style = MaterialTheme.typography.headlineLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == CatalogKind.DISH, onClick = { kind = CatalogKind.DISH }, label = { Text("Блюда") })
                FilterChip(selected = kind == CatalogKind.MEDICINE, onClick = { kind = CatalogKind.MEDICINE }, label = { Text("Лекарства") })
            }
            if (kind == CatalogKind.DISH) FoodTypePicker(type) { type = it }
            OutlinedTextField(query, { query = it }, label = { Text("Поиск по части названия") }, modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !busy, onClick = { entry = null; editing = true }) { Text("Добавить запись") }
            if (kind == CatalogKind.DISH) Text("Типовые блюда можно менять под свой рацион. Состав домашнего блюда должен соответствовать вашему рецепту.")
        }
        if (visible.isEmpty()) item { EmptyLabel("Записей нет. Добавьте первую запись.") }
        items(visible, key = { it.id }) { item ->
            NutritionCard {
                Text(item.name, style = MaterialTheme.typography.titleLarge)
                if (item.kind == CatalogKind.DISH) Text(FoodType.titleFor(item.foodType))
                if (item.ingredients.isNotEmpty()) Text(item.ingredients.joinToString(", "))
                Row {
                    TextButton(enabled = !busy, onClick = { entry = item; editing = true }) { Text("Изменить") }
                    TextButton(enabled = !busy, onClick = { viewModel.archiveCatalog(item.id) }) { Text("Убрать из справочника") }
                }
            }
        }
    }
    if (editing) CatalogEditor(kind = kind, initial = entry, initialType = type, busy = busy, onDismiss = { editing = false },
        onSave = { id, entryKind, name, foodType, ingredients ->
            viewModel.saveCatalog(id, entryKind, name, foodType, ingredients) { editing = false }
        })
}
