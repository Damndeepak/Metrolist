package com.mochyy.mochymusic.ui.player.ipod

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mochyy.mochymusic.ui.player.studio.vibrator

// the click wheel's feel: a crisp tick per notch, a firm click for the buttons and a light clack when they let go
@Stable
class IpodHaptics(context: Context) {
    private val vibrator: Vibrator? = context.vibrator()?.takeIf { it.hasVibrator() }
    // effects are held as plain objects so android 7 never loads the vibration effect class, it only came in android 8
    private val tickEffect = effect(Primitive.TICK, 0.55f, predefined = VibrationEffect.EFFECT_TICK, oneShotMs = 8, amplitude = 90)
    private val pressEffect = effect(Primitive.CLICK, 0.85f, predefined = VibrationEffect.EFFECT_CLICK, oneShotMs = 16, amplitude = 200)
    private val releaseEffect = effect(Primitive.TICK, 0.35f, predefined = VibrationEffect.EFFECT_TICK, oneShotMs = 6, amplitude = 60)

    fun tick() = play(tickEffect, 8L)

    fun press() = play(pressEffect, 16L)

    fun release() = play(releaseEffect, 6L)

    // a whole button click in one go, for the ring's buttons that only know they were tapped once the finger lifts
    fun click() = play(pressEffect, 16L)

    // handed to a thread of its own: each buzz is a call into the system, and on miui a fast spin of them stuttered the list
    private fun play(effect: Any?, fallbackMs: Long) {
        val motor = vibrator ?: return
        HapticsThread.handler.post {
            runCatching {
                if (Build.VERSION.SDK_INT >= 26 && effect is VibrationEffect) {
                    motor.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    motor.vibrate(fallbackMs)
                }
            }
        }
    }

    // the best the motor can do, newest first: a composed primitive, a predefined effect, then a short pulse
    private fun effect(primitive: Primitive, scale: Float, predefined: Int, oneShotMs: Long, amplitude: Int): Any? {
        val motor = vibrator ?: return null
        if (Build.VERSION.SDK_INT >= 30) composed(motor, primitive, scale)?.let { return it }
        if (Build.VERSION.SDK_INT >= 29) return VibrationEffect.createPredefined(predefined)
        if (Build.VERSION.SDK_INT >= 26) {
            val strength = if (motor.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            return VibrationEffect.createOneShot(oneShotMs, strength)
        }
        return null
    }

    @RequiresApi(30)
    private fun composed(motor: Vibrator, primitive: Primitive, scale: Float): VibrationEffect? {
        val id = when (primitive) {
            Primitive.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
            Primitive.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
        }
        if (!motor.areAllPrimitivesSupported(id)) return null
        return VibrationEffect.startComposition().addPrimitive(id, scale).compose()
    }

    private enum class Primitive { TICK, CLICK }
}

// one small thread for the whole app, started the first time the wheel buzzes
private object HapticsThread {
    val handler: Handler by lazy {
        val thread = HandlerThread("IpodHaptics").apply { start() }
        Handler(thread.looper)
    }
}

@Composable
fun rememberIpodHaptics(): IpodHaptics {
    val context = LocalContext.current
    return remember(context) { IpodHaptics(context) }
}
