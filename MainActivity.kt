package com.noveltranslator

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class MainActivity : Activity() {

    private val codes = listOf("ko", "ja", "zh", "en", "id")
    private val names = mapOf(
        "ko" to "Korea", "ja" to "Jepang", "zh" to "Mandarin",
        "en" to "Inggris", "id" to "Indonesia"
    )

    private lateinit var srcSpinner: Spinner
    private lateinit var tgtSpinner: Spinner
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var status: TextView
    private lateinit var translateBtn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "📖 Novel Translator"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Baris pilihan bahasa
        val langRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        srcSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Deteksi otomatis") + codes.map { names[it]!! }
            )
        }
        tgtSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                codes.map { names[it]!! }
            )
            setSelection(codes.indexOf("id"))
        }
        val arrow = TextView(this).apply { text = "  →  "; textSize = 18f }
        langRow.addView(srcSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        langRow.addView(arrow)
        langRow.addView(tgtSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(langRow)

        // Input teks
        input = EditText(this).apply {
            hint = "Tempel teks novel di sini…"
            textSize = 17f
            minLines = 6
            maxLines = 12
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setLineSpacing(0f, 1.2f)
        }
        root.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        // Tombol
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pasteBtn = Button(this).apply { text = "Tempel"; setOnClickListener { paste() } }
        val clearBtn = Button(this).apply {
            text = "Hapus"
            setOnClickListener { input.setText(""); output.text = ""; status.text = "" }
        }
        translateBtn = Button(this).apply { text = "Terjemahkan"; setOnClickListener { onTranslate() } }
        btnRow.addView(pasteBtn)
        btnRow.addView(clearBtn)
        btnRow.addView(translateBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(btnRow)

        status = TextView(this).apply { textSize = 12f }
        root.addView(status)

        // Hasil
        output = TextView(this).apply {
            hint = "Hasil terjemahan muncul di sini."
            textSize = 17f
            setLineSpacing(0f, 1.25f)
            setTextIsSelectable(true)
            setPadding(dp(4), dp(12), dp(4), dp(12))
        }
        root.addView(output)

        val copyBtn = Button(this).apply { text = "Salin hasil"; setOnClickListener { copy() } }
        root.addView(copyBtn)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun paste() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!t.isNullOrEmpty()) input.setText(t)
    }

    private fun copy() {
        val t = output.text.toString()
        if (t.isEmpty()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("terjemahan", t))
        Toast.makeText(this, "Disalin", Toast.LENGTH_SHORT).show()
    }

    // Cadangan deteksi bahasa lewat aksara Unicode
    private fun fallbackDetect(t: String): String = when {
        Regex("[\\uac00-\\ud7af]").containsMatchIn(t) -> "ko"
        Regex("[\\u3040-\\u30ff]").containsMatchIn(t) -> "ja"
        Regex("[\\u4e00-\\u9fff]").containsMatchIn(t) -> "zh"
        else -> "en"
    }

    private fun onTranslate() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        translateBtn.isEnabled = false
        output.text = ""
        val to = codes[tgtSpinner.selectedItemPosition]
        val srcPos = srcSpinner.selectedItemPosition

        if (srcPos == 0) {
            status.text = "Mendeteksi bahasa…"
            LanguageIdentification.getClient().identifyLanguage(text)
                .addOnSuccessListener { c ->
                    val code = c.substringBefore('-')
                    runTranslate(text, if (code in codes) code else fallbackDetect(text), to)
                }
                .addOnFailureListener { runTranslate(text, fallbackDetect(text), to) }
        } else {
            runTranslate(text, codes[srcPos - 1], to)
        }
    }

    private fun fail(e: Exception) {
        status.text = "Gagal: ${e.message}"
        translateBtn.isEnabled = true
    }

    private fun runTranslate(text: String, from: String, to: String) {
        if (from == to) {
            output.text = text
            status.text = "Bahasa asal dan tujuan sama."
            translateBtn.isEnabled = true
            return
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(from)
                .setTargetLanguage(to)
                .build()
        )

        status.text = "Menyiapkan model bahasa (pertama kali butuh internet)…"
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                val paras = text.split("\n")
                val results = MutableList(paras.size) { "" }

                fun step(i: Int) {
                    if (i >= paras.size) {
                        output.text = results.joinToString("\n")
                        status.text = "Selesai (${names[from]} → ${names[to]})"
                        translator.close()
                        translateBtn.isEnabled = true
                        return
                    }
                    if (paras[i].isBlank()) { step(i + 1); return }
                    status.text = "Menerjemahkan ${i + 1}/${paras.size}…"
                    translator.translate(paras[i])
                        .addOnSuccessListener { results[i] = it; step(i + 1) }
                        .addOnFailureListener { fail(it) }
                }
                step(0)
            }
            .addOnFailureListener { fail(it) }
    }
}
