package ru.hollowhorizon.hollowengine.client.render.entity

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hc.client.models.gltf.GltfTree
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated
import ru.hollowhorizon.hc.client.render.entity.GLTFEntityRenderer
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hollowengine.client.render.effects.EffectsCapability
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

class NPCRenderer<T>(context: EntityRendererProvider.Context) :
    GLTFEntityRenderer<T>(context) where T : LivingEntity, T : IAnimated {
    override fun drawVisuals(entity: LivingEntity, stack: PoseStack, node: GltfTree.Node, light: Int) {
        super.drawVisuals(entity, stack, node, light)

        val npc = entity as? NPCEntity ?: return

        val capability = npc[EffectsCapability::class]

        if (capability.effects.isNotEmpty()) {
            capability.effects.filter { it.node == node.name }.forEach { _ -> }
            capability.effects.removeIf { it.node == node.name }
        }
    }
}