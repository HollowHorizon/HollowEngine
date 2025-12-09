package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.serialization.serializeNoInline
import java.util.*

open class CodeBlockInterpreter<T : Any>(var root: CodeBlock, val type: Class<T>) {
    val rootUUID = root.uuid

    protected var currentUUID: UUID = root.uuid
    protected var currentBlock: CodeBlock = root

    @Suppress("UNCHECKED_CAST")
    open suspend fun execute(context: BlockContext): T {
        var current: CodeBlock? = root
        var result: Any? = null
        while (current != null) {
            currentUUID = current.uuid
            currentBlock = current
            result = with(current) { context.execute() }
            current = current.next
        }
        return result as T
    }

    open fun serialize(tag: CompoundTag) {
        tag.putUUID("uuid", currentUUID)
        tag.put("block", CompoundTag().apply {
            currentBlock.serialize(this)
        })
    }

    open fun deserialize(tag: CompoundTag) {
        currentUUID = tag.getUUID("uuid")
        var current: CodeBlock = root
        while (current.uuid != currentUUID) {
            current = current.next ?: error("Input $currentUUID not attached!")
        }
        root = current
        currentBlock = current

        tag.getCompound("block").let {
            currentBlock.deserialize(it)
        }
    }
}

class CachedCodeBlockInterpreter<T : Any>(root: CodeBlock, type: Class<T>) : CodeBlockInterpreter<T>(root, type) {
    var value: Value<T>? = null
    var isRestoring =
        false // Используется, чтобы кэшированное значение сработало только 1 раз при запуске. Иначе циклы работать не будут

    override suspend fun execute(context: BlockContext): T {
        if (isRestoring) {
            value?.let {
                isRestoring = false
                return it.value
            }
        }
        val value = super.execute(context)
        this.value = Value(value, type)
        return value
    }

    override fun serialize(tag: CompoundTag) {
        value?.let {
            tag.put(CACHED_TAG, it.serialize())
        } ?: run {
            super.serialize(tag)
        }
    }

    override fun deserialize(tag: CompoundTag) {
        val cached = tag.get(CACHED_TAG) ?: return super.deserialize(tag)
        value = Value.create(cached, type)
        isRestoring = true
    }

    companion object {
        const val CACHED_TAG = $$"$cached"
    }
}

class Value<T : Any>(var value: T, val type: Class<T>) {
    fun serialize(): Tag =
        value.let { value -> NBTFormat.serializeNoInline(value, type) }

    companion object {
        fun <T : Any> create(tag: Tag, type: Class<T>): Value<T> {
            return Value(NBTFormat.deserializeNoInline(tag, type), type)
        }
    }
}
