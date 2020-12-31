package demo

import com.google.gson.Gson
import java.io.File

class Configuration(
    val demo: String = "no demo specified",
    val window: Configuration.Window = Window(),
    val target: Configuration.Target = Target(),
    val capture: Capture = Capture(),
    val presentation: Presentation = Presentation()
) {

    class Presentation(val loop: Boolean = true, val holdAfterEnd: Long = 3000L)


    class Window(
        val fullscreen: Boolean = false,
        val resizable: Boolean = false,
        val alwaysOnTop: Boolean = false,
        val width: Int = 1280,
        val height: Int = 720
    )

    class Target(val width: Int = 1280, val height: Int = 720)

    class Capture(
        val enabled: Boolean = false,
        val framerate: Int = 60,
        val temporalBlur: TemporalBlur = TemporalBlur(),
        val constantRateFactor: Int = 13,
        val encoder : Encoder = Encoder.x264
    ) {
        enum class Encoder {
            x264,
            x265
        }

        class TemporalBlur(val enabled: Boolean = false, val samples: Int = 10)
    }

    companion object {
        fun loadFromJson(filename: String): Configuration {
            return Gson().fromJson(File(filename).readText(), Configuration::class.java)
        }
    }
}