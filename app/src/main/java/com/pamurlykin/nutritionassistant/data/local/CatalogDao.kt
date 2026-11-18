package com.pamurlykin.nutritionassistant.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.pamurlykin.nutritionassistant.data.model.CalendarDayCounts
import org.json.JSONArray

class IngredientConverters {
    @TypeConverter fun decode(value: String): List<String> = JSONArray(value).let { array ->
        List(array.length()) { array.getString(it) }
    }
    @TypeConverter fun encode(value: List<String>): String = JSONArray(value).toString()
}

@Dao
interface CatalogDao {
    @Query("""
        SELECT date, SUM(meals) AS meals, SUM(medicines) AS medicines, SUM(stools) AS stools,
            SUM(looseStools) AS looseStools, SUM(softStools) AS softStools,
            MAX(foodComplete) AS foodComplete, MAX(symptomsComplete) AS symptomsComplete, MAX(stoolSummary) AS stoolSummary
        FROM (
            SELECT date, COUNT(*) AS meals, 0 AS medicines, 0 AS stools, 0 AS looseStools, 0 AS softStools,
                0 AS foodComplete, 0 AS symptomsComplete, NULL AS stoolSummary FROM meals
            WHERE user_id = :userId AND date BETWEEN :start AND :end GROUP BY date
            UNION ALL
            SELECT date, 0, COUNT(*), 0, 0, 0, 0, 0, NULL FROM medicines
            WHERE user_id = :userId AND date BETWEEN :start AND :end GROUP BY date
            UNION ALL
            SELECT date, 0, 0, COUNT(*), SUM(CASE WHEN quality IN (6,7) THEN 1 ELSE 0 END),
                SUM(CASE WHEN quality = 5 THEN 1 ELSE 0 END), 0, 0, NULL FROM stools
            WHERE user_id = :userId AND date BETWEEN :start AND :end GROUP BY date
            UNION ALL
            SELECT date, 0, 0, 0, 0, 0, food_complete, symptoms_complete, stool_summary FROM day_status
            WHERE user_id = :userId AND date BETWEEN :start AND :end
        ) GROUP BY date ORDER BY date
    """)
    fun observeCalendarCounts(userId: Long, start: String, end: String): Flow<List<CalendarDayCounts>>

    @Query("SELECT COUNT(*) FROM catalog") suspend fun count(): Int
    @Transaction @Query("SELECT * FROM catalog WHERE archived = 0 ORDER BY normalized_name, id")
    fun observeEntries(): Flow<List<CatalogItem>>
    @Transaction @Query("SELECT * FROM catalog WHERE id = :id")
    suspend fun getItem(id: Long): CatalogItem?
    @Query("SELECT product_id FROM dish_products WHERE dish_id = :id")
    suspend fun productIds(id: Long): List<Long>
    @Query("DELETE FROM dish_products WHERE dish_id = :id")
    suspend fun clearProducts(id: Long)
    @Insert suspend fun insertProducts(products: List<DishProductEntity>)
    @Query("SELECT * FROM catalog WHERE id = :id")
    suspend fun get(id: Long): CatalogEntryEntity?
    @Query("SELECT * FROM catalog WHERE kind = :kind AND food_type = :foodType AND normalized_name = :name AND product_kind = 'basic' AND brand = '' AND variant_label = '' ORDER BY id LIMIT 1")
    suspend fun find(kind: String, foodType: String, name: String): CatalogEntryEntity?
    @Insert suspend fun insert(entry: CatalogEntryEntity): Long
    @Update suspend fun update(entry: CatalogEntryEntity)
    @Query("UPDATE catalog SET archived = 1, revision = revision + 1 WHERE id = :id AND revision < 2147483647")
    suspend fun archive(id: Long)
    @Insert suspend fun insertMealItems(items: List<MealItemEntity>)
    @Insert suspend fun insertMedicineItems(items: List<MedicineItemEntity>)
    @Query("DELETE FROM meal_items WHERE meal_id = :id") suspend fun clearMeal(id: Long)
    @Query("DELETE FROM medicine_items WHERE medicine_id = :id") suspend fun clearMedicine(id: Long)
    @Query("SELECT * FROM meal_items WHERE meal_id = :id ORDER BY id") suspend fun mealItems(id: Long): List<MealItemEntity>
    @Query("SELECT * FROM medicine_items WHERE medicine_id = :id ORDER BY id") suspend fun medicineItems(id: Long): List<MedicineItemEntity>
    @Transaction @Query("SELECT * FROM meals WHERE user_id = :userId ORDER BY date, id")
    fun observeMeals(userId: Long): Flow<List<MealWithItems>>
    @Transaction @Query("SELECT * FROM medicines WHERE user_id = :userId ORDER BY date, id")
    fun observeMedicines(userId: Long): Flow<List<MedicineWithItems>>
    @Query("SELECT * FROM catalog ORDER BY id") suspend fun allEntries(): List<CatalogEntryEntity>
    @Query("SELECT * FROM dish_products ORDER BY dish_id, position, product_id") suspend fun allDishProducts(): List<DishProductEntity>
    @Query("SELECT * FROM product_components ORDER BY owner_id, position, product_id") suspend fun allProductComponents(): List<ProductComponentEntity>
    @Query("DELETE FROM product_components WHERE owner_id = :id") suspend fun clearComponents(id: Long)
    @Insert suspend fun insertComponents(items: List<ProductComponentEntity>)
    @Transaction @Query("SELECT * FROM catalog ORDER BY normalized_name, id") fun observeAllItems(): Flow<List<CatalogItem>>
    @Transaction @Query("SELECT * FROM catalog ORDER BY id") suspend fun allItems(): List<CatalogItem>
    @Query("SELECT * FROM backup_aliases") suspend fun aliases(): List<BackupAliasEntity>
    @Query("SELECT * FROM backup_aliases") fun observeAliases(): Flow<List<BackupAliasEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAlias(alias: BackupAliasEntity)
    @Query("UPDATE catalog SET favorite = :value WHERE id = :id") suspend fun setFavorite(id: Long, value: Boolean)
    @Query("""SELECT catalog_id FROM (
        SELECT meal_items.catalog_id, meals.date || 'T' || COALESCE(meals.actual_time, '--:--') || '|' || meals.created_at AS used FROM meal_items JOIN meals ON meals.id = meal_items.meal_id WHERE meals.user_id = 1
        UNION ALL SELECT medicine_items.catalog_id, medicines.date || 'T' || COALESCE(medicines.actual_time, '--:--') || '|' || medicines.created_at FROM medicine_items JOIN medicines ON medicines.id = medicine_items.medicine_id WHERE medicines.user_id = 1
        ) GROUP BY catalog_id ORDER BY MAX(used) DESC, catalog_id DESC LIMIT 30""")
    fun observeRecentIds(): Flow<List<Long>>
    @Transaction @Query("SELECT * FROM meals WHERE user_id = 1 ORDER BY date DESC, actual_time DESC, id DESC LIMIT 10")
    fun observeRecentMeals(): Flow<List<MealWithItems>>

}
