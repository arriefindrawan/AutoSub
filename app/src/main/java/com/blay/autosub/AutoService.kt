package com.blay.autosub

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class AutoService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var running = false
    private var btn: Button? = null
    private val zeroDelayMs = 3000L   // tunggu berapa lama setelah 0 detik sebelum klik Next Video
    private var zeroSince = 0L        // waktu pertama kali terdeteksi 0 detik (0 = belum)

    override fun onServiceConnected() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        btn = Button(this).apply {
            text = "START"
            setOnClickListener {
                running = !running
                zeroSince = 0L
                text = if (running) "STOP" else "START"
                if (running) schedule(500)
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        wm.addView(btn, lp)
    }

    private fun schedule(ms: Long) {
        handler.postDelayed({ if (running) capture() }, ms)
    }

    private fun capture() {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                r.hardwareBuffer.close()
                if (bmp == null) { schedule(2000); return }
                analyze(bmp)
            }
            override fun onFailure(errorCode: Int) { schedule(2000) }
        })
    }

    private fun analyze(bmp: Bitmap) {
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { it.lines }
                val words = lines.flatMap { it.elements }
                val seconds = readSeconds(words)

                // 1) Begitu 0 detik terdeteksi, mulai timer. Setelah zeroDelayMs -> klik NEXT VIDEO
                //    (tetap klik walau angkanya sudah ga kebaca, kecuali sudah > 0 = video sudah pindah)
                val now = System.currentTimeMillis()
                if (seconds == 0 && zeroSince == 0L) zeroSince = now
                if (seconds != null && seconds > 0) zeroSince = 0L
                if (zeroSince != 0L) {
                    if (now - zeroSince < zeroDelayMs) {
                        schedule(500)
                        return@addOnSuccessListener
                    }
                    val box = lines.firstOrNull {
                        it.text.replace(" ", "").contains("NEXTVIDEO", true)
                    }?.boundingBox
                    val x = box?.centerX()?.toFloat() ?: (bmp.width * 0.76f)
                    val y = box?.centerY()?.toFloat() ?: (bmp.height * 0.67f)
                    tap(x, y)
                    zeroSince = 0L
                    schedule(4000)
                    return@addOnSuccessListener
                }
                // 2) Kalau ada tombol play merah -> klik play
                findPlay(bmp)?.let { (px, py) ->
                    tap(px, py); schedule(3000); return@addOnSuccessListener
                }
                schedule(1500)
            }
            .addOnFailureListener { schedule(2000) }
    }

    // Ambil angka tepat di kiri kata "seconds"
    private fun readSeconds(words: List<com.google.mlkit.vision.text.Text.Element>): Int? {
        val sec = words.firstOrNull { it.text.contains("second", true) } ?: return null
        Regex("\\d+").find(sec.text)?.value?.toIntOrNull()?.let { return it }
        val sb = sec.boundingBox ?: return null
        val left = words.filter {
            val b = it.boundingBox
            it !== sec && b != null &&
                Math.abs(b.centerY() - sb.centerY()) < sb.height() &&
                b.centerX() < sb.centerX()
        }.maxByOrNull { it.boundingBox!!.centerX() } ?: return null
        val t = left.text.trim().map {
            when (it) { 'O', 'o', 'D', 'Q' -> '0'; 'l', 'I', '|' -> '1'; else -> it }
        }.joinToString("")
        t.toIntOrNull()?.let { return it }
        // Di kiri "seconds" cuma ada kata "coins" -> angkanya ga kebaca, berarti 0
        if (left.text.contains("coin", true)) return 0
        return null
    }

    // Cari cluster piksel merah YouTube di area video (19%-57% tinggi layar).
    // Step dan ambang menyesuaikan resolusi layar.
    private fun findPlay(b: Bitmap): Pair<Float, Float>? {
        val step = maxOf(2, b.width / 270)
        val y0 = (b.height * 0.19).toInt(); val y1 = (b.height * 0.57).toInt()
        var sx = 0L; var sy = 0L; var n = 0; var total = 0
        for (y in y0 until y1 step step) for (x in 0 until b.width step step) {
            total++
            val p = b.getPixel(x, y)
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val bl = p and 0xFF
            if (r > 220 && g < 40 && bl < 70) { sx += x; sy += y; n++ }
        }
        // tombol play kira-kira >1.2% dari area yang discan
        return if (total > 0 && n > total * 0.012) Pair(sx.toFloat() / n, sy.toFloat() / n) else null
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build()
        dispatchGesture(g, null, null)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() { running = false }
}
