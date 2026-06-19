package com.osfans.trime.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.osfans.trime.R
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.toast
import com.osfans.trime.voice.api.CorrectionApiClient
import com.osfans.trime.voice.model.TextCorrectionResponse
import java.io.File

class VoiceCorrectionController(
    private val service: TrimeInputMethodService,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recordingTimeoutRunnable = Runnable { stopRecordingAndUpload() }

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var isCorrecting = false

    fun toggleFromVoiceKey() {
        if (isRecording) {
            stopRecordingAndUpload()
        } else {
            startRecordingFromKey()
        }
    }

    fun stopFromVoiceKeyRelease() {
        if (isRecording) {
            stopRecordingAndUpload()
        }
    }

    fun cancel() {
        mainHandler.removeCallbacks(recordingTimeoutRunnable)
        mediaRecorder?.let {
            runCatching {
                if (isRecording) it.stop()
            }
            runCatching {
                it.reset()
                it.release()
            }
        }
        mediaRecorder = null
        isRecording = false
        isCorrecting = false
        deleteRecordingFile(recordingFile)
        recordingFile = null
    }

    private fun startRecordingFromKey() {
        if (isCorrecting) {
            service.toast(R.string.voice_transform_correcting)
            return
        }
        if (!hasAudioPermission()) {
            service.toast(R.string.voice_transform_mic_permission_required)
            service.startActivity(
                Intent(service, VoicePermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        startRecording()
    }

    private fun startRecording() {
        try {
            val audioFile = File(service.cacheDir, "trime_voice_${System.currentTimeMillis()}.m4a")
            recordingFile = audioFile
            mediaRecorder = createRecorder(audioFile).apply {
                prepare()
                start()
            }
            isRecording = true
            service.toast(R.string.voice_transform_recording)
            mainHandler.postDelayed(recordingTimeoutRunnable, MAX_RECORDING_MS)
        } catch (exception: Exception) {
            cancel()
            service.toast(service.getString(R.string.voice_transform_recording_failed, exception.javaClass.simpleName))
        }
    }

    private fun stopRecordingAndUpload() {
        if (!isRecording) return
        val audioFile = recordingFile
        try {
            mediaRecorder?.stop()
        } catch (exception: RuntimeException) {
            cancel()
            service.toast(R.string.voice_transform_recording_too_short)
            return
        } finally {
            releaseRecorder()
        }

        isRecording = false
        mainHandler.removeCallbacks(recordingTimeoutRunnable)
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            service.toast(R.string.voice_transform_no_audio)
            return
        }
        uploadAudioForCorrection(audioFile)
    }

    private fun uploadAudioForCorrection(audioFile: File) {
        isCorrecting = true
        service.toast(R.string.voice_transform_uploading)

        val backendUrl = AppSettings.getBackendUrl(service)
        val userId = AppSettings.getUserId(service)
        val appContext = AppSettings.getAppContext(service)

        CorrectionApiClient(backendUrl).correctAudio(
            audioFile,
            userId,
            appContext,
            object : CorrectionApiClient.Callback {
                override fun onSuccess(response: TextCorrectionResponse) {
                    mainHandler.post {
                        isCorrecting = false
                        deleteRecordingFile(audioFile)
                        showCorrectionResult(response)
                    }
                }

                override fun onError(exception: Exception) {
                    mainHandler.post {
                        isCorrecting = false
                        deleteRecordingFile(audioFile)
                        service.toast(
                            service.getString(
                                R.string.voice_transform_backend_failed,
                                exception.javaClass.simpleName,
                                backendUrl,
                            ),
                        )
                    }
                }
            },
        )
    }

    private fun showCorrectionResult(response: TextCorrectionResponse) {
        val rawText = response.rawText.orEmpty()
        val correctedText = if (TextUtils.isEmpty(response.correctedText)) rawText else response.correctedText
        val method = correctionMethodText(response)
        service.showVoiceCorrectionResult(rawText, correctedText, method)
    }

    private fun correctionMethodText(response: TextCorrectionResponse): String {
        val method = when (response.correctionMethod) {
            "llm" -> service.getString(R.string.voice_transform_method_llm)
            "rule_pinyin_fallback" -> service.getString(R.string.voice_transform_method_rule)
            "raw_text" -> service.getString(R.string.voice_transform_method_raw)
            else -> response.correctionMethod ?: "unknown"
        }
        return when {
            response.llmUsed -> service.getString(R.string.voice_transform_method_with_llm, method)
            !response.llmError.isNullOrEmpty() -> service.getString(
                R.string.voice_transform_method_llm_error,
                method,
                response.llmError,
            )
            else -> method
        }
    }

    private fun hasAudioPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun createRecorder(outputFile: File): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(service)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioChannels(1)
            setAudioEncodingBitRate(64000)
            setOutputFile(outputFile.absolutePath)
        }
    }

    private fun releaseRecorder() {
        mediaRecorder?.let {
            runCatching {
                it.reset()
                it.release()
            }
        }
        mediaRecorder = null
    }

    private fun deleteRecordingFile(file: File?) {
        if (file != null && file.exists()) {
            file.delete()
        }
    }

    companion object {
        private const val MAX_RECORDING_MS = 60000L
    }
}
