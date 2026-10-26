// ============================================================================
// [앱 3] 랜덤 해상도/용량 이미지 무음 알림 발송 및 갤러리 자동 저장기 (RandomPhotoSaverNotifier)
// 패키지명: com.test.app3.photosaver
// 지원 기기: 삼성 갤럭시 S10 (Android 9/10) ~ S25 (Android 15, API 28~35)
// 업데이트 내역:
// 1. 앱 실행 즉시(onCreate) 알림 권한, 저장소 권한(Android 9 이하), 배터리 최적화 제외 자동 요청
// 2. 모든 알림 무음 처리 (IMPORTANCE_LOW + setSilent(true) + 소리/진동 제거)
// 3. [앱3 + 앱4 통합 65% 용량 상한 안전장치]:
//    StatFs로 기기 전체 용량 대비 현재 총 사용률(시스템 + 사진 + 앱4 캐시 포함)을 매회 측정하여,
//    기기 전체 용량의 65%(여유 공간 35% 미만)에 도달하면 사진 파일 추가 저장을 자동 제한하여
//    쾌적한 테스트 환경을 보장합니다.
// ============================================================================
// [AndroidManifest.xml 필수 권한 및 서비스 설정]
// <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
// <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
// <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
// <uses-permission android:name="android.permission.WAKE_LOCK" />
// <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
// <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
// ============================================================================

package com.test.app3.photosaver

import android.Manifest
import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        val titleText = TextView(this).apply {
            text = "앱 3: 랜덤 사진 무음 알림 + 자동 저장"
            textSize = 20f
        }

        statusText = TextView(this).apply {
            text = "상태: 대기 중\n" +
                   "- 앱 실행 시 권한 요청 팝업이 바로 표시됩니다.\n" +
                   "- 랜덤 해상도(400x400 ~ 2400x2400) JPEG를 갤러리(Pictures/TestApp3_RandomPhotos)에 자동 저장합니다.\n" +
                   "- 앱4(랜덤 캐시)와 합산하여 기기 총 용량 점유율이 65%를 넘지 않도록 실시간 보호합니다."
            textSize = 14f
            setPadding(0, 32, 0, 32)
        }

        val start48hBtn = Button(this).apply {
            text = "48시간(999개) 사진 무음 알림+저장 시작"
            setOnClickListener { checkPermissionsAndStart(fastMode = false) }
        }

        val fastBtn = Button(this).apply {
            text = "즉시 3초 간격 무음 테스트 시작 (검증용)"
            setOnClickListener { checkPermissionsAndStart(fastMode = true) }
        }

        val stopBtn = Button(this).apply {
            text = "테스트 중지"
            setOnClickListener {
                stopService(Intent(this@MainActivity, PhotoSaverForegroundService::class.java))
                statusText.text = "상태: 중지됨"
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(start48hBtn)
        layout.addView(fastBtn)
        layout.addView(stopBtn)
        setContentView(layout)

        // 앱 실행 즉시 권한 및 배터리 최적화 제외 요청
        requestPermissionsImmediately()
    }

    private fun requestPermissionsImmediately() {
        val neededPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                neededPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 1003)
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
        val neededPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                neededPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 1003)
            return
        }

        val serviceIntent = Intent(this, PhotoSaverForegroundService::class.java).apply {
            putExtra("FAST_MODE", fastMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        statusText.text = if (fastMode) "상태: 3초 간격 무음 사진 생성 및 자동 저장 중" else "상태: 48시간 무음 사진 알림 및 자동 저장 진행 중"
    }
}

class PhotoSaverForegroundService : Service() {

    private val handlerThread = HandlerThread("PhotoGeneratorThread").apply { start() }
    private lateinit var bgHandler: Handler
    private var sentCount = 0
    private val targetCount = 999
    private var intervalMs = (48L * 60L * 60L * 1000L) / targetCount
    private var wakeLock: PowerManager.WakeLock? = null

    // 기기 전체 저장공간 점유율이 65%를 넘지 않도록 제한 (최소 35% 가용 공간 유지)
    private val maxTotalDeviceUsageRatio = 0.65

