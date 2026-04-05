package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.BlockFrameStackElement
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.coroutines.RuntimeDefinitionId
import ru.hollowhorizon.hollowengine.common.coroutines.RuntimeExecutionState
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

data class CodeBlockExecutionState(
    private val ownerFile: ScriptFile,
    private val rootBlock: StartBlock,
    private val definitionId: RuntimeDefinitionId,
) : RuntimeExecutionState {
    var ownerKey: OwnerKey = OwnerKey.Global
        private set
    var signal: ScriptSignal? = null
        private set
    private var transientContext: CoroutineContext = EmptyCoroutineContext

    val instance: ScriptInstance = ScriptInstance(ownerFile, rootBlock, OwnerKey.Global, definitionId)
    private var stack: BlockFrameStackElement = BlockFrameStackElement(instance)

    fun initialize(
        ownerKey: OwnerKey,
        signal: ScriptSignal? = null,
        transientContext: CoroutineContext = EmptyCoroutineContext,
    ) {
        this.ownerKey = ownerKey
        this.signal = signal
        this.transientContext = transientContext
        instance.ownerKey = ownerKey
    }

    override fun save(tag: CompoundTag) {
        tag.put("owner", serializeOwnerKey(ownerKey))
        instance.serialize(tag)
        signal?.let { tag.put("signal", it.serialize()) }
    }

    override fun load(tag: CompoundTag) {
        ownerKey = deserializeOwnerKey(tag.getCompound("owner"))
        signal = tag.takeIf { it.contains("signal") }?.getCompound("signal")?.let(ScriptSignal::deserialize)
        transientContext = EmptyCoroutineContext
        instance.ownerKey = ownerKey
        instance.deserialize(tag)
        stack = BlockFrameStackElement(instance).also {
            tag.takeIf { it.contains("stack") }?.getCompound("stack")?.copy()?.let(it::load)
            instance.attachStack(it)
        }
    }

    override fun buildCoroutineContext(): CoroutineContext {
        instance.attachStack(stack)
        return ScriptContextElement(instance) + stack + transientContext + (signal?.let(::ScriptSignalContextElement) ?: EmptyCoroutineContext)
    }

    override fun installCancel(callback: () -> Unit) {
        instance.installCancel(callback)
    }
}

private fun serializeOwnerKey(ownerKey: OwnerKey): CompoundTag = CompoundTag().apply {
    when (ownerKey) {
        OwnerKey.Global -> putString("type", "global")
        is OwnerKey.Entity -> {
            putString("type", "entity")
            putUUID("uuid", ownerKey.uuid)
        }
    }
}

private fun deserializeOwnerKey(tag: CompoundTag): OwnerKey {
    return when (tag.getString("type")) {
        "entity" -> OwnerKey.Entity(tag.getUUID("uuid"))
        else -> OwnerKey.Global
    }
}
