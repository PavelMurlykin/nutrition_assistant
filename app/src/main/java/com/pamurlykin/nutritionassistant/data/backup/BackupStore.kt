package com.pamurlykin.nutritionassistant.data.backup

import androidx.room.withTransaction
import com.pamurlykin.nutritionassistant.data.local.NutritionDatabase
import org.json.JSONArray
import com.pamurlykin.nutritionassistant.data.model.JsonValues
import java.time.Instant

data class ImportOptions(val keepLocalConflicts: Boolean = false, val matchCatalog: Boolean = false, val restoreSettings: Boolean = false,
    val restoreCatalog: Boolean = false)
data class ImportIssue(val name: String, val reason: String)
class ImportPreview internal constructor(
    internal val file: BackupFile,
    internal val fingerprint: String,
    internal val additions: List<BackupRecord>,
    internal val aliases: List<BackupAlias>,
    val conflicts: List<ImportIssue>,
    val catalogMatches: List<ImportIssue>,
    val settingsChanged: Boolean,
    val skipped: Int,
) {
    val added: Int get() = additions.size
    val counts: Map<String, Int> get() = file.records.groupingBy { it.type }.eachCount()
}

/** All reads are coherent snapshots; preview is read-only; applying is one Room transaction. */
class BackupStore(private val database: NutritionDatabase, private val appVersion: String) {
    suspend fun export(): ByteArray = BackupCodec.encode(snapshot())
    suspend fun snapshot(): BackupFile = database.withTransaction { snapshotInTransaction() }
    suspend fun preview(file: BackupFile): ImportPreview = database.withTransaction {
        BackupCodec.validate(file)
        plan(file, snapshotInTransaction())
    }

    suspend fun apply(preview: ImportPreview, options: ImportOptions) = database.withTransaction {
        val local = snapshotInTransaction()
        require(BackupCodec.fingerprint(local) == preview.fingerprint) { "Данные изменились после предпросмотра. Выберите файл и проверьте импорт заново." }
        val current = plan(preview.file, local)
        require(current.conflicts.isEmpty() || options.keepLocalConflicts) { "Подтвердите сохранение локальных записей при конфликтах." }
        require(current.catalogMatches.isEmpty() || options.matchCatalog) { "Подтвердите сопоставление справочника по названиям." }
        val db = database.openHelper.writableDatabase
        val localIds = readLocalIds().toMutableMap()
        val mapping = current.aliases.associate { it.sourceId to it.targetId }
        fun resolve(id: String): String = mapping[id] ?: id
        current.additions.forEach { record ->
            val schema = BackupSchema.tables.getValue(record.type)
            val columns = listOf("public_id") + schema.fields.keys.filter { it !in setOf("component_links", "position") }
            val values = listOf<Any?>(record.publicId) + columns.drop(1).map { column ->
                val value = record.values[column]
                when (schema.fields.getValue(column)) {
                    ValueKind.REFERENCE -> requireNotNull(localIds[resolve(value as String)]) { "Не найдена ссылка для импорта." }
                    ValueKind.STRINGS, ValueKind.SNAPSHOT -> value?.let { BackupCodec.canonical(it) }
                    ValueKind.BOOLEAN -> if (value == true) 1L else 0L
                    else -> value
                }
            }
            val statement = db.compileStatement("INSERT INTO ${record.type} (${columns.joinToString(",")}) VALUES (${columns.joinToString(",") { "?" }})")
            try {
                values.forEachIndexed { index, value -> bind(statement, index + 1, value) }
                localIds[record.publicId] = statement.executeInsert()
            } finally { statement.close() }
        }
        val newCatalogIds = current.additions.filter { it.type == "catalog" }.map { it.publicId }.toSet()
        val catalogToWrite = current.file.records.filter { it.type == "catalog" && (it.publicId in newCatalogIds || options.restoreCatalog) }
        catalogToWrite.forEach { record ->
            val targetId = localIds.getValue(resolve(record.publicId))
            if (record.publicId !in newCatalogIds) {
                val fields = BackupSchema.tables.getValue("catalog").fields.keys.filter { it != "component_links" }
                val args = fields.map { key ->
                    val value = record.values[key]
                    when (BackupSchema.tables.getValue("catalog").fields[key]) {
                        ValueKind.STRINGS, ValueKind.SNAPSHOT -> value?.let { BackupCodec.canonical(it) }
                        ValueKind.BOOLEAN -> if (value == true) 1L else 0L
                        else -> value
                    }
                } + targetId
                db.execSQL("UPDATE catalog SET ${fields.joinToString(",") { "$it = ?" }} WHERE id = ?", args.toTypedArray())
                db.execSQL("DELETE FROM dish_products WHERE dish_id = ?", arrayOf(targetId))
                db.execSQL("DELETE FROM product_components WHERE owner_id = ?", arrayOf(targetId))
            }
            val table = if (record.values["kind"] == "product") "product_components" else "dish_products"
            val owner = if (table == "product_components") "owner_id" else "dish_id"
            (record.values.getValue("component_links") as List<*>).forEach { value ->
                val link = value as Map<*, *>
                db.execSQL("INSERT INTO $table ($owner, product_id, quantity, unit, position) VALUES (?, ?, ?, ?, ?)",
                    arrayOf(targetId, localIds.getValue(resolve(link["product_id"] as String)), link["quantity"], link["unit"], link["position"]))
            }
        }
        current.aliases.forEach { alias -> db.execSQL("INSERT OR REPLACE INTO backup_aliases (source_id, target_id, type) VALUES (?, ?, ?)", arrayOf(alias.sourceId, alias.targetId, alias.type)) }
        if (options.restoreSettings) {
            val source = current.file.records.single { it.type == "users" }
            val fields = BackupSchema.tables.getValue("users").fields.keys.toList()
            val args = fields.map { key -> source.values[key].let { if (it is Boolean) if (it) 1L else 0L else it } } + 1L
            db.execSQL("UPDATE users SET ${fields.joinToString(",") { "$it = ?" }} WHERE user_id = ?", args.toTypedArray())
        }
        // New observations invalidate confirmations of existing local days. Imported new confirmations are validated below.
        current.additions.filter { it.type in setOf("meals", "medicines", "stools", "health_entries", "sleeps") }.forEach { event ->
            val oldStatus = local.records.find { it.type == "day_status" && it.values["date"] == event.values["date"] }
            if (oldStatus != null) {
                val fields = if (event.type == "meals") "food_complete = 0" else "symptoms_complete = 0, stool_summary = NULL"
                db.execSQL("UPDATE day_status SET $fields, updated_at = ? WHERE public_id = ?", arrayOf(java.time.LocalDateTime.now().toString(), oldStatus.publicId))
            }
        }
        // Validate the resulting graph too: aliases and preserved local links can create a cycle after individually valid files merge.
        BackupCodec.validate(snapshotInTransaction())
        // Raw SQL participates in Room's transaction/invalidation triggers. No application data is cleared.
    }

