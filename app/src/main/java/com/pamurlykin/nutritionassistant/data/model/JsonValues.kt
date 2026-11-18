package com.pamurlykin.nutritionassistant.data.model

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer

/** Strict shared JSON reader for backups and immutable composition snapshots. */
object JsonValues {
    @Suppress("UNCHECKED_CAST")
    fun readObject(text: String): Map<String, Any?> = JsonReader(StringReader(text)).use { reader ->
        reader.isLenient = false
        val value = read(reader, 0) as? Map<String, Any?> ?: error("Expected object")
        require(reader.peek() == JsonToken.END_DOCUMENT)
        value
    }
    fun stringify(value: Any?): String = StringWriter().also { writeTo(it, value) }.toString()

    fun writeTo(output: Writer, value: Any?, pretty: Boolean = false) {
        JsonWriter(output).use { writer ->
            if (pretty) writer.setIndent("  ")
            write(writer, value)
        }
    }
    private fun read(reader: JsonReader, depth: Int): Any? {
        require(depth <= 64) { "JSON is too deep" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val values = linkedMapOf<String, Any?>(); reader.beginObject()
                while (reader.hasNext()) {
                    require(values.size < 100_000)
                    val key = reader.nextName(); require(!values.containsKey(key))
                    values[key] = read(reader, depth + 1)
                }
                reader.endObject(); values.toMap()
            }
            JsonToken.BEGIN_ARRAY -> {
                val values = mutableListOf<Any?>(); reader.beginArray()
                while (reader.hasNext()) { require(values.size < 100_000); values += read(reader, depth + 1) }
                reader.endArray(); values.toList()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString().let { require(it.matches(Regex("-?(0|[1-9][0-9]*)"))); it.toLong() }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); null }
            else -> error("Unexpected JSON token")
        }
    }
    private fun write(writer: JsonWriter, value: Any?) {
        when (value) {
            null -> writer.nullValue()
            is String -> writer.value(value)
            is Boolean -> writer.value(value)
            is Number -> writer.value(value)
            is List<*> -> { writer.beginArray(); value.forEach { write(writer, it) }; writer.endArray() }
            is Map<*, *> -> { writer.beginObject(); value.keys.map { it as String }.sorted().forEach { writer.name(it); write(writer, value[it]) }; writer.endObject() }
            else -> error("Unsupported JSON value")
        }
    }
}
