package com.pamurlykin.nutritionassistant.data.backup

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.pamurlykin.nutritionassistant.data.model.normalizeName
import java.io.InputStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

data class BackupRecord(val type: String, val publicId: String, val values: Map<String, Any?>)
data class BackupAlias(val sourceId: String, val targetId: String, val type: String)
data class BackupFile(
    val appVersion: String,
    val exportedAt: String,
    val records: List<BackupRecord>,
    val aliases: List<BackupAlias> = emptyList(),
)

internal enum class ValueKind { TEXT, OPTIONAL_TEXT, INTEGER, BOOLEAN, STRINGS, REFERENCES, REFERENCE }
internal data class BackupTable(val key: String = "id", val fields: Map<String, ValueKind>)

/** Version 1 is deliberately separate from the Room schema. Field names are a fixed allowlist. */
internal object BackupSchema {
    private fun fields(vararg pairs: Pair<String, ValueKind>) = linkedMapOf(*pairs)
    private val times = fields("created_at" to ValueKind.TEXT, "updated_at" to ValueKind.TEXT)
    private val event = fields("user_id" to ValueKind.REFERENCE, "date" to ValueKind.TEXT)
    val tables = linkedMapOf(
        "users" to BackupTable("user_id", fields(
            "breakfast_time" to ValueKind.TEXT, "lunch_time" to ValueKind.TEXT, "dinner_time" to ValueKind.TEXT,
            "toilet_time" to ValueKind.TEXT, "wakeup_time" to ValueKind.TEXT, "bed_time" to ValueKind.TEXT,
            "breakfast_enabled" to ValueKind.BOOLEAN, "lunch_enabled" to ValueKind.BOOLEAN,
            "dinner_enabled" to ValueKind.BOOLEAN, "toilet_enabled" to ValueKind.BOOLEAN,
        ) + times),
        "catalog" to BackupTable(fields = fields(
            "kind" to ValueKind.TEXT, "name" to ValueKind.TEXT, "normalized_name" to ValueKind.TEXT,
            "food_type" to ValueKind.TEXT, "ingredients" to ValueKind.STRINGS, "archived" to ValueKind.BOOLEAN,
            "product_ids" to ValueKind.REFERENCES,
        )),
        "meals" to BackupTable(fields = event + fields("meal_type" to ValueKind.TEXT, "description" to ValueKind.TEXT) + times),
        "medicines" to BackupTable(fields = event + fields("name" to ValueKind.TEXT, "dosage" to ValueKind.OPTIONAL_TEXT) + times),
        "stools" to BackupTable(fields = event + fields("quality" to ValueKind.INTEGER) + times),
        "feelings" to BackupTable(fields = event + fields("description" to ValueKind.TEXT) + times),
        "water" to BackupTable(fields = event + fields("glasses_count" to ValueKind.INTEGER) + times),
        "sleeps" to BackupTable(fields = event + fields("wakeup_time" to ValueKind.TEXT, "bed_time" to ValueKind.TEXT,
            "quality_description" to ValueKind.OPTIONAL_TEXT) + times),
        "meal_items" to BackupTable(fields = fields("meal_id" to ValueKind.REFERENCE, "catalog_id" to ValueKind.REFERENCE,
            "name" to ValueKind.TEXT, "food_type" to ValueKind.TEXT, "ingredients" to ValueKind.STRINGS, "kind" to ValueKind.TEXT, "position" to ValueKind.INTEGER)),
        "medicine_items" to BackupTable(fields = fields("medicine_id" to ValueKind.REFERENCE, "catalog_id" to ValueKind.REFERENCE,
            "name" to ValueKind.TEXT, "dosage" to ValueKind.OPTIONAL_TEXT, "position" to ValueKind.INTEGER)),
    )
    fun referenceTable(field: String): String = when (field) {
        "user_id" -> "users"; "catalog_id", "product_ids" -> "catalog"
        "meal_id" -> "meals"; "medicine_id" -> "medicines"
        else -> error("Unknown reference")
    }
}

object BackupCodec {
    const val APPLICATION_ID = "com.pamurlykin.nutritionassistant"
    const val VERSION = 1
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MAX_ITEMS = 100_000

