package com.example.ui.audio

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

enum class NexusSfx {
    CLICK,
    SCORE,
    REWARD,
    ERROR
}

class NexusSoundPlayer(
    private val isSoundEnabled: () -> Boolean,
    private val isHapticsEnabled: () -> Boolean,
    private val haptic: HapticFeedback
) {
    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, 55)
    }.getOrNull()

    fun play(sfx: NexusSfx) {
        if (isHapticsEnabled()) {
            val type = when (sfx) {
                NexusSfx.CLICK -> HapticFeedbackType.TextHandleMove
                NexusSfx.SCORE, NexusSfx.REWARD, NexusSfx.ERROR -> HapticFeedbackType.LongPress
            }
            runCatching { haptic.performHapticFeedback(type) }
        }
        if (isSoundEnabled()) {
            val (tone, duration) = when (sfx) {
                NexusSfx.CLICK -> ToneGenerator.TONE_PROP_BEEP to 45
                NexusSfx.SCORE -> ToneGenerator.TONE_PROP_ACK to 90
                NexusSfx.REWARD -> ToneGenerator.TONE_PROP_BEEP2 to 150
                NexusSfx.ERROR -> ToneGenerator.TONE_PROP_NACK to 120
            }
            runCatching { toneGenerator?.startTone(tone, duration) }
        }
    }

    fun release() {
        runCatching { toneGenerator?.release() }
    }
}

@Composable
fun rememberNexusSoundPlayer(
    soundEnabled: Boolean,
    hapticsEnabled: Boolean
): NexusSoundPlayer {
    val haptic = LocalHapticFeedback.current
    val soundState = androidx.compose.runtime.rememberUpdatedState(soundEnabled)
    val hapticsState = androidx.compose.runtime.rememberUpdatedState(hapticsEnabled)
    val player = remember(haptic) {
        NexusSoundPlayer(
            isSoundEnabled = { soundState.value },
            isHapticsEnabled = { hapticsState.value },
            haptic = haptic
        )
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}
