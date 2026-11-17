package com.pamurlykin.nutritionassistant.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.data.local.CatalogItem
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
    initial: CatalogItem? = null,
    initialName: String = "",
    initialType: String = FoodType.HOME.storageValue,
    entries: List<CatalogItem>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreateProduct: (String, (CatalogItem) -> Unit) -> Unit,
    onSave: (Long?, String, String, String, List<Long>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: initialName) }
    var type by rememberSaveable { mutableStateOf(initial?.foodType?.takeIf { value -> FoodType.entries.any { it.storageValue == value } } ?: initialType) }
    var selected by rememberSaveable { mutableStateOf(initial?.productIds.orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    var creatingProduct by rememberSaveable { mutableStateOf(false) }
    var createdProducts by remember { mutableStateOf(emptyList<CatalogEntryEntity>()) }
    val isDish = kind == CatalogKind.DISH
    val products = (entries.filter { it.kind == CatalogKind.PRODUCT }.map { it.entry } + initial?.products.orEmpty() + createdProducts)
        .distinctBy { it.id }.sortedBy { it.normalizedName }
    val matching = products.filter { catalogMatches(it, query) }
    val valid = name.isNotBlank() && name.trim().length <= 1000 &&
        (!isDish || selected.isNotEmpty())
    if (creatingProduct) {
        CatalogEditor(kind = CatalogKind.PRODUCT, initialName = query.trim(), entries = entries, busy = busy,
            onDismiss = { creatingProduct = false }, onCreateProduct = onCreateProduct,
            onSave = { _, _, productName, _, _ -> onCreateProduct(productName) { product ->
                createdProducts = createdProducts + product.entry
                selected = (selected + product.id).distinct()
                query = ""
                creatingProduct = false
            } })
        return
    }
    AppDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        busy = busy,
        hasChanges = name != (initial?.name ?: initialName) || selected != initial?.productIds.orEmpty() ||
            (isDish && type != (initial?.foodType ?: initialType)),
        title = { Text(when (kind) {
            CatalogKind.DISH -> "Блюдо в справочнике"
            CatalogKind.PRODUCT -> "Продукт в справочнике"
            else -> "Лекарство в справочнике"
        }) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(name, { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, isError = name.length > 1000)
                if (isDish) {
                    Text("Созданные продукты остаются в справочнике даже при отмене блюда.", style = MaterialTheme.typography.bodySmall)
                    FoodTypePicker(type) { type = it }
                    Text("Состав блюда · выбрано продуктов: ${selected.size}", style = MaterialTheme.typography.titleSmall)
                    AppTextField(query, { query = it }, label = { Text("Поиск продуктов") },
                        supportingText = { Text("Введите часть названия и отметьте продукты галочками.") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                    if (matching.isEmpty()) Text("Продукты не найдены.")
                    else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp).testTag("product-options")) {
                        items(matching, key = { it.id }) { product ->
                            Row(Modifier.fillMaxWidth().testTag("product-choice-${product.id}")
                                .toggleable(value = product.id in selected, enabled = !busy, role = Role.Checkbox,
                                    onValueChange = { checked -> selected = if (checked) (selected + product.id).distinct() else selected - product.id }),
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = product.id in selected, onCheckedChange = null)
                                Text(product.name + if (product.archived) " · убран из справочника" else "", Modifier.weight(1f))
                            }
                        }
                    }
                    if (query.isNotBlank() && products.none { it.normalizedName == normalizeName(query) }) {
                        TextButton(enabled = !busy, onClick = { creatingProduct = true }) { Text("Добавить продукт «${query.trim()}» и выбрать") }
                    }
                }
                if (initial != null) Text("Изменения применяются к новым событиям. Состав уже записанных событий сохраняется.")
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !busy, onClick = { onSave(initial?.id, kind, name, if (isDish) type else "", if (isDish) selected else emptyList()) }) { Text("Сохранить") }
        },
        dismissButton = { AppDismissButton(enabled = !busy) },
    )
}

data class SelectedCatalogItem(
    val id: Long,
    val name: String,
    val foodType: String = "",
    val ingredients: List<String> = emptyList(),
    val dosage: String = "",
    val kind: String = CatalogKind.DISH,
)

private val SelectedItemsSaver = listSaver<List<SelectedCatalogItem>, String>(
    save = { items -> items.map { item -> JSONObject().put("id", item.id).put("name", item.name)
        .put("type", item.foodType).put("ingredients", JSONArray(item.ingredients)).put("dose", item.dosage).put("kind", item.kind).toString() } },
    restore = { rows -> rows.map { row -> JSONObject(row).let { value ->
        val ingredients = value.getJSONArray("ingredients")
        SelectedCatalogItem(value.getLong("id"), value.getString("name"), value.getString("type"),
            List(ingredients.length()) { ingredients.getString(it) }, value.getString("dose"), value.getString("kind"))
    } } },
)

