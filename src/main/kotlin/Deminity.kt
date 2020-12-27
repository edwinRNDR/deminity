import bass.playMusic
import demo.Demo
import demo.LayerRenderer
import demo.PostProcessor
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.extras.imageFit.imageFit
import org.openrndr.ffmpeg.ScreenRecorder
import java.io.File

fun main() {
    val dataBase = "demos/microformer"

    val demo = Demo.loadFromJson("$dataBase/demo.json")

    application {
        configure {
            width = 1280
            height = 720
            title = demo.title
            windowAlwaysOnTop = true
        }
        program {
            val layerRenderer = LayerRenderer(this, demo)
            var enablePostProcessing = true
            val enableScreenRecorder = false

            if (enableScreenRecorder) {
                extend(ScreenRecorder()) {
                    frameRate = 60
                }
            }


            keyboard.character.listen {
                if (it.character == 'o') {
                    enablePostProcessing = !enablePostProcessing
                }
            }

            val postProcessor = PostProcessor.loadFromJson(this, File("${demo.dataBase}/post", "post.json"))

            val targetWidth = 1280
            val targetHeight = 720
            val target = renderTarget(targetWidth, targetHeight) {
                colorBuffer()
            }

            val layerTarget = renderTarget(targetWidth, targetHeight) {
                colorBuffer()
                depthBuffer()
            }

            if (!enableScreenRecorder) {
                demo.soundtrack?.let {
                    playMusic(File("${demo.dataBase}/assets", it.file).path)
                }
            }
            extend {
                val time = seconds
                drawer.clear(ColorRGBa.BLACK)
                drawer.isolatedWithTarget(target) {
                    drawer.clear(ColorRGBa.BLACK)
                    drawer.ortho(target)
                    drawer.isolatedWithTarget(layerTarget) {
                        drawer.clear(ColorRGBa.BLACK)
                        layerRenderer.renderLayers(time * demo.timescale)
                    }
                    if (enablePostProcessing) {
                        postProcessor.postProcess(layerTarget.colorBuffer(0), time * demo.timescale)
                        drawer.clear(ColorRGBa.BLACK)
                        drawer.image(postProcessor.result)
                    } else {
                        drawer.image(layerTarget.colorBuffer(0))
                    }
                }
                drawer.imageFit(target.colorBuffer(0), 0.0, 0.0, width * 1.0, height * 1.0)
                drawer.fill = ColorRGBa.YELLOW
                drawer.text(String.format("%.3f", time * demo.timescale), 20.0, 20.0)
            }
        }
    }
}