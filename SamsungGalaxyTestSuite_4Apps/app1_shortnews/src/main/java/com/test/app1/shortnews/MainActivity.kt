// ============================================================================
// [앱 1] 48시간 내 999개 뉴스형 단문 무음 알림 생성기 (ShortNewsNotifier)
// 패키지명: com.test.app1.shortnews
// 지원 기기: 삼성 갤럭시 S10 (Android 9/10) ~ S25 (Android 15, API 28~35)
// 업데이트 내역:
// 1. 앱을 켜자마자(onCreate) 즉시 알림 권한 및 배터리 최적화 제외 팝업 자동 요청
// 2. 모든 알림 무음 처리 (IMPORTANCE_LOW + setSilent(true) + 소리/진동 제거)
// ============================================================================
// [AndroidManifest.xml 필수 권한 및 서비스 설정]
// <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
// <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
// <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
// <uses-permission android:name="android.permission.WAKE_LOCK" />
// <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
//
// <application ...>
//     <activity android:name=".MainActivity" android:exported="true">
//         <intent-filter>
//             <action android:name="android.intent.action.MAIN" />
//             <category android:name="android.intent.category.LAUNCHER" />
//         </intent-filter>
//     </activity>
//     <service
//         android:name=".ShortNewsForegroundService"
//         android:enabled="true"
//         android:exported="false"
//         android:foregroundServiceType="dataSync" />
// </application>
// ============================================================================

package com.test.app1.shortnews

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        val titleText = TextView(this).apply {
            text = "앱 1: 48시간 999개 단문 뉴스 (무음 알림)"
            textSize = 20f
        }

        statusText = TextView(this).apply {
            text = "상태: 대기 중 (약 173초 간격으로 무음 알림 999개 발송)\n" +
                   "※ 앱 실행 시 권한 요청 팝업이 자동으로 표시됩니다."
            textSize = 14f
            setPadding(0, 32, 0, 32)
        }

        val startBtn = Button(this).apply {
            text = "48시간(999개) 무음 알림 시작"
            setOnClickListener {
                checkPermissionsAndStart(fastMode = false)
            }
        }

        val fastTestBtn = Button(this).apply {
            text = "즉시 1초 간격 무음 테스트 (검증용)"
            setOnClickListener {
                checkPermissionsAndStart(fastMode = true)
            }
        }

        val stopBtn = Button(this).apply {
            text = "테스트 중지"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ShortNewsForegroundService::class.java))
                statusText.text = "상태: 중지됨"
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(startBtn)
        layout.addView(fastTestBtn)
        layout.addView(stopBtn)
        setContentView(layout)

        // 앱 실행 즉시 권한 및 배터리 최적화 제외 요청
        requestPermissionsImmediately()
    }

    private fun requestPermissionsImmediately() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun checkPermissionsAndStart(fastMode: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                return
            }
        }

        val serviceIntent = Intent(this, ShortNewsForegroundService::class.java).apply {
            putExtra("FAST_MODE", fastMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        statusText.text = if (fastMode) "상태: 검증용 고속 무음 발송 중 (1초 간격)" else "상태: 48시간 999개 무음 발송 진행 중 (약 173초 간격)"
    }
}

class ShortNewsForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var sentCount = 0
    private val targetCount = 999
    // 48시간 = 172,800,000 ms -> 999개 나눔 = 약 172,972 ms (약 2분 53초)
    private var intervalMs = (48L * 60L * 60L * 1000L) / targetCount
    private var wakeLock: PowerManager.WakeLock? = null

    private val newsHeadlines = listOf(
        "[속보] 글로벌 IT 컨퍼런스 차세대 모바일 칩셋 발표",
        "[경제] 코스피 장중 변동성 확대, 주요 기술주 강세",
        "[사회] 주말 전국 대체로 맑음, 일교차 주의보 발령",
        "[테크] 차세대 스마트폰 배터리 효율 25% 향상 연구 공개",
        "[문화] 하반기 기대작 영화 예매율 1위 기록 경신",
        "[스포츠] 국가대표 평가전 후반 추가골로 극적 승리",
        "[세계] 주요국 중앙은행 금리 동결 기조 유지 전망"
    )

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (sentCount < targetCount) {
                sentCount++
                sendNewsNotification(sentCount)
                updateForegroundStatus(sentCount)
                handler.postDelayed(this, intervalMs)
            } else {
                updateForegroundStatus(sentCount, completed = true)
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "App1::ShortNewsWakeLock").apply {
            acquire(49 * 60 * 60 * 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fastMode = intent?.getBooleanExtra("FAST_MODE", false) ?: false
        intervalMs = if (fastMode) 1000L else (48L * 60L * 60L * 1000L) / targetCount
        sentCount = 0

        val fgNotification = buildForegroundNotification(0, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, fgNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, fgNotification)
        }

        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        return START_STICKY
    }

    private fun sendNewsNotification(index: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        val headline = newsHeadlines[index % newsHeadlines.size]

        val notification = NotificationCompat.Builder(this, "NEWS_SILENT_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("뉴스 브리핑 #$index / $targetCount")
            .setContentText("$headline ($timeStr)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup("SHORT_NEWS_GROUP_$index")
            .build()

        nm.notify(1000 + index, notification)
    }

    private fun updateForegroundStatus(count: Int, completed: Boolean = false) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildForegroundNotification(count, completed))
    }

    private fun buildForegroundNotification(count: Int, completed: Boolean): Notification {
        val status = if (completed) "발송 완료 ($count/$targetCount)" else "진행 중: $count / $targetCount 발송됨"
        return NotificationCompat.Builder(this, "SERVICE_STATUS_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("[앱1] 단문 뉴스 무음 스케줄러")
            .setContentText(status)
            .setOngoing(!completed)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val statusChannel = NotificationChannel(
                "SERVICE_STATUS_CHANNEL",
                "백그라운드 진행 상태 (무음)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            val newsChannel = NotificationChannel(
                "NEWS_SILENT_CHANNEL",
                "단문 뉴스 알림 (무음)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "48시간 동안 999개의 뉴스형 일반 알림을 소리/진동 없이 수신합니다."
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(statusChannel)
            nm.createNotificationChannel(newsChannel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}