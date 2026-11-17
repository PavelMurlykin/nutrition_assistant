package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import com.pamurlykin.nutritionassistant.ui.daily.*
import com.pamurlykin.nutritionassistant.ui.theme.NutritionAssistantTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class HealthWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:NutritionDatabase
    private lateinit var repo:NutritionRepository
    private val day="2026-11-17"
    @Before fun setup()=runBlocking { db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),NutritionDatabase::class.java).build();repo=NutritionRepository(db);repo.initialize() }
    @After fun close() { db.close() }
    private fun visible(node:SemanticsNodeInteraction):SemanticsNodeInteraction {
        try { node.assertIsDisplayed() } catch (_:AssertionError) { node.performScrollTo().assertIsDisplayed() }
        return node
    }
    private fun pick(metric:String,code:String) {
        compose.onNodeWithTag("pick-$metric").performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("value-$metric-$code"))).performScrollTo().performClick()
    }
    private fun capture(name:String) {
        val dir=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file=java.io.File(dir,"qa/$name.png").apply { parentFile?.mkdirs() }
        val bitmap=compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun quickDayNeedsNoTextAndRestoresAllDraftChoices() {
        val initial=runBlocking { repo.observeDailyStats(1,day).first() };val restoration=StateRestorationTester(compose)
        restoration.setContent { NutritionAssistantTheme { var open by rememberSaveable { mutableStateOf(true) };if(open) HealthDayDialog(initial,false,{}, { values,sleep,comment->runBlocking { repo.health.saveDay(1,day,values,sleep,comment) };open=false }) } }
        pick("sleep","4");pick("stress","0");pick("activity","none");pick("wellbeing","5");pick("illness","unknown")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("pick-illness").performScrollTo().assertTextContains("Не знаю",substring=true)
        capture("health-day-light")
        visible(compose.onNodeWithText("Сохранить день")).performClick()
        val stored=runBlocking { repo.observeDailyStats(1,day).first() }
        assertEquals(4,stored.sleep?.qualityRating);assertNull(stored.sleep?.bedTime)
        assertEquals(mapOf("stress" to "0","activity" to "none","wellbeing" to "5","illness" to "unknown"),stored.healthEntries.associate { it.metric to it.valueCode })
        assertTrue(stored.healthEntries.all { it.comment==null && it.scaleVersion==1 })
    }
    @Test fun darkLargeTextHealthFormKeepsChoicesAndSaveReachable() {
        val initial=runBlocking { repo.observeDailyStats(1,day).first() };var saved=false
        compose.setContent { val density=LocalDensity.current.density;CompositionLocalProvider(LocalDensity provides Density(density,2f)) {
            NutritionAssistantTheme(darkTheme=true) { HealthDayDialog(initial,false,{}, { _,_,_->saved=true }) }
        } }
        pick("stress","4");pick("illness","present")
        compose.onNodeWithTag("pick-illness").performScrollTo().assertIsDisplayed();capture("health-day-dark-font200")
        visible(compose.onNodeWithText("Сохранить день")).assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(saved) }
    }
    @Test fun stoolSupportsOptionalScoresCommentAndExplicitActualTime() {
        compose.setContent { NutritionAssistantTheme { var open by remember { mutableStateOf(true) };if(open) StructuredStoolDialog(day,null,false,{}, { quality,context,details->runBlocking { repo.addStool(1,day,quality,context,details) };open=false }) } }
        compose.onNodeWithText(StoolCondition.label(7)).performScrollTo().performClick();pick("urgency","0");pick("pain","2")
        compose.onNodeWithText("Указать фактическое время").performScrollTo().performClick()
        compose.onNodeWithTag("time-input").performTextClearance();compose.onNodeWithTag("time-input").performTextInput("0935");compose.onNodeWithText("Выбрать").performClick()
        compose.onNodeWithTag("event-utc-offset").performScrollTo().performTextReplacement("+03:00")
        compose.onNodeWithTag("event-time-zone").performScrollTo().performTextReplacement("Europe/Moscow")
        compose.onNodeWithText("Комментарий (необязательно)").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Комментарий")).performScrollTo().performTextInput("Наблюдение")
        capture("stool-actual-time");visible(compose.onNodeWithText("Сохранить")).performClick()
        val stool=runBlocking { repo.observeDailyStats(1,day).first().stools.single() }
        assertEquals(7,stool.quality);assertEquals(0,stool.urgency);assertEquals(2,stool.pain);assertEquals("Наблюдение",stool.comment);assertEquals("09:35",stool.actualTime);assertEquals("+03:00",stool.utcOffset)
    }
    @Test fun clearingActivityRemovesHiddenMinutesAndKeepsMomentCommentAfterRestoration() {
        var result:HealthDraft?=null
        val restoration=StateRestorationTester(compose)
        restoration.setContent { NutritionAssistantTheme { HealthObservationDialog(day,"activity",null,false,{}, { result=it }) } }
        pick("activity","moderate")
        compose.onNodeWithText("Минуты и вид нагрузки (необязательно)").performScrollTo().performClick()
        compose.onNodeWithText("Продолжительность, минут").performScrollTo().performTextInput("25")
        compose.onNodeWithText("Ходьба").performScrollTo().performClick()
        compose.onNodeWithText("Комментарий (необязательно)").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Комментарий")).performScrollTo().performTextInput("Наблюдение момента")
        restoration.emulateSavedInstanceStateRestore()
        if(compose.onAllNodesWithText("Продолжительность, минут").fetchSemanticsNodes().isEmpty()) compose.onNodeWithText("Минуты и вид нагрузки (необязательно)").performScrollTo().performClick()
        compose.onNodeWithText("Продолжительность, минут").performScrollTo().assertTextContains("25")
        pick("activity","none")
        visible(compose.onNodeWithText("Сохранить")).performClick()
        compose.runOnIdle { assertEquals("moment",result?.period);assertEquals("none",result?.value);assertNull(result?.durationMinutes);assertNull(result?.activityKind);assertEquals("Наблюдение момента",result?.context?.comment) }
    }
    @Test fun qualityOnlySleepAndCompletenessHaveNoAutomaticNormalAnswer() {
        var phase by mutableIntStateOf(0);var confirmed=false
        compose.setContent { NutritionAssistantTheme {
            if(phase==0) StructuredSleepDialog(day,null,false,{}, { draft->runBlocking { repo.health.saveSleep(1,day,draft) };phase=1 })
            else CompletenessDialog(null,false,{}, { _,symptoms,summary->confirmed=symptoms && summary=="no_stool" })
        } }
        pick("sleep","3");compose.onNodeWithText("Сохранить").performClick()
        val sleep=runBlocking { repo.observeDailyStats(1,day).first().sleep };assertEquals(3,sleep?.qualityRating);assertNull(sleep?.bedTime);assertNull(sleep?.bedDate)
        compose.onNodeWithTag("complete-symptoms").performClick();compose.onNodeWithText("Подтвердить").assertIsNotEnabled()
        compose.onNodeWithText("Стула не было").performScrollTo().performClick();compose.onNodeWithText("Подтвердить").performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
}
