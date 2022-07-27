package demo

import java.io.File

class TextCurtain(val characters: List<CharArray>, val encounters: List<IntArray>, val frameCount: Int) {

    fun prepareText(curtainStart: Double, curtainEnd: Double): List<String> {
        return prepareText((curtainStart * frameCount).toInt(), (curtainEnd * frameCount).toInt())
    }

    fun prepareText(frameStart: Int, frameEnd: Int): List<String> {
        val textHeight = characters.size
        val textWidth = characters[0].size
        return (characters.indices).map { y ->
            String((0 until characters[y].size).map { x -> if (encounters[y][x] in frameStart until frameEnd) characters[y][x] else ' ' }
                .toCharArray())
        }
    }
}

fun loadTextCurtain(file: File): TextCurtain {
    val r = file.reader()
    val lines = r.readLines()
    val (textWidth, textHeight) = lines.first().split(" ").map { it.toInt() }

    println("text width: $textWidth")
    println("text height: $textHeight")
    val characters = lines.asSequence().drop(1).take(textHeight).toList().map { l ->
        (0 until textWidth).map { l.getOrNull(it) ?: ' ' }.toCharArray()
    }
    val encounters = lines.asSequence().drop(1 + textHeight).toList().map {
        it.split(",").map { t -> t.trim().toInt() }.toIntArray()
    }
    r.close()

    val frameCount = encounters.maxOf { it.maxOrNull() ?: 0 }

    return TextCurtain(characters, encounters, frameCount)
}