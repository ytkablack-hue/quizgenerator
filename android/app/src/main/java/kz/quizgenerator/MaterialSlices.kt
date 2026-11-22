package kz.quizgenerator

import java.io.File
import org.json.JSONObject

internal data class MaterialSlice(val start: Int,val end: Int,val oversized: Boolean=false) {
    fun text(source: String)=source.substring(start,end)
}
/** Conservatively preserve explicit question blocks. Ambiguous oversized blocks require manual selection. */
internal object MaterialSlices {
    const val LIMIT=9800
    fun plan(text: String,limit: Int=LIMIT,check: ()->Unit={}): List<MaterialSlice> {
        require(limit>0);if(text.isEmpty())return emptyList()
        val boundaries=mutableListOf(0);var offset=0;var question=false;var letterOptions=false;var numericOptions=0;var number: Int?=null
        for(line in text.splitToSequence('\n')) {
            check();val value=line.trim()
            val named=ParserPatterns.namedQuestion.matchEntire(value)
            val numeric=ParserPatterns.numbered.matchEntire(value)
            val tagged=ParserPatterns.taggedQuestion.matches(value)
            val option=ParserPatterns.option.matchEntire(value)
            val n=numeric?.groupValues?.get(1)?.toIntOrNull()
            val starts=tagged || named!=null || (numeric!=null &&
                (!question || (n==number?.plus(1) && (letterOptions || (numeric.groupValues[2]=="." && numericOptions>=2)))))
            if(starts) {if(offset>boundaries.last())boundaries+=offset;question=true;number=n ?: named?.groupValues?.get(1)?.toIntOrNull();letterOptions=false;numericOptions=0}
            else if(option!=null && question) {if(ParserPatterns.isLetterLabel(option.groupValues[2]))letterOptions=true else numericOptions++}
            if(!question && value.isBlank() && offset>boundaries.last())boundaries+=offset
            offset+=line.length+1
        }
        boundaries+=text.length
        val result=mutableListOf<MaterialSlice>();var start=0;var end=0
        for(i in 0 until boundaries.lastIndex) {
            check();val a=boundaries[i];val b=boundaries[i+1]
            if(b<=a)continue
            if(b-a>limit) {
                if(end>start)result+=MaterialSlice(start,end)
                if(question)result+=MaterialSlice(a,b,true) else {
                    var p=a
                    while(p<b) {check();var q=minOf(p+limit,b);if(q<b && Character.isLowSurrogate(text[q]))q--;result+=MaterialSlice(p,q);p=q}
                }
                start=b;end=b
            } else if(b-start>limit) {if(end>start)result+=MaterialSlice(start,end);start=a;end=b}
            else end=b
        }
        if(end>start)result+=MaterialSlice(start,end)
        return result
    }
    fun compose(part: String,extra: String): String {
        require(extra.length<=2000) {"Дополнительный текст длиннее 2000 символов. Прикрепите его отдельным TXT"}
        return (part+if(extra.isBlank())"" else "\n\nДополнительный текст:\n"+extra).also {
            require(it.length<=12000) {"Выбранный фрагмент превышает лимит запроса. Выберите меньший диапазон"}
        }
    }
}

internal data class AiDraft(val sourceId: String?=null,val topic: String="",val extra: String="",val part: Int=0,
    val difficulty: String="medium",val count: String="5",val previewPage: Int=0)
internal class AiDraftStore(private val file: File) {
    fun load(): AiDraft {
        if(!file.exists())return AiDraft()
        require(file.length()<=32768) {"Повреждены настройки материала. Документы сохранены"}
        val r=JSONObject(file.readText());return AiDraft(r.opt("sourceId") as? String,r.optString("topic"),r.optString("extra"),
            r.optInt("part"),r.optString("difficulty","medium"),r.optString("count","5"),r.optInt("previewPage")).also(::validate)
    }
    fun save(value: AiDraft) {validate(value);atomicWrite(file,JSONObject().put("version",1).put("sourceId",value.sourceId)
        .put("topic",value.topic).put("extra",value.extra).put("part",value.part).put("difficulty",value.difficulty)
        .put("count",value.count).put("previewPage",value.previewPage).toString())}
    private fun validate(value: AiDraft) {
        require(value.sourceId==null || runCatching {java.util.UUID.fromString(value.sourceId).toString()==value.sourceId}.getOrDefault(false))
        require(value.topic.length<=160 && value.extra.length<=2000 && value.part in 0..100000 && value.previewPage in 0..100000)
        require(value.difficulty in setOf("easy","medium","hard") && value.count.length<=2)
    }
}

internal object AiRequestRules {
    fun validate(route: String,body: JSONObject) {
        if(route in setOf("quizzes","flashcards","review")) {
            require(body.optString("material").length<=12000) {"Материал запроса больше 12000 символов"}
            if(route!="review") {
                require(body.optString("topic").length<=160)
                require(body.optString("material").isNotBlank() || body.optString("topic").isNotBlank()) {"Добавьте тему или материал"}
                val count=body.opt("count")
                require(count is Number && count.toDouble()==count.toInt().toDouble() && count.toInt() in 1..25 && body.optString("difficulty") in setOf("easy","medium","hard"))
            }
        }
        require(body.toString().toByteArray(Charsets.UTF_8).size<=if(route=="extract")4300000 else 65536) {"Запрос слишком большой"}
    }
}
