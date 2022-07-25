package demo

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/** Deminity configuration */
data class Configuration(
    val demo: String = "no demo specified",
    val window: Configuration.Window = Window(),
    val target: Configuration.Target = Target(),
    val capture: Capture = Capture(),
    val presentation: Presentation = Presentation(),
    val tools: Tools = Tools()
) {
    data class Presentation(val loop: Boolean = true, val holdAfterEnd: Long = 3000L)

    data class Window(
        val fullscreen: Boolean = false,
        val resizable: Boolean = false,
        val alwaysOnTop: Boolean = false,
        val width: Int = 1280,
        val height: Int = 720
    )

    data class Target(val width: Int = 1280, val height: Int = 720)

    data class Capture(
        val enabled: Boolean = false,
        val framerate: Int = 60,
        val temporalBlur: TemporalBlur = TemporalBlur(),
        val constantRateFactor: Int = 13,
        val encoder: Encoder = Encoder.x264
    ) {
        enum class Encoder {
            x264,
            x265
        }

        data class TemporalBlur(val enabled: Boolean = false, val samples: Int = 10)
    }

    data class Tools(val generateBillOfMaterials: Boolean = false, val generateUnusedMaterials: Boolean = false)

    companion object {
        fun loadFromJson(filename: String): Configuration {
            return GsonBuilder().setLenient().create().fromJson(File(filename).readText(), Configuration::class.java)
        }
    }
}