package com.pamurlykin.nutritionassistant.data.repository

import com.pamurlykin.nutritionassistant.data.local.FeelingEntity
import com.pamurlykin.nutritionassistant.data.local.MealEntity
import com.pamurlykin.nutritionassistant.data.local.MedicineEntity
import com.pamurlykin.nutritionassistant.data.local.NotificationLogEntity
import com.pamurlykin.nutritionassistant.data.local.NutritionDatabase
import com.pamurlykin.nutritionassistant.data.local.SleepEntity
import com.pamurlykin.nutritionassistant.data.local.StoolEntity
import com.pamurlykin.nutritionassistant.data.local.UserEntity
import com.pamurlykin.nutritionassistant.data.local.WaterEntity
import com.pamurlykin.nutritionassistant.data.model.DailyStatsSnapshot
import com.pamurlykin.nutritionassistant.data.model.LocalUserDefaults
import com.pamurlykin.nutritionassistant.data.model.MealType
import com.pamurlykin.nutritionassistant.data.model.ScheduleSlot
import com.pamurlykin.nutritionassistant.util.StorageFormats
import com.pamurlykin.nutritionassistant.util.validateStoolQuality
import com.pamurlykin.nutritionassistant.util.validateText
import com.pamurlykin.nutritionassistant.util.validateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

