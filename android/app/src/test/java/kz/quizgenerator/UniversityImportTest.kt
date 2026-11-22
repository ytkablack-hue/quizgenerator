package kz.quizgenerator

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class UniversityImportTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun source(count: Int, tagged: Boolean): String = buildString {
        for (i in 1..count) {
            appendLine(if (tagged) "<question>Вопрос $i?" else "$i.\u00a0Вопрос $i?")
            appendLine()
            for (j in 1..12) appendLine(if (tagged) "<variant>\u202fВариант $j" else "${('A'.code + j - 1).toChar()}) Вариант $j")
        }
    }

    private fun docx(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""".toByteArray())
            text.lineSequence().forEach {
                val safe = it.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                zip.write("<w:p><w:r><w:t>$safe</w:t></w:r></w:p>".toByteArray(Charsets.UTF_8))
            }
            zip.write("</w:body></w:document>".toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    @Test fun importsEightHundredAndFifteenHundredInBothFormatsFromTxtAndWord() {
        for (count in listOf(100, 500, 800, 1500)) for (tagged in listOf(false, true)) {
            val text = source(count, tagged)
            val direct = QuizTextParser.parse(text, "Университет").quiz
            val archive = docx(text)
            val fromWord = QuizTextParser.parseFrom("Word") { consume ->
                DocxTextExtractor.paragraphs(ByteArrayInputStream(archive), temporary.root,
                    archive.size.toLong(), {}, consume)
            }.quiz
            for (quiz in listOf(direct, fromWord)) {
                assertEquals(count, quiz.questions.size)
                assertEquals("Вопрос $count?", quiz.questions.last().text)
                assertTrue(quiz.questions.all { it.options.size == 12 && it.correctIndex == null && it.evidence == AnswerEvidence.UNKNOWN })
                assertEquals("Вариант 12", quiz.questions.last().options.last())
                assertEquals(quiz, QuizJson.parse(QuizJson.serialize(quiz)))
            }
        }
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun doesNotSilentlyTruncateOptionsOrScoreAssumptions() {
        val quiz = QuizTextParser.parse(source(1, true), "Тест").quiz
        val inferred = AnswerPolicy.assumeFirst(quiz)
        assertEquals(AnswerEvidence.INFERRED, inferred.questions.single().evidence)
        assertEquals(QuizScore(0, 0, 0, 1), QuizScorer.score(inferred, listOf(0)))
        assertEquals(inferred, QuizJson.parse(QuizJson.serialize(inferred)))
        val confirmed = inferred.copy(questions = inferred.questions.map { it.copy(evidence = AnswerEvidence.MANUAL) })
        assertEquals(QuizScore(1, 0, 0, 0), QuizScorer.score(confirmed, listOf(0)))
    }

    @Test fun samplesTenFromEachPartAndRequiresEveryIndependentAnswerAndConsent() {
        val quiz = QuizTextParser.parse(source(1500, true), "Тест").quiz
        val sample = AnswerPolicy.sample(quiz)
        assertEquals(30, sample.size)
        assertEquals(10, sample.count { it in quiz.questions.subList(0,500) })
        assertEquals(10, sample.count { it in quiz.questions.subList(500,1000) })
        assertEquals(10, sample.count { it in quiz.questions.subList(1000,1500) })
        assertEquals(30, sample.map(AnswerPolicy::fingerprint).distinct().size)
        val keys = sample.associate { AnswerPolicy.fingerprint(it) to 0 }
        assertTrue(AnswerPolicy.supportsFirst(quiz, keys))
        assertThrows(IllegalArgumentException::class.java) { AnswerPolicy.applySampleRule(quiz, keys, false) }
        assertFalse(AnswerPolicy.supportsFirst(quiz, keys - AnswerPolicy.fingerprint(sample.first())))
        assertFalse(AnswerPolicy.supportsFirst(quiz, keys + (AnswerPolicy.fingerprint(sample.first()) to 1)))
        assertTrue(AnswerPolicy.applySampleRule(quiz,keys,true).questions.all { it.evidence == AnswerEvidence.INFERRED })
    }

    @Test fun cachesContentAcrossIdsAndSeparatesProviderRevisions() {
        val question = QuizTextParser.parse(source(1,true),"Тест").quiz.questions.single()
        val file = temporary.root.resolve("answers.json")
        AnswerCache(file,"provider:model:prompt1").store(question, 2)
        val changedId = question.copy(id="another-id")
        assertEquals(2, AnswerCache(file,"provider:model:prompt1").answers(listOf(changedId))[AnswerPolicy.fingerprint(question)])
        assertTrue(AnswerCache(file,"provider:model:prompt2").answers(listOf(question)).isEmpty())
        assertTrue(AnswerCache(file,"provider:model:prompt1").answers(listOf(question.copy(options=question.options.reversed()))).isEmpty())
    }

    @Test fun createsCardsAndPersistsStudyProgressWithoutDuplicates() {
        val unknown = QuizTextParser.parse(source(1,true), "Тест").quiz
        assertTrue(QuizCards.create(unknown,emptyList()).isEmpty())
        val inferred = AnswerPolicy.assumeFirst(unknown)
        val card = QuizCards.create(inferred,emptyList()).single()
        assertEquals(AnswerEvidence.INFERRED, card.evidence)
        assertEquals("Вариант 1",card.back)
        assertTrue(QuizCards.create(inferred,listOf(card)).isEmpty())
        val file = temporary.root.resolve("study.json")
        StudyStore(file).save(StudyData(cards=listOf(QuizCards.rate(card,"easy"))))
        val restored=StudyStore(file).load().cards.single()
        assertEquals(1,restored.reviews)
        assertEquals("easy",restored.lastRating)
        assertEquals(card.sourceQuizId,restored.sourceQuizId)
    }
    @Test fun independentProviderReceivesThirtyQuestionsOnceAndNeverAppliesRuleWithoutConsent() = runBlocking {
        val quiz = QuizTextParser.parse(source(1500,true),"Тест").quiz
        val cache = AnswerCache(temporary.root.resolve("sample.json"),"fake:model:revision1")
        var calls = 0
        var sent = 0
        val provider = IndependentAnswerProvider { questions ->
            calls++
            sent += questions.size
            assertTrue(questions.all { it.correctIndex == null && it.explanation == null })
            questions.associate { AnswerPolicy.fingerprint(it) to 0 }
        }
        assertEquals(SampleAssessment(30,true), SampleAnswerCheck.assess(quiz,cache,provider))
        assertEquals(SampleAssessment(30,true), SampleAnswerCheck.assess(quiz,cache,provider))
        assertEquals(1,calls)
        assertEquals(30,sent)
        assertTrue(quiz.questions.all { it.correctIndex == null })
    }

}
