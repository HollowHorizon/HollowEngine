package ru.hollowhorizon.hollowengine.common.utils.expressions

import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * The `math.` namespace of Molang, for any dialect that wants it.
 */
fun <C> DeclarationsBuilder<C>.mathNamespace(): ExprType.Struct = struct<Any?>("math") {
    float("pi") { Mth.PI }

    function("abs") { a -> abs(a) }
    function("sin") { a -> sin(a * Mth.DEG_TO_RAD) }
    function("cos") { a -> cos(a * Mth.DEG_TO_RAD) }
    function("tan") { a -> tan(a * Mth.DEG_TO_RAD) }
    function("asin") { a -> asin(a) * Mth.RAD_TO_DEG }
    function("acos") { a -> acos(a) * Mth.RAD_TO_DEG }
    function("atan") { a -> atan(a) * Mth.RAD_TO_DEG }
    function2("atan2") { y, x -> atan2(y, x) * Mth.RAD_TO_DEG }
    function("sqrt") { a -> sqrt(a) }
    function("exp") { a -> exp(a) }
    function("ln") { a -> ln(a) }
    function("floor") { a -> floor(a) }
    function("ceil") { a -> ceil(a) }
    function("round") { a -> a.roundToInt().toFloat() }
    function("trunc") { a -> a.toInt().toFloat() }
    function2("pow") { base, power -> base.pow(power) }
    function2("max") { a, b -> max(a, b) }
    function2("min") { a, b -> min(a, b) }
    function2("mod") { a, b -> a % b }
    function("random") { a -> Random.nextFloat() * a }
    function2("random") { from, to -> from + Random.nextFloat() * (to - from) }
    function3("clamp") { value, low, high -> value.coerceIn(low, high) }
    function3("lerp") { from, to, t -> from + (to - from) * t.coerceIn(0f, 1f) }
}
