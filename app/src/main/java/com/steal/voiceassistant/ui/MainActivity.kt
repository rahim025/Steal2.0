package com.steal.voiceassistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.steal.voiceassistant.R
import com.steal.voiceassistant.service.VoiceRecognitionService

class MainActivity : AppCompatActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.widget.Button>(R.id.btnEnableAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<android.widget.Button>(R.id.btnStartListening).setOnClickListener {
            if (hasAllPermissions()) {
                startVoiceService()
            } else {
                ActivityCompat.requestPermissions(this, requiredPermissions, 100)
            }
        }

        findViewById<android.widget.Button>(R.id.btnEnrollPassphrase).setOnClickListener {
            startActivity(Intent(this, EnrollPassphraseActivity::class.java))
        }
    }

    private fun hasAllPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasAllPermissions()) startVoiceService()
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceRecognitionService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
