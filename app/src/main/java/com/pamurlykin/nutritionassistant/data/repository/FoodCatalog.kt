package com.pamurlykin.nutritionassistant.data.repository

import androidx.room.withTransaction
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.util.validateText

/** Domain rules shared by all catalog editors, logging, and future richer catalog workflows. */
class FoodCatalog(private val database: NutritionDatabase) {
    private val dao = database.catalogDao()
    private data class Link(val productId: Long, val quantity: String?, val unit: String?, val position: Int)

    suspend fun save(id: Long?, kind: String, name: String, foodType: String, details: CatalogDetails,
                     components: List<ComponentSelection>): CatalogItem = database.withTransaction {
        require(kind in setOf(CatalogKind.DISH, CatalogKind.PRODUCT, CatalogKind.MEDICINE))
        val old = id?.let { requireNotNull(dao.get(it)) { "Запись не найдена." } }
        require(old == null || old.kind == kind) { "Нельзя изменить раздел записи." }
        require(if (kind == CatalogKind.DISH) foodType in setOf("home", "restaurant", "unknown") else foodType.isEmpty())
        require(kind != CatalogKind.MEDICINE || components.isEmpty())
        val clean = details.validated(kind)
        require(clean.composition == "unknown" || components.isNotEmpty() || kind != CatalogKind.DISH) { "Для известного состава блюда выберите продукты." }
        require(components.map { it.productId }.distinct().size == components.size) { "Компонент повторяется." }
        val entries = dao.allEntries().associateBy { it.id }
        val links = links().toMutableMap()
        val key = id ?: Long.MIN_VALUE
        val previous = links[key].orEmpty().map { it.productId }.toSet()
        val selected = components.mapIndexed { index, selection ->
            val entry = requireNotNull(entries[selection.productId]) { "Компонент не найден." }
            require(entry.kind == CatalogKind.PRODUCT && (!entry.archived || entry.id in previous)) { "Выберите действующий продукт." }
            val amount = Portion(selection.quantity, selection.unit).validated()
            Link(selection.productId, amount.quantity, amount.unit, index)
        }
        links[key] = selected
        val edges = (entries.keys + key).associate { owner -> owner.toString() to links[owner].orEmpty().map { it.productId.toString() } }
        CompositionGraph.validate(edges)
        require(old == null || old.revision < Int.MAX_VALUE) { "Нельзя увеличить версию записи." }
        val entry = (old ?: CatalogEntryEntity(kind = kind, name = "", normalizedName = "")).copy(
            name = validateText(name), normalizedName = normalizeName(name), foodType = foodType,
            categoryCode = clean.categoryCode, productKind = clean.productKind, brand = clean.brand, variantLabel = clean.variant,
            compositionState = clean.composition, compositionSource = clean.source, compositionCheckedOn = clean.checkedOn,
            comment = clean.comment, revision = (old?.revision ?: 0) + 1,
        )
        val target = if (old == null) dao.insert(entry) else old.id.also { dao.update(entry) }
        if (kind == CatalogKind.DISH) {
            dao.clearProducts(target)
            dao.insertProducts(selected.map { DishProductEntity(target, it.productId, it.quantity, it.unit, it.position) })
        } else if (kind == CatalogKind.PRODUCT) {
            dao.clearComponents(target)
            dao.insertComponents(selected.map { ProductComponentEntity(target, it.productId, it.quantity, it.unit, it.position) })
        }
        requireNotNull(dao.getItem(target))
    }

    /** Compatibility entry point: simple UI keeps metadata and known quantities while richer editors are added. */
    suspend fun saveBasic(id: Long?, kind: String, name: String, foodType: String, products: List<Long>): CatalogItem = database.withTransaction {
        val cleanName = validateText(name)
        val old = id?.let { requireNotNull(dao.get(it)) }
        val cleanType = if (kind == CatalogKind.DISH) foodType else ""
        require(kind in setOf(CatalogKind.DISH, CatalogKind.PRODUCT, CatalogKind.MEDICINE))
        require(old == null || old.kind == kind)
        require(kind != CatalogKind.DISH || products.isNotEmpty()) { "Выберите хотя бы один продукт для блюда." }
        require(kind == CatalogKind.DISH || products.isEmpty())
        if (kind == CatalogKind.DISH) require(FoodType.entries.any { it.storageValue == cleanType })
        if (id == null) {
            val matches = dao.allEntries().filter { it.kind == kind && it.foodType == cleanType && it.normalizedName == normalizeName(cleanName) &&
                it.productKind == "basic" && it.brand.isEmpty() && it.variantLabel.isEmpty() }
            require(matches.size <= 1) { "Найдены одноимённые варианты. Выберите нужную запись." }
            matches.singleOrNull()?.let { match ->
                if (match.archived) { require(match.revision < Int.MAX_VALUE); dao.update(match.copy(archived = false, revision = match.revision + 1)) }
                return@withTransaction requireNotNull(dao.getItem(match.id))
            }
        }
        val prior = links()[id].orEmpty()
        val chosen = if (kind == CatalogKind.DISH) products.distinct().map { product ->
            val previous = prior.find { it.productId == product }
            ComponentSelection(product, previous?.quantity, previous?.unit)
        } else prior.map { ComponentSelection(it.productId, it.quantity, it.unit) }
        val metadata = old?.details() ?: CatalogDetails(categoryCode = if (kind == CatalogKind.PRODUCT) ProductCategory.OTHER.code else null,
            composition = if (kind == CatalogKind.DISH) "partial" else "unknown")
        val changedComposition = kind == CatalogKind.DISH && prior.map { it.productId }.toSet() != chosen.map { it.productId }.toSet()
        val currentDetails = if (changedComposition && metadata.composition == "confirmed")
            metadata.copy(composition = "partial", source = null, checkedOn = null) else metadata
        save(id, kind, cleanName, cleanType, currentDetails, chosen)
    }

    suspend fun snapshots(ids: List<Long>): Map<Long, CompositionNode> = database.withTransaction {
        val entries = dao.allEntries().associateBy { it.id }
        val links = links()
        CompositionGraph.validate(entries.keys.associate { id -> id.toString() to links[id].orEmpty().map { it.productId.toString() } })
        var count = 0
        fun build(id: Long, portion: Portion = Portion()): CompositionNode {
            require(++count <= CompositionGraph.MAX_NODES) { "Приём пищи содержит более 10 000 узлов состава." }
            val entry = requireNotNull(entries[id])
            require(entry.kind in setOf(CatalogKind.DISH, CatalogKind.PRODUCT))
            return CompositionNode(entry.publicId, entry.name, entry.kind, entry.revision, entry.details().validated(entry.kind), portion,
                entry.ingredients, links[id].orEmpty().map { build(it.productId, Portion(it.quantity, it.unit).validated()) })
        }
        ids.distinct().associateWith { build(it) }
    }

    private suspend fun links(): Map<Long, List<Link>> = (
        dao.allDishProducts().map { it.dishId to Link(it.productId, it.quantity, it.unit, it.position) } +
            dao.allProductComponents().map { it.ownerId to Link(it.productId, it.quantity, it.unit, it.position) }
        ).groupBy({ it.first }, { it.second }).mapValues { (_, rows) -> rows.sortedWith(compareBy({ it.position }, { it.productId })) }
}
