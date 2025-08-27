package ru.hollowhorizon.hc.client.utils

//? if >=1.21 {

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.item.Item.TooltipContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import org.joml.Matrix4f
import org.joml.Matrix4fStack

fun Matrix4fStack.pushPose() = pushMatrix()
fun Matrix4fStack.popPose() = popMatrix()
fun Matrix4fStack.setIdentity() = set(identity())

fun ItemStack.getTooltipLines(player: LocalPlayer?, flags: TooltipFlag) = getTooltipLines(TooltipContext.of(player?.level()), player, flags)

fun VertexConsumer.vertex(pose: Matrix4f, x: Float, y: Float, z: Float) = addVertex(pose, x, y, z)
fun VertexConsumer.uv(x: Float, y: Float) = this.setUv( x, y)
fun VertexConsumer.uv2(x: Int) = this.setLight(x)
fun VertexConsumer.color(r: Float, g: Float, b: Float, a: Float) = setColor(r, g, b, a)
fun VertexConsumer.color(value: Int) = setColor(value)
fun VertexConsumer.endVertex() {}

fun PoseStack.mulPoseMatrix(matrix: Matrix4f) {
    mulPose(matrix)
}

//?}