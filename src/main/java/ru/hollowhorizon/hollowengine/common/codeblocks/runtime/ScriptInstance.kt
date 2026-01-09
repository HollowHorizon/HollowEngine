package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import kotlinx.coroutines.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockFrame
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.BlockFrameStackElement
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.scoped
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ScriptInstance(
    val ownerFile: ScriptFile,
    val rootBlock: StartBlock,
) {
    val localVariables = VariableMap()
    val scope =
        CoroutineScope(ownerFile.system.owner.dispatcher + SupervisorJob(ownerFile.system.owner.coroutineScope.coroutineContext.job))

    private val executors = ConcurrentHashMap<UUID, ExecutionContext>()

    fun start() {
        launchBlockChain(rootBlock, stackToRestore = null)

        ownerFile.allBlocks.filterIsInstance<StartBlock>()
            .filter { !it.isGlobal.value }
            .forEach { monitorLocalTrigger(it) }
    }

    fun resume() {
        val restoredExecutors = HashMap(executors)
        executors.clear()

        restoredExecutors.forEach { (triggerUuid, context) ->
            val triggerBlock = ownerFile.allBlocks.find { it.uuid == triggerUuid } as? StartBlock
            if (triggerBlock != null) {
                launchBlockChain(triggerBlock, stackToRestore = context.stack)
            }
        }

        ownerFile.allBlocks.filterIsInstance<StartBlock>()
            .filter { !it.isGlobal.value }
            .forEach { trigger ->
                // Если этот триггер сейчас не выполняется (не был восстановлен), запускаем слушатель
                if (!executors.containsKey(trigger.uuid)) {
                    // Если мы перезапускаем скрипт, то наверное старые слушатели, которые завершились уже не нужны?
                } else {
                    monitorLocalTrigger(trigger)
                }
            }
    }

    private fun monitorLocalTrigger(trigger: StartBlock) {
        scope.launch {
            while (isActive) {
                try {
                    withContext(ScriptContextElement(this@ScriptInstance)) {
                        trigger.trigger()
                    }
                    executors[trigger.uuid]?.job?.cancelAndJoin()
                    launchBlockChain(trigger, stackToRestore = null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    delay(1000)
                }
            }
        }
    }


    private fun launchBlockChain(startBlock: StartBlock, stackToRestore: BlockFrameStackElement?) {
        val stackElement = stackToRestore ?: BlockFrameStackElement(this)
        val interpreter = CodeBlockInterpreter<Unit>(startBlock)

        val context = ScriptContextElement(this) + stackElement

        val job = scope.launch(context) {
            try {
                scoped { interpreter.execute() }
            } finally {
                executors.remove(startBlock.uuid)
                if (executors.isEmpty()) stop()
            }
        }

        executors[startBlock.uuid] = ExecutionContext(job, stackElement)
    }

    fun stop() {
        scope.cancel()
        cleanup()
    }

    private fun cleanup() {
        ownerFile.instances.remove(this)
    }

    fun serialize(tag: CompoundTag) {
        tag.putUUID("rootBlockId", rootBlock.uuid)
        tag.put("locals", CompoundTag().apply(localVariables::serialize))

        val threadsTag = CompoundTag()
        executors.forEach { (uuid, context) ->
            val framesList = ListTag()
            framesList.addAll(context.stack.frames.map { it.tag })
            threadsTag.put(uuid.toString(), framesList)
        }
        tag.put("threads", threadsTag)
    }

    fun deserialize(tag: CompoundTag) {
        localVariables.deserialize(tag.getCompound("locals"))

        val threadsTag = tag.getCompound("threads")
        threadsTag.allKeys.forEach { key ->
            val uuid = UUID.fromString(key)
            val framesList = threadsTag.getList(key, 10)

            // Восстанавливаем стек
            val stackElement = BlockFrameStackElement(this)
            stackElement.frames.addAll(framesList.map { BlockFrame(it as CompoundTag) })

            executors[uuid] = ExecutionContext(Job(), stackElement)
        }
    }

    private data class ExecutionContext(
        val job: Job,
        val stack: BlockFrameStackElement,
    )
}