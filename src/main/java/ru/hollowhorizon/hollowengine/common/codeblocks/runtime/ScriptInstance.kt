package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.BlockFrameStackElement
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.scoped
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.coroutines.*
import ru.hollowhorizon.hollowengine.common.dev.DevLogs
import java.util.UUID

class ScriptInstance(
    val ownerFile: ScriptFile,
    val rootBlock: StartBlock,
    val ownerEntityId: UUID?,
    val instanceId: UUID = UUID.randomUUID(),
    private val triggerEvent: ru.hollowhorizon.hollowengine.common.events.Event? = null,
) {
    val localVariables = VariableMap()
    private var launchJob: Job? = null
    private val fallbackScope = EntityScope(
        ownerFile.system.owner.dispatcher +
            SupervisorJob(ownerFile.system.owner.coroutineScope.coroutineContext[Job])
    )

    private val coroutineKey = SerializableCoroutineKey.of(
        SerializableCoroutineKeyPart.Context(ScriptInstanceKey),
        ScriptPathKey with ownerFile.path,
        RootBlockKey with rootBlock.uuid,
        InstanceIdKey with instanceId,
    )
    private var isDefinitionRegistered = false
    private var isStopped = false

    fun start() {
        val scope = resolveLaunchScope() ?: run {
            ownerFile.unregisterInstance(this)
            return
        }

        DevLogs.startTrace(this)
        registerLaunchDefinition(scope)

        launchJob = scope.launchSerializable(
            key = coroutineKey,
            policy = LaunchPolicy.CANCEL_OLD,
        )
    }

    fun resume() {
        val scope = resolveLaunchScope() ?: return
        registerLaunchDefinition(scope)
    }

    private fun resolveLaunchScope(): EntityScope? {
        return ownerEntityId?.let(ownerFile::resolveEntityScope) ?: fallbackScope
    }

    private fun createStack(): BlockFrameStackElement = BlockFrameStackElement(this)

    private fun registerLaunchDefinition(scope: EntityScope) {
        if (isDefinitionRegistered) return

        val eventContext = triggerEvent?.let(::ScriptEventContextElement)
        val baseContext = ScriptContextElement(this) + (eventContext ?: kotlin.coroutines.EmptyCoroutineContext)

        scope.registerSerializable(
            SerializableCoroutineDefinition(
                key = coroutineKey,
                contextFactory = ::createStack,
                context = baseContext,
            ) {
                try {
                    val interpreter = CodeBlockInterpreter<Unit>(rootBlock)
                    scoped { interpreter.execute() }
                } catch (_: SkipScriptEventExecution) {
                    // The incoming event does not match this start block conditions.
                } finally {
                    ownerFile.unregisterInstance(this@ScriptInstance)
                }
            }
        )

        isDefinitionRegistered = true
    }

    fun stop() {
        if (isStopped) return
        isStopped = true

        DevLogs.endTrace(this)
        val scope = resolveLaunchScope()
        if (scope != null) {
            scope.cancelSerializable(coroutineKey)
            if (ownerEntityId == null) {
                fallbackScope.cancelAll()
            }
        } else {
            launchJob?.cancel()
        }
        cleanup()
    }

    private fun cleanup() {
        ownerFile.unregisterInstance(this)
    }

    fun serialize(tag: CompoundTag) {
        tag.putUUID("instanceId", instanceId)
        ownerEntityId?.let { tag.putUUID("ownerEntityId", it) }
        tag.putUUID("rootBlockId", rootBlock.uuid)
        tag.put("locals", CompoundTag().apply(localVariables::serialize))
    }

    fun deserialize(tag: CompoundTag) {
        localVariables.deserialize(tag.getCompound("locals"))
    }

    private object ScriptInstanceKey : kotlin.coroutines.CoroutineContext.Key<kotlin.coroutines.CoroutineContext.Element>
    private object ScriptPathKey : kotlin.coroutines.CoroutineContext.Key<kotlin.coroutines.CoroutineContext.Element>
    private object RootBlockKey : kotlin.coroutines.CoroutineContext.Key<kotlin.coroutines.CoroutineContext.Element>
    private object InstanceIdKey : kotlin.coroutines.CoroutineContext.Key<kotlin.coroutines.CoroutineContext.Element>
}
