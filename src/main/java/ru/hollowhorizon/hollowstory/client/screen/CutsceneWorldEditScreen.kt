package ru.hollowhorizon.hollowstory.client.screen

import com.mojang.blaze3d.matrix.MatrixStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.WorldRenderer
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.BlockItem
import net.minecraft.util.Direction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.BlockRayTraceResult
import net.minecraft.util.math.RayTraceContext
import net.minecraft.util.math.vector.Quaternion
import net.minecraft.util.math.vector.Vector3f
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.utils.toSTC

class CutsceneWorldEditScreen : HollowScreen("".toSTC()) {
    private var lookPos: BlockPos? = null
    private var currentButton = -1

    init {
        MinecraftForge.EVENT_BUS.register(this)
    }

    override fun render(p_230430_1_: MatrixStack, mouseX: Int, mouseY: Int, p_230430_4_: Float) {
        super.render(p_230430_1_, mouseX, mouseY, p_230430_4_)

        val player = Minecraft.getInstance().player!!

        val blockPos = player.pick(mouseX.toDouble(), mouseY.toDouble()).blockPos

        val state = player.level.getBlockState(blockPos)

        if (!state.isAir) {
            this.lookPos = blockPos
        } else {
            this.lookPos = null
        }

        if (lookPos != null) {
            if (currentButton == 0) {
                player.level.destroyBlock(lookPos!!, false, player)
            } else if (currentButton == 1 && player.mainHandItem.item is BlockItem) {
                player.level.setBlockAndUpdate(
                    lookPos!!,
                    (player.mainHandItem.item as BlockItem).block.defaultBlockState()
                )
            }
        }
    }

    @SubscribeEvent
    fun renderWorld(event: RenderWorldLastEvent) {
        if (lookPos != null) {
            val level = Minecraft.getInstance().level!!
            val shape = level.getBlockState(lookPos!!).getShape(level, lookPos!!)

            val buffer = Minecraft.getInstance().renderBuffers().bufferSource()
            WorldRenderer.renderVoxelShape(
                event.matrixStack,
                buffer.getBuffer(RenderType.lines()),
                shape,
                -lookPos!!.x.toDouble(),
                -lookPos!!.y.toDouble(),
                -lookPos!!.z.toDouble(),
                0.0f,
                0.0f,
                0.0f,
                0.4f
            )
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, ms: Double): Boolean {
        var scroll = ms
        val direction = Minecraft.getInstance().player!!.direction
        val rotVec = Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
        rotVec.mul(Vector3f.YP.rotationDegrees(Minecraft.getInstance().player!!.yHeadRot))
        rotVec.mul(Vector3f.XP.rotationDegrees(Minecraft.getInstance().player!!.xRot))

        val forward = Vector3f(0.0f, 0.0f, 1.0f)
        forward.transform(rotVec)

        if(direction == Direction.WEST || direction == Direction.EAST) scroll *= -1.0

        val offsetX = forward.x() * scroll
        val offsetY = forward.y() * scroll
        val offsetZ = forward.z() * scroll

        val oldPos = Minecraft.getInstance().player!!.position()
        Minecraft.getInstance().player!!.setPos(oldPos.x + offsetX, oldPos.y + offsetY, oldPos.z + offsetZ)

        return super.mouseScrolled(mouseX, mouseY, ms)
    }

    override fun mouseClicked(p_231044_1_: Double, p_231044_3_: Double, button: Int): Boolean {
        currentButton = button
        return super.mouseClicked(p_231044_1_, p_231044_3_, button)
    }

    override fun mouseReleased(p_231048_1_: Double, p_231048_3_: Double, button: Int): Boolean {
        currentButton = -1
        return super.mouseReleased(p_231048_1_, p_231048_3_, button)
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    override fun onClose() {
        super.onClose()
        MinecraftForge.EVENT_BUS.unregister(this)
    }
}

fun PlayerEntity.pick(mouseX: Double, mouseY: Double): BlockRayTraceResult {
    val disctance = 50.0F

    val eyePosition = getEyePosition(0.0F)
    val lookVector = CameraHelper.getMouseBasedViewVector(Minecraft.getInstance(), this.xRot, this.yRot)
    val toVector = eyePosition.add(lookVector.x * disctance, lookVector.y * disctance, lookVector.z * disctance)

    return level.clip(
        RayTraceContext(
            eyePosition,
            toVector,
            RayTraceContext.BlockMode.OUTLINE,
            RayTraceContext.FluidMode.NONE,
            this
        )
    )
}
