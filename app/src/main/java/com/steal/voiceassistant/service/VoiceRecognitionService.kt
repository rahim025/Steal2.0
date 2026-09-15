package com.steal.voiceassistant.service

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.steal.voiceassistant.model.CommandParser
import com.steal.voiceassistant.model.CommandRouter
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Service en avant-plan qui garde le micro ouvert et transcrit la parole en
 * continu grâce au modèle Vosk embarqué dans assets/vosk-model-small-fr.
 * 100% offline : aucune donnée audio ne quitte le téléphone.
 */
class VoiceRecognitionService : Service(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private lateinit var parser: CommandParser
    private lateinit var router: CommandRouter

    // Vérification de la phrase de déverrouillage quand l'écran est verrouillé
    private var passphraseListener: LockScreenPassphraseListener? = null
    private val lockCheckHandler = Handler(Looper.getMainLooper())
    private var lockCheckRunning = false

    companion object {
        private const val CHANNEL_ID = "steal_voice_channel"
        private const val NOTIFICATION_ID = 1
        private const val MODEL_ASSET_PATH = "vosk-model-small-fr"
    }

    override fun onCreate() {
        super.onCreate()
        parser = CommandParser()
        router = CommandRouter(applicationContext)
        startForeground(NOTIFICATION_ID, buildNotification())
        loadModel()
    }

    private fun loadModel() {
        StorageService.unpack(
            this, MODEL_ASSET_PATH, "model",
            { unpackedModel: Model ->
                model = unpackedModel
                passphraseListener = LockScreenPassphraseListener(applicationContext, unpackedModel)
                startListening()
                startLockScreenWatcher()
            },
            { exception: IOException ->
                exception.printStackTrace()
            }
        )
    }

    private fun startListening() {
        val currentModel = model ?: return
        speechService?.stop()
        speechService?.shutdown()
        try {
            val recognizer = Recognizer(currentModel, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Boucle de surveillance : tant que l'écran est verrouillé, on met en
     * pause l'écoute de commandes classique (SpeechService) et on capture
     * de courts échantillons pour vérifier la phrase de déverrouillage.
     * On ne peut pas faire tourner les deux en même temps : un seul
     * composant peut utiliser le micro à la fois.
     */
    private fun startLockScreenWatcher() {
        if (lockCheckRunning) return
        lockCheckRunning = true

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        var wasLocked = false
        val checkRunnable = object : Runnable {
            override fun run() {
                if (keyguardManager.isKeyguardLocked) {
                    if (!wasLocked) {
                        speechService?.stop()
                        wasLocked = true
                    }
                    val unlocked = passphraseListener?.checkOnce() ?: false
                    if (unlocked) {
                        UnlockAccessibilityService.instance?.attemptUnlock()
                    }
                } else if (wasLocked) {
                    // On vient de passer de verrouillé à déverrouillé :
                    // on relance l'écoute de commandes classique.
                    wasLocked = false
                    startListening()
                }
                lockCheckHandler.postDelayed(this, 500)
            }
        }
        lockCheckHandler.post(checkRunnable)
    }

    // ---- Callbacks Vosk ----

    override fun onResult(hypothesis: String?) {
        hypothesis ?: return
        // hypothesis est un JSON du type {"text": "ouvre whatsapp"}
        val text = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"").find(hypothesis)?.groupValues?.get(1) ?: return
        if (text.isNotBlank()) {
            val command = parser.parse(text)
            router.execute(command)
        }
    }

    override fun onPartialResult(hypothesis: String?) {}
    override fun onFinalResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) {
        exception?.printStackTrace()
    }
    override fun onTimeout() {
        // Relance l'écoute pour un fonctionnement en continu.
        speechService?.startListening(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        lockCheckHandler.removeCallbacksAndMessages(null)
        speechService?.stop()
        speechService?.shutdown()
        router.shutdown()
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Assistant vocal Steal", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Steal écoute")
            .setContentText("Dites une commande à tout moment.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}
