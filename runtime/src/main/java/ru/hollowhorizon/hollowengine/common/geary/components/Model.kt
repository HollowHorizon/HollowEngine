package ru.hollowhorizon.hollowengine.common.geary.components

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable
import ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationController
import ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationSystem
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScopeOrNull
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
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

