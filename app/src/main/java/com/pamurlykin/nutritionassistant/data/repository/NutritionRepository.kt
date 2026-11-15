package com.pamurlykin.nutritionassistant.data.repository

import androidx.room.withTransaction
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.util.*
import kotlinx.coroutines.flow.*

class NutritionRepository(private val database: NutritionDatabase) {
    private val users = database.userDao()
    private val meals = database.mealDao()
    private val medicines = database.medicineDao()
    private val stools = database.stoolDao()
    private val feelings = database.feelingDao()
    private val water = database.waterDao()
    private val sleeps = database.sleepDao()
    private val catalog = database.catalogDao()

    suspend fun ensureUser(userId: Long = LocalUserDefaults.USER_ID) {
        users.insert(UserEntity(userId = userId))
    }

    suspend fun initialize() = database.withTransaction {
        ensureUser()
        if (catalog.count() == 0) {
            listOf(
                Triple("Овсяная каша", FoodType.HOME, listOf("Овсяные хлопья", "Вода")),
                Triple("Омлет", FoodType.HOME, listOf("Яйцо", "Молоко")),
                Triple("Гречка с курицей", FoodType.HOME, listOf("Гречка", "Курица", "Вода")),
                Triple("Овощной салат", FoodType.HOME, listOf("Помидор", "Огурец", "Растительное масло")),
                Triple("Суп", FoodType.RESTAURANT, emptyList()),
                Triple("Пицца", FoodType.RESTAURANT, emptyList()),
                Triple("Паста", FoodType.RESTAURANT, emptyList()),
                Triple("Салат", FoodType.RESTAURANT, emptyList()),
                Triple("Йогурт", FoodType.STORE, emptyList()),
                Triple("Творог", FoodType.STORE, emptyList()),
                Triple("Хлеб", FoodType.STORE, emptyList()),
                Triple("Готовый сэндвич", FoodType.STORE, emptyList()),
            ).forEach { (name, type, ingredients) ->
                catalog.insert(CatalogEntryEntity(kind = CatalogKind.DISH, name = name,
                    normalizedName = normalizeName(name), foodType = type.storageValue, ingredients = ingredients))
            }
        }
        // Preserve old free-text records as one catalog item; never guess their type or ingredients.
        meals.withoutItems().forEach { event ->
            val entry = legacyEntry(CatalogKind.DISH, event.description.ifBlank { "Старая запись еды" }, "unknown")
            catalog.insertMealItems(listOf(MealItemEntity(mealId = event.id, catalogId = entry.id,
                name = entry.name, foodType = entry.foodType, ingredients = emptyList())))
        }
        medicines.withoutItems().forEach { event ->
            val entry = legacyEntry(CatalogKind.MEDICINE, event.name.ifBlank { "Старая запись лекарства" }, "")
            catalog.insertMedicineItems(listOf(MedicineItemEntity(medicineId = event.id, catalogId = entry.id,
                name = entry.name, dosage = event.dosage)))
        }
    }

    private suspend fun legacyEntry(kind: String, name: String, type: String): CatalogEntryEntity {
        catalog.find(kind, type, normalizeName(name))?.let { return it }
        val entry = CatalogEntryEntity(kind = kind, name = name, normalizedName = normalizeName(name), foodType = type)
        return entry.copy(id = catalog.insert(entry))
    }

    suspend fun getUser(userId: Long = LocalUserDefaults.USER_ID): UserEntity {
        ensureUser(userId)
        return requireNotNull(users.getUser(userId))
    }

    fun observeUser(): Flow<UserEntity> = flow {
        ensureUser()
        emitAll(users.observeUser(LocalUserDefaults.USER_ID).filterNotNull())
    }

    fun observeCatalog(): Flow<List<CatalogEntryEntity>> = flow {
        initialize()
        emitAll(catalog.observeEntries())
    }

    suspend fun saveCatalogEntry(id: Long?, kind: String, name: String, type: String, ingredients: List<String>): CatalogEntryEntity =
        database.withTransaction {
            require(kind == CatalogKind.DISH || kind == CatalogKind.MEDICINE)
            val cleanName = validateText(name)
            val cleanType = if (kind == CatalogKind.MEDICINE) "" else type.also {
                require(FoodType.entries.any { food -> food.storageValue == it }) { "Выберите тип еды." }
            }
            val cleanIngredients = if (kind == CatalogKind.DISH) ingredients.map(::validateText)
                .distinctBy(::normalizeName) else emptyList()
            require(cleanType != FoodType.HOME.storageValue || cleanIngredients.isNotEmpty()) { "Укажите ингредиенты домашнего блюда." }
            val duplicate = catalog.find(kind, cleanType, normalizeName(cleanName))
            if (id == null && duplicate != null) {
                if (duplicate.archived) catalog.update(duplicate.copy(archived = false))
                return@withTransaction duplicate.copy(archived = false)
            }
            require(duplicate == null || duplicate.id == id) { "Такое название уже есть в этом справочнике." }
            if (id != null) requireNotNull(catalog.get(id)) { "Запись не найдена." }
            val entry = CatalogEntryEntity(id = id ?: 0, kind = kind, name = cleanName,
                normalizedName = normalizeName(cleanName), foodType = cleanType, ingredients = cleanIngredients)
            if (id == null) entry.copy(id = catalog.insert(entry)) else entry.also { catalog.update(it) }
        }

