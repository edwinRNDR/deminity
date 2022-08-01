package demo

import org.openrndr.extra.keyframer.evaluateExpression
import org.openrndr.extra.keyframer.Function1
import org.openrndr.extra.keyframer.Function2
import org.openrndr.extra.keyframer.Function3
import org.openrndr.math.mix

typealias XMapper = (x: Double, list: List<*>) -> Double

sealed interface XMapperI {

}
fun bla (x: Double, list:List<*>) : Double {
    return 0.0
}

fun clamped(x: Double, list: List<*>): Double = x.coerceIn(0.0, list.lastIndex.toDouble())

fun normalized(x: Double, list: List<*>): Double = (x * list.lastIndex).coerceIn(0.0, list.lastIndex.toDouble())

private fun evalProperty(
    name: String,
    properties: List<*>,
    xs: List<Double>,
    depth: Int,
    xMapper: XMapper,
): Double {
    if (properties.isEmpty()) {
        return -0.0
    }

    val x = xs.getOrNull(depth) ?: 0.0
    val xm = xMapper(x, properties).toInt()

    val property = properties[xm]

    return when (val y = property) {
        is Double -> y
        is Int -> y.toDouble()
        is List<*> -> evalProperty(name, y, xs, depth + 1, xMapper)
        is String -> {
            val variables = xs.mapIndexed { index, it -> Pair("x$index", it) }.toMap()
            evaluateExpression(y, variables)
                ?: error("could not evaluate expression '$y' at ${name}(${xs.joinToString(", ")}/$depth)")
        }
        else -> error("could not evaluate expression '$y' at ${name}(${xs.joinToString(", ")}/$depth)")
    }
}


fun propertyListEvaluator1(name: String, properties: List<Any>, xMapper: XMapper = ::clamped): Function1 =
    { x0: Double ->
        evalProperty(name, properties, listOf(x0), 0, xMapper)
    }

fun propertyListEvaluatorMix1(name: String, properties: List<Any>, xMapper: XMapper = ::clamped): Function1 =
    { x0: Double ->
        val x0m = xMapper(x0, properties)
        val f = x0m - x0m.toInt()
        val u0: Double
        val u1: Double

        when(xMapper) {
            ::clamped -> {
                u0 = x0.toInt().toDouble()
                u1 = u0 + 1.0
            }
            ::normalized -> {
                val s = properties.lastIndex
                u0 = (x0 * s).toInt().toDouble() / s
                u1 = u0 + 1.0 / s
            }
            else -> error("unsupported mapper")
        }
        val y0 = evalProperty(name, properties, listOf(u0), 0, xMapper)
        val y1 = evalProperty(name, properties, listOf(u1), 0, xMapper)
        mix(y0, y1, f)
    }

fun propertyListEvaluator2(name: String, properties: List<Any>, xMapper: XMapper = ::clamped): Function2 =
    { x0: Double, x1: Double ->
        evalProperty(name, properties, listOf(x0, x1), 0, xMapper)
    }

fun propertyListEvaluator3(name: String, properties: List<Any>, xMapper: XMapper = ::clamped): Function3 =
    { x0: Double, x1: Double, x2: Double ->
        evalProperty(name, properties, listOf(x0, x1, x2), 0, xMapper)
    }