class NutritionRepository(
    private val database: NutritionDatabase,
) {
    private val userDao = database.userDao()
    private val mealDao = database.mealDao()
    private val medicineDao = database.medicineDao()
    private val stoolDao = database.stoolDao()
    private val feelingDao = database.feelingDao()
    private val waterDao = database.waterDao()
    private val sleepDao = database.sleepDao()
    private val notificationLogDao = database.notificationLogDao()

    suspend fun ensureUser(userId: Long = LocalUserDefaults.USER_ID) {
        if (userDao.getUser(userId) == null) {
            userDao.insert(UserEntity(userId = userId))
        }
    }

    suspend fun getUser(userId: Long = LocalUserDefaults.USER_ID): UserEntity {
        ensureUser(userId)
        return requireNotNull(userDao.getUser(userId))
    }

    suspend fun getAllUsers(): List<UserEntity> = userDao.getAllUsers()

    fun observeUser(userId: Long = LocalUserDefaults.USER_ID): Flow<UserEntity> = flow {
        ensureUser(userId)
        emitAll(userDao.observeUser(userId).filterNotNull())
    }

    suspend fun updateScheduleSlot(
        userId: Long,
        slot: ScheduleSlot,
        time: String,
    ) {
        val validatedTime = validateTime(time)
        val currentUser = getUser(userId)
        val updatedAt = StorageFormats.nowTimestamp()
        val updatedUser = when (slot) {
            ScheduleSlot.BREAKFAST -> currentUser.copy(breakfastTime = validatedTime, updatedAt = updatedAt)
            ScheduleSlot.LUNCH -> currentUser.copy(lunchTime = validatedTime, updatedAt = updatedAt)
            ScheduleSlot.DINNER -> currentUser.copy(dinnerTime = validatedTime, updatedAt = updatedAt)
            ScheduleSlot.TOILET -> currentUser.copy(toiletTime = validatedTime, updatedAt = updatedAt)
            ScheduleSlot.WAKEUP -> currentUser.copy(wakeupTime = validatedTime, updatedAt = updatedAt)
            ScheduleSlot.BED -> currentUser.copy(bedTime = validatedTime, updatedAt = updatedAt)
        }
        userDao.update(updatedUser)
    }

    suspend fun ensureSleepForDay(
        userId: Long,
        date: String,
    ) {
        ensureUser(userId)
        if (sleepDao.getForDay(userId, date) != null) {
            return
        }

        val user = getUser(userId)
        sleepDao.upsert(
            SleepEntity(
                userId = userId,
                date = date,
                wakeupTime = user.wakeupTime,
                bedTime = user.bedTime,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun observeDailyStats(
        userId: Long,
        date: String,
    ): Flow<DailyStatsSnapshot> = flow {
        ensureUser(userId)
        ensureSleepForDay(userId, date)
        emitAll(
            combine(
                observeUser(userId),
                mealDao.observeForDay(userId, date),
                medicineDao.observeForDay(userId, date),
                stoolDao.observeForDay(userId, date),
                feelingDao.observeForDay(userId, date),
                waterDao.observeForDay(userId, date),
                sleepDao.observeForDay(userId, date),
            ) { args ->
                buildDailySnapshot(
                    date = date,
                    user = args[0] as UserEntity,
                    meals = args[1] as List<MealEntity>,
                    medicines = args[2] as List<MedicineEntity>,
                    stools = args[3] as List<StoolEntity>,
                    feelings = args[4] as List<FeelingEntity>,
                    water = args[5] as WaterEntity?,
                    sleep = args[6] as SleepEntity?,
                )
            },
        )
    }

    suspend fun upsertMeal(
        userId: Long,
        date: String,
        mealType: MealType,
        description: String,
    ) {
        ensureUser(userId)
        val normalizedText = validateText(description)
        val now = StorageFormats.nowTimestamp()

        if (mealType.isPrimary) {
            val existing = mealDao.getPrimaryMeal(userId, date, mealType.storageValue)
            if (existing != null) {
                mealDao.update(existing.copy(description = normalizedText, updatedAt = now))
                return
            }
        }

        mealDao.insert(
            MealEntity(
                userId = userId,
                date = date,
                mealType = mealType.storageValue,
                description = normalizedText,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateMeal(
        userId: Long,
        mealId: Long,
        description: String,
    ) {
        val existing = requireNotNull(mealDao.getById(mealId))
        require(existing.userId == userId)
        mealDao.update(
            existing.copy(
                description = validateText(description),
                updatedAt = StorageFormats.nowTimestamp(),
            ),
        )
    }

    suspend fun deleteMeal(userId: Long, mealId: Long) {
        mealDao.deleteById(userId, mealId)
    }

    suspend fun addMedicine(
        userId: Long,
        date: String,
        name: String,
        dosage: String?,
    ) {
        ensureUser(userId)
        val now = StorageFormats.nowTimestamp()
        medicineDao.insert(
            MedicineEntity(
                userId = userId,
                date = date,
                name = validateText(name),
                dosage = dosage?.trim().orEmpty().ifBlank { null },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateMedicine(
        userId: Long,
        medicineId: Long,
        name: String,
        dosage: String?,
    ) {
        val existing = requireNotNull(medicineDao.getById(medicineId))
        require(existing.userId == userId)
        medicineDao.update(
            existing.copy(
                name = validateText(name),
                dosage = dosage?.trim().orEmpty().ifBlank { null },
                updatedAt = StorageFormats.nowTimestamp(),
            ),
        )
    }

    suspend fun deleteMedicine(userId: Long, medicineId: Long) {
        medicineDao.deleteById(userId, medicineId)
    }

    suspend fun addStool(
        userId: Long,
        date: String,
        quality: Int,
    ) {
        ensureUser(userId)
        val now = StorageFormats.nowTimestamp()
        stoolDao.insert(
            StoolEntity(
                userId = userId,
                date = date,
                quality = validateStoolQuality(quality),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateStool(
        userId: Long,
        stoolId: Long,
        quality: Int,
    ) {
        val existing = requireNotNull(stoolDao.getById(stoolId))
        require(existing.userId == userId)
        stoolDao.update(
            existing.copy(
                quality = validateStoolQuality(quality),
                updatedAt = StorageFormats.nowTimestamp(),
            ),
        )
    }

    suspend fun deleteStool(userId: Long, stoolId: Long) {
        stoolDao.deleteById(userId, stoolId)
    }

    suspend fun addFeeling(
        userId: Long,
        date: String,
        description: String,
    ) {
        ensureUser(userId)
        val now = StorageFormats.nowTimestamp()
        feelingDao.insert(
            FeelingEntity(
                userId = userId,
                date = date,
                description = validateText(description),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateFeeling(
        userId: Long,
        feelingId: Long,
        description: String,
    ) {
        val existing = requireNotNull(feelingDao.getById(feelingId))
        require(existing.userId == userId)
        feelingDao.update(
            existing.copy(
                description = validateText(description),
                updatedAt = StorageFormats.nowTimestamp(),
            ),
        )
    }

    suspend fun deleteFeeling(userId: Long, feelingId: Long) {
        feelingDao.deleteById(userId, feelingId)
    }

    suspend fun setWaterForDay(
        userId: Long,
        date: String,
        glassesCount: Int,
    ) {
        require(glassesCount >= 0) { "Количество воды не может быть отрицательным." }
        ensureUser(userId)
        val existing = waterDao.getForDay(userId, date)
        waterDao.upsert(
            WaterEntity(
                id = existing?.id ?: 0,
                userId = userId,
                date = date,
                glassesCount = glassesCount,
                createdAt = existing?.createdAt ?: StorageFormats.nowTimestamp(),
                updatedAt = StorageFormats.nowTimestamp(),
            ),
        )
    }

    suspend fun incrementWater(
        userId: Long,
        date: String,
        delta: Int,
    ) {
        val current = waterDao.getForDay(userId, date)?.glassesCount ?: 0
        setWaterForDay(userId, date, (current + delta).coerceAtLeast(0))
    }

    suspend fun upsertSleep(
        userId: Long,
        date: String,
        wakeupTime: String,
        bedTime: String,
        qualityDescription: String?,
    ) {
        ensureSleepForDay(userId, date)
        val existing = requireNotNull(sleepDao.getForDay(userId, date))
        sleepDao.upsert(
            existing.copy(
                wakeupTime = validateTime(wakeupTime),
                bedTime = validateTime(bedTime),
                qualityDescription = qualityDescription?.trim().orEmpty().ifBlank { null },
                updatedAt = StorageFormats.nowTimestamp(),
            ),
        )
    }

    suspend fun wasNotificationSent(
        userId: Long,
        type: String,
        date: String,
    ): Boolean = notificationLogDao.isSent(userId, type, date)

    suspend fun markNotificationSent(
        userId: Long,
        type: String,
        date: String,
    ): Boolean {
        val result = notificationLogDao.insert(
            NotificationLogEntity(
                userId = userId,
                type = type,
                date = date,
            ),
        )
        return result != -1L
    }

    private fun buildDailySnapshot(
        date: String,
        user: UserEntity,
        meals: List<MealEntity>,
        medicines: List<MedicineEntity>,
        stools: List<StoolEntity>,
        feelings: List<FeelingEntity>,
        water: WaterEntity?,
        sleep: SleepEntity?,
    ): DailyStatsSnapshot {
        val breakfast = meals.firstOrNull { it.mealType == MealType.BREAKFAST.storageValue }
        val lunch = meals.firstOrNull { it.mealType == MealType.LUNCH.storageValue }
        val dinner = meals.firstOrNull { it.mealType == MealType.DINNER.storageValue }
        val snacks = meals.filter { it.mealType == MealType.SNACK.storageValue }

        return DailyStatsSnapshot(
            date = date,
            user = user,
            breakfast = breakfast,
            lunch = lunch,
            dinner = dinner,
            snacks = snacks,
            medicines = medicines,
            stools = stools,
            feelings = feelings,
            waterCount = water?.glassesCount ?: 0,
            sleep = sleep ?: SleepEntity(
                userId = user.userId,
                date = date,
                wakeupTime = user.wakeupTime,
                bedTime = user.bedTime,
            ),
        )
    }
}
