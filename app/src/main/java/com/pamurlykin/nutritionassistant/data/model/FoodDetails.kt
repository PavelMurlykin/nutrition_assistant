package com.pamurlykin.nutritionassistant.data.model

import java.math.BigDecimal
import java.time.*

enum class ProductCategory(val code: String, val title: String) {
    GRAINS("grains", "Крупы и зерновые"), BREAD("bread", "Мука и хлеб"), VEGETABLES("vegetables", "Овощи и зелень"),
    FRUIT("fruit", "Фрукты и ягоды"), MEAT("meat", "Мясо и птица"), PREPARED("prepared", "Фарш и полуфабрикаты"),
    FISH("fish", "Рыба и морепродукты"), EGGS("eggs", "Яйца"), DAIRY("dairy", "Молочные продукты и альтернативы"),
    LEGUMES("legumes", "Бобовые"), NUTS("nuts", "Орехи и семена"), OILS("oils", "Масла"),
    SEASONING("seasoning", "Специи и соусы"), DRINKS("drinks", "Напитки"), OTHER("other", "Прочее");
}

data class Portion(val quantity: String? = null, val unit: String? = null) {
    fun validated(): Portion {
        require((quantity == null) == (unit == null)) { "Укажите количество и единицу вместе." }
        if (quantity == null) return this
        require(unit in setOf("g", "ml", "piece")) { "Неизвестная единица количества." }
        require(quantity.matches(Regex("[0-9]{1,18}(\\.[0-9]{1,6})?"))) { "Неверный формат количества." }
        val number = BigDecimal(quantity)
        require(number.signum() > 0) { "Количество должно быть положительным." }
        return copy(quantity = number.stripTrailingZeros().toPlainString())
    }
}

data class ComponentSelection(val productId: Long, val quantity: String? = null, val unit: String? = null)

data class CatalogDetails(
    val categoryCode: String? = null,
    val productKind: String = "basic",
    val brand: String = "",
    val variant: String = "",
    val composition: String = "unknown",
    val source: String? = null,
    val checkedOn: String? = null,
    val comment: String? = null,
) {
    fun validated(kind: String): CatalogDetails {
        require(categoryCode == null || ProductCategory.entries.any { it.code == categoryCode }) { "Неизвестная категория продукта." }
        require(productKind in setOf("basic", "variant"))
        require(kind == CatalogKind.PRODUCT || (categoryCode == null && productKind == "basic"))
        require(composition in setOf("unknown", "partial", "confirmed"))
        val cleanSource = cleanComment(source)
        if (checkedOn != null) validateDay(checkedOn)
        require(composition != "confirmed" || (cleanSource != null && checkedOn != null)) { "Для подтверждённого состава укажите источник и дату проверки." }
        require(brand.length <= 1000 && variant.length <= 1000)
        return copy(brand = brand.trim(), variant = variant.trim(), source = cleanSource, comment = cleanComment(comment))
    }
}

fun cleanComment(value: String?): String? = value?.trim()?.ifBlank { null }?.also { require(it.length <= 1000) { "Комментарий не длиннее 1000 символов." } }
fun validateDay(value: String): LocalDate = LocalDate.parse(value).also { require(it.year in 1..9999 && it.toString() == value) }

/** Actual time is independent of creation time; null means genuinely unknown. */
data class RecordedTime(val time: String? = null, val utcOffset: String? = null, val timeZone: String? = null) {
    val precision: String get() = if (time == null) "date_only" else "date_time"
    fun validated(date: String): RecordedTime {
        val day = validateDay(date)
        if (time == null) { require(utcOffset == null && timeZone == null); return this }
        require(time.matches(Regex("[0-9]{2}:[0-9]{2}")))
        val local = LocalDateTime.of(day, LocalTime.parse(time))
        val offset = ZoneOffset.of(requireNotNull(utcOffset) { "Для точного времени укажите смещение UTC." })
        if (timeZone != null) require(offset in ZoneId.of(timeZone).rules.getValidOffsets(local)) { "Время не соответствует часовому поясу." }
        return copy(utcOffset = offset.id)
    }
}

data class EventContext(val recordedTime: RecordedTime = RecordedTime(), val comment: String? = null) {
    fun validated(date: String): EventContext = copy(recordedTime = recordedTime.validated(date), comment = cleanComment(comment))
}

data class SleepPeriod(val bedDate: String, val wakeupDate: String) {
    fun validate(bedTime: String, wakeupTime: String): SleepPeriod {
        val start = LocalDateTime.of(validateDay(bedDate), LocalTime.parse(bedTime))
        val end = LocalDateTime.of(validateDay(wakeupDate), LocalTime.parse(wakeupTime))
        require(end.isAfter(start)) { "Подъём должен быть позже отбоя." }
        return this
    }
}

object CompositionGraph {
    const val MAX_DEPTH = 16
    const val MAX_NODES = 10_000
    fun validate(edges: Map<String, List<String>>) {
        val active = hashSetOf<String>()
        val sizes = hashMapOf<String, Pair<Int, Int>>()
        fun visit(id: String, depth: Int): Pair<Int, Int> {
            require(depth <= MAX_DEPTH) { "Состав глубже 16 уровней." }
            require(id in edges) { "Компонент состава не найден." }
            sizes[id]?.let { require(depth + it.first - 1 <= MAX_DEPTH); return it }
            require(active.add(id)) { "Циклический состав запрещён." }
            var height = 1; var count = 1
            edges.getValue(id).forEach { child ->
                val result = visit(child, depth + 1)
                height = maxOf(height, result.first + 1); count += result.second
                require(count <= MAX_NODES) { "Состав разворачивается более чем в 10 000 узлов." }
            }
            active.remove(id)
            return (height to count).also { sizes[id] = it }
        }
        edges.keys.forEach { visit(it, 1) }
    }
}
