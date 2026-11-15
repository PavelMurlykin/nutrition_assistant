package com.pamurlykin.nutritionassistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

val NutritionTypography = Typography().let { type -> type.copy(
    headlineLarge = type.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = type.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = type.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = type.titleLarge.copy(fontWeight = FontWeight.SemiBold),
) }
