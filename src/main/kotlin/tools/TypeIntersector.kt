package tools

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.shape.ClipMode
import org.openrndr.shape.Rectangle
import polyfill.drawComposition
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
            val type = loadSVG("tool-data/illustration-07.svg")
            val composition = drawComposition(documentBounds = Rectangle(0.0, 0.0, 1280.0, 720.0)) {
                for (shape in type.findShapes()) {
                    group {
                        for (i in 0 until lineCount) {
                            val y = height / (lineCount) * (i + 0.5)
                            val sx = 0.0 //(1280-720.0) / 2.0
                            val ex = 1280.0 // - (1280-720.0) / 2.0
                            lineSegment(sx, y, ex, y)
                        }
                        clipMode = ClipMode.INTERSECT_GROUP
                        translate(0.0, 0.0)
                        shape(shape.shape)
                    }
                }
            }
            val sc = composition
            val flatComposition = drawComposition(documentBounds = Rectangle(0.0, 0.0, 1280.0, 720.0)) {

                sc.findShapes().flatMap {
                    it.shape.contours
                }.filter { it.segments.size == 1 }.sortedWith(compareBy({ it.bounds.y }, { it.bounds.x })).forEach {
                    for (contour in it.shape.contours) {
                        contour(contour)
                    }
                }
            }

            flatComposition.saveToFile(File("${String.format("%03d", lineCount)}.svg"))

            extend {
                drawer.clear(ColorRGBa.PINK)
                drawer.composition(flatComposition)
            }
        }
    }
}