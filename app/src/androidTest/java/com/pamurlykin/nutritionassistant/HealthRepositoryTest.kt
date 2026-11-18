package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.nutritionassistant.data.backup.*
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthRepositoryTest {
    private lateinit var db: NutritionDatabase
    private lateinit var repo: NutritionRepository
    private val day="2026-11-17"
    private val options=ImportOptions(keepLocalConflicts=true,matchCatalog=true,restoreCatalog=true)
    private fun database()=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),NutritionDatabase::class.java).build()
    @Before fun setup()=runBlocking { db=database();repo=NutritionRepository(db);repo.initialize() }
    @After fun close() { db.close() }
    private suspend fun daily()=repo.observeDailyStats(1,day).first()
    private suspend fun reject(action:suspend ()->Unit) { try { action(); fail("Invalid observation accepted") } catch (_:IllegalArgumentException) {} }
    private fun values()=mapOf("stress" to HealthDraft("stress","0"),"activity" to HealthDraft("activity","none"),"wellbeing" to HealthDraft("wellbeing","4"),"illness" to HealthDraft("illness","unknown"))
    @Test fun quickDayPersistsFiveChoicesAndDailyIdentityWithoutInventingMissingDays()=runBlocking {
        repo.health.saveDay(1,day,values(),4,null)
        val first=daily(); assertEquals(4,first.healthEntries.size); assertEquals(4,first.sleep?.qualityRating);assertNull(first.sleep?.bedTime)
        assertTrue(first.healthEntries.all { it.period=="day" && it.daySlot==1 && it.scaleVersion==1 && it.comment==null && it.actualTime==null })
        val ids=first.healthEntries.associate { it.metric to it.publicId }
        repo.health.saveDay(1,day,values()+ ("stress" to HealthDraft("stress","2",context=EventContext(comment="Работа"))),5,"Лучше")
        assertEquals(ids,daily().healthEntries.associate { it.metric to it.publicId });assertEquals(first.sleep?.publicId,daily().sleep?.publicId)
        assertEquals("Работа",daily().healthEntries.single { it.metric=="stress" }.comment)
        val missing=repo.observeDailyStats(1,"2026-11-18").first();assertTrue(missing.healthEntries.isEmpty());assertNull(missing.sleep);assertNull(missing.dayStatus)
        repo.health.saveDay(1,day,values().mapValues { null },null,null)
        assertTrue(daily().healthEntries.isEmpty());assertNull(daily().sleep)
    }
    @Test fun momentsStaySeparateAndEditsPreserveUuidCreationAndExplicitTime()=runBlocking {
        repo.health.save(1,day,null,HealthDraft("activity","light"))
        val id=repo.health.save(1,day,null,HealthDraft("activity","moderate","moment",20,"walking",context=EventContext(RecordedTime("10:15","+03:00","Europe/Moscow"),"Прогулка")))
        repo.health.save(1,day,null,HealthDraft("activity","vigorous","moment"))
        val old=requireNotNull(db.feelingDao().getById(id))
        repo.health.save(1,day,id,old.toDraft().copy(context=old.toDraft().context.copy(comment="После еды")))
        val edited=requireNotNull(db.feelingDao().getById(id));assertEquals(old.publicId,edited.publicId);assertEquals(old.createdAt,edited.createdAt);assertEquals("10:15",edited.actualTime)
        assertEquals(ActivitySummary("vigorous",20,1),activitySummary(daily().healthEntries))
        assertEquals("light",daily().healthEntries.single { it.period=="day" }.valueCode)
        reject { repo.health.save(1,day,id,edited.toDraft().copy(period="day",context=EventContext())) }
        assertEquals(3,daily().healthEntries.size)
    }
    @Test fun completenessIsIndependentAndAllHealthSleepAndMedicationEditsResetSymptoms()=runBlocking {
        repo.confirmDay(1,day,true,true,"no_stool")
        repo.health.save(1,day,null,HealthDraft("illness","present",illnessImpact="mild",symptomCodes=listOf("digestive")))
        assertTrue(daily().dayStatus!!.foodComplete);assertFalse(daily().dayStatus!!.symptomsComplete)
        repo.confirmDay(1,day,true,true,"no_stool");repo.health.saveSleep(1,day,SleepDraft(4,awakenings=0))
        assertFalse(daily().dayStatus!!.symptomsComplete);assertEquals(0,daily().sleep?.awakenings)
        repo.confirmDay(1,day,true,true,"no_stool");repo.health.deleteSleep(1,day);assertFalse(daily().dayStatus!!.symptomsComplete)
        val med=repo.saveCatalogEntry(null,"medicine","Тестовый препарат","",emptyList())
        repo.confirmDay(1,day,true,true,"no_stool");repo.saveMedicines(day,null,listOf(MedicineSelection(med.id)))
        assertFalse(daily().dayStatus!!.symptomsComplete)
        repo.addStool(1,day,7,EventContext(RecordedTime("12:30","+03:00"),"Эпизод"),StoolDetails(0,2))
        reject { repo.confirmDay(1,day,true,true,"no_diarrhea") };reject { repo.confirmDay(1,day,true,true,"no_stool") }
        repo.confirmDay(1,day,false,true,"diarrhea");assertEquals("diarrhea",daily().dayStatus!!.stoolSummary)
        val stool=daily().stools.single();repo.updateStool(1,stool.id,6)
        assertEquals(stool.publicId,daily().stools.single().publicId);assertEquals(stool.createdAt,daily().stools.single().createdAt)
        assertEquals("12:30",daily().stools.single().actualTime);assertEquals(0,daily().stools.single().urgency);assertFalse(daily().dayStatus!!.symptomsComplete)
    }
    @Test fun oldUnratedLongNotesAndUndatedNightStayUnknownAfterEditingAndBackup()=runBlocking {
        val text="Прежняя запись ".repeat(100)
        val id=db.feelingDao().insert(HealthEntryEntity(userId=1,date=day,comment=text))
        repo.health.save(1,day,id,db.feelingDao().getById(id)!!.toDraft())
        db.sleepDao().upsert(SleepEntity(userId=1,date=day,bedTime="23:00",wakeupTime="07:00",qualityDescription=text))
        repo.health.saveSleep(1,day,db.sleepDao().getForDay(1,day)!!.toDraft().copy(quality=4))
        assertNull(daily().healthEntries.single().valueCode);assertNull(daily().sleep?.bedDate);assertNull(daily().sleep?.bedIntervalMinutes())
        val decoded=BackupCodec.decode(BackupStore(db,"test").export());assertEquals(text,decoded.records.single { it.type=="health_entries" }.values["comment"])
        assertEquals(text,decoded.records.single { it.type=="sleeps" }.values["quality_description"])
    }
    @Test fun jsonFourRestoresEveryScaleTimeCommentAndRepeatsWithoutDuplicates()=runBlocking {
        repo.health.saveDay(1,day,values(),3,"Сон")
        repo.health.save(1,day,null,HealthDraft("illness","present","moment",illnessImpact="moderate",symptomCodes=listOf("fever","digestive"),context=EventContext(RecordedTime("09:05","+03:00"),"Недомогание")))
        repo.addStool(1,day,6,EventContext(comment="Тест"),StoolDetails(2,1));repo.confirmDay(1,day,true,true,"diarrhea")
        val bytes=BackupStore(db,"test").export();val json=JSONObject(bytes.toString(Charsets.UTF_8));assertEquals(6,json.getInt("schemaVersion"));assertTrue(json.has("scales"))
        val target=database()
        try {
            val targetRepo=NutritionRepository(target);targetRepo.initialize();val store=BackupStore(target,"test");val file=BackupCodec.decode(bytes)
            store.apply(store.preview(file),options)
            val restored=targetRepo.observeDailyStats(1,day).first()
            assertEquals(daily().healthEntries.map { it.copy(id=0) }.toSet(),restored.healthEntries.map { it.copy(id=0) }.toSet())
            assertEquals(daily().sleep?.copy(id=0),restored.sleep?.copy(id=0));assertEquals(daily().stools.map { it.copy(id=0) },restored.stools.map { it.copy(id=0) })
            assertEquals("diarrhea",restored.dayStatus?.stoolSummary);assertEquals(0,store.preview(file).added);assertTrue(store.preview(file).conflicts.isEmpty())
        } finally { target.close() }
    }
    @Test fun conflictingDailyHealthIsExplicitAndInvalidImportCannotChangeDatabase()=runBlocking {
        repo.health.save(1,day,null,HealthDraft("stress","0"));val source=BackupStore(db,"test");val file=BackupCodec.decode(source.export())
        val target=database()
        try {
            val targetRepo=NutritionRepository(target);targetRepo.initialize();targetRepo.health.save(1,day,null,HealthDraft("stress","4"));val store=BackupStore(target,"test")
            val preview=store.preview(file);assertTrue(preview.conflicts.any { it.name.startsWith("Наблюдения состояния") });store.apply(preview,options)
            assertEquals("4",targetRepo.observeDailyStats(1,day).first().healthEntries.single().valueCode);assertEquals(0,store.preview(file).added)
        } finally { target.close() }
        val before=BackupCodec.fingerprint(source.snapshot())
        listOf(mapOf("value_label" to "Другой смысл"),mapOf("scale_version" to 2L),mapOf("duration_minutes" to 5L),mapOf("period" to "moment")).forEach { bad ->
            reject { source.preview(file.copy(records=file.records.map { if(it.type=="health_entries") it.copy(values=it.values+bad) else it })) }
        }
        val json=JSONObject(source.export().toString(Charsets.UTF_8));json.getJSONObject("scales").getJSONObject("stress").put("version",2)
        reject { BackupCodec.decode(json.toString().toByteArray(Charsets.UTF_8)) }
        assertEquals(before,BackupCodec.fingerprint(source.snapshot()))
    }
    @Test fun allLegacyJsonVersionsPreserveFreeTextWithoutGuessingRatingsOrTimes()=runBlocking {
        repo.addFeeling(1,day,"Хорошо, но балл неизвестен")
        repo.upsertSleep(1,day,"07:00","23:00","Неоценённый сон")
        val modern=JSONObject(BackupStore(db,"test").export().toString(Charsets.UTF_8))
        (1..3).forEach { version ->
            val json=JSONObject(modern.toString());json.put("schemaVersion",version);json.remove("scales")
            val rows=json.getJSONArray("records")
            for(index in 0 until rows.length()) {
                val row=rows.getJSONObject(index);val type=if(row.getString("type")=="health_entries") "feelings" else row.getString("type")
                row.put("type",type);val values=row.getJSONObject("values")
                if(type=="feelings") values.put("description",values.getString("comment"))
                if(type=="catalog" && version==1) values.put("product_ids",org.json.JSONArray())
                val fields=when(version) { 1->BackupSchema.versionOne;2->BackupSchema.versionTwo;else->BackupSchema.versionThree }.getValue(type).fields.keys
                values.keys().asSequence().toList().filter { it !in fields }.forEach { values.remove(it) }
            }
            val decoded=BackupCodec.decode(json.toString().toByteArray(Charsets.UTF_8))
            val note=decoded.records.single { it.type=="health_entries" };assertEquals("Хорошо, но балл неизвестен",note.values["comment"])
            assertNull(note.values["value_code"]);assertNull(note.values["scale_version"]);assertNull(note.values["actual_time"]);assertEquals("moment",note.values["period"])
            val sleep=decoded.records.single { it.type=="sleeps" };assertEquals("Неоценённый сон",sleep.values["quality_description"]);assertNull(sleep.values["quality_rating"])
            if(version==1) assertNull(sleep.values["bed_date"])
            assertEquals(daily().healthEntries.single().publicId,note.publicId)
        }
    }
    @Test fun documentedFormatsOneThroughFourAreDecodedByProductionParser()=runBlocking {
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        (1..6).forEach { version ->
            val file=assets.open("nutrition-backup-v$version.json").use(BackupCodec::read)
            BackupCodec.validate(file);assertEquals(file.records.size,BackupCodec.decode(BackupCodec.encode(file)).records.size)
        }
    }
}
