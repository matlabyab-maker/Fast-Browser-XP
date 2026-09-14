package com.fastbrowser.xp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var control: TextView
    private lateinit var selectStatus: TextView
    private var selectMode = false
    private var textScale = 100

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
        selectStatus = findViewById(R.id.selectStatus)

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
                if (selectMode) installSelectMode()
            }
        }
        web.webChromeClient = WebChromeClient()

        findViewById<TextView>(R.id.btnBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<TextView>(R.id.btnForward).setOnClickListener { if (web.canGoForward()) web.goForward() }
        findViewById<TextView>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<TextView>(R.id.btnHome).setOnClickListener { web.loadUrl(homeUrl) }
        findViewById<TextView>(R.id.btnChatGPT).setOnClickListener { web.loadUrl(chatGptUrl) }
        findViewById<TextView>(R.id.btnToken).setOnClickListener { web.loadUrl(tokenUrl) }
        findViewById<TextView>(R.id.btnRepo).setOnClickListener { web.loadUrl(githubUrl) }
        findViewById<TextView>(R.id.btnBookmark).setOnClickListener { showBookmarks() }
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

        web.loadUrl(savedInstanceState?.getString("last_url") ?: homeUrl)
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

    private fun toggleSelectMode() {
        selectMode = !selectMode
        control.text = if (selectMode) "SELECT" else "CONTROL"
        selectStatus.text = if (selectMode) "حالت SELECT فعال است — روی بخش موردنظر صفحه بزنید تا متن آن کپی شود." else "حالت SELECT خاموش است."
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
}
