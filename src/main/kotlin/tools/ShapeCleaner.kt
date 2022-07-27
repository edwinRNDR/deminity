package tools

import org.openrndr.application
import org.openrndr.shape.Rectangle
import org.openrndr.shape.ShapeContour
import polyfill.drawComposition
import org.openrndr.shape.shape
import org.openrndr.svg.loadSVG
import org.openrndr.svg.saveToFile
import java.io.File

fun main() {
    application {
        program {
            val source = loadSVG("tool-data/illustration-05.svg")
            val cleaned = drawComposition(documentBounds = Rectangle(0.0, 0.0, 1280.0, 720.0)) {
                for (shape in source.findShapes()) {
                    val fixedShape = shape.flatten().shape.contours.map {
                        ShapeContour(it.segments.filter {
                            it.length > 0.0
                        }, it.closed)
                    }.shape
                    shape(fixedShape)
                }
            }
            cleaned.saveToFile(File("cleaned.svg"))
        }
    }
}