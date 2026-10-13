package com.ruich97.nijikaahoge.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic ticks, borrowed from the Mi Band port of this game.
 *
 * The watch build has no audio at all — its manifest only asks for
 * `system.vibrator` and the game answers every input with a buzz ("short" when
 * an ahoge is tapped, "long" when a round of collecting finishes). Sound already
 * covers that role on phones, so the haptics stay deliberately light: a tick per
 * ahoge, a slightly longer one on score milestones.
 *
 * Silently no-ops when the device has no vibrator, so it also runs on
 * emulators, TV boxes and audio-only tablets.
 */
class Vib(context: Context) {

    private val vibrator: Vibrator? = resolveVibrator(context)

    @Volatile private var released = false

    /** short tick — grabbing / bagging an ahoge */
    // 9 ms: settled here after on-device tuning on a linear-resonant watch motor
    // (Snapdragon W5+ Gen 1). 12 ms read as a distinct buzz rather than a tick,
    // 6 ms came out a touch too light. LRA actuators reach full amplitude in
    // ~1 ms, so this is clearly felt there; an older eccentric-rotor phone may
    // want more.
    fun tick() = oneShot(9L, 70)

    /** longer buzz — score milestone reached */
    fun reward() = oneShot(55L, 150)

    private fun oneShot(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (released) return
        try {
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            } else {
                // pre-O has no amplitude control; duration only
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (t: Throwable) {
            // a broken vibrator must never take the game down
            android.util.Log.w("Ahoge", "vibrate failed", t)
        }
    }

    fun release() { released = true }

    private companion object {
        fun resolveVibrator(context: Context): Vibrator? = try {
            if (Build.VERSION.SDK_INT >= 31) {
                // Android 12 replaced getSystemService(VIBRATOR_SERVICE) with
                // VibratorManager; the SDK_INT guard keeps the new class from
                // ever being resolved on older devices.
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (t: Throwable) {
            null
        }
    }
}
