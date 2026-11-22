package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
class OcrLimitsTest {
    @Test fun decodedImageMemoryIsBounded() {
        for((w,h) in listOf(10000 to 10000,4000 to 3000,10 to 100000,1200 to 1600)) {
            val (x,y)=OcrLimits.dimensions(w,h)
            assertTrue(x>0 && y>0 && maxOf(x,y)<=2500 && x.toLong()*y<=OcrLimits.MAX_PIXELS)
        }
    }
    @Test fun invalidAndDecompressionBombDimensionsFail() {
        for((w,h) in listOf(0 to 100,-1 to 10,100001 to 1,20000 to 20000))assertThrows(IllegalArgumentException::class.java) {OcrLimits.dimensions(w,h)}
    }
    @Test fun ocrKeysNeedManualConfirmationAndNoOcrPreservesOriginal() {
        val q=QuizQuestion("q","Вопрос?",listOf("Да","Нет"),0,null)
        val quiz=Quiz("x","x",listOf(q))
        assertSame(quiz,OcrReview.review(quiz,false))
        val result=OcrReview.review(quiz,true)
        assertEquals(q.text,result.questions.single().text)
        assertEquals(q.correctIndices,result.questions.single().correctIndices)
        assertEquals(AnswerEvidence.INFERRED,result.questions.single().evidence)
        assertEquals(QuizScore(0,0,0,1),QuizScorer.score(result,listOf(0)))
    }
    @Test fun bundledLanguagesMatchPinnedOfficialManifest() {
        val dir=java.io.File("src/main/assets/ocr").takeIf {it.isDirectory} ?: java.io.File("android/app/src/main/assets/ocr")
        val manifest=org.json.JSONArray(java.io.File(dir,"models.json").readText())
        assertEquals(3,manifest.length())
        val expected=mapOf("rus" to "e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225",
            "kaz" to "fcc01eed3815a42b9c6321c4c9d3606f39b166cbf95ade98b7d8d12063eae53d",
            "eng" to "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2")
        for(i in 0 until manifest.length()) {
            val record=manifest.getJSONObject(i);val language=record.getString("language")
            val file=java.io.File(dir,"tessdata/"+language+".traineddata")
            assertEquals(record.getLong("bytes"),file.length())
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {"%02x".format(it.toInt() and 255)}
            assertEquals(expected[language],hash);assertEquals(hash,record.getString("sha256"))
        }
    }
}
