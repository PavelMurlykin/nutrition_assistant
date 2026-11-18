package com.pamurlykin.nutritionassistant

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createMissingDishSelectSecondBySubstringAndKeepMealAfterRecreation() {
        val name = "Автотест суп " + System.currentTimeMillis()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("daily-list").fetchSemanticsNodes().any { it.config[androidx.compose.ui.semantics.SemanticsProperties.CollectionInfo].rowCount > 2 } }
        compose.onNodeWithTag("daily-list").performScrollToNode(hasTestTag("meal-snack"))
        compose.onNode(hasText("Добавить") and hasAnyAncestor(hasTestTag("meal-snack"))).performClick()
        compose.onNodeWithText("Поиск по части названия").performTextInput(name)
        compose.onNodeWithText("Добавить «" + name + "» в справочник и выбрать").performScrollTo().performClick()
        compose.onNodeWithText("Поиск продуктов").performScrollTo().performTextInput("ВОД")
        compose.onNodeWithText("Вода").performClick()
        compose.onNodeWithText("Молоко").assertDoesNotExist()
        compose.onNodeWithText("Поиск продуктов").performScrollTo().performTextReplacement("МОЛ")
        compose.onNodeWithText("Молоко").performClick()
        compose.onNodeWithText("Состав блюда · выбрано продуктов: 2").assertExists()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Поиск по части названия").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Поиск по части названия").performScrollTo().performTextInput("ГРЕЧ")
        compose.onNodeWithText("Гречка с курицей").performScrollTo().performClick()
        compose.onNodeWithText("Сохранить событие").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name + " · Домашняя").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Гречка с курицей · Домашняя").onFirst().assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("daily-list").fetchSemanticsNodes().any { it.config[androidx.compose.ui.semantics.SemanticsProperties.CollectionInfo].rowCount > 2 } }
        compose.onNodeWithTag("daily-list").performScrollToNode(hasText(name + " · Домашняя"))
        compose.onNodeWithText(name + " · Домашняя").assertExists()
        compose.onAllNodesWithText("Гречка с курицей · Домашняя").onFirst().assertExists()
    }

    @Test fun productCatalogAndCheckboxRecipeSupportPartialSearchAndEditing() {
        val suffix = System.currentTimeMillis().toString()
        val productName = "Автотест продукт $suffix"
        val dishName = "Автотест блюдо $suffix"
        compose.onNodeWithText("Меню").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Справочники блюд, продуктов и лекарств").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Магазинная").assertDoesNotExist()
        compose.onNodeWithText("Продукты").performClick()
        compose.onNodeWithText("Домашняя").assertDoesNotExist()
        compose.onNodeWithText("Добавить запись").performClick()
        compose.onNodeWithText("Название").performTextInput(productName)
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Название").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("catalog-list").performScrollToIndex(0)
        compose.onNodeWithText("Поиск по части названия").performTextInput(suffix)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Записей нет. Добавьте первую запись.").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasText(productName))
        compose.onNodeWithText(productName).assertExists()
        compose.onNodeWithText("Молоко").assertDoesNotExist()
        compose.onNodeWithTag("catalog-list").performScrollToIndex(0)
        compose.onNodeWithText("Блюда").performClick()
        compose.onNodeWithText("Добавить запись").performClick()
        compose.onNodeWithText("Название").performTextInput(dishName)
        compose.onNodeWithText("Сохранить").assertIsNotEnabled()
        compose.onNodeWithText("Поиск продуктов").performScrollTo().performTextInput(suffix)
        compose.onNodeWithText(productName).performClick()
        compose.onNodeWithText("Поиск продуктов").performScrollTo().performTextReplacement("ВОД")
        compose.onNodeWithText("Вода").performClick()
        compose.onNodeWithText("Состав блюда · выбрано продуктов: 2").assertExists()
        capture("dish-products-editor")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Название").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("catalog-list").performScrollToIndex(0)
        compose.onNodeWithText("Поиск по части названия").performTextInput(suffix)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Записей нет. Добавьте первую запись.").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasText(dishName))
        compose.onNodeWithText("$productName, Вода").assertExists()
        compose.onNodeWithText("Изменить").performScrollTo().performClick()
        compose.onNodeWithText("Поиск продуктов").performScrollTo().performTextInput("ВОД")
        compose.onNode(hasText("Вода") and isToggleable()).assertIsOn().performClick()
        compose.onNodeWithText("Поиск продуктов").performTextClearance()
        compose.onNodeWithText("Поиск продуктов").performTextInput(suffix)
        compose.onNode(hasText(productName) and isToggleable()).assertIsOn()
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasText(dishName))
        compose.onNodeWithText(productName).assertExists()
        compose.onNodeWithText("$productName, Вода").assertDoesNotExist()
        compose.onNodeWithTag("catalog-list").performScrollToIndex(0)
        capture("menu-three-catalogs")
    }

    @Test fun missingProductCanBeCreatedInsideRecipeWithoutLosingSelection() {
        val suffix = System.currentTimeMillis().toString()
        val productName = "Новый продукт $suffix"
        compose.onNodeWithText("Меню").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Добавить запись").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Добавить запись").performClick()
        compose.onNodeWithText("Название").performTextInput("Блюдо с новым продуктом $suffix")
        compose.onNodeWithText("Поиск продуктов").performScrollTo().performTextInput("ВОД")
        compose.onNodeWithText("Вода").performClick()
        compose.onNodeWithText("Поиск продуктов").performTextClearance()
        compose.onNodeWithText("Поиск продуктов").performTextInput(productName)
        compose.onNodeWithText("Добавить продукт «$productName» и выбрать").performScrollTo().performClick()
        compose.onNodeWithText("Название").assertTextContains(productName)
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Состав блюда · выбрано продуктов: 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Название").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("catalog-list").performScrollToIndex(0)
        compose.onNodeWithText("Продукты").performClick()
        compose.onNodeWithText("Поиск по части названия").performTextInput(suffix)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Записей нет. Добавьте первую запись.").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasText(productName))
        compose.onNodeWithText(productName).assertExists()
    }

    private fun capture(name: String) {
        val output = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file = java.io.File(output, "qa/$name.png").apply { parentFile?.mkdirs() }
        val target = if (compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) compose.onNode(isDialog()) else compose.onRoot()
        val bitmap = target.captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
