package com.pamurlykin.nutritionassistant.research

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.ceil

internal fun fixture(name: String): String =
    InstrumentationRegistry.getInstrumentation().context.assets.open("on-device/$name")
        .bufferedReader(Charsets.UTF_8).use { it.readText().replace("\r\n", "\n") }

private data class Cue(val topic: Topic, val quote: String)
private data class Example(val id: String, val text: String, val expected: Map<Cue, Stance>, val critical: Boolean,
    val conflicts: Set<Topic>)

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }

private fun examples(): List<Example> = JSONArray(fixture("holdout.json")).objects().map { row ->
    val gold = row.getJSONArray("gold").objects().associate {
        Cue(Topic.valueOf(it.getString("topic")), it.getString("quote")) to Stance.valueOf(it.getString("stance"))
    }
    val conflicts = row.getJSONArray("conflicts").let { value ->
        (0 until value.length()).map { Topic.valueOf(value.getString(it)) }.toSet()
    }
    Example(row.getString("id"), row.getString("text"), gold, row.getBoolean("critical"), conflicts)
}

private data class Comparison(val noteId: String, val cue: Cue, val expected: Stance, val actual: Stance)
private data class Metrics(
    val comparisons: List<Comparison>,
    val confusion: Map<String, Map<String, Int>>,
    val macroF1: Double,
    val criticalFalseAffirmations: List<Comparison>,
    val conflictErrors: List<String>,
) {
    private fun Comparison.toValue() = mapOf(
        "note_id" to noteId, "quote" to cue.quote, "topic" to cue.topic.name, "expected" to expected.name, "actual" to actual.name,
    )

    fun toValue() = mapOf(
        "compared_units" to comparisons.size, "confusion_expected_rows" to confusion, "macro_f1" to macroF1,
        "critical_false_affirmations" to criticalFalseAffirmations.map { it.toValue() }, "conflict_errors" to conflictErrors,
        "differences" to comparisons.filter { it.expected != it.actual }.map { it.toValue() },
    )
}

private fun evaluate(dataset: List<Example>, reviewer: NoteReviewer): Metrics {
    val conflictErrors = mutableListOf<String>()
    val critical = dataset.filter { it.critical }.map { it.id }.toSet()
    val compared = dataset.flatMap { sample ->
        val review = reviewer.inspect(sample.id, sample.text, enabled = true)
        if (review.conflictingTopics != sample.conflicts) conflictErrors += sample.id
        review.proposals.forEach { proposal ->
            assertEquals(sample.id, proposal.sourceId)
            assertEquals(proposal.fragment.text, sample.text.substring(proposal.fragment.start, proposal.fragment.end))
        }
        val predicted = review.proposals.associate { Cue(it.topic, it.fragment.text) to it.stance }
        noteFragments(sample.text).flatMap { fragment ->
            Topic.entries.asSequence().map { topic ->
                val cue = Cue(topic, fragment.text)
                Comparison(sample.id, cue, sample.expected[cue] ?: Stance.NO_DATA, predicted[cue] ?: Stance.NO_DATA)
            }
        }.toList()
    }
    val confusion = Stance.entries.associate { expected ->
        expected.name to Stance.entries.associate { predicted ->
            predicted.name to compared.count { it.expected == expected && it.actual == predicted }
        }
    }
    val macroF1 = Stance.entries.map { label ->
        val truePositive = compared.count { it.expected == label && it.actual == label }
        val total = compared.count { it.expected == label } + compared.count { it.actual == label }
        require(total > 0)
        2.0 * truePositive / total
    }.average()
    return Metrics(compared, confusion, macroF1,
        compared.filter { it.noteId in critical && it.actual == Stance.PRESENT && it.expected != Stance.PRESENT },
        conflictErrors)
}

