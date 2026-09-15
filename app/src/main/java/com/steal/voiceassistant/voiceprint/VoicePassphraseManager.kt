package com.steal.voiceassistant.voiceprint

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Gère l'enregistrement (enrôlement) de la phrase de déverrouillage et la
 * vérification ultérieure : la phrase doit à la fois correspondre au bon
 * TEXTE (via Vosk, dans VoiceRecognitionService) ET à la bonne EMPREINTE
 * VOCALE (comparaison de similarité ci-dessous), pour limiter les faux
 * positifs si quelqu'un d'autre prononce la même phrase.
 *
 * Stockage 100% local (SharedPreferences), rien n'est envoyé nulle part.
 */
class VoicePassphraseManager(context: Context) {

    private val prefs = context.getSharedPreferences("steal_voiceprint", Context.MODE_PRIVATE)
    private val sampleRate = 16000

    companion object {
        // Un peu permissif car notre extracteur est simplifié (pas un vrai
        // modèle de speaker embedding) — à resserrer si trop de faux positifs.
        const val SIMILARITY_THRESHOLD = 0.92f
        private const val KEY_PASSPHRASE_TEXT = "passphrase_text"
        private const val KEY_EMBEDDING = "passphrase_embedding"
    }

    fun isEnrolled(): Boolean = prefs.contains(KEY_EMBEDDING)

    fun enrolledPassphraseText(): String? = prefs.getString(KEY_PASSPHRASE_TEXT, null)

    /**
     * Enregistre l'empreinte vocale à partir de plusieurs échantillons audio
     * (idéalement 3 répétitions de la phrase) : on moyenne leurs vecteurs
     * pour une empreinte plus robuste aux petites variations de prononciation.
     */
    fun enroll(passphraseText: String, samples: List<ShortArray>) {
        val vectors = samples.map { FeatureExtractor.extract(it) }
        val averaged = FloatArray(vectors[0].size)
        for (v in vectors) {
            for (i in v.indices) averaged[i] += v[i] / vectors.size
        }
        val normalized = normalize(averaged)

        prefs.edit()
            .putString(KEY_PASSPHRASE_TEXT, passphraseText.lowercase().trim())
            .putString(KEY_EMBEDDING, encode(normalized))
            .apply()
    }

    fun clearEnrollment() {
        prefs.edit().remove(KEY_PASSPHRASE_TEXT).remove(KEY_EMBEDDING).apply()
    }

    /**
     * @param spokenText texte reconnu par Vosk lors de la tentative
     * @param audioSample échantillon audio de la tentative
     * @return true si le texte ET la voix correspondent au profil enregistré
     */
    fun verify(spokenText: String, audioSample: ShortArray): Boolean {
        val expectedText = enrolledPassphraseText() ?: return false
        val storedEmbedding = prefs.getString(KEY_EMBEDDING, null) ?: return false

        val textMatches = spokenText.lowercase().trim() == expectedText
        if (!textMatches) return false

        val candidateEmbedding = FeatureExtractor.extract(audioSample)
        val referenceEmbedding = decode(storedEmbedding)
        val similarity = FeatureExtractor.cosineSimilarity(candidateEmbedding, referenceEmbedding)

        return similarity >= SIMILARITY_THRESHOLD
    }

    /** Utilitaire pour enregistrer un court échantillon audio brut (PCM 16 bits). */
    fun recordSample(durationMs: Int = 2500): ShortArray {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufferSize, sampleRate * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )
        val totalSamples = (sampleRate * durationMs / 1000)
        val buffer = ShortArray(totalSamples)

        recorder.startRecording()
        var offset = 0
        while (offset < totalSamples) {
            val read = recorder.read(buffer, offset, totalSamples - offset)
            if (read <= 0) break
            offset += read
        }
        recorder.stop()
        recorder.release()
        return buffer
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm == 0f) return vector
        return FloatArray(vector.size) { vector[it] / norm }
    }

    private fun encode(vector: FloatArray): String {
        val array = JSONArray()
        for (v in vector) array.put(v.toDouble())
        return JSONObject().put("v", array).toString()
    }

    private fun decode(json: String): FloatArray {
        val array = JSONObject(json).getJSONArray("v")
        return FloatArray(array.length()) { array.getDouble(it).toFloat() }
    }
}
