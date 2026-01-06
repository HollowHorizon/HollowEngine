package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.SetVarBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.createContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.walk
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class ScriptFile(
    val system: BlocksSystem,
    val path: String,
    val allBlocks: List<BlockModel>
) {
    val localVariables = VariableMap()

    val instances = CopyOnWriteArrayList<ScriptInstance>()

    val functions = allBlocks.filterIsInstance<CustomBlock>().associateBy { it.function }

    init {
        allBlocks.flatMap { it.walk() }
            .filterIsInstance<SetVarBlock>()
            .forEach {
                if (!localVariables.contains(it.variableName)) {
                    localVariables[it.variableName] = createContainer(it.expressionType)
                }
            }
    }

    fun startGlobalTriggers() {
        allBlocks.filterIsInstance<StartBlock>()
            .filter { it.isGlobal.value }
            .forEach { trigger ->
                launchNewInstance(trigger)
            }
    }

    fun launchNewInstance(rootBlock: StartBlock): ScriptInstance {
        val instance = ScriptInstance(this, rootBlock)
        instances.add(instance)
        instance.start()
        return instance
    }

    fun stopAll() {
        instances.forEach { it.stop() }
        instances.clear()
    }

    fun serialize(tag: CompoundTag) {
        tag.put("locals", CompoundTag().apply(localVariables::serialize))

        val instancesTag = CompoundTag()
        instances.forEach { instance ->
            instancesTag.put(instance.root.uuid.toString(), CompoundTag().apply(instance::serialize))
        }
        tag.put("instances", instancesTag)
    }

    fun deserialize(tag: CompoundTag) {
        localVariables.deserialize(tag.getCompound("locals"))

        val instancesList = tag.getCompound("instances")
        instancesList.allKeys.forEach { uuid ->
            val instTag = instancesList.getCompound(uuid)
            val rootId = UUID.fromString(uuid)
            val root = allBlocks.find { b -> b.uuid == rootId } as? StartBlock

            if (root != null) {
                val instance = ScriptInstance(this, root)
                instance.deserialize(instTag)
                instances.add(instance)
                instance.resume()
            }
        }
    }
}