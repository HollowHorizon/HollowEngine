package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.systems.RenderSystem
import dev.ftb.mods.ftbteams.FTBTeamsAPI
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hollowengine.common.capabilities.StoryTeamCapability
import kotlin.math.sqrt

@Mod.EventBusSubscriber(Dist.CLIENT)
object AimMarkRenderer {
    @SubscribeEvent
    fun renderWorldLast(event: RenderLevelStageEvent) {
        if (event.stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            val team = FTBTeamsAPI.getClientManager().selfTeam
            val stack = event.poseStack

            val marks = team.capability(StoryTeamCapability::class).aimMarks

            marks.forEach { mark ->
                stack.use {
                    RenderSystem.disableDepthTest()
                    RenderSystem.enableBlend()
                    RenderSystem.defaultBlendFunc()
                    val p = mc.gameRenderer.mainCamera.position
                    translate(-p.x, -p.y, -p.z)
                    translate(mark.x, mark.y, mark.z)
                    mulPose(Minecraft.getInstance().entityRenderDispatcher.cameraOrientation())
                    scale(-0.025f, -0.025f, 0.025f)

                    RenderSystem.setShaderTexture(0, mark.icon)

                    val size = 16
                    val pos = size / 2

                    val opacity = Minecraft.getInstance().options.textBackgroundOpacity().get()
                    pushPose()
                    translate(0.0, 0.0, 0.001)
                    Screen.fill(stack, -pos * 2, -pos * 2 + 4, pos * 2, pos * 2 + 8, (opacity * 255).toInt() shl 24)
                    popPose()

                    Screen.blit(stack, -pos, -pos, 0f, 0f, size, size, size, size)
                    Minecraft.getInstance().font.drawScaled(
                        stack,
                        Anchor.CENTER,
                        ("%.2f".format(sqrt(Minecraft.getInstance().player!!.distanceToSqr(mark.x, mark.y, mark.z))) + "м").mcText,
                        0,
                        pos + 9,
                        0xFFFFFF,
                        0.9f
                    )
                    RenderSystem.disableBlend()
                    RenderSystem.enableDepthTest()
                }
            }
        }
    }
}