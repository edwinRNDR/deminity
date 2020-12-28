package tools

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadImage
import org.openrndr.shape.drawComposition
import org.openrndr.svg.saveToFile
import java.io.File
import java.lang.Math.pow

fun main() {
    application {
        configure {
            width = 1280
            height = 720
        }
        program {

            val sourceImage = loadImage("tool-data/photo-06.png")
            val shad = sourceImage.shadow
            shad.download()

            val blockSize = 48
            val comp = drawComposition {

                translate((1280-sourceImage.width)/2.0, 0.0)
                for (y in 0 until sourceImage.height step blockSize) {
                    for (x in 0 until sourceImage.width step blockSize) {

                        var l = 0.0
                        var w = 0
                        for (v in 0 until blockSize) {
                            for (u in 0 until blockSize) {
                                if (x+u < sourceImage.width -1 && y+v <sourceImage.height - 1) {
                                    l += shad[x + u, y + v].luminance
                                    w += 1
                                }
                            }
                        }
                        l /= w
                        //l /= (blockSize * blockSize)

                        strokeWeight = (pow(l,1.1) * blockSize)
                        stroke = ColorRGBa.WHITE
                        //strokeWeight = 10.0
                        if (l * blockSize > 1.0) {
                            //lineSegment(x * 1.0, y + blockSize / 2.0, x * 1.0 + blockSize, y + blockSize / 2.0)
                            lineSegment(x * 1.0, y + blockSize / 2.0, x * 1.0 + blockSize, y + blockSize / 2.0)
                        }
                    }
                }
            }
            comp.saveToFile(File("${String.format("%03d",blockSize)}.svg"))

            extend {
                drawer.clear(ColorRGBa.BLACK)
                drawer.composition(comp)
            }
        }
    }
}