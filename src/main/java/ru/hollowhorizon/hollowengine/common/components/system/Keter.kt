package ru.hollowhorizon.hollowengine.common.components.system

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.LightLayer
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.ModelData
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.utils.toTexture
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.utils.isLogicalClient
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

@ComponentMeta("hollowengine:model_renderer")
class ModelComponent : Component<LivingEntity>() {
    var model: String by property { "hollowengine:models/entity/player_model.gltf" }
        .onChange { old, new ->
            if(!isLogicalClient) return@onChange
            internalModel = HollowModelManager.getOrCreate(new.rl)
        }

    internal var internalModel: AnimatedModel by mutableLazy { HollowModelManager.getOrCreate(model.rl) }
}

class MutableLazy<T>(private val initializer: () -> T) {
    private var _value: Any? = UNINITIALIZED
    private var initialized = false

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!initialized) {
            _value = initializer()
            initialized = true
        }
        @Suppress("UNCHECKED_CAST")
        return _value as T
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        _value = value
        initialized = true
    }

    fun isInitialized(): Boolean = initialized

    companion object {
        private object UNINITIALIZED
    }
}

fun <T> mutableLazy(initializer: () -> T) = MutableLazy(initializer)

object Keter {
    inline fun <reified T : Component<*>> query(noinline filter: T.() -> Boolean = { true }) = Query(T::class, filter)
}

class Query<T : Component<*>>(private val type: KClass<T>, private val filter: (T) -> Boolean) {
    val components = mutableSetOf<T>()

    private val onAdded: ComponentEvent.Added.() -> Unit = {
        if (component::class == type && filter(component as T)) {
            components.add(component)
        }
    }
    private val onRemoved: ComponentEvent.Removed.() -> Unit = {
        if (component::class == type) {
            components.remove(component as T)
        }
    }
    private val onUpdated: ComponentEvent.Updated.() -> Unit = {
        if (component::class == type) {
            val comp = component as T
            if (filter(comp)) {
                components.add(comp)
            } else {
                components.remove(comp)
            }
        }
    }
    private val onEnabled: ComponentEvent.Enabled.() -> Unit = {
        if (component::class == type && filter(component as T)) {
            components.add(component)
        }
    }
    private val onDisabled: ComponentEvent.Disabled.() -> Unit = {
        if (component::class == type) {
            components.remove(component as T)
        }
    }

    init {
        EventBus.register(onEnabled)
        EventBus.register(onDisabled)
        EventBus.register(onAdded)
        EventBus.register(onRemoved)
        EventBus.register(onUpdated)
    }

    inline fun <reified E : Event> on(noinline handler: E.(Collection<T>) -> Unit): (E) -> Unit {
        val eventListener: (E) -> Unit = { event ->
            handler(event, components)
        }
        EventBus.register(eventListener)
        return eventListener
    }

    inline fun <reified E : Event> onEach(noinline handler: (E, T) -> Unit) {
        on<E> { comps ->
            comps.forEach { handler(this, it) }
        }
    }

    fun dispose() {
        EventBus.unregister(onEnabled)
        EventBus.unregister(onDisabled)
        EventBus.unregister(onAdded)
        EventBus.unregister(onRemoved)
        EventBus.unregister(onUpdated)
    }
}

@Init
fun main() {
    Keter.query<ModelComponent>().apply {
        on<RenderLevelStageEvent> {
            if (stage != RenderStage.AFTER_ENTITIES) return@on
            val vec3 = camera.getPosition()
            val d0 = vec3.x()
            val d1 = vec3.y()
            val d2 = vec3.z()
            it.forEach {
                if(!it.provider.level().isClientSide) return@forEach
                val x = Mth.lerp(partialTick.toDouble(), it.provider.xOld, it.provider.x)
                val y = Mth.lerp(partialTick.toDouble(), it.provider.yOld, it.provider.y)
                val z = Mth.lerp(partialTick.toDouble(), it.provider.zOld, it.provider.z)
                val lerpBodyRot = Mth.rotLerp(partialTick, it.provider.yBodyRotO, it.provider.yBodyRot)

                poseStack.pushPose()
                poseStack.translate(x - d0, y - d1, z - d2)
                poseStack.mulPose(Quaternionf().rotateY(180f * Mth.DEG_TO_RAD))
                poseStack.mulPose(Quaternionf().rotateY(-lerpBodyRot * Mth.DEG_TO_RAD))

                val pos = it.provider.blockPosition()
                val light = LightTexture.pack(
                    if (it.provider.isOnFire) 15 else it.provider.level().getBrightness(LightLayer.BLOCK, pos),
                    it.provider.level().getBrightness(LightLayer.SKY, pos)
                )

                it.internalModel.render(
                    poseStack,
                    ModelData(null, null, null, null),
                    { it.toTexture().id },
                    Minecraft.getInstance().renderBuffers().bufferSource(),
                    light,
                    OverlayTexture.NO_OVERLAY
                )

                poseStack.popPose()
            }
        }
    }
}