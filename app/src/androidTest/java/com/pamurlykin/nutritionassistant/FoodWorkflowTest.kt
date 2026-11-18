package com.pamurlykin.nutritionassistant

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamurlykin.nutritionassistant.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val repo get() = (compose.activity.application as NutritionAssistantApplication).container.repository
    private fun button(text: String) = compose.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    private fun menu() {
        compose.onNodeWithText("Меню").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("catalog-list").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun concreteProductEditorPreservesVariantCompositionAndFavoriteAfterRecreation() {
        val name = "UI хлеб " + System.currentTimeMillis()
        menu(); compose.onNodeWithText("Продукты").performClick()
        compose.onNodeWithText("Добавить запись").performClick()
        compose.onNodeWithText("Название").performTextInput(name)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Категория: Прочее").performScrollTo().performClick()
        compose.onNodeWithText("Мука и хлеб").performScrollTo().performClick()
        compose.onNodeWithText("Конкретный товар").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Марка (необязательно)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Конкретный товар").assertIsSelected()
        compose.onNodeWithText("Марка (необязательно)").performScrollTo().performTextInput("Марка UI")
        compose.onNodeWithText("Уточнение варианта").performScrollTo().performTextInput("С чесноком")
        compose.onNodeWithText("Уточнить состав продукта").performScrollTo().performClick()
        compose.onNodeWithText("Поиск продуктов").performScrollTo().performTextInput("Чеснок")
        compose.onNodeWithTag("product-options").performScrollTo()
        compose.onNode(hasText("Чеснок") and isToggleable()).performScrollTo().assertIsDisplayed().performClick().assertIsOn()
        compose.onNodeWithText("Сведения о составе и комментарий").performScrollTo().performClick()
        compose.onNode(hasText("Частичный состав") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performScrollTo().performClick()
        capture("product-variant-editor")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Название").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Название").assertTextContains(name)
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(15_000) { runBlocking { repo.observeCatalog().first().any { it.name == name } } }
        val product = runBlocking { repo.observeCatalog().first().single { it.name == name } }
        assertTrue("Selected garlic survives recreation", product.ingredients.contains("Чеснок"))
        assertEquals("bread", product.entry.categoryCode); assertEquals("Марка UI", product.entry.brand)
        assertEquals("С чесноком", product.entry.variantLabel); assertEquals("partial", product.entry.compositionState)
        compose.onNodeWithTag("catalog-list").performScrollToIndex(0)
        compose.onNodeWithText("Поиск по части названия").performTextInput(name)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Записей нет. Добавьте первую запись.").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("favorite-${product.id}"))
        compose.onNodeWithTag("favorite-${product.id}").performClick()
        compose.waitUntil(10_000) { runBlocking { repo.observeCatalog().first().single { it.id == product.id }.entry.favorite } }
        compose.onNodeWithTag("catalog-list").performScrollToIndex(0)
        compose.onNodeWithText("Избранное").performScrollTo().performClick()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasText(product.displayName))
        compose.onNodeWithText(product.displayName).assertExists()
        button("Состав и источники").performScrollTo().performClick()
        try { compose.onNodeWithText(product.displayName + " → Чеснок").assertExists() }
        catch (failure: AssertionError) { throw AssertionError(compose.onRoot().printToString(), failure) }
        compose.onNodeWithText("Неполный состав не подтверждает отсутствие ингредиентов или лактозы.").assertExists()
        capture("nested-product-composition")
        compose.onNodeWithText("Отмена").performClick()
    }

    @Test fun mixedMealCanBeSelectedAndRepeatedOnlyAfterReviewingHistoricalComposition() {
        val suffix = System.currentTimeMillis().toString()
        val pair = runBlocking {
            repo.initialize()
            val entries = repo.observeCatalog().first()
            val garlic = entries.single { it.name == "Чеснок" }; val potato = entries.single { it.name == "Картофель" }
            val mince = repo.foodCatalog.save(null, "product", "UI фарш $suffix", "", CatalogDetails("prepared", "variant", variant = "С чесноком", composition = "partial"), listOf(ComponentSelection(garlic.id)))
            val cutlet = repo.foodCatalog.save(null, "dish", "UI котлета $suffix", "home", CatalogDetails(composition = "partial"), listOf(ComponentSelection(mince.id)))
            val mash = repo.foodCatalog.save(null, "dish", "UI пюре $suffix", "home", CatalogDetails(composition = "partial"), listOf(ComponentSelection(potato.id)))
            cutlet to mash
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("month-calendar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Сегодня").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Добавить запись").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Добавить запись").performClick()
        compose.onNode(hasText("Перекус") and hasAnyAncestor(isDialog())).performClick()
        for (name in listOf(pair.first.name, pair.second.name)) {
            compose.onNodeWithText("Поиск по части названия").performScrollTo().performTextInput(name)
            button(name).performScrollTo().performClick()
        }
        compose.onNodeWithText("Продукты").performScrollTo().performClick()
        compose.onNodeWithText("Поиск по части названия").performScrollTo().performTextInput("Огурец")
        button("Огурец").performScrollTo().performClick()
        compose.onNodeWithText("Выбрано: 3").assertExists()
        compose.onAllNodesWithText("Порция и комментарий").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Количество (необязательно)").performScrollTo().performTextInput("150")
        compose.onNodeWithText("г").performScrollTo().performClick()
        compose.onNodeWithText("Комментарий позиции").performScrollTo().performTextInput("Проверенная порция")
        compose.onNodeWithText("Сохранить событие").performClick()
        val day = LocalDate.now().toString()
        compose.waitUntil(15_000) { runBlocking { repo.observeDailyStats(1, day).first().meals.any { event -> event.items.any { it.catalogId == pair.first.id } } } }
        val source = runBlocking { repo.observeDailyStats(1, day).first().meals.single { it.items.any { item -> item.catalogId == pair.first.id } } }
        assertEquals("150", source.items.first().quantity)
        assertEquals("Проверенная порция", source.items.first().comment)
        assertEquals(3, source.items.size); assertTrue(source.items.first().ingredients.contains("Чеснок"))
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Добавить запись").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Добавить запись").performClick()
        compose.onNodeWithText("Повторить недавний приём").performClick()
        compose.onNode(hasText(pair.first.name, substring = true) and hasAnyAncestor(isDialog()) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Повторить: Перекус").assertExists()
        runBlocking {
            val potato = repo.observeCatalog().first().single { it.name == "Картофель" }
            repo.saveCatalogEntry(pair.first.id, "dish", pair.first.name, "home", listOf(potato.id))
        }
        compose.onAllNodesWithText("Обновить состав").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Записанный состав").assertExists()
        compose.onNodeWithText("Текущий состав").assertExists()
        compose.onNodeWithText("Использовать текущий состав").performClick()
        compose.onNodeWithText("Выбрано: 3").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Повторить: Перекус").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Сохранить событие").performClick()
        compose.waitUntil(15_000) { runBlocking { repo.observeDailyStats(1, day).first().meals.count { event -> event.items.any { it.catalogId == pair.first.id } } == 2 } }
        val copies = runBlocking { repo.observeDailyStats(1, day).first().meals.filter { event -> event.items.any { it.catalogId == pair.first.id } } }
        assertNotEquals(copies[0].event.publicId, copies[1].event.publicId)
        assertNotEquals(copies[0].items.first().publicId, copies[1].items.first().publicId)
        assertTrue(copies[0].items.first().ingredients.contains("Чеснок"))
        assertEquals(listOf("Картофель"), copies[1].items.first().ingredients)
        assertEquals("150", copies[1].items.first().quantity)
        assertEquals("Проверенная порция", copies[1].items.first().comment)
    }
    private fun capture(name: String) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file = java.io.File(output, "qa/$name.png").apply { parentFile?.mkdirs() }
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

}
