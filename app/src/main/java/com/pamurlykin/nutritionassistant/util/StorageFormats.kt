package com.pamurlykin.nutritionassistant.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object StorageFormats {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val displayDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
    private val shortDisplayDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun today(): String = LocalDate.now().format(dateFormatter)

    fun nowTimestamp(): String = LocalDateTime.now().withNano(0).format(timestampFormatter)

    fun formatDateForStorage(date: LocalDate): String = date.format(dateFormatter)

    fun parseDate(value: String): LocalDate = LocalDate.parse(value, dateFormatter)

    fun formatTimeForStorage(time: LocalTime): String = time.format(timeFormatter)

    fun parseTime(value: String): LocalTime = LocalTime.parse(value, timeFormatter)

    fun formatDateForDisplay(date: LocalDate): String = date.format(displayDateFormatter)

    fun formatShortDate(date: LocalDate): String = date.format(shortDisplayDateFormatter)

    fun plusMinutes(value: String, minutes: Long): String =
        parseTime(value).plusMinutes(minutes).format(timeFormatter)
}
