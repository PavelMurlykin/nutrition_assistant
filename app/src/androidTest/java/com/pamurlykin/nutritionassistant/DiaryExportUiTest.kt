package com.pamurlykin.nutritionassistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.pamurlykin.nutritionassistant.data.export.*
import com.pamurlykin.nutritionassistant.ui.backup.DiaryExportDialogs
import com.pamurlykin.nutritionassistant.ui.backup.PreparedDiaryExport
import com.pamurlykin.nutritionassistant.ui.theme.NutritionAssistantTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DiaryExportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filePickerOpensOnlyFromPreparedPackageAndCancelAllowsRetry() {
        val registry = DocumentRegistry()
        var result: Uri? = null
        var callbacks = 0
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                NutritionAssistantTheme {
                    var prepared by remember { mutableStateOf<PreparedDiaryExport?>(null) }
                    DiaryExportDialogs(prepared, false, null,
                        onPrepare = { options, format ->
                            prepared = PreparedDiaryExport(options, mapOf(DiarySection.STOOL to 1), format, byteArrayOf(1))
                        },
                        onClear = { prepared = null },
                        onSave = { _, uri -> result = uri; callbacks++ },
                        onClose = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("diary-export-comments").performScrollTo().assertIsOff()
        compose.onNodeWithTag("diary-export-hide-names").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Markdown").performScrollTo().performClick()
        compose.onNodeWithTag("diary-export-prepare").performClick()
        compose.onNodeWithTag("diary-export-preview").assertExists()
        compose.onNodeWithText("Стул — 1").assertExists()
        compose.runOnIdle { assertNull(registry.suggestedName) }

        compose.onNodeWithTag("diary-export-save").performClick()
        compose.runOnIdle {
            assertTrue(registry.suggestedName.orEmpty().endsWith(".md"))
            assertEquals("text/markdown", registry.mimeType)
            registry.dispatchResult(registry.request, Activity.RESULT_CANCELED, null)
        }
        compose.onNodeWithTag("diary-export-save").assertIsEnabled().performClick()
        val document = Uri.parse("content://test/diary.md")
        compose.runOnIdle {
            registry.dispatchResult(registry.request, Activity.RESULT_OK, Intent().setData(document))
        }
        compose.runOnIdle { assertEquals(document, result); assertEquals(2, callbacks) }
    }

    private class DocumentRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        var request = 0
        var suggestedName: String? = null
        var mimeType: String? = null

        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            request = requestCode
            suggestedName = input as? String
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            mimeType = contract.createIntent(context, input).type
        }
    }
}
