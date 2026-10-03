package com.example.jotmodellab

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/** Strictly local system baseline; execution hardware is not exposed by this API. */
suspend fun recognizeSystemSample(context: Context, audio: FloatArray): String = withContext(Dispatchers.Main) {
    if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        throw SecurityException("Microphone permission is required by the system recognizer")
    }
    check(SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) { "No on-device system recognizer installed" }
    withTimeout(20_000) {
        suspendCancellableCoroutine { continuation ->
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val pipe = ParcelFileDescriptor.createPipe()
            var finished = false
            fun finish(result: Result<String>) {
                if (finished) return
                finished = true; recognizer.destroy(); runCatching { pipe[0].close() }; runCatching { pipe[1].close() }
                if (continuation.isActive) continuation.resumeWith(result)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) = finish(Result.failure(IllegalStateException("On-device recognizer error $error. Audio-file input may be unsupported by this provider.")))
                override fun onResults(results: Bundle?) = finish(Result.success(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()))
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            continuation.invokeOnCancellation {
                android.os.Handler(context.mainLooper).post { finish(Result.failure(CancellationException())) }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            try {
                recognizer.startListening(intent)
                Thread({ runCatching { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(PcmWave.pcm(audio)) } } }, "JotSystemSample").start()
            } catch (e: Exception) { finish(Result.failure(e)) }
        }
    }
}
