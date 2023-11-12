package ru.hollowhorizon.hollowengine.client.render.effects

import com.lowdragmc.lowdraglib.utils.Vector3
import com.lowdragmc.photon.client.emitter.IParticleEmitter
import com.lowdragmc.photon.client.fx.FXEffect
import com.lowdragmc.photon.client.fx.FXHelper
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector4f
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.models.gltf.GltfTree

class NpcEffect(location: ResourceLocation, val node: String) : FXEffect(
    FXHelper.getFX(location),
    Minecraft.getInstance().level
) {
    val pos = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)

    override fun updateEmitter(emitter: IParticleEmitter): Boolean {
        emitter.updatePos(
            Vector3(
                pos.x() + xOffset,
                pos.y() + yOffset,
                pos.z() + zOffset
            )
        )
        return false
    }

    override fun start() {
        this.emitters.clear()
        emitters.addAll(fx.generateEmitters())
        emitters.filter { !it.isSubEmitter }.forEach { emitter ->
            emitter.reset()
            emitter.self().delay = delay
            emitter.emmitToLevel(
                this,
                level, pos.x().toDouble(), pos.y().toDouble(), pos.z().toDouble(), xRotation, yRotation, zRotation
            )
        }
    }
}