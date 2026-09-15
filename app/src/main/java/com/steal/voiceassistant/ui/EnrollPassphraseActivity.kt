package com.steal.voiceassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.steal.voiceassistant.R
import com.steal.voiceassistant.voiceprint.VoicePassphraseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran d'enrôlement : le propriétaire tape la phrase choisie, puis
 * l'enregistre 3 fois pour construire une empreinte vocale robuste
 * (voir VoicePassphraseManager). Le TEXTE et la VOIX seront tous les deux
 * requis au moment du déverrouillage.
 */
class EnrollPassphraseActivity : AppCompatActivity() {

    private lateinit var manager: VoicePassphraseManager
    private val samples = mutableListOf<ShortArray>()
    private val requiredSamples = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll_passphrase)
        manager = VoicePassphraseManager(this)

        val passphraseInput = findViewById<EditText>(R.id.passphraseInput)
        val progressText = findViewById<TextView>(R.id.progressText)
        val recordButton = findViewById<Button>(R.id.btnRecordSample)
        val saveButton = findViewById<Button>(R.id.btnSaveEnrollment)

        recordButton.setOnClickListener {
            if (passphraseInput.text.isBlank()) {
                Toast.makeText(this, "Écris d'abord ta phrase.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!hasRecordPermission()) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
                return@setOnClickListener
            }
            recordButton.isEnabled = false
            Toast.makeText(this, "Parle maintenant...", Toast.LENGTH_SHORT).show()

            CoroutineScope(Dispatchers.Main).launch {
                val sample = withContext(Dispatchers.IO) { manager.recordSample() }
                samples.add(sample)
                progressText.text = "Enregistrements : ${samples.size} / $requiredSamples"
                recordButton.isEnabled = samples.size < requiredSamples
                saveButton.isEnabled = samples.size >= requiredSamples
            }
        }

        saveButton.setOnClickListener {
            manager.enroll(passphraseInput.text.toString(), samples)
            Toast.makeText(this, "Phrase de déverrouillage enregistrée.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun hasRecordPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}
