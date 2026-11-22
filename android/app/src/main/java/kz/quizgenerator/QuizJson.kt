package kz.quizgenerator

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object QuizJson {
    fun parse(source: String): Quiz {
        JsonInputLimits.check(source)
        val input = JSONTokener(source)
        val root = JSONObject(input)
        require(input.nextClean() == '\u0000') { "После теста остались лишние данные" }
        val id = root.getString("id").trim()
        val title = root.getString("title").trim()
        require(id.isNotEmpty() && title.isNotEmpty()) { "Укажите id и название теста" }

        val rawQuestions = root.getJSONArray("questions")
        require(rawQuestions.length() in 1..QuizLimits.QUESTIONS) { "Тест должен содержать от 1 до 5000 вопросов" }
        val ids = mutableSetOf<String>()
        val questions = (0 until rawQuestions.length()).map { position ->
            val item = rawQuestions.getJSONObject(position)
            val questionId = item.getString("id").trim()
            require(questionId.isNotEmpty() && ids.add(questionId)) { "ID вопросов должны быть уникальными" }

            val text = item.getString("text").trim()
            val rawOptions = item.getJSONArray("options")
            require(text.isNotEmpty()) { "Текст вопроса не может быть пустым" }
            require(rawOptions.length() in 2..QuizLimits.OPTIONS) { "Вопрос должен содержать от 2 до 64 вариантов" }
            val options = (0 until rawOptions.length()).map { optionIndex ->
                rawOptions.getString(optionIndex).trim().also {
                    require(it.isNotEmpty()) { "Варианты ответов не могут быть пустыми" }
                }
            }
            val correctIndex = if (!item.has("correctIndex") || item.isNull("correctIndex")) {
                null
            } else {
                val number = item.get("correctIndex")
                require(number is Number && number.toDouble() == number.toInt().toDouble()) {
                    "Индекс правильного ответа должен быть целым числом"
                }
                number.toInt()
            }
            val keyIndices = if (item.has("correctIndices") && !item.isNull("correctIndices")) {
                val raw = item.getJSONArray("correctIndices")
                require(raw.length() <= QuizLimits.OPTIONS) { "Слишком много индексов ответов" }
                (0 until raw.length()).map {
                    val number = raw.get(it)
                    require(number is Number && number.toDouble() == number.toInt().toDouble()) {
                        "Индекс правильного ответа должен быть целым числом"
                    }
                    (number as Number).toInt()
                }
            } else {
                listOfNotNull(correctIndex)
            }
            require(keyIndices.all { it in options.indices }) { "Неверный индекс правильного ответа" }
            require(correctIndex == null || correctIndex in keyIndices) { "Противоречивые индексы правильных ответов" }
            val primary = correctIndex ?: keyIndices.firstOrNull()
            require(primary == null || primary in options.indices) { "Неверный индекс правильного ответа" }
            val additional = keyIndices.filter { it != primary }.distinct()
            val explanation = if (!item.has("explanation") || item.isNull("explanation")) {
                null
            } else {
                item.getString("explanation").trim().ifEmpty { null }
            }
            val sourceExcerpt = item.opt("sourceExcerpt") as? String
            val evidence = if (primary == null) AnswerEvidence.UNKNOWN else {
                val fallback = if (root.optString("source") == "ai") AnswerEvidence.INFERRED else AnswerEvidence.SOURCE
                if (!item.has("answerEvidence")) fallback
                else runCatching { AnswerEvidence.valueOf(item.getString("answerEvidence")) }.getOrDefault(AnswerEvidence.INFERRED)
            }
            QuizQuestion(questionId, text, options, primary, explanation, sourceExcerpt, evidence, additional)
        }

        val rawProblems = root.optJSONArray("importProblems") ?: JSONArray()
        require(rawProblems.length() <= QuizLimits.QUESTIONS) { "Слишком много проблем импорта" }
        val problems = (0 until rawProblems.length()).map { i ->
            val item = rawProblems.getJSONObject(i)
            val parts = item.getJSONArray("sourceParts")
            require(parts.length() <= 134) { "Слишком длинный проблемный фрагмент" }
            val source = (0 until parts.length()).joinToString("") { n ->
                parts.getString(n).also { require(it.length <= 60_000) }
            }
            val reason = item.getString("reason").also { require(it.length <= 400) }
            val number = if (item.isNull("number")) null else item.getInt("number")
            ImportProblem(number, reason, source)
        }
        return Quiz(id, title, questions, root.optString("source") == "ai", problems)
    }

    fun serialize(quiz: Quiz): String {
        val result = JSONObject()
            .put("id", quiz.id)
            .put("title", quiz.title)
        if (quiz.generatedByAi) result.put("source", "ai")

        val questions = JSONArray()
        quiz.questions.forEach { q ->
            val options = JSONArray()
            q.options.forEach(options::put)
            questions.put(
                JSONObject()
                    .put("id", q.id)
                    .put("text", q.text)
                    .put("options", options)
                    .put("correctIndex", q.correctIndex ?: JSONObject.NULL)
                    .put("correctIndices", JSONArray(q.correctIndices))
                    .put("explanation", q.explanation ?: JSONObject.NULL)
                    .put("sourceExcerpt", q.sourceExcerpt ?: JSONObject.NULL)
                    .put("answerEvidence", q.evidence.name)
            )
        }
        result.put("questions", questions)
        if (quiz.importProblems.isNotEmpty()) {
            val problems = JSONArray()
            quiz.importProblems.forEach { issue ->
                problems.put(JSONObject().put("number", issue.number ?: JSONObject.NULL)
                    .put("reason", issue.reason).put("sourceParts", JSONArray(issue.source.chunked(60_000))))
            }
            result.put("importProblems", problems)
        }
        return result.toString(2)
    }
}
