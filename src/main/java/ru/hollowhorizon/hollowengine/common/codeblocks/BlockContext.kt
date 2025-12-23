package ru.hollowhorizon.hollowengine.common.codeblocks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.SetVarBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.EndBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.LivingEntityContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.SerializableVariableContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.VariableContainer
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import java.util.*


class BlockContext(val scope: CoroutineScope, val file: String) {
    val server = currentServer

    private val _variables = mutableMapOf<String, VariableContainer<*>>()
    val variables: Map<String, VariableContainer<*>>
        get() = _variables
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
        initVariables()

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

    private fun initVariables() {
        if (_variables.isNotEmpty()) return

        interpreters.forEach { interpreter ->
            interpreter.root.walk().filterIsInstance<SetVarBlock>().forEach {
                val type = it.expressionType ?: return@forEach

                val variable: VariableContainer<*> = if (typeOf<LivingEntity>().accepts(type)) {
                    LivingEntityContainer<LivingEntity>()
                } else {
                    val serializer = serializer((type as KTypeExpressionType).kType) as KSerializer<Any>
                    SerializableVariableContainer(serializer)
                }

                _variables.put(it.varName, variable)
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
        tag.put("variables", CompoundTag().apply {
            variables.forEach { (key, value) ->
                put(key, CompoundTag().apply(value::save))
            }
        })
    }

    fun load(tag: CompoundTag) {
        initVariables()

        context.clear()
        var nbt = tag.getCompound("context")
        nbt.allKeys.forEach { key ->
            val element = BlockContextElement(this)
            element.frames += nbt.getList(key, 10).map { BlockFrame(it as CompoundTag) }
            context[UUID.fromString(key)] = element
        }
        nbt = tag.getCompound("variables")
        nbt.allKeys.forEach { key ->
            _variables[key]?.load(nbt.getCompound(key))
        }
    }
}