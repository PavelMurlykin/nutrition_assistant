package com.pamurlykin.nutritionassistant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.nutritionassistant.ui.components.*
import com.pamurlykin.nutritionassistant.ui.theme.NutritionAssistantTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class DialogLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightDialogWithKeyboardKeepsActionsReachable() = verify(dark = false, fontScale = 1f)
    @Test fun darkDialogWithDoubleSizeTextAndKeyboardKeepsActionsReachable() = verify(dark = true, fontScale = 2f)

    private fun verify(dark: Boolean, fontScale: Float) {
        var saved = false
        var screenDensity = 1f
        compose.setContent {
            val density = LocalDensity.current.density
            SideEffect { screenDensity = density }
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                NutritionAssistantTheme(darkTheme = dark) {
                    var name by rememberSaveable { mutableStateOf("") }
                    AppDialog(onDismissRequest = {}, title = { Text("Редактор записи") },
                        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Название, комментарии и действия используют общую тему приложения.")
                            AppTextField(name, { name = it }, label = { Text("Название") })
                        } }, confirmButton = { TextButton(onClick = { saved = true }) { Text("Сохранить") } })
                }
            }
        }
        compose.onNodeWithText("Название").performTextInput("Проверка формы")
        if (fontScale > 1.5f) {
            assertTrue("The dialog must inherit the actual enlarged text scale", compose.onNodeWithText("Редактор записи").fetchSemanticsNode().boundsInRoot.height > screenDensity * 40f)
        }
        try { compose.onNodeWithText("Сохранить").assertIsDisplayed() }
        catch (_: AssertionError) { compose.onNodeWithText("Сохранить").performScrollTo().assertIsDisplayed() }
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if (output != null) {
            val file = java.io.File(output, "qa/dialog-${if (dark) "dark" else "light"}-font${(fontScale * 100).toInt()}.png").apply { parentFile?.mkdirs() }
            val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertTrue(saved) }
    }
}
