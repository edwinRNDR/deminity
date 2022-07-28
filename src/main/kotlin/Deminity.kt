import bass.Channel
import bass.DummyChannel
import bass.playMusic
import demo.Configuration
import demo.Demo
import demo.LayerRenderer
import demo.PostProcessor
import org.openrndr.Fullscreen
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.extra.temporalblur.TemporalBlur
import org.openrndr.extra.videoprofiles.X265Profile
import org.openrndr.extra.imageFit.FitMethod
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.ffmpeg.MP4Profile
import org.openrndr.ffmpeg.ScreenRecorder
import java.io.File

fun main(args: Array<String>) {
    val configFilename = args.getOrElse(0) { "data/config.json" }
    val configuration = Configuration.loadFromJson(configFilename)
    val demo = Demo.loadFromJson("${configuration.demo}/demo.json")

    application {
        configure {
            windowSetIcon = true
            width = configuration.window.width
            height = configuration.window.height
            title = demo.title
            windowAlwaysOnTop = configuration.window.alwaysOnTop
            windowResizable = configuration.window.resizable
            fullscreen = if (configuration.window.fullscreen) Fullscreen.CURRENT_DISPLAY_MODE else Fullscreen.DISABLED
        }
        program {
            val layerRenderer = LayerRenderer(this, demo, configuration.target.width, configuration.target.height)
            var enablePostProcessing = true

            program.ended.listen {
                /**
                 * Generate a bill of materials when the program is closed.
                 */

                /** Recursive file lister */
                fun File.listRecursive(): List<File> {
                    val list = this.listFiles()!!
                    return list.filter {
                        it.isFile && !it.isHidden
                    } + list.filter { it.isDirectory }.flatMap { it.listRecursive() }
                }

                val billOfMaterials = setOfNotNull(
                    demo.soundtrack?.let {
                        File("${demo.dataBase}/assets", it.file).path
                    }
                ) + layerRenderer.billOfMaterials

                if (configuration.tools.generateBillOfMaterials) {
                    File("bill-of-materials.txt").writeText(
                        billOfMaterials.sorted().joinToString("\n")
                    )
                }

                if (configuration.tools.generateUnusedMaterials) {
                    val allAssets = File("${demo.dataBase}/assets").listRecursive().toSet()
                    File("unused-materials.txt").writeText(
                        allAssets.filter {
                            it.path !in billOfMaterials
                        }.sorted().joinToString(
                            "\n"
                        )
                    )
                }
            }

            /**
             * Set up video capturing
             */
            if (configuration.capture.enabled) {
                extend(ScreenRecorder()) {
                    width = configuration.target.width
                    height = configuration.target.height
                    frameRate = configuration.capture.framerate
                    maximumDuration = demo.duration

                    when (configuration.capture.encoder) {
                        Configuration.Capture.Encoder.x264 -> (profile as MP4Profile).apply {
                            constantRateFactor(configuration.capture.constantRateFactor)
                        }

                        Configuration.Capture.Encoder.x265 -> {
                            val x265Profile = X265Profile().apply {
                                constantRateFactor(configuration.capture.constantRateFactor)
                            }
                            profile = x265Profile
                        }
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

            val channel =
                if (!configuration.capture.enabled) {
                    demo.soundtrack?.let {
                        playMusic(File("${demo.dataBase}/assets", it.file).path, loop = configuration.presentation.loop)
                    } ?: DummyChannel(this)
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
                    layerRenderer.renderLayers(time * demo.`time-scale`)

                    if (enablePostProcessing) {
                        postProcessor.postProcess(layerRenderer.finalTarget.colorBuffer(0), time * demo.`time-scale`)
                        drawer.clear(ColorRGBa.BLACK)
                        drawer.image(postProcessor.result)
                    } else {
                        drawer.image(layerRenderer.finalTarget.colorBuffer(0))
                    }
                }
                drawer.imageFit(
                    target.colorBuffer(0),
                    0.0,
                    0.0,
                    width * 1.0,
                    height * 1.0,
                    fitMethod = FitMethod.Contain
                )
                layerRenderer.renderUI(time * demo.`time-scale`)
            }
        }
    }
}