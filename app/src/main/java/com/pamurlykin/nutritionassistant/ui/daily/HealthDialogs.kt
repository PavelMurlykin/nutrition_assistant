package com.pamurlykin.nutritionassistant.ui.daily

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.ui.components.*
import java.time.LocalDate

@Composable
internal fun ScalePicker(metric: String, value: String?, enabled: Boolean = true, allowMissing: Boolean = true, onChange: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val title = HealthScales.metrics[metric] ?: when (metric) { "sleep" -> "Качество сна"; "urgency" -> "Срочность позыва"; else -> "Боль" }
    TextButton(enabled = enabled, onClick = { open = true }, modifier = Modifier.testTag("pick-$metric")) { Text(title + ": " + (value?.let { HealthScales.label(metric, it) } ?: "Не заполнено")) }
    if (open) AppDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (allowMissing) Box(Modifier.testTag("value-$metric-missing")) { AppRadioChoice("Не заполнено", value == null) { onChange(null); open = false } }
            HealthScales.choices(metric).forEach { choice -> Box(Modifier.testTag("value-$metric-${choice.code}")) {
                AppRadioChoice((if (choice.code.toIntOrNull() != null) choice.code + " · " else "") + choice.label, value == choice.code) { onChange(choice.code); open = false }
            } }
        }
    }, confirmButton = { AppDismissButton() }, dismissButton = null)
}

@Composable
private fun OptionalComment(value: String, enabled: Boolean, title: String = "Комментарий", existingValue: String? = null, onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(enabled = enabled, onClick = { open = !open }) { Text(title + if (value.isNotBlank()) " · заполнен" else " (необязательно)") }
    if (open) AppTextField(value, onChange, label = { Text(title) }, enabled = enabled, singleLine = false, isError = value.length > 1000 && value != existingValue)
}

@Composable
private fun ActivityExtras(duration: String, kind: String?, enabled: Boolean, onDuration: (String) -> Unit, onKind: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(enabled = enabled, onClick = { open = !open }) { Text("Минуты и вид нагрузки (необязательно)") }
    if (open) {
        AppTextField(duration, { onDuration(it.filter(Char::isDigit).take(5)) }, label = { Text("Продолжительность, минут") }, enabled = enabled,
            isError = duration.isNotBlank() && duration.toIntOrNull()?.let { it in 1..1440 } != true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = kind == null, onClick = { onKind(null) }, enabled = enabled, label = { Text("Вид не указан") })
            HealthScales.kinds.forEach { option -> FilterChip(selected = kind == option.code, onClick = { onKind(option.code) }, enabled = enabled, label = { Text(option.label) }) }
        }
    }
    val uri = LocalUriHandler.current
    Text("При умеренной нагрузке обычно можно разговаривать, но трудно петь; при высокой — говорить лишь короткими фразами между вдохами.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { uri.openUri("https://www.cdc.gov/physical-activity-basics/measuring/index.html") }) { Text("Ориентир интенсивности: CDC") }
}

@Composable
private fun IllnessExtras(impact: String?, symptoms: List<String>, enabled: Boolean, onImpact: (String?) -> Unit, onSymptoms: (List<String>) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(enabled = enabled, onClick = { open = !open }) { Text("Влияние и категории симптомов (необязательно)") }
    if (open) {
        Text("Влияние на обычные дела")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = impact == null, onClick = { onImpact(null) }, enabled = enabled, label = { Text("Не оценено") })
            HealthScales.impact.forEach { option -> FilterChip(selected = impact == option.code, onClick = { onImpact(option.code) }, enabled = enabled, label = { Text(option.label) }) }
        }
        Text("Категории симптомов; диагноз не требуется")
        HealthScales.symptoms.forEach { option -> Row {
            Checkbox(checked = option.code in symptoms, enabled = enabled, onCheckedChange = { checked -> onSymptoms(if (checked) symptoms + option.code else symptoms - option.code) })
            Text(option.label)
        } }
    }
}

