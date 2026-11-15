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
        SELECT date, SUM(meals) AS meals, SUM(medicines) AS medicines, SUM(stools) AS stools
        FROM (
            SELECT date, COUNT(*) AS meals, 0 AS medicines, 0 AS stools FROM meals
            WHERE user_id = :userId AND date BETWEEN :start AND :end GROUP BY date
            UNION ALL
            SELECT date, 0 AS meals, COUNT(*) AS medicines, 0 AS stools FROM medicines
            WHERE user_id = :userId AND date BETWEEN :start AND :end GROUP BY date
            UNION ALL
            SELECT date, 0 AS meals, 0 AS medicines, COUNT(*) AS stools FROM stools
            WHERE user_id = :userId AND date BETWEEN :start AND :end GROUP BY date
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
    @Query("SELECT * FROM catalog WHERE kind = :kind AND food_type = :foodType AND normalized_name = :name")
    suspend fun find(kind: String, foodType: String, name: String): CatalogEntryEntity?
    @Insert suspend fun insert(entry: CatalogEntryEntity): Long
    @Update suspend fun update(entry: CatalogEntryEntity)
    @Query("UPDATE catalog SET archived = 1 WHERE id = :id")
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
}
