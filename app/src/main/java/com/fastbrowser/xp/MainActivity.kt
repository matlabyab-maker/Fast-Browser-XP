package com.fastbrowser.xp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import android.webkit.ValueCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var control: TextView
    private lateinit var selectIndicator: View
    private var selectMode = false
    private var textScale = 100
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val historyPrefs by lazy { getSharedPreferences("browser_history", MODE_PRIVATE) }
    private data class BrowserTab(var url: String = "about:blank", var title: String = "New Tab")
    private val tabs = mutableListOf<BrowserTab>()
    private var currentTab = 0
    private lateinit var tabBar: LinearLayout

    private val homeUrl = "https://chatgpt.com/"
    private val chatGptUrl = "https://chatgpt.com/"
    private val githubUrl = "https://github.com/matlabyab-maker/Web_Browser_Fast_2"
    private val tokenUrl = "https://github.com/settings/tokens"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.webView)
        address = findViewById(R.id.addressBar)
        control = findViewById(R.id.btnControl)
        selectIndicator = findViewById(R.id.selectIndicator)
        selectIndicator.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.rgb(135,206,235)) }
        tabBar = findViewById(R.id.tabBar)
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

        findViewById<TextView>(R.id.btnBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<TextView>(R.id.btnForward).setOnClickListener { if (web.canGoForward()) web.goForward() }
        findViewById<TextView>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<TextView>(R.id.btnHome).setOnClickListener { web.loadUrl(homeUrl) }
        findViewById<TextView>(R.id.btnChatGPT).setOnClickListener { web.loadUrl(chatGptUrl) }
        findViewById<TextView>(R.id.btnToken).setOnClickListener { web.loadUrl(tokenUrl) }
        findViewById<TextView>(R.id.btnRepo).setOnClickListener { web.loadUrl(githubUrl) }
        findViewById<TextView>(R.id.btnBookmark).setOnClickListener { showBookmarks() }
        findViewById<TextView>(R.id.btnHistory).setOnClickListener { showHistory() }
        findViewById<TextView>(R.id.btnDownloads).setOnClickListener { openDownloads() }
        findViewById<TextView>(R.id.btnGo).setOnClickListener { openAddress() }
        address.setOnEditorActionListener { _, _, _ -> openAddress(); true }

        control.setOnClickListener { toggleSelectMode() }
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

        expandTouchTargets()
        web.loadUrl(savedInstanceState?.getString("last_url") ?: homeUrl)
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
            val b = TextView(this).apply {
                text = "${index + 1}: ${tab.title.take(18)}"
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 10f
                setPadding(12, 0, 12, 0)
                setBackgroundResource(if (index == currentTab) R.drawable.xp_button else R.drawable.xp_bar)
                setOnClickListener { switchTab(index) }
            }
            tabBar.addView(b, LinearLayout.LayoutParams(120, 34).apply { setMargins(3, 3, 3, 3) })
        }
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
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Fast Browser XP")
                setMimeType(mimetype ?: "application/octet-stream")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                web.url?.let { addRequestHeader("Referer", it) }
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "دانلود شروع شد: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "دانلود شروع نشد.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (_: Exception) {
            Toast.makeText(this, "برنامه دانلودها در دستگاه پیدا نشد.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun expandTouchTargets() {
        val ids = intArrayOf(
            R.id.btnControl, R.id.btnBack, R.id.btnForward, R.id.btnReload, R.id.btnHome,
            R.id.btnChatGPT, R.id.btnToken, R.id.btnRepo, R.id.btnBookmark, R.id.btnHistory,
            R.id.btnGo, R.id.btnSelect, R.id.btnWheel, R.id.btnScreenshot, R.id.btnSavePage, R.id.btnDownloads, R.id.btnNewTab, R.id.btnEnterTop, R.id.btnEnterBottom
        )
        web.post {
            ids.forEach { id ->
                val v = findViewById<View>(id) ?: return@forEach
                val parent = v.parent as? View
                parent?.post {
                    val r = android.graphics.Rect()
                    v.getHitRect(r)
                    val extra = if (id == R.id.btnControl) 10 else 6
                    r.inset(-extra, -extra)
                    parent.touchDelegate = android.view.TouchDelegate(r, v)
                }
            }
        }
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
        web.requestFocus()
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        web.evaluateJavascript("""(function(){var e=document.activeElement;if(e){e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));e.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));}})();""", null)
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
