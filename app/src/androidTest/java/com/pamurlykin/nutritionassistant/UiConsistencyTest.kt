package com.pamurlykin.nutritionassistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking

class UiConsistencyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recipeDraftSurvivesRecreationAndCancelRequiresExplicitDiscard() {
        val name = "Черновик блюда ${System.currentTimeMillis()}"
        compose.onNodeWithText("Меню").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Добавить запись").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Добавить запись").performClick()
        compose.onNodeWithText("Название").performTextInput(name)
        compose.onNodeWithText("Поиск продуктов").performScrollTo().performTextInput("ВОД")
        compose.onNodeWithText("Вода").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Название").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Название").assertTextContains(name)
        compose.onNodeWithText("Поиск продуктов").assertTextContains("ВОД")
        compose.onNodeWithText("Состав блюда · выбрано продуктов: 1").assertExists()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Отменить изменения?").assertExists()
        compose.onAllNodesWithText("Отмена").onLast().performClick()
        compose.onNodeWithText("Название").assertTextContains(name)
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Отбросить").performClick()
        compose.onNodeWithText("Название").assertDoesNotExist()
    }

    @Test fun timeAndDateInputsValidateDigitsAndSurviveRecreation() {
        compose.onNodeWithText("Расписание").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Изменить время").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Изменить время").onFirst().performClick()
        compose.onNodeWithTag("time-input").performTextClearance()
        compose.onNodeWithTag("time-input").performTextInput("2560")
        compose.onNodeWithText("Выбрать").assertIsNotEnabled()
        compose.onNodeWithTag("time-input").performTextClearance()
        compose.onNodeWithTag("time-input").performTextInput("0935")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("time-input").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("time-input").assertTextContains("09:35")
        compose.onNodeWithText("Выбрать").performClick()
        val repository = (compose.activity.application as NutritionAssistantApplication).container.repository
        compose.waitUntil(10_000) { runBlocking { repository.getUser().breakfastTime == "09:35" } }
        compose.onNodeWithText("Календарь").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Выбрать дату").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Выбрать дату").performClick()
        compose.onNodeWithTag("date-input").performTextClearance()
        compose.onNodeWithTag("date-input").performTextInput("29022025")
        compose.onNodeWithText("Выбрать").assertIsNotEnabled()
        compose.onNodeWithTag("date-input").performTextClearance()
        compose.onNodeWithTag("date-input").performTextInput("29022024")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("date-input").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("date-input").assertTextContains("29.02.2024")
        compose.onNodeWithText("Выбрать").performClick()
        compose.onNodeWithTag("calendar-day-2024-02-29").assertIsSelected()
    }

    @Test fun unsavedMealSelectionsSurviveActivityRecreation() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("daily-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Добавить запись").performClick()
        compose.onNode(hasText("Перекус") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Продукты").performClick()
        compose.onNodeWithText("Поиск по части названия").performTextInput("Молоко")
        compose.onNode(hasText("Молоко") and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button)).performClick()
        compose.onNodeWithText("Выбрано: 1").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Выбрано: 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Молоко · Продукт").assertExists()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Отбросить").performClick()
        compose.onNodeWithText("Сохранить событие").assertDoesNotExist()
    }
}