    fun read(stream: InputStream): BackupFile {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= MAX_BYTES) { "Файл превышает 16 МиБ." }
            output.write(buffer, 0, count)
        }
        return decode(output.toByteArray())
    }

    fun decode(bytes: ByteArray): BackupFile {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Пустой файл или размер больше 16 МиБ." }
        try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            val root = JsonReader(StringReader(text)).use { reader ->
                reader.isLenient = false
                val value = readValue(reader, 0).asObject()
                require(reader.peek() == JsonToken.END_DOCUMENT) { "Лишние данные после JSON." }
                value
            }
            require(root.keys == setOf("applicationId", "schemaVersion", "appVersion", "exportedAt", "records", "aliases")) { "Неподдерживаемые поля JSON." }
            require(root["applicationId"] == APPLICATION_ID) { "Это резервная копия другого приложения." }
            require(root["schemaVersion"] == VERSION.toLong()) { "Версия JSON не поддерживается. Обновите приложение." }
            val records = root["records"].asList().map { value ->
                val row = value.asObject()
                require(row.keys == setOf("type", "publicId", "values")) { "Неверные поля записи." }
                BackupRecord(row["type"] as String, row["publicId"] as String, row["values"].asObject())
            }
            val aliases = root["aliases"].asList().map { value ->
                val row = value.asObject()
                require(row.keys == setOf("sourceId", "targetId", "type")) { "Неверное сопоставление." }
                BackupAlias(row["sourceId"] as String, row["targetId"] as String, row["type"] as String)
            }
            return BackupFile(root["appVersion"] as String, root["exportedAt"] as String, records, aliases).also(::validate)
        } catch (failure: Exception) {
            // Never put imported text or parser diagnostics containing user data into UI/logs.
            if (failure is IllegalArgumentException && failure.message in setOf(
                    "Это резервная копия другого приложения.", "Версия JSON не поддерживается. Обновите приложение.")) throw failure
            throw IllegalArgumentException("Файл JSON повреждён или содержит неподдерживаемые данные. Выберите проверенную резервную копию.")
        }
    }

    fun encode(file: BackupFile): ByteArray {
        validate(file)
        val bytes = canonical(mapOf("applicationId" to APPLICATION_ID, "schemaVersion" to VERSION.toLong(),
            "appVersion" to file.appVersion, "exportedAt" to file.exportedAt, "records" to recordsValue(file.records),
            "aliases" to aliasesValue(file.aliases))).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Резервная копия превышает 16 МиБ." }
        return bytes
    }

    fun fingerprint(file: BackupFile): String = MessageDigest.getInstance("SHA-256")
        .digest(canonical(listOf(recordsValue(file.records), aliasesValue(file.aliases))).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    internal fun canonical(value: Any?): String = StringWriter().also { output -> JsonWriter(output).use { writeValue(it, value) } }.toString()
    private fun recordsValue(records: List<BackupRecord>) = records.sortedWith(compareBy({ it.type }, { it.publicId }))
        .map { mapOf("type" to it.type, "publicId" to it.publicId, "values" to it.values) }
    private fun aliasesValue(aliases: List<BackupAlias>) = aliases.sortedBy { it.sourceId }
        .map { mapOf("sourceId" to it.sourceId, "targetId" to it.targetId, "type" to it.type) }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asObject(): Map<String, Any?> = this as? Map<String, Any?> ?: error("Expected object")
    private fun Any?.asList(): List<*> = this as? List<*> ?: error("Expected array")

    private fun readValue(reader: JsonReader, depth: Int): Any? {
        require(depth <= 16) { "Слишком глубокий JSON." }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val values = linkedMapOf<String, Any?>(); reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(!values.containsKey(key)) { "Повтор поля JSON." }
                    values[key] = readValue(reader, depth + 1)
                }
                reader.endObject(); values.toMap()
            }
            JsonToken.BEGIN_ARRAY -> {
                val values = mutableListOf<Any?>(); reader.beginArray()
                while (reader.hasNext()) { require(values.size < MAX_ITEMS); values += readValue(reader, depth + 1) }
                reader.endArray(); values.toList()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString().let { require(it.matches(Regex("-?(0|[1-9][0-9]*)"))); it.toLong() }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); null }
            else -> error("Unexpected JSON token")
        }
    }

    private fun writeValue(writer: JsonWriter, value: Any?) {
        when (value) {
            null -> writer.nullValue()
            is String -> writer.value(value)
            is Boolean -> writer.value(value)
            is Number -> writer.value(value)
            is List<*> -> { writer.beginArray(); value.forEach { writeValue(writer, it) }; writer.endArray() }
            is Map<*, *> -> { writer.beginObject(); value.keys.map { it as String }.sorted().forEach { writer.name(it); writeValue(writer, value[it]) }; writer.endObject() }
            else -> error("Unsupported JSON value")
        }
    }

    fun validate(file: BackupFile) {
        require(file.appVersion.isNotBlank() && file.appVersion.length <= 100)
        Instant.parse(file.exportedAt)
        val byId = file.records.associateBy { it.publicId }
        require(byId.size == file.records.size && file.records.size <= MAX_ITEMS) { "Повтор идентификатора." }
        require(file.records.count { it.type == "users" } == 1) { "Ожидается один локальный профиль." }
        var itemCount = file.records.size.toLong() + file.aliases.size
        val catalogKeys = hashSetOf<List<Any?>>()
        val dailyKeys = hashSetOf<List<Any?>>()
        val positions = hashSetOf<List<Any?>>()
        file.records.forEach { record ->
            requireUuid(record.publicId)
            val schema = requireNotNull(BackupSchema.tables[record.type]) { "Неизвестный вид записи." }
            require(record.values.keys == schema.fields.keys) { "Неверные поля записи." }
            schema.fields.forEach { (key, kind) ->
                val value = record.values[key]
                require(when (kind) {
                    ValueKind.TEXT, ValueKind.REFERENCE -> value is String
                    ValueKind.OPTIONAL_TEXT -> value == null || value is String
                    ValueKind.INTEGER -> value is Long
                    ValueKind.BOOLEAN -> value is Boolean
                    ValueKind.STRINGS, ValueKind.REFERENCES -> value is List<*> && value.all { it is String }
                }) { "Неверный тип поля." }
                if (value is List<*>) itemCount += value.size
                if (kind == ValueKind.REFERENCE) require(byId[value]?.type == BackupSchema.referenceTable(key)) { "Отсутствует ссылка." }
                if (kind == ValueKind.REFERENCES) {
                    val refs = value as List<*>
                    require(refs.distinct().size == refs.size)
                    refs.forEach { require(byId[it]?.type == "catalog" && byId[it]?.values?.get("kind") == "product") }
                }
                if (key == "date") { val date = LocalDate.parse(value as String); require(date.year in 1..9999 && date.toString() == value) }
                if (key.endsWith("_time")) { require((value as String).matches(Regex("[0-9]{2}:[0-9]{2}"))); LocalTime.parse(value) }
                if (key in setOf("created_at", "updated_at")) { val time = LocalDateTime.parse(value as String); require(time.year in 1..9999) }
                if (key == "name") require((value as String).isNotBlank() && value.length <= 1000)
                if (key == "dosage") require(value == null || (value as String).length <= 1000)
            }
            val v = record.values
            if (record.type == "catalog") {
                require(v["kind"] in setOf("product", "dish", "medicine"))
                require(v["normalized_name"] == normalizeName(v["name"] as String))
                require(catalogKeys.add(listOf(v["kind"], v["food_type"], v["normalized_name"])))
                require(if (v["kind"] == "dish") v["food_type"] in setOf("home", "restaurant", "unknown") else v["food_type"] == "")
                require(v["kind"] == "dish" || (v["product_ids"] as List<*>).isEmpty())
            }
            if (record.type == "meals") require(v["meal_type"] in setOf("breakfast", "lunch", "dinner", "snack"))
            if (record.type == "stools") require((v["quality"] as Long) in 0..7)
            if (record.type == "water") require((v["glasses_count"] as Long) in 0..Int.MAX_VALUE.toLong())
            if (record.type in setOf("water", "sleeps")) require(dailyKeys.add(listOf(record.type, v["user_id"], v["date"])))
            if (record.type == "meal_items") {
                require(v["kind"] in setOf("dish", "product"))
                require(byId[v["catalog_id"]]?.values?.get("kind") == v["kind"])
                require(if (v["kind"] == "product") v["food_type"] == "" else v["food_type"] in setOf("home", "restaurant", "unknown"))
            }
            if (record.type == "medicine_items") require(byId[v["catalog_id"]]?.values?.get("kind") == "medicine")
            if (record.type in setOf("meal_items", "medicine_items")) {
                require((v["position"] as Long) in 0 until MAX_ITEMS.toLong())
                val parent = v[if (record.type == "meal_items") "meal_id" else "medicine_id"]
                require(positions.add(listOf(record.type, parent, v["position"])))
            }
        }
        val aliasSources = hashSetOf<String>()
        file.aliases.forEach { alias ->
            requireUuid(alias.sourceId); requireUuid(alias.targetId)
            require(aliasSources.add(alias.sourceId) && alias.sourceId !in byId && byId[alias.targetId]?.type == alias.type)
        }
        require(itemCount <= MAX_ITEMS) { "Больше 100 000 объектов и компонентов." }
    }

    private fun requireUuid(value: String) { require(UUID.fromString(value).toString() == value) { "Некорректный UUID." } }
}
