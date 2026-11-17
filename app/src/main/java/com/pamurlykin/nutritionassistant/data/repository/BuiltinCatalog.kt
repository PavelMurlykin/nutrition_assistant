package com.pamurlykin.nutritionassistant.data.repository

import androidx.room.withTransaction
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import java.util.UUID

data class SeedProduct(val key: String, val name: String, val category: String) {
    val publicId: String get() = builtinId("product:$key")
}
data class SeedConflict(val seed: SeedProduct, val candidates: List<CatalogItem>)
fun builtinId(key: String): String = UUID.nameUUIDFromBytes("nutrition-assistant/catalog/v1/$key".toByteArray(Charsets.UTF_8)).toString()

object BuiltinCatalog {
    val products = listOf(
        SeedProduct("buckwheat", "Гречка", "grains"),
        SeedProduct("rice", "Рис", "grains"),
        SeedProduct("oats", "Овсяные хлопья", "grains"),
        SeedProduct("millet", "Пшено", "grains"),
        SeedProduct("barley", "Перловка", "grains"),
        SeedProduct("semolina", "Манная крупа", "grains"),
        SeedProduct("corn_grits", "Кукурузная крупа", "grains"),
        SeedProduct("bulgur", "Булгур", "grains"),
        SeedProduct("quinoa", "Киноа", "grains"),
        SeedProduct("pasta", "Макароны", "grains"),
        SeedProduct("wheat_flour", "Мука пшеничная", "bread"),
        SeedProduct("rye_flour", "Мука ржаная", "bread"),
        SeedProduct("rice_flour", "Мука рисовая", "bread"),
        SeedProduct("bread", "Хлеб", "bread"),
        SeedProduct("rye_bread", "Хлеб ржаной", "bread"),
        SeedProduct("lavash", "Лаваш", "bread"),
        SeedProduct("breadcrumbs", "Панировочные сухари", "bread"),
        SeedProduct("potato", "Картофель", "vegetables"),
        SeedProduct("carrot", "Морковь", "vegetables"),
        SeedProduct("onion", "Лук репчатый", "vegetables"),
        SeedProduct("garlic", "Чеснок", "vegetables"),
        SeedProduct("cabbage", "Капуста белокочанная", "vegetables"),
        SeedProduct("cauliflower", "Цветная капуста", "vegetables"),
        SeedProduct("broccoli", "Брокколи", "vegetables"),
        SeedProduct("zucchini", "Кабачок", "vegetables"),
        SeedProduct("beet", "Свёкла", "vegetables"),
        SeedProduct("cucumber", "Огурец", "vegetables"),
        SeedProduct("tomato", "Помидор", "vegetables"),
        SeedProduct("pepper", "Перец сладкий", "vegetables"),
        SeedProduct("pumpkin", "Тыква", "vegetables"),
        SeedProduct("lettuce", "Листовой салат", "vegetables"),
        SeedProduct("spinach", "Шпинат", "vegetables"),
        SeedProduct("dill", "Укроп", "vegetables"),
        SeedProduct("parsley", "Петрушка", "vegetables"),
        SeedProduct("apple", "Яблоко", "fruit"),
        SeedProduct("pear", "Груша", "fruit"),
        SeedProduct("banana", "Банан", "fruit"),
        SeedProduct("orange", "Апельсин", "fruit"),
        SeedProduct("mandarin", "Мандарин", "fruit"),
        SeedProduct("lemon", "Лимон", "fruit"),
        SeedProduct("grapes", "Виноград", "fruit"),
        SeedProduct("kiwi", "Киви", "fruit"),
        SeedProduct("strawberry", "Клубника", "fruit"),
        SeedProduct("blueberry", "Черника", "fruit"),
        SeedProduct("beef", "Говядина", "meat"),
        SeedProduct("pork", "Свинина", "meat"),
        SeedProduct("chicken", "Курица", "meat"),
        SeedProduct("turkey", "Индейка", "meat"),
        SeedProduct("rabbit", "Кролик", "meat"),
        SeedProduct("lamb", "Баранина", "meat"),
        SeedProduct("beef_mince", "Фарш говяжий", "prepared"),
        SeedProduct("pork_mince", "Фарш свиной", "prepared"),
        SeedProduct("chicken_mince", "Фарш куриный", "prepared"),
        SeedProduct("turkey_mince", "Фарш из индейки", "prepared"),
        SeedProduct("mixed_mince", "Фарш смешанный", "prepared"),
        SeedProduct("dumplings", "Пельмени", "prepared"),
        SeedProduct("sandwich", "Готовый сэндвич", "prepared"),
        SeedProduct("sausage", "Колбаса", "prepared"),
        SeedProduct("cod", "Треска", "fish"),
        SeedProduct("pollock", "Минтай", "fish"),
        SeedProduct("salmon", "Лосось", "fish"),
        SeedProduct("mackerel", "Скумбрия", "fish"),
        SeedProduct("hake", "Хек", "fish"),
        SeedProduct("shrimp", "Креветки", "fish"),
        SeedProduct("squid", "Кальмар", "fish"),
        SeedProduct("egg", "Яйцо", "eggs"),
        SeedProduct("quail_egg", "Перепелиное яйцо", "eggs"),
        SeedProduct("milk", "Молоко", "dairy"),
        SeedProduct("lactose_free_milk", "Молоко без лактозы", "dairy"),
        SeedProduct("kefir", "Кефир", "dairy"),
        SeedProduct("yogurt", "Йогурт", "dairy"),
        SeedProduct("curd", "Творог", "dairy"),
        SeedProduct("sour_cream", "Сметана", "dairy"),
        SeedProduct("cream", "Сливки", "dairy"),
        SeedProduct("cheese", "Сыр", "dairy"),
        SeedProduct("oat_drink", "Овсяный напиток", "dairy"),
        SeedProduct("soy_drink", "Соевый напиток", "dairy"),
        SeedProduct("lentils", "Чечевица", "legumes"),
        SeedProduct("chickpeas", "Нут", "legumes"),
        SeedProduct("beans", "Фасоль", "legumes"),
        SeedProduct("peas", "Горох", "legumes"),
        SeedProduct("walnut", "Грецкий орех", "nuts"),
        SeedProduct("almond", "Миндаль", "nuts"),
        SeedProduct("peanut", "Арахис", "nuts"),
        SeedProduct("sunflower_seeds", "Семечки подсолнечника", "nuts"),
        SeedProduct("vegetable_oil", "Растительное масло", "oils"),
        SeedProduct("olive_oil", "Оливковое масло", "oils"),
        SeedProduct("butter", "Сливочное масло", "oils"),
        SeedProduct("salt", "Соль", "seasoning"),
        SeedProduct("sugar", "Сахар", "seasoning"),
        SeedProduct("black_pepper", "Перец чёрный", "seasoning"),
        SeedProduct("tomato_paste", "Томатная паста", "seasoning"),
        SeedProduct("soy_sauce", "Соевый соус", "seasoning"),
        SeedProduct("water", "Вода", "drinks"),
        SeedProduct("tea", "Чай", "drinks"),
        SeedProduct("coffee", "Кофе", "drinks"),
        SeedProduct("cocoa", "Какао", "drinks"),
        SeedProduct("honey", "Мёд", "other"),
        SeedProduct("gelatin", "Желатин", "other"),
        SeedProduct("mushroom", "Шампиньоны", "other"),
    )
    fun pending(items: List<CatalogItem>, aliases: List<BackupAliasEntity>): List<SeedConflict> {
        val known = items.map { it.entry.publicId }.toSet() + aliases.filter { it.type == "catalog" }.map { it.sourceId }
        return products.filter { it.publicId !in known }.mapNotNull { seed ->
            val matches = candidates(seed, items)
            if (matches.size > 1) SeedConflict(seed, matches) else null
        }
    }
    private fun candidates(seed: SeedProduct, items: List<CatalogItem>) = items.filter { it.kind == "product" &&
        it.normalizedName == normalizeName(seed.name) && it.entry.productKind == "basic" && it.entry.brand.isEmpty() && it.entry.variantLabel.isEmpty() }
    suspend fun install(database: NutritionDatabase) = database.withTransaction {
        val dao = database.catalogDao()
        val entries = dao.allItems().toMutableList()
        val aliases = dao.aliases().associateBy { it.sourceId }.toMutableMap()
        products.forEach { seed ->
            if (entries.any { it.entry.publicId == seed.publicId } || aliases[seed.publicId]?.let { alias -> entries.any { it.entry.publicId == alias.targetId } } == true) return@forEach
            val found = candidates(seed, entries)
            if (found.size > 1) return@forEach // Shown as an explicit choice in Menu; never choose an arbitrary identity.
            if (found.size == 1) {
                var entry = found.single().entry
                val alias = BackupAliasEntity(seed.publicId, entry.publicId, "catalog")
                dao.insertAlias(alias); aliases[seed.publicId] = alias
                // Only fill a missing category on an untouched legacy entry; never replace edits or archive state.
                if (entry.categoryCode == null && entry.revision == 1) {
                    entry = entry.copy(categoryCode = seed.category); dao.update(entry)
                    entries[entries.indexOfFirst { it.id == entry.id }] = found.single().copy(entry = entry)
                }
            } else {
                val entry = newEntry(seed)
                val id = dao.insert(entry)
                entries += CatalogItem(entry.copy(id = id), emptyList())
            }
        }
    }
    suspend fun resolve(database: NutritionDatabase, key: String, targetId: Long?) = database.withTransaction {
        val dao = database.catalogDao()
        val seed = products.single { it.key == key }
        val conflict = pending(dao.allItems(), dao.aliases()).singleOrNull { it.seed.key == key }
        requireNotNull(conflict) { "Сопоставление уже изменилось. Откройте список заново." }
        if (targetId == null) dao.insert(newEntry(seed))
        else {
            val target = conflict.candidates.single { it.id == targetId }
            dao.insertAlias(BackupAliasEntity(seed.publicId, target.entry.publicId, "catalog"))
        }
    }
    private fun newEntry(seed: SeedProduct) = CatalogEntryEntity(kind = "product", name = seed.name,
        normalizedName = normalizeName(seed.name), categoryCode = seed.category, publicId = seed.publicId,
        comment = when {
            seed.key.endsWith("mince") -> "Базовый продукт без заданных добавок. Состав конкретного покупного фарша уточните по упаковке в отдельном варианте."
            seed.key in setOf("bread", "rye_bread", "dumplings", "sausage", "sandwich", "lactose_free_milk") -> "Общее название не подтверждает состав. Для конкретного товара проверьте упаковку и создайте вариант."
            else -> null
        })
}
