package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.Tesselator
import dev.ftb.mods.ftbteams.FTBTeamsAPI
import dev.ftb.mods.ftbteams.data.ClientTeamManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.Position
import net.minecraft.locale.Language
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import ru.hollowhorizon.hc.client.utils.capability
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.use
import ru.hollowhorizon.hollowengine.common.capabilities.StoryTeamCapability
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.distanceToXZ
import kotlin.math.sqrt

@Mod.EventBusSubscriber(Dist.CLIENT)
object AimMarkRenderer {
    @SubscribeEvent
    fun renderWorldLast(event: RenderLevelStageEvent) {
        if (event.stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            val team = ClientTeamManager.INSTANCE?.selfTeam ?: return
            val player = Minecraft.getInstance().player ?: return
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

                    val fullDist = sqrt(player.distanceToSqr(mark.x, mark.y, mark.z))

                    val distance =
                        if (!mark.ignoreY) fullDist
                        else player.distanceToXZ(Vec3(mark.x, mark.y, mark.z))

                    val scale = (0.00390625 * ((fullDist + 4.0) / 3.0)).toFloat() * 2f

                    scale(-scale, -scale, scale)

                    RenderSystem.setShaderTexture(0, mark.icon)

                    val size = 16
                    val pos = size / 2

                    val opacity = Minecraft.getInstance().options.textBackgroundOpacity().get()
                    pushPose()
                    translate(0.0, 0.0, 0.001)
                    Screen.fill(stack, -pos * 2, -pos * 2 + 2, pos * 2, pos * 2 + 8, (opacity * 255).toInt() shl 24)
                    popPose()

                    Screen.blit(stack, -pos - 4, -pos - 4, 0f, 0f, size + 8, size + 8, size + 8, size + 8)

                    use {
                        val source = MultiBufferSource.immediate(Tesselator.getInstance().builder)
                        val text = "%.2f".format(distance) + "м"
                        val font = Minecraft.getInstance().font
                        val s = 26f / font.width(text)
                        translate(
                            0.0,
                            pos + 7.0, 0.0
                        )
                        scale(s, s, 0f)
                        font.drawInBatch(
                            text,
                            -font.width(text) / 2f,
                            0f,
                            0xFFFFFF,
                            false,
                            last().pose(),
                            source,
                            true,
                            0,
                            15728880,
                            Language.getInstance().isDefaultRightToLeft
                        )
                        source.endBatch()
                    }
                    RenderSystem.disableBlend()
                    RenderSystem.enableDepthTest()
                }
            }
        }
    }
}
