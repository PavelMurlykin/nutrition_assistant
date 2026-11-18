
package com.pamurlykin.nutritionassistant.data.export

import com.pamurlykin.nutritionassistant.data.model.JsonValues
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter

private val labels = mapOf(
    "event_id" to "ID события", "item_id" to "ID позиции", "name" to "Название",
    "items" to "Позиции", "local_time" to "Местное время",
    "utc_offset" to "Смещение UTC", "time_zone" to "Часовой пояс", "time_precision" to "Точность времени",
    "comment" to "Комментарий", "composition" to "Исторический состав", "components" to "Компоненты",
    "composition_source" to "Источник состава", "legacy_ingredients" to "Старый список ингредиентов",
    "meal" to "Приём пищи", "dose" to "Записанная доза", "quantity" to "Количество", "unit" to "Единица",
    "scale_version" to "Версия шкалы", "value" to "Значение", "label" to "Подпись",
    "event_ids" to "ID исходных событий", "observations" to "Наблюдения", "days_with" to "Дней с фактором",
    "days_without" to "Дней без фактора", "affected_with" to "Из них с жидким стулом (с фактором)",
    "affected_without" to "Из них с жидким стулом (без фактора)", "excluded_days" to "Исключено дней",
)

object DiaryExportWriter {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val backticks = Regex("\u0060+")

    /** Returns null for a package exceeding the byte budget, without retaining the full text. */
    fun encode(data: DiaryExportPackage, format: DiaryExportFormat): ByteArray? {
        val bytes = ByteArrayOutputStream()
        val limited = object : OutputStream() {
            override fun write(value: Int) {
                if (bytes.size() >= MAX_BYTES) throw ExportSizeException()
                bytes.write(value)
            }

            override fun write(buffer: ByteArray, start: Int, length: Int) {
                if (length > MAX_BYTES - bytes.size()) throw ExportSizeException()
                bytes.write(buffer, start, length)
            }
        }
        try {
            OutputStreamWriter(limited, Charsets.UTF_8).buffered().use { output ->
                when (format) {
                    DiaryExportFormat.JSON -> JsonValues.writeTo(output, data.toValue(), pretty = true)
                    DiaryExportFormat.MARKDOWN -> output.appendMarkdown(data)
                }
            }
        } catch (_: ExportSizeException) { return null }
        return bytes.toByteArray()
    }

    private class ExportSizeException : IOException()

    private fun Appendable.appendMarkdown(data: DiaryExportPackage) {
        appendLine("# Nutrition Assistant — пакет аналитики")
        appendLine()
        appendLine("Период ${data.options.start} — ${data.options.end} включительно. Версия формата 1.")
        appendLine(DiarySection.entries.filter { it in data.options.sections }.joinToString(", ") { it.label })
        appendLine(if (data.options.hideNames) "Свободные названия, марки, источники состава и текст доз скрыты." else "Сохранены исходные названия и источники.")
        appendLine(if (data.options.includeComments) "Комментарии включены." else "Комментарии исключены.")
        if (data.options.prompt.isNotBlank()) {
            appendLine()
            appendLine("## Запрос для анализа")
            appendLine()
            appendLine(scalar(data.options.prompt))
        }
        appendLine()
        appendLine("## Как читать данные")
        appendValue(diaryDataRules, "")
        if (data.units.isNotEmpty()) {
            appendLine()
            appendLine("## Единицы")
            appendValue(data.units, "")
        }
        if (data.scales.isNotEmpty()) {
            appendLine()
            appendLine("## Словарь шкал")
            appendValue(data.scales, "")
        }
        appendLine()
        if (data.categories.isNotEmpty()) {
            appendLine("## Словарь категорий")
            appendValue(data.categories, "")
            appendLine()
        }
        appendLine("## События")
        if (data.events.isEmpty()) appendLine("За этот период в выбранных разделах записей нет.")
        data.events.groupBy { it.date }.forEach { (date, events) ->
            appendLine()
            appendLine("### $date")
            for (event in events) {
                appendLine()
                appendLine("#### ${event.section.label} · ${event.time ?: precisionLabel(event.precision)}")
                appendValue(event.toValue() - setOf("section", "date", "local_time"), "")
            }
        }
        data.analytics?.let {
            appendLine()
            appendLine("## Наблюдения аналитики")
            appendValue(it, "")
        }
    }

    private fun precisionLabel(code: String) = when (code) {
        "day" -> "за день"
        "night" -> "за ночь"
        else -> "время неизвестно"
    }

    private fun Appendable.appendValue(value: Any?, indent: String) {
        when (value) {
            is Map<*, *> -> value.forEach { (key, child) ->
                val title = labels[key] ?: key
                if (child is Map<*, *> && child.isNotEmpty() || child is List<*> && child.isNotEmpty()) {
                    appendLine("$indent- $title")
                    appendValue(child, "$indent  ")
                } else appendLine("$indent- $title — ${scalar(child)}")
            }
            is List<*> -> value.forEach { child ->
                if (child is Map<*, *>) {
                    appendLine("$indent- Запись")
                    appendValue(child, "$indent  ")
                } else appendLine("$indent- ${scalar(child)}")
            }
            else -> appendLine("$indent- ${scalar(value)}")
        }
    }

    /** Quote user text with a delimiter longer than any backtick run inside it. */
    private fun scalar(value: Any?): String {
        if (value == null) return "не указано"
        if (value is Boolean) return if (value) "да" else "нет"
        if (value is List<*> || value is Map<*, *>) return "пусто"
        if (value !is String) return value.toString()
        val quote = JsonValues.stringify(listOf(value)).removeSurrounding("[", "]")
        val fence = "\u0060".repeat(1 + (backticks.findAll(quote).maxOfOrNull { it.value.length } ?: 0))
        return "$fence $quote $fence"
    }
}
