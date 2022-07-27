package polyfill

import org.openrndr.shape.*

fun drawComposition(
    documentBounds: Rectangle = Rectangle(0.0, 0.0, 1280.0, 720.0),
    composition: Composition? = null,
    cursor: GroupNode? = composition?.root as? GroupNode,
    drawFunction: CompositionDrawer.() -> Unit
): Composition = drawComposition(
    CompositionDimensions(
        documentBounds.x.pixels,
        documentBounds.y.pixels,
        documentBounds.width.pixels,
        documentBounds.height.pixels
    ), composition, cursor, drawFunction
)
