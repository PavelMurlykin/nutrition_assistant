package com.pamurlykin.nutritionassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.data.model.*
import java.time.*
import java.time.format.DateTimeFormatter

val RecordedTimeSaver = listSaver<RecordedTime, String>(save = { listOf(it.time.orEmpty(), it.utcOffset.orEmpty(), it.timeZone.orEmpty()) },
    restore = { RecordedTime(it[0].takeIf(String::isNotBlank), it[1].takeIf(String::isNotBlank), it[2].takeIf(String::isNotBlank)) })

fun recordedTimeLabel(time: String?, offset: String?, zone: String?): String = if (time == null) "Фактическое время не указано"
    else "$time · UTC${if (offset == "Z") "+00:00" else offset.orEmpty()}" + (zone?.let { " · $it" } ?: "")

@Composable
fun RecordedTimeEditor(date: String, value: RecordedTime, enabled: Boolean = true, onChange: (RecordedTime) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(recordedTimeLabel(value.time, value.utcOffset, value.timeZone), style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = enabled, onClick = { choosing = true }) { Text(if (value.time == null) "Указать фактическое время" else "Изменить фактическое время") }
        if (value.time != null) {
            AppTextField(value.utcOffset.orEmpty(), { onChange(value.copy(utcOffset = it.trim().takeIf(String::isNotBlank))) },
                label = { Text("Смещение UTC, например +03:00") }, modifier = Modifier.testTag("event-utc-offset"), enabled = enabled)
            AppTextField(value.timeZone.orEmpty(), { onChange(value.copy(timeZone = it.trim().takeIf(String::isNotBlank))) },
                label = { Text("Часовой пояс (необязательно)") }, modifier = Modifier.testTag("event-time-zone"), enabled = enabled)
            val offsets = runCatching { value.timeZone?.let { ZoneId.of(it).rules.getValidOffsets(LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(value.time))) } }.getOrNull()
            if (offsets?.size == 2) {
                Text("Время повторяется при переводе часов. Уточните смещение.")
                FlowRow { offsets.forEach { offset -> FilterChip(selected = value.utcOffset == offset.id, onClick = { onChange(value.copy(utcOffset = offset.id)) }, enabled = enabled, label = { Text(offset.id) }) } }
            }
            if (runCatching { value.validated(date) }.isFailure) Text("Проверьте время, UTC-смещение и пояс для выбранной даты.", color = MaterialTheme.colorScheme.error)
            TextButton(enabled = enabled, onClick = { onChange(RecordedTime()) }) { Text("Время неизвестно") }
        }
    }
    if (choosing) AppTimePicker(value.time ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), { choosing = false }) { selected ->
        val zone = value.timeZone ?: if (value.time == null) ZoneId.systemDefault().id else null
        val offsets = runCatching { zone?.let { ZoneId.of(it).rules.getValidOffsets(LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(selected))) } }.getOrNull()
        val offset = if (zone == null) value.utcOffset else offsets?.singleOrNull()?.id
        onChange(RecordedTime(selected, offset, zone)); choosing = false
    }
}
