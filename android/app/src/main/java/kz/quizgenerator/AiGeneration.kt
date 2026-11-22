package kz.quizgenerator
import org.json.JSONObject

internal enum class AiInputMode(val title: String) { TOPIC("По теме"), DOCUMENT("По документу"), TEXT("Свой текст") }
internal object AiLimits {
    const val TOPIC = 160
    const val MATERIAL = 12000
    const val INSTRUCTIONS = 2000
    const val COUNT = 25
    const val REQUEST_BYTES = 65536
}
/** A single request snapshot: unselected sources can never leak into its material. */
internal object AiGeneration {
    fun build(mode: AiInputMode, topic: String, ownText: String, document: String, instructions: String,
              difficulty: String, count: Int): JSONObject {
        val material = when(mode) {
            AiInputMode.TOPIC -> { require(topic.isNotBlank()) { "Введите тему" }; "" }
            AiInputMode.TEXT -> { require(ownText.isNotBlank()) { "Введите собственный текст" }; ownText.trim() }
            AiInputMode.DOCUMENT -> { require(document.isNotBlank()) { "Выберите документ и дождитесь обработки" }; MaterialSlices.compose(document,instructions) }
        }
        return input(topic,material,difficulty,count)
    }
    fun input(topic: String, material: String, difficulty: String, count: Int): JSONObject =
        JSONObject().put("topic",topic.trim()).put("material",material.trim()).put("difficulty",difficulty).put("count",count)
            .also { AiRequestRules.validate("quizzes",it) }
    fun initialDocument(mode: AiInputMode,current: String?,allowed: List<String>): String? =
        if(mode==AiInputMode.DOCUMENT && current !in allowed) allowed.firstOrNull() else current
}