    suspend fun archiveCatalogEntry(id: Long) = catalog.archive(id)

    fun observeCalendarCounts(start: String, end: String): Flow<List<CalendarDayCounts>> = flow {
        initialize()
        emitAll(catalog.observeCalendarCounts(LocalUserDefaults.USER_ID, start, end))
    }

    suspend fun updateScheduleSlot(userId: Long, slot: ScheduleSlot, time: String) = database.withTransaction {
        val user = getUser(userId)
        val value = validateTime(time)
        users.update(when (slot) {
            ScheduleSlot.BREAKFAST -> user.copy(breakfastTime = value)
            ScheduleSlot.LUNCH -> user.copy(lunchTime = value)
            ScheduleSlot.DINNER -> user.copy(dinnerTime = value)
            ScheduleSlot.TOILET -> user.copy(toiletTime = value)
        }.copy(updatedAt = StorageFormats.nowTimestamp()))
    }

    suspend fun setReminderEnabled(slot: ScheduleSlot, enabled: Boolean) = database.withTransaction {
        val user = getUser()
        users.update(when (slot) {
            ScheduleSlot.BREAKFAST -> user.copy(breakfastEnabled = enabled)
            ScheduleSlot.LUNCH -> user.copy(lunchEnabled = enabled)
            ScheduleSlot.DINNER -> user.copy(dinnerEnabled = enabled)
            ScheduleSlot.TOILET -> user.copy(toiletEnabled = enabled)
        }.copy(updatedAt = StorageFormats.nowTimestamp()))
    }

    @Suppress("UNCHECKED_CAST")
    fun observeDailyStats(userId: Long, date: String): Flow<DailyStatsSnapshot> = flow {
        initialize()
        val foodHistory = catalog.observeMeals(userId)
        val stoolHistory = stools.observeAll(userId)
        emitAll(combine(observeUser(), foodHistory, catalog.observeMedicines(userId), stoolHistory,
            feelings.observeForDay(userId, date), water.observeForDay(userId, date), sleeps.observeForDay(userId, date)) { args ->
            val allMeals = args[1] as List<MealWithItems>
            val allStools = args[3] as List<StoolEntity>
            DailyStatsSnapshot(date, args[0] as UserEntity, allMeals.filter { it.event.date == date },
                (args[2] as List<MedicineWithItems>).filter { it.event.date == date },
                allStools.filter { it.date == date }, args[4] as List<FeelingEntity>,
                (args[5] as WaterEntity?)?.glassesCount ?: 0, args[6] as SleepEntity?,
                StoolAnalyzer.analyze(date, allMeals, allStools))
        })
    }

    suspend fun saveMeal(date: String, type: MealType, id: Long?, catalogIds: List<Long>) = database.withTransaction {
        require(catalogIds.isNotEmpty()) { "Выберите хотя бы одно блюдо." }
        val existing = id?.let { requireNotNull(meals.getById(it)) }
            ?: if (type.isPrimary) meals.getPrimaryMeal(LocalUserDefaults.USER_ID, date, type.storageValue) else null
        if (existing != null) require(existing.userId == LocalUserDefaults.USER_ID && existing.date == date)
        val oldItems = existing?.let { catalog.mealItems(it.id) }.orEmpty()
        val selected = catalogIds.distinct().map { catalogId ->
            oldItems.find { it.catalogId == catalogId }?.copy(id = 0) ?: run {
                val entry = requireNotNull(catalog.get(catalogId))
                require(entry.kind == CatalogKind.DISH && !entry.archived) { "Блюдо удалено из справочника." }
                MealItemEntity(mealId = 0, catalogId = entry.id, name = entry.name, foodType = entry.foodType, ingredients = entry.ingredients)
            }
        }
        val event = MealEntity(id = existing?.id ?: 0, userId = LocalUserDefaults.USER_ID, date = date,
            mealType = type.storageValue, description = selected.joinToString("; ") { it.name },
            createdAt = existing?.createdAt ?: StorageFormats.nowTimestamp())
        val eventId = if (existing == null) meals.insert(event) else existing.id.also { meals.update(event) }
        catalog.clearMeal(eventId)
        catalog.insertMealItems(selected.map { it.copy(mealId = eventId) })
    }

