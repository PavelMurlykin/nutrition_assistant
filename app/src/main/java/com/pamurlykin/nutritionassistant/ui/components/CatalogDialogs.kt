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
fun FoodTypePicker(selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FoodType.entries.forEach { type ->
            FilterChip(selected = selected == type.storageValue, enabled = enabled, onClick = { onSelect(type.storageValue) }, label = { Text(type.title) })
        }
    }
}

@Composable
fun CatalogEditor(
    kind: String, initial: CatalogItem? = null, initialName: String = "", initialType: String = FoodType.HOME.storageValue,
    entries: List<CatalogItem>, busy: Boolean, onDismiss: () -> Unit,
    onCreate: (CatalogDraft, (CatalogItem) -> Unit) -> Unit, onSave: (CatalogDraft) -> Unit,
) {
    val oldDetails = initial?.entry?.details() ?: CatalogDetails(categoryCode = if (kind == CatalogKind.PRODUCT) "other" else null,
        composition = if (kind == CatalogKind.DISH) "partial" else "unknown")
    val oldComponents = initial?.componentsForEdit().orEmpty()
    var name by rememberSaveable { mutableStateOf(initial?.name ?: initialName) }
    var type by rememberSaveable { mutableStateOf(initial?.foodType?.takeIf { it in setOf("home", "restaurant") } ?: initialType) }
    var selected by rememberSaveable { mutableStateOf(oldComponents.map { it.productId }) }
    var quantities by rememberSaveable { mutableStateOf(oldComponents.associate { it.productId to it.quantity.orEmpty() }) }
    var units by rememberSaveable { mutableStateOf(oldComponents.associate { it.productId to it.unit.orEmpty() }) }
    var category by rememberSaveable { mutableStateOf(oldDetails.categoryCode) }
    var variant by rememberSaveable { mutableStateOf(oldDetails.productKind) }
    var brand by rememberSaveable { mutableStateOf(oldDetails.brand) }
    var label by rememberSaveable { mutableStateOf(oldDetails.variant) }
    var composition by rememberSaveable { mutableStateOf(oldDetails.composition) }
    var source by rememberSaveable { mutableStateOf(oldDetails.source.orEmpty()) }
    var checkedOn by rememberSaveable { mutableStateOf(oldDetails.checkedOn) }
    var comment by rememberSaveable { mutableStateOf(oldDetails.comment.orEmpty()) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var amountsOpen by rememberSaveable { mutableStateOf(false) }
    var productCompositionOpen by rememberSaveable { mutableStateOf(oldComponents.isNotEmpty()) }
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var creatingProduct by rememberSaveable { mutableStateOf(false) }
    var created by remember { mutableStateOf(emptyList<CatalogItem>()) }
    val isDish = kind == CatalogKind.DISH
    val isFood = kind != CatalogKind.MEDICINE
    val products = (entries + created).distinctBy { it.id }.filter { it.kind == CatalogKind.PRODUCT && it.id != initial?.id && (!it.archived || it.id in selected) }.sortedBy { it.normalizedName }
    val matching = products.filter { catalogMatches(it, query) }
    val draft = CatalogDraft(initial?.id, kind, name, if (isDish) type else "",
        CatalogDetails(category, variant, brand, label, composition, source.takeIf { it.isNotBlank() }, checkedOn, comment.takeIf { it.isNotBlank() }),
        selected.map { ComponentSelection(it, quantities[it]?.takeIf(String::isNotBlank), units[it]?.takeIf(String::isNotBlank)) })
    val valid = name.isNotBlank() && name.trim().length <= 1000 && (!isDish || selected.isNotEmpty()) &&
        (kind != CatalogKind.PRODUCT || category != null) && runCatching { draft.details.validated(kind); draft.components.forEach { Portion(it.quantity, it.unit).validated() } }.isSuccess
    if (creatingProduct) {
        CatalogEditor(kind = CatalogKind.PRODUCT, initialName = query.trim(), entries = entries, busy = busy,
            onDismiss = { creatingProduct = false }, onCreate = onCreate, onSave = { child ->
                onCreate(child) { product -> created = created + product; selected = (selected + product.id).distinct(); query = ""; creatingProduct = false }
            })
        return
    }
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, busy = busy,
        hasChanges = name != (initial?.name ?: initialName) || type != (initial?.foodType?.takeIf { it in setOf("home", "restaurant") } ?: initialType) ||
            draft.details != oldDetails || draft.components != oldComponents,
        title = { Text(when (kind) { CatalogKind.DISH -> "Блюдо в справочнике"; CatalogKind.PRODUCT -> "Продукт в справочнике"; else -> "Лекарство в справочнике" }) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(name, { name = it }, label = { Text("Название") }, enabled = !busy, isError = name.length > 1000)
                if (kind == CatalogKind.PRODUCT) {
                    CategoryPicker(category, enabled = !busy) { category = it }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(enabled = !busy, selected = variant == "basic", onClick = { variant = "basic" }, label = { Text("Базовый продукт") })
                        FilterChip(enabled = !busy, selected = variant == "variant", onClick = { variant = "variant" }, label = { Text("Конкретный товар") })
                    }
                    if (variant == "variant" || brand.isNotEmpty() || label.isNotEmpty()) {
                        AppTextField(brand, { brand = it }, label = { Text("Марка (необязательно)") }, enabled = !busy)
                        AppTextField(label, { label = it }, label = { Text("Уточнение варианта") }, enabled = !busy)
                    }
                    TextButton(onClick = { productCompositionOpen = !productCompositionOpen }) { Text(if (productCompositionOpen) "Скрыть состав продукта" else "Уточнить состав продукта") }
                }
                if (isDish || kind == CatalogKind.PRODUCT && productCompositionOpen) {
                    Text("Созданные продукты остаются в справочнике даже при отмене блюда.", style = MaterialTheme.typography.bodySmall)
                    if (isDish) FoodTypePicker(type, enabled = !busy) { type = it }
                    Text((if (isDish) "Состав блюда" else "Состав продукта") + " · выбрано продуктов: ${selected.size}", style = MaterialTheme.typography.titleSmall)
                    AppTextField(query, { query = it }, label = { Text("Поиск продуктов") }, enabled = !busy)
                    TextButton(onClick = { amountsOpen = !amountsOpen }) { Text(if (amountsOpen) "Скрыть количества компонентов" else "Количества компонентов (необязательно)") }
                    if (matching.isEmpty()) Text("Продукты не найдены.")
                    else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp).testTag("product-options")) {
                        items(matching, key = { it.id }) { product ->
                            Column {
                                Row(Modifier.fillMaxWidth().testTag("product-choice-${product.id}")
                                    .toggleable(value = product.id in selected, enabled = !busy, role = Role.Checkbox, onValueChange = { checked ->
                                        selected = if (checked) (selected + product.id).distinct() else selected - product.id
                                        if (composition == "confirmed") composition = "partial"
                                    }), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = product.id in selected, onCheckedChange = null)
                                    Text(product.displayName + if (product.archived) " · убран из справочника" else "", Modifier.weight(1f))
                                }
                                if (amountsOpen && product.id in selected) PortionFields(quantities[product.id].orEmpty(), units[product.id]?.takeIf(String::isNotBlank), !busy) { amount, unit ->
                                    quantities = quantities + (product.id to amount); units = units + (product.id to unit.orEmpty())
                                    if (composition == "confirmed") composition = "partial"
                                }
                            }
                        }
                    }
                    if (query.isNotBlank()) TextButton(enabled = !busy, onClick = { creatingProduct = true }) { Text("Добавить продукт «${query.trim()}» и выбрать") }
                }
                TextButton(onClick = { detailsOpen = !detailsOpen }) { Text(if (detailsOpen) "Скрыть сведения и комментарий" else "Сведения о составе и комментарий") }
                if (detailsOpen) {
                    if (isFood) {
                        listOf("unknown", "partial", "confirmed").forEach { state -> AppRadioChoice(compositionTitle(state), composition == state, !busy) { composition = state } }
                        if (composition == "confirmed") Text("Для подтверждённого состава нужны источник и дата проверки.", style = MaterialTheme.typography.bodySmall)
                        AppTextField(source, { source = it }, label = { Text("Источник состава") }, enabled = !busy, isError = source.length > 1000 || composition == "confirmed" && source.isBlank())
                        TextButton(onClick = { dateOpen = true }) { Text("Дата проверки: " + (checkedOn ?: "не указана")) }
                        if (checkedOn != null) TextButton(onClick = { checkedOn = null }) { Text("Очистить дату проверки") }
                        Text("Общее название не подтверждает состав. Для товара сверяйте упаковку.", style = MaterialTheme.typography.bodySmall)
                    }
                    AppTextField(comment, { comment = it }, label = { Text("Комментарий (необязательно)") }, enabled = !busy, singleLine = false, isError = comment.length > 1000)
                }
                if (initial != null) Text("Изменения применяются к новым событиям. Состав уже записанных событий сохраняется.")
            }
        }, confirmButton = { TextButton(enabled = valid && !busy, onClick = { onSave(draft) }) { Text("Сохранить") } }, dismissButton = { AppDismissButton(enabled = !busy) })
    if (dateOpen) AppDatePicker(checkedOn?.let(java.time.LocalDate::parse) ?: java.time.LocalDate.now(), { dateOpen = false }) { checkedOn = it.toString(); dateOpen = false }
}

