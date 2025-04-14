package ru.hollowhorizon.hollowengine.compiler.coroutine

import JvmHacks
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction0
import ru.hollowhorizon.hollowengine.scripting.ResumeState
import ru.hollowhorizon.hollowengine.scripting.SuspendState
import ru.hollowhorizon.hollowengine.scripting.Suspendable

class AsyncController(val action: SFunction0<Any?>) {
    var isActive = false
        private set

    val serializer = Serializer()

    fun update() {
        if (isActive) {
            var result: Any?
            do {
                result = action()
            } while (result == ResumeState)
            if (result != SuspendState) isActive = false
        }
    }

    fun start() {
        isActive = true
    }

    fun stop() {
        isActive = false
    }

    external fun await() // Will be replaced by compiler

    inner class Serializer: KSerializer<AsyncController> {
        override val descriptor = buildClassSerialDescriptor("async_controller") {
            element("isActive", Boolean.serializer().descriptor)
            element("function", action.serializer.descriptor)
        }

        override fun serialize(encoder: Encoder, value: AsyncController) {
            val encoder = encoder.beginStructure(descriptor)
            encoder.encodeBooleanElement(descriptor, 0, value.isActive)
            encoder.encodeSerializableElement(descriptor, 1, value.action.serializer, JvmHacks.forceCast(value.action))
            encoder.endStructure(descriptor)
        }

        override fun deserialize(decoder: Decoder): AsyncController {
            val decoder = decoder.beginStructure(descriptor)
            while(true) {
                when(decoder.decodeElementIndex(descriptor)) {
                    0 -> isActive = decoder.decodeBooleanElement(descriptor, 0)
                    1 -> decoder.decodeSerializableElement(descriptor, 1, action.serializer)
                    CompositeDecoder.DECODE_DONE -> break
                }
            }
            decoder.endStructure(descriptor)
            return this@AsyncController
        }
    }
}

fun async(function: @Suspendable () -> Unit): AsyncController = error("Must be replaced by compiler!")