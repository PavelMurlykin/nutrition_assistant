package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import com.pamurlykin.nutritionassistant.ui.daily.*
import com.pamurlykin.nutritionassistant.ui.theme.NutritionAssistantTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*

class AnalyticsUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var db:NutritionDatabase
    private lateinit var repo:NutritionRepository
    private lateinit var garlic:CatalogItem
    private var navigated:String?=null
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),NutritionDatabase::class.java).build();repo=NutritionRepository(db);repo.initialize()
        val known=CatalogDetails("vegetables",composition="confirmed",source="Тестовая этикетка",checkedOn="2026-11-18")
        garlic=repo.foodCatalog.save(null,"product","Чеснок аналитики","",known,emptyList())
        val mince=repo.foodCatalog.save(null,"product","Фарш аналитики","",known.copy(categoryCode="prepared"),listOf(ComponentSelection(garlic.id,"2","g")))
        val rice=repo.foodCatalog.save(null,"product","Рис аналитики","",known.copy(categoryCode="grains"),emptyList())
        val start=LocalDate.of(2026,11,10)
        (0..5).forEach {i->val date=start.plusDays(i.toLong()).toString();val previous=start.plusDays(i.toLong()).minusDays(1).toString()
            repo.saveMeal(previous,MealType.DINNER,null,listOf(if(i<3) mince.id else rice.id),EventContext(RecordedTime("19:00","+03:00"),"Комментарий исходной еды"))
            repo.addStool(1,date,if(i<3) 7 else 4,EventContext(RecordedTime("09:00","+03:00")))
        }
        (-1..5).forEach {i->val date=start.plusDays(i.toLong()).toString();repo.confirmDay(1,date,true,true,if(i<0) "no_stool" else if(i<3) "diarrhea" else "no_diarrhea")}
        repo.saveMeal("2026-11-18",MealType.BREAKFAST,null,listOf(garlic.id),EventContext(RecordedTime("09:00","+03:00"),"Перед выбранным эпизодом"))
        repo.addStool(1,"2026-11-18",7,EventContext(RecordedTime("12:00","+03:00")))
        val late=repo.saveCatalogEntry(null,"medicine","Позднее лекарство аналитики","",emptyList())
        repo.saveMedicines("2026-11-18",null,listOf(MedicineSelection(late.id,"1 таблетка")),EventContext(RecordedTime("14:00","+03:00")))
    }
    @After fun close() {db.close()}
    @Composable private fun Screen(dark:Boolean=false,scale:Float=1f) {
        val density=LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density,scale)) { NutritionAssistantTheme(darkTheme=dark) {Surface(Modifier.fillMaxSize()) {
            var date by rememberSaveable {mutableStateOf("2026-11-18")};var days by rememberSaveable {mutableIntStateOf(30)}
            val h by remember {repo.observeAnalyticsHistory()}.collectAsState(initial=null)
            val entries by remember {repo.observeCatalogGraph()}.collectAsState(initial=emptyList())
            val report by produceState<ExplainableReport?>(null,h,date,days) {value=h?.let {history->withContext(Dispatchers.Default) {ExplainableAnalyzer.analyze(history,date,days)}}}
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {ExplainableAnalysisCard(date,h,report?.takeIf {it.selectedDate==date && it.requestedDays==days},days,entries,false,{days=it},{date=it;navigated=it},
                {id,catalogId,scope,source,comment,active,success->runBlocking {repo.analytics.saveTarget(1,id,catalogId,scope,source,comment,active)};success()},
                {id,active->runBlocking {repo.analytics.setActive(1,id,active)}})}
        } } }
    }
    private fun ready()=compose.waitUntil(15_000) {compose.onAllNodesWithTag("analysis-coverage").fetchSemanticsNodes().isNotEmpty()}
    private fun visible(node:SemanticsNodeInteraction):SemanticsNodeInteraction {try {node.assertIsDisplayed()} catch(_:AssertionError) {node.performScrollTo().assertIsDisplayed()};return node}
    private fun capture(name:String) {val dir=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file=java.io.File(dir,"qa/$name.png").apply {parentFile?.mkdirs()};val bitmap=compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)} }
    @Test fun observationShowsDenominatorsCoFactorsCommentsAndSourceNavigation() {
        compose.setContent {Screen()};ready();compose.onNodeWithTag("analysis-factors").performScrollTo().performClick()
        compose.onNodeWithText("Поиск фактора").performTextInput("Чеснок аналитики")
        val tag="factor-ingredient:${garlic.entry.publicId}:r1"
        compose.onNodeWithTag("analysis-factor-list").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithText("С фактором: из 3 пригодных дней в 3 был жидкий стул. Без фактора: из 3 в 0. Исключено по полноте/неизвестности: 24.").assertExists()
        capture("analysis-observation-light")
        compose.onNodeWithTag("analysis-observation-days").performScrollToNode(hasText("2026-11-10 · записан тип 6–7"))
        compose.onNodeWithText("2026-11-10 · записан тип 6–7").performClick()
        compose.onNodeWithTag("analysis-sources").performScrollToNode(hasText("Фарш аналитики → Чеснок аналитики"))
        compose.onNodeWithText("Фарш аналитики → Чеснок аналитики").assertExists()
        compose.onNodeWithTag("analysis-sources").performScrollToNode(hasText("Открыть дневник 2026-11-09"))
        compose.onAllNodesWithText("Открыть дневник 2026-11-09").onFirst().performClick()
        compose.runOnIdle {assertEquals("2026-11-09",navigated)}
    }
    @Test fun targetDraftSurvivesRestorationAndPersistsAssumptionWithoutDiagnosis() {
        val restoration=StateRestorationTester(compose);restoration.setContent {Screen()};ready()
        compose.onNodeWithTag("analysis-targets").performScrollTo().performClick();compose.onNodeWithText("Добавить продукт или компонент").performClick()
        compose.onNodeWithText("Поиск продукта или блюда").performScrollTo().performTextInput("Чеснок аналитики")
        compose.onNode(hasText("Чеснок аналитики") and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,androidx.compose.ui.semantics.Role.Button)).performScrollTo().performClick()
        compose.onNodeWithText("Источник предположения (необязательно)").performScrollTo().performTextInput("Собственное наблюдение")
        compose.onNodeWithText("Комментарий (необязательно)").performScrollTo().performTextInput("Проверить историю")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Источник предположения (необязательно)").performScrollTo().assertTextContains("Собственное наблюдение")
        capture("analysis-target-editor-restored")
        visible(compose.onNodeWithText("Сохранить предположение")).performClick()
        compose.waitUntil(10_000) {runBlocking {repo.observeAnalyticsHistory().first().targets.size==1}}
        val target=runBlocking {repo.observeAnalyticsHistory().first().targets.single().target}
        assertEquals("ingredient",target.scope);assertEquals("Собственное наблюдение",target.sourceNote);assertEquals("Проверить историю",target.comment)
    }
    @Test fun darkDoubleSizeWindowExcludesMedicineAfterEpisodeAndKeepsActionsReachable() {
        compose.setContent {Screen(dark=true,scale=2f)};ready()
        compose.onNodeWithTag("analysis-window-SIX").performScrollTo().performClick();compose.onNodeWithTag("analysis-episode").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Не вошли из-за позднего/равного времени: событий 1.").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText("Позднее лекарство аналитики",substring=true).assertCountEquals(0)
        capture("analysis-window-dark200")
        visible(compose.onNodeWithText("Отмена")).assertIsDisplayed().performClick()
        compose.onNodeWithText("Правила расчёта и ограничения").performScrollTo().performClick()
        capture("analysis-rules-dark200");visible(compose.onNodeWithText("Отмена")).performClick()
    }
}
