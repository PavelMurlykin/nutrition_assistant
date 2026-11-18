package com.pamurlykin.nutritionassistant

import com.pamurlykin.nutritionassistant.data.model.ProductLinkDraft
import com.pamurlykin.nutritionassistant.data.model.productLinkUrl
import org.junit.Assert.*
import org.junit.Test

class ProductLinkUrlTest {
    @Test fun preservesProductParametersAndUsesHostAsDefaultLabel() {
        val draft = ProductLinkDraft(" HTTPS://Shop.Example:443/food/42?item=1%2F2#details ", "", "2026-11-18").validated()
        assertEquals("https://shop.example/food/42?item=1%2F2#details", draft.url)
        assertEquals("shop.example", draft.source)
        assertEquals("2026-11-18", draft.checkedOn)
        assertEquals("https://xn--e1afmkfd.xn--p1ai/", productLinkUrl("https://xn--e1afmkfd.xn--p1ai").toString())
    }

    @Test fun rejectsAmbiguousHostsAndAddressesThatAreNotHttpsProductLinks() {
        val invalid = listOf(
            "http://shop.example/42", "//shop.example/42", "file:///sdcard/test", "intent://shop.example/",
            "https://shop.example@different.example/42", "https://shop.example\\@different.example/",
            "https://shop.example:8888/42", "https://shop.example:/42", "https://shop.example./42",
            "https://127.1/42", "https://[::1]/42", "https://localhost/42", "https://shop.example/%0aabc",
            "https://shop.example/ab\u202Ecd", "https://shop.example/" + "x".repeat(2048),
        )
        invalid.forEach { assertTrue(it, runCatching { productLinkUrl(it) }.isFailure) }
        assertTrue(runCatching { ProductLinkDraft("https://shop.example", "Магазин\nвторая строка", null).validated() }.isFailure)
        assertTrue(runCatching { ProductLinkDraft("https://shop.example", "Магазин", "2026-02-30").validated() }.isFailure)
    }
}
