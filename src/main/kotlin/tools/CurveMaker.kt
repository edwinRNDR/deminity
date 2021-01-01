package tools

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.shapes.bezierPatch
import org.openrndr.shape.Rectangle
import org.openrndr.shape.contour
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
            val dist = 64.0

            val sx = (1280 - 720.0) / 2.0
            val ex = 1280.0 - sx

            val s = contour {
                moveTo(sx, 0.0)
                curveTo(640.0, -dist, ex, 0.0)
                curveTo(ex + dist, 360.0, ex, 720.0)
                curveTo(640.0, 720.0 + dist, sx, 720.0)
                curveTo(sx - dist, 360.0, sx, 0.0)
                close()
            }

            val bp = bezierPatch(s)
            val composition = drawComposition(documentBounds = Rectangle(0.0, 0.0, 1280.0, 720.0)) {
                for (i in 0 until lineCount) {
                    contour(bp.horizontal((i + 0.5) / lineCount))
                }
            }
            composition.saveToFile(File("curves-$lineCount.svg"))
            val composition2 = drawComposition(documentBounds = Rectangle(0.0, 0.0, 1280.0, 720.0)) {
                contour(s)
            }
            composition2.saveToFile(File("curves-outline.svg"))
            extend {
                drawer.clear(ColorRGBa.PINK)
                drawer.composition(composition)
            }
        }
    }
}