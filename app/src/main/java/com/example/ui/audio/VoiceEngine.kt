package com.example.ui.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

data class RecordedVoiceClip(
    val base64Wav: String,
    val durationSec: Int
)

/**
 * Hardware microphone & AudioTrack engine for real voice channels and push-to-talk clips.
 */
class VoiceEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isRecordingClip = MutableStateFlow(false)
    val isRecordingClip: StateFlow<Boolean> = _isRecordingClip.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private var monitorJob: Job? = null
    private var clipJob: Job? = null
    private var playJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val clipPcmBuffer = ByteArrayOutputStream()
    private var clipStartMs: Long = 0L

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startMicMonitor(onSpeakingChanged: (Boolean) -> Unit) {
        if (!hasMicPermission()) {
            stopMicMonitor()
            return
        }
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)
            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            }.getOrNull() ?: return@launch

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { recorder.release() }
                return@launch
            }
            audioRecord = recorder
            runCatching { recorder.startRecording() }

            val shortBuf = ShortArray(minBuf / 2)
            var lastSpokeAt = 0L
            var prevSpeaking = false

            try {
                while (isActive) {
                    val read = recorder.read(shortBuf, 0, shortBuf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = shortBuf[i].toDouble()
                            sumSq += s * s
                        }
                        val rms = sqrt(sumSq / read).toFloat()
                        val normalized = (rms / 6500f).coerceIn(0f, 1f)
                        _micLevel.value = normalized

                        if (_isRecordingClip.value && clipPcmBuffer.size() < MAX_PCM_BYTES) {
                            synchronized(clipPcmBuffer) {
                                for (i in 0 until read) {
                                    val v = shortBuf[i].toInt()
                                    clipPcmBuffer.write(v and 0xFF)
                                    clipPcmBuffer.write((v shr 8) and 0xFF)
                                }
                            }
                        }

                        val now = System.currentTimeMillis()
                        if (normalized > 0.14f) {
                            lastSpokeAt = now
                        }
                        val speakingNow = (now - lastSpokeAt) < 600L
                        if (speakingNow != prevSpeaking) {
                            prevSpeaking = speakingNow
                            _isSpeaking.value = speakingNow
                            onSpeakingChanged(speakingNow)
                        }
                    }
                    delay(40)
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                audioRecord = null
                _micLevel.value = 0f
                if (prevSpeaking) {
                    _isSpeaking.value = false
                    onSpeakingChanged(false)
                }
            }
        }
    }

    fun stopMicMonitor() {
        monitorJob?.cancel()
        monitorJob = null
        _micLevel.value = 0f
        _isSpeaking.value = false
    }

    @SuppressLint("MissingPermission")
    fun startPushToTalkClip() {
        if (!hasMicPermission()) return
        synchronized(clipPcmBuffer) {
            clipPcmBuffer.reset()
        }
        clipStartMs = System.currentTimeMillis()
        _isRecordingClip.value = true

        // If monitor isn't running yet, start temporary recorder for PTT
        if (monitorJob?.isActive != true) {
            clipJob?.cancel()
            clipJob = scope.launch {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)
                val recorder = runCatching {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf * 2
                    )
                }.getOrNull() ?: return@launch
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    runCatching { recorder.release() }
                    return@launch
                }
                runCatching { recorder.startRecording() }
                val buf = ByteArray(minBuf)
                try {
                    while (isActive && _isRecordingClip.value && clipPcmBuffer.size() < MAX_PCM_BYTES) {
                        val read = recorder.read(buf, 0, buf.size)
                        if (read > 0) {
                            synchronized(clipPcmBuffer) {
                                clipPcmBuffer.write(buf, 0, read)
                            }
                        }
                    }
                } finally {
                    runCatching { recorder.stop() }
                    runCatching { recorder.release() }
                }
            }
        }
    }

    fun stopAndEncodePushToTalkClip(): RecordedVoiceClip? {
        _isRecordingClip.value = false
        clipJob?.cancel()
        clipJob = null
        val pcmBytes = synchronized(clipPcmBuffer) {
            val out = clipPcmBuffer.toByteArray()
            clipPcmBuffer.reset()
            out
        }
        if (pcmBytes.size < 1600) return null
        val durationSec = ((System.currentTimeMillis() - clipStartMs) / 1000L).toInt().coerceIn(1, 30)
        val wavBytes = wrapWavHeader(pcmBytes, SAMPLE_RATE)
        val encoded = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        return RecordedVoiceClip(base64Wav = encoded, durationSec = durationSec)
    }

    fun playVoiceMessage(messageId: String, base64Wav: String) {
        if (_playingMessageId.value == messageId) {
            stopPlayback()
            return
        }
        stopPlayback()
        _playingMessageId.value = messageId
        playJob = scope.launch {
            val raw = runCatching { Base64.decode(base64Wav, Base64.DEFAULT) }.getOrNull()
            if (raw == null || raw.size <= 44) {
                _playingMessageId.value = null
                return@launch
            }
            val pcm = raw.copyOfRange(44, raw.size)
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull()

            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                runCatching { track?.release() }
                _playingMessageId.value = null
                return@launch
            }

            audioTrack = track
            try {
                track.play()
                var offset = 0
                val chunk = minBuf
                while (isActive && offset < pcm.size) {
                    val count = minOf(chunk, pcm.size - offset)
                    track.write(pcm, offset, count)
                    offset += count
                }
                delay(180)
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
                audioTrack = null
                if (_playingMessageId.value == messageId) {
                    _playingMessageId.value = null
                }
            }
        }
    }

    fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        _playingMessageId.value = null
    }

    fun release() {
        stopMicMonitor()
        clipJob?.cancel()
        stopPlayback()
    }

    private fun wrapWavHeader(pcm: ByteArray, sampleRate: Int): ByteArray {
        val totalAudioLen = pcm.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * 2
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = 1; header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2; header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
        return header + pcm
    }

    private companion object {
        const val SAMPLE_RATE = 8000 // Compact 16KB/sec mono speech -> ~150KB Base64 for 7s clip
        const val MAX_PCM_BYTES = 160_000
    }
}
