package com.pamurlykin.nutritionassistant.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.pamurlykin.nutritionassistant.util.StorageFormats

@Entity(tableName = "tracking_targets", foreignKeys = [ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["id"], childColumns = ["catalog_id"])],
    indices = [Index("catalog_id"), Index(value = ["public_id"], unique = true), Index(value = ["user_id", "catalog_id", "scope"], unique = true)])
data class TrackingTargetEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long, @ColumnInfo(name = "catalog_id") val catalogId: Long,
    val scope: String, @ColumnInfo(name = "name_snapshot") val nameSnapshot: String,
    @ColumnInfo(name = "source_note") val sourceNote: String? = null, val comment: String? = null, val active: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "updated_at") val updatedAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "public_id") val publicId: String = java.util.UUID.randomUUID().toString())
data class TrackingTargetWithEntry(@Embedded val target: TrackingTargetEntity,
    @Relation(parentColumn = "catalog_id", entityColumn = "id") val entry: CatalogEntryEntity)
data class AnalyticsRows(@Embedded val user: UserEntity,
    @Relation(parentColumn = "user_id", entityColumn = "user_id", entity = MealEntity::class) val meals: List<MealWithItems>,
    @Relation(parentColumn = "user_id", entityColumn = "user_id", entity = MedicineEntity::class) val medicines: List<MedicineWithItems>,
    @Relation(parentColumn = "user_id", entityColumn = "user_id") val stools: List<StoolEntity>,
    @Relation(parentColumn = "user_id", entityColumn = "user_id") val health: List<HealthEntryEntity>,
    @Relation(parentColumn = "user_id", entityColumn = "user_id") val sleeps: List<SleepEntity>,
    @Relation(parentColumn = "user_id", entityColumn = "user_id") val statuses: List<DayStatusEntity>,
    @Relation(parentColumn = "user_id", entityColumn = "user_id", entity = TrackingTargetEntity::class) val targets: List<TrackingTargetWithEntry>)
@Dao
interface AnalyticsDao {
    @Transaction @Query("SELECT * FROM users WHERE user_id = :userId") fun observe(userId: Long): Flow<AnalyticsRows?>
    @Transaction @Query("SELECT * FROM meals WHERE user_id = :userId AND date BETWEEN :start AND :end")
    suspend fun mealsInRange(userId: Long, start: String, end: String): List<MealWithItems>
    @Transaction @Query("SELECT * FROM medicines WHERE user_id = :userId AND date BETWEEN :start AND :end")
    suspend fun medicinesInRange(userId: Long, start: String, end: String): List<MedicineWithItems>
    @Query("SELECT * FROM stools WHERE user_id = :userId AND date BETWEEN :start AND :end")
    suspend fun stoolsInRange(userId: Long, start: String, end: String): List<StoolEntity>
    @Query("SELECT * FROM health_entries WHERE user_id = :userId AND date BETWEEN :start AND :end")
    suspend fun healthInRange(userId: Long, start: String, end: String): List<HealthEntryEntity>
    @Query("SELECT * FROM sleeps WHERE user_id = :userId AND date BETWEEN :start AND :end")
    suspend fun sleepsInRange(userId: Long, start: String, end: String): List<SleepEntity>
    @Query("SELECT * FROM day_status WHERE user_id = :userId AND date BETWEEN :start AND :end")
    suspend fun statusesInRange(userId: Long, start: String, end: String): List<DayStatusEntity>
    @Query("SELECT * FROM tracking_targets WHERE id = :id") suspend fun get(id: Long): TrackingTargetEntity?
    @Query("SELECT * FROM tracking_targets WHERE user_id = :userId AND catalog_id = :catalogId AND scope = :scope") suspend fun find(userId: Long, catalogId: Long, scope: String): TrackingTargetEntity?
    @Insert suspend fun insert(value: TrackingTargetEntity): Long
    @Update suspend fun update(value: TrackingTargetEntity)
}
