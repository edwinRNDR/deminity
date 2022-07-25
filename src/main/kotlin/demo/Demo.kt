package demo

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Demo descriptor
 */
data class Demo(
        val title: String = "nameless",
        val duration: Double = 0.0,
        val soundtrack: Soundtrack? = null,
        val `time-scale`: Double = (112.0/60.0),
) {
    data class Soundtrack(val file: String = "assets/sound/missing.mp3")

    @Transient
    var dataBase = File("demos/missing")

    companion object {
        fun loadFromJson(filename: String): Demo {
            return GsonBuilder().setLenient().create().fromJson(File(filename).readText(), Demo::class.java).apply {
                dataBase = File(filename).parentFile
            }
        }
    }
}