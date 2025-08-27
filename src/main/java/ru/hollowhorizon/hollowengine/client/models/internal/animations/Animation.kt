/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.client.models.internal.animations

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.scene.TrsTransformF
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.Node
import ru.hollowhorizon.hollowengine.client.models.internal.animations.interpolations.Interpolator

class Animation(
    val name: String,
    private val animationData: Map<Node, AnimationData>,
    val duration: Float = animationData.values.maxOf { it.duration },
) {
    val temp = TrsTransformF()

    fun compute(node: Node, currentTime: Float): TrsTransformF? {
        return animationData[node]?.let {
            val t = it.translation?.compute(currentTime)
            val r = it.rotation?.compute(currentTime)
            val s = it.scale?.compute(currentTime)

            temp.setIdentity()
            t?.let(temp::translate)
            r?.let(temp::rotate)
            s?.let(temp::scale)
            temp
        }
    }

    fun computeWeights(node: Node, currentTime: Float): FloatArray? {
        return animationData[node]?.weights?.compute(currentTime)
    }

    override fun toString() = name

}

fun Vec3f.array(): FloatArray {
    return floatArrayOf(x, y, z)
}

fun QuatF.array(): FloatArray {
    return floatArrayOf(x, y, z, w)
}

fun Vec4f.array(): FloatArray {
    return floatArrayOf(x, y, z, w)
}

enum class AnimationType {
    IDLE, IDLE_SNEAKED, WALK, WALK_SNEAKED, JUMP, HURT,
    RUN, SWIM, FALL, FLY, SIT, SLEEP, SWING, DEATH;

    companion object {
        // @formatter:off
        private val patterns: Map<AnimationType, List<List<String>>> = mapOf(
            IDLE            to listOf(listOf("idle")),
            IDLE_SNEAKED    to listOf(listOf("sneak"), listOf("crouch", "crouth", "idle")),
            WALK            to listOf(listOf("walk"), listOf("move"), listOf("go")),
            WALK_SNEAKED    to listOf(listOf("walk", "sneak"), listOf("crouch", "crouth", "walk")),
            RUN             to listOf(listOf("run"), listOf("dash"), listOf("flee")),
            JUMP            to listOf(listOf("jump"), listOf("hop"), listOf("leap")),
            FALL            to listOf(listOf("fall")),
            FLY             to listOf(listOf("fly"), listOf("glide")),
            SWIM            to listOf(listOf("swim")),
            SIT             to listOf(listOf("sit")),
            SLEEP           to listOf(listOf("sleep"), listOf("rest")),
            HURT            to listOf(listOf("hurt"), listOf("damage")),
            SWING           to listOf(listOf("swing"), listOf("attack"), listOf("use")),
            DEATH           to listOf(listOf("death"), listOf("die"), listOf("dead"))
        )
        // @formatter:on

        @JvmStatic
        fun load(model: AnimatedModel): HashMap<AnimationType, String> {
            val names = model.animations.keys.toMutableList()
            val result = hashMapOf<AnimationType, String>()

            // Утилиты поиска
            fun String.scoreAgainst(keys: List<String>): Int {
                val lower = lowercase()
                // +100 за точное совпадение
                if (lower == keys.joinToString("_")) return 100
                // +50 за startsWith любого ключевого слова
                if (keys.any { lower.startsWith(it) }) return 50
                // +10 за contains всех ключевых слов
                if (keys.all { lower.contains(it) }) return 10
                return 0
            }

            fun List<String>.findBest(keys: List<String>): String? {
                return this
                    .asSequence()
                    .map { it to it.scoreAgainst(keys) }
                    .filter { it.second > 0 }
                    .sortedWith(compareByDescending<Pair<String, Int>> { it.second }
                        .thenByDescending { (name, _) ->
                            // для speed-анимаций можно учесть цифры в имени
                            if (keys.any { it in listOf("run", "walk", "sneak") }) {
                                Regex("""\d+""").find(name)?.value?.toInt() ?: 0
                            } else 0
                        })
                    .map { it.first }
                    .firstOrNull()
            }

            for (type in entries) {
                val keysList = patterns[type] ?: continue
                var found: String? = null
                for (keys in keysList) {
                    found = names.findBest(keys)
                    if (found != null) break
                }
                if (found != null) {
                    result[type] = found
                    names.remove(found)
                }
            }

            return result
        }
    }
}

enum class AnimationState { STARTING, PLAYING, FINISHED }

@Serializable
enum class PlayMode {
    ONCE, //Одиночный запуск анимации
    LOOPED, //После завершения анимация начнётся с начала
    LAST_FRAME, //После завершения анимация застынет на последнем кадре
    REVERSED; //После завершения анимация начнёт проигрываться в обратном порядке


    fun stopOnEnd(): Boolean = this == ONCE
}

class AnimationData(
    val node: Node,
    val translation: Interpolator<Vec3f>?,
    val rotation: Interpolator<QuatF>?,
    val scale: Interpolator<Vec3f>?,
    val weights: Interpolator<FloatArray>?,
) {
    val duration = maxOf(
        translation?.duration ?: 0f,
        rotation?.duration ?: 0f,
        scale?.duration ?: 0f,
        weights?.duration ?: 0f
    )
}

enum class AnimationTarget { TRANSLATION, ROTATION, SCALE, WEIGHTS }