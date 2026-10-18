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

    override fun onServiceConnected() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        btn = Button(this).apply {
            text = "START"
            setOnClickListener {
                running = !running
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
        // 1) Cari tombol play YouTube (merah) di area video
        findPlay(bmp)?.let { (x, y) ->
            tap(x, y); schedule(3000); return
        }
        // 2) OCR: baca "seconds" dan "NEXT VIDEO"
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { it.lines }
                val secLine = lines.firstOrNull { it.text.contains("second", true) }
                var seconds: Int? = null
                if (secLine != null) {
                    seconds = Regex("\\d+").find(secLine.text)?.value?.toIntOrNull()
                    if (seconds == null) {
                        // angka mungkin terpisah dari kata "seconds"
                        val cy = secLine.boundingBox?.centerY() ?: 0
                        seconds = lines.filter {
                            val b = it.boundingBox
                            b != null && Math.abs(b.centerY() - cy) < 60 &&
                                b.centerX() < (secLine.boundingBox?.centerX() ?: 0)
                        }.firstNotNullOfOrNull { Regex("^\\d+$").find(it.text.trim())?.value?.toInt() }
                    }
                }
                if (seconds == 0) {
                    val next = lines.firstOrNull {
                        it.text.replace(" ", "").contains("NEXTVIDEO", true)
                    }?.boundingBox
                    if (next != null) {
                        tap(next.centerX().toFloat(), next.centerY().toFloat())
                        schedule(4000); return@addOnSuccessListener
                    }
                }
                schedule(1500)
            }
            .addOnFailureListener { schedule(2000) }
    }

    // Cari cluster piksel merah YouTube di area video (19%-57% tinggi layar)
    private fun findPlay(b: Bitmap): Pair<Float, Float>? {
        var sx = 0L; var sy = 0L; var n = 0
        val y0 = (b.height * 0.19).toInt(); val y1 = (b.height * 0.57).toInt()
        for (y in y0 until y1 step 4) for (x in 0 until b.width step 4) {
            val p = b.getPixel(x, y)
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val bl = p and 0xFF
            if (r > 220 && g < 40 && bl < 70) { sx += x; sy += y; n++ }
        }
        return if (n > 800) Pair(sx.toFloat() / n, sy.toFloat() / n) else null
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
