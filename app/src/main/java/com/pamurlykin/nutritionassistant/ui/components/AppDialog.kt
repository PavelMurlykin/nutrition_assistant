package com.pamurlykin.nutritionassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

/** Spacing and surfaces shared by screens, short prompts, and long editors. */
object AppLayout {
    val screenPadding = 16.dp
    val gap = 12.dp
    val fieldShape @Composable get() = MaterialTheme.shapes.medium
}

private val LocalDismissDialog = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun AppDismissButton(enabled: Boolean = true) {
    val dismiss = LocalDismissDialog.current
    TextButton(enabled = enabled, onClick = dismiss) { Text("Отмена") }
}

/** Both the platform back action and Cancel share the same unsaved-changes guard. */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = { AppDismissButton() },
    hasChanges: Boolean = false,
    busy: Boolean = false,
) {
    var discard by rememberSaveable { mutableStateOf(false) }
    val dismiss = { if (!busy) { if (hasChanges) discard = true else onDismissRequest() } }
    val parentDensity = LocalDensity.current
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides parentDensity) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp), contentAlignment = Alignment.Center) {
            val compact = maxHeight < 320.dp || (LocalDensity.current.fontScale > 1.6f && maxHeight < 500.dp)
            val bodyLimit = maxHeight * 0.58f
            Box(Modifier.matchParentSize().testTag("dialog-scrim").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss))
            Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(max = maxHeight).testTag("app-dialog")
                .pointerInput(Unit) { detectTapGestures { } },
                shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 6.dp) {
                CompositionLocalProvider(LocalDismissDialog provides dismiss) {
                    Column(Modifier.then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(if (compact) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                        Box(Modifier.fillMaxWidth().then(if (compact) Modifier.heightIn(max = bodyLimit) else Modifier.weight(1f, fill = false))
                            .testTag("app-dialog-body")) {
                            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() }
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            dismissButton?.invoke()
                            confirmButton()
                        }
                    }
                }
            }
        }
        }
    }
    if (discard) ConfirmActionDialog("Отменить изменения?", "Введённые изменения этой формы не сохранятся.", "Отбросить",
        onDismiss = { discard = false }, onConfirm = { discard = false; onDismissRequest() })
}

@Composable
fun AppRadioChoice(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ConfirmActionDialog(title: String, description: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AppDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Text(description, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(confirmLabel) } })
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true,
    supportingText: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(value, onValueChange, modifier = modifier.fillMaxWidth(), label = label,
        enabled = enabled, isError = isError, singleLine = singleLine,
        minLines = if (singleLine) 1 else 3, shape = AppLayout.fieldShape,
        textStyle = MaterialTheme.typography.bodyLarge, keyboardOptions = keyboardOptions,
        supportingText = supportingText ?: if (isError) ({ Text("Проверьте значение поля.") }) else null)
}
