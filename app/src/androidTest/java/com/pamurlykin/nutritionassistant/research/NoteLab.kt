package com.pamurlykin.nutritionassistant.research

import com.pamurlykin.nutritionassistant.data.model.JsonValues
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ln
import kotlin.math.roundToLong

internal enum class Topic(val title: String, val terms: Regex) {
    STRESS("Стресс", words("стресс|нервнич|тревож|тревог|напряг|пережива")),
    ACTIVITY("Нагрузка", words("тренир|пробеж|бег|нагрузк|спорт|упражнен")),
    ILLNESS("Признаки болезни", words("болезн|болел|болею|забол|простуд|лихорад|температур")),
    MEDICINE("Лекарство", words("лекарств|таблет|препарат|капсул|медикамент")),
}

private fun words(stems: String) = Regex("(?<!\\p{L})(?:$stems)\\p{L}*")

internal enum class Stance(val title: String) {
    PRESENT("Возможно, отмечено"), NEGATED("Возможно, отрицается"),
    UNCERTAIN("Неопределённо"), NO_DATA("Недостаточно данных"),
}

internal data class Fragment(val start: Int, val end: Int, val text: String)
internal data class Proposal(val sourceId: String, val fragment: Fragment, val topic: Topic, val stance: Stance)
internal data class NoteReview(val version: String, val proposals: List<Proposal>, val conflictingTopics: Set<Topic>)

private val sentence = Regex("[^.!?;\\r\\n]+")
private val nonWord = Regex("[^a-zа-я0-9]+")
private fun normalize(s: String) = s.lowercase(Locale.ROOT).replace('ё', 'е')
internal fun noteFragments(note: String) = sentence.findAll(note).mapNotNull { match ->
    val trimmed = match.value.trim()
    if (trimmed.isEmpty()) null else {
        val start = match.range.first + match.value.indexOf(trimmed)
        Fragment(start, start + trimmed.length, trimmed)
    }
}

internal fun interface FragmentJudge {
    fun classify(text: String): Stance
}

internal class NoteReviewer(private val version: String, private val judge: FragmentJudge) {
    fun inspect(sourceId: String, note: String, enabled: Boolean): NoteReview {
        if (!enabled || note.length > 1000) return NoteReview(version, emptyList(), emptySet())
        val proposals = noteFragments(note).flatMap { fragment ->
            val topics = Topic.entries.filter { it.terms.containsMatchIn(normalize(fragment.text)) }
            if (topics.isEmpty()) emptySequence() else {
                val stance = judge.classify(fragment.text)
                if (stance == Stance.NO_DATA) emptySequence()
                else topics.asSequence().map { Proposal(sourceId, fragment, it, stance) }
            }
        }.toList()
        val conflicting = proposals.groupBy { it.topic }.filterValues { rows ->
            rows.any { it.stance == Stance.PRESENT } && rows.any { it.stance == Stance.NEGATED }
        }.keys
        return NoteReview(version, proposals, conflicting)
    }
}

internal object TemplateJudge : FragmentJudge {
    private val irrelevant = Regex("(?:у|для) (?:коллеги|друга|соседа|ребенка|сына|дочери)|обсуждали|врач предложил|планирую|купил[аи]?|в рекламе|в книге")
    private val uncertain = Regex("кажется|возможно|не уверен|не помню|может быть|сомневаюсь|под вопросом|не знаю|неясно|вроде|вероятно")
    private val negation = Regex("(?<!\\p{L})(?:не|нет|без)(?!\\p{L})|пропустил|отменил|обошлось")
    override fun classify(text: String): Stance {
        val clean = normalize(text)
        return when {
            irrelevant.containsMatchIn(clean) -> Stance.NO_DATA
            uncertain.containsMatchIn(clean) -> Stance.UNCERTAIN
            negation.containsMatchIn(clean) -> Stance.NEGATED
            else -> Stance.PRESENT
        }
    }
}

/** Multinomial Naive Bayes over word and character n-grams. Training uses the synthetic set only. */
internal class TrainedNoteJudge private constructor(val version: String, private val weights: Map<String, List<Long>>) : FragmentJudge {
    override fun classify(text: String): Stance {
        val known = features(text).mapNotNull { weights[it] }
        if (known.isEmpty()) return Stance.NO_DATA
        val scores = Stance.entries.map { label -> known.sumOf { it[label.ordinal] } }
        val ranked = Stance.entries.sortedByDescending { scores[it.ordinal] }
        val gap = scores[ranked[0].ordinal] - scores[ranked[1].ordinal]
        // Fixed abstention on a narrow normalized score margin.
        return if (gap < 150L * known.size) Stance.UNCERTAIN else ranked[0]
    }

    fun encode() = JsonValues.stringify(mapOf("version" to version, "weights" to weights))

    companion object {
        fun train(tsv: String): TrainedNoteJudge {
            val counts = Stance.entries.map { mutableMapOf<String, Int>() }
            tsv.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val parts = line.trimEnd().split('\t', limit = 2)
                require(parts.size == 2 && parts[1].isNotBlank())
                val label = Stance.valueOf(parts[0])
                features(parts[1]).forEach { feature ->
                    val bucket = counts[label.ordinal]
                    bucket[feature] = (bucket[feature] ?: 0) + 1
                }
            }
            require(counts.all { it.isNotEmpty() })
            val vocabulary = counts.flatMap { it.keys }.toSortedSet()
            val totals = counts.map { it.values.sum() }
            val weights = vocabulary.associateWith { feature ->
                Stance.entries.map { label ->
                    val index = label.ordinal
                    val smoothed = ((counts[index][feature] ?: 0) + 1.0) / (totals[index] + vocabulary.size)
                    (ln(smoothed) * 10_000).roundToLong()
                }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(tsv.toByteArray(Charsets.UTF_8))
                .take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return TrainedNoteJudge("note-ngram-nb1-$digest", weights)
        }

        fun decode(encoded: String): TrainedNoteJudge {
            val root = JsonValues.readObject(encoded)
            val weights = (root.getValue("weights") as Map<*, *>).entries.associate { (key, value) ->
                val row = (value as List<*>).map { it as Long }
                require(row.size == Stance.entries.size)
                key as String to row
            }
            return TrainedNoteJudge(root.getValue("version") as String, weights)
        }
    }
}

private fun features(text: String): Set<String> {
    val tokens = normalize(text).split(nonWord).filter { it.isNotEmpty() }
    return buildSet {
        tokens.forEach { token ->
            add("w:$token")
            val bounded = "^$token$"
            for (size in 3..4) if (bounded.length >= size) {
                bounded.windowed(size).forEach { add("c:$it") }
            }
        }
        tokens.zipWithNext().forEach { (first, second) -> add("b:$first $second") }
    }
}
