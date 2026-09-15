package com.example.windowsbrowser

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val radioGroup = findViewById<RadioGroup>(R.id.layoutModeGroup)
        val desktopMode = findViewById<RadioButton>(R.id.radioDesktop)
        val mobileMode = findViewById<RadioButton>(R.id.radioMobile)
        desktopMode.isChecked = true

        findViewById<Button>(R.id.btnApplyLayout).setOnClickListener {
            val mode = if (desktopMode.isChecked) "desktop" else "mobile"
            val result = Intent().putExtra("layout_mode", mode)
            setResult(RESULT_OK, result)
            Toast.makeText(this, getString(R.string.layout_applied, mode), Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnEnableVpn).setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 100)
            } else {
                startVpn()
            }
        }
        findViewById<Button>(R.id.btnDisableVpn).setOnClickListener {
            stopService(Intent(this, BrowserVpnService::class.java))
            Toast.makeText(this, R.string.vpn_stop_requested, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) startVpn()
    }

    private fun startVpn() {
        startService(Intent(this, BrowserVpnService::class.java))
        Toast.makeText(this, R.string.vpn_start_requested, Toast.LENGTH_SHORT).show()
    }
}
