package com.pamurlykin.nutritionassistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    fun observeUser(userId: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    suspend fun getUser(userId: Long): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE id NOT IN (SELECT meal_id FROM meal_items)")
    suspend fun withoutItems(): List<MealEntity>
    @Insert
    suspend fun insert(meal: MealEntity): Long

    @Update
    suspend fun update(meal: MealEntity)

    @Query("DELETE FROM meals WHERE id = :mealId AND user_id = :userId")
    suspend fun deleteById(userId: Long, mealId: Long)

    @Query("SELECT * FROM meals WHERE id = :mealId LIMIT 1")
    suspend fun getById(mealId: Long): MealEntity?

    @Query(
        "SELECT * FROM meals WHERE user_id = :userId AND date = :date ORDER BY created_at",
    )
    fun observeForDay(userId: Long, date: String): Flow<List<MealEntity>>

    @Query(
        """
        SELECT * FROM meals
        WHERE user_id = :userId AND date = :date AND meal_type = :mealType
        ORDER BY created_at
        LIMIT 1
        """,
    )
    suspend fun getPrimaryMeal(userId: Long, date: String, mealType: String): MealEntity?
}

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines WHERE id NOT IN (SELECT medicine_id FROM medicine_items)")
    suspend fun withoutItems(): List<MedicineEntity>
    @Insert
    suspend fun insert(medicine: MedicineEntity): Long

    @Update
    suspend fun update(medicine: MedicineEntity)

    @Query("DELETE FROM medicines WHERE id = :medicineId AND user_id = :userId")
    suspend fun deleteById(userId: Long, medicineId: Long)

    @Query("SELECT * FROM medicines WHERE id = :medicineId LIMIT 1")
    suspend fun getById(medicineId: Long): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE user_id = :userId AND date = :date ORDER BY created_at")
    fun observeForDay(userId: Long, date: String): Flow<List<MedicineEntity>>
}

@Dao
interface StoolDao {
    @Query("SELECT * FROM stools WHERE user_id = :userId AND date = :date ORDER BY id")
    suspend fun getForDay(userId: Long, date: String): List<StoolEntity>
    @Query("SELECT * FROM stools WHERE user_id = :userId ORDER BY date, id")
    fun observeAll(userId: Long): Flow<List<StoolEntity>>
    @Insert
    suspend fun insert(stool: StoolEntity): Long

    @Update
    suspend fun update(stool: StoolEntity)

    @Query("DELETE FROM stools WHERE id = :stoolId AND user_id = :userId")
    suspend fun deleteById(userId: Long, stoolId: Long)

    @Query("SELECT * FROM stools WHERE id = :stoolId LIMIT 1")
    suspend fun getById(stoolId: Long): StoolEntity?

    @Query("SELECT * FROM stools WHERE user_id = :userId AND date = :date ORDER BY created_at")
    fun observeForDay(userId: Long, date: String): Flow<List<StoolEntity>>
}

@Dao
interface FeelingDao {
    @Insert
    suspend fun insert(feeling: FeelingEntity): Long

    @Update
    suspend fun update(feeling: FeelingEntity)

    @Query("DELETE FROM feelings WHERE id = :feelingId AND user_id = :userId")
    suspend fun deleteById(userId: Long, feelingId: Long)

    @Query("SELECT * FROM feelings WHERE id = :feelingId LIMIT 1")
    suspend fun getById(feelingId: Long): FeelingEntity?

    @Query("SELECT * FROM feelings WHERE user_id = :userId AND date = :date ORDER BY created_at")
    fun observeForDay(userId: Long, date: String): Flow<List<FeelingEntity>>
}

@Dao
interface WaterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(water: WaterEntity): Long

    @Query("SELECT * FROM water WHERE user_id = :userId AND date = :date LIMIT 1")
    suspend fun getForDay(userId: Long, date: String): WaterEntity?

    @Query("SELECT * FROM water WHERE user_id = :userId AND date = :date LIMIT 1")
    fun observeForDay(userId: Long, date: String): Flow<WaterEntity?>
}

@Dao
interface SleepDao {
    @Query("DELETE FROM sleeps WHERE user_id = :userId AND date = :date")
    suspend fun deleteForDay(userId: Long, date: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sleep: SleepEntity): Long

    @Query("SELECT * FROM sleeps WHERE user_id = :userId AND date = :date LIMIT 1")
    suspend fun getForDay(userId: Long, date: String): SleepEntity?

    @Query("SELECT * FROM sleeps WHERE user_id = :userId AND date = :date LIMIT 1")
    fun observeForDay(userId: Long, date: String): Flow<SleepEntity?>
}

@Dao
interface NotificationLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: NotificationLogEntity): Long

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM notifications_log
            WHERE user_id = :userId AND type = :type AND date = :date
        )
        """,
    )
    suspend fun isSent(userId: Long, type: String, date: String): Boolean
}
