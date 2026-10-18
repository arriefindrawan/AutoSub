package com.blay.autosub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        root.addView(TextView(this).apply {
            text = "1. Aktifkan 'AutoSub' di Accessibility.\n2. Buka Sub4Sub, buka menu View.\n3. Tekan tombol START yang melayang di kiri layar."
        })
        root.addView(Button(this).apply {
            text = "Buka Pengaturan Accessibility"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        setContentView(root)
    }
}
