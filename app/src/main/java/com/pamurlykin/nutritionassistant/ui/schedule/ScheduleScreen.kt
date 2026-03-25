package com.pamurlykin.nutritionassistant.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.nutritionassistant.data.model.ScheduleSlot
import com.pamurlykin.nutritionassistant.ui.components.CardHeadline
import com.pamurlykin.nutritionassistant.ui.components.NutritionCard
import com.pamurlykin.nutritionassistant.ui.components.showTimePicker

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val scheduleItems = listOf(
        ScheduleSlot.BREAKFAST to uiState.user.breakfastTime,
        ScheduleSlot.LUNCH to uiState.user.lunchTime,
        ScheduleSlot.DINNER to uiState.user.dinnerTime,
        ScheduleSlot.TOILET to uiState.user.toiletTime,
        ScheduleSlot.WAKEUP to uiState.user.wakeupTime,
        ScheduleSlot.BED to uiState.user.bedTime,
    )

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Расписание",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Настрой локальные напоминания по тем же слотам, что и в Telegram-боте.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.66f),
                )
            }
        }

        item {
            NutritionCard {
                CardHeadline(title = "Сон и утро")
                Text(
                    text = uiState.sleepQualityTime,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "Напоминание о качестве сна приходит через 30 минут после времени подъема.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                )
            }
        }

        items(scheduleItems) { (slot, time) ->
            NutritionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = slot.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (slot == ScheduleSlot.BREAKFAST) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            showTimePicker(context = context, initialTime = time) {
                                viewModel.updateTime(slot, it)
                            }
                        },
                    ) {
                        Text("Изменить")
                    }
                }
            }
        }

        item {
            NutritionCard(modifier = Modifier.padding(bottom = 20.dp)) {
                CardHeadline(title = "Как это работает")
                Text(
                    text = "Данные хранятся локально, а напоминания планируются на устройстве. " +
                        "Структура времен совпадает с таблицей users из бота, чтобы позже проще добавить синхронизацию.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
    }
}
