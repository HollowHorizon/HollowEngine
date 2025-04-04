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

/** A function that takes 0 arguments. */
interface SFunction0<out R> : Function<R> {
    /** Invokes the function. */
    @Suspendable
    operator fun invoke(): R
    fun restoreState()
    val serializer: KSerializer<*>
}

/** A function that takes 1 argument. */
interface SFunction1<in P1, out R> : Function<R> {
    /** Invokes the function with the specified argument. */
    @Suspendable
    operator fun invoke(p1: P1): R
    fun restoreState(p1: P1)
    val serializer: KSerializer<*>
}

/** A function that takes 2 arguments. */
interface SFunction2<in P1, in P2, out R> : Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2): R
    fun restoreState(p1: P1, p2: P2)
    val serializer: KSerializer<*>
}

/** A function that takes 3 arguments. */
interface SFunction3<in P1, in P2, in P3, out R> : Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3): R
    fun restoreState(p1: P1, p2: P2, p3: P3)
    val serializer: KSerializer<*>
}

/** A function that takes 4 arguments. */
interface SFunction4<in P1, in P2, in P3, in P4, out R> : Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4)
    val serializer: KSerializer<*>
}

/** A function that takes 5 arguments. */
interface SFunction5<in P1, in P2, in P3, in P4, in P5, out R> : Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5)
    val serializer: KSerializer<*>
}

/** A function that takes 6 arguments. */
interface SFunction6<in P1, in P2, in P3, in P4, in P5, in P6, out R> : Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6)
    val serializer: KSerializer<*>
}

/** A function that takes 7 arguments. */
interface SFunction7<in P1, in P2, in P3, in P4, in P5, in P6, in P7, out R> : Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7)
    val serializer: KSerializer<*>
}

/** A function that takes 8 arguments. */
interface SFunction8<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, out R> : Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8)
    val serializer: KSerializer<*>
}

/** A function that takes 9 arguments. */
interface SFunction9<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, out R> : Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9)
    val serializer: KSerializer<*>
}

/** A function that takes 10 arguments. */
interface SFunction10<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, out R> : Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10)
    val serializer: KSerializer<*>
}

/** A function that takes 11 arguments. */
interface SFunction11<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11): R
    fun restoreState(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9, p10: P10, p11: P11)
    val serializer: KSerializer<*>
}

/** A function that takes 12 arguments. */
interface SFunction12<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
    )
    val serializer: KSerializer<*>
}

/** A function that takes 13 arguments. */
interface SFunction13<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
    )
    val serializer: KSerializer<*>
}

/** A function that takes 14 arguments. */
interface SFunction14<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
    )
    val serializer: KSerializer<*>
}

/** A function that takes 15 arguments. */
interface SFunction15<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
    )
    val serializer: KSerializer<*>
}

/** A function that takes 16 arguments. */
interface SFunction16<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
    )
    val serializer: KSerializer<*>
}

/** A function that takes 17 arguments. */
interface SFunction17<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
    )
    val serializer: KSerializer<*>
}

/** A function that takes 18 arguments. */
interface SFunction18<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, in P18, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
        p18: P18,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
        p18: P18,
    )
    val serializer: KSerializer<*>
}

/** A function that takes 19 arguments. */
interface SFunction19<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, in P18, in P19, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
        p18: P18,
        p19: P19,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
        p18: P18,
        p19: P19,
    )
    val serializer: KSerializer<*>
}

/** A function that takes 20 arguments. */
interface SFunction20<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, in P18, in P19, in P20, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
        p18: P18,
        p19: P19,
        p20: P20,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
        p18: P18,
        p19: P19,
        p20: P20,
    )
    val serializer: KSerializer<*>
}

/** A function that takes 21 arguments. */
interface SFunction21<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, in P18, in P19, in P20, in P21, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
        p18: P18,
        p19: P19,
        p20: P20,
        p21: P21,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
        p18: P18,
        p19: P19,
        p20: P20,
        p21: P21,
    )
    val serializer: KSerializer<*>
}

/** A function that takes 22 arguments. */
interface SFunction22<in P1, in P2, in P3, in P4, in P5, in P6, in P7, in P8, in P9, in P10, in P11, in P12, in P13, in P14, in P15, in P16, in P17, in P18, in P19, in P20, in P21, in P22, out R> :
    Function<R> {
    /** Invokes the function with the specified arguments. */
    @Suspendable
    operator fun invoke(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
        p18: P18,
        p19: P19,
        p20: P20,
        p21: P21,
        p22: P22,
    ): R

    fun restoreState(
        p1: P1,
        p2: P2,
        p3: P3,
        p4: P4,
        p5: P5,
        p6: P6,
        p7: P7,
        p8: P8,
        p9: P9,
        p10: P10,
        p11: P11,
        p12: P12,
        p13: P13,
        p14: P14,
        p15: P15,
        p16: P16,
        p17: P17,
        p18: P18,
        p19: P19,
        p20: P20,
        p21: P21,
        p22: P22,
    )
    val serializer: KSerializer<*>
}
