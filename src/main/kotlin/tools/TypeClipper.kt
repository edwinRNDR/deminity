package tools

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.ClipMode
import org.openrndr.shape.Rectangle
import org.openrndr.shape.drawComposition
import org.openrndr.svg.loadSVG
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

            val type = loadSVG("tool-data/illustration-04-fixed.svg")

            val composition = drawComposition(documentBounds = Rectangle(0.0, 0.0, 1280.0, 720.0)) {
                for (i in 0 until lineCount) {
                    val y = height / (lineCount) * (i + 0.5)

                    val sx = 0.0 //(1280-1068.0) / 2.0
                    val ex = 1280.0 //1280.0 - (1280-1068.0) / 2.0

                    lineSegment(sx, y, ex, y)
                }
                clipMode = ClipMode.DIFFERENCE

                for (shape in type.findShapes()) {
                    shape(shape.shape)
                }

                //circle(Vector2(width / 2.0, height / 2.0), 360.0)
            }

            val sc = composition
            val flatComposition = drawComposition(documentBounds = Rectangle(0.0, 0.0, 1280.0, 720.0)) {

                val contours = sc.findShapes().flatMap {
                    it.shape.contours
                }

                sc.findShapes().forEach {

                    for (contour in it.shape.contours) {
                        contour(contour)
                    }

                }
            }

            flatComposition.saveToFile(File("${String.format("%03d", lineCount)}.svg"))

            extend {
                drawer.clear(ColorRGBa.PINK)
                drawer.composition(composition)
            }
        }
    }
}