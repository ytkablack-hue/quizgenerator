package kz.quizgenerator

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/** Explicitly user-started foreground work. Disk checkpoints survive; force-stop requires manual resume. */
class BatchService: Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var running: Job?=null
    private var wake: android.os.PowerManager.WakeLock?=null
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Обработка учебного материала",NotificationManager.IMPORTANCE_LOW))}
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        val uid=intent?.getStringExtra("uid");val id=intent?.getStringExtra("job")
        if(uid==null || id==null){stopSelf();return START_NOT_STICKY}
        if(intent.action==PAUSE){if(active==(uid to id))running?.cancel();else if(running?.isActive!=true)stopSelf();return START_NOT_STICKY}
        if(running?.isActive==true)return START_NOT_STICKY
        active=uid to id
        try {val notification=notification(uid,id,"Подготовка обработки")
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(NOTIFICATION,notification)
        }catch(_: Exception){active=null;stopSelf();return START_NOT_STICKY}
        wake=runCatching {getSystemService(android.os.PowerManager::class.java).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"TestHelper:batch").apply {acquire(60*60*1000L)}}.getOrNull()
        running=scope.launch {
            var guard: Job?=null
            try {
                val auth=AuthSessions.get(applicationContext)
                require(auth.account?.uid==uid && auth.account?.verified==true)
                val access=AccountStorage(filesDir).access(uid) as? AccountStorage.Access.Ready ?: error("library unavailable")
                val store=BatchJobStore(access.root,uid);val engine=BatchEngine(store)
                val original=store.load(id);val restored=store.restore(original)
                if(restored!=original)store.save(restored)
                if(restored.status !in listOf(BatchStatus.READY,BatchStatus.RUNNING))return@launch
                val client=AiClient(StudySettings(),authToken={check(auth.account?.uid==uid);auth.token().also {check(auth.account?.uid==uid)}})
                val owner=currentCoroutineContext()[Job]
                guard=launch {while(isActive){if(auth.account?.uid!=uid){owner?.cancel();break};delay(500)}}
                while(isActive) {
                    if(auth.account?.uid!=uid)break
                    val j=store.load(id)
                    if(j.status !in listOf(BatchStatus.READY,BatchStatus.RUNNING))break
                    val cm=getSystemService(ConnectivityManager::class.java);val network=cm.activeNetwork
                    if(network==null || cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)!=true){store.update(id){it.copy(status=BatchStatus.PAUSED,notice="Нет соединения. Готовые результаты сохранены")};break}
                    val wait=maxOf(j.nextAt,store.readyAt())-System.currentTimeMillis()
                    if(wait>0){update(uid,id,"Ожидание следующего запроса · ${j.actual} сохранено");delay(wait.coerceAtMost(22000));continue}
                    val next=engine.step(id){route,body,requestId->check(auth.account?.uid==uid);client.batch(route,body,requestId)}
                    update(uid,id,"Сохранено ${next.actual} · ${next.done} из ${next.parts.size} партий")
                    if(next.status !in listOf(BatchStatus.READY,BatchStatus.RUNNING))break
                }
            }catch(e: CancellationException){throw e}catch(_: Exception){/* No content, tokens or documents in Android logs. Disk SENDING is recovered as uncertain. */}
            finally {guard?.cancel();wake?.let {if(it.isHeld)it.release()};wake=null;active=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
        }
        return START_NOT_STICKY
    }
    private fun notification(uid: String,id: String,text: String): android.app.Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pause=PendingIntent.getService(this,1,Intent(this,BatchService::class.java).setAction(PAUSE).putExtra("uid",uid).putExtra("job",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_batch_notification).setContentTitle("TestHelper · Обработка материала").setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(0,"Пауза",pause).build()
    }
    private fun update(uid: String,id: String,text: String){getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification(uid,id,text))}
    override fun onDestroy(){wake?.let {if(it.isHeld)it.release()};wake=null;running?.cancel();scope.cancel();super.onDestroy()}
    override fun onTimeout(startId: Int,fgsType: Int){running?.cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    companion object {
        private const val CHANNEL="study_batches"
        private const val NOTIFICATION=8701
        private const val PAUSE="kz.quizgenerator.PAUSE_BATCH"
        @Volatile internal var active: Pair<String,String>?=null;private set
        internal fun start(context: Context,uid: String,id: String) {context.startForegroundService(Intent(context,BatchService::class.java).putExtra("uid",uid).putExtra("job",id))}
        internal fun pause(context: Context,uid: String,id: String) {if(active==(uid to id))context.startService(Intent(context,BatchService::class.java).setAction(PAUSE).putExtra("uid",uid).putExtra("job",id))}
    }
}