data class SelectedCatalogItem(
    val id: Long, val name: String, val foodType: String = "", val ingredients: List<String> = emptyList(),
    val dosage: String = "", val kind: String = CatalogKind.DISH, val quantity: String = "", val unit: String? = null,
    val comment: String = "", val snapshot: String? = null, val refresh: Boolean = false, val expectedHash: String? = null,
)

private val SelectedItemsSaver = listSaver<List<SelectedCatalogItem>, String>(
    save = { items -> items.map { item -> JSONObject().put("id", item.id).put("name", item.name).put("type", item.foodType)
        .put("dose", item.dosage).put("kind", item.kind).put("quantity", item.quantity).put("unit", item.unit ?: JSONObject.NULL)
        .put("comment", item.comment).put("refresh", item.refresh).put("hash", item.expectedHash ?: JSONObject.NULL).toString() } },
    restore = { rows -> rows.map { row -> JSONObject(row).let { value ->
        SelectedCatalogItem(value.getLong("id"), value.getString("name"), value.getString("type"), dosage = value.getString("dose"), kind = value.getString("kind"),
            quantity = value.optString("quantity", ""), unit = if (value.isNull("unit")) null else value.getString("unit"), comment = value.optString("comment", ""),
            refresh = value.optBoolean("refresh", false), expectedHash = if (value.isNull("hash")) null else value.getString("hash"))
    } } },
)

