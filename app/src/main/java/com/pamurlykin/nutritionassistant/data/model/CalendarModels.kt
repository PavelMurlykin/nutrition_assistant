package com.pamurlykin.nutritionassistant.data.model

import java.time.LocalDate
import java.time.YearMonth

/** Counts events, not the individual dishes or medicines inside an intake. */
data class CalendarDayCounts(
    val date: String,
    val meals: Int,
    val medicines: Int,
    val stools: Int,
    val looseStools: Int = 0,
    val softStools: Int = 0,
    val foodComplete: Boolean = false,
    val symptomsComplete: Boolean = false,
    val stoolSummary: String? = null,
)

data class CalendarDayUiModel(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val meals: Int = 0,
    val medicines: Int = 0,
    val stools: Int = 0,
    val looseStools: Int = 0,
    val softStools: Int = 0,
    val foodComplete: Boolean = false,
    val symptomsComplete: Boolean = false,
    val stoolSummary: String? = null,
) {
    val stoolState: CalendarStoolState get() = when {
        looseStools > 0 -> CalendarStoolState.LOOSE
        symptomsComplete && stoolSummary == "no_stool" -> CalendarStoolState.NO_STOOL
        symptomsComplete && stoolSummary == "no_diarrhea" -> CalendarStoolState.NO_LOOSE
        else -> CalendarStoolState.UNCONFIRMED
    }
}

enum class CalendarStoolState(val symbol: String, val title: String) {
    LOOSE("!", "Жидкий стул, тип 6–7"),
    NO_LOOSE("✓", "Подтверждено: жидкого стула не было"),
    NO_STOOL("✓", "Подтверждено: стула не было"),
    UNCONFIRMED("?", "Итог симптомов не подтверждён"),
}

data class CalendarMonthUiModel(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val days: List<CalendarDayUiModel>,
    val isLoaded: Boolean = true,
)

fun calendarStart(month: YearMonth): LocalDate = month.atDay(1).let {
    it.minusDays((it.dayOfWeek.value - 1).toLong())
}

fun buildCalendarMonth(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    counts: List<CalendarDayCounts> = emptyList(),
    isLoaded: Boolean = true,
): CalendarMonthUiModel {
    val byDate = counts.associateBy { it.date }
    val start = calendarStart(month)
    return CalendarMonthUiModel(month, selectedDate, List(42) { offset ->
        val date = start.plusDays(offset.toLong())
        val events = byDate[date.toString()]
        CalendarDayUiModel(date, YearMonth.from(date) == month, date == today,
            events?.meals ?: 0, events?.medicines ?: 0, events?.stools ?: 0,
            events?.looseStools ?: 0, events?.softStools ?: 0, events?.foodComplete ?: false, events?.symptomsComplete ?: false, events?.stoolSummary)
    }, isLoaded)
}

data class CalendarMonthSummary(val days: Int, val looseDays: Int, val looseEpisodes: Int, val softEpisodes: Int,
    val confirmedWithoutLoose: Int, val foodConfirmed: Int, val symptomsConfirmed: Int)
fun summarizeCalendarMonth(month: CalendarMonthUiModel): CalendarMonthSummary {
    val days = month.days.filter { it.inCurrentMonth }
    return CalendarMonthSummary(days.size, days.count { it.looseStools > 0 }, days.sumOf { it.looseStools }, days.sumOf { it.softStools },
        days.count { it.stoolState in setOf(CalendarStoolState.NO_STOOL, CalendarStoolState.NO_LOOSE) }, days.count { it.foodComplete }, days.count { it.symptomsComplete })
}
