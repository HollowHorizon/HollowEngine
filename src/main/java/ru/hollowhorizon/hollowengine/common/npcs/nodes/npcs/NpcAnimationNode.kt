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

package ru.hollowhorizon.hollowengine.common.npcs.nodes.npcs

import imgui.ImGui
import imgui.type.ImInt
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.gltf.manager.GltfManager
import ru.hollowhorizon.hc.client.models.gltf.manager.LayerMode
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.packets.StartAnimationPacket
import ru.hollowhorizon.hc.common.network.packets.StopAnimationPacket
import ru.hollowhorizon.hollowengine.client.gui.comboNode
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import ru.hollowhorizon.hollowengine.common.npcs.nodes.base.OperationNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.inPin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.NpcPin

class NpcStartAnimationNode: NPCOperationNode() {
    private val layers = arrayOf("Добавочный", "Перезапись")
    private val playModes = arrayOf("Одиночный", "Цикл", "Посл. кадр", "Обратный")
    private val animation = ImInt()
    private var anim = ""
    private val layer = ImInt()
    private val playMode = ImInt()
    private val speed = FloatArray(1) { 1.0f }
    private var duration = 0
    private var ticks = -1

    override fun tick(graph: ScriptGraph) {
        val serverLayers = npc[AnimatedEntityCapability::class].layers

        if (serverLayers.any { it.animation == anim }) {
            if(ticks == -1) ticks = duration
            if(ticks-- == 0) {
                ticks = -1
                complete(graph)
                if(PlayMode.entries[playMode.get()] == PlayMode.ONCE) serverLayers.removeIfNoUpdate { it.animation == anim}
            }
            return
        }

        StartAnimationPacket(
            npc.id, anim, LayerMode.entries[layer.get()], PlayMode.entries[playMode.get()], speed[0]
        ).send(PacketDistributor.TRACKING_ENTITY.with { npc })

        serverLayers.addNoUpdate(
            AnimationLayer(anim, LayerMode.entries[layer.get()], PlayMode.entries[playMode.get()], speed[0])
        )
    }

    override fun draw(graph: ScriptGraph) {
        val animations =
            GltfManager.getOrCreate(graph.npc[AnimatedEntityCapability::class].model.rl).modelTree.animations
                .map { it.name ?: "Unnamed" }.toTypedArray()

        if (anim.isNotEmpty() && anim != animations[animation.get()]) animation.set(animations.indexOf(anim))

        ImGui.pushItemWidth(350f)
        if (comboNode("Анимация", animations, animation)) {
            anim = animations[animation.get()]
            duration = ((GltfManager.getOrCreate(graph.npc[AnimatedEntityCapability::class].model.rl).animationPlayer.nameToAnimationMap[anim]?.maxTime ?: 0f) * 20).toInt()
        }
        comboNode("Слой", layers, layer)
        comboNode("Режим", playModes, playMode)
        ImGui.dragFloat("Скорость", speed, 0.1f, 0.1f, 10f, "%.2f")
        ImGui.popItemWidth()
    }

    override fun serialize(tag: CompoundTag) {
        super.serialize(tag)
        tag.putString("animation", anim)
        tag.putInt("layer", layer.get())
        tag.putInt("playMode", playMode.get())
        tag.putFloat("speed", speed[0])
        tag.putInt("duration", duration)
    }

    override fun deserialize(tag: CompoundTag) {
        super.deserialize(tag)
        anim = tag.getString("animation")
        layer.set(tag.getInt("layer"))
        playMode.set(tag.getInt("playMode"))
        speed[0] = tag.getFloat("speed")
        duration = tag.getInt("duration")
    }
}

class NpcStopAnimationNode: NPCOperationNode() {
    private val animation = ImInt()
    private var anim = ""

    override fun tick(graph: ScriptGraph) {
        npc[AnimatedEntityCapability::class].layers.removeIfNoUpdate { it.animation == anim }
        StopAnimationPacket(npc.id, anim).send(PacketDistributor.TRACKING_ENTITY.with { npc })
        complete(graph)
    }

    override fun draw(graph: ScriptGraph) {
        val animations =
            GltfManager.getOrCreate(graph.npc[AnimatedEntityCapability::class].model.rl).modelTree.animations
                .map { it.name ?: "Unnamed" }.toTypedArray()

        if (anim.isNotEmpty() && anim != animations[animation.get()]) animation.set(animations.indexOf(anim))

        comboNode("Анимация", animations, animation)
    }

    override fun serialize(tag: CompoundTag) {
        super.serialize(tag)
        tag.putString("animation", anim)
    }

    override fun deserialize(tag: CompoundTag) {
        super.deserialize(tag)
        anim = tag.getString("animation")
    }
}