package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import kotlinx.coroutines.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.SetVarBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.createContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.walk
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher
import java.util.concurrent.CopyOnWriteArrayList

class ScriptFile(
    val system: BlocksSystem,
    val path: String,
    val allBlocks: List<BlockModel>
) {
    private val declaredLocalVariables = allBlocks.flatMap { it.walk() }
        .filterIsInstance<SetVarBlock>()
        .associate { it.variableName to it.expressionType }

    val instances = CopyOnWriteArrayList<ScriptInstance>()
    val functions = allBlocks.filterIsInstance<CustomBlock>().associateBy { it.function }

    private val scope = CoroutineScope(system.owner.dispatcher + SupervisorJob())

    fun startGlobalTriggers() {
        allBlocks.filterIsInstance<StartBlock>()
            .filter { it.isGlobal.value }
            .forEach { trigger ->
                monitorGlobalTrigger(trigger)
            }
    }

    private fun monitorGlobalTrigger(trigger: StartBlock) {
        scope.launch {
            while (isActive) {
                try {
                    trigger.trigger()

                    launchNewInstance(trigger)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    HollowCore.LOGGER.error("Error in global trigger monitor: $path", e)
                    cancel("Error in global trigger monitor: $path")
                }
            }
        }
    }

    private fun launchNewInstance(rootBlock: StartBlock): ScriptInstance {
        val instance = ScriptInstance(this, rootBlock)

        declaredLocalVariables.forEach { (name, type) ->
            if (!instance.localVariables.contains(name)) {
                instance.localVariables[name] = createContainer(type)
            }
        }

        instances.add(instance)
        instance.start()
        return instance
    }

    fun stopAll() {
        scope.cancel()
        instances.forEach { it.stop() }
        instances.clear()
    }

    fun serialize(tag: CompoundTag) {
        val instancesList = ListTag()
        instances.forEach { instance ->
            instancesList.add(CompoundTag().apply(instance::serialize))
        }
        tag.put("instances", instancesList)
    }

    fun deserialize(tag: CompoundTag) {
        val instancesList = tag.getList("instances", 10)

        instancesList.forEach { it ->
            val instTag = it as CompoundTag
            val rootId = instTag.getUUID("rootBlockId")
            val root = allBlocks.find { b -> b.uuid == rootId } as? StartBlock

            if (root != null) {
                val instance = ScriptInstance(this, root)
                declaredLocalVariables.forEach { (name, type) ->
                    instance.localVariables[name] = createContainer(type)
                }

                instance.deserialize(instTag)
                instances.add(instance)

                instance.resume()
            }
        }
    }
}