package com.cs312.drafty.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/** Thin MediaRecorder wrapper producing AAC-in-MP4 (.m4a), 44.1 kHz, 128 kbps. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Starts recording into [outputFile]. Throws if the mic is unavailable. */
    fun start(outputFile: File) {
        check(recorder == null) { "Already recording" }
        outputFile.parentFile?.mkdirs()

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(SAMPLE_RATE)
            mr.setAudioEncodingBitRate(BIT_RATE)
            mr.setAudioChannels(1)
            mr.setOutputFile(outputFile.absolutePath)
            mr.prepare()
            mr.start()
        } catch (e: Exception) {
            mr.release()
            throw e
        }
        recorder = mr
        this.outputFile = outputFile
    }

    /**
     * Stops recording and returns the file, or null if nothing valid was captured
     * (stopped too quickly, or the file is missing or <= [MIN_FILE_BYTES]). Invalid
     * files are deleted. Never throws.
     */
    fun stop(): File? {
        val mr = recorder ?: return null
        recorder = null
        val file = outputFile
        outputFile = null
        var ok = true
        try {
            mr.stop()
        } catch (e: RuntimeException) {
            // IllegalStateException / RuntimeException: stop() called before any audio data arrived
            Log.w(TAG, "MediaRecorder.stop() failed; recording discarded", e)
            ok = false
        } finally {
            try {
                mr.release()
            } catch (e: Exception) {
                Log.w(TAG, "MediaRecorder.release() failed", e)
            }
        }
        if (file == null) return null
        if (!ok || !file.exists() || file.length() <= MIN_FILE_BYTES) {
            Log.w(TAG, "Discarding recording (ok=$ok, exists=${file.exists()}, size=${file.length()})")
            file.delete()
            return null
        }
        return file
    }

    /** Peak amplitude since the last call (0..32767); 0 when idle. For waveform UI. */
    fun getMaxAmplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (e: IllegalStateException) {
        0
    }

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 44_100
        const val BIT_RATE = 128_000
        const val MIME_TYPE = "audio/m4a"
        const val MIN_FILE_BYTES = 1024L
    }
}

