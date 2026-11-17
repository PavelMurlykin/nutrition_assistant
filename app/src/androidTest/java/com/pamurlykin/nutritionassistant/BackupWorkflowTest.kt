package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pamurlykin.nutritionassistant.data.backup.*
import com.pamurlykin.nutritionassistant.data.local.NutritionDatabase
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import com.pamurlykin.nutritionassistant.ui.backup.ImportPreviewDialog
import com.pamurlykin.nutritionassistant.ui.theme.NutritionAssistantTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class BackupWorkflowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var source: NutritionDatabase
    private lateinit var target: NutritionDatabase
    private lateinit var store: BackupStore
    private lateinit var preview: ImportPreview
    private lateinit var before: String

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = Room.inMemoryDatabaseBuilder(context, NutritionDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, NutritionDatabase::class.java).build()
        NutritionRepository(source).initialize(); NutritionRepository(target).initialize()
        NutritionRepository(source).saveCatalogEntry(null, "product", "Совпадение импорта", "", emptyList())
        NutritionRepository(target).saveCatalogEntry(null, "product", "Совпадение импорта", "", emptyList())
        NutritionRepository(source).addFeeling(1, "2026-11-17", "Комментарий для переноса")
        store = BackupStore(target, "test")
        before = BackupCodec.fingerprint(store.snapshot())
        preview = store.preview(BackupCodec.decode(BackupStore(source, "test").export()))
    }
    @After fun close() { source.close(); target.close() }

    @Test fun previewRequiresExplicitCatalogMatchBeforeApply() {
        var applied: ImportOptions? = null
        compose.setContent { NutritionAssistantTheme { ImportPreviewDialog(preview, false, {}, { applied = it }) } }
        compose.onNodeWithTag("apply-import").assertIsNotEnabled()
        compose.onNodeWithTag("import-preview").performScrollToNode(hasText("Сопоставить одноимённые позиции с локальным справочником"))
        compose.onNodeWithText("Сопоставить одноимённые позиции с локальным справочником").performClick()
        compose.onNodeWithTag("apply-import").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertTrue(requireNotNull(applied).matchCatalog)
            assertFalse(requireNotNull(applied).restoreSettings)
        }
    }

    @Test fun cancelClosesPreviewWithoutChangingDatabase() {
        var dismissed = false
        compose.setContent { NutritionAssistantTheme { ImportPreviewDialog(preview, false, { dismissed = true }, {}) } }
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
        runBlocking { assertEquals(before, BackupCodec.fingerprint(store.snapshot())) }
    }
}
