package tools

import org.openrndr.application
import org.openrndr.svg.loadSVG

fun main() {
    application {
        val svg = loadSVG("grayscale-grid.svg")
        svg.findShapes().forEach {
            println(it.effectiveStrokeWeight)
            println(it.strokeWeight)
        }
        program {

        }
    }
}