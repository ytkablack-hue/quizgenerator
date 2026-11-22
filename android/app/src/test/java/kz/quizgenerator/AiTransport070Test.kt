package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
class AiTransport070Test {
 @Test fun realHttpTopicAndCardsWireContractNeverIncludesDocumentAndMapsQuotaAndFormat()=runBlocking {
    val received=mutableListOf<JSONObject>();val requests=AtomicInteger();val status=AtomicInteger(200);val broken=AtomicInteger();val authorization=AtomicReference<String>()
    val server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=8000
    val workerError=AtomicReference<Throwable>()
    val worker=Thread {
      try {repeat(4){server.accept().use {socket->
        socket.soTimeout=5000;val input=socket.getInputStream();val header=ByteArrayOutputStream()
        while(true){val c=input.read();require(c>=0);header.write(c);require(header.size()<=16384);if(header.toString("US-ASCII").endsWith("\r\n\r\n"))break}
        val lines=header.toString("US-ASCII").split("\r\n");val length=lines.first {it.startsWith("Content-Length:",true)}.substringAfter(':').trim().toInt();require(length in 1..65536)
        val bytes=ByteArray(length);var offset=0;while(offset<length){val n=input.read(bytes,offset,length-offset);require(n>0);offset+=n}
        requests.incrementAndGet();authorization.set(lines.first {it.startsWith("Authorization:",true)}.substringAfter(':').trim());val body=JSONObject(bytes.toString(Charsets.UTF_8));synchronized(received){received+=body}
        val n=body.optInt("count",1)
        val data=if(status.get()!=200)JSONObject().put("detail","quota").toString() else if(broken.get()==1)"not json" else if(lines.first().contains("/flashcards "))JSONObject().put("cards",JSONArray(List(n){JSONObject().put("front","Вопрос").put("back","Ответ")})).toString() else QuizJson.serialize(Quiz("00000000-0000-0000-0000-000000000001","Законы Ньютона",List(n){QuizQuestion("q$it","Вопрос $it",listOf("Да","Нет"),0,explanation="Пояснение",evidence=AnswerEvidence.MANUAL)},generatedByAi=true))
        val response=data.toByteArray(Charsets.UTF_8);socket.getOutputStream().use {out->out.write(("HTTP/1.1 "+status.get()+" "+(if(status.get()==200)"OK" else "Too Many Requests")+"\r\nContent-Type: application/json\r\nContent-Length: "+response.size+"\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII));out.write(response);out.flush()}
      }}}
      catch(t: Throwable){if(!server.isClosed)workerError.set(t)}
    }.apply {isDaemon=true;start()}
    try {
        val client=AiClient(StudySettings(),authToken={"mock-firebase-jwt"},endpoint="http://127.0.0.1:"+server.localPort)
        val payload=AiGeneration.build(AiInputMode.TOPIC,"Законы Ньютона","Свой текст","НЕ ОТПРАВЛЯТЬ ДОКУМЕНТ","Инструкции","medium",2)
        val quiz=client.quiz(payload.getString("topic"),payload.getString("material"),"medium",2);assertEquals(2,quiz.questions.size);assertTrue(quiz.questions.all {it.evidence==AnswerEvidence.CLOUD_AI})
        val cards=client.cards("Законы Ньютона","","medium",1);assertEquals(1,cards.size);assertEquals(AnswerEvidence.CLOUD_AI,cards[0].evidence)
        assertEquals("Bearer mock-firebase-jwt",authorization.get());assertEquals(2,requests.get());assertTrue(received.all {it.getString("material")=="" && !it.toString().contains("ДОКУМЕНТ")})
        try {client.quiz("Тема","","medium",26);fail("count must fail before network")}catch(_: IllegalArgumentException){};assertEquals(2,requests.get())
        status.set(429);try {client.quiz("Тема","","medium",1);fail("quota expected")}catch(e: IllegalStateException){assertTrue(e.message!!.contains("Лимит"))};assertEquals(3,requests.get())
        status.set(200);broken.set(1);try {client.quiz("Тема","","medium",1);fail("format expected")}catch(e: IllegalStateException){assertTrue(e.message!!.contains("формат"))};assertEquals(4,requests.get())
    } finally {server.close();worker.join(1000)}
    assertNull(workerError.get())
 }
}
