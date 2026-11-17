package com.pamurlykin.nutritionassistant

import androidx.compose.ui.test.*
import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamurlykin.nutritionassistant.util.StorageFormats
import com.pamurlykin.nutritionassistant.ui.NutritionAssistantApp
import com.pamurlykin.nutritionassistant.ui.theme.NutritionAssistantTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun monthSwipeSelectionRecreationTodayAndNavigation() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("month-calendar").fetchSemanticsNodes().isNotEmpty() }
        capture("calendar-light")
        val today = LocalDate.now()
        compose.onNodeWithTag("month-calendar").performTouchInput { swipeLeft() }
        val next = YearMonth.from(today).plusMonths(1)
        val title = next.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru"))
            .replaceFirstChar { it.uppercase() } + " " + next.year
        compose.onNodeWithTag("calendar-month").assertTextEquals(title)
        compose.onNodeWithTag("calendar-day-${next.atDay(1)}").assertIsSelected()
        val selected = next.atDay(12)
        compose.onNodeWithTag("calendar-day-$selected").performClick().assertIsSelected()
        compose.onNodeWithTag("daily-list").performScrollToNode(hasText(StorageFormats.formatDateForDisplay(selected)))
        compose.onNodeWithText(StorageFormats.formatDateForDisplay(selected)).assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("calendar-day-$selected").assertIsSelected()
        compose.onNodeWithText("Расписание").performClick()
        compose.onNodeWithText("Время ежедневных напоминаний").assertExists()
        capture("schedule-light")
        compose.onNodeWithText("Меню").performClick()
        compose.onNodeWithText("Справочники блюд, продуктов и лекарств").assertExists()
        capture("menu-light")
        compose.onNodeWithText("Календарь").performClick()
        compose.onNodeWithTag("calendar-day-$selected").assertIsSelected()
        compose.onNodeWithText("Сегодня").performClick()
        compose.onNodeWithTag("calendar-day-$today").assertIsSelected()
    }

    @Test fun toiletEntryAppearsInCalendarAndCanBeRemoved() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("month-calendar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Сегодня").performClick()
        val today = LocalDate.now()
        val tag = "calendar-day-$today"
        val original = compose.onNodeWithTag(tag).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        compose.onNodeWithContentDescription("Добавить запись").performClick()
        compose.onNode(hasText("Туалет") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("4 · Гладкий, мягкий, оформленный").performScrollTo().performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) {
            compose.onNodeWithTag(tag).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single() != original
        }
        capture("calendar-with-event")
        compose.onNodeWithTag("daily-list").performScrollToNode(hasTestTag("stool-card"))
        compose.onAllNodes(hasText("Удалить") and hasAnyAncestor(hasTestTag("stool-card")) and hasClickAction()).onLast().performScrollTo().performClick()
        compose.onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000) {
            compose.onNodeWithTag(tag).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single() == original
        }
    }

    @Test fun calendarCollapsesWhenDiaryScrollsAndExpandsAtTop() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("month-calendar").fetchSemanticsNodes().isNotEmpty() }
        val initial = compose.onNodeWithTag("month-calendar").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("month-calendar").performTouchInput { swipeUp() }
        val collapsed = compose.onNodeWithTag("month-calendar").fetchSemanticsNode().boundsInRoot.height
        org.junit.Assert.assertTrue(collapsed < initial)
        capture("calendar-collapsed")
        compose.onNodeWithTag("daily-list").performScrollToIndex(0)
        compose.onNodeWithTag("month-calendar").performTouchInput { swipeDown() }
        org.junit.Assert.assertTrue(compose.onNodeWithTag("month-calendar").fetchSemanticsNode().boundsInRoot.height > collapsed)
    }

    @Test fun darkThemeAndLargeTextKeepDiaryReachable() {
        compose.activityRule.scenario.onActivity { activity ->
            val density = activity.resources.displayMetrics.density
            val container = (activity.application as NutritionAssistantApplication).container
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                    NutritionAssistantTheme(darkTheme = true) { NutritionAssistantApp(container) }
                }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("month-calendar").fetchSemanticsNodes().isNotEmpty() }
        capture("calendar-dark-large-text")
        compose.onNodeWithTag("daily-list").performScrollToNode(hasTestTag("meal-snack"))
        compose.onNode(hasText("Добавить") and hasAnyAncestor(hasTestTag("meal-snack"))).assertIsDisplayed()
    }

    @Test fun landscapeCanScrollFromCalendarToDiary() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(15_000) {
            val bounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
            bounds.width > bounds.height
        }
        compose.onNodeWithTag("daily-list").performScrollToIndex(0)
        capture("calendar-landscape")
        compose.onNodeWithTag("daily-list").performScrollToNode(hasTestTag("meal-snack"))
        compose.onNode(hasText("Добавить") and hasAnyAncestor(hasTestTag("meal-snack"))).assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (output != null) File(output, "qa") else File(context.getExternalFilesDir(null), "qa")
        directory.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
