package com.pamurlykin.nutritionassistant.data.model

import java.net.URI
import java.util.Locale

private const val MAX_URL_LENGTH = 2048
private const val MAX_SOURCE_LENGTH = 120
private val dnsLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
private val encodedControl = Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE)

private fun Char.isUnsafeLinkCharacter() = isISOControl() || Character.getType(this) == Character.FORMAT.toInt()

/** Parse once for validation and canonicalize the scheme and ASCII host. Preserve the pasted path/query. */
fun productLinkUrl(value: String): URI {
    require(value.length <= MAX_URL_LENGTH && value.none { it.isUnsafeLinkCharacter() }) {
        "Введите HTTPS-ссылку длиной до 2048 символов."
    }
    val uri = try {
        URI(value.trim())
    } catch (_: java.net.URISyntaxException) {
        throw IllegalArgumentException("Введите полный HTTPS-адрес товара.")
    }
    val host = uri.host?.lowercase(Locale.ROOT)
    val labels = host.orEmpty().split('.')
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.isOpaque && uri.rawUserInfo == null &&
        host != null && host.length <= 253 && labels.size >= 2 && labels.all(dnsLabel::matches) &&
        labels.last().any { it in 'a'..'z' } && uri.port in listOf(-1, 443) &&
        uri.rawAuthority.equals(if (uri.port < 0) host else "$host:443", ignoreCase = true) &&
        !encodedControl.containsMatchIn(uri.toString())) {
        "Нужен HTTPS-адрес с доменным именем, без логина, пароля и нестандартного порта."
    }
    val address = buildString {
        append("https://").append(host)
        append(uri.rawPath.ifEmpty { "/" })
        uri.rawQuery?.let { append('?').append(it) }
        uri.rawFragment?.let { append('#').append(it) }
    }
    val ascii = URI(address).toASCIIString()
    require(ascii.length <= MAX_URL_LENGTH) { "Ссылка слишком длинная." }
    return URI(ascii)
}

data class ProductLinkDraft(val url: String, val source: String, val checkedOn: String?) {
    fun validated(): ProductLinkDraft {
        val address = productLinkUrl(url)
        val label = source.trim().ifEmpty { requireNotNull(address.host) }
        require(source.none { it.isUnsafeLinkCharacter() } && label.length <= MAX_SOURCE_LENGTH) {
            "Название источника — до 120 символов, одной строкой."
        }
        checkedOn?.let(::validateDay)
        return copy(url = address.toString(), source = label)
    }
}
