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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import com.pamurlykin.nutritionassistant.ui.components.MonthCalendar
import com.pamurlykin.nutritionassistant.ui.daily.*
import com.pamurlykin.nutritionassistant.ui.theme.NutritionAssistantTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import java.time.*
import org.junit.*
import org.junit.Assert.*

class SymptomCalendarUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var db:NutritionDatabase
    private lateinit var repo:NutritionRepository
    private val day=LocalDate.of(2040,2,17)
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),NutritionDatabase::class.java).build();repo=NutritionRepository(db);repo.initialize()
        val product=repo.saveCatalogEntry(null,"product","Еда хронологии","",emptyList())
        val med=repo.saveCatalogEntry(null,"medicine","Лекарство хронологии","",emptyList())
        repo.saveMeal(day.toString(),MealType.BREAKFAST,null,listOf(product.id),EventContext(RecordedTime("09:00","+03:00"),"Еда до эпизода"))
        repo.saveMedicines(day.toString(),null,listOf(MedicineSelection(med.id,"1 таблетка")),EventContext(RecordedTime("13:00","+03:00"),"Приём после эпизода"))
        repo.addStool(1,day.toString(),4,EventContext(RecordedTime("10:00","+03:00")))
        repo.addStool(1,day.toString(),6,EventContext(RecordedTime("12:30","+03:00")))
        repo.addStool(1,day.toString(),7);repo.addStool(1,day.plusDays(1).toString(),5)
        repo.confirmDay(1,day.plusDays(2).toString(),false,true,"no_stool")
        repo.health.save(1,day.toString(),null,HealthDraft("stress","0"))
        repo.health.saveSleep(1,day.toString(),SleepDraft(4))
    }
    @After fun close() { db.close() }
    @Composable private fun Screen(dark:Boolean=false,scale:Float=1f) {
        val density=LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density,scale)) { NutritionAssistantTheme(darkTheme=dark) { Surface(Modifier.fillMaxSize()) {
            var selected by rememberSaveable { mutableStateOf(day.toString()) }
            val date=LocalDate.parse(selected);val month=YearMonth.from(date);val start=calendarStart(month)
            val counts by remember(month) { repo.observeCalendarCounts(start.toString(),start.plusDays(41).toString()).map { it as List<CalendarDayCounts>? } }.collectAsState(initial=null)
            val snapshot by remember(selected) { repo.observeDailyStats(1,selected) }.collectAsState(initial=null)
            val calendar=buildCalendarMonth(month,date,day,counts.orEmpty(),isLoaded=counts!=null)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                MonthCalendar(calendar,{selected=month.minusMonths(1).atDay(1).toString()},{selected=month.plusMonths(1).atDay(1).toString()},{selected=it.toString()})
                CalendarMonthOverview(calendar,true) { selected=it.toString() }
                snapshot?.let { DayTimelineCard(it) }
            }
        } } }
    }
    private fun description(date:LocalDate)=compose.onNodeWithTag("calendar-day-$date").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
    private fun ready()=compose.waitUntil(15_000) { runCatching { description(day).contains("Жидкий стул, тип 6–7: 2") }.getOrDefault(false) }
    private fun capture(name:String) {
        val dir=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file=java.io.File(dir,"qa/$name.png").apply { parentFile?.mkdirs() };val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun mixedDayAccessibleMarkAndCountersReactToRemovalOfLastLooseEpisode() {
        compose.setContent { Screen() };ready()
        compose.onNodeWithTag("calendar-day-$day").assertIsSelected().assertContentDescriptionContains("Сегодня",substring=true)
        assertTrue(description(day.plusDays(1)).contains("Мягкий стул, тип 5: 1"));assertTrue(description(day.plusDays(1)).contains("Итог симптомов не подтверждён"))
        capture("calendar-symptoms-light")
        val stools=runBlocking { repo.observeDailyStats(1,day.toString()).first().stools }
        runBlocking { repo.updateStool(1,stools.single { it.quality==7 }.id,5);repo.deleteStool(1,stools.single { it.quality==6 }.id) }
        compose.waitUntil(10_000) { description(day).contains("Жидкий стул, тип 6–7: 0") }
        assertTrue(description(day).contains("Итог симптомов не подтверждён"));assertFalse(description(day).contains("Подтверждено:"))
    }
    @Test fun monthFilterRestoresAndSwitchesMonthsWithoutTreatingEmptyMonthAsHealthy() {
        val restoration=StateRestorationTester(compose);restoration.setContent { Screen() };ready()
        compose.onNodeWithTag("open-month-days").performScrollTo().performClick();compose.onNodeWithTag("filter-loose-days").performClick()
        compose.onNodeWithTag("month-list-day-$day").assertExists();compose.onNodeWithTag("month-list-day-${day.plusDays(1)}").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("filter-loose-days").assertIsSelected();compose.onNodeWithTag("month-list-day-$day").performClick()
        compose.onNodeWithTag("calendar-day-$day").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Следующий месяц").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("calendar-day-2040-03-01").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open-month-days").performScrollTo().performClick()
        compose.onNodeWithText("В этом месяце нет записанных эпизодов типа 6–7. Пропуски не означают отсутствия симптома.").assertExists()
        capture("calendar-filter-empty-month")
    }
    @Test fun darkDoubleSizeCalendarAndTimelineKeepAllStatesReachable() {
        compose.setContent { Screen(dark=true,scale=2f) };ready();capture("calendar-symptoms-dark200-top")
        val confirmed=day.plusDays(2);compose.onNodeWithTag("calendar-day-$confirmed").performScrollTo().performClick().assertIsSelected()
        assertTrue(description(confirmed).contains("Подтверждено: стула не было"));assertTrue(description(confirmed).contains("Питание: не подтверждено"))
        capture("calendar-symptoms-dark200-days")
        compose.onNodeWithTag("calendar-day-$day").performScrollTo().performClick()
        val snapshot=runBlocking { repo.observeDailyStats(1,day.toString()).first() }
        val meal=snapshot.meals.single().event.id;val med=snapshot.medicines.single().event.id;val stool=snapshot.stools.single { it.quality==6 }.id
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("timeline-meal-$meal").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("timeline-meal-$meal").performScrollTo().assertExists()
        compose.onNodeWithTag("timeline-stool-$stool").performScrollTo().assertExists()
        compose.onNodeWithTag("timeline-medicine-$med").performScrollTo().assertExists()
        compose.onNodeWithText("Приём после эпизода").assertExists();compose.onNodeWithText("За день или ночь — без порядка относительно событий (2)").performScrollTo().assertIsDisplayed()
        val mealY=compose.onNodeWithTag("timeline-meal-$meal").fetchSemanticsNode().positionInRoot.y
        val stoolY=compose.onNodeWithTag("timeline-stool-$stool").fetchSemanticsNode().positionInRoot.y
        val medicineY=compose.onNodeWithTag("timeline-medicine-$med").fetchSemanticsNode().positionInRoot.y
        assertTrue("Medication must appear after the earlier stool and meal",mealY < stoolY && stoolY < medicineY)
        capture("calendar-timeline-dark200")
    }
}