    private val photoTask = object : Runnable {
        override fun run() {
            if (sentCount < targetCount) {
                sentCount++
                generateSaveAndNotifyPhoto(sentCount)
                bgHandler.postDelayed(this, intervalMs)
            } else {
                updateForegroundStatus(sentCount, completed = true)
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        bgHandler = Handler(handlerThread.looper)
        createNotificationChannels()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "App3::PhotoSaverWakeLock").apply {
            acquire(49 * 60 * 60 * 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fastMode = intent?.getBooleanExtra("FAST_MODE", false) ?: false
        intervalMs = if (fastMode) 3000L else (48L * 60L * 60L * 1000L) / targetCount
        sentCount = 0

        val fgNotification = buildForegroundNotification(0, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(3, fgNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(3, fgNotification)
        }

        bgHandler.removeCallbacks(photoTask)
        bgHandler.post(photoTask)
        return START_STICKY
    }

    private fun isBelow65PercentStorageCap(): Boolean {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes
        val usedBytes = totalBytes - stat.availableBytes
        val currentUsageRatio = usedBytes.toDouble() / totalBytes.toDouble()
        return currentUsageRatio < maxTotalDeviceUsageRatio
    }

    private fun generateSaveAndNotifyPhoto(index: Int) {
        // 1. 랜덤 가로/세로 해상도 결정 (400px ~ 2400px)
        val width = Random.nextInt(400, 2401)
        val height = Random.nextInt(400, 2401)
        val quality = Random.nextInt(60, 96)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)),
            Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)),
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val circleCount = Random.nextInt(30, 200)
        for (i in 0 until circleCount) {
            paint.color = Color.argb(
                Random.nextInt(100, 255),
                Random.nextInt(256),
                Random.nextInt(256),
                Random.nextInt(256)
            )
            canvas.drawCircle(
                Random.nextFloat() * width,
                Random.nextFloat() * height,
                Random.nextFloat() * (width / 8f),
                paint
            )
        }

        paint.color = Color.WHITE
        paint.textSize = (width / 18f).coerceAtLeast(24f)
        canvas.drawText("Random Photo #$index (${width}x${height})", 40f, height / 2f, paint)

        // 2. 통합 용량 65% 이하일 때만 갤러리에 파일 저장
        val fileName = "TestPhoto_${index}_${width}x${height}_${System.currentTimeMillis()}.jpg"
        val savedSizeKb = if (isBelow65PercentStorageCap()) {
            saveBitmapToGallery(bitmap, fileName, quality)
        } else {
            -1L // 65% 도달 시 저장 생략 표시
        }

        // 3. 알림용 썸네일 축소본 생성
        val previewBitmap = Bitmap.createScaledBitmap(bitmap, 600, (600f * height / width).toInt().coerceAtLeast(300), true)
        bitmap.recycle()

        // 4. 무음 BigPictureStyle 알림 전송
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val summaryMsg = if (savedSizeKb >= 0) {
            "해상도: ${width}x${height} | 크기: 약 ${savedSizeKb}KB"
        } else {
            "기기 용량 65% 상한 도달로 파일 저장 생략 (알림만 표시)"
        }

        val bigPicStyle = NotificationCompat.BigPictureStyle()
            .bigPicture(previewBitmap)
            .setBigContentTitle("사진 알림 #$index")
            .setSummaryText(summaryMsg)

        val notification = NotificationCompat.Builder(this, "PHOTO_SILENT_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(if (savedSizeKb >= 0) "[사진 저장됨 #$index] ${width}x${height} (${savedSizeKb}KB)" else "[용량보호 #$index] 65% 상한으로 저장 생략")
            .setContentText(summaryMsg)
            .setLargeIcon(previewBitmap)
            .setStyle(bigPicStyle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .setAutoCancel(true)
            .setGroup("PHOTO_GROUP_$index")
            .build()

        nm.notify(3000 + index, notification)
        updateForegroundStatus(index)
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, fileName: String, quality: Int): Long {
        var fileSizeKb = 0L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TestApp3_RandomPhotos")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                    resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        fileSizeKb = pfd.statSize / 1024L
                    }
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "TestApp3_RandomPhotos")
                if (!targetDir.exists()) targetDir.mkdirs()
                val outFile = File(targetDir, fileName)
                FileOutputStream(outFile).use { outStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
                }
                fileSizeKb = outFile.length() / 1024L
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fileSizeKb
    }

    private fun updateForegroundStatus(count: Int, completed: Boolean = false) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(3, buildForegroundNotification(count, completed))
    }

    private fun buildForegroundNotification(count: Int, completed: Boolean): Notification {
        val status = if (completed) "사진 저장 완료 ($count/$targetCount)" else "진행 중: $count / $targetCount 장 처리됨 (65% 상한 보호)"
        return NotificationCompat.Builder(this, "PHOTO_SERVICE_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("[앱3] 랜덤 사진 무음 알림 및 자동 저장기")
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
                "PHOTO_SERVICE_CHANNEL",
                "사진 서비스 상태 (무음)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            val alertChannel = NotificationChannel(
                "PHOTO_SILENT_CHANNEL",
                "랜덤 사진 수신 알림 (무음)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(serviceChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        bgHandler.removeCallbacks(photoTask)
        handlerThread.quitSafely()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}