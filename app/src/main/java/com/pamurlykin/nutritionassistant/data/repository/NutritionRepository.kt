package com.pamurlykin.nutritionassistant.data.repository

import androidx.room.withTransaction
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NutritionRepository(private val database: NutritionDatabase) {
    private val users = database.userDao()
    private val meals = database.mealDao()
    private val medicines = database.medicineDao()
    private val stools = database.stoolDao()
    private val feelings = database.feelingDao()
    private val water = database.waterDao()
    private val sleeps = database.sleepDao()
    private val catalog = database.catalogDao()
    private val dayStatus = database.dayStatusDao()
    val foodCatalog = FoodCatalog(database)
    private val initialization = Mutex()
    private var catalogInitialized = false

    suspend fun ensureUser(userId: Long = LocalUserDefaults.USER_ID) {
        users.insert(UserEntity(userId = userId))
    }

    suspend fun initialize() = initialization.withLock {
        database.withTransaction {
            ensureUser()
            if (!catalogInitialized) initializeCatalog()
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
        catalogInitialized = true
    }

    private suspend fun initializeCatalog() {
        val newCatalog = catalog.count() == 0
        BuiltinCatalog.install(database)
        if (newCatalog) {
            listOf("Йогурт", "Творог", "Хлеб", "Готовый сэндвич").forEach { product(it) }
            listOf(
                Triple("Овсяная каша", FoodType.HOME, listOf("Овсяные хлопья", "Вода")),
                Triple("Омлет", FoodType.HOME, listOf("Яйцо", "Молоко")),
                Triple("Гречка с курицей", FoodType.HOME, listOf("Гречка", "Курица", "Вода")),
                Triple("Овощной салат", FoodType.HOME, listOf("Помидор", "Огурец", "Растительное масло")),
                Triple("Суп", FoodType.RESTAURANT, emptyList()),
                Triple("Пицца", FoodType.RESTAURANT, emptyList()),
                Triple("Паста", FoodType.RESTAURANT, emptyList()),
                Triple("Салат", FoodType.RESTAURANT, emptyList()),
            ).forEach { (name, type, ingredients) ->
                val id = catalog.insert(CatalogEntryEntity(kind = CatalogKind.DISH, name = name,
                    normalizedName = normalizeName(name), foodType = type.storageValue,
                    publicId = builtinId("dish:" + when (name) { "Овсяная каша" -> "oat_porridge"; "Омлет" -> "omelet"; "Гречка с курицей" -> "buckwheat_chicken"; "Овощной салат" -> "vegetable_salad"; "Суп" -> "soup"; "Пицца" -> "pizza"; "Паста" -> "pasta"; else -> "salad" }), compositionState = if (ingredients.isEmpty()) "unknown" else "partial",
                    comment = "Шаблон рецепта: проверьте продукты и количества для своего блюда."))
                catalog.insertProducts(ingredients.mapIndexed { position, name -> DishProductEntity(id, product(name).id, position = position) })
            }
        }
    }

    private suspend fun product(name: String): CatalogEntryEntity {
        catalog.find(CatalogKind.PRODUCT, "", normalizeName(name))?.let { return it }
        val entry = CatalogEntryEntity(kind = CatalogKind.PRODUCT, name = name, normalizedName = normalizeName(name))
        return entry.copy(id = catalog.insert(entry))
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

    fun observeCatalog(): Flow<List<CatalogItem>> = flow {
        initialize()
        emitAll(catalog.observeEntries())
    }

    fun observeCatalogGraph(): Flow<List<CatalogItem>> = flow { initialize(); emitAll(catalog.observeAllItems()) }
    fun observeSeedConflicts(): Flow<List<SeedConflict>> = flow {
        initialize()
        emitAll(combine(catalog.observeAllItems(), catalog.observeAliases()) { entries, aliases -> BuiltinCatalog.pending(entries, aliases) })
    }
    suspend fun resolveSeed(key: String, targetId: Long?) = BuiltinCatalog.resolve(database, key, targetId)
    suspend fun setFavorite(id: Long, value: Boolean) = catalog.setFavorite(id, value)
    fun observeRecentCatalogIds() = catalog.observeRecentIds()
    fun observeRecentMeals() = catalog.observeRecentMeals()

    suspend fun saveCatalogEntry(id: Long?, kind: String, name: String, type: String, productIds: List<Long>): CatalogItem =
        foodCatalog.saveBasic(id, kind, name, type, productIds)

    suspend fun archiveCatalogEntry(id: Long) = database.withTransaction {
        val old = requireNotNull(catalog.get(id))
        require(old.revision < Int.MAX_VALUE) { "Нельзя увеличить версию записи." }
        catalog.archive(id)
    }

    suspend fun restoreCatalogEntry(id: Long) = database.withTransaction {
        val old = requireNotNull(catalog.get(id)); require(old.revision < Int.MAX_VALUE)
        catalog.update(old.copy(archived = false, revision = old.revision + 1))
    }

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
            feelings.observeForDay(userId, date), water.observeForDay(userId, date), sleeps.observeForDay(userId, date), dayStatus.observe(userId, date)) { args ->
            val allMeals = args[1] as List<MealWithItems>
            val allStools = args[3] as List<StoolEntity>
            DailyStatsSnapshot(date, args[0] as UserEntity, allMeals.filter { it.event.date == date },
                (args[2] as List<MedicineWithItems>).filter { it.event.date == date },
                allStools.filter { it.date == date }, args[4] as List<FeelingEntity>,
                (args[5] as WaterEntity?)?.glassesCount ?: 0, args[6] as SleepEntity?,
                StoolAnalyzer.analyze(date, allMeals, allStools), args[7] as DayStatusEntity?)
        })
    }

    suspend fun saveMeal(date: String, type: MealType, id: Long?, catalogIds: List<Long>,
                         context: EventContext? = null, portions: Map<Long, Portion> = emptyMap(),
                         comments: Map<Long, String?> = emptyMap(), refreshComposition: Set<Long> = emptySet(),
                         sourceMealId: Long? = null, expectedCompositionHashes: Map<Long, String> = emptyMap()) = database.withTransaction {
        validateDay(date)
        require(catalogIds.isNotEmpty()) { "Выберите хотя бы одно блюдо или продукт." }
        val ids = catalogIds.distinct()
        require((portions.keys + comments.keys + refreshComposition).all { it in ids })
        val existing = id?.let { requireNotNull(meals.getById(it)) }
        if (existing != null) require(existing.userId == LocalUserDefaults.USER_ID && existing.date == date)
        require(id == null || sourceMealId == null)
        val source = sourceMealId?.let { requireNotNull(meals.getById(it)) }
        require(source == null || source.userId == LocalUserDefaults.USER_ID)
        val oldItems = existing?.let { catalog.mealItems(it.id) } ?: source?.let { sourceEvent ->
            catalog.mealItems(sourceEvent.id).map { it.copy(id = 0, publicId = java.util.UUID.randomUUID().toString()) }
        }.orEmpty()
        val toCapture = ids.filter { selected -> oldItems.none { it.catalogId == selected } || selected in refreshComposition }
        val snapshots = foodCatalog.snapshots(toCapture)
        toCapture.forEach { key -> expectedCompositionHashes[key]?.let { expected ->
            require(expected == compositionFingerprint(CompositionSnapshot.encode(snapshots.getValue(key)))) { "Состав изменился после выбора. Обновите позицию и проверьте её заново." }
        } }
        val selected = ids.map { catalogId ->
            val old = oldItems.find { it.catalogId == catalogId }
            val item = if (old != null && catalogId !in refreshComposition) old.copy(id = 0) else {
                val entry = requireNotNull(catalog.get(catalogId))
                require(!entry.archived || old != null) { "Блюдо или продукт убраны из справочника." }
                val snapshot = snapshots.getValue(catalogId)
                MealItemEntity(mealId = 0, catalogId = catalogId, name = entry.name, foodType = entry.foodType,
                    ingredients = snapshot.ingredientNames(), kind = entry.kind,
                    sourceRevision = entry.revision, compositionSnapshot = CompositionSnapshot.encode(snapshot),
                    publicId = old?.publicId ?: java.util.UUID.randomUUID().toString(),
                    quantity = old?.quantity, unit = old?.unit, comment = old?.comment)
            }
            val amount = (portions[catalogId] ?: Portion(item.quantity, item.unit)).validated()
            item.copy(quantity = amount.quantity, unit = amount.unit,
                comment = if (catalogId in comments) cleanComment(comments[catalogId]) else item.comment)
        }
        val expandedSize = selected.sumOf { item -> item.compositionSnapshot?.let { CompositionSnapshot.decode(it).nodeCount() } ?: 1L }
        require(expandedSize <= CompositionGraph.MAX_NODES) { "Приём пищи содержит более 10 000 узлов состава." }
        val details = (context ?: EventContext(existing?.let { RecordedTime(it.actualTime, it.utcOffset, it.timeZone) } ?: RecordedTime(), existing?.comment)).validated(date)
        val event = (existing ?: MealEntity(userId = LocalUserDefaults.USER_ID, date = date, mealType = type.storageValue, description = "")).copy(
            mealType = type.storageValue, description = selected.joinToString("; ") { it.name }, updatedAt = StorageFormats.nowTimestamp(),
            actualTime = details.recordedTime.time, utcOffset = details.recordedTime.utcOffset, timeZone = details.recordedTime.timeZone,
            timePrecision = details.recordedTime.precision, comment = details.comment)
        val eventId = if (existing == null) meals.insert(event) else existing.id.also { meals.update(event) }
        catalog.clearMeal(eventId)
        catalog.insertMealItems(selected.map { it.copy(mealId = eventId) })
        dayStatus.invalidateFood(LocalUserDefaults.USER_ID, date, StorageFormats.nowTimestamp())
    }

    suspend fun saveMedicines(date: String, id: Long?, selections: List<MedicineSelection>, context: EventContext? = null) = database.withTransaction {
        validateDay(date)
        require(selections.isNotEmpty()) { "Выберите хотя бы одно лекарство." }
        val existing = id?.let { requireNotNull(medicines.getById(it)) }
        if (existing != null) require(existing.userId == LocalUserDefaults.USER_ID && existing.date == date)
        val oldItems = existing?.let { catalog.medicineItems(it.id) }.orEmpty()
        val selected = selections.distinctBy { it.catalogId }.map { selection ->
            val old = oldItems.find { it.catalogId == selection.catalogId }
            val entry = requireNotNull(catalog.get(selection.catalogId))
            require(entry.kind == CatalogKind.MEDICINE && (!entry.archived || old != null)) { "Лекарство удалено из справочника." }
            val dosage = cleanComment(selection.dosage)
            val amount = (selection.portion ?: Portion(old?.quantity, old?.unit)).validated()
            (old ?: MedicineItemEntity(medicineId = 0, catalogId = entry.id, name = entry.name, dosage = null, sourceRevision = entry.revision))
                .copy(id = 0, dosage = dosage, quantity = amount.quantity, unit = amount.unit,
                    comment = if (selection.updateComment) cleanComment(selection.comment) else old?.comment)
        }
        val details = (context ?: EventContext(existing?.let { RecordedTime(it.actualTime, it.utcOffset, it.timeZone) } ?: RecordedTime(), existing?.comment)).validated(date)
        val event = (existing ?: MedicineEntity(userId = LocalUserDefaults.USER_ID, date = date, name = "", dosage = null)).copy(
            name = selected.joinToString("; ") { it.name }, updatedAt = StorageFormats.nowTimestamp(),
            actualTime = details.recordedTime.time, utcOffset = details.recordedTime.utcOffset, timeZone = details.recordedTime.timeZone,
            timePrecision = details.recordedTime.precision, comment = details.comment)
        val eventId = if (existing == null) medicines.insert(event) else existing.id.also { medicines.update(event) }
        catalog.clearMedicine(eventId)
        catalog.insertMedicineItems(selected.map { it.copy(medicineId = eventId) })
    }

    suspend fun deleteMeal(userId: Long, id: Long) = database.withTransaction {
        val old = meals.getById(id)
        meals.deleteById(userId, id)
        if (old?.userId == userId) dayStatus.invalidateFood(userId, old.date, StorageFormats.nowTimestamp())
    }
    suspend fun deleteMedicine(userId: Long, id: Long) = medicines.deleteById(userId, id)
    suspend fun addStool(userId: Long, date: String, quality: Int, context: EventContext = EventContext()) = database.withTransaction {
        val details = context.validated(date)
        stools.insert(StoolEntity(userId = userId, date = date, quality = validateStoolQuality(quality),
            actualTime = details.recordedTime.time, utcOffset = details.recordedTime.utcOffset, timeZone = details.recordedTime.timeZone,
            timePrecision = details.recordedTime.precision, comment = details.comment))
        dayStatus.invalidateSymptoms(userId, date, StorageFormats.nowTimestamp())
    }
    suspend fun updateStool(userId: Long, id: Long, quality: Int, context: EventContext? = null) = database.withTransaction {
        val entry = requireNotNull(stools.getById(id))
        require(entry.userId == userId)
        val details = (context ?: EventContext(RecordedTime(entry.actualTime, entry.utcOffset, entry.timeZone), entry.comment)).validated(entry.date)
        stools.update(entry.copy(quality = validateStoolQuality(quality), updatedAt = StorageFormats.nowTimestamp(),
            actualTime = details.recordedTime.time, utcOffset = details.recordedTime.utcOffset, timeZone = details.recordedTime.timeZone,
            timePrecision = details.recordedTime.precision, comment = details.comment))
        dayStatus.invalidateSymptoms(userId, entry.date, StorageFormats.nowTimestamp())
    }
    suspend fun deleteStool(userId: Long, id: Long) = database.withTransaction {
        val old = stools.getById(id)
        stools.deleteById(userId, id)
        if (old?.userId == userId) dayStatus.invalidateSymptoms(userId, old.date, StorageFormats.nowTimestamp())
    }
    suspend fun confirmDay(userId: Long, date: String, foodComplete: Boolean, symptomsComplete: Boolean, stoolSummary: String?) = database.withTransaction {
        validateDay(date)
        require(stoolSummary in setOf(null, "no_diarrhea", "diarrhea", "no_stool"))
        require(if (symptomsComplete) stoolSummary != null else stoolSummary == null)
        val entries = stools.getForDay(userId, date)
        if (symptomsComplete) {
            require(stoolSummary != "no_stool" || entries.isEmpty())
            require(stoolSummary != "no_diarrhea" || entries.none { it.quality in 6..7 })
            require(stoolSummary != "diarrhea" || entries.any { it.quality in 6..7 })
        }
        val old = dayStatus.get(userId, date)
        val value = (old ?: DayStatusEntity(userId = userId, date = date)).copy(foodComplete = foodComplete,
            symptomsComplete = symptomsComplete, stoolSummary = stoolSummary, updatedAt = StorageFormats.nowTimestamp())
        if (old == null) dayStatus.insert(value) else dayStatus.update(value)
    }
    suspend fun addFeeling(userId: Long, date: String, text: String) = database.withTransaction {
        validateDay(date)
        feelings.insert(FeelingEntity(userId = userId, date = date, description = validateText(text)))
        dayStatus.invalidateSymptoms(userId, date, StorageFormats.nowTimestamp())
    }
    suspend fun updateFeeling(userId: Long, id: Long, text: String) = database.withTransaction {
        val entry = requireNotNull(feelings.getById(id))
        require(entry.userId == userId)
        feelings.update(entry.copy(description = validateText(text), updatedAt = StorageFormats.nowTimestamp()))
        dayStatus.invalidateSymptoms(userId, entry.date, StorageFormats.nowTimestamp())
    }
    suspend fun deleteFeeling(userId: Long, id: Long) = database.withTransaction {
        val old = feelings.getById(id)
        feelings.deleteById(userId, id)
        if (old?.userId == userId) dayStatus.invalidateSymptoms(userId, old.date, StorageFormats.nowTimestamp())
    }
    suspend fun setWaterForDay(userId: Long, date: String, count: Int) = database.withTransaction {
        require(count >= 0)
        val old = water.getForDay(userId, date)
        water.upsert(WaterEntity(id = old?.id ?: 0, userId = userId, date = date, glassesCount = count,
            createdAt = old?.createdAt ?: StorageFormats.nowTimestamp(),
            publicId = old?.publicId ?: java.util.UUID.randomUUID().toString()))
    }
    suspend fun incrementWater(userId: Long, date: String, delta: Int) = database.withTransaction {
        val count = water.getForDay(userId, date)?.glassesCount ?: 0
        setWaterForDay(userId, date, (count + delta).coerceAtLeast(0))
    }
    suspend fun upsertSleep(userId: Long, date: String, wakeupTime: String, bedTime: String, qualityDescription: String?, period: SleepPeriod? = null) = database.withTransaction {
        validateDay(date)
        val wakeup = validateTime(wakeupTime); val bed = validateTime(bedTime)
        val old = sleeps.getForDay(userId, date)
        // Legacy nights retain unknown dates; new records refer to the night ending on the selected day.
        val actualPeriod = period ?: if (old == null) SleepPeriod(
            if (bed >= wakeup) validateDay(date).minusDays(1).toString() else date, date)
            else old.bedDate?.let { SleepPeriod(it, requireNotNull(old.wakeupDate)) }
        actualPeriod?.validate(bed, wakeup)
        require(actualPeriod == null || actualPeriod.wakeupDate == date)
        sleeps.upsert((old ?: SleepEntity(userId = userId, date = date, wakeupTime = wakeup, bedTime = bed)).copy(
            wakeupTime = wakeup, bedTime = bed, qualityDescription = cleanComment(qualityDescription),
            bedDate = actualPeriod?.bedDate, wakeupDate = actualPeriod?.wakeupDate, updatedAt = StorageFormats.nowTimestamp()))
    }
    suspend fun deleteSleep(userId: Long, date: String) = sleeps.deleteForDay(userId, date)
    suspend fun markNotificationSent(userId: Long, type: String, date: String): Boolean =
        database.notificationLogDao().insert(NotificationLogEntity(userId = userId, type = type, date = date)) != -1L
}
