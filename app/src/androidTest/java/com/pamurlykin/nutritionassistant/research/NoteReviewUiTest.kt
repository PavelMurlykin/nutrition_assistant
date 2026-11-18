package com.pamurlykin.nutritionassistant.research

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.pamurlykin.nutritionassistant.ui.components.NutritionCard
import com.pamurlykin.nutritionassistant.ui.theme.NutritionAssistantTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

private data class SyntheticNote(val id: String, val text: String)

@Composable
private fun ReviewDemo(note: SyntheticNote, reviewer: NoteReviewer, onAccepted: (Proposal) -> Unit) {
    var enabled by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf<Set<Proposal>>(emptySet()) }
    val result = remember(enabled, note, reviewer) { reviewer.inspect(note.id, note.text, enabled) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Эксперимент с комментариями", style = MaterialTheme.typography.titleLarge)
        Text(note.text, Modifier.testTag("lab-note"))
        FilterChip(selected = enabled, onClick = {
            enabled = !enabled
            if (!enabled) accepted = emptySet()
        }, label = { Text("Подсказки") })
        if (enabled) {
            Text("Версия ${result.version}", style = MaterialTheme.typography.bodySmall)
            if (result.proposals.isEmpty()) Text("Недостаточно данных")
            if (result.conflictingTopics.isNotEmpty()) Text("Проверьте расхождения в исходной заметке.")
            result.proposals.forEach { proposal ->
                NutritionCard {
                    Text(proposal.topic.title + " · " + proposal.stance.title)
                    Text("Источник ${proposal.sourceId}, символы ${proposal.fragment.start}–${proposal.fragment.end}")
                    Text("«${proposal.fragment.text}»")
                    Text("Порядок и фактическое время проверяются в дневнике.")
                    TextButton(enabled = proposal !in accepted, onClick = {
                        accepted += proposal
                        onAccepted(proposal)
                    }) { Text(if (proposal in accepted) "Проверено в прототипе" else "Подтвердить для проверки") }
                }
            }
        }
    }
}

class NoteReviewUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun proposalsRequireOptInAndUserReviewWithUnchangedSource() {
        val model = TrainedNoteJudge.train(fixture("train.tsv"))
        val note = SyntheticNote("ui-synthetic", "После ужина принял таблетку")
        var accepted: Proposal? = null
        compose.setContent {
            NutritionAssistantTheme {
                ReviewDemo(note, NoteReviewer(model.version, model)) { accepted = it }
            }
        }
        compose.onNodeWithText("Подтвердить для проверки").assertDoesNotExist()
        compose.onNodeWithText("Подсказки").performClick()
        compose.onNodeWithText("«${note.text}»").assertIsDisplayed()
        compose.onNodeWithText("Подтвердить для проверки").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(note.id, accepted?.sourceId);assertEquals(note.text, accepted?.fragment?.text) }
        compose.onNodeWithTag("lab-note").assertTextEquals(note.text)
        compose.onNodeWithText("Подсказки").performScrollTo().performClick()
        compose.onNodeWithText("«${note.text}»").assertDoesNotExist()
    }
}