    suspend fun saveMedicines(date: String, id: Long?, selections: List<MedicineSelection>) = database.withTransaction {
        require(selections.isNotEmpty()) { "Выберите хотя бы одно лекарство." }
        val existing = id?.let { requireNotNull(medicines.getById(it)) }
        if (existing != null) require(existing.userId == LocalUserDefaults.USER_ID && existing.date == date)
        val oldItems = existing?.let { catalog.medicineItems(it.id) }.orEmpty()
        val selected = selections.distinctBy { it.catalogId }.map { selection ->
            val old = oldItems.find { it.catalogId == selection.catalogId }
            val entry = requireNotNull(catalog.get(selection.catalogId))
            require(entry.kind == CatalogKind.MEDICINE && (!entry.archived || old != null)) { "Лекарство удалено из справочника." }
            val dosage = selection.dosage.trim().ifBlank { null }
            require((dosage?.length ?: 0) <= 1000) { "Слишком длинная дозировка." }
            MedicineItemEntity(medicineId = 0, catalogId = entry.id, name = old?.name ?: entry.name, dosage = dosage)
        }
        val event = MedicineEntity(id = existing?.id ?: 0, userId = LocalUserDefaults.USER_ID, date = date,
            name = selected.joinToString("; ") { it.name }, dosage = null, createdAt = existing?.createdAt ?: StorageFormats.nowTimestamp())
        val eventId = if (existing == null) medicines.insert(event) else existing.id.also { medicines.update(event) }
        catalog.clearMedicine(eventId)
        catalog.insertMedicineItems(selected.map { it.copy(medicineId = eventId) })
    }

    suspend fun deleteMeal(userId: Long, id: Long) = meals.deleteById(userId, id)
    suspend fun deleteMedicine(userId: Long, id: Long) = medicines.deleteById(userId, id)
    suspend fun addStool(userId: Long, date: String, quality: Int) {
        stools.insert(StoolEntity(userId = userId, date = date, quality = validateStoolQuality(quality)))
    }
    suspend fun updateStool(userId: Long, id: Long, quality: Int) {
        val entry = requireNotNull(stools.getById(id))
        require(entry.userId == userId)
        stools.update(entry.copy(quality = validateStoolQuality(quality), updatedAt = StorageFormats.nowTimestamp()))
    }
    suspend fun deleteStool(userId: Long, id: Long) = stools.deleteById(userId, id)
    suspend fun addFeeling(userId: Long, date: String, text: String) {
        feelings.insert(FeelingEntity(userId = userId, date = date, description = validateText(text)))
    }
    suspend fun updateFeeling(userId: Long, id: Long, text: String) {
        val entry = requireNotNull(feelings.getById(id))
        require(entry.userId == userId)
        feelings.update(entry.copy(description = validateText(text), updatedAt = StorageFormats.nowTimestamp()))
    }
    suspend fun deleteFeeling(userId: Long, id: Long) = feelings.deleteById(userId, id)
    suspend fun setWaterForDay(userId: Long, date: String, count: Int) = database.withTransaction {
        require(count >= 0)
        val old = water.getForDay(userId, date)
        water.upsert(WaterEntity(id = old?.id ?: 0, userId = userId, date = date, glassesCount = count,
            createdAt = old?.createdAt ?: StorageFormats.nowTimestamp()))
    }
    suspend fun incrementWater(userId: Long, date: String, delta: Int) = database.withTransaction {
        val count = water.getForDay(userId, date)?.glassesCount ?: 0
        setWaterForDay(userId, date, (count + delta).coerceAtLeast(0))
    }
    suspend fun upsertSleep(userId: Long, date: String, wakeupTime: String, bedTime: String, qualityDescription: String?) {
        val old = sleeps.getForDay(userId, date)
        sleeps.upsert(SleepEntity(id = old?.id ?: 0, userId = userId, date = date,
            wakeupTime = validateTime(wakeupTime), bedTime = validateTime(bedTime),
            qualityDescription = qualityDescription?.trim()?.ifBlank { null },
            createdAt = old?.createdAt ?: StorageFormats.nowTimestamp()))
    }
    suspend fun deleteSleep(userId: Long, date: String) = sleeps.deleteForDay(userId, date)
    suspend fun markNotificationSent(userId: Long, type: String, date: String): Boolean =
        database.notificationLogDao().insert(NotificationLogEntity(userId = userId, type = type, date = date)) != -1L
}
