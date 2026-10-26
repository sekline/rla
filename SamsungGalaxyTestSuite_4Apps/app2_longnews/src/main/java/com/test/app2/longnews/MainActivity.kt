// ============================================================================
// [앱 2] 48시간 내 999개 장문(BigTextStyle) 무음 알림 생성기 (LongTextNotifier)
// 패키지명: com.test.app2.longnews
// 지원 기기: 삼성 갤럭시 S10 (Android 9/10) ~ S25 (Android 15, API 28~35)
// 업데이트 내역:
// 1. 컴파일 오류 수정 완료: android.R.drawable.ic_menu_agenda 소문자 상수로 교정
// 2. 앱을 켜자마자(onCreate) 즉시 알림 권한 및 배터리 최적화 제외 팝업 자동 요청
// 3. 모든 장문 알림 완전 무음 처리 (IMPORTANCE_LOW + setSilent(true) + 소리/진동 제거)
// ============================================================================
// [AndroidManifest.xml 필수 권한 및 서비스 설정]
// <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
// <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
// <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
// <uses-permission android:name="android.permission.WAKE_LOCK" />
// <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
// ============================================================================

package com.test.app2.longnews

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
            text = "앱 2: 48시간 999개 장문(BigText) 무음 알림"
            textSize = 20f
        }

        statusText = TextView(this).apply {
            text = "상태: 대기 중 (약 173초 간격으로 장문 무음 알림 999개 발송)\n" +
                   "※ 앱 실행 시 권한 요청 팝업이 자동으로 표시됩니다."
            textSize = 14f
            setPadding(0, 32, 0, 32)
        }

        val startBtn = Button(this).apply {
            text = "48시간(999개) 장문 무음 알림 시작"
            setOnClickListener { checkPermissionsAndStart(fastMode = false) }
        }

        val fastTestBtn = Button(this).apply {
            text = "즉시 1초 간격 장문 무음 테스트 (검증용)"
            setOnClickListener { checkPermissionsAndStart(fastMode = true) }
        }

        val stopBtn = Button(this).apply {
            text = "테스트 중지"
            setOnClickListener {
                stopService(Intent(this@MainActivity, LongNewsForegroundService::class.java))
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
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
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
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
                return
            }
        }

        val serviceIntent = Intent(this, LongNewsForegroundService::class.java).apply {
            putExtra("FAST_MODE", fastMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        statusText.text = if (fastMode) "상태: 고속 장문 무음 발송 중 (1초 간격)" else "상태: 48시간 999개 장문 무음 발송 진행 중"
    }
}

class LongNewsForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var sentCount = 0
    private val targetCount = 999
    private var intervalMs = (48L * 60L * 60L * 1000L) / targetCount
    private var wakeLock: PowerManager.WakeLock? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (sentCount < targetCount) {
                sentCount++
                sendLongNotification(sentCount)
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
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "App2::LongNewsWakeLock").apply {
            acquire(49 * 60 * 60 * 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fastMode = intent?.getBooleanExtra("FAST_MODE", false) ?: false
        intervalMs = if (fastMode) 1000L else (48L * 60L * 60L * 1000L) / targetCount
        sentCount = 0

        val fgNotification = buildForegroundNotification(0, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, fgNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(2, fgNotification)
        }

        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        return START_STICKY
    }

    private fun generateLongArticle(index: Int, timeStr: String): String {
        return """
            [심층 기획 리포트 #$index | 발송시각: $timeStr]
            본 알림은 삼성 갤럭시 S10부터 S25 시리즈까지 One UI 알림 센터의 대용량 장문 텍스트(BigTextStyle) 렌더링 성능과 SystemUI 메모리 점유율을 검증하기 위해 자동 생성된 장문 뉴스 본문입니다.
            
            1. 테스트 배경 및 목적:
            모바일 운영체제에서 확장형 알림(Expandable Notification)이 다수 누적될 때 알림 드로어(Notification Shade)의 스크롤 프레임 방어율과 직렬화(Parcel) 오버헤드를 정밀 측정합니다.
            
            2. 주요 점검 항목:
            - 장문 문자열(약 1,000자~1,500자) 포함 시 알림 펼침/접기 애니메이션 응답 속도
            - 48시간 연속 999회 수신 시 SystemUI 프로세스 힙 메모리 추이
            - 다국어 및 특수문자, 줄바꿈 레이아웃 깨짐 여부 확인
            
            3. 상세 본문 데이터 스트림 (회차 고유 시드: ${UUID.randomUUID()}):
            차세대 모바일 환경에서는 고밀도 정보 전달과 백그라운드 동기화가 동시에 이루어집니다. 본 테스트 패킷은 실제 장문 뉴스 기사 전문에 상응하는 텍스트 페이로드를 포함하고 있으며, 각 알림 인스턴스마다 고유한 문자열 버퍼를 할당하여 캐시 중복 제거를 방지합니다.
        """.trimIndent()
    }

    private fun sendLongNotification(index: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
        val longBody = generateLongArticle(index, timeStr)

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle("[심층뉴스 #$index/$targetCount] 장문 알림 스트레스 테스트 리포트")
            .bigText(longBody)
            .setSummaryText("장문 뉴스 아카이브 #$index")

        val notification = NotificationCompat.Builder(this, "LONG_SILENT_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("[장문뉴스 #$index] 심층 기획 리포트 도착 ($timeStr)")
            .setContentText(longBody.take(60) + "...")
            .setStyle(bigTextStyle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup("LONG_NEWS_GROUP_$index")
            .build()

        nm.notify(2000 + index, notification)
    }

    private fun updateForegroundStatus(count: Int, completed: Boolean = false) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2, buildForegroundNotification(count, completed))
    }

    private fun buildForegroundNotification(count: Int, completed: Boolean): Notification {
        val status = if (completed) "장문 발송 완료 ($count/$targetCount)" else "장문 진행 중: $count / $targetCount 발송됨"
        return NotificationCompat.Builder(this, "LONG_SERVICE_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("[앱2] 장문 뉴스 무음 스케줄러")
            .setContentText(status)
            .setOngoing(!completed)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val serviceChannel = NotificationChannel(
                "LONG_SERVICE_CHANNEL",
                "장문 서비스 상태 (무음)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            val newsChannel = NotificationChannel(
                "LONG_SILENT_CHANNEL",
                "장문 뉴스 알림 (무음)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(serviceChannel)
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