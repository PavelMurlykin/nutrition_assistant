package com.pamurlykin.nutritionassistant.data.model

import java.time.LocalDate
import java.time.YearMonth

/** Counts events, not the individual dishes or medicines inside an intake. */
data class CalendarDayCounts(
    val date: String,
    val meals: Int,
    val medicines: Int,
    val stools: Int,
)

data class CalendarDayUiModel(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val meals: Int = 0,
    val medicines: Int = 0,
    val stools: Int = 0,
)

data class CalendarMonthUiModel(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val days: List<CalendarDayUiModel>,
)

fun calendarStart(month: YearMonth): LocalDate = month.atDay(1).let {
    it.minusDays((it.dayOfWeek.value - 1).toLong())
}

fun buildCalendarMonth(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    counts: List<CalendarDayCounts> = emptyList(),
): CalendarMonthUiModel {
    val byDate = counts.associateBy { it.date }
    val start = calendarStart(month)
    return CalendarMonthUiModel(month, selectedDate, List(42) { offset ->
        val date = start.plusDays(offset.toLong())
        val events = byDate[date.toString()]
        CalendarDayUiModel(date, YearMonth.from(date) == month, date == today,
            events?.meals ?: 0, events?.medicines ?: 0, events?.stools ?: 0)
    })
}
