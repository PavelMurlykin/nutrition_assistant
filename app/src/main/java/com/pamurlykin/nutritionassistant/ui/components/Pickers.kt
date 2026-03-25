package com.pamurlykin.nutritionassistant.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import com.pamurlykin.nutritionassistant.util.StorageFormats
import java.time.LocalDate
import java.time.LocalTime

fun showTimePicker(
    context: Context,
    initialTime: String,
    onSelected: (String) -> Unit,
) {
    val parsedTime = StorageFormats.parseTime(initialTime)
    TimePickerDialog(
        context,
        { _, hour, minute ->
            onSelected(StorageFormats.formatTimeForStorage(LocalTime.of(hour, minute)))
        },
        parsedTime.hour,
        parsedTime.minute,
        true,
    ).show()
}

fun showDatePicker(
    context: Context,
    initialDate: LocalDate,
    onSelected: (LocalDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onSelected(LocalDate.of(year, month + 1, dayOfMonth))
        },
        initialDate.year,
        initialDate.monthValue - 1,
        initialDate.dayOfMonth,
    ).show()
}
