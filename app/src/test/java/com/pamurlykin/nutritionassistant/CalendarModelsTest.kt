package com.pamurlykin.nutritionassistant

import com.pamurlykin.nutritionassistant.data.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CalendarModelsTest {
    @Test fun leapFebruaryHasCompleteMondayFirstWeeksAndAdjacentMonths() {
        val month = YearMonth.of(2024, 2)
        val state = buildCalendarMonth(month, month.atDay(29), month.atDay(10))
        assertEquals(42, state.days.size)
        assertEquals(DayOfWeek.MONDAY, state.days.first().date.dayOfWeek)
        assertEquals(LocalDate.of(2024, 1, 29), state.days.first().date)
        assertEquals(LocalDate.of(2024, 3, 10), state.days.last().date)
        assertEquals(29, state.days.count { it.inCurrentMonth })
        assertTrue(state.days.single { it.isToday }.date == month.atDay(10))
        state.days.zipWithNext().forEach { (a, b) -> assertEquals(a.date.plusDays(1), b.date) }
    }

    @Test fun decemberGridCrossesYearAndDistinguishesTodayFromSelection() {
        val selected = LocalDate.of(2026, 12, 1)
        val state = buildCalendarMonth(YearMonth.of(2026, 12), selected, LocalDate.of(2026, 11, 15))
        assertEquals(selected, state.selectedDate)
        assertEquals(LocalDate.of(2026, 11, 30), state.days.first().date)
        assertEquals(LocalDate.of(2027, 1, 10), state.days.last().date)
        assertFalse(state.days.any { it.isToday })
    }

    @Test fun allThreeCategoriesAppearOnTheirDayIncludingAdjacentDays() {
        val day = LocalDate.of(2026, 11, 15)
        val state = buildCalendarMonth(YearMonth.from(day), day, day,
            listOf(CalendarDayCounts(day.toString(), 4, 2, 3), CalendarDayCounts("2026-10-31", 1, 0, 1)))
        val selected = state.days.single { it.date == day }
        assertEquals(4, selected.meals)
        assertEquals(2, selected.medicines)
        assertEquals(3, selected.stools)
        assertEquals(1, state.days.single { it.date.toString() == "2026-10-31" }.meals)
        assertEquals(0, state.days.single { it.date == day.plusDays(1) }.meals)
    }
}
