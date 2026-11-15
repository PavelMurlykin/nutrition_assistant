package com.pamurlykin.nutritionassistant.ui.daily

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.ui.components.*

@Composable
fun StoolAnalysisCard(analysis: StoolAnalysis) {
    NutritionCard {
        CardHeadline("Аналитика стула")
        Text("За день: ${analysis.stoolCount}; оформленный: ${analysis.normalCount}; мягкий: ${analysis.softCount}; признаки запора: ${analysis.constipationCount}; жидкий: ${analysis.diarrheaCount}; не оценен: ${analysis.unknownCount}.")
        if (analysis.conditions.isEmpty()) {
            Text("При записи типов 1–2 или 6–7 здесь появится анализ еды за предыдущий день.")
        } else {
            Text("Еда за ${analysis.previousDate}", style = MaterialTheme.typography.titleMedium)
            if (analysis.previousMeals.isEmpty()) Text("Нет записей о еде за предыдущий день. Для анализа добавьте их в дневник.")
            analysis.previousMeals.forEach { meal ->
                val title = MealType.entries.find { it.storageValue == meal.event.mealType }?.title ?: "Прием пищи"
                Text(title + ": " + meal.items.joinToString("; ") { item ->
                    item.name + " (" + foodItemTypeTitle(item.kind, item.foodType) + ")" +
                        if (item.kind == CatalogKind.PRODUCT && item.ingredients.isEmpty()) ""
                        else if (item.ingredients.isEmpty()) " — состав не указан" else " — " + item.ingredients.joinToString(", ")
                })
            }
            analysis.conditions.forEach { condition ->
                HorizontalDivider()
                Text(condition.condition.title, style = MaterialTheme.typography.titleMedium)
                if (condition.factors.none { it.repeated }) Text("Повторяющихся связей пока не выявлено: данных мало либо частота не выше, чем без продукта.")
                condition.factors.take(12).forEach { factor ->
                    Text(factor.factor + if (factor.repeated) " · повторяющаяся связь" else " · наблюдение")
                    Text("Фактор записан накануне: ${factor.affectedWith}/${factor.daysWith} дней с этим типом стула. Не записан: ${factor.affectedWithout}/${factor.daysWithout}.", style = MaterialTheme.typography.bodySmall)
                }
                if (condition.factors.size > 12) Text("Показаны 12 факторов с наибольшей разницей частот.")
            }
            Text("Сравниваются только дни до выбранной даты включительно с записями стула и еды накануне. Повторяющаяся связь: минимум два дня со сбоем после фактора, минимум два дня без фактора и более высокая частота с ним.", style = MaterialTheme.typography.bodySmall)
            Text("Результат зависит от полноты дневника. Совпадение не доказывает причину и не является диагнозом. На стул могут влиять лекарства, инфекции и другие факторы; приложение не оценивает их влияние.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