@Composable
internal fun HealthDayDialog(snapshot: DailyStatsSnapshot, busy: Boolean, onDismiss: () -> Unit,
    onSave: (Map<String, HealthDraft?>, Int?, String?) -> Unit) {
    val existing = snapshot.healthEntries.filter { it.period == "day" }.associateBy { it.metric }
    val original = HealthScales.metrics.keys.associateWith { existing[it]?.valueCode.orEmpty() }
    var values by rememberSaveable { mutableStateOf(original) }
    var comments by rememberSaveable { mutableStateOf(HealthScales.metrics.keys.associateWith { existing[it]?.comment.orEmpty() }) }
    var minutes by rememberSaveable { mutableStateOf(existing["activity"]?.durationMinutes?.toString().orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(existing["activity"]?.activityKind) }
    var impact by rememberSaveable { mutableStateOf(existing["illness"]?.illnessImpact) }
    var symptoms by rememberSaveable { mutableStateOf(existing["illness"]?.symptomCodes.orEmpty()) }
    var sleepQuality by rememberSaveable { mutableStateOf(snapshot.sleep?.qualityRating?.toString()) }
    var sleepComment by rememberSaveable { mutableStateOf(snapshot.sleep?.qualityDescription.orEmpty()) }
    val drafts = HealthScales.metrics.keys.associateWith { metric -> values[metric]?.takeIf(String::isNotBlank)?.let { code ->
        HealthDraft(metric, code, durationMinutes = if (metric == "activity") minutes.toIntOrNull() else null,
            activityKind = if (metric == "activity") kind else null, illnessImpact = if (metric == "illness") impact else null,
            symptomCodes = if (metric == "illness") symptoms else emptyList(), context = EventContext(comment = comments[metric]))
    } }
    val valid = runCatching { drafts.values.filterNotNull().forEach { it.validated(snapshot.date, preserveLongComment = existing[it.metric]?.comment == it.context.comment) }; if (sleepComment != snapshot.sleep?.qualityDescription) cleanComment(sleepComment) }.isSuccess &&
        (minutes.isBlank() || minutes.toIntOrNull()?.let { it in 1..1440 } == true)
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Состояние за день") }, busy = busy,
        hasChanges = values != original || comments != HealthScales.metrics.keys.associateWith { existing[it]?.comment.orEmpty() } ||
            minutes != existing["activity"]?.durationMinutes?.toString().orEmpty() || kind != existing["activity"]?.activityKind || impact != existing["illness"]?.illnessImpact || symptoms != existing["illness"]?.symptomCodes.orEmpty() ||
            sleepQuality != snapshot.sleep?.qualityRating?.toString() || sleepComment != snapshot.sleep?.qualityDescription.orEmpty(),
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(snapshot.date + " · собственные шкалы самооценки дневника")
            Text("Не заполнено означает пропуск. Комментарии необязательны. Дневные оценки не заменяют отдельные наблюдения.", style = MaterialTheme.typography.bodySmall)
            ScalePicker("sleep", sleepQuality, !busy) { sleepQuality = it }
            OptionalComment(sleepComment, !busy, "Комментарий о сне", snapshot.sleep?.qualityDescription) { sleepComment = it }
            HealthScales.metrics.keys.forEach { metric ->
                ScalePicker(metric, values[metric]?.takeIf(String::isNotBlank), !busy) { value ->
                    values = values + (metric to value.orEmpty())
                    if (value == null) comments = comments + (metric to "")
                    if (metric == "activity" && (value == null || value == "none")) { minutes = ""; kind = null }
                    if (metric == "illness" && value != "present") { impact = null; symptoms = emptyList() }
                }
                if (metric == "activity" && !values[metric].isNullOrEmpty() && values[metric] != "none") ActivityExtras(minutes, kind, !busy, { minutes = it }, { kind = it })
                if (metric == "illness" && values[metric] == "present") IllnessExtras(impact, symptoms, !busy, { impact = it }, { symptoms = it })
                if (!values[metric].isNullOrEmpty()) OptionalComment(comments[metric].orEmpty(), !busy, "Комментарий: " + HealthScales.metrics.getValue(metric), existing[metric]?.comment) { comments = comments + (metric to it) }
            }
            Text("Очистка дневной оценки удаляет её комментарий. Время и прежние сведения о ночи сохранятся; их можно изменить в разделе сна.", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(enabled = valid && !busy, onClick = { onSave(drafts, sleepQuality?.toInt(), sleepComment.takeIf(String::isNotBlank)) }) { Text("Сохранить день") } })
}

