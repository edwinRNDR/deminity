package demo

/** Cascadable interface */
interface Cascadable<T> {
    /** Return a copy with this [Cascadable]'s properties laid over [lower]'s properties */
    infix fun over(lower: T): T
}

/** cascading helper function */
infix fun <T : Any> T?.over(lower: T?): T? = this ?: lower