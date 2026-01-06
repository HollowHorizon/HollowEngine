package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockFrame
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.BlockFrameStackElement
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher
import kotlin.coroutines.cancellation.CancellationException

class ScriptInstance(
    val ownerFile: ScriptFile,
    rootBlock: StartBlock,
) {
    val scriptContext = ScriptContextElement(this)
    val blockStack = BlockFrameStackElement(this)

    val scope = CoroutineScope(ownerFile.system.owner.dispatcher + SupervisorJob() + scriptContext + blockStack)

    val interpreter = CodeBlockInterpreter<Unit>(rootBlock)
    val root get() = interpreter.root

    fun start() {
        scope.launch {
            try {
                interpreter.execute()
            } catch (_: CancellationException) {
                // Скрипт остановлен
            } catch (e: Exception) {
                HollowCore.LOGGER.error("Error in script ${ownerFile.path}", e)
            } finally {
                cleanup()
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    private fun cleanup() {
        ownerFile.instances.remove(this)
    }

    fun serialize(tag: CompoundTag) {
        val list = ListTag()
        list.addAll(blockStack.frames.map { it.tag })
        tag.put("stack", list)
    }

    fun deserialize(tag: CompoundTag) {
        val frames = tag.getList("stack", 10).map {
            BlockFrame(it as CompoundTag)
        }
        blockStack.frames.clear()
        blockStack.frames.addAll(frames)
    }

    fun resume() {
        start()
    }
}