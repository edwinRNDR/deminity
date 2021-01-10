package demo

import mu.KotlinLogging
import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ColorType
import org.openrndr.draw.RenderTarget
import org.openrndr.draw.colorBuffer
import org.openrndr.extra.fx.blend.Add
import org.openrndr.extra.fx.blur.GaussianBloom
import org.openrndr.extra.fx.blur.LaserBlur
import org.openrndr.extra.fx.color.Pal
import org.openrndr.extra.fx.distort.TapeNoise
import org.openrndr.extra.fx.distort.VideoGlitch
import org.openrndr.extra.keyframer.Keyframer
import org.openrndr.math.Vector2
import org.operndr.extras.filewatcher.watchFile
import java.io.File

private val logger = KotlinLogging.logger {}

class PostAnimation : Keyframer() {
    val bloomGain by DoubleChannel("bloom-gain")
    val palGain by DoubleChannel("pal-gain")
    val palInvGain by DoubleChannel("pal-inv-gain")
    val palAmount by DoubleChannel("pal-amount")
    val palPixelation by DoubleChannel("pal-pixelation")

    val laserCenter by Vector2Channel(arrayOf("laser-center-x", "laser-center-y"), defaultValue = Vector2.ZERO)
    val laserRadius by DoubleChannel("laser-radius", defaultValue = -0.18)
    val laserAmp0 by DoubleChannel("laser-amp0", defaultValue = 0.5)
    val laserAmp1 by DoubleChannel("laser-amp1", defaultValue = 0.5)
    val laserVignette by DoubleChannel("laser-vignette", defaultValue = 0.0)
    val laserVignetteSize by DoubleChannel("laser-vignette-size", defaultValue = 0.0)
    val laserAberration by DoubleChannel("laser-aberration", defaultValue = 0.006)
    val laserExp by DoubleChannel("laser-exp", defaultValue = 0.739)
    val laserPhase by DoubleChannel("laser-phase", defaultValue = 0.0)

    val crtAmplitude by DoubleChannel("crt-amplitude", 0.0)
    val crtScroll0 by DoubleChannel("crt-scroll0", 0.0)
    val crtScroll1 by DoubleChannel("crt-scroll1", 0.0)

    val vcrGain by DoubleChannel("vcr-gain", defaultValue = 1.0)
    val vcrNoiseLow by DoubleChannel("vcr-noise-low", 0.5)
    val vcrNoiseHigh by DoubleChannel("vcr-noise-high", 0.8)
    val vcrTint by RGBChannel(arrayOf("vcr-tint-r", "vcr-tint-g", "vcr-tint-b"), ColorRGBa.WHITE)
    val vcrGapFrequency by DoubleChannel("vcr-gap-frequency", 10.0)
    val vcrGapLow by DoubleChannel("vcr-gap-low", -1.0)
    val vcrGapHigh by DoubleChannel("vcr-gap-high", -0.99)
    val vcrDeformGain by DoubleChannel("vcr-deform-gain", 0.1)
    val vcrDeformFrequency by DoubleChannel("vcr-deform-frequency", 1.0)
}

class PostProcessor(val animationWatcher: () -> PostAnimation) {
    private val bloom by lazy { GaussianBloom() }
    private val add by lazy { Add() }
    private val laserBlur by lazy { LaserBlur() }
    private val pal by lazy { Pal() }
    private val crt by lazy { VideoGlitch() }
    private val vcr by lazy { TapeNoise() }

    /** intermediate targets */
    private val intermediate by lazy {
        val w = RenderTarget.active.width
        val h = RenderTarget.active.height
        List(2) {
            colorBuffer(w, h, type = ColorType.FLOAT16)
        }
    }

    /** final result */
    val result by lazy {
        val w = RenderTarget.active.width
        val h = RenderTarget.active.height
        colorBuffer(w, h)
    }

    fun postProcess(input: ColorBuffer, time: Double) {
        val animation = animationWatcher()
        animation(time)

        // marshall parameters from the animation into the filters

        bloom.gain = animation.bloomGain

        pal.filter_gain = animation.palGain
        pal.filter_invgain = animation.palInvGain
        pal.pixelation = animation.palPixelation
        pal.amount = animation.palAmount

        laserBlur.aberration = animation.laserAberration
        laserBlur.amp0 = animation.laserAmp0
        laserBlur.amp1 = animation.laserAmp1
        laserBlur.center = animation.laserCenter
        laserBlur.exp = animation.laserExp
        laserBlur.radius = animation.laserRadius
        laserBlur.vignette = animation.laserVignette
        laserBlur.vignetteSize = animation.laserVignetteSize
        laserBlur.phase = animation.laserPhase

        crt.time = time
        crt.amplitude = animation.crtAmplitude
        crt.scrollOffset0 = animation.crtScroll0
        crt.scrollOffset1 = animation.crtScroll1

        vcr.time = time
        vcr.gain = animation.vcrGain
        vcr.noiseHigh = animation.vcrNoiseHigh
        vcr.noiseLow = animation.vcrNoiseLow
        vcr.tint = animation.vcrTint
        vcr.monochrome = true
        vcr.gapLow = animation.vcrGapLow
        vcr.gapHigh = animation.vcrGapHigh
        vcr.gapFrequency = animation.vcrGapFrequency
        vcr.deformAmplitude = animation.vcrDeformGain
        vcr.deformFrequency = animation.vcrDeformFrequency

        laserBlur.apply(input, intermediate[0])
        add.apply(arrayOf(intermediate[0], input), intermediate[0])
        bloom.apply(intermediate[0], intermediate[1])

        vcr.apply(intermediate[1], intermediate[0])
        crt.apply(intermediate[0], intermediate[1])
        pal.apply(intermediate[1], result)
    }

    companion object {
        fun loadFromJson(program: Program, file: File): PostProcessor {
            val watcher = program.watchFile(file) {
                val postAnimation = PostAnimation()
                try {
                    postAnimation.loadFromJson(file)
                } catch (e: Throwable) {
                    logger.error {
                        e.message
                    }
                    throw e
                }
                postAnimation
            }
            return PostProcessor(watcher)
        }
    }
}