// UsbDacDetails.kt

package com.mochyy.mochymusic.playback.audio

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import androidx.annotation.RequiresApi

/** what a plugged-in usb dac says about itself: the usb side from its descriptors, the audio side from android */
data class UsbDacDetails(
    val name: String,
    val maker: String?,
    val vendorId: Int?,
    val productId: Int?,
    val firmware: String?,
    /** 1, 2 or 3, from the audio interface's protocol */
    val uacVersion: Int?,
    /** true when the dac runs on its own clock and tells the phone how fast to send */
    val asyncClock: Boolean?,
    val maxPowerMa: Int?,
    val hasMic: Boolean,
    /** empty when android says it takes any rate */
    val rates: List<Int>,
    val bitDepths: List<Int>,
    /** what android 14's mixer can hand it untouched; null on older android */
    val bitPerfect: List<DacFormat>?,
    /** false while mochy's own driver holds it, android doesn't see it as an output then */
    val androidOutput: Boolean,
)

data class DacFormat(val sampleRate: Int, val bits: Int)

object UsbDacReader {

    /** the usb dac plugged in now, or null; reads descriptors android already has, so no permission is asked */
    fun read(context: Context): UsbDacDetails? {
        val audio = context.getSystemService(AudioManager::class.java)
        val output = audio?.let(::usbOutput)
        val usb = context.getSystemService(UsbManager::class.java)?.let(::audioDevice)
        if (output == null && usb == null) return null

        val streaming = usb?.let(::streamingInterfaces).orEmpty()
        val outEndpoints = streaming.flatMap { iface ->
            (0 until iface.endpointCount).map(iface::getEndpoint).filter { ep ->
                ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC
            }
        }
        // a mic shows up as an iso endpoint coming in that carries data, not the clock feedback
        val hasMic = streaming.any { iface ->
            (0 until iface.endpointCount).map(iface::getEndpoint).any { ep ->
                ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                    (ep.attributes shr 4) and 3 == 0
            }
        } || output?.type == AudioDeviceInfo.TYPE_USB_HEADSET

        return UsbDacDetails(
            name = usb?.productName?.trim()?.takeIf { it.isNotEmpty() }
                ?: output?.productName?.toString()?.removePrefix("USB-Audio - ")?.trim()?.takeIf { it.isNotEmpty() }
                ?: "USB DAC",
            maker = usb?.manufacturerName?.trim()?.takeIf { it.isNotEmpty() },
            vendorId = usb?.vendorId,
            productId = usb?.productId,
            firmware = usb?.version?.takeIf { it.isNotBlank() },
            uacVersion = usb?.let(::uacVersion),
            asyncClock = outEndpoints.takeIf { it.isNotEmpty() }?.any { (it.attributes shr 2) and 3 == 1 },
            maxPowerMa = usb?.takeIf { it.configurationCount > 0 }?.getConfiguration(0)?.maxPower,
            hasMic = hasMic,
            rates = output?.sampleRates?.distinct()?.sorted().orEmpty(),
            bitDepths = output?.encodings?.toList().orEmpty().mapNotNull(AudioOutputs::bitDepthOf).distinct().sorted(),
            bitPerfect = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> null
                output == null || audio == null -> emptyList()
                else -> MixerFormats.bitPerfect(audio, output)
            },
            androidOutput = output != null,
        )
    }

    /** the rate android's own mixer runs at for music on whatever plays now */
    fun mixerRate(): Int? = runCatching { AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) }
        .getOrNull()?.takeIf { it > 0 }

    fun usbOutput(audio: AudioManager): AudioDeviceInfo? =
        runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
            .firstOrNull { it.isSink && AudioOutputs.kindOf(it.type) == AudioOutputs.Kind.USB }

    /** the first attached device with a usb audio streaming interface */
    fun audioDevice(usb: UsbManager): UsbDevice? =
        runCatching { usb.deviceList.values.toList() }.getOrDefault(emptyList())
            .sortedBy { it.deviceId }
            .firstOrNull { streamingInterfaces(it).isNotEmpty() }

    private fun streamingInterfaces(device: UsbDevice) = (0 until device.interfaceCount).map(device::getInterface)
        .filter { it.interfaceClass == UsbConstants.USB_CLASS_AUDIO && it.interfaceSubclass == 2 }

    private fun uacVersion(device: UsbDevice): Int? = (0 until device.interfaceCount).map(device::getInterface)
        .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_AUDIO }
        ?.let { when (it.interfaceProtocol) { 0x20 -> 2; 0x30 -> 3; else -> 1 } }
}

// android 14's mixer list, apart so older phones never load AudioMixerAttributes
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private object MixerFormats {
    fun bitPerfect(audio: AudioManager, device: AudioDeviceInfo): List<DacFormat> =
        runCatching { audio.getSupportedMixerAttributes(device) }.getOrDefault(emptyList())
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            .mapNotNull { attr -> AudioOutputs.bitDepthOf(attr.format.encoding)?.let { DacFormat(attr.format.sampleRate, it) } }
            .distinct()
            .sortedWith(compareBy({ it.bits }, { it.sampleRate }))
}
