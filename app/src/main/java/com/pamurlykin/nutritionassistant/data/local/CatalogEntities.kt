package com.pamurlykin.nutritionassistant.data.local

import androidx.room.*

@Entity(tableName = "catalog", indices = [Index(value = ["kind", "food_type", "normalized_name"], unique = true)])
data class CatalogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "food_type") val foodType: String = "",
    val ingredients: List<String> = emptyList(),
    val archived: Boolean = false,
)

@Entity(tableName = "meal_items", foreignKeys = [
    ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["meal_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["id"], childColumns = ["catalog_id"]),
], indices = [Index("meal_id"), Index("catalog_id")])
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meal_id") val mealId: Long,
    @ColumnInfo(name = "catalog_id") val catalogId: Long,
    val name: String,
    @ColumnInfo(name = "food_type") val foodType: String,
    val ingredients: List<String>,
)

@Entity(tableName = "medicine_items", foreignKeys = [
    ForeignKey(entity = MedicineEntity::class, parentColumns = ["id"], childColumns = ["medicine_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["id"], childColumns = ["catalog_id"]),
], indices = [Index("medicine_id"), Index("catalog_id")])
data class MedicineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicine_id") val medicineId: Long,
    @ColumnInfo(name = "catalog_id") val catalogId: Long,
    val name: String,
    val dosage: String?,
)

data class MealWithItems(
    @Embedded val event: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "meal_id") val items: List<MealItemEntity>,
)

data class MedicineWithItems(
    @Embedded val event: MedicineEntity,
    @Relation(parentColumn = "id", entityColumn = "medicine_id") val items: List<MedicineItemEntity>,
)
