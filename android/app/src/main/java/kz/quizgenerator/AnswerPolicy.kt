package kz.quizgenerator

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONArray

object QuizLimits {
    const val QUESTIONS = 5_000
    const val OPTIONS = 64
}

enum class AnswerEvidence(val label: String) {
    SOURCE("Подтверждён источником"),
    MANUAL("Подтверждён вручную"),
    INFERRED("Предположительный ответ"),
    UNKNOWN("Правильный ответ неизвестен")
}

internal object AnswerPolicy {
    fun assumeFirst(quiz: Quiz): Quiz = quiz.copy(questions = quiz.questions.map {
        if (it.correctIndex == null) it.copy(correctIndex = 0, evidence = AnswerEvidence.INFERRED) else it
    })

    fun sample(quiz: Quiz): List<QuizQuestion> {
        val eligible = quiz.questions.filter { it.correctIndex == null }
        if (eligible.size < 30) return emptyList()
        val third = eligible.size / 3
        return (0..2).flatMap { part ->
            val from = part * third
            val until = if (part == 2) eligible.size else (part + 1) * third
            (0..9).map { step -> eligible[from + (until - from - 1) * step / 9] }
        }
    }

    fun fingerprint(question: QuizQuestion): String {
        val data = JSONArray().put(question.text.trim()).put(JSONArray(question.options.map(String::trim))).toString()
        return MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun supportsFirst(quiz: Quiz, cached: Map<String, Int>): Boolean {
        val sample = sample(quiz)
        return sample.size == 30 && sample.map(::fingerprint).distinct().size == 30 && sample.all { cached[fingerprint(it)] == 0 }
    }

    fun applySampleRule(quiz: Quiz, cached: Map<String, Int>, userConfirmed: Boolean): Quiz {
        require(userConfirmed && supportsFirst(quiz, cached)) { "Нужны 30 независимых ответов и подтверждение пользователя" }
        return assumeFirst(quiz)
    }
}

/** Separate cache for future independent answer providers. Includes provider/model/prompt revision. */
internal class AnswerCache(private val file: File, private val providerRevision: String) {
    private fun read(): JSONObject = if (file.exists()) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject()
    fun answers(questions: List<QuizQuestion>): Map<String, Int> {
        val cache = read().optJSONObject(providerRevision) ?: return emptyMap()
        return questions.mapNotNull {
            val hash = AnswerPolicy.fingerprint(it)
            val value = cache.opt(hash)
            if (value is Int && value in it.options.indices) hash to value else null
        }.toMap()
    }
    fun store(question: QuizQuestion, index: Int) {
        require(index in question.options.indices)
        val root = read()
        val provider = root.optJSONObject(providerRevision) ?: JSONObject()
        provider.put(AnswerPolicy.fingerprint(question), index)
        root.put(providerRevision, provider)
        atomicWrite(file, root.toString())
    }
}


/** A configured provider implements this boundary. No provider is created or invoked automatically. */
internal fun interface IndependentAnswerProvider {
    suspend fun verify(questions: List<QuizQuestion>): Map<String, Int>
}

internal data class SampleAssessment(val checked: Int, val firstOptionSupported: Boolean)

internal object SampleAnswerCheck {
    suspend fun assess(quiz: Quiz, cache: AnswerCache, provider: IndependentAnswerProvider): SampleAssessment {
        val sample = AnswerPolicy.sample(quiz)
        require(sample.size == 30 && sample.map(AnswerPolicy::fingerprint).distinct().size == 30) {
            "Для выборочной проверки нужны 30 разных вопросов без ключа"
        }
        val saved = cache.answers(sample)
        val missing = sample.filter { AnswerPolicy.fingerprint(it) !in saved }
        if (missing.isNotEmpty()) {
            // Remove explanations and keys so the provider supplies independent answers.
            val clean = missing.map { it.copy(correctIndex = null, evidence = AnswerEvidence.UNKNOWN, explanation = null, sourceExcerpt = null) }
            val found = provider.verify(clean)
            require(found.keys == missing.map(AnswerPolicy::fingerprint).toSet() &&
                missing.all { found[AnswerPolicy.fingerprint(it)] in it.options.indices }) {
                "Провайдер вернул неполный или неверный набор ответов"
            }
            missing.forEach { cache.store(it, found.getValue(AnswerPolicy.fingerprint(it))) }
        }
        return SampleAssessment(sample.size, AnswerPolicy.supportsFirst(quiz, cache.answers(sample)))
    }
}
