package com.pamurlykin.nutritionassistant.ui.daily

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.data.model.CalendarMonthUiModel
import com.pamurlykin.nutritionassistant.ui.components.MonthCalendar
import java.time.LocalDate

@Composable
internal fun CalendarDayLayout(
    calendar: CalendarMonthUiModel,
    selectedDate: LocalDate,
    busy: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onAdd: () -> Unit,
    addEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val list = rememberLazyListState()
    var collapsed by rememberSaveable { mutableFloatStateOf(0f) }
    LaunchedEffect(selectedDate) { list.scrollToItem(0) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Календарь", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                TextButton(enabled = !busy, onClick = onToday) { Text("Сегодня") }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // A single scrolling list leaves both the calendar and diary usable on short screens.
                val shortScreen = maxHeight < 440.dp || LocalDensity.current.fontScale > 1.3f
                if (shortScreen) {
                    LazyColumn(Modifier.fillMaxSize().testTag("daily-list"), state = list,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item(key = "calendar") {
                            MonthCalendar(calendar, onPreviousMonth, onNextMonth, onDate,
                                height = 400.dp, enabled = !busy)
                        }
                        content()
                    }
                } else {
                    val fullHeight = (maxHeight * 0.70f).coerceIn(360.dp, 520.dp)
                    val minimumHeight = 260.dp
                    val collapseRange = with(LocalDensity.current) { (fullHeight - minimumHeight).toPx() }
                    val connection = remember(list, collapseRange) {
                        object : NestedScrollConnection {
                            fun consume(delta: Float): Offset {
                                if (collapseRange <= 0) return Offset.Zero
                                val old = collapsed
                                collapsed = (old - delta / collapseRange).coerceIn(0f, 1f)
                                return Offset(0f, (old - collapsed) * collapseRange)
                            }
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                                if (available.y < 0 || !list.canScrollBackward) consume(available.y) else Offset.Zero
                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                                if (available.y > 0 && !list.canScrollBackward) consume(available.y) else Offset.Zero
                        }
                    }
                    Column(Modifier.fillMaxSize().nestedScroll(connection).padding(horizontal = 12.dp)) {
                        MonthCalendar(calendar, onPreviousMonth, onNextMonth, onDate,
                            modifier = Modifier.scrollable(rememberScrollableState { delta -> -list.dispatchRawDelta(-delta) }, Orientation.Vertical),
                            height = fullHeight - (fullHeight - minimumHeight) * collapsed,
                            collapseFraction = collapsed, enabled = !busy)
                        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("daily-list"), state = list,
                            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    }
                }
            }
        }
        if (!busy && addEnabled) FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Rounded.Add, "Добавить запись", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
