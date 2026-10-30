package com.fitness.companion.data.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.fitness.companion.domain.sensor.FeedbackPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Plays system tones and vibrations as rep and countdown cues. */
@Singleton
class ToneFeedbackPlayer @Inject constructor(
    @ApplicationContext context: Context,
) : FeedbackPlayer {

    // Creating a ToneGenerator fails when the audio service has no free track; the app then
    // simply stays silent and keeps the haptic cue.
    private val toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
    } catch (e: RuntimeException) {
        null
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun repCompleted() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        vibrate(40)
    }

    override fun countdownTick() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
    }

    override fun countdownFinished() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        vibrate(250)
    }

    private fun vibrate(durationMs: Long) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        device.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private companion object {
        const val TONE_VOLUME = 80
    }
}
