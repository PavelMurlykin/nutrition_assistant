package com.pamurlykin.nutritionassistant.data.model

import java.util.UUID

data class CompositionNode(
    val publicId: String, val name: String, val kind: String, val revision: Int,
    val details: CatalogDetails,
    val portion: Portion = Portion(),
    val legacyIngredients: List<String> = emptyList(),
    val components: List<CompositionNode> = emptyList(),
) {
    val fullyKnown: Boolean get() = details.composition == "confirmed" && legacyIngredients.isEmpty() && components.all { it.fullyKnown }
    fun nodeCount(): Long = 1L + legacyIngredients.size + components.sumOf { it.nodeCount() }
    fun ingredientNames(): List<String> = (legacyIngredients + components.flatMap { listOf(it.name) + it.ingredientNames() })
        .distinctBy(::normalizeName).sortedBy(::normalizeName)
    fun toValue(): Map<String, Any?> = mapOf("id" to publicId, "name" to name, "kind" to kind, "revision" to revision.toLong(),
        "metadata" to mapOf("category" to details.categoryCode, "product_kind" to details.productKind, "brand" to details.brand,
            "variant" to details.variant, "composition" to details.composition, "source" to details.source,
            "checked_on" to details.checkedOn, "comment" to details.comment),
        "quantity" to portion.quantity, "unit" to portion.unit, "legacy_ingredients" to legacyIngredients,
        "components" to components.map { it.toValue() })
}

object CompositionSnapshot {
    fun toValue(root: CompositionNode): Map<String, Any?> = mapOf("version" to 1L, "root" to root.toValue())
    fun encode(root: CompositionNode): String = JsonValues.stringify(toValue(root))
    fun decode(text: String): CompositionNode = fromValue(JsonValues.readObject(text))
    @Suppress("UNCHECKED_CAST")
    fun fromValue(value: Map<String, Any?>): CompositionNode {
        require(value.keys == setOf("version", "root") && value["version"] == 1L)
        var count = 0
        fun node(value: Any?, depth: Int, path: Set<String>): CompositionNode {
            require(depth <= CompositionGraph.MAX_DEPTH && ++count <= CompositionGraph.MAX_NODES)
            val row = value as? Map<String, Any?> ?: error("Expected snapshot node")
            require(row.keys == setOf("id", "name", "kind", "revision", "metadata", "quantity", "unit", "legacy_ingredients", "components"))
            val id = row["id"] as String; require(UUID.fromString(id).toString() == id && id !in path)
            val name = row["name"] as String; require(name.isNotBlank() && name.length <= 1000)
            val kind = row["kind"] as String; require(kind in setOf("dish", "product") && (depth == 1 || kind == "product"))
            val revision = row["revision"] as Long; require(revision in 1..Int.MAX_VALUE.toLong())
            val metadata = row["metadata"] as Map<String, Any?>
            require(metadata.keys == setOf("category", "product_kind", "brand", "variant", "composition", "source", "checked_on", "comment"))
            fun optional(map: Map<String, Any?>, key: String): String? = map[key].also { require(it == null || it is String) } as String?
            val details = CatalogDetails(optional(metadata, "category"), metadata["product_kind"] as String, metadata["brand"] as String,
                metadata["variant"] as String, metadata["composition"] as String, optional(metadata, "source"), optional(metadata, "checked_on"), optional(metadata, "comment")).validated(kind)
            val portion = Portion(optional(row, "quantity"), optional(row, "unit")).validated()
            val legacy = row["legacy_ingredients"] as List<*>; require(legacy.all { it is String })
            val children = (row["components"] as List<*>).map { node(it, depth + 1, path + id) }
            require(children.map { it.publicId }.distinct().size == children.size)
            return CompositionNode(id, name, kind, revision.toInt(), details, portion, legacy.map { it as String }, children)
        }
        return node(value["root"], 1, emptySet())
    }
}
