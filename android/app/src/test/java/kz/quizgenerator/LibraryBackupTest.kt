package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.concurrent.CancellationException

class LibraryBackupTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun backupContainsOnlyCurrentLibraryAndExcludesLegacyCredentials() {
        val root=temporary.newFolder("alice")
        val quiz=Quiz("bank","Мои вопросы",listOf(QuizQuestion("q","Единица длины?",listOf("Метр","Секунда"),0,null)),
            importProblems=listOf(ImportProblem(2,"Проверить","Исходный фрагмент")))
        File(root,"quiz_banks").mkdirs();File(root,"quiz_banks/bank.json").writeText(QuizJson.serialize(quiz))
        val studyFile=File(root,"study.json");StudyStore(studyFile).save(StudyData())
        val original=JSONObject(studyFile.readText()).apply { getJSONObject("settings").put("serverPassword","never-export").put("serverToken","never-export") }.toString()
        studyFile.writeText(original)
        File(root,"auth-session.enc").writeText("private-session")
        File(root,".legacy-library-owner-v1").writeText("private-owner")
        File(root,"accounts/bob/quiz_banks").mkdirs();File(root,"accounts/bob/quiz_banks/other.json").writeText("other-account")
        val bytes=ByteArrayOutputStream();LibraryBackup.write(root,bytes)
        val entries=mutableMapOf<String,String>()
        ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            while(true) { val entry=zip.nextEntry ?: break;entries[entry.name]=String(zip.readBytes(),Charsets.UTF_8) }
        }
        assertEquals(setOf("study.json","quiz_banks/bank.json"),entries.keys)
        assertEquals(quiz,QuizJson.parse(entries.getValue("quiz_banks/bank.json")))
        assertFalse(entries.values.any { it.contains("never-export") || it.contains("private-session") || it.contains("other-account") })
        assertEquals(original,studyFile.readText())
        assertEquals("private-session",File(root,"auth-session.enc").readText())
    }
    @Test fun cancellationDoesNotModifySourceData() {
        val file=File(temporary.root,"study.json").apply { writeText("{}"); }
        assertThrows(CancellationException::class.java) { LibraryBackup.write(temporary.root,ByteArrayOutputStream()) { throw CancellationException() } }
        assertEquals("{}",file.readText())
    }
}
