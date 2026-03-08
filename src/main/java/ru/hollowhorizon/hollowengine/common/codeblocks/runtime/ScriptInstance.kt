package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.BlockFrameStackElement
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.coroutines.RuntimeDefinitionId
import java.util.UUID

class ScriptInstance(
    val ownerFile: ScriptFile,
    val rootBlock: StartBlock,
    var ownerKey: OwnerKey,
    val definitionId: RuntimeDefinitionId,
    val instanceId: UUID = UUID.randomUUID(),
) {
    val localVariables = VariableMap(ownerFile.system::markDirty)
    val branchKey: BranchKey get() = rootBlock.buildBranchKey(ownerFile.path, ownerKey)

    private var cancelExecution: (() -> Unit)? = null
    private var initialStackSnapshot: CompoundTag? = null

    @Volatile
    private var activeStack: BlockFrameStackElement? = null

    @Volatile
    private var lastKnownBlockId: UUID? = null

    fun installCancel(callback: () -> Unit) {
        cancelExecution = callback
    }

    fun attachStack(stack: BlockFrameStackElement) {
        activeStack = stack
    }

    fun detachStack() {
        initialStackSnapshot = snapshotStack()
        activeStack = null
    }

    fun stop() {
        cancelExecution?.invoke()
    }

    fun currentBlockId(): UUID? =
        lastKnownBlockId ?: activeStack?.currentBlockId() ?: extractCurrentBlockId(initialStackSnapshot)

    internal fun updateCurrentBlockId(blockId: UUID?) {
        lastKnownBlockId = blockId
    }

    fun serialize(tag: CompoundTag) {
        localVariables.serialize(tag.getOrPutCompound("locals"))
        snapshotStack()?.let { tag.put("stack", it) }
    }

    fun deserialize(tag: CompoundTag) {
        localVariables.deserialize(tag.getCompound("locals"))
        initialStackSnapshot = if (tag.contains("stack")) tag.getCompound("stack").copy() else null
        lastKnownBlockId = extractCurrentBlockId(initialStackSnapshot)
    }

    private fun snapshotStack(): CompoundTag? {
        activeStack?.let { stack ->
            return CompoundTag().also(stack::save)
        }
        return initialStackSnapshot?.copy()
    }

    private fun extractCurrentBlockId(snapshot: CompoundTag?): UUID? {
        val tag = snapshot ?: return null
        val frames = tag.getList("frames", 10)
        if (frames.isEmpty()) return null
        val currentFrame = frames.lastOrNull() as? CompoundTag ?: return null
        return if (currentFrame.contains("uuid")) currentFrame.getUUID("uuid") else null
    }
}

private fun CompoundTag.getOrPutCompound(name: String): CompoundTag {
    if (!contains(name)) put(name, CompoundTag())
    return getCompound(name)
}
