package com.steal.voiceassistant.voiceprint

import kotlin.math.*

/**
 * Extraction de caractéristiques spectrales simplifiées à partir d'un buffer
 * audio PCM 16 bits, pour construire une "empreinte vocale" utilisable en
 * vérification du locuteur. Ce n'est pas un MFCC complet (pas de DCT ni de
 * bancs de filtres Mel calibrés), mais une approximation par énergie de bandes
 * de fréquence, suffisante pour distinguer des voix différentes prononçant la
 * même phrase, tout en restant 100% offline et légère à calculer.
 */
object FeatureExtractor {

    private const val SAMPLE_RATE = 16000
    private const val FRAME_SIZE = 512
    private const val NUM_BANDS = 20

    /**
     * @param pcm échantillons audio mono 16 bits (valeurs -32768..32767)
     * @return vecteur de caractéristiques normalisé représentant la phrase prononcée
     */
    fun extract(pcm: ShortArray): FloatArray {
        val frameCount = pcm.size / FRAME_SIZE
        if (frameCount == 0) return FloatArray(NUM_BANDS)

        val accumulatedBands = FloatArray(NUM_BANDS)

        for (frameIndex in 0 until frameCount) {
            val frame = DoubleArray(FRAME_SIZE)
            for (i in 0 until FRAME_SIZE) {
                val sampleIndex = frameIndex * FRAME_SIZE + i
                // Fenêtre de Hamming pour réduire les artefacts de bord
                val windowed = pcm[sampleIndex] * hammingWindow(i, FRAME_SIZE)
                frame[i] = windowed
            }
            val magnitudes = fftMagnitudes(frame)
            val bands = bandEnergies(magnitudes)
            for (b in 0 until NUM_BANDS) accumulatedBands[b] += bands[b]
        }

        // Moyenne sur toutes les frames puis normalisation (log + norme L2)
        val averaged = FloatArray(NUM_BANDS) { (accumulatedBands[it] / frameCount).toFloat() }
        val logScaled = FloatArray(NUM_BANDS) { ln(1.0 + averaged[it]).toFloat() }
        return normalize(logScaled)
    }

    private fun hammingWindow(n: Int, size: Int): Double =
        0.54 - 0.46 * cos(2.0 * PI * n / (size - 1))

    /** FFT simple (Cooley-Tukey, taille puissance de 2) retournant les magnitudes. */
    private fun fftMagnitudes(input: DoubleArray): DoubleArray {
        val n = input.size
        val real = input.copyOf()
        val imag = DoubleArray(n)
        fft(real, imag)
        val half = n / 2
        return DoubleArray(half) { i -> sqrt(real[i] * real[i] + imag[i] * imag[i]) }
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        if (n <= 1) return
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var curWr = 1.0; var curWi = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]; val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * curWr - imag[i + k + len / 2] * curWi
                    val vI = real[i + k + len / 2] * curWi + imag[i + k + len / 2] * curWr
                    real[i + k] = uR + vR; imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR; imag[i + k + len / 2] = uI - vI
                    val nextWr = curWr * wr - curWi * wi
                    curWi = curWr * wi + curWi * wr
                    curWr = nextWr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Regroupe les magnitudes fréquentielles en NUM_BANDS bandes d'énergie. */
    private fun bandEnergies(magnitudes: DoubleArray): DoubleArray {
        val bands = DoubleArray(NUM_BANDS)
        val bandSize = magnitudes.size / NUM_BANDS
        for (b in 0 until NUM_BANDS) {
            var sum = 0.0
            for (i in b * bandSize until (b + 1) * bandSize) sum += magnitudes[i]
            bands[b] = sum / bandSize
        }
        return bands
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm == 0f) return vector
        return FloatArray(vector.size) { vector[it] / norm }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot // les deux vecteurs sont déjà normalisés (norme L2 = 1)
    }
}
