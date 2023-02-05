package ru.hollowhorizon.hollowstory.client

import net.minecraft.util.math.vector.Matrix4f
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import ru.hollowhorizon.hollowstory.HollowStory

object ClientEvents {
    val PROJ_MAT = Matrix4f().apply { setIdentity() }
    val VIEW_MAT = Matrix4f().apply { setIdentity() }

    @JvmStatic
    fun renderLast(event: RenderWorldLastEvent) {
        PROJ_MAT.set(event.projectionMatrix)
        VIEW_MAT.set(event.matrixStack.last().pose())
    }
}