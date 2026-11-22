package kz.quizgenerator

import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

internal data class Lecture(val id: String,val setId: String,val title: String,val body: String,
    val sourceRef: String?,val sourceHash: String?,val complete: Boolean,val edited: Boolean=false,val createdAt: Long=System.currentTimeMillis())
internal class LectureStore(private val root: File) {
    private val directory=File(root,"lectures")
    private fun file(id: String): File {require(UUID.fromString(id).toString()==id);return File(directory,"$id.json").also {require(it.canonicalFile.parentFile==directory.canonicalFile)}}
    fun save(v: Lecture) {require(UUID.fromString(v.setId).toString()==v.setId);require(v.title.isNotBlank() && v.title.length<=160 && v.body.isNotBlank() && v.body.length<=8_000_000);require(v.sourceRef==null || v.sourceRef.length<=250)
        val json=JSONObject().put("version",1).put("id",v.id).put("setId",v.setId).put("title",v.title).put("body",v.body).put("sourceRef",v.sourceRef).put("sourceHash",v.sourceHash).put("complete",v.complete).put("edited",v.edited).put("createdAt",v.createdAt).toString()
        require(json.toByteArray().size<=32_000_000);atomicWrite(file(v.id),json)
    }
    fun exists(id: String)=file(id).exists()
    fun load(id: String): Lecture {val f=file(id);require(f.length() in 1..32_000_000);val j=JSONObject(f.readText());require(j.getInt("version")==1 && j.getString("id")==id)
        return Lecture(id,j.getString("setId"),j.getString("title"),j.getString("body"),j.opt("sourceRef") as? String,j.opt("sourceHash") as? String,j.getBoolean("complete"),j.optBoolean("edited"),j.getLong("createdAt"))}
    fun list(setId: String): List<Lecture> = directory.listFiles {f->f.extension=="json"}.orEmpty().map {load(it.nameWithoutExtension)}.filter {it.setId==setId}.sortedByDescending {it.createdAt}
    fun delete(id: String) {val f=file(id);check(!f.exists() || f.delete()) {"Не удалось удалить лекцию"}}
}
internal object StudyContract {
    private fun text(j: JSONObject,key: String,max: Int): String=j.getString(key).trim().also {require(it.isNotBlank() && it.length<=max)}
    private fun strings(j: JSONObject,key: String,maxItems: Int,maxText: Int,min: Int=0): List<String> {val a=j.getJSONArray(key);require(a.length() in min..maxItems);return List(a.length()){a.getString(it).trim().also {s->require(s.isNotBlank() && s.length<=maxText)}}}
    fun lecture(value: JSONObject,input: JSONObject): String {
        require(value.getBoolean("complete") && value.getInt("sectionIndex")==input.getInt("sectionIndex")) {"ИИ не завершил часть лекции"}
        val result=buildString {
            text(value,"title",160);appendLine("## Введение");appendLine(text(value,"introduction",2400))
            val sections=value.getJSONArray("sections");require(sections.length() in 1..8)
            fun pairs(j: JSONObject,key: String,max: Int) {val a=j.getJSONArray(key);require(a.length()<=max);for(i in 0 until a.length()){val p=a.getJSONObject(i);appendLine("- "+text(p,"term",160)+": "+text(p,"meaning",1200))}}
            for(i in 0 until sections.length()) {val s=sections.getJSONObject(i);appendLine("## "+text(s,"heading",160));strings(s,"paragraphs",8,1600,1).forEach {appendLine(it);appendLine()}
                val sub=s.getJSONArray("subsections");require(sub.length()<=4);for(k in 0 until sub.length()){val v=sub.getJSONObject(k);appendLine("### "+text(v,"heading",160));strings(v,"paragraphs",6,1200,1).forEach {appendLine(it)}}
                if(s.getJSONArray("definitions").length()>0){appendLine("### Определения");pairs(s,"definitions",12)}
                strings(s,"examples",6,1200).forEach {appendLine("- Пример: $it")}
                strings(s,"formulas",6,200).forEach {require(!it.contains('\\') && !it.contains('$'));appendLine("Формула: $it")}
                strings(s,"quotes",6,600).forEach {quote->require(input.optString("material").isNotEmpty() && input.getString("material").contains(quote)) {"Цитата не подтверждается источником"};appendLine("> Из выбранного источника: $quote")}
            }
            appendLine("## Заключение");appendLine(text(value,"conclusion",2400));appendLine("## Термины для повторения");require(value.getJSONArray("terms").length()>0);pairs(value,"terms",30)
        }
        require(result.length<=60000);return result
    }
    fun answers(value: JSONObject,input: JSONObject): List<JSONObject> {
        val questions=input.getJSONArray("questions");val expected=(0 until questions.length()).associate {val q=questions.getJSONObject(it);q.getString("id") to q}
        val a=value.getJSONArray("answers");require(a.length()==expected.size);val seen=mutableSetOf<String>()
        return List(a.length()){i->val v=a.getJSONObject(i);val id=v.getString("id");require(seen.add(id) && id in expected) {"Ответы не совпадают с ID исходных вопросов"}
            val indices=v.getJSONArray("indices");val items=List(indices.length()){k->val n=indices.get(k);require(n is Number && n.toDouble()==n.toInt().toDouble());n.toInt()};require(items.distinct().size==items.size && items.all {it in 0 until expected.getValue(id).getJSONArray("options").length()})
            val uncertain=v.getBoolean("uncertain");require(uncertain || items.isNotEmpty());text(v,"explanation",2000)
            JSONObject(v.toString()).put("evidence","CLOUD_AI")
        }
    }
}
