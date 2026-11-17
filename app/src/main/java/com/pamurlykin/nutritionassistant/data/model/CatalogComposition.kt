package com.pamurlykin.nutritionassistant.data.model

import com.pamurlykin.nutritionassistant.data.local.*

data class CatalogDraft(val id: Long?, val kind: String, val name: String, val foodType: String,
    val details: CatalogDetails, val components: List<ComponentSelection>)

fun catalogSnapshot(id: Long, entries: List<CatalogItem>): CompositionNode {
    val byId = entries.associateBy { it.id }
    var count = 0
    fun build(key: Long, amount: Portion, path: Set<Long>): CompositionNode {
        require(key !in path && path.size < CompositionGraph.MAX_DEPTH && ++count <= CompositionGraph.MAX_NODES)
        val item = requireNotNull(byId[key]) { "Компонент не найден." }
        require(item.kind in setOf("product", "dish"))
        val links = if (item.kind == "dish") item.dishLinks.map { ComponentSelection(it.productId, it.quantity, it.unit) to it.position }
            else item.componentLinks.map { ComponentSelection(it.productId, it.quantity, it.unit) to it.position }
        return CompositionNode(item.entry.publicId, item.name, item.kind, item.entry.revision, item.entry.details().validated(item.kind), amount, item.entry.ingredients,
            links.sortedWith(compareBy({ it.second }, { it.first.productId })).map { (link, _) -> build(link.productId, Portion(link.quantity, link.unit).validated(), path + key) })
    }
    return build(id, Portion(), emptySet())
}
fun compositionTitle(value: String) = when (value) { "confirmed" -> "Подтверждён по источнику"; "partial" -> "Частичный состав"; else -> "Состав неизвестен" }
fun quantityTitle(quantity: String?, unit: String?): String = if (quantity == null) "Количество не указано" else "$quantity ${when (unit) { "g" -> "г"; "ml" -> "мл"; else -> "шт." }}"
fun CatalogItem.matches(query: String, entries: List<CatalogItem>, inComposition: Boolean = false): Boolean {
    val search = normalizeName(query)
    if (normalizeName(displayName).contains(search)) return true
    return inComposition && kind != "medicine" && runCatching { catalogSnapshot(id, entries).ingredientNames().any { normalizeName(it).contains(search) } }.getOrDefault(false)
}

fun CatalogItem.componentsForEdit(): List<ComponentSelection> = if (kind == "dish") dishLinks.sortedBy { it.position }.map { ComponentSelection(it.productId, it.quantity, it.unit) }
    else componentLinks.sortedBy { it.position }.map { ComponentSelection(it.productId, it.quantity, it.unit) }

fun compositionFingerprint(snapshot: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(snapshot.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

fun MealItemEntity.historyDisplayName(): String {
    val details = compositionSnapshot?.let { runCatching { CompositionSnapshot.decode(it).details }.getOrNull() } ?: return name
    return name + listOf(details.brand, details.variant).filter { it.isNotBlank() }.joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" }
}
