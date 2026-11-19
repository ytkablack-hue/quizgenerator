package kz.quizgenerator

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class AiClientTest {
    @Test fun rejectsInsecureExternalOrCredentialBearingUrls() {
        assertThrows(IllegalArgumentException::class.java) { AiClient.baseUri("http://example.com") }
        assertThrows(IllegalArgumentException::class.java) { AiClient.baseUri("https://user:token@example.com") }
        assertEquals("example.com", AiClient.baseUri("https://example.com/testhelper").host)
    }

    @Test fun acceptsValidatedQuizFromLoopbackAndPreservesSource() {
        val payload = """{"source":"ai","id":"test","title":"Физика","questions":[
            {"id":"one","text":"Единица тока?","options":["Ампер","Вольт"],"correctIndex":0,
            "explanation":"Сила тока измеряется в амперах.","sourceExcerpt":"Ток измеряется в амперах"}
        ]}"""
        withResponse(200, payload, {
            assertEquals("POST /api/v1/ai/quizzes HTTP/1.1", it.line)
            assertEquals("Bearer test-only", it.headers["authorization"])
            assertTrue(it.body.contains("\"count\":1"))
        }) { endpoint ->
            val client = AiClient(StudySettings(server = endpoint, accessToken = "test-only"))
            val quiz = runBlocking { client.quiz("Физика", "", "easy", 1) }
            assertTrue(quiz.generatedByAi)
            assertEquals("Ток измеряется в амперах", quiz.questions.single().sourceExcerpt)
            assertEquals(quiz, QuizJson.parse(QuizJson.serialize(quiz)))
        }
    }

    @Test fun preservesUsefulServerError() {
        withResponse(503, """{"detail":"Сервер занят"}""", {
            assertEquals("GET /api/v1/ai/status HTTP/1.1", it.line)
        }) { endpoint ->
            val client = AiClient(StudySettings(server = endpoint))
            val error = assertThrows(IllegalStateException::class.java) { runBlocking { client.configured() } }
            assertEquals("Сервер занят", error.message)
        }
    }

    private data class Request(val line: String, val headers: Map<String, String>, val body: String)

    private fun withResponse(status: Int, payload: String, check: (Request) -> Unit, exercise: (String) -> Unit) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val task = FutureTask {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = DataInputStream(socket.getInputStream().buffered())
                    fun line() = generateSequence(input::read)
                        .takeWhile { it != -1 && it != '\n'.code }
                        .map(Int::toChar).joinToString("").trimEnd('\r')
                    val first = line()
                    val headers = generateSequence(::line).takeWhile(String::isNotEmpty).associate {
                        it.substringBefore(':').lowercase() to it.substringAfter(':').trim()
                    }
                    val body = ByteArray(headers["content-length"]?.toInt() ?: 0).also(input::readFully)
                    val bytes = payload.toByteArray(Charsets.UTF_8)
                    val response = "HTTP/1.1 $status Test\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(response.toByteArray(Charsets.US_ASCII))
                        write(bytes)
                        flush()
                    }
                    check(Request(first, headers, body.toString(Charsets.UTF_8)))
                }
            }
            val worker = Thread(task).apply { isDaemon = true; start() }
            try {
                exercise("http://127.0.0.1:${server.localPort}")
                task.get(6, TimeUnit.SECONDS)
            } finally {
                server.close()
                worker.join(6000)
            }
        }
    }
}
