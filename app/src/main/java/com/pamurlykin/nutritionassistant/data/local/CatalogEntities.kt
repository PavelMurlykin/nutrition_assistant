package com.pamurlykin.nutritionassistant.data.local

import androidx.room.*

@Entity(tableName = "catalog", indices = [Index(value = ["kind", "food_type", "normalized_name"]), Index(value = ["public_id"], unique = true)])
data class CatalogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "food_type") val foodType: String = "",
    // Compatibility column for schema 2; current recipes use dish_products.
    val ingredients: List<String> = emptyList(),
    val archived: Boolean = false,
    @ColumnInfo(name = "public_id", defaultValue = "''") val publicId: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "category_code", defaultValue = "NULL") val categoryCode: String? = null,
    @ColumnInfo(name = "product_kind", defaultValue = "'basic'") val productKind: String = "basic",
    @ColumnInfo(defaultValue = "''") val brand: String = "",
    @ColumnInfo(name = "variant_label", defaultValue = "''") val variantLabel: String = "",
    @ColumnInfo(name = "composition_state", defaultValue = "'unknown'") val compositionState: String = "unknown",
    @ColumnInfo(name = "composition_source", defaultValue = "NULL") val compositionSource: String? = null,
    @ColumnInfo(name = "composition_checked_on", defaultValue = "NULL") val compositionCheckedOn: String? = null,
    @ColumnInfo(defaultValue = "NULL") val comment: String? = null,
    @ColumnInfo(defaultValue = "1") val revision: Int = 1,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
)

@Entity(tableName = "dish_products", primaryKeys = ["dish_id", "product_id"], foreignKeys = [
    ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["id"], childColumns = ["dish_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["id"], childColumns = ["product_id"]),
], indices = [Index("product_id")])
data class DishProductEntity(
    @ColumnInfo(name = "dish_id") val dishId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(defaultValue = "NULL") val quantity: String? = null,
    @ColumnInfo(defaultValue = "NULL") val unit: String? = null,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

@Entity(tableName = "product_components", primaryKeys = ["owner_id", "product_id"], foreignKeys = [
    ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["id"], childColumns = ["owner_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["id"], childColumns = ["product_id"]),
], indices = [Index("product_id")])
data class ProductComponentEntity(
    @ColumnInfo(name = "owner_id") val ownerId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(defaultValue = "NULL") val quantity: String? = null,
    @ColumnInfo(defaultValue = "NULL") val unit: String? = null,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

data class CatalogItem(
    @Embedded val entry: CatalogEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "id", associateBy = Junction(
        value = DishProductEntity::class, parentColumn = "dish_id", entityColumn = "product_id"))
    val products: List<CatalogEntryEntity>,
    @Relation(parentColumn = "id", entityColumn = "id", associateBy = Junction(
        value = ProductComponentEntity::class, parentColumn = "owner_id", entityColumn = "product_id"))
    val productComponents: List<CatalogEntryEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "dish_id") val dishLinks: List<DishProductEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "owner_id") val componentLinks: List<ProductComponentEntity> = emptyList(),
) {
    val id get() = entry.id
    val kind get() = entry.kind
    val name get() = entry.name
    val normalizedName get() = entry.normalizedName
    val foodType get() = entry.foodType
    val archived get() = entry.archived
    val directProducts get() = if (kind == "product") productComponents else products
    val productIds get() = directProducts.map { it.id }
    val ingredients get() = directProducts.sortedBy { it.normalizedName }.map { it.name }
    val displayName get() = entry.name + listOf(entry.brand, entry.variantLabel).filter { it.isNotBlank() }.joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" }
}

@Entity(tableName = "meal_items", foreignKeys = [
    ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["meal_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["id"], childColumns = ["catalog_id"]),
], indices = [Index("meal_id"), Index("catalog_id"), Index(value = ["public_id"], unique = true)])
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meal_id") val mealId: Long,
    @ColumnInfo(name = "catalog_id") val catalogId: Long,
    val name: String,
    @ColumnInfo(name = "food_type") val foodType: String,
    val ingredients: List<String>,
    @ColumnInfo(defaultValue = "'dish'") val kind: String = "dish",
    @ColumnInfo(name = "public_id", defaultValue = "''") val publicId: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "source_revision", defaultValue = "NULL") val sourceRevision: Int? = null,
    @ColumnInfo(name = "composition_snapshot", defaultValue = "NULL") val compositionSnapshot: String? = null,
    @ColumnInfo(defaultValue = "NULL") val quantity: String? = null,
    @ColumnInfo(defaultValue = "NULL") val unit: String? = null,
    @ColumnInfo(defaultValue = "NULL") val comment: String? = null,
)

@Entity(tableName = "medicine_items", foreignKeys = [
    ForeignKey(entity = MedicineEntity::class, parentColumns = ["id"], childColumns = ["medicine_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["id"], childColumns = ["catalog_id"]),
], indices = [Index("medicine_id"), Index("catalog_id"), Index(value = ["public_id"], unique = true)])
data class MedicineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicine_id") val medicineId: Long,
    @ColumnInfo(name = "catalog_id") val catalogId: Long,
    val name: String,
    val dosage: String?,
    @ColumnInfo(name = "public_id", defaultValue = "''") val publicId: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "source_revision", defaultValue = "NULL") val sourceRevision: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val quantity: String? = null,
    @ColumnInfo(defaultValue = "NULL") val unit: String? = null,
    @ColumnInfo(defaultValue = "NULL") val comment: String? = null,
)

/** Explicit, persistent matches confirmed in an import preview. */
@Entity(tableName = "backup_aliases")
data class BackupAliasEntity(
    @PrimaryKey @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    val type: String,
)

data class MealWithItems(
    @Embedded val event: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "meal_id") val items: List<MealItemEntity>,
)

data class MedicineWithItems(
    @Embedded val event: MedicineEntity,
    @Relation(parentColumn = "id", entityColumn = "medicine_id") val items: List<MedicineItemEntity>,
)
