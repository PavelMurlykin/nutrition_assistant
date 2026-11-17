package com.pamurlykin.nutritionassistant.data.backup

import com.pamurlykin.nutritionassistant.data.model.*
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
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

internal enum class ValueKind { TEXT, OPTIONAL_TEXT, INTEGER, OPTIONAL_INTEGER, BOOLEAN, STRINGS, REFERENCES, REFERENCE, COMPONENTS, SNAPSHOT }
internal data class BackupTable(val key: String = "id", val fields: Map<String, ValueKind>)

/** Backup versions are independent of Room versions; fields are strict allowlists. */
internal object BackupSchema {
    private fun fields(vararg pairs: Pair<String, ValueKind>) = linkedMapOf(*pairs)
    private val times = fields("created_at" to ValueKind.TEXT, "updated_at" to ValueKind.TEXT)
    private val event = fields("user_id" to ValueKind.REFERENCE, "date" to ValueKind.TEXT)
    val versionOne = linkedMapOf(
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
    private val amounts = fields("quantity" to ValueKind.OPTIONAL_TEXT, "unit" to ValueKind.OPTIONAL_TEXT, "comment" to ValueKind.OPTIONAL_TEXT)
    private val actualTime = fields("actual_time" to ValueKind.OPTIONAL_TEXT, "utc_offset" to ValueKind.OPTIONAL_TEXT,
        "time_zone" to ValueKind.OPTIONAL_TEXT, "time_precision" to ValueKind.TEXT, "comment" to ValueKind.OPTIONAL_TEXT)
    val catalogMetadata = fields("category_code" to ValueKind.OPTIONAL_TEXT, "product_kind" to ValueKind.TEXT,
        "brand" to ValueKind.TEXT, "variant_label" to ValueKind.TEXT, "composition_state" to ValueKind.TEXT,
        "composition_source" to ValueKind.OPTIONAL_TEXT, "composition_checked_on" to ValueKind.OPTIONAL_TEXT,
        "comment" to ValueKind.OPTIONAL_TEXT, "revision" to ValueKind.INTEGER)
    val versionTwo = linkedMapOf<String, BackupTable>().apply {
        versionOne.forEach { (table, schema) ->
            val extra = when (table) {
                "catalog" -> catalogMetadata + fields("component_links" to ValueKind.COMPONENTS)
                "meals", "medicines", "stools" -> actualTime
                "meal_items" -> amounts + fields("source_revision" to ValueKind.OPTIONAL_INTEGER, "composition_snapshot" to ValueKind.SNAPSHOT)
                "medicine_items" -> amounts + fields("source_revision" to ValueKind.OPTIONAL_INTEGER)
                "sleeps" -> fields("bed_date" to ValueKind.OPTIONAL_TEXT, "wakeup_date" to ValueKind.OPTIONAL_TEXT)
                else -> emptyMap()
            }
            put(table, schema.copy(fields = (schema.fields - "product_ids") + extra))
        }
        put("day_status", BackupTable(fields = event + fields("food_complete" to ValueKind.BOOLEAN,
            "symptoms_complete" to ValueKind.BOOLEAN, "stool_summary" to ValueKind.OPTIONAL_TEXT) + times))
    }
    val tables = versionTwo.mapValues { (name, table) -> if (name == "catalog") table.copy(fields = table.fields + ("favorite" to ValueKind.BOOLEAN)) else table }
    fun upgradeTwo(record: BackupRecord): BackupRecord = if (record.type == "catalog") record.copy(values = record.values + ("favorite" to false)) else record
    fun upgrade(record: BackupRecord): BackupRecord {
        val defaults: Map<String, Any?> = when (record.type) {
            "catalog" -> mapOf("category_code" to null, "product_kind" to "basic", "brand" to "", "variant_label" to "",
                "composition_state" to "unknown", "composition_source" to null, "composition_checked_on" to null, "comment" to null,
                "revision" to 1L, "component_links" to (record.values["product_ids"] as List<*>).mapIndexed { position, id ->
                    mapOf("product_id" to id, "quantity" to null, "unit" to null, "position" to position.toLong()) })
            "meals", "medicines", "stools" -> mapOf("actual_time" to null, "utc_offset" to null, "time_zone" to null, "time_precision" to "date_only", "comment" to null)
            "meal_items", "medicine_items" -> mapOf("source_revision" to null, "quantity" to null, "unit" to null, "comment" to null) +
                if (record.type == "meal_items") mapOf("composition_snapshot" to null) else emptyMap()
            "sleeps" -> mapOf("bed_date" to null, "wakeup_date" to null)
            else -> emptyMap()
        }
        return record.copy(values = (record.values - "product_ids") + defaults)
    }
    fun referenceTable(field: String): String = when (field) {
        "user_id" -> "users"; "catalog_id", "product_ids" -> "catalog"
        "meal_id" -> "meals"; "medicine_id" -> "medicines"
        else -> error("Unknown reference")
    }
}

object BackupCodec {
    const val APPLICATION_ID = "com.pamurlykin.nutritionassistant"
    const val VERSION = 3
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
            val root = JsonValues.readObject(text)
            require(root.keys == setOf("applicationId", "schemaVersion", "appVersion", "exportedAt", "records", "aliases")) { "Неподдерживаемые поля JSON." }
            require(root["applicationId"] == APPLICATION_ID) { "Это резервная копия другого приложения." }
            require(root["schemaVersion"] in setOf(1L, 2L, VERSION.toLong())) { "Версия JSON не поддерживается. Обновите приложение." }
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
            val file = BackupFile(root["appVersion"] as String, root["exportedAt"] as String, records, aliases)
            if (root["schemaVersion"] == 1L) {
                validate(file, legacy = true)
                return file.copy(records = file.records.map { BackupSchema.upgradeTwo(BackupSchema.upgrade(it)) }).also { validate(it) }
            }
            if (root["schemaVersion"] == 2L) {
                validate(file, versionTwo = true)
                return file.copy(records = file.records.map(BackupSchema::upgradeTwo)).also { validate(it) }
            }
            return file.also { validate(it) }
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

