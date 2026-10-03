package com.example.jotmodellab

import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.*

/** Whisper's CPU audio preprocessing; encoder and decoder inference run on HTP. */
object MelFeatures {
    const val sampleRate = 16000
    const val maxSamples = 480000
    private fun mel(hz: Double): Double = if (hz < 1000) hz / (200.0 / 3) else 15 + ln(hz / 1000) / (ln(6.4) / 27)
    private fun hz(mel: Double): Double = if (mel < 15) mel * (200.0 / 3) else 1000 * exp((mel - 15) * ln(6.4) / 27)
    private val filters = Array(80) { band ->
        val points = DoubleArray(82) { hz(mel(8000.0) * it / 81) }
        DoubleArray(201) { bin ->
            val f = bin * 40.0
            max(0.0, min((f - points[band]) / (points[band + 1] - points[band]), (points[band + 2] - f) / (points[band + 2] - points[band + 1]))) * 2 / (points[band + 2] - points[band])
        }
    }
    fun extract(audio: FloatArray): FloatArray {
        require(audio.isNotEmpty() && audio.size <= maxSamples)
        val fft = DoubleFFT_1D(400)
        val window = DoubleArray(400) { 0.5 - 0.5 * cos(2 * PI * it / 400) }
        val output = FloatArray(80 * 3000) { -10f }
        val spectrum = DoubleArray(800)
        val powers = DoubleArray(201)
        // Zero padding gives an identical silence tail without computing 30 seconds of FFTs.
        val frames = min(3000, (audio.size + 200 + 159) / 160)
        for (frame in 0 until frames) {
            spectrum.fill(0.0)
            for (i in 0 until 400) {
                val index = abs(frame * 160 + i - 200)
                spectrum[i] = (if (index < audio.size) audio[index].toDouble() else 0.0) * window[i]
            }
            fft.realForwardFull(spectrum)
            for (bin in 0..200) powers[bin] = spectrum[bin * 2].pow(2) + spectrum[bin * 2 + 1].pow(2)
            for (band in 0 until 80) {
                var value = 0.0
                for (bin in 0..200) value += filters[band][bin] * powers[bin]
                output[band * 3000 + frame] = log10(max(value, 1e-10)).toFloat()
            }
        }
        val floor = (output.maxOrNull() ?: -10f) - 8f
        for (i in output.indices) output[i] = (max(output[i], floor) + 4f) / 4f
        return output
    }
}
