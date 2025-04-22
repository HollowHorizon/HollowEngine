package ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable

import kotlinx.serialization.KSerializer
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.pluginContext
import ru.hollowhorizon.hollowengine.scripting.Suspendable

val SFUNCTION_PACKAGE = FqName("ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable")

fun sFunctionN(n: Int) = pluginContext.referenceClass(
    ClassId(
        SFUNCTION_PACKAGE,
        Name.identifier("SFunction$n")
    )
)!!

interface SFunction0<out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(): R
    fun restoreState()
    fun updateAsyncs()
    val serializer: KSerializer<*>
}
interface SFunction1<in P1, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1): R
    fun restoreState(p1: P1)
    fun updateAsyncs(p1: P1)
    val serializer: KSerializer<*>
}
interface SFunction2<in P1, in P2, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2): R
    fun restoreState(p1: P1, p2: P2)
    fun updateAsyncs(p1: P1, p2: P2)
    val serializer: KSerializer<*>
}
interface SFunction3<in P1, in P2, in P3, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3): R
    fun restoreState(p1: P1, p2: P2, p3: P3)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3)
    val serializer: KSerializer<*>
}
interface SFunction4<in P1, in P2, in P3, in P4, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4)
    val serializer: KSerializer<*>
}
interface SFunction5<in P1, in P2, in P3, in P4, in P5, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5)
    val serializer: KSerializer<*>
}
interface SFunction6<in P1, in P2, in P3, in P4, in P5, in P6, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6)
    val serializer: KSerializer<*>
}
interface SFunction7<in P1, in P2, in P3, in P4, in P5, in P6, in P7, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7)
    val serializer: KSerializer<*>
}
interface SFunction8<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8)
    val serializer: KSerializer<*>
}
interface SFunction9<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9)
    val serializer: KSerializer<*>
}
interface SFunction10<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10)
    val serializer: KSerializer<*>
}
interface SFunction11<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11)
    val serializer: KSerializer<*>
}
interface SFunction12<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12)
    val serializer: KSerializer<*>
}
interface SFunction13<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13)
    val serializer: KSerializer<*>
}
interface SFunction14<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14)
    val serializer: KSerializer<*>
}
interface SFunction15<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15)
    val serializer: KSerializer<*>
}
interface SFunction16<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16)
    val serializer: KSerializer<*>
}
interface SFunction17<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17)
    val serializer: KSerializer<*>
}
interface SFunction18<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, in P18, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18)
    val serializer: KSerializer<*>
}
interface SFunction19<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, in P18, in P19, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19)
    val serializer: KSerializer<*>
}
interface SFunction20<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, in P18, in P19, in P20, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19, p20: P20): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19, p20: P20)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19, p20: P20)
    val serializer: KSerializer<*>
}
interface SFunction21<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, in P18, in P19, in P20, in P21, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19, p20: P20, p21: P21): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19, p20: P20, p21: P21)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19, p20: P20, p21: P21)
    val serializer: KSerializer<*>
}
interface SFunction22<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, in P18, in P19, in P20, in P21, in P22, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19, p20: P20, p21: P21, p22: P22): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19, p20: P20, p21: P21, p22: P22)
    fun updateAsyncs(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11, p12: P12, p13: P13, p14: P14, p15: P15, p16: P16, p17: P17, p18: P18, p19: P19, p20: P20, p21: P21, p22: P22)
    val serializer: KSerializer<*>
}
