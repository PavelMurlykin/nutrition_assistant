package com.pamurlykin.nutritionassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val NutritionColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = CardSurface,
    primaryContainer = AccentBlueSoft,
    onPrimaryContainer = Ink,
    secondary = SuccessGreen,
    background = ScreenBackground,
    onBackground = Ink,
    surface = CardSurface,
    onSurface = Ink,
    surfaceVariant = AccentBlueSoft,
    outline = Divider,
)

@Composable
fun NutritionAssistantTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NutritionColorScheme,
        typography = NutritionTypography,
        content = content,
    )
}
