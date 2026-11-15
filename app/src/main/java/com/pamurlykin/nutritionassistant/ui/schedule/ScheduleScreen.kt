package com.pamurlykin.nutritionassistant.ui.schedule

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import com.pamurlykin.nutritionassistant.data.model.ScheduleSlot
import com.pamurlykin.nutritionassistant.notification.BuildCompat
import com.pamurlykin.nutritionassistant.ui.components.*

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Расписание", style = MaterialTheme.typography.headlineMedium)
            Text("Время ежедневных напоминаний", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        item {
            NutritionCard {
                Text(if (allowed) "Уведомления разрешены" else "Уведомления отключены")
                TextButton(onClick = {
                    if (BuildCompat.requiresNotificationPermission() && !allowed) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text(if (allowed) "Настройки уведомлений" else "Разрешить уведомления") }
                if (!allowed) TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Открыть настройки Android") }
            }
        }
        items(ScheduleSlot.entries) { slot ->
            NutritionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.title, style = MaterialTheme.typography.titleLarge)
                    Switch(checked = slot.enabled(user), onCheckedChange = { viewModel.setEnabled(slot, it) })
                }
                Text(slot.time(user), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = { showTimePicker(context, slot.time(user)) { viewModel.updateTime(slot, it) } }) { Text("Изменить время") }
            }
        }
        item {
            NutritionCard {
                Text("Работа напоминаний", style = MaterialTheme.typography.titleMedium)
                Text("Android может задерживать уведомления для экономии батареи. После перезагрузки или принудительной остановки откройте приложение, чтобы восстановить напоминания. Для автоматического восстановления потребовалось бы дополнительное разрешение.")
                Text("Все записи и справочники находятся на устройстве. Интернет и учетная запись не нужны.")
            }
        }
    }
}
