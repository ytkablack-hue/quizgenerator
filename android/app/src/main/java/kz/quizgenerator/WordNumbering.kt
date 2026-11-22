package kz.quizgenerator

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/** Bounded numbering metadata; document paragraphs remain streamed by DocxTextExtractor. */
internal class WordNumbering private constructor() {
    private data class Level(var format: String = "decimal", var pattern: String = "%1.", var start: Int = 1)
    private val definitions = mutableMapOf<Pair<String, Int>, Level>()
    private val instances = mutableMapOf<String, String>()
    private val overrides = mutableMapOf<Pair<String, Int>, Int>()
    private val levelOverrides = mutableMapOf<Pair<String, Int>, Level>()
    private val counters = mutableMapOf<Pair<String, Int>, Int>()

    fun prefix(id: String?, level: Int): String {
        if (id == null || id == "0") return ""
        val abstractId = instances[id] ?: throw IllegalArgumentException("Нет определения списка Word")
        val definition = levelOverrides[id to level] ?: definitions[abstractId to level] ?: throw IllegalArgumentException("Нет уровня списка Word")
        val key = id to level
        val count = counters[key]?.plus(1) ?: overrides[key] ?: definition.start
        require(count in 1..1_000_000) { "Слишком длинная нумерация Word" }
        counters.keys.filter { it.first == id && it.second > level }.toList().forEach(counters::remove)
        counters[key] = count
        if (definition.format == "bullet" || definition.format == "none") return ""
        require(definition.format in setOf("decimal", "upperLetter", "lowerLetter")) {
            "Нумерация Word "+definition.format+" требует проверки: сохраните документ с цифровыми или буквенными списками"
        }
        return Regex("%([1-9])").replace(definition.pattern) { match ->
            val target = match.groupValues[1].toInt()-1
            val item = levelOverrides[id to target] ?: definitions[abstractId to target] ?: definition
            val value = counters[id to target] ?: item.start
            when (item.format) {
                "upperLetter" -> letters(value)
                "lowerLetter" -> letters(value).lowercase(java.util.Locale.ROOT)
                else -> value.toString()
            }
        } + " "
    }

    private fun letters(number: Int): String {
        var n = number
        return buildString { while(n>0) { n--; insert(0, ('A'.code+n%26).toChar()); n/=26 } }
    }

    companion object {
        private const val LIMIT = 4 * 1024 * 1024
        fun read(zip: ZipFile, checkCancelled: () -> Unit): WordNumbering {
            val result = WordNumbering()
            val entry = zip.getEntry("word/numbering.xml") ?: return result
            require(entry.size in 0..LIMIT.toLong()) { "Слишком большой файл нумерации Word" }
            val output = ByteArrayOutputStream()
            val crc = CRC32()
            var matched = 0
            val forbidden = "<!DOCTYPE"
            zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(8192)
                while(true) {
                    checkCancelled()
                    val n = input.read(buffer); if(n<0)break
                    require(output.size()+n <= LIMIT && output.size()+n <= entry.size) { "Повреждённая нумерация Word" }
                    for(i in 0 until n) {
                        val value = buffer[i].toInt() and 255
                        if(value==0)continue
                        matched = if(value==forbidden[matched].code) matched+1 else if(value==60) 1 else 0
                        require(matched<forbidden.length) { "В документе найдено небезопасное объявление XML" }
                    }
                    output.write(buffer,0,n);crc.update(buffer,0,n)
                }
            }
            require(output.size().toLong()==entry.size && crc.value==entry.crc) { "Повреждённая нумерация Word" }
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware=true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl",true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities",false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities",false) }
            }
            val handler = object: DefaultHandler() {
                var abstractId: String?=null;var numId: String?=null;var level=0;var overrideLevel=0;var nodes=0
                var targetLevel: Level? = null
                override fun resolveEntity(publicId:String?,systemId:String?):InputSource = throw SAXException("Внешние XML-ресурсы отключены")
                override fun startElement(uri:String?,localName:String?,qName:String?,attributes:Attributes) {
                    checkCancelled()
                    require(++nodes<=100_000) { "Слишком сложная нумерация Word" }
                    fun value(name:String="val")=attributes.getValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main",name)
                        ?: attributes.getValue("w:"+name) ?: attributes.getValue(name)
                    when(localName?.takeIf { it.isNotEmpty() } ?: qName?.substringAfter(':')) {
                        "abstractNum" -> { abstractId=value("abstractNumId");require(result.definitions.size<=10_000) }
                        "num" -> { numId=value("numId");require(result.instances.size<=10_000) }
                        "abstractNumId" -> if(numId!=null) result.instances[numId!!]=value().orEmpty()
                        "lvlOverride" -> overrideLevel=value("ilvl")?.toIntOrNull() ?: 0
                        "startOverride" -> if(numId!=null) result.overrides[numId!! to overrideLevel]=value()?.toIntOrNull() ?: 1
                        "lvl" -> {
                            level=value("ilvl")?.toIntOrNull() ?: 0;require(level in 0..8)
                            targetLevel=Level()
                            if(abstractId!=null) result.definitions[abstractId!! to level]=targetLevel!!
                            else if(numId!=null) result.levelOverrides[numId!! to level]=targetLevel!!
                        }
                        "numFmt" -> targetLevel?.format=value().orEmpty()
                        "lvlText" -> targetLevel?.pattern=value().orEmpty().also { p -> require(p.length<=128) }
                        "start" -> targetLevel?.start=value()?.toIntOrNull() ?: 1
                    }
                }
                override fun endElement(uri:String?,localName:String?,qName:String?) {
                    when(localName?.takeIf { it.isNotEmpty() } ?: qName?.substringAfter(':')) {
                        "lvl" -> targetLevel=null
                        "abstractNum" -> abstractId=null
                        "num" -> numId=null
                    }
                }
            }
            factory.newSAXParser().parse(output.toByteArray().inputStream(),handler)
            return result
        }
    }
}