@Composable
internal fun HealthObservationDialog(date: String, metric: String, initial: HealthEntryEntity?, busy: Boolean, onDismiss: () -> Unit, onSave: (HealthDraft) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial?.valueCode) }
    var period by rememberSaveable { mutableStateOf(initial?.period ?: "moment") }
    var minutes by rememberSaveable { mutableStateOf(initial?.durationMinutes?.toString().orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(initial?.activityKind) }
    var impact by rememberSaveable { mutableStateOf(initial?.illnessImpact) }
    var symptoms by rememberSaveable { mutableStateOf(initial?.symptomCodes.orEmpty()) }
    var comment by rememberSaveable { mutableStateOf(initial?.comment.orEmpty()) }
    var time by rememberSaveable(stateSaver = RecordedTimeSaver) { mutableStateOf(initial?.let { RecordedTime(it.actualTime, it.utcOffset, it.timeZone) } ?: RecordedTime()) }
    val draft = HealthDraft(metric, value, period, minutes.toIntOrNull(), kind, impact, symptoms, EventContext(time, comment))
    val valid = runCatching { draft.validated(date, allowLegacy = initial != null && initial.valueCode == null, preserveLongComment = initial != null && initial.comment == comment) }.isSuccess &&
        (minutes.isBlank() || minutes.toIntOrNull()?.let { it in 1..1440 } == true)
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(HealthScales.metrics.getValue(metric)) }, busy = busy,
        hasChanges = (initial?.toDraft() ?: HealthDraft(metric, null, "moment")) != draft.copy(context = draft.context.copy(comment = comment.takeIf(String::isNotBlank))) || minutes != initial?.durationMinutes?.toString().orEmpty(),
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(date)
            if (initial?.valueCode == null && initial != null) Text("Старая запись: числовая оценка отсутствует; прежний текст сохранён в комментарии.")
            Row { FilterChip(selected = period == "day", onClick = { period = "day"; time = RecordedTime() }, enabled = !busy, label = { Text("Весь день") })
                FilterChip(selected = period == "moment", onClick = { period = "moment" }, enabled = !busy, label = { Text("Отдельный момент") }) }
            ScalePicker(metric, value, !busy, allowMissing = initial?.valueCode == null) { code ->
                value = code
                if (metric == "activity" && (code == null || code == "none")) { minutes = ""; kind = null }
                if (metric == "illness" && code != "present") { impact = null; symptoms = emptyList() }
            }
            if (metric == "activity" && value != null && value != "none") ActivityExtras(minutes, kind, !busy, { minutes = it }, { kind = it })
            if (metric == "illness" && value == "present") IllnessExtras(impact, symptoms, !busy, { impact = it }, { symptoms = it })
            if (period == "moment") RecordedTimeEditor(date, time, !busy) { time = it } else Text("Оценка относится ко всему дню, без точного часа.", style = MaterialTheme.typography.bodySmall)
            OptionalComment(comment, !busy, existingValue = initial?.comment) { comment = it }
        } }, confirmButton = { TextButton(enabled = valid && !busy, onClick = { onSave(draft) }) { Text("Сохранить") } })
}

@Composable
internal fun StructuredStoolDialog(date: String, initial: StoolEntity?, busy: Boolean, onDismiss: () -> Unit,
    onSave: (Int, EventContext, StoolDetails) -> Unit) {
    var quality by rememberSaveable { mutableStateOf(initial?.quality?.takeIf { it in 1..7 }) }
    var urgency by rememberSaveable { mutableStateOf(initial?.urgency?.toString()) }
    var pain by rememberSaveable { mutableStateOf(initial?.pain?.toString()) }
    var comment by rememberSaveable { mutableStateOf(initial?.comment.orEmpty()) }
    var time by rememberSaveable(stateSaver = RecordedTimeSaver) { mutableStateOf(initial?.let { RecordedTime(it.actualTime, it.utcOffset, it.timeZone) } ?: RecordedTime()) }
    val context = EventContext(time, comment)
    val valid = quality in 1..7 && runCatching { context.validated(date) }.isSuccess
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Качество стула") }, busy = busy,
        hasChanges = quality != initial?.quality?.takeIf { it in 1..7 } || urgency != initial?.urgency?.toString() || pain != initial?.pain?.toString() || comment != initial?.comment.orEmpty() || time != (initial?.let { RecordedTime(it.actualTime, it.utcOffset, it.timeZone) } ?: RecordedTime()),
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            Text(date + " · выберите тип по Бристольской шкале")
            (1..7).forEach { score -> AppRadioChoice(StoolCondition.label(score), quality == score, !busy) { quality = score } }
            ScalePicker("urgency", urgency, !busy) { urgency = it }; ScalePicker("pain", pain, !busy) { pain = it }
            RecordedTimeEditor(date, time, !busy) { time = it }
            OptionalComment(comment, !busy) { comment = it }
        } }, confirmButton = { TextButton(enabled = valid && !busy, onClick = { onSave(requireNotNull(quality), context, StoolDetails(urgency?.toInt(), pain?.toInt())) }) { Text("Сохранить") } })
}

