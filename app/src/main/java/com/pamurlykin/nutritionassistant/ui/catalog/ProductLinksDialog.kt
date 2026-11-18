package com.pamurlykin.nutritionassistant.ui.catalog

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.nutritionassistant.data.local.CatalogItem
import com.pamurlykin.nutritionassistant.data.local.ProductLinkEntity
import com.pamurlykin.nutritionassistant.data.model.ProductLinkDraft
import com.pamurlykin.nutritionassistant.data.model.productLinkUrl
import com.pamurlykin.nutritionassistant.ui.components.*
import com.pamurlykin.nutritionassistant.ui.daily.DailyStatsViewModel
import java.time.LocalDate

@Composable
internal fun ProductLinksDialog(product: CatalogItem, viewModel: DailyStatsViewModel, busy: Boolean, onClose: () -> Unit) {
    val flow = remember(product.id, viewModel) { viewModel.observeProductLinks(product.id) }
    val links by flow.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    ProductLinksContent(product.displayName, links, busy, onClose,
        onSave = { id, draft, done -> viewModel.saveProductLink(product.id, id, draft, done) },
        onDelete = { viewModel.deleteProductLink(product.id, it) },
        onOpen = { address -> context.startActivity(productBrowserIntent(address)) },
    )
}

internal fun productBrowserIntent(url: String): Intent =
    Intent(Intent.ACTION_VIEW, productLinkUrl(url).toString().toUri()).apply {
        selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
    }

@Composable
internal fun ProductLinksContent(
    productName: String,
    links: List<ProductLinkEntity>?,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: (Long?, ProductLinkDraft, () -> Unit) -> Unit,
    onDelete: (Long) -> Unit,
    onOpen: (String) -> Unit,
) {
    var panel by rememberSaveable { mutableStateOf("list") }
    var linkId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openError by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = links?.find { it.id == linkId }
    val toList = { panel = "list"; linkId = null; openError = null }
    val pick: (String, Long) -> Unit = { nextPanel, id ->
        linkId = id
        panel = nextPanel
    }
    when {
        panel == "add" || panel == "edit" && selected != null -> ProductLinkEditor(selected, busy, toList) { draft ->
            onSave(selected?.id, draft, toList)
        }
        panel == "delete" && selected != null -> ConfirmActionDialog(
            "Удалить ссылку?", "Закладка «${selected.source}» будет удалена из карточки продукта.", "Удалить",
            toList, { onDelete(selected.id); toList() },
        )
        panel == "open" && selected != null -> {
            val address = runCatching { productLinkUrl(selected.url) }.getOrNull()
            AppDialog(title = { Text("Открыть внешний сайт?") }, onDismissRequest = toList,
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(address?.host ?: "Адрес недоступен", style = MaterialTheme.typography.titleMedium)
                        address?.let { Text(it.toString()) }
                        Text("Страница откроется в браузере. Перед открытием проверьте домен.")
                        openError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(enabled = address != null && !busy, onClick = {
                        try {
                            onOpen(requireNotNull(address).toString())
                            toList()
                        } catch (_: ActivityNotFoundException) {
                            openError = "Android не нашёл браузер для открытия ссылки."
                        } catch (_: SecurityException) {
                            openError = "Android не разрешил открыть ссылку."
                        }
                    }, modifier = Modifier.testTag("product-link-open-confirm")) { Text("Открыть в браузере") }
                },
            )
        }
        else -> AppDialog(title = { Text("Ссылки на товар") }, onDismissRequest = onClose, busy = busy,
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp).testTag("product-links"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(productName, style = MaterialTheme.typography.titleMedium)
                        Text("Личные закладки. Страница товара может измениться. Проверяйте этикетку перед уточнением состава.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (links == null) item { CircularProgressIndicator() }
                    else {
                        if (links.isEmpty()) item { Text("Ссылок пока нет.") }
                        items(links, key = { it.id }) { link ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(link.source, style = MaterialTheme.typography.titleMedium)
                                val host = runCatching { productLinkUrl(link.url).host }.getOrNull()
                                Text(host ?: "Адрес недоступен", style = MaterialTheme.typography.bodySmall)
                                Text(link.checkedOn?.let { "Дата проверки страницы: $it" } ?: "Дата проверки страницы не указана.",
                                    style = MaterialTheme.typography.bodySmall)
                                FlowRow {
                                    TextButton(enabled = host != null && !busy, onClick = { pick("open", link.id) },
                                        modifier = Modifier.testTag("product-link-open-${link.id}")) { Text("Открыть") }
                                    TextButton(enabled = !busy, onClick = { pick("edit", link.id) }) { Text("Изменить ссылку") }
                                    TextButton(enabled = !busy, onClick = { pick("delete", link.id) }) { Text("Удалить ссылку") }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = links != null && !busy, onClick = { panel = "add"; linkId = null },
                    modifier = Modifier.testTag("product-link-add")) { Text("Добавить ссылку") }
            },
        )
    }
}

@Composable
private fun ProductLinkEditor(link: ProductLinkEntity?, busy: Boolean, onClose: () -> Unit, onSave: (ProductLinkDraft) -> Unit) {
    var url by rememberSaveable { mutableStateOf(link?.url.orEmpty()) }
    var source by rememberSaveable { mutableStateOf(link?.source.orEmpty()) }
    var checkedOn by rememberSaveable { mutableStateOf(link?.checkedOn) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    val candidate = ProductLinkDraft(url, source, checkedOn)
    val validated = remember(candidate) { runCatching { candidate.validated() } }
    val initial = ProductLinkDraft(link?.url.orEmpty(), link?.source.orEmpty(), link?.checkedOn)
    if (choosingDate) {
        AppDatePicker(checkedOn?.let(LocalDate::parse) ?: LocalDate.now(), { choosingDate = false }) { checkedOn = it.toString() }
        return
    }
    AppDialog(title = { Text(if (link == null) "Добавить ссылку" else "Изменить ссылку") },
        onDismissRequest = onClose, busy = busy, hasChanges = candidate != initial,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(url, { address ->
                    if (address != url) checkedOn = null
                    url = address
                }, label = { Text("HTTPS-адрес товара") }, modifier = Modifier.testTag("product-link-url"),
                    enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                AppTextField(source, { source = it }, label = { Text("Название источника (необязательно)") },
                    modifier = Modifier.testTag("product-link-source"), enabled = !busy,
                    supportingText = { Text("Если поле пустое, будет показан домен.") })
                Text("Дата проверки страницы: " + (checkedOn ?: "не указана"))
                FlowRow {
                    TextButton(enabled = !busy && url.isNotBlank(), onClick = { choosingDate = true }) { Text("Указать дату проверки") }
                    if (checkedOn != null) TextButton(enabled = !busy, onClick = { checkedOn = null }) { Text("Очистить дату") }
                }
                if (url.isNotBlank()) validated.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Храните обычный адрес товара без личных данных.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = validated.isSuccess && !busy, onClick = { onSave(validated.getOrThrow()) },
                modifier = Modifier.testTag("product-link-save")) { Text("Сохранить ссылку") }
        },
    )
}