@Composable
fun CatalogEventDialog(
    title: String,
    kind: String,
    initial: List<SelectedCatalogItem>,
    entries: List<CatalogItem>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, List<Long>, (CatalogItem) -> Unit) -> Unit,
    onSave: (List<SelectedCatalogItem>) -> Unit,
) {
    var selected by rememberSaveable(stateSaver = SelectedItemsSaver) { mutableStateOf(initial) }
    var query by rememberSaveable { mutableStateOf("") }
    var foodType by rememberSaveable { mutableStateOf(FoodType.HOME.storageValue) }
    var foodKind by rememberSaveable { mutableStateOf(CatalogKind.DISH) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val isDish = kind == CatalogKind.DISH
    if (creating) {
        CatalogEditor(kind = if (isDish) foodKind else kind, initialName = query.trim(), initialType = foodType,
            entries = entries, busy = busy, onDismiss = { creating = false },
            onCreateProduct = { name, success -> onCreate(CatalogKind.PRODUCT, name, "", emptyList(), success) },
            onSave = { _, entryKind, name, type, productIds ->
                onCreate(entryKind, name, type, productIds) { entry ->
                    if (selected.none { it.id == entry.id }) selected = selected + SelectedCatalogItem(entry.id, entry.name, entry.foodType, entry.ingredients, kind = entry.kind)
                    creating = false
                    query = ""
                }
            })
        return
    }
    val matching = entries.filter { it.kind == (if (isDish) foodKind else kind) &&
        (!isDish || foodKind == CatalogKind.PRODUCT || it.foodType == foodType || it.foodType == "unknown") && catalogMatches(it, query) }
    val exact = matching.any { it.normalizedName == normalizeName(query) }
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, busy = busy, hasChanges = selected != initial,
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Выбрано: " + selected.size, style = MaterialTheme.typography.titleMedium)
                selected.forEach { item ->
                    Column {
                        Text(item.name + if (isDish) " · " + foodItemTypeTitle(item.kind, item.foodType) else "")
                        if (item.ingredients.isNotEmpty()) Text(item.ingredients.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        if (!isDish) AppTextField(item.dosage, { value ->
                            selected = selected.map { if (it.id == item.id) it.copy(dosage = value) else it }
                        }, label = { Text("Дозировка / количество (необязательно)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, isError = item.dosage.length > 1000)
                        TextButton(enabled = !busy, onClick = { selected = selected.filterNot { it.id == item.id } }) { Text("Убрать из события") }
                    }
                    HorizontalDivider()
                }
                if (isDish) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = foodKind == CatalogKind.DISH, onClick = { foodKind = CatalogKind.DISH; query = "" }, label = { Text("Блюда") })
                        FilterChip(selected = foodKind == CatalogKind.PRODUCT, onClick = { foodKind = CatalogKind.PRODUCT; query = "" }, label = { Text("Продукты") })
                    }
                    if (foodKind == CatalogKind.DISH) FoodTypePicker(foodType) { foodType = it }
                }
                AppTextField(query, { query = it }, label = { Text("Поиск по части названия") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                matching.take(40).forEach { entry ->
                    val chosen = selected.any { it.id == entry.id }
                    TextButton(enabled = !chosen && !busy, onClick = {
                        selected = selected + SelectedCatalogItem(entry.id, entry.name, entry.foodType, entry.ingredients, kind = entry.kind)
                        query = ""
                    }) { Text(entry.name + if (chosen) " · выбрано" else "") }
                }
                if (matching.size > 40) Text("Показаны первые 40 совпадений. Уточните название.")
                if (matching.isEmpty()) Text("Совпадений нет.")
                if (query.isNotBlank() && !exact) TextButton(enabled = !busy, onClick = { creating = true }) {
                    Text("Добавить «" + query.trim() + "» в справочник и выбрать")
                }
                Text("Событие может включать несколько " + if (isDish) "блюд и продуктов." else "лекарств.")
                Text("Созданные записи справочника остаются в нём даже при отмене события.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = selected.isNotEmpty() && selected.all { it.dosage.length <= 1000 } && !busy, onClick = { onSave(selected) }) { Text("Сохранить событие") } },
        dismissButton = { AppDismissButton(enabled = !busy) })
}