@Composable
internal fun StructuredSleepDialog(date: String, initial: SleepEntity?, busy: Boolean, onDismiss: () -> Unit, onSave: (SleepDraft) -> Unit) {
    var quality by rememberSaveable { mutableStateOf(initial?.qualityRating?.toString()) }
    var comment by rememberSaveable { mutableStateOf(initial?.qualityDescription.orEmpty()) }
    var awakenings by rememberSaveable { mutableStateOf(initial?.awakenings?.toString().orEmpty()) }
    var interval by rememberSaveable { mutableStateOf(initial?.bedTime != null) }
    var bed by rememberSaveable { mutableStateOf(initial?.bedTime ?: "23:00") }
    var wakeup by rememberSaveable { mutableStateOf(initial?.wakeupTime ?: "07:00") }
    var bedDate by rememberSaveable { mutableStateOf(initial?.bedDate) }
    var wakeupDate by rememberSaveable { mutableStateOf(initial?.wakeupDate) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    val draft = SleepDraft(quality?.toInt(), comment.takeIf(String::isNotBlank), awakenings.toIntOrNull(), if (interval) bed else null, if (interval) wakeup else null,
        if (interval) bedDate else null, if (interval) wakeupDate else null)
    val empty = draft.quality == null && draft.comment == null && draft.awakenings == null && draft.bedTime == null
    val valid = (empty && initial != null || runCatching { draft.validated(date, allowLegacyComment = initial != null && initial.qualityDescription == draft.comment) }.isSuccess) && (awakenings.isBlank() || awakenings.toIntOrNull()?.let { it in 0..1000 } == true)
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Сон") }, busy = busy,
        hasChanges = draft != (initial?.toDraft() ?: SleepDraft()),
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ночь с подъёмом $date. Качество можно оценить без времени.")
            ScalePicker("sleep", quality, !busy) { quality = it }
            OptionalComment(comment, !busy, "Комментарий о сне", initial?.qualityDescription) { comment = it }
            AppTextField(awakenings, { awakenings = it.filter(Char::isDigit).take(5) }, label = { Text("Пробуждения (необязательно)") }, enabled = !busy,
                isError = awakenings.isNotBlank() && awakenings.toIntOrNull()?.let { it in 0..1000 } != true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            Row { Checkbox(checked = interval, enabled = !busy, onCheckedChange = { checked ->
                interval = checked
                if (checked && bedDate == null && initial?.bedTime == null) { bedDate = LocalDate.parse(date).minusDays(1).toString(); wakeupDate = date }
            }); Text("Записать интервал в постели") }
            if (interval) {
                Text("Проверьте обе границы. Это время в постели по местным часам, а не измеренная длительность сна.", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = { target = "bed-date" }) { Text("Дата отбоя: " + (bedDate ?: "не уточнена (старая запись)")) }
                TextButton(enabled = !busy, onClick = { target = "bed" }) { Text("Отбой: $bed") }
                Text("Дата подъёма: " + (wakeupDate ?: "не уточнена (старая запись)"))
                TextButton(enabled = !busy, onClick = { target = "wakeup" }) { Text("Подъем: $wakeup") }
                if (bedDate == null) Text("Прежние часы сохранены без выдуманных дат. Уточните дату отбоя, если ночь известна.", style = MaterialTheme.typography.bodySmall)
            }
            if (empty && initial != null) Text("Сведений не осталось: запись сна будет удалена.")
        } }, confirmButton = { TextButton(enabled = valid && !busy, onClick = { onSave(draft) }) { Text("Сохранить") } })
    if (target == "bed-date") AppDatePicker(bedDate?.let(LocalDate::parse) ?: LocalDate.parse(date).minusDays(1), { target = null }) { bedDate = it.toString(); wakeupDate = date; target = null }
    if (target == "bed" || target == "wakeup") AppTimePicker(if (target == "bed") bed else wakeup, { target = null }) { if (target == "bed") bed = it else wakeup = it; target = null }
}

@Composable
internal fun CompletenessDialog(status: DayStatusEntity?, busy: Boolean, onDismiss: () -> Unit, onSave: (Boolean, Boolean, String?) -> Unit) {
    var food by rememberSaveable { mutableStateOf(status?.foodComplete ?: false) }
    var symptoms by rememberSaveable { mutableStateOf(status?.symptomsComplete ?: false) }
    var summary by rememberSaveable { mutableStateOf(status?.stoolSummary) }
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Полнота дневника за день") }, busy = busy,
        hasChanges = food != (status?.foodComplete ?: false) || symptoms != (status?.symptomsComplete ?: false) || summary != status?.stoolSummary,
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            Row { Checkbox(checked = food, enabled = !busy, modifier = Modifier.testTag("complete-food"), onCheckedChange = { food = it }); Text("Всё питание учтено") }
            Row { Checkbox(checked = symptoms, enabled = !busy, modifier = Modifier.testTag("complete-symptoms"), onCheckedChange = { symptoms = it; if (!it) summary = null }); Text("Все наблюдения и симптомы учтены") }
            if (symptoms) listOf("no_diarrhea" to "Жидкого стула не было", "diarrhea" to "Был жидкий стул, тип 6–7 записан", "no_stool" to "Стула не было").forEach { (code, title) -> AppRadioChoice(title, summary == code, !busy) { summary = code } }
            Text("Пустой день не считается нормальным. После изменения питания или наблюдений соответствующее подтверждение снимается.")
        } }, confirmButton = { TextButton(enabled = !busy && (!symptoms || summary != null), onClick = { onSave(food, symptoms, if (symptoms) summary else null) }) { Text("Подтвердить") } })
}
