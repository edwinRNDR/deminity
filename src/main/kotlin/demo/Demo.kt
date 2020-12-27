package demo

import com.google.gson.Gson
import java.io.File

class Soundtrack(val file: String = "assets/sound/missing.mp3")

class Demo(
    val title: String = "nameless",
    val duration: Double = 0.0,
    val soundtrack: Soundtrack? = null,
    val timescale: Double = (112.0/60.0)
) {
    var dataBase = File("demos/missing")

    companion object {
        fun loadFromJson(filename: String): Demo {
            return Gson().fromJson(File(filename).readText(), Demo::class.java).apply {
                dataBase = File(filename).parentFile
            }
        }
    }
}