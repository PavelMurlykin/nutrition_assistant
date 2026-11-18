package com.pamurlykin.nutritionassistant.ui.daily

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.ui.components.*
import com.pamurlykin.nutritionassistant.util.StorageFormats
import java.time.LocalDate

@Composable
internal fun CalendarMonthOverview(month: CalendarMonthUiModel, enabled: Boolean, onDate: (LocalDate) -> Unit) {
    var open by rememberSaveable(month.month.toString()) { mutableStateOf(false) }
    var onlyLoose by rememberSaveable { mutableStateOf(false) }
    val summary = summarizeCalendarMonth(month)
    NutritionCard(modifier = Modifier.testTag("month-overview")) {
        CardHeadline("Сводка за ${month.month}")
        if (!month.isLoaded) Text("Загрузка сводки…") else {
            Text("Жидкий стул, тип 6–7: дней ${summary.looseDays}, эпизодов ${summary.looseEpisodes}", modifier = Modifier.testTag("month-loose-count"), color = if (summary.looseDays > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text("Тип 5 (мягкий стул): эпизодов ${summary.softEpisodes}")
            Text("Дней с подтверждением без жидкого стула: ${summary.confirmedWithoutLoose}")
            Text("Подтверждения за весь месяц (${summary.days} дней): питание ${summary.foodConfirmed}, симптомы ${summary.symptomsConfirmed}.", style = MaterialTheme.typography.bodySmall)
            Text("Пропущенные и будущие дни остаются неподтверждёнными. Отметка ! — запись типа 6–7, не диагноз.", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(enabled = enabled && month.isLoaded, onClick = { open = true }, modifier = Modifier.testTag("open-month-days")) { Text("Дни месяца и фильтр") }
    }
    if (open) AppDialog(onDismissRequest = { open = false }, title = { Text("Дни ${month.month}") },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = onlyLoose, onClick = { onlyLoose = !onlyLoose }, modifier = Modifier.testTag("filter-loose-days"), label = { Text("Только дни с типом 6–7") })
            val days = month.days.filter { it.inCurrentMonth && (!onlyLoose || it.looseStools > 0) }
            if (days.isEmpty()) Text("В этом месяце нет записанных эпизодов типа 6–7. Пропуски не означают отсутствия симптома.")
            days.forEach { day -> TextButton(enabled = enabled, modifier = Modifier.testTag("month-list-day-${day.date}"), onClick = { open = false; onDate(day.date) }) {
                Text(StorageFormats.formatDateForDisplay(day.date) + " · " + day.stoolState.symbol + " " + day.stoolState.title + " · эпизодов 6–7: ${day.looseStools}",
                    color = if (day.looseStools > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            } }
        } }, confirmButton = { AppDismissButton() }, dismissButton = null)
}

@Composable
internal fun DayTimelineCard(snapshot: DailyStatsSnapshot) {
    var open by rememberSaveable(snapshot.date) { mutableStateOf(false) }
    val timeline = buildDayTimeline(snapshot)
    NutritionCard(modifier = Modifier.testTag("day-timeline")) {
        CardHeadline("Хронология дня")
        TimelineBody(timeline, full = false)
        if (listOf(timeline.timed, timeline.unknownTime, timeline.wholePeriod).any { it.size > 6 }) TextButton(onClick = { open = true }) { Text("Показать хронологию полностью") }
    }
    if (open) AppDialog(onDismissRequest = { open = false }, title = { Text("Хронология ${snapshot.date}") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { TimelineBody(timeline, full = true) }
    }, confirmButton = { AppDismissButton() }, dismissButton = null)
}

@Composable
private fun TimelineBody(timeline: DayTimeline, full: Boolean) {
    if (timeline.timed.isEmpty() && timeline.unknownTime.isEmpty() && timeline.wholePeriod.isEmpty()) Text("Записей еды, лекарств и состояния нет. Состояние дня неизвестно.")
    Text("Точные события упорядочены с учётом UTC-смещения. Одинаковое время не устанавливает порядок; время создания не используется.", style = MaterialTheme.typography.bodySmall)
    listOf("С фактическим временем" to timeline.timed, "Время не указано — порядок неизвестен" to timeline.unknownTime,
        "За день или ночь — без порядка относительно событий" to timeline.wholePeriod).forEach { (title, entries) ->
        if (entries.isNotEmpty()) {
            Text(title + " (${entries.size})", style = MaterialTheme.typography.titleSmall)
            (if (full) entries else entries.take(6)).forEach { entry -> Column(Modifier.fillMaxWidth().testTag("timeline-${entry.key}").padding(vertical = 4.dp)) {
                if (entry.actualTime != null) Text(recordedTimeLabel(entry.actualTime, entry.utcOffset, entry.timeZone), style = MaterialTheme.typography.bodySmall)
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                entry.details.forEach { Text(it) }; entry.comment?.let { Text(it) }
            } }
            if (!full && entries.size > 6) Text("Показаны первые 6 из ${entries.size}.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
