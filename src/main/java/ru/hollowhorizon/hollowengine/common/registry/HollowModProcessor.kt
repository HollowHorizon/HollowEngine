package ru.hollowhorizon.hollowengine.common.registry

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.serializerOrNull
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.api.*
import ru.hollowhorizon.hollowengine.api.utils.Polymorphic
import ru.hollowhorizon.hollowengine.common.config.Config
import ru.hollowhorizon.hollowengine.common.config.ConfigName
import ru.hollowhorizon.hollowengine.common.events.*
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hollowengine.common.geary.components.*
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.registerPacket
import ru.hollowhorizon.hollowengine.common.network.registerPackets
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryManager
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.Side
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBT_TAGS
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.full.findAnnotation

@OptIn(InternalSerializationApi::class)
object HollowModProcessor {
    init {
        val handles = MethodHandles.lookup()

        registerMethodHandler<SubscribeEvent> { method, _ ->
            val listener = if (method.isStatic()) {
                handles.createStaticEventListener(method)
            } else {
                val obj = method.declaringClass.kotlin.objectInstance ?: return@registerMethodHandler
                handles.createEventListener(method, obj)
            }
            EventBus.registerNoInline(method.parameterTypes[0] as Class<Event>, listener)
        }

        AnnotationProcessorEvent(getAnnotatedClasses, getSubTypes, getAnnotatedMethods).post()

        registerClassHandler<ConfigName> { type, _ ->
            (type.kotlin.objectInstance as Config).initialize()
        }

        val runnables = arrayListOf<Runnable>()

        registerClassHandler<HollowPacketHandler> { type, _ ->
            if (HollowPacket::class.java.isAssignableFrom(type)) runnables += Runnable { registerPacket(type) }
            else HollowCore.LOGGER.warn("Unsupported packet: ${type.simpleName}")
        }

        registerPackets = {
            runnables.forEach(Runnable::run)
        }

        registerClassHandler<Polymorphic> { type, annotation ->
            NBT_TAGS.computeIfAbsent(annotation.baseClass) { ArrayList() }.add(type.kotlin)
        }

        registerClassHandler<Init> { type, _ ->
            type.kotlin.objectInstance ?: throw IllegalArgumentException("${type.simpleName} must be an object!")
        }
        registerClassHandler<ReloadListener> { type, listener ->
            val instance = type.kotlin.objectInstance as ResourceManagerReloadListener

            when (listener.side) {
                Side.CLIENT -> {
                    if (isPhysicalClient) EventBus.register<RegisterReloadListenersEvent.Client> {
                        it.register(instance)
                    }
                }

                Side.SERVER -> {
                    EventBus.register<RegisterReloadListenersEvent.Server> {
                        it.register(instance)
                    }
                }

                Side.BOTH -> {
                    EventBus.register<RegisterReloadListenersEvent.Server> {
                        it.register(instance)
                    }
                    if (isPhysicalClient) EventBus.register<RegisterReloadListenersEvent.Client> {
                        it.register(instance)
                    }
                }
            }
        }
        registerClassHandler<Registerable> { type, _ ->
            val component = type.kotlin
            val serializer = component.serializerOrNull() ?: error("${type.simpleName} must be serializable!")
            val key = component.findAnnotation<SerialName>()?.value?.rl
                ?: error("@SerialName not found for class ${type.simpleName}")

            ComponentDescriptorRegistry.register(
                ComponentDescriptor(
                    id = key,
                    value = component,
                    serializer = JavaHacks.forceCast(serializer),
                    editable = !type.isAnnotationPresent(EditorHidden::class.java),
                    persistencePolicy = if (type.isAnnotationPresent(LooseOnDeath::class.java)) {
                        ComponentPersistencePolicy.LOOSE_ON_DEATH
                    } else {
                        ComponentPersistencePolicy.PERSIST
                    },
                    syncPolicy = if (type.isAnnotationPresent(Syncable::class.java)) {
                        ComponentSyncPolicy.SYNC
                    } else {
                        ComponentSyncPolicy.NONE
                    },
                )
            )
        }
        registerMethodHandler<Init> { method, _ ->
            if (method.isStatic()) {
                method.invoke(null)
            } else {
                val obj = method.declaringClass.kotlin.objectInstance
                    ?: throw IllegalArgumentException("${method.declaringClass.simpleName} must be an object!")
                method.invoke(obj)
            }
        }

        RegistryManager.bakeAll()
        RegistryManager.freezeAll()

        registerClassInitializers<HollowRegistry>()
    }

    private inline fun <reified T : Annotation> registerClassHandler(noinline task: (Class<*>, T) -> Unit) {
        getAnnotatedClasses(T::class.java).forEach {
            val annotation = it.getAnnotation(T::class.java)
            task(it, annotation)
        }
    }

    private inline fun <reified T> registerClassInitializers() {
        getSubTypes(T::class.java).forEach {
            HollowCore.LOGGER.info("Registering initializer: ${it.simpleName}")
            it.kotlin.objectInstance ?: throw IllegalArgumentException("${T::class.java.simpleName} must be an object!")
        }
    }

    private inline fun <reified T : Annotation> registerMethodHandler(noinline task: (Method, T) -> Unit) {
        getAnnotatedMethods(T::class.java).forEach {
            val annotation = it.getAnnotation(T::class.java)
            task(it, annotation)
        }
    }
}

lateinit var getAnnotatedClasses: (Class<*>) -> Set<Class<*>>
lateinit var getSubTypes: (Class<*>) -> Set<Class<*>>
lateinit var getAnnotatedMethods: (Class<*>) -> Set<Method>

private fun Method.isStatic(): Boolean {
    return Modifier.isStatic(this.modifiers)
}
