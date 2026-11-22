package kz.quizgenerator
import java.io.File
import java.security.MessageDigest

/** The legacy library stays in place. Ownership is explicit and never inferred from email. */
internal class AccountStorage(private val files: File) {
    sealed interface Access {
        data class Ready(val root: File): Access
        data object NeedsLegacyOwnership: Access
    }
    private val marker get()=File(files,".legacy-library-owner-v1")
    private fun identity(uid: String): String {
        require(uid.isNotBlank() && uid.length<=128) { "Некорректный аккаунт" }
        return MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun owner(): String? = if(!marker.exists())null else { require(marker.length() in 64..66) { "Повреждена запись владельца. Файлы не изменены" };marker.readText().trim() }.also {
        require(it == null || it.matches(Regex("[a-f0-9]{64}"))) { "Не удалось прочитать владельца локальной библиотеки. Файлы не изменены" }
    }
    private fun hasLegacy(): Boolean = File(files,"study.json").exists() || File(files,"active-attempt.json").exists() ||
        File(files,"quiz_banks").listFiles { f -> f.isFile && f.extension=="json" }.orEmpty().isNotEmpty()
    fun access(uid: String): Access = synchronized(lock) {
        val hash=identity(uid)
        when(val owner=owner()) {
            hash -> Access.Ready(files)
            null -> if(hasLegacy())Access.NeedsLegacyOwnership else Access.Ready(File(files,"accounts/$hash"))
            else -> Access.Ready(File(files,"accounts/$hash"))
        }
    }
    fun claimLegacy(uid: String): File = synchronized(lock) {
        val hash=identity(uid)
        val old=owner()
        require(old==null || old==hash) { "Старая библиотека уже закреплена за другим аккаунтом" }
        if(old==null)atomicWrite(marker,hash)
        files
    }
    private companion object { val lock=Any() }
}
