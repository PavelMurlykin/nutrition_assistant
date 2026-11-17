package com.pamurlykin.nutritionassistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily

val NutritionTypography = Typography().let { type -> type.copy(
    displayLarge = type.displayLarge.copy(fontFamily = FontFamily.SansSerif),
    displayMedium = type.displayMedium.copy(fontFamily = FontFamily.SansSerif),
    displaySmall = type.displaySmall.copy(fontFamily = FontFamily.SansSerif),
    headlineLarge = type.headlineLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    headlineMedium = type.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    headlineSmall = type.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    titleLarge = type.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif),
    titleMedium = type.titleMedium.copy(fontFamily = FontFamily.SansSerif),
    titleSmall = type.titleSmall.copy(fontFamily = FontFamily.SansSerif),
    bodyLarge = type.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = type.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = type.bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = type.labelLarge.copy(fontFamily = FontFamily.SansSerif),
    labelMedium = type.labelMedium.copy(fontFamily = FontFamily.SansSerif),
    labelSmall = type.labelSmall.copy(fontFamily = FontFamily.SansSerif),
) }
