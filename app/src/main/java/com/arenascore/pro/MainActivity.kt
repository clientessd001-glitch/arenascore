package com.arenascore.pro

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results: Array<Uri>? = if (result.resultCode == RESULT_OK && result.data?.data != null) {
            arrayOf(result.data!!.data!!)
        } else {
            null
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#070b14")
        window.navigationBarColor = Color.parseColor("#070b14")

        webView = WebView(this)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallbackArg: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = filePathCallbackArg

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                return try {
                    fileChooserLauncher.launch(
                        Intent.createChooser(intent, "Escolher Escudo da Equipe")
                    )
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }

        webView.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Sair do ArenaScore Pro?")
                    .setMessage("Os dados da partida, relatório da arbitragem e elencos já estão salvos automaticamente no aparelho.")
                    .setPositiveButton("Sair") { _, _ -> finish() }
                    .setNegativeButton("Continuar no Jogo", null)
                    .show()
            }
        })
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun sharePdf(base64Data: String, fileName: String, shareText: String) {
            try {
                val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val pdfBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val pdfDir = File(cacheDir, "pdfs").apply { mkdirs() }
                val pdfFile = File(pdfDir, cleanName)
                FileOutputStream(pdfFile).use { it.write(pdfBytes) }

                val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    pdfFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                runOnUiThread {
                    startActivity(
                        Intent.createChooser(shareIntent, "Compartilhar Súmula Oficial (PDF)")
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Erro ao compartilhar PDF: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun savePdf(base64Data: String, fileName: String) {
            try {
                val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val pdfBytes = Base64.decode(base64Data, Base64.DEFAULT)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, cleanName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    )
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { it.write(pdfBytes) }
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Súmula salva em Downloads: $cleanName",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return
                    }
                }

                sharePdf(base64Data, cleanName, "Súmula Oficial ArenaScore Pro")
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Erro ao salvar PDF: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun copyText(text: String) {
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ArenaScore Resumo", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    this@MainActivity,
                    "Resumo copiado! Cole no WhatsApp.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
