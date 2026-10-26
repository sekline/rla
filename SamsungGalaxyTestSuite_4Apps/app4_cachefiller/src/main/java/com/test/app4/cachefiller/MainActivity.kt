// ============================================================================
// [앱 4] 48시간 동안 휴대폰 용량을 랜덤 크기 캐시 파일로 채우되 총 점유율 65%를 넘지 않는 앱 (GradualCacheFiller)
// 패키지명: com.test.app4.cachefiller
// 지원 기기: 삼성 갤럭시 S10 (Android 9/10) ~ S25 (Android 15, API 28~35)
// 업데이트 내역:
// 1. 앱 실행 즉시(onCreate) 알림 권한 및 배터리 최적화 제외 팝업 자동 요청
// 2. 모든 진행 알림 무음 처리 (IMPORTANCE_LOW + setSilent(true) + 소리/진동 제거)
// 3. [앱3(사진 저장) + 앱4(랜덤 캐시) 합산 최대 65% 용량 상한 제어]:
//    - 앱3가 999장의 랜덤 사진(약 1~3GB)을 저장하는 공간과 기기 기본 시스템 영역을 모두 포함하여,
//      기기 전체 저장공간 점유율이 절대 65%(최소 가용 여유 공간 35% 보장)를 넘지 않도록 실시간 StatFs로 제어합니다.
//    - 목표 점유율을 50% ~ 60%(+사진 앱 여유분 포함 최대 65% 상한) 사이에서 랜덤으로 설정하고,
//      1MB ~ 64MB 사이의 랜덤 크기 파일로 분산 생성합니다.
// ============================================================================
// [AndroidManifest.xml 필수 권한 및 서비스 설정]
// <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
// <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
// <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
// <uses-permission android:name="android.permission.WAKE_LOCK" />
// <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
// ============================================================================

package com.test.app4.cachefiller

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
import java.io.File
import java.io.FileOutputStream
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
            text = "앱 4: 48시간 랜덤 캐시 점유 (사진앱 합산 최대 65% 상한)"
            textSize = 20f
        }

        statusText = TextView(this).apply {
            text = getStorageSummaryText()
            textSize = 14f
            setPadding(0, 32, 0, 32)
        }

        val start48hBtn = Button(this).apply {
            text = "48시간 점진적 캐시 채우기 시작 (최대 65% 이내)"
            setOnClickListener { checkPermissionsAndStart(fastMode = false) }
        }

        val fastBtn = Button(this).apply {
            text = "고속 캐시 채우기 시작 (검증용 - 5초 간격)"
            setOnClickListener { checkPermissionsAndStart(fastMode = true) }
        }

        val clearBtn = Button(this).apply {
            text = "생성된 캐시 파일 전체 삭제"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CacheFillerForegroundService::class.java))
                val targetDir = File(cacheDir, "random_cache_chunks")
                targetDir.deleteRecursively()
                externalCacheDir?.let { File(it, "random_cache_chunks").deleteRecursively() }
                statusText.text = "캐시 삭제 완료!\n" + getStorageSummaryText()
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(start48hBtn)
        layout.addView(fastBtn)
        layout.addView(clearBtn)
        setContentView(layout)

        // 앱 실행 즉시 권한 및 배터리 최적화 제외 요청
        requestPermissionsImmediately()
    }

    private fun requestPermissionsImmediately() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1004)
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

    private fun getStorageSummaryText(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / (1024.0 * 1024.0 * 1024.0)
        val availGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
        val usedPercent = ((stat.totalBytes - stat.availableBytes).toDouble() / stat.totalBytes.toDouble()) * 100.0
        return String.format(
            "기기 전체 용량: %.2f GB\n현재 가용 용량: %.2f GB (현재 사용률: %.1f%%)\n" +
            "※ 사진 저장 앱(앱3)과 본 캐시 앱(앱4)을 모두 합쳐 기기 전체 사용률이 55%%~65%%를 절대 넘지 않도록 자동 조절됩니다.",
            totalGb, availGb, usedPercent
        )
    }

    private fun checkPermissionsAndStart(fastMode: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1004)
                return
            }
        }

        val serviceIntent = Intent(this, CacheFillerForegroundService::class.java).apply {
            putExtra("FAST_MODE", fastMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        statusText.text = "상태: 무음 캐시 생성 서비스 실행 중 (사진앱 합산 최대 65% 안전 상한 적용)"
    }
}

