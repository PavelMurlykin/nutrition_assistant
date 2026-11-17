package com.pamurlykin.nutritionassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val DateInputFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)

private fun dateDigits(input: String): String {
    val digits = input.filter { it in '0'..'9' }.take(8)
    return digits.take(2) + (if (digits.length > 2) "." + digits.substring(2, minOf(4, digits.length)) else "") +
        (if (digits.length > 4) "." + digits.substring(4) else "")
}

@Composable
fun AppDatePicker(initialDate: LocalDate, onDismiss: () -> Unit, onSelected: (LocalDate) -> Unit) {
    val original = initialDate.format(DateInputFormat)
    var value by rememberSaveable { mutableStateOf(original) }
    val date = runCatching { LocalDate.parse(value, DateInputFormat).takeIf { it.year in 1..9999 && it.format(DateInputFormat) == value } }.getOrNull()
    AppDialog(onDismissRequest = onDismiss, title = { Text("Выбрать дату") }, hasChanges = value != original,
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Введите дату в формате день.месяц.год.")
            AppTextField(value, { value = dateDigits(it) }, label = { Text("Дата (ДД.ММ.ГГГГ)") },
                modifier = Modifier.testTag("date-input"), isError = date == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text(if (date == null) "Укажите существующую дату, например 17.11.2026." else "Выбранная дата: $value") })
        } }, confirmButton = { TextButton(enabled = date != null, onClick = { date?.let(onSelected); onDismiss() }) { Text("Выбрать") } })
}

@Composable
fun AppTimePicker(initialTime: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initialTime) }
    val time = runCatching { LocalTime.parse(value).takeIf { value.matches(Regex("[0-9]{2}:[0-9]{2}")) } }.getOrNull()
    AppDialog(onDismissRequest = onDismiss, title = { Text("Выбрать время") }, hasChanges = value != initialTime,
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("24-часовой формат. Изменение применяется после подтверждения.")
            AppTextField(value, { value = it.filter { char -> char in '0'..'9' }.take(4).chunked(2).joinToString(":") }, label = { Text("Время (ЧЧ:ММ)") },
                modifier = Modifier.testTag("time-input"), isError = time == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text(if (time == null) "Введите время от 00:00 до 23:59." else "Выбранное время: $value") })
        } }, confirmButton = { TextButton(enabled = time != null, onClick = { onSelected(value); onDismiss() }) { Text("Выбрать") } })
}
