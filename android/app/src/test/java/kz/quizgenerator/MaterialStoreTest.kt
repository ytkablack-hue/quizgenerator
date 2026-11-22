package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CancellationException

class MaterialStoreTest {
    @get:Rule val folder=TemporaryFolder()
    private val full=(1..1500).joinToString("\n\n") {"Вопрос $it. Қазақша мәтін: Ә Ғ Қ Ң Ө Ұ Ү Һ І. Русский текст без обрезки."}
    @Test fun fullSourceAndExtractedTextSurviveNewStoreAndDoNotTouchOldFiles() {
        val root=folder.newFolder("alice");File(root,"study.json").writeText("existing-study")
        val bytes=full.toByteArray();val first=MaterialStore(root).import("../../экзамен.docx","docx",bytes.inputStream()) {MaterialText(full)}
        val reopened=MaterialStore(root)
        assertTrue(first.characters>12000);assertEquals(full,reopened.text(first.id))
        assertArrayEquals(bytes,reopened.source(first.id).readBytes())
        assertEquals(first,reopened.load(first.id));assertEquals("existing-study",File(root,"study.json").readText())
        assertEquals(1,reopened.list().size)
    }
    @Test fun accountsAreSeparateAndBrokenTextIsReportedWithoutRewrite() {
        val alice=folder.newFolder("alice");val bob=folder.newFolder("bob")
        val store=MaterialStore(alice);val record=store.import("a.txt","txt","text".byteInputStream()) {MaterialText("Текст")}
        assertTrue(MaterialStore(bob).list().isEmpty())
        val text=File(alice,"materials/"+record.id+"/document.txt");text.writeText("Повреждено")
        assertThrows(IllegalArgumentException::class.java) {store.text(record.id)};assertEquals("Повреждено",text.readText())
        assertThrows(IllegalArgumentException::class.java) {store.source("../alice")}
    }
    @Test fun emptyDamagedAndCancelledImportsLeaveOldMaterialUntouched() {
        val store=MaterialStore(folder.root)
        val old=store.import("old.txt","txt","old".byteInputStream()) {MaterialText("Старый текст")}
        assertThrows(IllegalArgumentException::class.java) {store.import("empty","txt",byteArrayOf().inputStream()) {MaterialText("")}}
        assertThrows(IllegalArgumentException::class.java) {store.import("bad","docx","bad".byteInputStream()) {throw IllegalArgumentException("Повреждён DOCX")}}
        assertThrows(CancellationException::class.java) {store.import("cancel","txt","new".byteInputStream(),{throw CancellationException()}) {MaterialText("Новое")}}
        assertEquals("Старый текст",store.text(old.id));assertEquals(1,store.list().size)
        assertFalse(File(folder.root,"materials").listFiles().orEmpty().any {it.name.startsWith(".pending-")})
    }
    @Test fun backupIncludesDurableMaterialButNotForeignOrSecretFiles() {
        val store=MaterialStore(folder.root)
        val record=store.import("text.txt","txt","original".byteInputStream()) {MaterialText("Полный текст")}
        File(folder.root,"materials/"+record.id+"/auth-session.enc").writeText("never-export")
        File(folder.root,"accounts/bob").mkdirs();File(folder.root,"accounts/bob/study.json").writeText("other-account")
        val bytes=java.io.ByteArrayOutputStream();LibraryBackup.write(folder.root,bytes)
        val names=mutableListOf<String>()
        java.util.zip.ZipInputStream(bytes.toByteArray().inputStream()).use {zip -> while(true) {
            val entry=zip.nextEntry ?: break;names+=entry.name;assertFalse(String(zip.readBytes()).contains("never-export"))
        }}
        assertEquals(setOf("source.bin","document.txt","meta.json").map {"materials/"+record.id+"/"+it}.toSet(),names.toSet())
    }
}
