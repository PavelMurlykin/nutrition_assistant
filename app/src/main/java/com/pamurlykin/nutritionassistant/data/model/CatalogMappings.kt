package com.pamurlykin.nutritionassistant.data.model

import com.pamurlykin.nutritionassistant.data.local.CatalogEntryEntity

fun CatalogEntryEntity.details(): CatalogDetails = CatalogDetails(categoryCode, productKind, brand, variantLabel,
    compositionState, compositionSource, compositionCheckedOn, comment)
