package com.pamurlykin.nutritionassistant.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.nutritionassistant.data.local.CatalogItem
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.ui.components.*
import com.pamurlykin.nutritionassistant.ui.daily.DailyStatsViewModel

@Composable
fun CatalogScreen(viewModel: DailyStatsViewModel) {
    val entries by viewModel.catalog.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(CatalogKind.DISH) }
    var type by rememberSaveable { mutableStateOf(FoodType.HOME.storageValue) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var entryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var archiveId by rememberSaveable { mutableStateOf<Long?>(null) }
    val entry = entries.find { it.id == entryId }
    val visible = entries.filter { it.kind == kind && (kind != CatalogKind.DISH || it.foodType == type || it.foodType == "unknown") && catalogMatches(it, query) }
    LazyColumn(modifier = Modifier.testTag("catalog-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Меню", style = MaterialTheme.typography.headlineMedium)
            Text("Справочники блюд, продуктов и лекарств", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == CatalogKind.DISH, onClick = { kind = CatalogKind.DISH; query = "" }, label = { Text("Блюда") })
                FilterChip(selected = kind == CatalogKind.PRODUCT, onClick = { kind = CatalogKind.PRODUCT; query = "" }, label = { Text("Продукты") })
                FilterChip(selected = kind == CatalogKind.MEDICINE, onClick = { kind = CatalogKind.MEDICINE; query = "" }, label = { Text("Лекарства") })
            }
            if (kind == CatalogKind.DISH) FoodTypePicker(type) { type = it }
            AppTextField(query, { query = it }, label = { Text("Поиск по части названия") }, modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !busy, onClick = { entryId = null; editing = true }) { Text("Добавить запись") }
            if (kind == CatalogKind.DISH) Text("Собирайте состав блюда из продуктов. Типовые блюда можно менять под свой рецепт.")
        }
        if (visible.isEmpty()) item { EmptyLabel("Записей нет. Добавьте первую запись.") }
        items(visible, key = { it.id }) { item ->
            NutritionCard(modifier = Modifier.testTag("catalog-entry-${item.id}")) {
                Text(item.name, style = MaterialTheme.typography.titleLarge)
                if (item.kind == CatalogKind.DISH) Text(FoodType.titleFor(item.foodType))
                if (item.ingredients.isNotEmpty()) Text(item.ingredients.joinToString(", "))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = { entryId = item.id; editing = true }) { Text("Изменить") }
                    TextButton(enabled = !busy, onClick = { archiveId = item.id }) { Text("Убрать из справочника") }
                }
            }
        }
    }
    archiveId?.let { id -> ConfirmActionDialog("Убрать из справочника?", "Позиция исчезнет из нового выбора. Составы и история сохранятся.", "Убрать",
        onDismiss = { archiveId = null }, onConfirm = { archiveId = null; viewModel.archiveCatalog(id) }) }
    if (editing && (entryId == null || entry != null)) CatalogEditor(kind = kind, initial = entry, initialType = type, entries = entries, busy = busy, onDismiss = { editing = false },
        onCreateProduct = { name, success -> viewModel.saveCatalog(null, CatalogKind.PRODUCT, name, "", emptyList(), success) },
        onSave = { id, entryKind, name, foodType, productIds ->
            viewModel.saveCatalog(id, entryKind, name, foodType, productIds) { editing = false }
        })
}
