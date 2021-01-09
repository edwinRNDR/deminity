package demo

interface Cascadable<T> {
    infix fun over(lower: T): T
}


infix fun <T : Any> T?.over(lower: T?): T? = this ?: lower