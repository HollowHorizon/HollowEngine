package ru.hollowhorizon.hollowengine.common.codeblocks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.EndBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.VariableContainer
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import java.util.*


class BlockContext(val scope: CoroutineScope, val file: String) {
    val server = currentServer

    val variables = mutableMapOf<String, VariableContainer<*>>()
    val functions = mutableMapOf<String, CustomBlock>()

    val context = hashMapOf<UUID, BlockContextElement>()
    val interpreters = mutableSetOf<CodeBlockInterpreter<Unit>>()

    fun addBlock(block: StatementBlock) {
        assert(block is StartBlock) { "Root block must be a Start block!" }
        assert(block !is EndBlock) { "Start block can't be a End block!" }

        val interpreter = CodeBlockInterpreter<Unit>(block)
        context[block.uuid] = BlockContextElement(this)
        interpreters += interpreter
    }

    fun addFunction(block: CustomBlock) {
        functions[block.function] = block
    }

    fun launch() {
        interpreters.forEach { interpreter ->
            scope.launch {
                withContext(context[interpreter.root.uuid] ?: error("Context not found!")) {
                    scoped {
                        interpreter.execute()
                    }
                }
            }
        }
    }

    fun save(tag: CompoundTag) {
        tag.put("context", CompoundTag().apply {
            context.forEach { (key, value) ->
                val list = ListTag()
                list.addAll(value.frames.map { it.tag })
                put(key.toString(), list)
            }
        })
    }

    fun load(tag: CompoundTag) {
        context.clear()
        val nbt = tag.getCompound("context")
        nbt.allKeys.forEach { key ->
            val element = BlockContextElement(this)
            element.frames += nbt.getList(key, 10).map { BlockFrame(it as CompoundTag) }
            context[UUID.fromString(key)] = element
        }
    }
}