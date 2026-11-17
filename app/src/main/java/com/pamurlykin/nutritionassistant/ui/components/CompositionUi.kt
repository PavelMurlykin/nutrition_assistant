package com.pamurlykin.nutritionassistant.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.data.model.*

@Composable
fun CategoryPicker(value: String?, enabled: Boolean = true, allowAll: Boolean = false, onChange: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(enabled = enabled, onClick = { open = true }) {
        Text("Категория: " + (ProductCategory.entries.find { it.code == value }?.title ?: if (allowAll) "Все" else "Не указана"))
    }
    if (open) AppDialog(onDismissRequest = { open = false }, title = { Text("Категория продукта") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (allowAll) AppRadioChoice("Все категории", value == null) { onChange(null); open = false }
            ProductCategory.entries.forEach { category -> AppRadioChoice(category.title, value == category.code) { onChange(category.code); open = false } }
        }
    }, confirmButton = { AppDismissButton() }, dismissButton = null)
}

@Composable
fun QuickCatalogFilter(value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("all" to "Все", "favorite" to "Избранное", "recent" to "Недавние").forEach { (key, title) ->
            FilterChip(selected = value == key, onClick = { onChange(key) }, enabled = enabled, label = { Text(title) })
        }
    }
}

@Composable
fun PortionFields(quantity: String, unit: String?, enabled: Boolean = true, onChange: (String, String?) -> Unit) {
    val amount = quantity.takeIf { it.isNotBlank() }
    val valid = runCatching { Portion(amount, unit).validated() }.isSuccess
    AppTextField(quantity, { onChange(it.replace(',', '.').take(25), unit) }, label = { Text("Количество (необязательно)") },
        enabled = enabled, isError = !valid, supportingText = { Text("Количество и единица задаются вместе; пусто — неизвестно.") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("g" to "г", "ml" to "мл", "piece" to "шт.").forEach { (key, title) ->
            FilterChip(selected = unit == key, onClick = { onChange(quantity, key) }, enabled = enabled, label = { Text(title) })
        }
        TextButton(enabled = enabled, onClick = { onChange("", null) }) { Text("Очистить количество") }
    }
}

@Composable
fun CompositionDetails(snapshot: String?, legacy: List<String> = emptyList()) {
    val root = remember(snapshot) { snapshot?.let { runCatching { CompositionSnapshot.decode(it) }.getOrNull() } }
    if (root == null) {
        Text("Состав неизвестен. Полного исторического дерева нет.")
        if (legacy.isNotEmpty()) Text("Старый плоский список: " + legacy.joinToString(", "))
    } else {
        val lines = remember(snapshot) {
            val rows = mutableListOf<Pair<String, CompositionNode>>()
            fun walk(node: CompositionNode, path: List<String>) {
                if (rows.size >= 200) return
                val label = node.name + listOf(node.details.brand, node.details.variant).filter { it.isNotBlank() }.joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" }
                val current = path + label
                rows += current.joinToString(" → ") to node
                node.components.forEach { walk(it, current) }
            }
            walk(root, emptyList()); rows
        }
        lines.forEach { (path, node) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(path, style = MaterialTheme.typography.titleSmall)
                Text(compositionTitle(node.details.composition) + " · версия " + node.revision, style = MaterialTheme.typography.bodySmall)
                if (node.portion.quantity != null) Text(quantityTitle(node.portion.quantity, node.portion.unit))
                node.details.source?.let { Text("Источник: $it" + (node.details.checkedOn?.let { day -> " · $day" } ?: ""), style = MaterialTheme.typography.bodySmall) }
                node.details.comment?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (node.legacyIngredients.isNotEmpty()) Text("Старые ингредиенты: " + node.legacyIngredients.joinToString(", "))
            }
        }
        if (root.nodeCount() > lines.size) Text("Показаны первые 200 узлов; полный состав сохраняется в JSON.")
        if (!root.fullyKnown) Text("Неполный состав не подтверждает отсутствие ингредиентов или лактозы.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CompositionDialog(snapshot: String?, legacy: List<String> = emptyList(), onDismiss: () -> Unit) {
    AppDialog(onDismissRequest = onDismiss, title = { Text("Состав и источники") },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) { CompositionDetails(snapshot, legacy) } },
        confirmButton = { AppDismissButton() }, dismissButton = null)
}
