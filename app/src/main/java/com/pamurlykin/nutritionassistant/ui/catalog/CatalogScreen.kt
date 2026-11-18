package com.pamurlykin.nutritionassistant.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.verticalScroll
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
    val recentIds by viewModel.recentIds.collectAsStateWithLifecycle()
    val conflicts by viewModel.seedConflicts.collectAsStateWithLifecycle()
    var resolvingSeed by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var linksId by rememberSaveable { mutableStateOf<Long?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var quick by rememberSaveable { mutableStateOf("all") }
    var archived by rememberSaveable { mutableStateOf(false) }
    var searchIngredients by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(CatalogKind.DISH) }
    var type by rememberSaveable { mutableStateOf(FoodType.HOME.storageValue) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var entryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var archiveId by rememberSaveable { mutableStateOf<Long?>(null) }
    val entry = entries.find { it.id == entryId }
    val visible = entries.filter { it.archived == archived && it.kind == kind && (kind != CatalogKind.DISH || it.foodType == type || it.foodType == "unknown") &&
        (kind != CatalogKind.PRODUCT || category == null || it.entry.categoryCode == category) &&
        (quick != "favorite" || it.entry.favorite) && (quick != "recent" || it.id in recentIds) && it.matches(query, entries, searchIngredients) }
        .let { if (quick == "recent") it.sortedBy { entry -> recentIds.indexOf(entry.id) } else it }
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
            if (conflicts.isNotEmpty()) TextButton(enabled = !busy, onClick = { resolvingSeed = conflicts.first().seed.key }) { Text("Уточнить совпадения встроенного каталога: ${conflicts.size}") }
            if (kind == CatalogKind.DISH) FoodTypePicker(type) { type = it }
            if (kind == CatalogKind.PRODUCT) CategoryPicker(category, allowAll = true) { category = it }
            QuickCatalogFilter(quick, !busy) { quick = it }
            FilterChip(selected = archived, onClick = { archived = !archived }, label = { Text("Архив") })
            AppTextField(query, { query = it }, label = { Text("Поиск по части названия") }, modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !busy, onClick = { entryId = null; editing = true }) { Text("Добавить запись") }
            if (kind != CatalogKind.MEDICINE) FilterChip(selected = searchIngredients, onClick = { searchIngredients = !searchIngredients }, label = { Text("Искать в составе") })
            if (kind == CatalogKind.DISH) Text("Собирайте состав блюда из продуктов. Типовые блюда можно менять под свой рецепт.")
        }
        if (visible.isEmpty()) item { EmptyLabel("Записей нет. Добавьте первую запись.") }
        items(visible, key = { it.id }) { item ->
            NutritionCard(modifier = Modifier.testTag("catalog-entry-${item.id}")) {
                Text(item.displayName, style = MaterialTheme.typography.titleLarge)
                if (item.kind == CatalogKind.DISH) Text(FoodType.titleFor(item.foodType))
                if (item.ingredients.isNotEmpty()) Text(item.ingredients.joinToString(", "))
                if (item.kind != CatalogKind.MEDICINE) {
                    Text(compositionTitle(item.entry.compositionState), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { viewingId = item.id }) { Text("Состав и источники") }
                }
                if (item.kind == CatalogKind.PRODUCT) TextButton(enabled = !busy, onClick = { linksId = item.id },
                    modifier = Modifier.testTag("product-links-${item.id}")) { Text("Ссылки на товар") }
                item.entry.comment?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                TextButton(enabled = !busy, onClick = { viewModel.favorite(item.id, !item.entry.favorite) }, modifier = Modifier.testTag("favorite-${item.id}")) { Text(if (item.entry.favorite) "Убрать из избранного" else "В избранное") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = { entryId = item.id; editing = true }) { Text("Изменить") }
                    if (item.archived) TextButton(enabled = !busy, onClick = { viewModel.restoreCatalog(item.id) }) { Text("Вернуть в справочник") }
                    else TextButton(enabled = !busy, onClick = { archiveId = item.id }) { Text("Убрать из справочника") }
                }
            }
        }
    }
    archiveId?.let { id -> ConfirmActionDialog("Убрать из справочника?", "Позиция исчезнет из нового выбора. Составы и история сохранятся.", "Убрать",
        onDismiss = { archiveId = null }, onConfirm = { archiveId = null; viewModel.archiveCatalog(id) }) }
    if (editing && (entryId == null || entry != null)) CatalogEditor(kind = kind, initial = entry, initialType = type, entries = entries, busy = busy,
        onDismiss = { editing = false }, onCreate = viewModel::saveDraft, onSave = { draft -> viewModel.saveDraft(draft) { editing = false } })
    entries.find { it.id == linksId && it.kind == CatalogKind.PRODUCT }?.let { product ->
        key(product.id) { ProductLinksDialog(product, viewModel, busy) { linksId = null } }
    }
    viewingId?.let { id -> CompositionDialog(runCatching { CompositionSnapshot.encode(catalogSnapshot(id, entries)) }.getOrNull()) { viewingId = null } }
    conflicts.find { it.seed.key == resolvingSeed }?.let { conflict ->
        AppDialog(onDismissRequest = { resolvingSeed = null }, title = { Text("Встроенный продукт: " + conflict.seed.name) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Несколько прежних записей совпали по имени. Выберите, с какой связать встроенный продукт; их состав и архив сохранятся.")
                conflict.candidates.forEach { candidate ->
                    TextButton(enabled = !busy, onClick = { viewModel.resolveSeed(conflict.seed.key, candidate.id); resolvingSeed = null }) {
                        Text(candidate.displayName + " · " + candidate.entry.publicId.take(8) + if (candidate.archived) " · архив" else "")
                    }
                    Text(candidate.entry.comment.orEmpty() + " · " + compositionTitle(candidate.entry.compositionState))
                }
                TextButton(enabled = !busy, onClick = { viewModel.resolveSeed(conflict.seed.key, null); resolvingSeed = null }) { Text("Создать встроенный отдельно") }
            } }, confirmButton = { AppDismissButton() }, dismissButton = null)
    }
}
