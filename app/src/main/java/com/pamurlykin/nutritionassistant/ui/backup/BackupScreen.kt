package com.pamurlykin.nutritionassistant.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.nutritionassistant.data.backup.*
import com.pamurlykin.nutritionassistant.ui.components.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BackupScreen(viewModel: BackupViewModel) {
    val context = LocalContext.current
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val prepared by viewModel.preparedExport.collectAsStateWithLifecycle()
    val diaryPrepared by viewModel.diaryPrepared.collectAsStateWithLifecycle()
    val diaryError by viewModel.diaryError.collectAsStateWithLifecycle()
    val diarySaved by viewModel.diarySaved.collectAsStateWithLifecycle()
    var showDiary by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(diarySaved) {
        if (diarySaved) {
            showDiary = false
            viewModel.clearDiary()
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { viewModel.saveExport(context.contentResolver, it) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.openImport(context.contentResolver, it) }
    LaunchedEffect(prepared, busy) {
        if (prepared && !busy) {
            viewModel.exportPickerOpened()
            export.launch("nutrition-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json")
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("backup-screen"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Данные", style = MaterialTheme.typography.headlineMedium) }
        item { NutritionCard {
            Text("Резервные копии", style = MaterialTheme.typography.titleLarge)
            Text("JSON сохраняет весь дневник, справочники, архивные позиции, состав записанных приёмов пищи и настройки напоминаний.")
            Text("Файл не зашифрован и содержит сведения о здоровье. Выбирайте место хранения и получателя внимательно.")
            Button(enabled = !busy && preview == null, onClick = viewModel::prepareExport, modifier = Modifier.testTag("export-backup")) { Text("Экспорт резервной копии") }
            OutlinedButton(enabled = !busy && preview == null, onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.testTag("import-backup")) { Text("Импорт из JSON") }
        } }
        item { DiaryExportCard(!busy && preview == null) { showDiary = true } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Выполняется операция…") }
        if (message != null) item { Text(message.orEmpty(), modifier = Modifier.testTag("backup-message")) }
        item { Text("Импорт сначала показывает проверку. До нажатия «Применить импорт» дневник не меняется. Существующая база не очищается. Экспорт JSON 5, импорт JSON 1–5; лимит 16 МиБ и 100 000 объектов и компонентов.") }
        item { Text("После закрытия процесса выберите файл заново. Прерванный экспорт может оставить неполный файл: сохраните новую копию и проверьте её импортом.") }
    }
    if (showDiary && !diarySaved) {
        DiaryExportDialogs(diaryPrepared, busy, diaryError, viewModel::prepareDiary, viewModel::clearDiary, viewModel::saveDiary) {
            viewModel.clearDiary()
            showDiary = false
        }
    }
    preview?.let { selected ->
        ImportPreviewDialog(selected, busy, viewModel::cancelPreview, viewModel::applyImport)
    }
}

@Composable
fun ImportPreviewDialog(preview: ImportPreview, busy: Boolean, onDismiss: () -> Unit, onApply: (ImportOptions) -> Unit) {
    var keepLocal by rememberSaveable(preview) { mutableStateOf(false) }
    var matchCatalog by rememberSaveable(preview) { mutableStateOf(false) }
    var restoreSettings by rememberSaveable(preview) { mutableStateOf(false) }
    var restoreCatalog by rememberSaveable(preview) { mutableStateOf(false) }
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }, busy = busy,
        hasChanges = keepLocal || matchCatalog || restoreSettings || restoreCatalog,
        title = { Text("Проверка импорта") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp).testTag("import-preview"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("История из файла будет объединена с текущим личным дневником.")
                    Text("Добавить объектов: ${preview.added}. Совпадающие или сохраняемые локально: ${preview.skipped}.")
                }
                items(preview.counts.entries.toList(), key = { it.key }) { Text("${tableTitle(it.key)}: ${it.value}") }
                if (preview.catalogMatches.isNotEmpty()) {
                    item { Text("Сопоставления справочника: ${preview.catalogMatches.size}", style = MaterialTheme.typography.titleSmall) }
                    items(preview.catalogMatches.take(100)) { Text("${it.name}: ${it.reason}") }
                    if (preview.catalogMatches.size > 100) item { Text("Показаны первые 100 сопоставлений.") }
                    item { ImportCheck("Сопоставить одноимённые позиции с локальным справочником", matchCatalog, !busy) { matchCatalog = it } }
                }
                if (preview.conflicts.isNotEmpty()) {
                    item { Text("Различающиеся записи: ${preview.conflicts.size}", style = MaterialTheme.typography.titleSmall) }
                    items(preview.conflicts.take(100)) { Text("${it.name}: ${it.reason}") }
                    if (preview.conflicts.size > 100) item { Text("Показаны первые 100 конфликтов.") }
                    item { ImportCheck("Сохранить локальные события при конфликтах; справочник — по выбору ниже", keepLocal, !busy) { keepLocal = it } }
                }
                item {
                    ImportCheck("Восстановить состав и архив справочника из файла", restoreCatalog, !busy) { restoreCatalog = it }
                    Text("Если включить: текущие данные сопоставленных продуктов, блюд и лекарств будут заменены данными файла, включая избранное, источники и варианты. Исторические снимки еды и лекарств останутся прежними. По умолчанию справочник сохраняется локальный.")
                    Text(if (preview.settingsChanged) "Настройки напоминаний отличаются." else "Настройки напоминаний совпадают.")
                    ImportCheck("Восстановить настройки напоминаний из файла", restoreSettings, !busy) { restoreSettings = it }
                    Text("По умолчанию текущие настройки сохраняются. Отмена оставляет дневник без изменений.")
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && (preview.conflicts.isEmpty() || keepLocal) && (preview.catalogMatches.isEmpty() || matchCatalog),
            onClick = { onApply(ImportOptions(keepLocal, matchCatalog, restoreSettings, restoreCatalog)) }, modifier = Modifier.testTag("apply-import")) { Text("Применить импорт") } },
        dismissButton = { AppDismissButton(enabled = !busy) },
    )
}

@Composable
private fun ImportCheck(text: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(text, Modifier.weight(1f).padding(top = 12.dp))
    }
}
