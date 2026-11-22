package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class AccountStorageTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun differentUsersHaveIsolatedLibrariesAndReturnToTheirOwnData() {
        val storage=AccountStorage(temporary.root)
        val a=(storage.access("alice") as AccountStorage.Access.Ready).root
        val b=(storage.access("bob") as AccountStorage.Access.Ready).root
        assertNotEquals(a,b);a.mkdirs();File(a,"study.json").writeText("alice-only")
        assertFalse(File(b,"study.json").exists())
        assertEquals("alice-only",File((storage.access("alice") as AccountStorage.Access.Ready).root,"study.json").readText())
    }
    @Test fun legacyClaimKeepsEveryOriginalByteAndDoesNotExposeItToAnotherUid() {
        val file=File(temporary.root,"study.json").apply { writeText("original-data") }
        val storage=AccountStorage(temporary.root)
        assertEquals(AccountStorage.Access.NeedsLegacyOwnership,storage.access("alice"))
        assertEquals(temporary.root,storage.claimLegacy("alice"))
        assertEquals("original-data",file.readText())
        assertEquals(temporary.root,(storage.access("alice") as AccountStorage.Access.Ready).root)
        val other=(storage.access("bob") as AccountStorage.Access.Ready).root
        assertFalse(File(other,"study.json").exists())
        assertThrows(IllegalArgumentException::class.java) { storage.claimLegacy("bob") }
        assertEquals("original-data",file.readText())
    }
    @Test fun uidsCannotEscapeThePrivateRootAndBrokenOwnershipFailsClosed() {
        val storage=AccountStorage(temporary.root)
        val path=(storage.access("../../outside") as AccountStorage.Access.Ready).root.canonicalPath
        assertTrue(path.startsWith(File(temporary.root,"accounts").canonicalPath+File.separator))
        assertThrows(IllegalArgumentException::class.java) { storage.access("") }
        File(temporary.root,".legacy-library-owner-v1").writeText("broken")
        assertThrows(IllegalArgumentException::class.java) { storage.access("alice") }
    }
}
