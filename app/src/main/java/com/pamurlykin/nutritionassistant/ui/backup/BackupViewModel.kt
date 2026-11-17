package com.pamurlykin.nutritionassistant.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pamurlykin.nutritionassistant.AppContainer
import com.pamurlykin.nutritionassistant.data.backup.*
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
                message.value = "Файл не прочитан: нужен корректный JSON Nutrition Assistant версии 1–4, UTF-8, до 16 МиБ и 100 000 объектов. База не изменена."
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
