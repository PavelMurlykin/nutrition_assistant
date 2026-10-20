package com.pamurlykin.nutritionassistant.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class IngredientConverters {
    @TypeConverter fun decode(value: String): List<String> = JSONArray(value).let { array ->
        List(array.length()) { array.getString(it) }
    }
    @TypeConverter fun encode(value: List<String>): String = JSONArray(value).toString()
}

@Dao
interface CatalogDao {
    @Query("SELECT COUNT(*) FROM catalog") suspend fun count(): Int
    @Query("SELECT * FROM catalog WHERE archived = 0 ORDER BY name")
    fun observeEntries(): Flow<List<CatalogEntryEntity>>
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
