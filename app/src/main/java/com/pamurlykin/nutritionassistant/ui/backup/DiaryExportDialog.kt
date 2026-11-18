package com.pamurlykin.nutritionassistant.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.data.export.*
import com.pamurlykin.nutritionassistant.ui.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal data class PreparedDiaryExport(
    val options: DiaryExportOptions,
    val counts: Map<DiarySection, Int>,
    val format: DiaryExportFormat,
    val bytes: ByteArray,
) {
    val fileName get() = "nutrition-analysis-${options.start}_${options.end}.${format.extension}"
}

@Composable
internal fun DiaryExportDialogs(
    prepared: PreparedDiaryExport?, busy: Boolean, error: String?,
    onPrepare: (DiaryExportOptions, DiaryExportFormat) -> Unit, onClear: () -> Unit,
    onSave: (ContentResolver, Uri?) -> Unit, onClose: () -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        pickerOpen = false
        onSave(resolver, it)
    }
    val saveMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        pickerOpen = false
        onSave(resolver, it)
    }
    var startText by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(29).toString()) }
    var endText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var dateField by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableIntStateOf(DiarySection.defaults.sumOf { 1 shl it.ordinal }) }
    var formatName by rememberSaveable { mutableStateOf(DiaryExportFormat.JSON.name) }
    var comments by rememberSaveable { mutableStateOf(false) }
    var hideNames by rememberSaveable { mutableStateOf(false) }
    var prompt by rememberSaveable { mutableStateOf(DEFAULT_EXPORT_PROMPT) }
    val start = LocalDate.parse(startText)
    val end = LocalDate.parse(endText)
    val days = ChronoUnit.DAYS.between(start, end) + 1
    val format = DiaryExportFormat.valueOf(formatName)
    val sections = DiarySection.entries.filterTo(mutableSetOf()) { selected and (1 shl it.ordinal) != 0 }
    val displayDate = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    val field = dateField
    if (field != null) {
        AppDatePicker(if (field == "start") start else end, { dateField = null }) {
            if (field == "start") startText = it.toString() else endText = it.toString()
        }
        return
    }
    if (prepared != null) {
        val options = prepared.options
        AppDialog(title = { Text("Пакет готов к сохранению") }, onDismissRequest = { if (!pickerOpen) onClear() }, busy = busy,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("diary-export-preview"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${prepared.format.title} · ${options.start} — ${options.end} · ${prepared.bytes.size} байт")
                    prepared.counts.forEach { (section, count) -> Text("${section.label} — $count") }
                    if (DiarySection.COMPOSITION in options.sections) Text("Исторический состав включён.")
                    if (DiarySection.ANALYSIS in options.sections) Text("Наблюдения аналитики включены.")
                    Text(if (options.includeComments) "Комментарии включены. Они могут содержать личные сведения." else "Комментарии исключены.")
                    if (options.hideNames) Text("Свободные названия, марки, источники и текст доз скрыты. Даты и ID событий остаются.")
                    Text("Файл содержит сведения о здоровье. Проверьте его перед передачей внешнему сервису.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(enabled = !busy && !pickerOpen, onClick = onClear) { Text("Изменить состав") } },
            confirmButton = {
                TextButton(enabled = !busy && !pickerOpen, onClick = {
                    pickerOpen = true
                    if (prepared.format == DiaryExportFormat.JSON) saveJson.launch(prepared.fileName)
                    else saveMarkdown.launch(prepared.fileName)
                }, modifier = Modifier.testTag("diary-export-save")) { Text("Сохранить файл") }
            },
        )
    } else {
        AppDialog(title = { Text("Выгрузка для анализа") }, onDismissRequest = onClose, busy = busy,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("diary-export-form"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow {
                        TextButton(enabled = !busy, onClick = { dateField = "start" }) { Text("С ${start.format(displayDate)}") }
                        TextButton(enabled = !busy, onClick = { dateField = "end" }) { Text("По ${end.format(displayDate)}") }
                    }
                    if (days !in 1..MAX_EXPORT_DAYS.toLong()) Text("Допустимо от 1 до 365 дней включительно.", color = MaterialTheme.colorScheme.error)
                    Text("Разделы", style = MaterialTheme.typography.titleMedium)
                    DiarySection.entries.forEach { section ->
                        val enabled = !busy && when (section) {
                            DiarySection.COMPOSITION -> DiarySection.FOOD in sections
                            DiarySection.ANALYSIS -> sections.containsAll(DiarySection.analysisInputs)
                            else -> true
                        }
                        ExportCheck(section.label, section in sections, enabled, "diary-section-${section.name}") { checked ->
                            val next = sections.toMutableSet()
                            if (checked) next.add(section) else next.remove(section)
                            if (DiarySection.FOOD !in next) next.remove(DiarySection.COMPOSITION)
                            if (!next.containsAll(DiarySection.analysisInputs)) next.remove(DiarySection.ANALYSIS)
                            selected = next.sumOf { 1 shl it.ordinal }
                        }
                    }
                    Text("Для наблюдений аналитики выберите питание, стул и полноту дневника.", style = MaterialTheme.typography.bodySmall)
                    ExportCheck("Включить комментарии", comments, !busy, "diary-export-comments") { comments = it }
                    ExportCheck("Скрыть свободные названия и источники", hideNames, !busy, "diary-export-hide-names") { hideNames = it }
                    if (hideNames) Text("Названия блюд, продуктов и лекарств будут заменены метками. Марки, варианты, источники состава и текст доз будут пропущены.")
                    Text("Имена и магазины могут встречаться в свободном тексте. Проверьте запрос и включённые комментарии.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Формат файла", style = MaterialTheme.typography.titleMedium)
                    FlowRow {
                        DiaryExportFormat.entries.forEach { choice ->
                            FilterChip(selected = choice == format, enabled = !busy, onClick = { formatName = choice.name },
                                label = { Text(choice.title) })
                        }
                    }
                    AppTextField(prompt, { prompt = it.take(MAX_EXPORT_PROMPT) }, label = { Text("Запрос для внешней модели (необязательно)") },
                        singleLine = false, enabled = !busy,
                        supportingText = { Text("До 4000 символов. Пустое поле исключает запрос из файла.") })
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && days in 1..MAX_EXPORT_DAYS.toLong() && sections.isNotEmpty(),
                    onClick = { onPrepare(DiaryExportOptions(start, end, sections, comments, hideNames, prompt), format) },
                    modifier = Modifier.testTag("diary-export-prepare")) { Text("Подготовить пакет") }
            },
        )
    }
}

@Composable
internal fun DiaryExportCard(enabled: Boolean, onClick: () -> Unit) {
    NutritionCard {
        Text("Выгрузка для анализа", style = MaterialTheme.typography.titleLarge)
        Text("Сохраните выбранные записи и запрос для внешней модели в JSON или Markdown.")
        OutlinedButton(enabled = enabled, onClick = onClick, modifier = Modifier.testTag("diary-export-open")) {
            Text("Настроить выгрузку")
        }
    }
}

@Composable
private fun ExportCheck(label: String, checked: Boolean, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(top = 12.dp))
    }
}
