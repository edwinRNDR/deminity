package tools

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.shape.Rectangle
import org.openrndr.shape.drawComposition
import org.openrndr.svg.saveToFile
import java.io.File

fun main() {
    application {

        configure {
            width = 1280
            height = 720
        }

        program {
            val lineCount = 120
            val composition = drawComposition(documentBounds = Rectangle(0.0, 0.0, 1280.0, 720.0)) {
                for (i in 0 until lineCount) {
                    val y = height / (lineCount) * (i + 0.5)

                    val sx = (1280 - 720.0) / 2.0
                    val ex = 1280 - (1280 - 720.0) / 2.0

                    lineSegment(sx, y, ex, y)
                }
            }
            composition.saveToFile(File("lines-$lineCount.svg"))
            extend {
                drawer.clear(ColorRGBa.PINK)
                drawer.composition(composition)
            }
        }
    }
}