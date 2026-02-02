package ru.hollowhorizon.hollowengine.common.geary.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.geary.api.entity

@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:model")
@EditorIcon("hollowengine:textures/gui/icons/eye.svg")
data class Model(
    @EditorName("Модель")
    val model: String = "hollowengine:models/entity/player_model.gltf",
    @EditorRange(min = 0f, max = 100f)
    val scale: Float = 1f,
) {
    val attachment by lazy {
        ModelAttachment(model)
    }
}

@SubscribeEvent
fun onRender(event: RenderEntityEvent.Pre) {
    val fleks = event.entity.entity

    val model = fleks.get<Model>() ?: return

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

        model.attachment.pipeline.render(RenderContext(poseStack, buffer, packedLight, overlay))
        poseStack.popPose()

        isCanceled = true

    }
}

@Registerable
@Serializable
@SerialName("hollowengine:transform")
@EditorIcon("hollowengine:textures/gui/icons/world.svg")
data class TransformComponent(
    @EditorName("Позиция X")
    @EditorRange(-1000f, 1000f)
    val x: Float = 0f,

    @EditorName("Позиция Y")
    @EditorRange(-100f, 300f)
    val y: Float = 0f,

    @EditorName("Позиция Z")
    @EditorRange(-1000f, 1000f)
    val z: Float = 0f,

    @EditorName("Поворот (Yaw)")
    @EditorRange(0f, 360f)
    val yaw: Float = 0f,

    @EditorName("Наклон (Pitch)")
    @EditorRange(-90f, 90f)
    val pitch: Float = 0f,

    @EditorName("Масштаб")
    @EditorRange(0.1f, 10f)
    @EditorIcon("hollowengine:textures/gui/icons/maximize.svg")
    val scale: Float = 1f,
)

@Registerable
@Serializable
@SerialName("hollowengine:interaction")
@EditorIcon("hollowengine:textures/gui/icons/interaction.svg")
data class InteractionComponent(
    @EditorHidden // Это поле не должно быть в редакторе
    val interactionId: String = "uuid_default",

    @EditorName("Активно")
    val isInteractable: Boolean = true,

    @EditorName("Радиус действия")
    @EditorRange(1f, 64f)
    val radius: Float = 3.0f,

    @EditorName("Текст подсказки")
    @EditorIcon("hollowengine:textures/gui/icons/dialogue.png")
    val hintText: String = "Нажмите Е чтобы говорить",

    @EditorName("Скрипт события")
    @EditorIcon("hollowengine:textures/gui/icons/file_kts.svg")
    val scriptPath: String = "scripts/npc/dialogue_start.kts",
)

@Registerable
@Serializable
@SerialName("hollowengine:advanced_model")
@EditorIcon("hollowengine:textures/gui/icons/folder_npcs.svg")
data class AdvancedModelComponent(
    @EditorName("Путь к модели")
    val modelPath: String = "models/entity/custom_npc.gltf",

    @EditorName("Текстура скина")
    @EditorIcon("hollowengine:textures/gui/icons/file_image.svg")
    val texturePath: String = "textures/entity/skin.png",

    @EditorName("Прозрачность")
    @EditorRange(0f, 1f)
    val alpha: Float = 1.0f,

    @EditorName("Светящийся")
    @EditorIcon("hollowengine:textures/gui/icons/eye.svg")
    val glow: Boolean = false,

    @EditorName("Анимация покоя")
    @EditorIcon("hollowengine:textures/gui/icons/pose_editor.png")
    val idleAnimation: String = "idle_loop",
)