class NoteLabTest {
    @Test fun evaluateOfflinePrototypeAgainstTemplateOnHeldOutNotes() {
        val runner = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val resolver = runner.context.contentResolver
        val airplane = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val wifiOff = Settings.Global.getInt(resolver, "wifi_on", -1) == 0
        if (args.getString("requireOffline") == "true") {
            assertTrue("Airplane mode must be enabled", airplane)
            assertTrue("Wi-Fi must be disabled", wifiOff)
        }

        val learning = fixture("train.tsv")
        val dataset = examples()
        val trainingNotes = learning.lineSequence().filter { it.isNotBlank() }.map { it.substringAfter('\t') }.toList()
        assertEquals(trainingNotes.size, trainingNotes.distinct().size)
        assertEquals(dataset.size, dataset.map { it.id }.distinct().size)
        dataset.forEach { sample ->
            val spans = noteFragments(sample.text).map { it.text }.toSet()
            assertTrue(sample.id, sample.text.trim() !in trainingNotes && sample.expected.keys.all { it.quote in spans })
        }

        val encoded = TrainedNoteJudge.train(learning).encode()
        val pssBefore = Debug.getPss()
        val starting = SystemClock.elapsedRealtimeNanos()
        val model = TrainedNoteJudge.decode(encoded)
        val decodeMs = elapsedMs(starting)
        val pssAfter = Debug.getPss()
        assertEquals(encoded, model.encode())
        val prototype = NoteReviewer(model.version, model)
        val template = NoteReviewer("template1", TemplateJudge)
        assertTrue(prototype.inspect("sample", "Таблетку принял", enabled = false).proposals.isEmpty())
        assertTrue(prototype.inspect("sample", " ", enabled = true).proposals.isEmpty())
        assertTrue(prototype.inspect("sample", "страница ".repeat(120), enabled = true).proposals.isEmpty())

        val baseline = evaluate(dataset, template)
        val proposed = evaluate(dataset, prototype)
        val samples = dataset.filter { it.text.isNotBlank() }
        repeat(5) { samples.forEach { prototype.inspect(it.id, it.text, enabled = true) } }
        val latencies = List(5) {
            samples.map { sample ->
                val tick = SystemClock.elapsedRealtimeNanos()
                prototype.inspect(sample.id, sample.text, enabled = true)
                elapsedMs(tick)
            }
        }.flatten().sorted()
        fun percentile(p: Double) = latencies[(ceil(p * latencies.size).toInt() - 1).coerceAtLeast(0)]

        val qualityPass = proposed.macroF1 >= .90 && proposed.macroF1 - baseline.macroF1 >= .05 &&
            proposed.criticalFalseAffirmations.isEmpty() && proposed.conflictErrors.isEmpty()
        val result = mapOf(
            "model_version" to model.version, "train_examples" to trainingNotes.size, "holdout_notes" to dataset.size,
            "device" to mapOf("sdk" to Build.VERSION.SDK_INT, "abi" to Build.SUPPORTED_ABIS.first(),
                "airplane_mode" to airplane, "wifi_disabled" to wifiOff),
            "template" to baseline.toValue(), "model" to proposed.toValue(), "quality_gate_pass" to qualityPass,
            "resources_emulator_only" to mapOf("encoded_model_bytes" to encoded.toByteArray(Charsets.UTF_8).size,
                "deserialize_ms" to decodeMs, "pss_before_kib" to pssBefore, "pss_after_kib" to pssAfter,
                "requests" to latencies.size, "warm_p50_ms" to percentile(.50), "warm_p95_ms" to percentile(.95)),
        )
        assertEquals(baseline.comparisons.size, proposed.comparisons.size)
        val text = JsonValues.stringify(result)
        args.getString("additionalTestOutputDir")?.let { directory ->
            File(directory, "on-device-note-lab.json").apply { parentFile?.mkdirs();writeText(text, Charsets.UTF_8) }
        }
        Log.i("NoteLab", "${model.version} · template=${baseline.macroF1} · model=${proposed.macroF1} · " +
            "critical=${proposed.criticalFalseAffirmations.size} · p95=${percentile(.95)} ms")
    }

    @Test fun structuredAnalysisStillUsesRecordedTimeAndNeverUsesCommentAsEvidence() {
        val date = "2026-11-18"
        val stool = StoolEntity(userId = 1, date = date, quality = 7, actualTime = "07:00", utcOffset = "+03:00")
        val later = MedicineWithItems(
            MedicineEntity(id = 2, userId = 1, date = date, name = "Лекарство", dosage = null,
                actualTime = "10:00", utcOffset = "+03:00", comment = "В заметке написано, что таблетку принял до симптома"),
            listOf(MedicineItemEntity(medicineId = 2, catalogId = 2, name = "Лекарство", dosage = null)),
        )
        val food = MealWithItems(
            MealEntity(id = 3, userId = 1, date = date, mealType = "breakfast", description = "Фарш",
                actualTime = "06:00", utcOffset = "+03:00", comment = "Состав прочитать не смог. Возможно, чеснок."),
            listOf(MealItemEntity(mealId = 3, catalogId = 3, name = "Фарш", foodType = "", ingredients = emptyList(), kind = "product")),
        )
        val history = AnalysisHistory(meals = listOf(food), medicines = listOf(later), stools = listOf(stool))
        val case = ExplainableAnalyzer.episode(history, "stool:${stool.publicId}", AnalysisWindow.SIX)
        assertEquals(listOf("Фарш"), case.preceding.map { it.title })
        assertEquals(1, case.excludedLaterOrEqual)
        assertTrue(case.preceding.single().details.contains("Версия состава неизвестна"))
        val summary = ExplainableAnalyzer.analyze(history, "2026-11-20", 2)
        assertEquals(0, summary.coverage.paired)
        assertEquals(1, summary.coverage.unknownComposition)
        assertFalse(summary.observations.any { it.factor.title.contains("чеснок", ignoreCase = true) })
    }

    private fun elapsedMs(tick: Long) = (SystemClock.elapsedRealtimeNanos() - tick) / 1_000_000.0
}