@Composable
fun CatalogEventDialog(
    title: String, kind: String, initial: List<SelectedCatalogItem>, entries: List<CatalogItem>, busy: Boolean,
    onDismiss: () -> Unit, onCreate: (CatalogDraft, (CatalogItem) -> Unit) -> Unit,
    onSave: (List<SelectedCatalogItem>, String, RecordedTime) -> Unit, initialComment: String = "",
    date: String = java.time.LocalDate.now().toString(), initialTime: RecordedTime = RecordedTime(),
    recentIds: List<Long> = emptyList(), onFavorite: (Long, Boolean) -> Unit = { _, _ -> }, repeating: Boolean = false,
) {
    var selected by rememberSaveable(stateSaver = SelectedItemsSaver) { mutableStateOf(initial) }
    var query by rememberSaveable { mutableStateOf("") }
    var foodType by rememberSaveable { mutableStateOf(FoodType.HOME.storageValue) }
    var foodKind by rememberSaveable { mutableStateOf(CatalogKind.DISH) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var quick by rememberSaveable { mutableStateOf("all") }
    var searchIngredients by rememberSaveable { mutableStateOf(false) }
    var expandedItem by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewingHistory by rememberSaveable { mutableStateOf(false) }
    var refreshingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var commentOpen by rememberSaveable { mutableStateOf(false) }
    var comment by rememberSaveable { mutableStateOf(initialComment) }
    var eventTime by rememberSaveable(stateSaver = RecordedTimeSaver) { mutableStateOf(initialTime) }
    val isDish = kind == CatalogKind.DISH
    fun current(id: Long) = runCatching { CompositionSnapshot.encode(catalogSnapshot(id, entries)) }.getOrNull()
    fun selectedEntry(entry: CatalogItem): SelectedCatalogItem {
        val tree = if (isDish) current(entry.id) else null
        return SelectedCatalogItem(entry.id, entry.displayName, entry.foodType, entry.ingredients, kind = entry.kind,
            expectedHash = tree?.let(::compositionFingerprint))
    }
    fun historical(id: Long): String? = if (selected.find { it.id == id }?.refresh == true) current(id) else initial.find { it.id == id }?.snapshot ?: current(id).takeIf { initial.none { it.id == id } }
    if (creating) {
        CatalogEditor(kind = if (isDish) foodKind else kind, initialName = query.trim(), initialType = foodType,
            entries = entries, busy = busy, onDismiss = { creating = false }, onCreate = onCreate, onSave = { draft ->
                onCreate(draft) { entry ->
                    // The catalog flow can arrive after this callback; capture a preview from the returned item plus known components.
                    val graph = entries.filterNot { it.id == entry.id } + entry
                    val tree = if (isDish) runCatching { CompositionSnapshot.encode(catalogSnapshot(entry.id, graph)) }.getOrNull() else null
                    if (selected.none { it.id == entry.id }) selected = selected + SelectedCatalogItem(entry.id, entry.displayName, entry.foodType, entry.ingredients,
                        kind = entry.kind, expectedHash = tree?.let(::compositionFingerprint))
                    creating = false; query = ""
                }
            })
        return
    }
    LaunchedEffect(entries, selected.map { it.id }) {
        if (isDish) selected = selected.map { item ->
            if (item.expectedHash == null && initial.none { it.id == item.id })
                current(item.id)?.let { item.copy(expectedHash = compositionFingerprint(it)) } ?: item else item
        }
    }
    val matching = entries.filter { !it.archived && it.kind == (if (isDish) foodKind else kind) &&
        (!isDish || foodKind == CatalogKind.PRODUCT || it.foodType == foodType || it.foodType == "unknown") &&
        (foodKind != CatalogKind.PRODUCT || category == null || it.entry.categoryCode == category) &&
        (quick != "favorite" || it.entry.favorite) && (quick != "recent" || it.id in recentIds) && it.matches(query, entries, searchIngredients) }
        .let { if (quick == "recent") it.sortedBy { entry -> recentIds.indexOf(entry.id) } else it }
    fun changed(items: List<SelectedCatalogItem>) = items.map { it.copy(ingredients = emptyList(), snapshot = null) }
    val valid = selected.isNotEmpty() && selected.all { (!isDish || initial.any { old -> old.id == it.id } || it.expectedHash != null) && it.dosage.length <= 1000 && it.comment.length <= 1000 && runCatching { Portion(it.quantity.takeIf(String::isNotBlank), it.unit).validated() }.isSuccess } && comment.length <= 1000 && runCatching { eventTime.validated(date) }.isSuccess
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, busy = busy,
        hasChanges = changed(selected) != changed(initial) || comment != initialComment || eventTime != initialTime,
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Выбрано: " + selected.size, style = MaterialTheme.typography.titleMedium)
                Text(date, style = MaterialTheme.typography.bodySmall)
                RecordedTimeEditor(date, eventTime, !busy) { eventTime = it }
                if (repeating) Text("Новый приём на выбранную дату. Проверьте порции. Записанный состав сохранится; «Обновить состав» заменяет только выбранную позицию.")
                selected.forEach { item ->
                    Column {
                        Text(item.name + if (isDish) " · " + foodItemTypeTitle(item.kind, item.foodType) else "")
                        if (entries.find { it.id == item.id }?.archived == true) Text("Позиция в архиве; проверьте её перед повтором.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isDish) {
                            Text(quantityTitle(item.quantity.takeIf(String::isNotBlank), item.unit), style = MaterialTheme.typography.bodySmall)
                            val saved = historical(item.id)
                            Text(saved?.let { runCatching { compositionTitle(CompositionSnapshot.decode(it).details.composition) }.getOrNull() } ?: "Состав неизвестен", style = MaterialTheme.typography.bodySmall)
                            FlowRow {
                                TextButton(onClick = { viewingId = item.id; viewingHistory = true }) { Text("Состав позиции") }
                                if (initial.any { it.id == item.id }) TextButton(enabled = !busy, onClick = { refreshingId = item.id }) { Text("Обновить состав") }
                            }
                        } else AppTextField(item.dosage, { value -> selected = selected.map { if (it.id == item.id) it.copy(dosage = value) else it } },
                            label = { Text("Дозировка / количество (необязательно)") }, enabled = !busy, isError = item.dosage.length > 1000)
                        TextButton(onClick = { expandedItem = if (expandedItem == item.id) null else item.id }) { Text("Порция и комментарий") }
                        if (expandedItem == item.id) {
                            PortionFields(item.quantity, item.unit, !busy) { amount, unit -> selected = selected.map { if (it.id == item.id) it.copy(quantity = amount, unit = unit) else it } }
                            AppTextField(item.comment, { value -> selected = selected.map { if (it.id == item.id) it.copy(comment = value) else it } },
                                label = { Text("Комментарий позиции") }, enabled = !busy, singleLine = false, isError = item.comment.length > 1000)
                        }
                        TextButton(enabled = !busy, onClick = { selected = selected.filterNot { it.id == item.id } }) { Text("Убрать из события") }
                    }
                    HorizontalDivider()
                }
                if (isDish) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(enabled = !busy, selected = foodKind == CatalogKind.DISH, onClick = { foodKind = CatalogKind.DISH; query = "" }, label = { Text("Блюда") })
                        FilterChip(enabled = !busy, selected = foodKind == CatalogKind.PRODUCT, onClick = { foodKind = CatalogKind.PRODUCT; query = "" }, label = { Text("Продукты") })
                    }
                    if (foodKind == CatalogKind.DISH) FoodTypePicker(foodType, enabled = !busy) { foodType = it }
                    else CategoryPicker(category, allowAll = true) { category = it }
                }
                QuickCatalogFilter(quick, !busy) { quick = it }
                AppTextField(query, { query = it }, label = { Text("Поиск по части названия") }, singleLine = true, enabled = !busy)
                if (isDish) FilterChip(selected = searchIngredients, onClick = { searchIngredients = !searchIngredients }, label = { Text("Искать в составе") })
                matching.take(40).forEach { entry ->
                    Column {
                        TextButton(enabled = selected.none { it.id == entry.id } && !busy, onClick = { selected = selected + selectedEntry(entry); query = "" }) { Text(entry.displayName + if (selected.any { it.id == entry.id }) " · выбрано" else "") }
                        FlowRow {
                            if (isDish) TextButton(onClick = { viewingId = entry.id; viewingHistory = false }) { Text("Состав") }
                            TextButton(enabled = !busy, onClick = { onFavorite(entry.id, !entry.entry.favorite) }) { Text(if (entry.entry.favorite) "Убрать из избранного" else "В избранное") }
                        }
                    }
                }
                if (matching.size > 40) Text("Показаны первые 40 совпадений. Уточните название.")
                if (matching.isEmpty()) Text("Совпадений нет.")
                if (query.isNotBlank()) TextButton(enabled = !busy, onClick = { creating = true }) { Text("Добавить «" + query.trim() + "» в справочник и выбрать") }
                TextButton(onClick = { commentOpen = !commentOpen }) { Text("Комментарий приёма") }
                if (commentOpen) AppTextField(comment, { comment = it }, label = { Text("Комментарий приёма (необязательно)") }, singleLine = false, enabled = !busy, isError = comment.length > 1000)
                Text("Событие может включать несколько " + if (isDish) "блюд и продуктов." else "лекарств.")
                Text("Созданные записи справочника остаются в нём даже при отмене события.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = valid && !busy, onClick = { onSave(selected, comment, eventTime) }) { Text("Сохранить событие") } }, dismissButton = { AppDismissButton(enabled = !busy) })
    viewingId?.let { id -> CompositionDialog(if (viewingHistory) historical(id) else current(id), initial.find { it.id == id }?.ingredients.orEmpty()) { viewingId = null } }
    refreshingId?.let { id ->
        val now = current(id)
        AppDialog(onDismissRequest = { refreshingId = null }, title = { Text("Обновить состав позиции?") },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text("Записанный состав", style = MaterialTheme.typography.titleMedium); CompositionDetails(historical(id), initial.find { it.id == id }?.ingredients.orEmpty())
                HorizontalDivider(); Text("Текущий состав", style = MaterialTheme.typography.titleMedium); CompositionDetails(now)
                Text("Изменится только эта позиция после сохранения события. Порция и комментарий сохранятся.")
            } }, confirmButton = { TextButton(enabled = now != null && !busy, onClick = {
                val entry = entries.single { it.id == id }
                selected = selected.map { if (it.id == id) it.copy(name = entry.displayName, foodType = entry.foodType, ingredients = entry.ingredients, refresh = true, expectedHash = now?.let(::compositionFingerprint)) else it }
                refreshingId = null
            }) { Text("Использовать текущий состав") } })
    }
}
