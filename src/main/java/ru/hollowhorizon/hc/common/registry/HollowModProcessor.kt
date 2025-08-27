/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hc.common.registry

import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.api.Init
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.common.utils.nbt.NBT_TAGS
import ru.hollowhorizon.hc.common.capabilities.CAPABILITIES
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapability
import ru.hollowhorizon.hc.common.events.*
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.network.registerPacket
import ru.hollowhorizon.hc.common.network.registerPackets
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

object HollowModProcessor {
    init {
        val handles = MethodHandles.lookup()

        registerMethodHandler<SubscribeEvent> { method, _ ->
            val listener = if (method.isStatic()) {
                handles.createStaticEventListener(method)
            } else {
                val obj = method.declaringClass.kotlin.objectInstance
                    ?: throw IllegalArgumentException("${method.declaringClass.simpleName} must be an object!")
                handles.createEventListener(method, obj)
            }
            EventBus.registerNoInline(method.parameterTypes[0] as Class<Event>, listener)
        }

        AnnotationProcessorEvent(getAnnotatedClasses, getSubTypes, getAnnotatedMethods).post()

        val runnables = arrayListOf<Runnable>()

        registerClassHandler<HollowPacketHandler> { type, _ ->
            if (HollowPacket::class.java.isAssignableFrom(type)) runnables += Runnable { registerPacket(type) }
            else HollowCore.LOGGER.warn("Unsupported packet: ${type.simpleName}")
        }

        registerPackets = {
            runnables.forEach(Runnable::run)
        }

        registerClassHandler<HollowCapability> { clazz, annotation ->
            val generator: () -> CapabilityInstance = {
                clazz.getDeclaredConstructor().newInstance() as CapabilityInstance
            }
            annotation.value.forEach {
                CAPABILITIES.computeIfAbsent(it.java) { ArrayList() }.add(generator)
            }
        }

        registerClassHandler<Registry> { clazz, t ->
            val type = (clazz.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<*>
            val instance = clazz.kotlin.objectInstance as CoreRegistry<*>
            REGISTRIES[type] = instance
        }

        registerClassHandler<Polymorphic> { type, annotation ->
            NBT_TAGS.computeIfAbsent(annotation.baseClass) { ArrayList() }.add(type.kotlin)
        }

        registerClassHandler<Init> { type, _ ->
            type.kotlin.objectInstance ?: throw IllegalArgumentException("${type.simpleName} must be an object!")
        }

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
