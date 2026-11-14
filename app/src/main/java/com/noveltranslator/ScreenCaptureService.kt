package com.noveltranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScreenCaptureService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val codes = listOf("ko", "ja", "zh", "en", "id")
    private val languageNames = mapOf(
        "ko" to "한국어", "ja" to "日本語", "zh" to "中文",
        "en" to "English", "id" to "Bahasa Indonesia"
    )
    private var sourceChoice = "auto"
    private var targetLanguage = "id"
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var windowManager: WindowManager? = null
    private var overlay: LinearLayout? = null
    private var overlayState: TextView? = null
    private var overlaySource: TextView? = null
    private var overlayResult: TextView? = null
    private var recognizers: List<TextRecognizer> = emptyList()
    private val languageIdentifier by lazy { LanguageIdentification.getClient() }
    private var translator: Translator? = null
    private var translatorSource: String? = null
    private var translationRunning = false
    private var queuedRequest: TranslationRequest? = null
    private var latestRecognizedText = ""
    private var captureStartedAt = 0L
    private var lastFrameAt = 0L
    @Volatile private var analyzingFrame = false
    @Volatile private var destroyed = false

    private data class TranslationRequest(val text: String, val source: String)

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        captureThread = HandlerThread("ScreenTranslatorOCR").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)
        recognizers = listOf(
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        sourceChoice = intent.getStringExtra(EXTRA_SOURCE_LANGUAGE) ?: "auto"
        targetLanguage = intent.getStringExtra(EXTRA_TARGET_LANGUAGE) ?: "id"
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = getResultData(intent)
        if (resultData == null || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        try {
            showOverlay()
            captureStartedAt = SystemClock.elapsedRealtime()
            startCapture(resultCode, resultData)
            updateOverlay(state = "Beralih ke aplikasi…")
        } catch (error: Exception) {
            updateOverlay(state = "Gagal memulai perekaman: " + (error.message ?: "kesalahan"))
            mainHandler.postDelayed({ stopSelf() }, 1800L)
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun getResultData(source: Intent): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        source.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        source.getParcelableExtra(EXTRA_RESULT_DATA)
    }

    private fun startForegroundCompat() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Screen Translator", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Screen Translator aktif")
            .setContentText("Perekaman berlanjut saat kamu menggunakan aplikasi lain")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Berhenti", stopPending).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun screenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        windowManager!!.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
            ?: throw IllegalStateException("Izin perekaman layar tidak diberikan")
        projection!!.registerCallback(projectionCallback, mainHandler)
        val metrics = screenMetrics()
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        if (width <= 0 || height <= 0) throw IllegalStateException("Ukuran layar tidak valid")

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also {
            it.setOnImageAvailableListener({ reader -> onFrameAvailable(reader, width, height) }, captureHandler)
        }
        virtualDisplay = projection!!.createVirtualDisplay(
            "ScreenTranslatorCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, captureHandler
        ) ?: throw IllegalStateException("Tidak dapat membuat layar virtual")
    }

    private fun onFrameAvailable(reader: ImageReader, width: Int, height: Int) {
        val image = try { reader.acquireLatestImage() } catch (_: Exception) { null } ?: return
        val now = SystemClock.elapsedRealtime()
        if (destroyed || analyzingFrame || now - captureStartedAt < 2400L || now - lastFrameAt < 1300L) {
            image.close()
            return
        }
        analyzingFrame = true
        lastFrameAt = now
        var bitmap: Bitmap? = null
        try {
            val plane = image.planes[0]
            val paddedWidth = maxOf(width, plane.rowStride / plane.pixelStride)
            val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            plane.buffer.rewind()
            padded.copyPixelsFromBuffer(plane.buffer)
            bitmap = if (paddedWidth == width) padded else Bitmap.createBitmap(padded, 0, 0, width, height).also {
                padded.recycle()
            }
        } catch (_: Exception) {
            analyzingFrame = false
            updateOverlay(state = "Tidak dapat membaca layar")
        } finally {
            image.close()
        }
        val frameBitmap = bitmap
        if (frameBitmap == null) return
        try {
            val input = InputImage.fromBitmap(frameBitmap, 0)
            val tasks = recognizers.map { it.process(input) }
            Tasks.whenAllComplete(tasks).addOnCompleteListener {
                try {
                    if (destroyed) return@addOnCompleteListener
                    val candidates = tasks.mapIndexedNotNull { index, task ->
                        if (task.isSuccessful) (task.result as? Text)?.text?.let { index to it } else null
                    }
                    val best = candidates.maxByOrNull { candidateRank(it.first, it.second) }?.second.orEmpty().trim()
                    if (best.isNotEmpty()) acceptRecognizedText(best)
                } finally {
                    if (!frameBitmap.isRecycled) frameBitmap.recycle()
                    analyzingFrame = false
                }
            }
        } catch (_: Exception) {
            if (!frameBitmap.isRecycled) frameBitmap.recycle()
            analyzingFrame = false
            updateOverlay(state = "Tidak dapat mengenali teks")
        }
    }

    private fun candidateRank(index: Int, text: String): Int {
        val detected = scriptLanguage(text)
        val expected = when (index) {
            1 -> "ja"
            2 -> "zh"
            3 -> "ko"
            else -> null
        }
        val preferred = if (detected == expected || (detected == null && index == 0)) 100000 else 0
        return preferred + text.count { it.isLetterOrDigit() }
    }

    private fun scriptLanguage(text: String): String? = when {
        text.any { it in '\uac00'..'\ud7af' } -> "ko"
        text.any { it in '\u3040'..'\u30ff' } -> "ja"
        text.any { it in '\u4e00'..'\u9fff' } -> "zh"
        else -> null
    }

    private fun acceptRecognizedText(rawText: String) {
        val text = rawText.trim().take(2500)
        if (text.length < 2 || text == latestRecognizedText) return
        latestRecognizedText = text
        updateOverlay(source = text, state = "Mendeteksi bahasa…")
        if (sourceChoice != "auto" && sourceChoice in codes) {
            enqueueTranslation(text, sourceChoice)
            return
        }
        val script = scriptLanguage(text)
        if (script != null) {
            enqueueTranslation(text, script)
            return
        }
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { detected ->
                val source = detected.substringBefore('-').takeIf { it in codes } ?: "en"
                enqueueTranslation(text, source)
            }
            .addOnFailureListener { enqueueTranslation(text, "en") }
    }

    private fun enqueueTranslation(text: String, source: String) {
        if (destroyed) return
        val request = TranslationRequest(text, source)
        if (translationRunning) {
            queuedRequest = request
            updateOverlay(source = text, state = "Menyiapkan terjemahan…")
        } else {
            runTranslation(request)
        }
    }

    private fun runTranslation(request: TranslationRequest) {
        if (request.source == targetLanguage) {
            updateOverlay(source = request.text, translated = request.text, state = "Teks sudah dalam bahasa tujuan")
            runQueuedRequest()
            return
        }
        translationRunning = true
        updateOverlay(source = request.text, state = "Memuat model terjemahan…")
        val activeTranslator = getTranslator(request.source)
        activeTranslator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                activeTranslator.translate(request.text)
                    .addOnSuccessListener { translated ->
                        translationRunning = false
                        val next = queuedRequest
                        if (next == null) {
                            updateOverlay(
                                source = request.text,
                                translated = translated,
                                state = (languageNames[request.source] ?: request.source) + " → " +
                                    (languageNames[targetLanguage] ?: targetLanguage)
                            )
                        }
                        runQueuedRequest()
                    }
                    .addOnFailureListener { error -> translationFailed(request, error.message) }
            }
            .addOnFailureListener { error -> translationFailed(request, error.message) }
    }

    private fun getTranslator(source: String): Translator {
        if (translator == null || translatorSource != source) {
            translator?.close()
            translatorSource = source
            translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(targetLanguage)
                    .build()
            )
        }
        return translator!!
    }

    private fun translationFailed(request: TranslationRequest, message: String?) {
        translationRunning = false
        if (queuedRequest == null) {
            updateOverlay(source = request.text, translated = "", state = "Gagal menerjemahkan: " + (message ?: "coba lagi"))
        }
        runQueuedRequest()
    }

    private fun runQueuedRequest() {
        val next = queuedRequest ?: return
        queuedRequest = null
        runTranslation(next)
    }

    @Suppress("DEPRECATION")
    private fun showOverlay() {
        val wm = windowManager ?: return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(244, 18, 25, 42))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(100, 132, 119, 255))
            }
            elevation = dp(8).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "SCREEN TRANSLATOR  •  LIVE"
            textSize = 11f
            setTextColor(Color.rgb(202, 196, 255))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
        header.addView(title, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val stop = TextView(this).apply {
            text = "BERHENTI"
            textSize = 10f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(8), dp(11), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(95, 68, 155))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { stopSelf() }
        }
        header.addView(stop)
        container.addView(header)
        overlayState = TextView(this).apply {
            text = "Memulai perekaman…"
            textSize = 11f
            setTextColor(Color.rgb(157, 169, 194))
            includeFontPadding = false
            setPadding(0, dp(8), 0, dp(9))
        }
        container.addView(overlayState)
        container.addView(TextView(this).apply {
            text = "TEKS TERDETEKSI"
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(135, 148, 175))
            includeFontPadding = false
        })
        overlaySource = TextView(this).apply {
            text = "Mencari teks di layar…"
            textSize = 13f
            setTextColor(Color.rgb(220, 226, 239))
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(0, dp(4), 0, dp(9))
        }
        container.addView(overlaySource)
        container.addView(TextView(this).apply {
            text = "TERJEMAHAN"
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(158, 146, 255))
            includeFontPadding = false
        })
        overlayResult = TextView(this).apply {
            text = "Hasil terjemahan akan muncul di sini"
            textSize = 15f
            setTextColor(Color.WHITE)
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(0, dp(4), 0, 0)
        }
        container.addView(overlayResult)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val width = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(260))
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(42)
        }
        header.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downX = 0f
            private var downY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        downX = event.rawX
                        downY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val maxX = ((resources.displayMetrics.widthPixels - container.width) / 2).coerceAtLeast(0)
                        val maxY = (resources.displayMetrics.heightPixels - container.height).coerceAtLeast(0)
                        params.x = (startX + (event.rawX - downX).toInt()).coerceIn(-maxX, maxX)
                        params.y = (startY + (event.rawY - downY).toInt()).coerceIn(0, maxY)
                        try { wm.updateViewLayout(container, params) } catch (_: Exception) { }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
                }
                return false
            }
        })
        try {
            wm.addView(container, params)
            overlay = container
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun updateOverlay(source: String? = null, translated: String? = null, state: String? = null) {
        val change = Runnable {
            if (destroyed) return@Runnable
            if (source != null) overlaySource?.text = source
            if (translated != null) overlayResult?.text = translated
            if (state != null) overlayState?.text = state
        }
        if (Looper.myLooper() == Looper.getMainLooper()) change.run() else mainHandler.post(change)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        destroyed = true
        try { projection?.unregisterCallback(projectionCallback) } catch (_: Exception) { }
        try { projection?.stop() } catch (_: Exception) { }
        virtualDisplay?.release()
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        captureThread?.quitSafely()
        recognizers.forEach { try { it.close() } catch (_: Exception) { } }
        try { languageIdentifier.close() } catch (_: Exception) { }
        translator?.close()
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "screen_translator.result_code"
        const val EXTRA_RESULT_DATA = "screen_translator.result_data"
        const val EXTRA_SOURCE_LANGUAGE = "screen_translator.source_language"
        const val EXTRA_TARGET_LANGUAGE = "screen_translator.target_language"
        private const val ACTION_STOP = "com.noveltranslator.STOP_CAPTURE"
        private const val CHANNEL_ID = "screen_translation_capture"
        private const val NOTIFICATION_ID = 901
    }
}
