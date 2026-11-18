package com.pamurlykin.nutritionassistant.data.repository

import androidx.room.withTransaction
import com.pamurlykin.nutritionassistant.data.local.NutritionDatabase
import com.pamurlykin.nutritionassistant.data.local.ProductLinkEntity
import com.pamurlykin.nutritionassistant.data.model.CatalogKind
import com.pamurlykin.nutritionassistant.data.model.ProductLinkDraft
import com.pamurlykin.nutritionassistant.util.StorageFormats

class ProductLinkRepository(private val db: NutritionDatabase) {
    private val dao = db.productLinkDao()

    fun observe(catalogId: Long) = dao.observe(catalogId)

    suspend fun save(catalogId: Long, id: Long?, draft: ProductLinkDraft): Long = db.withTransaction {
        require(db.catalogDao().get(catalogId)?.kind == CatalogKind.PRODUCT) { "Выберите продукт." }
        val value = draft.validated()
        val original = id?.let { requireNotNull(dao.find(it, catalogId)) { "Ссылка больше недоступна." } }
        val duplicate = dao.findByUrl(catalogId, value.url)
        require(duplicate == null || duplicate.id == id) { "Этот адрес уже сохранён для продукта." }
        if (original == null) dao.insert(ProductLinkEntity(
            catalogId = catalogId, url = value.url, source = value.source, checkedOn = value.checkedOn,
        )) else {
            dao.update(original.copy(url = value.url, source = value.source, checkedOn = value.checkedOn,
                updatedAt = StorageFormats.nowTimestamp()))
            original.id
        }
    }

    suspend fun delete(catalogId: Long, id: Long) = db.withTransaction {
        val original = dao.find(id, catalogId) ?: return@withTransaction
        dao.forgetAliases(original.publicId)
        dao.delete(original)
    }
}