class CacheFillerForegroundService : Service() {

    private val handlerThread = HandlerThread("CacheFillerThread").apply { start() }
    private lateinit var bgHandler: Handler
    private var wakeLock: PowerManager.WakeLock? = null

    private var targetBytesToFill: Long = 0L
    private var filledBytesSoFar: Long = 0L
    private var targetTotalDevicePercent: Int = 60 // 기기 전체 목표 점유율 (55~63% 랜덤 설정 + 사진앱 여유분 = 최대 65% 이하)
    private var totalSteps: Int = 960 // 48시간 * 20회/시간 (3분 간격) = 960 스텝
    private var currentStep: Int = 0
    private var stepIntervalMs: Long = 3 * 60 * 1000L
    private var createdFileCount: Int = 0

    // 앱3(사진 저장)와 합산하여 기기 전체 용량의 65%를 절대 초과하지 않도록 실시간 마지노선 설정 (최소 35% 여유 공간 유지)
    private val hardCapTotalUsageRatio = 0.65
    // 앱3(랜덤 사진 999장, 약 2.5GB)가 사용할 여유 버퍼 공간 예약
    private val reservedForPhotoAppBytes = 2500L * 1024L * 1024L

    private val fillRunnable = object : Runnable {
        override fun run() {
            // 매 스텝마다 실시간 기기 가용 용량을 다시 확인하여 앱3(사진)가 늘어난 만큼 캐시 한도를 동적 조정
            val dynamicAllowedBytes = getRemainingSafeBytesUnder65Cap()

            if (currentStep < totalSteps && filledBytesSoFar < targetBytesToFill && dynamicAllowedBytes > 1024L * 1024L) {
                currentStep++
                val remainingSteps = (totalSteps - currentStep + 1).coerceAtLeast(1)
                val remainingTarget = (targetBytesToFill - filledBytesSoFar).coerceAtMost(dynamicAllowedBytes).coerceAtLeast(0L)
                val quotaForThisStep = remainingTarget / remainingSteps

                writeRandomSizedCacheFiles(quotaForThisStep)
                updateForegroundStatus(completed = false)
                bgHandler.postDelayed(this, stepIntervalMs)
            } else {
                updateForegroundStatus(completed = true)
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        bgHandler = Handler(handlerThread.looper)
        createNotificationChannel()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "App4::CacheFillerWakeLock").apply {
            acquire(49 * 60 * 60 * 1000L)
        }
    }

    // 실시간으로 기기 전체 사용률이 65%(최소 여유 공간 35%)를 넘지 않기 위해 추가로 쓸 수 있는 바이트 계산
    private fun getRemainingSafeBytesUnder65Cap(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes
        val availBytes = stat.availableBytes
        val minRequiredFreeBytes = (totalBytes * (1.0 - hardCapTotalUsageRatio)).toLong()
        return (availBytes - minRequiredFreeBytes).coerceAtLeast(0L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fastMode = intent?.getBooleanExtra("FAST_MODE", false) ?: false

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalDeviceBytes = stat.totalBytes
        val currentUsedBytes = totalDeviceBytes - stat.availableBytes

        // 사진 앱(앱3)과 합쳐서 최종 55% ~ 65% 사이가 되도록 랜덤 목표 설정
        targetTotalDevicePercent = Random.nextInt(55, 65) // 55% ~ 64%
        val maxAllowedTotalUsedBytes = (totalDeviceBytes * (targetTotalDevicePercent / 100.0)).toLong()

        // 현재 이미 사용 중인 용량과 사진 앱 예약 용량(약 2.5GB)을 빼고 캐시가 채울 목표량 산출
        val rawTargetForCache = (maxAllowedTotalUsedBytes - currentUsedBytes - reservedForPhotoAppBytes).coerceAtLeast(0L)
        targetBytesToFill = rawTargetForCache.coerceAtMost(getRemainingSafeBytesUnder65Cap())

        filledBytesSoFar = 0L
        createdFileCount = 0
        currentStep = 0

        if (fastMode) {
            totalSteps = 100
            stepIntervalMs = 5000L
        } else {
            totalSteps = 960
            stepIntervalMs = (48L * 60L * 60L * 1000L) / totalSteps
        }

        val fgNotification = buildForegroundNotification(completed = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(4, fgNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(4, fgNotification)
        }

        bgHandler.removeCallbacks(fillRunnable)
        bgHandler.post(fillRunnable)
        return START_STICKY
    }

    private fun writeRandomSizedCacheFiles(quotaBytes: Long) {
        val baseDir = File(cacheDir, "random_cache_chunks")
        if (!baseDir.exists()) baseDir.mkdirs()

        var writtenInStep = 0L
        val buffer = ByteArray(256 * 1024)

        while (writtenInStep < quotaBytes && filledBytesSoFar < targetBytesToFill) {
            val safeRemaining = getRemainingSafeBytesUnder65Cap()
            if (safeRemaining <= 1024L * 1024L) break // 기기 전체 점유율 65% 도달 시 즉시 멈춤

            val minFileBytes = 1L * 1024L * 1024L
            val maxFileBytes = 64L * 1024L * 1024L
            val randomFileSize = Random.nextLong(minFileBytes, maxFileBytes + 1)
                .coerceAtMost(quotaBytes - writtenInStep + minFileBytes)
                .coerceAtMost(targetBytesToFill - filledBytesSoFar)
                .coerceAtMost(safeRemaining)

            if (randomFileSize <= 0L) break

            createdFileCount++
            val chunkFile = File(baseDir, "cache_chunk_${createdFileCount}_${randomFileSize / 1024}KB.bin")

            try {
                FileOutputStream(chunkFile).use { fos ->
                    var bytesLeft = randomFileSize
                    Random.nextBytes(buffer)
                    while (bytesLeft > 0) {
                        val toWrite = minOf(bytesLeft, buffer.size.toLong()).toInt()
                        fos.write(buffer, 0, toWrite)
                        bytesLeft -= toWrite
                    }
                    fos.fd.sync()
                }
                writtenInStep += randomFileSize
                filledBytesSoFar += randomFileSize
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }
    }

    private fun updateForegroundStatus(completed: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(4, buildForegroundNotification(completed))
    }

    private fun buildForegroundNotification(completed: Boolean): Notification {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalUsedPercent = ((stat.totalBytes - stat.availableBytes).toDouble() / stat.totalBytes.toDouble()) * 100.0
        val filledGb = filledBytesSoFar / (1024.0 * 1024.0 * 1024.0)
        val targetGb = targetBytesToFill / (1024.0 * 1024.0 * 1024.0)

        val statusText = if (completed) {
            String.format("완료: 캐시 %.2fGB (%d개 파일) | 기기 총 사용률 %.1f%% (65%% 이하 유지)", filledGb, createdFileCount, totalUsedPercent)
        } else {
            String.format("캐시 %.2f/%.2fGB (파일 %d개) | 기기 총 사용률 %.1f%% (상한 65%%)", filledGb, targetGb, createdFileCount, totalUsedPercent)
        }

        return NotificationCompat.Builder(this, "CACHE_FILLER_SILENT_CHANNEL")
            .setSmallIcon(android.R.drawable.stat_notify_sdcard)
            .setContentTitle("[앱4] 랜덤 캐시 무음 점유 (최대 65% 보호)")
            .setContentText(statusText)
            .setOngoing(!completed)
            .setSilent(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                "CACHE_FILLER_SILENT_CHANNEL",
                "캐시 점유 진행 상태 (무음)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        bgHandler.removeCallbacks(fillRunnable)
        handlerThread.quitSafely()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}