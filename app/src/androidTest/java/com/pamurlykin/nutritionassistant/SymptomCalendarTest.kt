package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.backup.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class SymptomCalendarTest {
    private lateinit var db:NutritionDatabase
    private lateinit var repo:NutritionRepository
    private val day="2026-11-17"
    private fun database()=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),NutritionDatabase::class.java).build()
    @Before fun setup()=runBlocking { db=database();repo=NutritionRepository(db);repo.initialize() }
    @After fun close() { db.close() }
    private suspend fun counts()=repo.observeCalendarCounts("2026-10-26","2026-12-06").first()
    private suspend fun month()=buildCalendarMonth(YearMonth.of(2026,11),LocalDate.parse(day),LocalDate.parse(day),counts())
    @Test fun mixedStoolDayCountsEpisodesAndUpdatesAfterEditingAndDeletingLastLooseStool()=runBlocking {
        listOf(4,5,6,7).forEach { repo.addStool(1,day,it) };repo.addStool(1,"2026-10-31",7);repo.addStool(1,"2026-12-01",7)
        assertEquals(CalendarMonthSummary(30,1,2,1,0,0,0),summarizeCalendarMonth(month()))
        val stools=repo.observeDailyStats(1,day).first().stools
        repo.updateStool(1,stools.single { it.quality==7 }.id,5)
        assertEquals(1,counts().single { it.date==day }.looseStools);assertEquals(2,counts().single { it.date==day }.softStools)
        val flow=async(start=CoroutineStart.UNDISPATCHED) { withTimeout(10_000) { repo.observeCalendarCounts(day,day).first { it.single().looseStools==0 } } }
        repo.deleteStool(1,stools.single { it.quality==6 }.id);assertEquals(0,flow.await().single().looseStools)
        val selected=month().days.single { it.date.toString()==day };assertEquals(CalendarStoolState.UNCONFIRMED,selected.stoolState);assertEquals(3,selected.stools)
        assertEquals(0,summarizeCalendarMonth(month()).looseDays)
    }
    @Test fun confirmationOnlyDaysAreVisibleAndMissingNeverBecomesConfirmedAfterDeletion()=runBlocking {
        repo.confirmDay(1,day,false,true,"no_stool")
        val empty=LocalDate.parse(day).plusDays(1)
        var state=month();assertEquals(CalendarStoolState.NO_STOOL,state.days.single { it.date.toString()==day }.stoolState)
        assertEquals(CalendarStoolState.UNCONFIRMED,state.days.single { it.date==empty }.stoolState)
        assertEquals(0,summarizeCalendarMonth(state).foodConfirmed);assertEquals(1,summarizeCalendarMonth(state).confirmedWithoutLoose)
        repo.addStool(1,day,4);repo.confirmDay(1,day,true,true,"no_diarrhea")
        assertEquals(CalendarStoolState.NO_LOOSE,month().days.single { it.date.toString()==day }.stoolState)
        repo.addStool(1,day,7);val loose=repo.observeDailyStats(1,day).first().stools.single { it.quality==7 }
        repo.deleteStool(1,loose.id);state=month()
        assertEquals(CalendarStoolState.UNCONFIRMED,state.days.single { it.date.toString()==day }.stoolState)
        assertTrue(state.days.single { it.date.toString()==day }.foodComplete);assertEquals(0,summarizeCalendarMonth(state).confirmedWithoutLoose)
    }
    @Test fun jsonFourRestoresCalendarResultsWithoutAnyNewBackupFields()=runBlocking {
        repo.addStool(1,day,6);repo.addStool(1,day,7);repo.addStool(1,"2026-11-18",5)
        repo.confirmDay(1,day,true,true,"diarrhea");repo.confirmDay(1,"2026-11-19",false,true,"no_stool")
        val original=counts();val bytes=BackupStore(db,"test").export();val target=database()
        try {
            val other=NutritionRepository(target);other.initialize();val store=BackupStore(target,"test");val file=BackupCodec.decode(bytes)
            store.apply(store.preview(file),ImportOptions(keepLocalConflicts=true,matchCatalog=true,restoreCatalog=true))
            assertEquals(original,other.observeCalendarCounts("2026-10-26","2026-12-06").first())
            assertEquals(0,store.preview(file).added)
        } finally { target.close() }
    }
}
