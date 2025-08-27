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

package ru.hollowhorizon.hollowengine.client.models.internal.manager

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.models.internal.controller.*
import ru.hollowhorizon.hollowengine.common.capabilities.CSyncEntityCapabilityPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntity
import ru.hollowhorizon.hollowengine.common.utils.get

interface IAnimated

val IAnimated.manager get() = this[AnimatedEntityCapability::class]

fun AnimatedEntityCapability.play(
    animation: String,
    blendMode: BlendMode = BlendMode.Additive,
    wrapMode: WrapMode = WrapMode.Once,
    priority: Int = 0,
    mask: Mask = Mask.full(),
    speed: String = "1f",
    transitionTime: Float = 0.25f,
    referencePose: String = "",
) {
    if (wrapMode == WrapMode.Once) {
        AddOnceLayerPacket(
            animation,
            (provider as Entity).id,
            priority,
            mask,
            blendMode,
            speed,
            transitionTime,
            referencePose
        ).sendTrackingEntity(provider as Entity)
        return
    }

    controller = animationController {
        controller.layers.forEach(::layer)
        layer(
            "__${animation}_layer__",
            blendMode = blendMode,
            priority = priority,
            mask = mask,
            referencePose = referencePose
        ) {
            stateMachine {
                state(animation + "_state") {
                    clip(animation, wrap = wrapMode, speed = speed)
                }
                transition("*", animation + "_state") {
                    condition("true")
                    duration(transitionTime)
                }
            }
        }
    }
}

fun AnimatedEntityCapability.stop(
    animation: String, transitionTime: Float = 0.25f,
) {
    val layer = controller.layers.find { it.name == "__${animation}_layer__" }?.also {
        it.stateMachine.transitions.add(
            TransitionBuilder("${animation}_state", "__end__").apply {
                condition("true"); duration(transitionTime); exitTime(false)
            }.build()
        )
    } ?: run {
        StopOnceLayerPacket(animation, (provider as Entity).id, transitionTime).sendTrackingEntity(provider as Entity)
        return
    }
    CSyncEntityCapabilityPacket(
        (provider as Entity).id,
        AnimatedEntityCapability::class.java.name,
        serializeNBT()
    ).sendTrackingEntity((provider as Entity))
    controller.layers.remove(layer) // Удалим без обновления
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class AddOnceLayerPacket(
    private val animation: String,
    private val entityId: Int,
    private val priority: Int,
    private val mask: Mask,
    private val blendMode: BlendMode,
    private val speed: String,
    private val transition: Float,
    private val referencePose: String,
) : HollowPacket {
    override fun handle(player: Player) {
        val level = Minecraft.getInstance().level ?: return
        val entity = level.getEntity(entityId) ?: return
        val controller = entity[AnimatedEntityCapability::class].controller
        val layers = controller.layers

        layers.removeIf { it.name == animation }
        layers.add(Layer("__${animation}_layer__", priority, 1f, mask, blendMode, StateMachineBuilder().apply {
            val animState = state(animation + "_state") {
                clip(animation, WrapMode.Once, speed)
            }

            transition("*", animState.name) {
                condition("true")
                duration(transition)
            }

            exit(animState.name, transition)
        }.build(), referencePose))
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class StopOnceLayerPacket(
    private val animation: String,
    private val entityId: Int,
    private val transition: Float,
): HollowPacket {
    override fun handle(player: Player) {
        val level = Minecraft.getInstance().level ?: return
        val entity = level.getEntity(entityId) ?: return
        val controller = entity[AnimatedEntityCapability::class].controller
        controller.layers.find { it.name == "__${animation}_layer__" }?.also {
            it.stateMachine.transitions.add(
                TransitionBuilder("${animation}_state", "__end__").apply {
                    condition("true"); duration(transition); exitTime(false)
                }.build()
            )
        }
    }

}