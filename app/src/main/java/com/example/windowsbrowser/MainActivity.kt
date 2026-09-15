package com.example.windowsbrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private var screenshotCounter = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        addressBar = findViewById(R.id.addressBar)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.isForMainFrame) {
                    view.loadUrl(request.url.toString())
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                addressBar.setText(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                addressBar.setText(url)
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Real file-download bridge for URLs exposed by WebView.
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startSystemDownload(url, userAgent, contentDisposition, mimeType)
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btnHome).setOnClickListener {
            webView.loadUrl(getString(R.string.home_url))
        }
        findViewById<ImageButton>(R.id.btnGo).setOnClickListener { loadFromAddressBar() }
        addressBar.setOnEditorActionListener { _, _, _ ->
            loadFromAddressBar()
            true
        }

        findViewById<ImageButton>(R.id.btnToggleUI).setOnClickListener {
            val panel = findViewById<View>(R.id.buttonPanel)
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<ImageButton>(R.id.btnScreenshot).setOnClickListener { takeScreenshotJpg() }
        findViewById<ImageButton>(R.id.btnSavePage).setOnClickListener { savePage() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(getString(R.string.home_url))
        }
    }

    private fun startSystemDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) addRequestHeader("Cookie", cookies)
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadFromAddressBar() {
        var url = addressBar.text.toString().trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = if (url.contains(".") && !url.contains(" ")) {
                "https://$url"
            } else {
                "https://www.google.com/search?q=" + Uri.encode(url)
            }
        }
        webView.loadUrl(url)
    }

    private fun takeScreenshotJpg() {
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        webView.draw(canvas)

        screenshotCounter++
        val dir = File(getExternalFilesDir(null), "Screenshots").apply { mkdirs() }
        val file = File(dir, "screenshot_$screenshotCounter.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        bitmap.recycle()
        Toast.makeText(this, getString(R.string.screenshot_saved, file.absolutePath), Toast.LENGTH_LONG).show()
    }

    private fun savePage() {
        val dir = File(getExternalFilesDir(null), "SavedPages").apply { mkdirs() }
        val file = File(dir, "page_${System.currentTimeMillis()}.mht")
        webView.saveWebArchive(file.absolutePath)
        Toast.makeText(this, getString(R.string.page_saved, file.absolutePath), Toast.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Android compatibility")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
