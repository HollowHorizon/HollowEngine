package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.BlockFrameStackElement
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.scoped
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.coroutines.*
import ru.hollowhorizon.hollowengine.common.dev.DevLogs

class ScriptInstance(
    val ownerFile: ScriptFile,
    val rootBlock: StartBlock,
) {
    val localVariables = VariableMap()
    val scope =
        CoroutineScope(
            ownerFile.system.owner.dispatcher +
                SupervisorJob(ownerFile.system.owner.coroutineScope.coroutineContext[Job])
        )

    private val serializableScope = EntityScope(scope.coroutineContext)
    private val coroutineKey = SerializableCoroutineKey.of(
        SerializableCoroutineKeyPart.Context(ScriptInstanceKey),
        ScriptPathKey with ownerFile.path,
        RootBlockKey with rootBlock.uuid,
    )
    private var isDefinitionRegistered = false
    private var isStopped = false

    fun start() {
        DevLogs.startTrace(this)
        registerLaunchDefinition()

        serializableScope.launchSerializable(
            key = coroutineKey,
            policy = LaunchPolicy.CANCEL_OLD,
        )
    }

    fun resume() {
        registerLaunchDefinition()
    }

    private fun createStack(): BlockFrameStackElement = BlockFrameStackElement(this)

    private fun registerLaunchDefinition() {
        if (isDefinitionRegistered) return

        serializableScope.registerSerializable(
            SerializableCoroutineDefinition(
                key = coroutineKey,
                contextFactory = ::createStack,
                context = ScriptContextElement(this),
            ) {
                val interpreter = CodeBlockInterpreter<Unit>(rootBlock)
                scoped { interpreter.execute() }
            }
        )

        isDefinitionRegistered = true
    }

    fun stop() {
        if (isStopped) return
        isStopped = true

        DevLogs.endTrace(this)
        serializableScope.cancelAll()
        scope.cancel()
        cleanup()
    }

    private fun cleanup() {
        ownerFile.instances.remove(this)
    }

    fun serialize(tag: CompoundTag) {
        tag.putUUID("rootBlockId", rootBlock.uuid)
        tag.put("locals", CompoundTag().apply(localVariables::serialize))
        tag.put("threads", CompoundTag().apply(serializableScope::serialize))
    }

    fun deserialize(tag: CompoundTag) {
        localVariables.deserialize(tag.getCompound("locals"))
        val threadsTag = tag.getCompound("threads")

        if (threadsTag.contains("executions")) {
            serializableScope.deserialize(threadsTag)
            return
        }

        // Backward compatibility for old ScriptInstance format (uuid -> ListTag<CompoundTag frames>)
        val migrated = CompoundTag()
        val migratedExecutions = ListTag()
        threadsTag.allKeys.forEach { key ->
            val frames = threadsTag.getList(key, 10)
            val context = CompoundTag().apply { put("frames", frames) }
            migratedExecutions.add(
                CompoundTag().apply {
                    val migratedKey = if (key == rootBlock.uuid.toString()) {
                        coroutineKey
                    } else {
                        SerializableCoroutineKey.of(
                            SerializableCoroutineKeyPart.Context(ScriptInstanceKey),
                            ScriptPathKey with ownerFile.path,
                            RootBlockKey with key,
                        )
                    }
                    migratedKey.save(this)
                    putString("state", "RUNNING")
                    put("context", context)
                }
            )
        }
        migrated.put("executions", migratedExecutions)
        serializableScope.deserialize(migrated)
    }

    private object ScriptInstanceKey : kotlin.coroutines.CoroutineContext.Key<kotlin.coroutines.CoroutineContext.Element>
    private object ScriptPathKey : kotlin.coroutines.CoroutineContext.Key<kotlin.coroutines.CoroutineContext.Element>
    private object RootBlockKey : kotlin.coroutines.CoroutineContext.Key<kotlin.coroutines.CoroutineContext.Element>
}
