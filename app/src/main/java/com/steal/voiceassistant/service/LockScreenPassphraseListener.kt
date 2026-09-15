package com.steal.voiceassistant.service

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import com.steal.voiceassistant.voiceprint.VoicePassphraseManager
import org.json.JSONObject

/**
 * Tourne en boucle uniquement quand l'écran est verrouillé (voir
 * VoiceRecognitionService). Contrairement au flux de commandes classique
 * (qui utilise SpeechService), on capture ici l'audio brut nous-mêmes afin
 * de pouvoir à la fois :
 *   1. le transcrire avec Vosk pour vérifier le TEXTE de la phrase
 *   2. l'analyser avec FeatureExtractor pour vérifier la VOIX
 * Les deux doivent correspondre pour déclencher le déverrouillage.
 */
class LockScreenPassphraseListener(
    context: Context,
    private val model: Model
) {
    private val passphraseManager = VoicePassphraseManager(context)
    private val sampleRate = 16000f

    /**
     * Enregistre ~2,5 secondes d'audio et tente de faire correspondre la
     * phrase de déverrouillage. Retourne true si le déverrouillage doit être
     * déclenché.
     */
    fun checkOnce(): Boolean {
        if (!passphraseManager.isEnrolled()) return false

        val audio = passphraseManager.recordSample(durationMs = 2500)
        val recognizedText = transcribe(audio)
        if (recognizedText.isBlank()) return false

        return passphraseManager.verify(recognizedText, audio)
    }

    private fun transcribe(pcm: ShortArray): String {
        val recognizer = Recognizer(model, sampleRate)
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            bytes[i * 2] = (pcm[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((pcm[i].toInt() shr 8) and 0xFF).toByte()
        }
        recognizer.acceptWaveForm(bytes, bytes.size)
        val resultJson = recognizer.finalResult
        recognizer.close()
        return try {
            JSONObject(resultJson).optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }
}
