import bass.Channel
import bass.playMusic
import demo.Configuration
import demo.Demo
import demo.LayerRenderer
import demo.PostProcessor
import org.openrndr.Fullscreen
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.BufferMultisample
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.extra.temporalblur.TemporalBlur
import org.openrndr.extras.imageFit.FitMethod
import org.openrndr.extras.imageFit.imageFit
import org.openrndr.ffmpeg.MP4Profile
import org.openrndr.ffmpeg.ScreenRecorder
import java.io.File

fun main() {
    val configuration = Configuration.loadFromJson("data/config.json")
    val demo = Demo.loadFromJson("${configuration.demo}/demo.json")

    application {
        configure {
            width = configuration.window.width
            height = configuration.window.height
            title = demo.title
            windowAlwaysOnTop = configuration.window.alwaysOnTop
            windowResizable = configuration.window.resizable
            fullscreen = if (configuration.window.fullscreen) Fullscreen.CURRENT_DISPLAY_MODE else Fullscreen.DISABLED
        }
        program {
            val layerRenderer = LayerRenderer(this, demo)
            var enablePostProcessing = true

            if (configuration.capture.enabled) {
                extend(ScreenRecorder()) {
                    frameRate = configuration.capture.framerate
                    maximumDuration = demo.duration
                    (profile as MP4Profile).apply {
                        constantRateFactor(configuration.capture.constantRateFactor)
                    }
                }
                if (configuration.capture.temporalBlur.enabled) {
                    extend(TemporalBlur()) {
                        samples = configuration.capture.temporalBlur.samples
                        fps = configuration.capture.framerate.toDouble()
                    }
                }
            }

            keyboard.character.listen {
                if (it.character == 'o') {
                    enablePostProcessing = !enablePostProcessing
                }
            }

            val postProcessor = PostProcessor.loadFromJson(this, File("${demo.dataBase}/post", "post.json"))

            val targetWidth = configuration.target.width
            val targetHeight = configuration.target.height
            val target = renderTarget(targetWidth, targetHeight) {
                colorBuffer()
            }

            val layerTarget = renderTarget(targetWidth, targetHeight, multisample = BufferMultisample.SampleCount(8)) {
                colorBuffer()
                depthBuffer()
            }
            val layerResolved = colorBuffer(targetWidth, targetHeight)

            val channel =
            if (!configuration.capture.enabled) {
                demo.soundtrack?.let {
                    playMusic(File("${demo.dataBase}/assets", it.file).path, loop = configuration.presentation.loop)
                } ?: Channel()
            } else {
                Channel()
            }
            layerRenderer.channel = channel
            extend {
                val time = seconds.coerceAtLeast(0.0)
                if (!configuration.presentation.loop && !configuration.capture.enabled) {
                    if (time >= demo.duration) {
                        channel.pause()
                        Thread.sleep(configuration.presentation.holdAfterEnd)
                        application.exit()
                    }
                }

                drawer.clear(ColorRGBa.BLACK)
                drawer.isolatedWithTarget(target) {
                    drawer.clear(ColorRGBa.BLACK)
                    drawer.ortho(target)
                    drawer.isolatedWithTarget(layerTarget) {
                        drawer.clear(ColorRGBa.BLACK)
                        layerRenderer.renderLayers(time * demo.timescale)
                    }
                    layerTarget.colorBuffer(0).copyTo(layerResolved)

                    if (enablePostProcessing) {
                        postProcessor.postProcess(layerResolved, time * demo.timescale)
                        drawer.clear(ColorRGBa.BLACK)
                        drawer.image(postProcessor.result)
                    } else {
                        drawer.image(layerResolved)
                    }
                }
                drawer.imageFit(target.colorBuffer(0), 0.0, 0.0, width * 1.0, height * 1.0, fitMethod = FitMethod.Contain)
                layerRenderer.renderUI(time * demo.timescale)
            }
        }
    }
}