package com.pamurlykin.nutritionassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.util.StorageFormats
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Adapted from sports_activity_assistant: Monday-first grid, swipe, selection and compact mode. */
@Composable
fun MonthCalendar(
    state: CalendarMonthUiModel,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 460.dp,
    collapseFraction: Float = 0f,
    enabled: Boolean = true,
) {
    val previous by rememberUpdatedState(onPreviousMonth)
    val next by rememberUpdatedState(onNextMonth)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val compact = collapseFraction > 0.5f
    val fontScale = LocalDensity.current.fontScale
    val calendarHeight = if (fontScale > 1.3f) height.coerceAtLeast((200 + 400 * fontScale).dp) else if (!compact) height.coerceAtLeast(400.dp) else height
    Card(modifier.fillMaxWidth().height(calendarHeight).testTag("month-calendar").pointerInput(threshold, enabled) {
        var distance = 0f
        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onHorizontalDrag = { change, delta ->
            change.consume(); distance += delta
        }, onDragEnd = { if (enabled) {
            if (distance < -threshold) next() else if (distance > threshold) previous()
        } })
    }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = if (compact) 2.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 4.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = onPreviousMonth, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, "Предыдущий месяц", tint = MaterialTheme.colorScheme.primary)
                }
                Text(state.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru"))
                    .replaceFirstChar { it.uppercase() } + " " + state.month.year,
                    modifier = Modifier.weight(1f).testTag("calendar-month"), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                IconButton(enabled = enabled, onClick = onNextMonth, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ChevronRight, "Следующий месяц", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (!compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CalendarLegend(Icons.Rounded.Restaurant, "Еда")
                CalendarLegend(Icons.Rounded.Medication, "Лекарства")
                CalendarLegend(Icons.Rounded.Wc, "Туалет")
            }
            if (!compact) {
                Text("! Жидкий стул, тип 6–7", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                Text("✓ Итог без жидкого стула подтверждён · ? Не подтверждён", style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { name ->
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            state.days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { day -> key(day.date) {
                        CalendarDayCell(day, day.date == state.selectedDate, { onDaySelected(day.date) },
                            Modifier.weight(1f).fillMaxHeight(), compact, enabled, state.isLoaded)
                    } }
                }
            }
        }
    }
}

@Composable
private fun CalendarLegend(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDayUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    compact: Boolean,
    enabled: Boolean,
    loaded: Boolean,
) {
    val description = buildList {
        add(StorageFormats.formatDateForDisplay(day.date))
        if (day.isToday) add("Сегодня")
        add("Приемов пищи: ${day.meals}")
        add("Приемов лекарств: ${day.medicines}")
        add("Туалет: ${day.stools}")
        if (loaded) {
            add("Жидкий стул, тип 6–7: ${day.looseStools}")
            add("Мягкий стул, тип 5: ${day.softStools}")
            add(day.stoolState.title)
            add("Питание: " + if (day.foodComplete) "подтверждено" else "не подтверждено")
            add("Симптомы: " + if (day.symptomsComplete) "подтверждены" else "не подтверждены")
        } else add("Данные календаря загружаются")
    }.joinToString(", ")
    val primary = MaterialTheme.colorScheme.primary
    val border = if (isSelected || day.isToday) primary else primary.copy(alpha = 0.08f)
    Box(modifier.testTag("calendar-day-${day.date}").clip(RoundedCornerShape(10.dp))
        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
        .border(if (day.isToday) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
        .semantics(mergeDescendants = true) { selected = isSelected; contentDescription = description }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(2.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (compact) Arrangement.Center else Arrangement.SpaceBetween) {
            val largeText = LocalDensity.current.fontScale > 1.3f
            val number: @Composable () -> Unit = {
                Text(day.date.dayOfMonth.toString(),
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall,
                    color = if (day.inCurrentMonth) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Medium)
            }
            val status: @Composable () -> Unit = {
                Text(if (loaded) day.stoolState.symbol else "…", style = MaterialTheme.typography.labelSmall,
                    modifier = if (day.looseStools > 0) Modifier.background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp) else Modifier,
                    color = if (day.looseStools > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold)
            }
            if (largeText) { number(); status() }
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { number(); status() }
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                val size = if (compact) 10.dp else 12.dp
                if (day.meals > 0) Icon(Icons.Rounded.Restaurant, null, Modifier.size(size), tint = primary)
                if (day.medicines > 0) Icon(Icons.Rounded.Medication, null, Modifier.size(size), tint = primary)
                if (day.stools > 0) Icon(Icons.Rounded.Wc, null, Modifier.size(size), tint = primary)
            }
        }
    }
}
