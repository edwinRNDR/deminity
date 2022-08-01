import demo.propertyListEvaluator1
import demo.propertyListEvaluator2
import demo.propertyListEvaluator3
import demo.propertyListEvaluatorMix1
import demo.normalized
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPropertyEvaluators {
    @Test
    fun testEval1() {
        val list = listOf("5 + 2")
        val f = propertyListEvaluator1("henk", list)
        assertEquals(7.0, f(0.0))
    }

    @Test
    fun testEval1Mix() {
        val list = listOf(0.0, 1.0)
        val f = propertyListEvaluatorMix1("henk", list)
        assertEquals(0.5, f(0.5))
    }

    @Test
    fun testEval1MixFunctions() {
        val list = listOf("x0", "x0")
        val f = propertyListEvaluatorMix1("henk", list)

        for (i in 0..10) {
            println(f(i/10.0))
        }

        assertEquals(0.5, f(0.5))
    }

    @Test
    fun testEval1MixNorm() {
        val list = listOf(0.0, 1.0, 3.0)
        val f = propertyListEvaluatorMix1("henk", list, ::normalized)
        assertEquals(1.0, f(0.5))
    }

    @Test
    fun testEval1x0() {
        val list = listOf("x0")
        val f = propertyListEvaluator1("henk", list)
        assertEquals(3.0, f(3.0))
    }

    @Test
    fun testEval2x0x1() {
        val list = listOf("x0 + x1")
        val f = propertyListEvaluator2("henk", list)
        assertEquals(7.0, f(3.0, 4.0))
    }


    @Test
    fun testEval3x0x1x2() {
        val list = listOf("x0 + x1 + x2")
        val f = propertyListEvaluator3("henk", list)
        assertEquals(12.0, f(3.0, 4.0, 5.0))
    }

    @Test
    fun testEval2MultiList() {
        val list = listOf(3.0, emptyList<Any>())
        val f = propertyListEvaluator2("henk", list)
        assertEquals(3.0, f(0.0, 0.0))
        assertEquals(-0.0, f(1.0, 0.0))
    }

    @Test
    fun testEval2MultiList2() {
        val list = listOf(3.0, listOf(4.0))
        val f = propertyListEvaluator2("henk", list)
        assertEquals(3.0, f(-1.0, 0.0))
        assertEquals(3.0, f(0.0, 0.0))
        assertEquals(4.0, f(1.0, 0.0))
        assertEquals(4.0, f(1.0, 1.0))
        assertEquals(4.0, f(3.0, 0.0))
    }

}