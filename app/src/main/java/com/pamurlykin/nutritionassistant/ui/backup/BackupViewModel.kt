package com.pamurlykin.nutritionassistant.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pamurlykin.nutritionassistant.AppContainer
import com.pamurlykin.nutritionassistant.data.backup.*
import com.pamurlykin.nutritionassistant.data.export.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(private val container: AppContainer) : ViewModel() {
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val preview = MutableStateFlow<ImportPreview?>(null)
    val preparedExport = MutableStateFlow(false)
    private var exportBytes: ByteArray? = null

    private fun run(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; message.value = null
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { container.repository.initialize(); action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = "Не удалось выполнить операцию. Проверьте файл и доступное место. Существующие записи не очищались." }
            finally { busy.value = false }
        }
    }

    internal val diaryPrepared = MutableStateFlow<PreparedDiaryExport?>(null)
    internal val diaryError = MutableStateFlow<String?>(null)
    internal val diarySaved = MutableStateFlow(false)

    internal fun prepareDiary(options: DiaryExportOptions, format: DiaryExportFormat) {
        diaryError.value = null
        run {
            try {
                val (history, water) = container.repository.analytics.readExportData(options)
                val prepared = withContext(Dispatchers.Default) {
                    val data = DiaryExportBuilder.build(history, water, options)
                    val bytes = DiaryExportWriter.encode(data, format) ?: return@withContext null
                    val eventCounts = data.events.groupingBy { it.section }.eachCount()
                    val counts = DiarySection.entries
                        .filter { it in options.sections && it != DiarySection.COMPOSITION && it != DiarySection.ANALYSIS }
                        .associateWith { eventCounts[it] ?: 0 }
                    PreparedDiaryExport(options, counts, format, bytes)
                }
                if (prepared == null) diaryError.value = "Файл больше 16 МиБ. Сократите период или число разделов."
                else diaryPrepared.value = prepared
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { diaryError.value = "Не удалось подготовить пакет. Попробуйте сократить период." }
        }
    }

    internal fun clearDiary() {
        diaryPrepared.value = null
        diaryError.value = null
        diarySaved.value = false
    }

    internal fun saveDiary(resolver: ContentResolver, uri: Uri?) {
        if (uri == null) { diaryError.value = "Сохранение отменено."; return }
        val file = diaryPrepared.value
        if (file == null) { diaryError.value = "Подготовьте пакет заново."; return }
        diaryError.value = null
        run {
            try {
                requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(file.bytes) }
                diarySaved.value = true
                diaryPrepared.value = null
                message.value = "Пакет для анализа сохранён."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { diaryError.value = "Не удалось записать весь файл. Выберите место для новой копии." }
        }
    }

    fun prepareExport() = run {
        exportBytes = null
        exportBytes = container.backupStore.export()
        preparedExport.value = true
    }
    fun exportPickerOpened() { preparedExport.value = false }
    fun saveExport(resolver: ContentResolver, uri: Uri?) {
        if (uri == null) { exportBytes = null; message.value = "Экспорт отменён."; return }
        run {
            val bytes = requireNotNull(exportBytes) { "Подготовьте копию заново." }
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes); it.flush() }
            exportBytes = null
            message.value = "Резервная копия JSON сохранена."
        }
    }
    fun openImport(resolver: ContentResolver, uri: Uri?) {
        if (uri == null) return
        run {
            preview.value = null
            val file = try { requireNotNull(resolver.openInputStream(uri)).use(BackupCodec::read) }
            catch (_: Exception) {
                message.value = "Файл не прочитан: нужен корректный JSON Nutrition Assistant версии 1–5, UTF-8, до 16 МиБ и 100 000 объектов. База не изменена."
                return@run
            }
            preview.value = container.backupStore.preview(file)
        }
    }
    fun cancelPreview() { if (!busy.value) preview.value = null }
    fun applyImport(options: ImportOptions) = run {
        val selected = requireNotNull(preview.value)
        try { container.backupStore.apply(selected, options) }
        catch (failure: IllegalArgumentException) {
            preview.value = null
            message.value = if (failure.message?.startsWith("Данные изменились") == true)
                "Данные изменились после предпросмотра. Выберите файл и проверьте импорт заново. База не изменена."
            else "Импорт не применён: конфликт данных. Проверьте резервную копию. База не изменена."
            return@run
        }
        preview.value = null
        message.value = "Импорт завершён. Добавлено объектов: ${selected.added}. Локальные конфликтующие записи сохранены."
        try { container.notificationScheduler.reschedule(container.repository.getUser()) }
        catch (_: Exception) { message.value = "Данные импортированы. Откройте «Расписание», чтобы проверить напоминания." }
    }
    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BackupViewModel(container) as T
        }
    }
}
