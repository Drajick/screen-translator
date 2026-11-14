package com.noveltranslator

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val languageCodes = listOf("ko", "ja", "zh", "en", "id")
    private val languageNames = listOf("한국어", "日本語", "中文", "English", "Bahasa Indonesia")
    private lateinit var sourceSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var startButton: Button

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Screen Translator"
        window.statusBarColor = Color.rgb(11, 16, 31)
        window.navigationBarColor = Color.rgb(11, 16, 31)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(32))
            setBackgroundColor(Color.rgb(11, 16, 31))
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = createText("ST", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(111, 91, 255), 16)
        }
        brandRow.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandText.addView(createText("SCREEN TRANSLATOR", 13f, Color.rgb(193, 187, 255), true))
        brandText.addView(createText("TERJEMAHAN LANGSUNG", 11f, Color.rgb(145, 156, 180)))
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(brandRow)

        page.addView(createText("Pahami setiap layar", 27f, Color.WHITE, true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(28)
            })
        page.addView(createText(
            "Deteksi teks dari aplikasi lain dan tampilkan terjemahannya dalam jendela melayang.",
            15f, Color.rgb(166, 177, 201)
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        val languageCard = card()
        languageCard.addView(createText("BAHASA", 12f, Color.rgb(166, 177, 201), true))
        val languageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sourceSpinner = makeSpinner(listOf("Deteksi otomatis") + languageNames)
        targetSpinner = makeSpinner(languageNames)
        targetSpinner.setSelection(languageCodes.indexOf("id"))
        val sourceColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sourceColumn.addView(createText("DARI", 10f, Color.rgb(142, 153, 178), true))
        sourceColumn.addView(sourceSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(7)
        })
        val targetColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        targetColumn.addView(createText("KE", 10f, Color.rgb(142, 153, 178), true))
        targetColumn.addView(targetSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(7)
        })
        languageRow.addView(sourceColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        languageRow.addView(createText("→", 20f, Color.rgb(156, 145, 255), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(15), dp(7), 0)
        })
        languageRow.addView(targetColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        languageCard.addView(languageRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        page.addView(languageCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(24)
        })

        startButton = Button(this).apply {
            text = "Mulai terjemahan layar"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(111, 91, 255), 18)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { beginLiveTranslation() }
        }
        page.addView(startButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)).apply {
            topMargin = dp(18)
        })

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(21, 29, 48), 14)
        }
        statusCard.addView(createText("●", 13f, Color.rgb(129, 114, 255), true))
        statusText = createText("Siap memulai", 13f, Color.rgb(196, 205, 222)).apply {
            setPadding(dp(9), 0, 0, 0)
        }
        statusCard.addView(statusText)
        page.addView(statusCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        val stepsCard = card()
        stepsCard.addView(createText("CARA KERJA", 12f, Color.rgb(193, 187, 255), true))
        stepsCard.addView(createText(
            "1. Izinkan jendela melayang dan perekaman layar.\n2. Pilih satu aplikasi atau seluruh layar.\n3. Buka aplikasinya; terjemahan akan muncul di atasnya.",
            14f, Color.rgb(190, 199, 218)
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })
        page.addView(stepsCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        })
        page.addView(createText(
            "Pengenalan teks dan terjemahan diproses di perangkat. Model bahasa mungkin perlu diunduh saat pertama kali digunakan.",
            12f, Color.rgb(126, 139, 164)
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        })
    }

    private fun makeSpinner(options: List<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, options).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        background = rounded(Color.rgb(31, 41, 64), 12)
        setPadding(dp(9), 0, dp(7), 0)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.rgb(21, 29, 48), 18)
    }

    private fun createText(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun beginLiveTranslation() {
        startButton.isEnabled = false
        statusText.text = "Memeriksa izin…"
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = "Izinkan Screen Translator tampil di atas aplikasi lain"
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
            startActivityForResult(settingsIntent, REQUEST_OVERLAY_PERMISSION)
            return
        }
        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        statusText.text = "Konfirmasi izin perekaman layar Android…"
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE)
    }

    @Deprecated("Deprecated by Android, retained for API 23 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                requestScreenCapture()
            } else {
                statusText.text = "Izin jendela melayang diperlukan untuk menampilkan terjemahan"
                startButton.isEnabled = true
            }
            return
        }
        if (requestCode != REQUEST_SCREEN_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            statusText.text = "Perekaman dibatalkan. Kamu bisa mencoba lagi."
            startButton.isEnabled = true
            return
        }

        val source = if (sourceSpinner.selectedItemPosition == 0) {
            "auto"
        } else {
            languageCodes[sourceSpinner.selectedItemPosition - 1]
        }
        val target = languageCodes[targetSpinner.selectedItemPosition]
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenCaptureService.EXTRA_SOURCE_LANGUAGE, source)
            putExtra(ScreenCaptureService.EXTRA_TARGET_LANGUAGE, target)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            statusText.text = "Terjemahan aktif. Buka aplikasi yang ingin diterjemahkan."
            Toast.makeText(this, "Terjemahan melayang dimulai", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
        } catch (error: Exception) {
            statusText.text = "Tidak dapat memulai: " + (error.message ?: "kesalahan tidak diketahui")
            startButton.isEnabled = true
        }
    }

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1201
        private const val REQUEST_SCREEN_CAPTURE = 1202
    }
}
