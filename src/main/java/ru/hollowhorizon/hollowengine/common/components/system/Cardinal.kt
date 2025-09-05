package ru.hollowhorizon.hollowengine.common.components.system

import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.client.kool.addons.StringRenderer
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.ModelData
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.utils.toTexture
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.ComponentDispatcherEvent
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.utils.isLogicalClient
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

@ComponentMeta("hollowengine:model_renderer")
class ModelComponent : Component<LivingEntity>() {
    var model: String by property { "hollowengine:models/entity/player_model.gltf" }
        .renderer(StringRenderer)
        .onChange { old, new ->
            if (!isLogicalClient) return@onChange
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

object Cardinal {
    inline fun <reified T : Component<*>> query(noinline filter: T.() -> Boolean = { true }) = Query(T::class, filter)

    inline fun <reified T : ComponentDispatcherEvent<*>, reified C : Component<*>> on(noinline handler: T.(C) -> Unit): (T) -> Unit {
        val location = C::class.findAnnotation<ComponentMeta>()?.location?.rl
            ?: error("ComponentMeta annotation not found on ${C::class}")
        val eventListener: (T) -> Unit = let@{ event ->
            val dispatcher = event.owner
            val component = dispatcher.`hollowcore$components`[location]
            (component as? C)?.let { handler(event, it) }
        }
        EventBus.register(eventListener)
        return eventListener
    }
}

class Query<T : Component<*>>(private val type: KClass<T>, private val filter: (T) -> Boolean) {
    val components = mutableSetOf<T>()
    val clientComponents = mutableSetOf<T>()

    private val onAdded: ComponentEvent.Added.() -> Unit = {
        if (component::class == type && filter(component as T)) {
            if (component.isClient) clientComponents.add(component as T)
            else components.add(component)
        }
    }
    private val onRemoved: ComponentEvent.Removed.() -> Unit = {
        if (component::class == type) {
            components.remove(component as T)
            clientComponents.remove(component as T)
        }
    }
    private val onUpdated: ComponentEvent.Updated.() -> Unit = {
        if (component::class == type) {
            val comp = component as T
            if (filter(comp)) {
                if (comp.isClient) clientComponents.add(comp)
                else components.add(comp)
            } else {
                components.remove(comp)
                clientComponents.remove(comp)
            }
        }
    }
    private val onEnabled: ComponentEvent.Enabled.() -> Unit = {
        if (component::class == type && filter(component as T)) {
            if (component.isClient) clientComponents.add(component as T)
            else components.add(component as T)
        }
    }
    private val onDisabled: ComponentEvent.Disabled.() -> Unit = {
        if (component::class == type) {
            components.remove(component as T)
            clientComponents.remove(component as T)
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
        val eventListener: (E) -> Unit = listener@{ event ->
            if (event is ClientEvent) {
                if (clientComponents.isEmpty()) return@listener
                handler(event, clientComponents)
                return@listener
            } else if (components.isEmpty()) return@listener
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
    Cardinal.on<RenderEntityEvent.Pre, ModelComponent> { model ->

        poseStack.pushPose()
        if (model.internalModel.model.isBlockBench) poseStack.mulPose(Quaternionf().rotateY(180f * Mth.DEG_TO_RAD))
        var overlay = OverlayTexture.NO_OVERLAY
        if (entity is LivingEntity) {
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

        model.internalModel.render(
            poseStack,
            ModelData(null, null, null, null),
            { it.toTexture().id },
            buffer,
            packedLight,
            overlay
        )
        poseStack.popPose()

        isCanceled = true
    }
}