    internal fun canonical(value: Any?): String = JsonValues.stringify(value)
    private fun recordsValue(records: List<BackupRecord>) = records.sortedWith(compareBy({ it.type }, { it.publicId }))
        .map { mapOf("type" to it.type, "publicId" to it.publicId, "values" to it.values) }
    private fun aliasesValue(aliases: List<BackupAlias>) = aliases.sortedBy { it.sourceId }
        .map { mapOf("sourceId" to it.sourceId, "targetId" to it.targetId, "type" to it.type) }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asObject(): Map<String, Any?> = this as? Map<String, Any?> ?: error("Expected object")
    private fun Any?.asList(): List<*> = this as? List<*> ?: error("Expected array")

    @Suppress("UNCHECKED_CAST")
    fun validate(file: BackupFile, legacy: Boolean = false, versionTwo: Boolean = false) {
        require(file.appVersion.isNotBlank() && file.appVersion.length <= 100)
        Instant.parse(file.exportedAt)
        val byId = file.records.associateBy { it.publicId }
        require(byId.size == file.records.size && file.records.size <= MAX_ITEMS)
        require(file.records.count { it.type == "users" } == 1)
        val tables = if (legacy) BackupSchema.versionOne else if (versionTwo) BackupSchema.versionTwo else BackupSchema.tables
        var itemCount = file.records.size.toLong() + file.aliases.size
        val dailyKeys = hashSetOf<List<Any?>>()
        val positions = hashSetOf<List<Any?>>()
        val legacyKeys = hashSetOf<List<Any?>>()
        val graph = linkedMapOf<String, List<String>>()
        val stoolsByDay = file.records.filter { it.type == "stools" }.groupBy { it.values["date"] }
        fun optional(v: Map<String, Any?>, key: String): String? = v[key] as String?
        fun revision(value: Any?) { require(value == null || value is Long && value in 1..Int.MAX_VALUE.toLong()) }
        fun countNodes(node: CompositionNode): Long = 1L + node.legacyIngredients.size + node.components.sumOf(::countNodes)
        file.records.forEach { record ->
            requireUuid(record.publicId)
            val schema = requireNotNull(tables[record.type])
            require(record.values.keys == schema.fields.keys)
            schema.fields.forEach { (key, kind) ->
                val value = record.values[key]
                require(when (kind) {
                    ValueKind.TEXT, ValueKind.REFERENCE -> value is String
                    ValueKind.OPTIONAL_TEXT -> value == null || value is String
                    ValueKind.INTEGER -> value is Long
                    ValueKind.OPTIONAL_INTEGER -> value == null || value is Long
                    ValueKind.BOOLEAN -> value is Boolean
                    ValueKind.STRINGS, ValueKind.REFERENCES -> value is List<*> && value.all { it is String }
                    ValueKind.COMPONENTS -> value is List<*> && value.all { it is Map<*, *> }
                    ValueKind.SNAPSHOT -> value == null || value is Map<*, *>
                })
                if (value is List<*>) itemCount += value.size
                if (kind == ValueKind.REFERENCE) require(byId[value]?.type == BackupSchema.referenceTable(key))
                if (kind == ValueKind.REFERENCES) {
                    val refs = value as List<*>
                    require(refs.distinct().size == refs.size)
                    refs.forEach { require(byId[it]?.type == "catalog" && byId[it]?.values?.get("kind") == "product") }
                }
                if (key == "date") validateDay(value as String)
                if (key.endsWith("_time") && value != null) { require((value as String).matches(Regex("[0-9]{2}:[0-9]{2}"))); LocalTime.parse(value) }
                if (key in setOf("created_at", "updated_at")) { val time = LocalDateTime.parse(value as String); require(time.year in 1..9999) }
                if (key == "name") require((value as String).isNotBlank() && value.length <= 1000)
                if (key in setOf("dosage", "comment")) require(value == null || (value as String).length <= 1000)
            }
            val v = record.values
            if (record.type == "catalog") {
                val kind = v["kind"] as String
                require(kind in setOf("product", "dish", "medicine"))
                require(v["normalized_name"] == normalizeName(v["name"] as String))
                require(if (kind == "dish") v["food_type"] in setOf("home", "restaurant", "unknown") else v["food_type"] == "")
                if (legacy) {
                    require(legacyKeys.add(listOf(kind, v["food_type"], v["normalized_name"])))
                    require(kind == "dish" || (v["product_ids"] as List<*>).isEmpty())
                } else {
                    CatalogDetails(optional(v, "category_code"), v["product_kind"] as String, v["brand"] as String, v["variant_label"] as String,
                        v["composition_state"] as String, optional(v, "composition_source"), optional(v, "composition_checked_on"), optional(v, "comment")).validated(kind)
                    revision(v["revision"])
                    val links = v["component_links"] as List<Map<String, Any?>>
                    require(kind != "medicine" || links.isEmpty())
                    val ids = hashSetOf<String>(); val order = hashSetOf<Long>()
                    links.forEach { link ->
                        require(link.keys == setOf("product_id", "quantity", "unit", "position"))
                        val id = link["product_id"] as String
                        require(byId[id]?.type == "catalog" && byId[id]?.values?.get("kind") == "product" && ids.add(id))
                        require(link["quantity"] == null || link["quantity"] is String)
                        require(link["unit"] == null || link["unit"] is String)
                        Portion(optional(link, "quantity"), optional(link, "unit")).validated()
                        val position = link["position"] as Long
                        require(position in 0 until MAX_ITEMS.toLong() && order.add(position))
                    }
                    require(kind != "dish" || v["composition_state"] == "unknown" || links.isNotEmpty())
                    graph[record.publicId] = links.map { it["product_id"] as String }
                }
            }
            if (record.type == "meals") require(v["meal_type"] in setOf("breakfast", "lunch", "dinner", "snack"))
            if (record.type == "stools") require((v["quality"] as Long) in 0..7)
            if (record.type == "water") require((v["glasses_count"] as Long) in 0..Int.MAX_VALUE.toLong())
            if (record.type in setOf("water", "sleeps", "day_status")) require(dailyKeys.add(listOf(record.type, v["user_id"], v["date"])))
            if (!legacy && record.type in setOf("meals", "medicines", "stools")) {
                val time = RecordedTime(optional(v, "actual_time"), optional(v, "utc_offset"), optional(v, "time_zone")).validated(v["date"] as String)
                require(time.precision == v["time_precision"])
            }
            if (!legacy && record.type == "sleeps") {
                require((v["bed_date"] == null) == (v["wakeup_date"] == null))
                if (v["bed_date"] != null) {
                    SleepPeriod(v["bed_date"] as String, v["wakeup_date"] as String).validate(v["bed_time"] as String, v["wakeup_time"] as String)
                    require(v["wakeup_date"] == v["date"])
                }
            }
            if (record.type == "meal_items") {
                require(v["kind"] in setOf("dish", "product"))
                require(byId[v["catalog_id"]]?.values?.get("kind") == v["kind"])
                require(if (v["kind"] == "product") v["food_type"] == "" else v["food_type"] in setOf("home", "restaurant", "unknown"))
                if (!legacy) {
                    require((v["composition_snapshot"] == null) == (v["source_revision"] == null))
                    (v["composition_snapshot"] as? Map<String, Any?>)?.let { value ->
                        val root = CompositionSnapshot.fromValue(value)
                        require(root.kind == v["kind"] && root.name == v["name"] && root.revision.toLong() == v["source_revision"])
                        require(root.portion == Portion() && root.ingredientNames() == v["ingredients"])
                        itemCount += countNodes(root)
                    }
                }
            }
            if (record.type == "medicine_items") require(byId[v["catalog_id"]]?.values?.get("kind") == "medicine")
            if (record.type in setOf("meal_items", "medicine_items")) {
                require((v["position"] as Long) in 0 until MAX_ITEMS.toLong())
                val parent = v[if (record.type == "meal_items") "meal_id" else "medicine_id"]
                require(positions.add(listOf(record.type, parent, v["position"])))
                if (!legacy) { Portion(optional(v, "quantity"), optional(v, "unit")).validated(); revision(v["source_revision"]) }
            }
            if (record.type == "day_status") {
                require(v["stool_summary"] in setOf(null, "no_diarrhea", "diarrhea", "no_stool"))
                require(if (v["symptoms_complete"] == true) v["stool_summary"] != null else v["stool_summary"] == null)
                if (v["symptoms_complete"] == true) {
                    val entries = stoolsByDay[v["date"]].orEmpty()
                    require(v["stool_summary"] != "no_stool" || entries.isEmpty())
                    require(v["stool_summary"] != "no_diarrhea" || entries.none { (it.values["quality"] as Long) in 6..7 })
                    require(v["stool_summary"] != "diarrhea" || entries.any { (it.values["quality"] as Long) in 6..7 })
                }
            }
        }
        if (!legacy) {
            CompositionGraph.validate(graph)
            file.records.filter { it.type == "meal_items" }.groupBy { it.values["meal_id"] }.values.forEach { items ->
                require(items.sumOf { item -> (item.values["composition_snapshot"] as? Map<String, Any?>)?.let { countNodes(CompositionSnapshot.fromValue(it)) } ?: 1L } <= CompositionGraph.MAX_NODES)
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