    private fun snapshotInTransaction(): BackupFile {
        val db = database.openHelper.writableDatabase
        val records = mutableListOf<BackupRecord>()
        val references = mutableMapOf<Pair<String, Long>, String>()
        BackupSchema.tables.forEach { (table, schema) ->
            db.query("SELECT ${schema.key}, public_id FROM $table").use { cursor ->
                while (cursor.moveToNext()) references[table to cursor.getLong(0)] = cursor.getString(1)
            }
        }
        val products = mutableMapOf<Long, MutableList<Map<String, Any?>>>()
        listOf("dish_products" to "dish_id", "product_components" to "owner_id").forEach { (table, owner) ->
            db.query("SELECT $owner, product_id, quantity, unit, position FROM $table ORDER BY position, product_id").use { cursor ->
                while (cursor.moveToNext()) products.getOrPut(cursor.getLong(0)) { mutableListOf() }.add(mapOf(
                    "product_id" to references.getValue("catalog" to cursor.getLong(1)),
                    "quantity" to if (cursor.isNull(2)) null else cursor.getString(2),
                    "unit" to if (cursor.isNull(3)) null else cursor.getString(3), "position" to cursor.getLong(4)))
            }
        }
        BackupSchema.tables.forEach { (table, schema) ->
            val positions = mutableMapOf<Long, Long>()
            db.query("SELECT * FROM $table ORDER BY ${schema.key}").use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(schema.key))
                    val position = if (table in setOf("meal_items", "medicine_items")) {
                        val parentId = cursor.getLong(cursor.getColumnIndexOrThrow(if (table == "meal_items") "meal_id" else "medicine_id"))
                        positions.getOrDefault(parentId, 0L).also { positions[parentId] = it + 1 }
                    } else 0L
                    val values = schema.fields.mapValues { (key, kind) ->
                        if (key == "component_links") products[id].orEmpty()
                        else if (key == "position") position
                        else {
                            val index = cursor.getColumnIndexOrThrow(key)
                            if (cursor.isNull(index)) null else when (kind) {
                                ValueKind.INTEGER, ValueKind.OPTIONAL_INTEGER -> cursor.getLong(index)
                                ValueKind.BOOLEAN -> { val value = cursor.getLong(index); require(value in 0..1); value == 1L }
                                ValueKind.REFERENCE -> references.getValue(BackupSchema.referenceTable(key) to cursor.getLong(index))
                                ValueKind.SNAPSHOT -> JsonValues.readObject(cursor.getString(index))
                                ValueKind.STRINGS -> JSONArray(cursor.getString(index)).let { array -> List(array.length()) { array.getString(it) } }
                                else -> cursor.getString(index)
                            }
                        }
                    }
                    records += BackupRecord(table, references.getValue(table to id), values)
                }
            }
        }
        val byId = records.associateBy { it.publicId }
        val aliases = mutableListOf<BackupAlias>()
        db.query("SELECT source_id, target_id, type FROM backup_aliases ORDER BY source_id").use { cursor ->
            while (cursor.moveToNext()) {
                val alias = BackupAlias(cursor.getString(0), cursor.getString(1), cursor.getString(2))
                if (byId[alias.targetId]?.type == alias.type && alias.sourceId !in byId) aliases += alias
            }
        }
        return BackupFile(appVersion, Instant.now().toString(), records, aliases)
    }

    private fun readLocalIds(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        BackupSchema.tables.forEach { (table, schema) ->
            database.openHelper.writableDatabase.query("SELECT public_id, ${schema.key} FROM $table").use { cursor ->
                while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getLong(1)
            }
        }
        return result
    }

    private fun plan(file: BackupFile, local: BackupFile): ImportPreview {
        val byId = local.records.associateBy { it.publicId }
        val localAliases = local.aliases.associate { it.sourceId to it.targetId }
        val fileAliasesByTarget = file.aliases.groupBy { it.targetId }
        val fileByType = file.records.groupBy { it.type }
        val localByType = local.records.groupBy { it.type }
        val mapping = mutableMapOf<String, String>()
        fun known(id: String): String? = if (id in byId) id else localAliases[id]
        file.records.forEach { record ->
            val candidates = (listOfNotNull(known(record.publicId)) + fileAliasesByTarget[record.publicId].orEmpty().mapNotNull { known(it.sourceId) }).distinct()
            require(candidates.size <= 1) { "Неоднозначные идентификаторы. Импорт остановлен." }
            candidates.singleOrNull()?.let { target ->
                require(byId[target]?.type == record.type) { "UUID относится к другому виду записи." }
                mapping[record.publicId] = target
            }
        }
        val localUser = local.records.single { it.type == "users" }
        val sourceUser = file.records.single { it.type == "users" }
        mapping[sourceUser.publicId] = localUser.publicId
        val catalogMatches = mutableListOf<ImportIssue>()
        val conflicts = mutableListOf<ImportIssue>()
        val catalogByKey = localByType["catalog"].orEmpty().groupBy { catalogKey(it) }
        val fileCatalogByKey = fileByType["catalog"].orEmpty().groupBy { catalogKey(it) }
        fileByType["catalog"].orEmpty().forEach { record ->
            if (record.publicId !in mapping && fileCatalogByKey[catalogKey(record)]?.size == 1)
                catalogByKey[catalogKey(record)]?.singleOrNull()?.let { target ->
                mapping[record.publicId] = target.publicId
                catalogMatches += ImportIssue(record.values["name"].toString(), "Совпало название и раздел. По умолчанию сохраняются локальные состав и архив. Их восстановление выбирается отдельно ниже; история сохраняет свои снимки.")
            }
            mapping[record.publicId]?.let { require(byId.getValue(it).values["kind"] == record.values["kind"]) { "UUID изменяет раздел справочника." } }
        }
        val catalogTargets = fileByType["catalog"].orEmpty().mapNotNull { mapping[it.publicId] }
        require(catalogTargets.distinct().size == catalogTargets.size) { "Две позиции файла сопоставлены с одной записью справочника. Импорт остановлен." }
        fun dailyKey(record: BackupRecord): List<Any?>? = when {
            record.type in setOf("water", "sleeps", "day_status") -> listOf(record.type, record.values["date"])
            record.type == "health_entries" && record.values["day_slot"] == 1L -> listOf(record.type, record.values["date"], record.values["metric"])
            else -> null
        }
        val dailyByKey = local.records.mapNotNull { record -> dailyKey(record)?.let { it to record } }.toMap()
        file.records.filter { it.publicId !in mapping }.forEach { record ->
            dailyKey(record)?.let { key -> dailyByKey[key]?.let { mapping[record.publicId] = it.publicId } }
        }
        fun mappedValues(record: BackupRecord): Map<String, Any?> = record.values.mapValues { (key, value) ->
            when (BackupSchema.tables.getValue(record.type).fields[key]) {
                ValueKind.REFERENCE -> mapping[value] ?: value
                ValueKind.REFERENCES -> (value as List<*>).map { mapping[it] ?: it }.sortedBy { it.toString() }
                ValueKind.COMPONENTS -> (value as List<*>).map { row -> (row as Map<*, *>).entries.associate { (k, v) ->
                    k as String to if (k == "product_id") mapping[v] ?: v else v } }
                else -> value
            }
        }
        fun childrenByParent(rows: List<BackupRecord>): Map<Any?, List<BackupRecord>> = rows.filter { it.type in setOf("meal_items", "medicine_items") }
            .groupBy { it.values[if (it.type == "meal_items") "meal_id" else "medicine_id"] }
        val fileChildren = childrenByParent(file.records)
        val localChildren = childrenByParent(local.records)
        fun signature(record: BackupRecord, translate: Boolean): String {
            val childType = when (record.type) { "meals" -> "meal_items"; "medicines" -> "medicine_items"; else -> null }
            val children = (if (translate) fileChildren else localChildren)[record.publicId].orEmpty().filter { it.type == childType }
                .sortedBy { it.publicId }.map { listOf(if (translate) mapping[it.publicId] ?: it.publicId else it.publicId, if (translate) mappedValues(it) else it.values) }
            return BackupCodec.canonical(listOf(if (translate) mappedValues(record) else record.values, children))
        }
        val additions = mutableListOf<BackupRecord>()
        val existingParents = hashSetOf<String>()
        // Iterating table order ensures parents/catalog exist before dependent items are inserted.
        BackupSchema.tables.keys.forEach { table -> fileByType[table].orEmpty().sortedBy { (it.values["position"] as? Long) ?: 0L }.forEach recordLoop@ { record ->
            if (table == "users") return@recordLoop
            val parent = when (table) { "meal_items" -> record.values["meal_id"]; "medicine_items" -> record.values["medicine_id"]; else -> null }
            if (parent in existingParents) return@recordLoop
            val target = mapping[record.publicId]?.let { byId[it] }
            if (target != null) {
                require(parent == null) { "Позиция события уже принадлежит другой записи. Импорт остановлен." }
                if (table in setOf("meals", "medicines")) existingParents += record.publicId
                if (signature(record, true) != signature(target, false)) {
                    conflicts += ImportIssue(record.values["name"]?.toString() ?: "${tableTitle(table)} · ${record.values["date"]}",
                        if (table == "catalog") "Справочник различается. По умолчанию сохраняется локальный; восстановление состава и архива выбирается отдельно ниже."
                        else "Данные различаются. Локальная запись и её позиции останутся без изменений.")
                }
            } else additions += record
        } }
        val aliases = linkedMapOf<String, BackupAlias>()
        fun addAlias(source: String, target: String, type: String) {
            if (source == target) return
            require(source !in byId && (localAliases[source] == null || localAliases[source] == target)) { "Конфликт ранее подтверждённых сопоставлений." }
            val alias = BackupAlias(source, target, type)
            require(aliases[source] == null || aliases[source] == alias) { "Противоречивое сопоставление." }
            aliases[source] = alias
        }
        file.records.forEach { record -> mapping[record.publicId]?.let { addAlias(record.publicId, it, record.type) } }
        val availableIds = byId.keys + additions.map { it.publicId }
        file.aliases.forEach { alias ->
            val target = mapping[alias.targetId] ?: alias.targetId
            if (target in availableIds) addAlias(alias.sourceId, target, alias.type)
        }
        val settingsKeys = BackupSchema.tables.getValue("users").fields.keys - setOf("created_at", "updated_at")
        return ImportPreview(file, BackupCodec.fingerprint(local), additions, aliases.values.toList(), conflicts, catalogMatches,
            settingsKeys.any { sourceUser.values[it] != localUser.values[it] }, file.records.size - additions.size)
    }

    private fun catalogKey(record: BackupRecord): List<Any?> = listOf(record.values["kind"], record.values["food_type"], record.values["normalized_name"], record.values["product_kind"], record.values["brand"], record.values["variant_label"])
    private fun bind(statement: androidx.sqlite.db.SupportSQLiteStatement, index: Int, value: Any?) {
        when (value) { null -> statement.bindNull(index); is Long -> statement.bindLong(index, value); is String -> statement.bindString(index, value); else -> error("Unsupported SQL value") }
    }
}

fun tableTitle(type: String): String = when (type) {
    "users" -> "Настройки"; "catalog" -> "Справочник"; "meals" -> "Приёмы пищи"; "medicines" -> "Приёмы лекарств"
    "stools" -> "Стул"; "health_entries" -> "Наблюдения состояния"; "water" -> "Вода"; "sleeps" -> "Сон"
    "day_status" -> "Полнота дня"; "meal_items" -> "Позиции еды"; "medicine_items" -> "Позиции лекарств"; else -> type
}
