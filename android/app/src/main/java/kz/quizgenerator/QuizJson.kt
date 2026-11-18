package kz.quizgenerator

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object QuizJson {
    fun parse(source: String): Quiz {
        val input = JSONTokener(source)
        val root = JSONObject(input)
        require(input.nextClean() == '\u0000') { "После теста остались лишние данные" }
        val id = root.getString("id").trim()
        val title = root.getString("title").trim()
        require(id.isNotEmpty() && title.isNotEmpty()) { "Укажите id и название теста" }

        val rawQuestions = root.getJSONArray("questions")
        require(rawQuestions.length() in 1..500) { "Тест должен содержать от 1 до 500 вопросов" }
        val ids = mutableSetOf<String>()
        val questions = (0 until rawQuestions.length()).map { position ->
            val item = rawQuestions.getJSONObject(position)
            val questionId = item.getString("id").trim()
            require(questionId.isNotEmpty() && ids.add(questionId)) { "ID вопросов должны быть уникальными" }

            val text = item.getString("text").trim()
            val rawOptions = item.getJSONArray("options")
            require(text.isNotEmpty()) { "Текст вопроса не может быть пустым" }
            require(rawOptions.length() in 2..8) { "Вопрос должен содержать от 2 до 8 вариантов" }
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
            require(correctIndex == null || correctIndex in options.indices) { "Неверный индекс правильного ответа" }
            val explanation = if (!item.has("explanation") || item.isNull("explanation")) {
                null
            } else {
                item.getString("explanation").trim().ifEmpty { null }
            }
            val sourceExcerpt = item.opt("sourceExcerpt") as? String
            QuizQuestion(questionId, text, options, correctIndex, explanation, sourceExcerpt)
        }

        return Quiz(id, title, questions, root.optString("source") == "ai")
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
                    .put("explanation", q.explanation ?: JSONObject.NULL)
                    .put("sourceExcerpt", q.sourceExcerpt ?: JSONObject.NULL)
            )
        }
        result.put("questions", questions)
        return result.toString(2)
    }
}
