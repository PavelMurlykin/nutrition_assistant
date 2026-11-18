package com.pamurlykin.nutritionassistant.data.local

import androidx.room.*
import com.pamurlykin.nutritionassistant.util.StorageFormats
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(
    tableName = "product_links",
    foreignKeys = [ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["id"], childColumns = ["catalog_id"])],
    indices = [Index(value = ["catalog_id", "url"], unique = true), Index(value = ["public_id"], unique = true)],
)
data class ProductLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "catalog_id") val catalogId: Long,
    val url: String,
    val source: String,
    @ColumnInfo(name = "checked_on") val checkedOn: String?,
    @ColumnInfo(name = "created_at") val createdAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "updated_at") val updatedAt: String = StorageFormats.nowTimestamp(),
    @ColumnInfo(name = "public_id") val publicId: String = UUID.randomUUID().toString(),
)

@Dao
interface ProductLinkDao {
    @Query("SELECT * FROM product_links WHERE catalog_id = :catalogId ORDER BY source COLLATE NOCASE, id")
    fun observe(catalogId: Long): Flow<List<ProductLinkEntity>>

    @Query("SELECT * FROM product_links WHERE id = :id AND catalog_id = :catalogId")
    suspend fun find(id: Long, catalogId: Long): ProductLinkEntity?

    @Query("SELECT * FROM product_links WHERE catalog_id = :catalogId AND url = :url")
    suspend fun findByUrl(catalogId: Long, url: String): ProductLinkEntity?

    @Insert suspend fun insert(link: ProductLinkEntity): Long
    @Update suspend fun update(link: ProductLinkEntity)
    @Delete suspend fun delete(link: ProductLinkEntity)

    @Query("DELETE FROM backup_aliases WHERE target_id = :publicId AND type = 'product_links'")
    suspend fun forgetAliases(publicId: String)
}
