package com.fastbrowser.xp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import android.net.Uri
import android.os.Environment
import android.os.Bundle
import android.view.KeyEvent
import android.os.Handler
import android.os.Looper
import android.util.Xml
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import android.view.View
import android.view.animation.AlphaAnimation
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebView.HitTestResult
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import android.content.Intent
import android.webkit.ValueCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var control: TextView
    private lateinit var selectIndicator: View
    private lateinit var control2Indicator: View
    private var control2Mode = false
    private var control2StartX = -1f
    private var control2StartY = -1f
    private var selectMode = false
    private var textScale = 100
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val historyPrefs by lazy { getSharedPreferences("browser_history", MODE_PRIVATE) }
    private data class BrowserTab(var url: String = "about:blank", var title: String = "New Tab")
    private val tabs = mutableListOf<BrowserTab>()
    private var currentTab = 0
    private lateinit var tabBar: LinearLayout
    private lateinit var webArea: FrameLayout
    private lateinit var floatingWheel: LinearLayout
    private lateinit var btnShowAll: TextView
    private lateinit var newsTicker: LinearLayout
    private lateinit var newsText: TextView
    private lateinit var btnNewsSource: TextView
    private lateinit var btnNewsTranslate: TextView
    private lateinit var btnHideNews: TextView
    private var chromeBarsHidden = false
    private var newsHidden = false
    private var newsSourceIndex = 0
    private var currentNewsText = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private val newsSources = listOf(
        "DW فارسی/عربی" to "https://rss.dw.com/syndication/feeds/MENA_RSS_GNS_AR.42103-copypaste.html",
        "DW English" to "https://rss.dw.com/syndication/feeds/VAS_CB_Eng_OurVoice.31791-cb.html"
    )

    private val homeUrl = "https://chatgpt.com/"
    private val chatGptUrl = "https://chatgpt.com/"
    private val githubUrl = "https://github.com/matlabyab-maker/Fast-Browser-XP"
    private val tokenUrl = "https://github.com/settings/tokens"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.webView)
        address = findViewById(R.id.addressBar)
        control = findViewById(R.id.btnControl)
        selectIndicator = findViewById(R.id.selectIndicator)
        control2Indicator = findViewById(R.id.control2Indicator)
        selectIndicator.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.rgb(135,206,235)) }
        tabBar = findViewById(R.id.tabBar)
        webArea = findViewById(R.id.webArea)
        floatingWheel = findViewById(R.id.floatingWheel)
        btnShowAll = findViewById(R.id.btnShowAll)
        newsTicker = findViewById(R.id.newsTicker)
        newsText = findViewById(R.id.newsText)
        btnNewsSource = findViewById(R.id.btnNewsSource)
        btnNewsTranslate = findViewById(R.id.btnNewsTranslate)
        btnHideNews = findViewById(R.id.btnHideNews)
        newsText.isSelected = true
        newsText.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        setupFloatingWheel()
        setupNewsTicker()
        tabs.add(BrowserTab(homeUrl, "Fast Browser XP"))
        refreshTabBar()

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.useWideViewPort = true
        s.loadWithOverviewMode = false
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.textZoom = textScale
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                address.setText(url)
                if (tabs.isNotEmpty()) {
                    tabs[currentTab].url = url
                    tabs[currentTab].title = view.title?.takeIf { it.isNotBlank() } ?: "New Tab"
                    refreshTabBar()
                }
                saveHistory(url)
                if (selectMode) installSelectMode()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST)
                    true
                } catch (_: Exception) {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = null
                    false
                }
            }
        }

        web.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            startDownload(url, userAgent, contentDisposition, mimetype)
        })

        // Long-press an image (or downloadable link) to open our own download dialog.
        web.setOnLongClickListener {
            val hit = web.hitTestResult
            val type = hit?.type ?: HitTestResult.UNKNOWN_TYPE
            val extra = hit?.extra
            when {
                (type == HitTestResult.IMAGE_TYPE || type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) && !extra.isNullOrBlank() -> {
                    showImageDownloadDialog(extra, web.url)
                    true
                }
                type == HitTestResult.SRC_ANCHOR_TYPE && !extra.isNullOrBlank() && isHttpUrl(extra) -> {
                    showImageDownloadDialog(extra, web.url)
                    true
                }
                else -> false
            }
        }

        web.setOnTouchListener { _, event ->
            if (!control2Mode) return@setOnTouchListener false
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                if (control2StartX < 0f) {
                    control2StartX = event.x
                    control2StartY = event.y
                    Toast.makeText(this, "نقطه اول ثبت شد؛ نقطه دوم را بزنید.", Toast.LENGTH_SHORT).show()
                } else {
                    val endX = event.x
                    val endY = event.y
                    copySelectedRegion(control2StartX, control2StartY, endX, endY)
                    control2StartX = -1f
                    control2StartY = -1f
                }
                return@setOnTouchListener true
            }
            true
        }

        findViewById<TextView>(R.id.btnBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<TextView>(R.id.btnForward).setOnClickListener { if (web.canGoForward()) web.goForward() }
        findViewById<TextView>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<TextView>(R.id.btnHome).setOnClickListener { web.loadUrl(homeUrl) }
        findViewById<TextView>(R.id.btnChatGPT).setOnClickListener { web.loadUrl(chatGptUrl) }
        findViewById<TextView>(R.id.btnToken).setOnClickListener { web.loadUrl(tokenUrl) }
        findViewById<TextView>(R.id.btnRepo).setOnClickListener { web.loadUrl(githubUrl) }
        findViewById<TextView>(R.id.btnBookmark).setOnClickListener { showBookmarks() }
        findViewById<TextView>(R.id.btnHistory).setOnClickListener { showHistory() }
        findViewById<TextView>(R.id.btnDownloads).setOnClickListener { showDownloads() }
        findViewById<TextView>(R.id.btnGo).setOnClickListener { openAddress() }
        address.setOnEditorActionListener { _, _, _ -> openAddress(); true }

        control.setOnClickListener { toggleSelectMode() }
        findViewById<TextView>(R.id.btnControl2).setOnClickListener { toggleControl2Mode() }
        findViewById<TextView>(R.id.btnSelect).setOnClickListener { toggleSelectMode() }
        findViewById<TextView>(R.id.btnWheel).setOnClickListener { showWheel() }
        findViewById<TextView>(R.id.btnScreenshot).setOnClickListener {
            Toast.makeText(this, "برای ذخیره تصویر از قابلیت Screenshot دستگاه استفاده کنید.", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.btnSavePage).setOnClickListener {
            Toast.makeText(this, "ذخیره صفحه وب به‌صورت فایل در این نسخه فعال نیست.", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.btnNewTab).setOnClickListener { createNewTab() }
        findViewById<TextView>(R.id.btnEnterTop).setOnClickListener { sendEnterToWeb() }
        findViewById<TextView>(R.id.btnEnterBottom).setOnClickListener { sendEnterToWeb() }

        web.loadUrl(savedInstanceState?.getString("last_url") ?: homeUrl)
    }

    private fun setupFloatingWheel() {
        findViewById<TextView>(R.id.btnWheelUp).setOnClickListener { web.scrollBy(0, -(web.height * 0.72f).toInt().coerceAtLeast(120)) }
        findViewById<TextView>(R.id.btnWheelDown).setOnClickListener { web.scrollBy(0, (web.height * 0.72f).toInt().coerceAtLeast(120)) }
        findViewById<TextView>(R.id.btnWheelReset).setOnClickListener { web.scrollTo(0, 0) }
        findViewById<TextView>(R.id.btnHideAll).setOnClickListener { setChromeBarsHidden(true) }
        btnShowAll.setOnClickListener { setChromeBarsHidden(false); newsHidden = false; newsTicker.visibility = View.VISIBLE }
    }

    private fun setChromeBarsHidden(hidden: Boolean) {
        chromeBarsHidden = hidden
        val topTitle = findViewById<View>(R.id.titleBar)
        val topNav = topTitle.nextSiblingView()
        val tab = findViewById<View>(R.id.tabScroller)
        val addressRow = tab.nextSiblingView()
        topTitle.visibility = if (hidden) View.GONE else View.VISIBLE
        topNav?.visibility = if (hidden) View.GONE else View.VISIBLE
        tab.visibility = if (hidden) View.GONE else View.VISIBLE
        addressRow?.visibility = if (hidden) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnNewTab).parent?.parent?.let { (it as? View)?.visibility = if (hidden) View.GONE else View.VISIBLE }
        floatingWheel.visibility = if (hidden) View.GONE else View.VISIBLE
        btnShowAll.visibility = if (hidden) View.VISIBLE else View.GONE
    }

    private fun View.nextSiblingView(): View? {
        val p = parent as? android.view.ViewGroup ?: return null
        val i = p.indexOfChild(this)
        return if (i >= 0 && i + 1 < p.childCount) p.getChildAt(i + 1) else null
    }

    private fun setupNewsTicker() {
        btnNewsSource.setOnClickListener {
            newsSourceIndex = (newsSourceIndex + 1) % newsSources.size
            loadNews()
        }
        btnNewsTranslate.setOnClickListener { translateCurrentNews() }
        btnHideNews.setOnClickListener {
            newsHidden = true
            newsTicker.visibility = View.GONE
        }
        loadNews()
    }

    private fun loadNews() {
        btnNewsSource.text = if (newsSourceIndex == 0) "DW AR" else "DW EN"
        val source = newsSources[newsSourceIndex]
        Thread {
            try {
                val conn = URL(source.second).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "Fast-Browser-XP/1.0")
                val input = BufferedInputStream(conn.inputStream)
                val parser = Xml.newPullParser()
                parser.setInput(input, "UTF-8")
                val titles = mutableListOf<String>()
                var event = parser.eventType
                var insideTitle = false
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && titles.size < 12) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name.equals("title", true)) insideTitle = true
                    else if (event == org.xmlpull.v1.XmlPullParser.TEXT && insideTitle) {
                        val t = parser.text.trim()
                        if (t.isNotBlank() && !titles.contains(t)) titles.add(t)
                        insideTitle = false
                    } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name.equals("title", true)) insideTitle = false
                    event = parser.next()
                }
                input.close(); conn.disconnect()
                val result = titles.filter { !it.equals("DW", true) && !it.equals("World News", true) }.take(8)
                mainHandler.post {
                    currentNewsText = if (result.isEmpty()) "NEWS: خبری دریافت نشد" else result.joinToString("     •     ")
                    newsText.text = currentNewsText
                    newsText.isSelected = true
                    newsText.scrollTo(0, 0)
                }
            } catch (_: Exception) {
                mainHandler.post { newsText.text = "NEWS: اتصال خبر در دسترس نیست" }
            }
        }.start()
    }

    private fun translateCurrentNews() {
        val text = currentNewsText.trim()
        if (text.isBlank()) return
        btnNewsTranslate.text = "…"
        Thread {
            try {
                val q = Uri.encode(text.take(1200))
                val u = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=fa&dt=t&q=$q")
                val conn = u.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "Fast-Browser-XP/1.0")
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONArray(body)
                val chunks = root.optJSONArray(0)
                val translated = buildString {
                    if (chunks != null) {
                        for (i in 0 until chunks.length()) {
                            val part = chunks.optJSONArray(i)
                            val t = part?.optString(0).orEmpty()
                            if (t.isNotBlank()) append(t)
                        }
                    }
                }
                mainHandler.post {
                    if (translated.isNotBlank()) {
                        currentNewsText = translated
                        newsText.text = translated
                        newsText.isSelected = true
                    } else Toast.makeText(this, "ترجمه دریافت نشد.", Toast.LENGTH_SHORT).show()
                    btnNewsTranslate.text = "→FA"
                }
                conn.disconnect()
            } catch (_: Exception) {
                mainHandler.post {
                    btnNewsTranslate.text = "→FA"
                    Toast.makeText(this, "ترجمه فعلاً در دسترس نیست.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun saveHistory(url: String) {
        if (url.isBlank() || url == "about:blank") return
        val old = historyPrefs.getStringSet("urls", linkedSetOf())?.toMutableList() ?: mutableListOf()
        old.remove(url)
        old.add(0, url)
        val trimmed = old.take(250).toSet()
        historyPrefs.edit().putStringSet("urls", trimmed).apply()
    }

    private fun createNewTab() {
        // No artificial tab-count limit; Android memory remains the practical limit.
        if (tabs.isNotEmpty()) tabs[currentTab].url = web.url ?: tabs[currentTab].url
        tabs.add(BrowserTab("about:blank", "New Tab"))
        currentTab = tabs.lastIndex
        address.setText("")
        selectMode = false
        control.text = "CONTROL"
        setIndicator(false)
        refreshTabBar()
        web.loadUrl("about:blank")
    }

    private fun switchTab(index: Int) {
        if (index !in tabs.indices || index == currentTab) return
        if (tabs.isNotEmpty()) tabs[currentTab].url = web.url ?: tabs[currentTab].url
        currentTab = index
        refreshTabBar()
        val url = tabs[currentTab].url
        address.setText(if (url == "about:blank") "" else url)
        web.loadUrl(url)
    }

    private fun refreshTabBar() {
        if (!::tabBar.isInitialized) return
        tabBar.removeAllViews()
        tabs.forEachIndexed { index, tab ->
            val holder = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(if (index == currentTab) R.drawable.xp_button else R.drawable.xp_bar)
            }
            val title = TextView(this).apply {
                text = "${index + 1}: ${tab.title.take(14)}"
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 10f
                setPadding(10, 0, 5, 0)
                setOnClickListener { switchTab(index) }
            }
            val close = TextView(this).apply {
                text = "×"
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 18f
                setPadding(5, 0, 8, 0)
                setOnClickListener { closeTab(index) }
            }
            holder.addView(title, LinearLayout.LayoutParams(0, 34, 1f))
            holder.addView(close, LinearLayout.LayoutParams(34, 34))
            tabBar.addView(holder, LinearLayout.LayoutParams(154, 34).apply { setMargins(3, 3, 3, 3) })
        }
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            tabs.add(BrowserTab("about:blank", "New Tab"))
            currentTab = 0
            web.loadUrl("about:blank")
        } else {
            currentTab = currentTab.coerceIn(0, tabs.lastIndex)
            val url = tabs[currentTab].url
            address.setText(if (url == "about:blank") "" else url)
            web.loadUrl(url)
        }
        refreshTabBar()
    }

    private fun showHistory() {
        val items = historyPrefs.getStringSet("urls", emptySet())?.toList() ?: emptyList()
        if (items.isEmpty()) {
            AlertDialog.Builder(this).setTitle("History / سابقه")
                .setMessage("هنوز سابقه‌ای ذخیره نشده است.")
                .setPositiveButton("OK", null).show()
            return
        }
        val ordered = items
        AlertDialog.Builder(this).setTitle("History / سابقه")
            .setItems(ordered.toTypedArray()) { _, which -> web.loadUrl(ordered[which]) }
            .setNeutralButton("پاک کردن") { _, _ ->
                historyPrefs.edit().remove("urls").apply()
                Toast.makeText(this, "سابقه پاک شد.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("لغو", null).show()
    }

    private fun startDownload(url: String, userAgent: String, contentDisposition: String?, mimetype: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        enqueueDownload(url, fileName, userAgent, mimetype)
    }

    private fun showImageDownloadDialog(resourceUrl: String, referer: String?) {
        if (!isHttpUrl(resourceUrl)) {
            Toast.makeText(this, "این تصویر لینک قابل دانلود مستقیم ندارد.", Toast.LENGTH_SHORT).show()
            return
        }
        val guessed = URLUtil.guessFileName(resourceUrl, null, null)
        val input = EditText(this).apply {
            setText(guessed)
            selectAll()
            setSingleLine(true)
            hint = "نام فایل"
            setPadding(24, 8, 24, 8)
        }
        AlertDialog.Builder(this)
            .setTitle("دانلود تصویر")
            .setMessage("نام فایل را در صورت نیاز تغییر بده:")
            .setView(input)
            .setPositiveButton("دانلود") { _, _ ->
                val chosen = safeFileName(input.text.toString(), guessed)
                enqueueDownload(resourceUrl, chosen, web.settings.userAgentString, guessMimeForName(chosen), referer)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun enqueueDownload(
        url: String,
        fileName: String,
        userAgent: String?,
        mimetype: String?,
        referer: String? = web.url
    ) {
        try {
            val cleanName = safeFileName(fileName, "download")
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(cleanName)
                setDescription("Fast Browser XP")
                setMimeType(mimetype ?: "application/octet-stream")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                userAgent?.let { addRequestHeader("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                referer?.let { addRequestHeader("Referer", it) }
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, cleanName)
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "دانلود شروع شد: $cleanName", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "دانلود شروع نشد.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDownloads() {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or
                DownloadManager.STATUS_FAILED
        ))
        val rows = mutableListOf<Pair<String, Long>>()
        cursor.use { c ->
            val idCol = c.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val sizeCol = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val doneCol = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            while (c.moveToNext() && rows.size < 80) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: "Download"
                val status = c.getInt(statusCol)
                val total = if (sizeCol >= 0) c.getLong(sizeCol) else -1L
                val done = if (doneCol >= 0) c.getLong(doneCol) else 0L
                val progress = if (total > 0) " ${(done * 100 / total).coerceIn(0, 100)}%" else ""
                rows.add("${downloadStatus(status)}$progress  $title" to id)
            }
        }

        val labels = if (rows.isEmpty()) arrayOf("هنوز دانلودی ثبت نشده است.") else rows.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Downloads / دانلودها")
            .setItems(labels) { _, which ->
                if (rows.isNotEmpty()) {
                    val id = rows[which].second
                    try {
                        startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                    } catch (_: Exception) {
                        Toast.makeText(this, "نمایش دانلودهای دستگاه در دسترس نیست.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNeutralButton("دانلودهای گوشی") { _, _ ->
                try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                catch (_: Exception) { Toast.makeText(this, "برنامه دانلودهای دستگاه پیدا نشد.", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun downloadStatus(status: Int): String = when (status) {
        DownloadManager.STATUS_PENDING -> "⏳"
        DownloadManager.STATUS_RUNNING -> "⬇"
        DownloadManager.STATUS_PAUSED -> "⏸"
        DownloadManager.STATUS_SUCCESSFUL -> "✓"
        DownloadManager.STATUS_FAILED -> "✕"
        else -> "•"
    }

    private fun isHttpUrl(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")

    private fun safeFileName(value: String, fallback: String): String {
        val cleaned = value.trim().replace(Regex("[\\/:*?\"<>|]"), "_")
        return cleaned.take(180).ifBlank { fallback }
    }

    private fun guessMimeForName(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".svg") -> "image/svg+xml"
            else -> null
        }
    }

    private val extraTouchPx = 18

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_UP) {
            val buttonIds = intArrayOf(
                R.id.btnControl, R.id.btnControl2, R.id.btnBack, R.id.btnForward, R.id.btnReload, R.id.btnEnterTop,
                R.id.btnHome, R.id.btnChatGPT, R.id.btnToken, R.id.btnRepo, R.id.btnHistory, R.id.btnBookmark,
                R.id.btnGo, R.id.btnNewTab, R.id.btnEnterBottom, R.id.btnSelect, R.id.btnWheel, R.id.btnScreenshot,
                R.id.btnDownloads, R.id.btnSavePage, R.id.btnWheelUp, R.id.btnWheelReset, R.id.btnWheelDown,
                R.id.btnHideAll, R.id.btnShowAll, R.id.btnNewsSource, R.id.btnNewsTranslate, R.id.btnHideNews
            )
            val x = event.rawX.toInt()
            val y = event.rawY.toInt()
            for (id in buttonIds) {
                val v = findViewById<View>(id) ?: continue
                if (v.visibility != View.VISIBLE || !v.isEnabled) continue
                val r = android.graphics.Rect()
                v.getGlobalVisibleRect(r)
                r.inset(-extraTouchPx, -extraTouchPx)
                if (r.contains(x, y)) {
                    v.performClick()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val results = if (resultCode == RESULT_OK && data != null) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            fileCallback?.onReceiveValue(results)
            fileCallback = null
        }
    }

    private fun sendEnterToWeb() {
        // ENTER first behaves like GO when the address bar is focused. Otherwise it
        // acts on the web control the user is currently working with: focused buttons,
        // menu items, links and form fields receive an Enter-equivalent action.
        if (address.hasFocus()) {
            openAddress()
            return
        }
        web.evaluateJavascript("""(function(){
          var el=document.activeElement;
          if(!el || el===document.body || el===document.documentElement){
            var c=document.querySelector('[aria-expanded=\"true\"], [role=\"menuitem\"]:focus, button:focus, [role=\"button\"]:focus, a:focus');
            el=c||el;
          }
          if(!el) return 'none';
          var tag=(el.tagName||'').toLowerCase();
          var role=(el.getAttribute('role')||'').toLowerCase();
          if(tag==='button' || tag==='a' || tag==='select' || role==='button' || role==='menuitem' || role==='option' || el.getAttribute('aria-haspopup')==='true'){
            el.click(); return 'click';
          }
          var ev=new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true});
          el.dispatchEvent(ev);
          var ev2=new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true});
          el.dispatchEvent(ev2);
          return 'key';
        })()""".trimIndent()) { result ->
            if (result == "\"none\"" || result == "null") {
                Toast.makeText(this, "عنصر فعالی برای ENTER پیدا نشد.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAddress(): Boolean {
        var u = address.text.toString().trim()
        if (u.isEmpty()) return true
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = if (u.contains(".") && !u.contains(" ")) "https://$u"
            else "https://www.google.com/search?q=" + Uri.encode(u)
        }
        web.loadUrl(u)
        return true
    }

    private fun setIndicator(active: Boolean) {
        if (!active) {
            selectIndicator.clearAnimation()
            selectIndicator.visibility = View.GONE
            return
        }
        selectIndicator.visibility = View.VISIBLE
        val blink = AlphaAnimation(1.0f, 0.15f).apply {
            duration = 550
            repeatMode = android.view.animation.Animation.REVERSE
            repeatCount = android.view.animation.Animation.INFINITE
        }
        selectIndicator.startAnimation(blink)
    }

    private fun toggleSelectMode() {
        selectMode = !selectMode
        control.text = if (selectMode) "SELECT" else "CONTROL"
        setIndicator(selectMode)
        if (selectMode) installSelectMode() else removeSelectMode()
    }

    private fun toggleControl2Mode() {
        control2Mode = !control2Mode
        control2StartX = -1f
        control2StartY = -1f
        setControl2Indicator(control2Mode)
        Toast.makeText(
            this,
            if (control2Mode) "CONTROL 2: نقطه اول و سپس نقطه دوم را در همان صفحه بزنید." else "CONTROL 2 خاموش شد.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setControl2Indicator(active: Boolean) {
        if (!active) {
            control2Indicator.clearAnimation()
            control2Indicator.visibility = View.GONE
            return
        }
        control2Indicator.visibility = View.VISIBLE
        val blink = AlphaAnimation(1.0f, 0.15f).apply {
            duration = 550
            repeatMode = android.view.animation.Animation.REVERSE
            repeatCount = android.view.animation.Animation.INFINITE
        }
        control2Indicator.startAnimation(blink)
    }

    private fun copySelectedRegion(x1: Float, y1: Float, x2: Float, y2: Float) {
        if (web.width <= 0 || web.height <= 0) return
        val left = minOf(x1, x2).toInt().coerceIn(0, web.width - 1)
        val top = minOf(y1, y2).toInt().coerceIn(0, web.height - 1)
        val right = maxOf(x1, x2).toInt().coerceIn(left + 1, web.width)
        val bottom = maxOf(y1, y2).toInt().coerceIn(top + 1, web.height)

        val full = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.ARGB_8888)
        web.draw(Canvas(full))
        val crop = Bitmap.createBitmap(full, left, top, right - left, bottom - top)
        full.recycle()

        val file = File(cacheDir, "fbxp_selection_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(file).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            crop.recycle()
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
            copyRegionTextAndHtml(left, top, right, bottom, uri)
        } catch (_: Exception) {
            crop.recycle()
            Toast.makeText(this, "کپی ناحیه انجام نشد.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyRegionTextAndHtml(left: Int, top: Int, right: Int, bottom: Int, imageUri: Uri) {
        web.evaluateJavascript(
            """(function(){
              const sx=${left}, sy=${top}, ex=${right}, ey=${bottom};
              const vw=Math.max(1, document.documentElement.clientWidth);
              const vh=Math.max(1, document.documentElement.clientHeight);
              const scaleX=vw/${web.width.toDouble()};
              const scaleY=vh/${web.height.toDouble()};
              const l=sx*scaleX, t=sy*scaleY, r=ex*scaleX, b=ey*scaleY;
              const els=[...document.querySelectorAll('body *')];
              const picked=els.filter(el=>{
                const z=el.getBoundingClientRect();
                return z.width>0 && z.height>0 && z.right>l && z.left<r && z.bottom>t && z.top<b;
              });
              const texts=[]; const htmls=[];
              picked.forEach(el=>{
                const tx=(el.innerText||el.textContent||'').trim();
                if(tx && tx.length<20000 && !texts.includes(tx)) texts.push(tx);
                if(el.children.length===0){ const h=el.outerHTML||''; if(h && h.length<30000) htmls.push(h); }
              });
              return JSON.stringify({text:texts.join('\n'),html:htmls.join('\n')});
            })()""".trimIndent()
        ) { raw ->
            try {
                val decoded = JSONTokener(raw).nextValue() as? String ?: ""
                val obj = JSONObject(decoded)
                val text = obj.optString("text", "")
                val html = obj.optString("html", text)
                val clip = ClipData.newHtmlText("Fast Browser XP", text, html)
                clip.addItem(ClipData.Item(imageUri))
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(clip)
                grantUriPermission("com.android.systemui", imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                Toast.makeText(this, "ناحیه با متن و تصویر کپی شد.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Fast Browser XP", "ناحیه انتخاب شد؛ تصویر در کلیپ‌بورد قرار گرفت."))
                Toast.makeText(this, "تصویر ناحیه کپی شد.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showWheel() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val label = TextView(this).apply { text = "اندازه متن صفحه: $textScale%"; textSize = 17f; setTextColor(Color.BLACK) }
        val seek = SeekBar(this).apply { min = 50; max = 250; progress = textScale }
        box.addView(label)
        box.addView(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                textScale = p.coerceIn(50, 250)
                label.text = "اندازه متن صفحه: $textScale%"
                web.settings.textZoom = textScale
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        AlertDialog.Builder(this).setTitle("WHEEL").setView(box).setPositiveButton("OK", null).show()
    }

    private fun showBookmarks() {
        val items = arrayOf("ChatGPT", "GitHub مخزن", "GitHub Token")
        val urls = arrayOf(chatGptUrl, githubUrl, tokenUrl)
        AlertDialog.Builder(this).setTitle("Bookmarks / نشانک‌ها")
            .setItems(items) { _, which -> web.loadUrl(urls[which]) }
            .setNegativeButton("لغو", null).show()
    }

    private fun installSelectMode() {
        val js = """
            (function(){
              if(window.__fbxp_style) return;
              window.__fbxp_style=document.createElement('style');
              window.__fbxp_style.innerHTML='*{cursor:crosshair !important}';
              document.head.appendChild(window.__fbxp_style);
              window.__fbxp_click=function(e){
                e.preventDefault(); e.stopPropagation();
                var el=e.target;
                var text=(el.innerText || el.textContent || '').trim();
                if(!text) text=(el.outerHTML || '').trim();
                try{navigator.clipboard.writeText(text);}catch(x){
                  var ta=document.createElement('textarea'); ta.value=text;
                  document.body.appendChild(ta); ta.select(); document.execCommand('copy'); ta.remove();
                }
                return false;
              };
              document.addEventListener('click',window.__fbxp_click,true);
            })();
        """.trimIndent()
        web.evaluateJavascript(js, null)
        Toast.makeText(this, "روی بخش موردنظر صفحه بزنید.", Toast.LENGTH_SHORT).show()
    }

    private fun removeSelectMode() {
        web.evaluateJavascript("""
            (function(){
              if(window.__fbxp_click) document.removeEventListener('click',window.__fbxp_click,true);
              if(window.__fbxp_style){window.__fbxp_style.remove();window.__fbxp_style=null;}
            })();
        """.trimIndent(), null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("last_url", web.url)
        super.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() { }

    companion object {
        private const val FILE_CHOOSER_REQUEST = 4101
    }
}
