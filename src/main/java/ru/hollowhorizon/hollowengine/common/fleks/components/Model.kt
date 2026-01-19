package ru.hollowhorizon.hollowengine.common.fleks.components

import com.github.quillraven.fleks.ComponentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.fleks.MutableSyncedComponent
import ru.hollowhorizon.hollowengine.common.fleks.fleks
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation

@Serializable
@SerialName("hollowengine:model")
data class Model(private var model: @Serializable(ForResourceLocation::class) ResourceLocation) :
    MutableSyncedComponent<Model>() {

    fun setModel(model: String) {
        this.model = ResourceLocation(model)
        markDirty()
    }

    fun getModel(): ResourceLocation = model

    val attachment by lazy {
        ModelAttachment(model.toString())
    }

    override fun type() = Model

    companion object : ComponentType<Model>()
}

@SubscribeEvent
fun onRender(event: RenderEntityEvent.Pre) {
    val fleks = event.entity.fleks
    event.entity.level().fleks.apply {
        if (fleks hasNo Model) return

        with(event) {
            poseStack.pushPose()

            var overlay = OverlayTexture.NO_OVERLAY
            if (this.entity is LivingEntity) {
                poseStack.mulPose(
                    Quaternionf().rotateY(
                        -Mth.rotLerp(
                            partialTicks,
                            entity.yBodyRotO,
                            entity.yBodyRot
                        ) * Mth.DEG_TO_RAD
                    )
                )
                overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f)
            }

            fleks[Model].attachment.pipeline.render(RenderContext(poseStack, buffer, packedLight, overlay))
            poseStack.popPose()

            isCanceled = true

        }
    }
}