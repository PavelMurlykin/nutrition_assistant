package com.pamurlykin.nutritionassistant.util

private const val MAX_TEXT_LENGTH = 1000

fun validateText(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) { "Поле не должно быть пустым." }
    require(normalized.length <= MAX_TEXT_LENGTH) {
        "Текст не должен быть длиннее $MAX_TEXT_LENGTH символов."
    }
    return normalized
}

fun validateTime(value: String): String {
    StorageFormats.parseTime(value)
    return value
}

fun validateStoolQuality(value: Int): Int {
    require(value in 0..7) { "Оценка стула должна быть в диапазоне от 0 до 7." }
    return value
}
