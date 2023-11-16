package ru.hollowhorizon.hollowengine.client.render.entity

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hc.client.models.gltf.GltfTree
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated
import ru.hollowhorizon.hc.client.render.entity.GLTFEntityRenderer

class NPCRenderer<T>(context: EntityRendererProvider.Context): GLTFEntityRenderer<T>(context) where T: LivingEntity, T: IAnimated {
    override fun drawVisuals(entity: LivingEntity, stack: PoseStack, node: GltfTree.Node, light: Int) {
        super.drawVisuals(entity, stack, node, light)
    }
}