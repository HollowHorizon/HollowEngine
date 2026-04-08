package ru.hollowhorizon.hollowengine.common.geary.components

import de.fabmax.kool.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable
import ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationController
import ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationSystem
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScopeOrNull
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.generated.Assets

@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:model")
@EditorIcon("hollowengine:textures/gui/icons/eye.svg")
data class Model(
    @EditorName("Модель")
    val model: String = "hollowengine:models/entity/player_model.gltf",
    @EditorName("Контроллер анимаций")
    val controllerScript: String = "player_model.animation-controller.kts",
    @EditorRange(min = 0f, max = 100f)
    val scale: Float = 1f,
    @EditorName("Включить анимации")
    val enableAnimations: Boolean = true,
) {

    val attachment by lazy {
        try {
            ModelAttachment(model)
        } catch (_: Exception) {
            ModelAttachment(Assets.Hollowengine.Models.ERROR.toString())
        }
    }

    val animationSystem: AnimationSystem? by lazy {
        if (enableAnimations) {
            try {
                AnimationSystem(attachment)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    @Transient
    private var controllerCache: AnimationController? = null

    @Transient
    private var controllerUpdateJob: Job? = null

    suspend fun getOrCreateController(): AnimationController? {
        if (controllerScript.isBlank()) return null
        controllerCache?.let { return it }

        val system = animationSystem ?: return null
        if (!ru.hollowhorizon.hollowengine.HollowEngine.compilerLoader.isLoaded) return null

        val file = DirectoryManager.HOLLOW_ENGINE.resolve("scripts").resolve(controllerScript).toFile()
        if (!file.exists()) return null

        val instance = withContext(Dispatchers.IO) {
            val compiled = ScriptingEnvironment.INSTANCE.compiler.compile(file).getOrNull() ?: return@withContext null
            compiled.base.execute<AnimationController>(system).getOrNull()
                ?: compiled.base.execute<AnimationController>().getOrNull()
                ?: return@withContext null
        }
        controllerCache = instance
        return instance
    }

    fun requestControllerUpdate(entity: LivingEntity, dt: Float) {
        if (controllerUpdateJob?.isActive == true) return
        val scope = Minecraft.getInstance().coroutineScopeOrNull ?: return

        controllerUpdateJob = scope.launch {
            getOrCreateController()?.update(entity, dt)
        }
    }
}

@SubscribeEvent
fun onRender(event: RenderEntityEvent.Pre) {
    val ecsEntity = event.entity.entity

    val model = ecsEntity.get<Model>() ?: return
    val transform = ecsEntity.get<TransformComponent>() ?: TransformComponent()

    model.animationSystem?.let { animationSystem ->
        if (event.entity is LivingEntity) {
            model.requestControllerUpdate(event.entity, Time.deltaT)
            animationSystem.update(Time.deltaT)
        }
    }

    with(event) {
        poseStack.pushPose()
        poseStack.translate(
            transform.translation.x.toDouble(),
            transform.translation.y.toDouble(),
            transform.translation.z.toDouble(),
        )

        var overlay = OverlayTexture.NO_OVERLAY
        if (this.entity is LivingEntity) {
            poseStack.mulPose(
                Quaternionf().rotateY(
                    -Mth.rotLerp(
                        partialTicks,
                        entity.yBodyRotO,
                        entity.yBodyRot,
                    ) * Mth.DEG_TO_RAD,
                )
            )
            overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f)
        }

        poseStack.mulPose(
            Quaternionf(
                transform.rotation.x,
                transform.rotation.y,
                transform.rotation.z,
                transform.rotation.w,
            )
        )
        poseStack.scale(
            model.scale * transform.scale.x,
            model.scale * transform.scale.y,
            model.scale * transform.scale.z,
        )
        model.attachment.pipeline.render(RenderContext(poseStack, buffer, packedLight, overlay, allowInstancing = true))
        poseStack.popPose()

        isCanceled = true
    }
}

@Registerable
@Serializable
@SerialName("hollowengine:interaction")
@EditorIcon("hollowengine:textures/gui/icons/interaction.svg")
data class InteractionComponent(
    @EditorHidden
    val interactionId: String = "uuid_default",
    @EditorName("РђРєС‚РёРІРЅРѕ")
    val isInteractable: Boolean = true,
    @EditorName("Р Р°РґРёСѓСЃ РґРµР№СЃС‚РІРёСЏ")
    @EditorRange(1f, 64f)
    val radius: Float = 3.0f,
    @EditorName("РўРµРєСЃС‚ РїРѕРґСЃРєР°Р·РєРё")
    @EditorIcon("hollowengine:textures/gui/icons/dialogue.png")
    val hintText: String = "РќР°Р¶РјРёС‚Рµ Р• С‡С‚РѕР±С‹ РіРѕРІРѕСЂРёС‚СЊ",
    @EditorName("РЎРєСЂРёРїС‚ СЃРѕР±С‹С‚РёСЏ")
    @EditorIcon("hollowengine:textures/gui/icons/file_kts.svg")
    val scriptPath: String = "scripts/npc/dialogue_start.kts",
)

@Registerable
@Serializable
@SerialName("hollowengine:advanced_model")
@EditorIcon("hollowengine:textures/gui/icons/folder_npcs.svg")
data class AdvancedModelComponent(
    @EditorName("РџСѓС‚СЊ Рє РјРѕРґРµР»Рё")
    val modelPath: String = "models/entity/custom_npc.gltf",
    @EditorName("РўРµСЃС‚СѓСЂР° СЃРєРёРЅР°")
    @EditorIcon("hollowengine:textures/gui/icons/file_image.svg")
    val texturePath: String = "textures/entity/skin.png",
    @EditorName("РџСЂРѕР·СЂР°С‡РЅРѕСЃС‚СЊ")
    @EditorRange(0f, 1f)
    val alpha: Float = 1.0f,
    @EditorName("РЎРІРµС‚СЏС‰РёР№СЃСЏ")
    @EditorIcon("hollowengine:textures/gui/icons/eye.svg")
    val glow: Boolean = false,
    @EditorName("РђРЅРёРјР°С†РёСЏ РїРѕРєРѕСЏ")
    @EditorIcon("hollowengine:textures/gui/icons/pose_editor.png")
    val idleAnimation: String = "idle_loop",
)
