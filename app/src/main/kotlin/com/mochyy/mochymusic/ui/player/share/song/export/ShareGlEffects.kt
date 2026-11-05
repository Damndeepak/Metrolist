// ShareGlEffects.kt

package com.mochyy.mochymusic.ui.player.share.song.export

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

// every pass draws one quad over the whole frame; the texture position comes straight from the corner, bottom left at 0, 0
private const val VertexShader = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

// shared by both blur passes: how far a point is from the card's rounded edge, below zero inside it
private const val CardShape = """
precision highp float;
uniform vec2 uSize;
uniform vec4 uRect;
uniform float uCorner;
uniform float uSigma;
varying vec2 vTexSamplingCoord;
float cardDistance(vec2 p) {
  vec2 centre = vec2(uRect.x + uRect.z, uRect.y + uRect.w) * 0.5;
  vec2 extent = vec2(uRect.z - uRect.x, uRect.w - uRect.y) * 0.5;
  vec2 q = abs(p - centre) - extent + vec2(uCorner);
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uCorner;
}
vec4 blurAlong(sampler2D source, vec2 p, vec2 direction) {
  float stepPx = max(uSigma * 2.5 / 16.0, 0.5);
  vec4 sum = vec4(0.0);
  float total = 0.0;
  for (int i = -16; i <= 16; i++) {
    float x = float(i) * stepPx;
    float w = exp(-0.5 * x * x / (uSigma * uSigma));
    sum += texture2D(source, (p + direction * x) / uSize) * w;
    total += w;
  }
  return sum / total;
}
"""

// first pass: blur across, on the card and a band round it that the second pass reads from
private const val AcrossShader = CardShape + """
uniform sampler2D uTexSampler;
void main() {
  vec2 p = vTexSamplingCoord * uSize;
  if (cardDistance(p) > uSigma * 3.0 + 2.0) {
    gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord);
  } else {
    gl_FragColor = blurAlong(uTexSampler, p, vec2(1.0, 0.0));
  }
}
"""

// second pass: blur down from the first, inside the card only, with a soft one pixel edge; everything else stays the sharp frame
private const val DownShader = CardShape + """
uniform sampler2D uTexSampler;
uniform sampler2D uAcross;
void main() {
  vec2 p = vTexSamplingCoord * uSize;
  vec4 sharp = texture2D(uTexSampler, vTexSamplingCoord);
  float d = cardDistance(p);
  if (d > 1.0) {
    gl_FragColor = sharp;
  } else {
    gl_FragColor = mix(sharp, blurAlong(uAcross, p, vec2(0.0, 1.0)), clamp(0.5 - d, 0.0, 1.0));
  }
}
"""

// the card's frame laid over the video; it's premultiplied, as the gpu drew it, and its rows run top down
private const val ForegroundShader = """
precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uForeground;
uniform float uForegroundScaleX;
uniform float uHasForeground;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 back = texture2D(uTexSampler, vTexSamplingCoord);
  vec4 front = texture2D(uForeground, vec2(vTexSamplingCoord.x * uForegroundScaleX, 1.0 - vTexSamplingCoord.y)) * uHasForeground;
  gl_FragColor = vec4(front.rgb + back.rgb * (1.0 - front.a), 1.0);
}
"""

// where the frosted card is on the frame, in pixels from the top left, and its blur radius the way android counts it
internal class CardFrost(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cornerPx: Float,
    val radiusPx: Float,
) {
    // android's blur radius to a gaussian's spread, the same sum the card's glass uses on screen
    val sigma: Float get() = 0.57735f * radiusPx + 0.5f
}

internal class CardFrostEffect(private val frost: CardFrost) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = CardFrostProgram(frost)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = frost.radiusPx < 0.5f
}

// lays each frame of the card, drawn by compose for that exact moment, over the video
internal class ForegroundEffect(private val frameAt: (presentationTimeUs: Long) -> FrameImage?) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = ForegroundProgram(frameAt)
}

private fun quadProgram(fragmentShader: String): GlProgram =
    try {
        GlProgram(VertexShader, fragmentShader).apply {
            setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        }
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }

private class CardFrostProgram(private val frost: CardFrost) : BaseGlShaderProgram(false, 1) {
    private val across = quadProgram(AcrossShader)
    private val down = quadProgram(DownShader)
    private var width = 0
    private var height = 0
    private var middleTexture = -1
    private var middleFbo = -1

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (inputWidth != width || inputHeight != height) {
            freeMiddle()
            middleTexture = GlUtil.createTexture(inputWidth, inputHeight, false)
            middleFbo = GlUtil.createFboForTexture(middleTexture)
            width = inputWidth
            height = inputHeight
        }
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            // the frame's own output, which the base class bound before calling in
            val output = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, output, 0)

            GlUtil.focusFramebufferUsingCurrentContext(middleFbo, width, height)
            across.use()
            setCard(across)
            across.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            across.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GlUtil.focusFramebufferUsingCurrentContext(output[0], width, height)
            down.use()
            setCard(down)
            down.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            down.setSamplerTexIdUniform("uAcross", middleTexture, 1)
            down.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    // gl counts rows from the bottom, so the card's top and bottom swap round
    private fun setCard(program: GlProgram) {
        program.setFloatsUniform("uSize", floatArrayOf(width.toFloat(), height.toFloat()))
        program.setFloatsUniform("uRect", floatArrayOf(frost.left, height - frost.bottom, frost.right, height - frost.top))
        program.setFloatUniform("uCorner", frost.cornerPx)
        program.setFloatUniform("uSigma", frost.sigma)
    }

    private fun freeMiddle() {
        if (middleFbo != -1) GlUtil.deleteFbo(middleFbo)
        if (middleTexture != -1) GlUtil.deleteTexture(middleTexture)
        middleFbo = -1
        middleTexture = -1
    }

    override fun release() {
        super.release()
        try {
            freeMiddle()
            across.delete()
            down.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}

private class ForegroundProgram(private val frameAt: (Long) -> FrameImage?) : BaseGlShaderProgram(false, 1) {
    private val program = quadProgram(ForegroundShader)
    private var foregroundTexture = -1

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            // waits here, on the gl thread, while the main thread draws the card for this moment
            val frame = frameAt(presentationTimeUs)
            if (foregroundTexture == -1) foregroundTexture = GlUtil.generateTexture()
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            if (frame != null) {
                GlUtil.setTexture(foregroundTexture, frame.bitmap)
                program.setFloatUniform("uForegroundScaleX", frame.width.toFloat() / frame.bitmap.width)
                program.setFloatUniform("uHasForeground", 1f)
            } else {
                program.setFloatUniform("uForegroundScaleX", 1f)
                program.setFloatUniform("uHasForeground", 0f)
            }
            program.setSamplerTexIdUniform("uForeground", foregroundTexture, 1)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            if (foregroundTexture != -1) GlUtil.deleteTexture(foregroundTexture)
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}
