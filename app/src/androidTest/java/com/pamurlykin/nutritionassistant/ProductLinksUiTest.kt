package com.pamurlykin.nutritionassistant

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.pamurlykin.nutritionassistant.data.local.ProductLinkEntity
import com.pamurlykin.nutritionassistant.ui.catalog.ProductLinksContent
import com.pamurlykin.nutritionassistant.ui.catalog.productBrowserIntent
import com.pamurlykin.nutritionassistant.ui.theme.NutritionAssistantTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProductLinksUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun draftAndListRequireValidAddressAndExplicitBrowserConfirmation() {
        var opened: Intent? = null
        compose.setContent {
            NutritionAssistantTheme {
                var links by remember { mutableStateOf(emptyList<ProductLinkEntity>()) }
                ProductLinksContent("Гречка", links, false, {},
                    onSave = { _, draft, done ->
                        links = listOf(ProductLinkEntity(1, 1, draft.url, draft.source, draft.checkedOn))
                        done()
                    },
                    onDelete = { id -> links = links.filterNot { it.id == id } },
                    onOpen = { url -> opened = productBrowserIntent(url) },
                )
            }
        }
        compose.onNodeWithTag("product-link-add").performClick()
        compose.onNodeWithTag("product-link-url").performTextInput("http://shop.example/food")
        compose.onNodeWithTag("product-link-save").assertIsNotEnabled()
        compose.onNodeWithTag("product-link-url").performTextReplacement("https://shop.example/food?item=42")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithTag("product-link-save").assertIsEnabled().performClick()
        compose.onNodeWithTag("product-link-open-1").performScrollTo().performClick()
        compose.onNodeWithText("Открыть внешний сайт?").assertIsDisplayed()
        compose.onNodeWithText("https://shop.example/food?item=42").assertExists()
        compose.runOnIdle { assertNull(opened) }
        compose.onNodeWithTag("product-link-open-confirm").performClick()
        compose.runOnIdle {
            val intent = requireNotNull(opened)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("https://shop.example/food?item=42", intent.dataString)
            assertTrue(requireNotNull(intent.selector).hasCategory(Intent.CATEGORY_APP_BROWSER))
            assertNull(intent.extras)
        }
    }
}
