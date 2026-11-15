package com.pamurlykin.nutritionassistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createMissingDishSelectSecondBySubstringAndKeepMealAfterRecreation() {
        val name = "Автотест суп " + System.currentTimeMillis()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("daily-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("daily-list").performScrollToNode(hasTestTag("meal-snack"))
        compose.onNode(hasText("Добавить") and hasAnyAncestor(hasTestTag("meal-snack"))).performClick()
        compose.onNodeWithText("Поиск по части названия").performTextInput(name)
        compose.onNodeWithText("Добавить «" + name + "» в справочник и выбрать").performScrollTo().performClick()
        compose.onNodeWithText("Ингредиенты, по одному на строку").performTextInput("Картофель\nВода")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Поиск по части названия").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Поиск по части названия").performScrollTo().performTextInput("ГРЕЧ")
        compose.onNodeWithText("Гречка с курицей").performScrollTo().performClick()
        compose.onNodeWithText("Сохранить событие").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name + " · Домашняя").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Гречка с курицей · Домашняя").onFirst().assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("daily-list").performScrollToNode(hasText(name + " · Домашняя"))
        compose.onNodeWithText(name + " · Домашняя").assertExists()
        compose.onAllNodesWithText("Гречка с курицей · Домашняя").onFirst().assertExists()
    }
}
