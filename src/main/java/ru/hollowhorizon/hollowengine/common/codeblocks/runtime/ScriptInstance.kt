package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.BlockFrameStackElement
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.scoped
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.coroutines.*
import ru.hollowhorizon.hollowengine.common.dev.DevLogs
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class ScriptInstance(
    val ownerFile: ScriptFile,
    val rootBlock: StartBlock,
    val ownerEntityId: UUID?,
    val instanceId: UUID = UUID.randomUUID(),
    private val triggerContext: CoroutineContext = EmptyCoroutineContext,
) {
    val localVariables = VariableMap()
    private var launchJob: Job? = null
    private val fallbackScope: EntityScope by lazy {
        EntityScope(
            ownerFile.system.owner.dispatcher +
                SupervisorJob(ownerFile.system.owner.coroutineScope.coroutineContext[Job])
        )
    }

    private val coroutineKey = SerializableCoroutineKey.of(
        SerializableCoroutineKeyPart.Context(ScriptInstanceKey),
        ScriptPathKey with ownerFile.path,
        RootBlockKey with rootBlock.uuid,
        InstanceIdKey with instanceId,
    )
    private var isDefinitionRegistered = false
    private var isStopped = false
    private var isCleanedUp = false
    private var traceStarted = false
    private var initialStackSnapshot: CompoundTag? = null

    @Volatile
    private var activeStack: BlockFrameStackElement? = null

    @Volatile
    private var lastKnownBlockId: UUID? = null

    val ownerKey: OwnerKey = ownerEntityId?.toOwnerKey() ?: OwnerKey.Global
    val branchKey: BranchKey = rootBlock.buildBranchKey(ownerFile.path, ownerKey)

    fun start() {
        if (isStopped) return

        val scope = resolveLaunchScope() ?: run {
            ownerFile.onInstanceUnavailable(this)
            return
        }

        registerLaunchDefinition(scope)
        beginTraceIfNeeded()
        launchJob = scope.launchSerializable(
            key = coroutineKey,
            policy = LaunchPolicy.CANCEL_OLD,
        )
    }

    fun resume() {
        if (isStopped) return

        val scope = resolveLaunchScope() ?: return
        registerLaunchDefinition(scope)

        if (!scope.hasSerializableExecution(coroutineKey)) {
            beginTraceIfNeeded()
            launchJob = scope.launchSerializable(
                key = coroutineKey,
                policy = LaunchPolicy.CANCEL_OLD,
            )
        }
    }

    fun currentBlockId(): UUID? =
        lastKnownBlockId ?: activeStack?.currentBlockId() ?: extractCurrentBlockId(initialStackSnapshot)

    internal fun updateCurrentBlockId(blockId: UUID?) {
        lastKnownBlockId = blockId
    }

    internal fun initialTriggerContext(): CoroutineContext = triggerContext

    private fun beginTraceIfNeeded() {
        if (traceStarted) return
        traceStarted = true
        DevLogs.startTrace(this)
    }

    private fun resolveLaunchScope(): EntityScope? {
        return ownerEntityId?.let(ownerFile::resolveEntityScope) ?: fallbackScope
    }

    private fun createStack(): BlockFrameStackElement = BlockFrameStackElement(this).also { stack ->
        initialStackSnapshot?.copy()?.let(stack::load)
        activeStack = stack
    }

    private fun registerLaunchDefinition(scope: EntityScope) {
        if (isDefinitionRegistered) return

        val baseContext = ScriptContextElement(this) + triggerContext

        scope.registerSerializable(
            SerializableCoroutineDefinition(
                key = coroutineKey,
                contextFactory = ::createStack,
                context = baseContext,
            ) {
                var suspendedByScopeLoss = false
                try {
                    val interpreter = CodeBlockInterpreter<Unit>(rootBlock)
                    scoped { interpreter.execute() }
                } catch (_: SkipScriptEventExecution) {
                    // The incoming event does not match this start block conditions.
                } catch (cancelled: CancellationException) {
                    if (!isStopped && ownerEntityId != null && ownerFile.resolveEntityScope(ownerEntityId) == null) {
                        suspendedByScopeLoss = true
                        suspendExecution()
                    }
                    throw cancelled
                } catch (t: Throwable) {
                    HollowCore.LOGGER.error(
                        "Script {} instance {} failed at block {}",
                        ownerFile.path,
                        instanceId,
                        currentBlockId(),
                        t,
                    )
                    throw t
                } finally {
                    if (!suspendedByScopeLoss) {
                        cleanup()
                    }
                }
            }
        )

        isDefinitionRegistered = true
    }

    fun stop() {
        if (isStopped) return
        isStopped = true

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

    private fun suspendExecution() {
        if (isCleanedUp) return

        initialStackSnapshot = snapshotStack()
        activeStack = null
        isDefinitionRegistered = false
        launchJob = null
        ownerFile.onInstanceSuspended(this)
    }

    private fun cleanup() {
        if (isCleanedUp) return
        isCleanedUp = true

        activeStack = null
        DevLogs.endTrace(this)
        ownerFile.onInstanceCompleted(this)
    }

    fun serialize(tag: CompoundTag) {
        tag.putUUID("instanceId", instanceId)
        ownerEntityId?.let { tag.putUUID("ownerEntityId", it) }
        tag.putUUID("rootBlockId", rootBlock.uuid)
        tag.put("locals", CompoundTag().apply(localVariables::serialize))
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

    private object ScriptInstanceKey : CoroutineContext.Key<CoroutineContext.Element>
    private object ScriptPathKey : CoroutineContext.Key<CoroutineContext.Element>
    private object RootBlockKey : CoroutineContext.Key<CoroutineContext.Element>
    private object InstanceIdKey : CoroutineContext.Key<CoroutineContext.Element>
}



