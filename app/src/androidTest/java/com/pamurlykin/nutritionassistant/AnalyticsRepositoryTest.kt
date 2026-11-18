package com.pamurlykin.nutritionassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.backup.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.json.JSONObject

class AnalyticsRepositoryTest {
    private lateinit var db:NutritionDatabase
    private lateinit var repo:NutritionRepository
    private fun database()=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),NutritionDatabase::class.java).build()
    private val options=ImportOptions(keepLocalConflicts=true,matchCatalog=true,restoreCatalog=true)
    @Before fun setup()=runBlocking {db=database();repo=NutritionRepository(db);repo.initialize()}
    @After fun close() {db.close()}
    private suspend fun reject(action:suspend ()->Unit) {try {action();fail("Invalid analytics data accepted")} catch(_:IllegalArgumentException) {}}
    @Test fun profileKeepsIdentitySourceAndHistoryAcrossRenameArchiveAndDeactivation()=runBlocking {
        val garlic=repo.foodCatalog.save(null,"product","Профиль чеснок","",CatalogDetails("vegetables",composition="confirmed",source="Этикетка",checkedOn="2026-11-18"),emptyList())
        repo.saveMeal("2026-11-17",MealType.DINNER,null,listOf(garlic.id),EventContext(comment="Заметка приёма"))
        val id=repo.analytics.saveTarget(1,null,garlic.id,"ingredient","Моё предположение","Наблюдаю")
        val first=db.analyticsDao().get(id)!!
        repo.foodCatalog.save(garlic.id,"product","Новое имя","",garlic.entry.details(),emptyList());repo.archiveCatalogEntry(garlic.id)
        repo.analytics.saveTarget(1,id,garlic.id,"ingredient","Совет обсудить с врачом","Уточнение",false)
        val edited=db.analyticsDao().get(id)!!;assertEquals(first.publicId,edited.publicId);assertEquals(first.createdAt,edited.createdAt);assertEquals("Профиль чеснок",edited.nameSnapshot)
        assertFalse(edited.active);val h=repo.observeAnalyticsHistory().first()
        val evidence=ExplainableAnalyzer.targetHistory(h,h.targets.single(),"2026-11-18")
        assertEquals(1,evidence.size);assertTrue(evidence.single().title.contains("Профиль чеснок"));assertEquals("Заметка приёма",evidence.single().comment)
        assertEquals(id,repo.analytics.saveTarget(1,null,garlic.id,"ingredient",null,null))
    }
    @Test fun coherentHistoryFlowAndJsonFiveRestoreTrackingAndRepeatWithoutDuplicates()=runBlocking {
        val product=repo.observeCatalog().first().first {it.kind=="product"};repo.analytics.saveTarget(1,null,product.id,"entry","Собственная заметка","Комментарий")
        val store=BackupStore(db,"test");val bytes=store.export();assertEquals(6,JSONObject(bytes.toString(Charsets.UTF_8)).getInt("schemaVersion"))
        val target=database()
        try {
            val other=NutritionRepository(target);other.initialize();val targetStore=BackupStore(target,"test");val file=BackupCodec.decode(bytes)
            targetStore.apply(targetStore.preview(file),options)
            val restored=other.observeAnalyticsHistory().first().targets.single()
            assertEquals("Собственная заметка",restored.target.sourceNote);assertEquals("Комментарий",restored.target.comment);assertEquals(product.entry.publicId,restored.entry.publicId)
            assertEquals(0,targetStore.preview(file).added);assertTrue(targetStore.preview(file).conflicts.isEmpty())
            val changed=async(start=CoroutineStart.UNDISPATCHED) { withTimeout(10_000) {other.observeAnalyticsHistory().first {it.targets.single().target.active==false}} }
            other.analytics.setActive(1,restored.target.id,false);assertFalse(changed.await().targets.single().target.active)
        } finally {target.close()}
    }
    @Test fun profileConflictRequiresExplicitLocalChoiceAndInvalidScopeRollsBack()=runBlocking {
        val product=repo.observeCatalog().first().first {it.kind=="product"};repo.analytics.saveTarget(1,null,product.id,"entry","Из файла",null)
        val file=BackupCodec.decode(BackupStore(db,"test").export());val target=database()
        try {
            val other=NutritionRepository(target);other.initialize();val matching=other.observeCatalog().first().single {it.entry.publicId==product.entry.publicId}
            other.analytics.saveTarget(1,null,matching.id,"entry","Локально",null)
            val store=BackupStore(target,"test");val preview=store.preview(file);assertTrue(preview.conflicts.any {it.name.startsWith("Что отслеживать")})
            reject {store.apply(preview,ImportOptions())};store.apply(preview,options)
            assertEquals("Локально",other.observeAnalyticsHistory().first().targets.single().target.sourceNote);assertEquals(0,store.preview(file).added)
            val before=BackupCodec.fingerprint(store.snapshot())
            reject {store.preview(file.copy(records=file.records.map {if(it.type=="tracking_targets") it.copy(values=it.values+("scope" to "confirmed_intolerance")) else it}))}
            assertEquals(before,BackupCodec.fingerprint(store.snapshot()))
        } finally {target.close()}
        val medicine=repo.saveCatalogEntry(null,"medicine","Тестовый препарат","",emptyList())
        reject {repo.analytics.saveTarget(1,null,medicine.id,"ingredient",null,null)}
    }
    @Test fun documentedLegacyFourAddsNoGuessedTargetsAndVersionFiveReadsThroughParser()=runBlocking {
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        val old=assets.open("nutrition-backup-v4.json").use(BackupCodec::read);assertFalse(old.records.any {it.type=="tracking_targets"})
        val latest=assets.open("nutrition-backup-v5.json").use(BackupCodec::read);assertEquals(1,latest.records.count {it.type=="tracking_targets"})
        val store=BackupStore(db,"test");store.apply(store.preview(latest),options);assertEquals(1,repo.observeAnalyticsHistory().first().targets.size)
    }
}
