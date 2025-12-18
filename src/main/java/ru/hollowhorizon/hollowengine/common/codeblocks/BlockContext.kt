package ru.hollowhorizon.hollowengine.common.codeblocks

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.decodeFromStream
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.EndBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.*
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.CachedCodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockSerializer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.LivingEntityContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.SerializableVariableContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.VariableContainer
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.io.File
import java.util.*

class CodeBlocksComponent(server: MinecraftServer) : Component<MinecraftServer>(server) {
    val contexts = mutableListOf<BlockContext>()

    override fun serialize(tag: CompoundTag) {
        contexts.forEach {
            tag.put(it.file, CompoundTag().apply(it::save))
            it.scope.cancel()
        }
    }

    override fun deserialize(compound: CompoundTag) {
        contexts.forEach { it.scope.cancel() }
        contexts.clear()
        compound.allKeys.forEach { file ->
            val context = createScript(file.fromReadablePath(), owner)
            context.load(compound.getCompound(file))
            context.launch()
        }
    }
}

@SubscribeEvent
fun onServerStart(event: ServerEvent.Starting) {
    (event.server as ComponentDispatcher).container.attach("hollowengine:code_blocks_component".rl)
}

class BlockContext(val scope: CoroutineScope, val file: String) {
    val server = currentServer
    val variables = mutableMapOf<String, VariableContainer<*>>()
    val interpreters = mutableSetOf<CodeBlockInterpreter<Unit>>()
    val functions = mutableMapOf<String, CustomBlock>()

    private val onLoad = mutableListOf<suspend () -> Unit>()

    fun addBlock(block: StatementBlock) {
        if (block !is StartBlock) return
        if (block is EndBlock) return

        val interpreter = CachedCodeBlockInterpreter(block, Unit::class.java)
        interpreters += interpreter
    }

    fun addFunction(block: CustomBlock) {
        functions[block.function] = block
    }

    fun launch() {
        val loader = scope.launch {
            onLoad.forEach {
                if (this.isActive) {
                    it()
                }
            }
        }

        interpreters.forEach {
            scope.launch {
                loader.join()
                it.execute(this@BlockContext)
            }
        }
    }

    fun save(tag: CompoundTag) {
        val context = CompoundTag()
        interpreters.forEach {
            context.put(it.rootUUID.toString(), CompoundTag().apply(it::serialize))
        }
        val variables = CompoundTag()
        this.variables.forEach { (key, container) ->
            val tag = CompoundTag().apply(container::save)
            tag.putString("type", container.type)
            variables.put(key, tag)
        }
        tag.put("context", context)
        tag.put("variables", variables)
    }

    fun load(tag: CompoundTag) {
        val context = tag.getCompound("context")
        context.allKeys.forEach { key ->
            val uuid = UUID.fromString(key)
            interpreters.find { it -> it.rootUUID == uuid }?.deserialize(context.getCompound(key))
        }
        val variables = tag.getCompound("variables")
        variables.allKeys.forEach { key ->
            val tag = variables.getCompound(key)
            val type = tag.getString("type")
            this.variables[key] = when (type) {
                "hollowengine:serializable_value" ->
                    SerializableVariableContainer(Int.serializer()) //TODO: fix generic serialization
                "hollowengine:living_entity" -> LivingEntityContainer<LivingEntity>()
                else -> error("Unknown variable container type: $type")
            }
            onLoad += {
                this.variables[key]?.load(tag)
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun createScript(file: File, server: MinecraftServer = currentServer): BlockContext {
    val repository = BlockRepository.create("Скрипт") {
        include(StandardModules.AllBasics)
        include(NPCModule)
        include(EntityModule)
        include(WorldModule)
        include(PlayerModule)
    }
    val format = CodeBlockFormat(repository)
    val blocks = format.json.decodeFromStream(CodeBlockSerializer(format), file.inputStream())
    return createScript(server, blocks, file.toReadablePath())
}

fun createScript(server: MinecraftServer, rootBlocks: List<BlockModel>, file: String): BlockContext {
    val context = BlockContext(CoroutineScope(server.dispatcher + SupervisorJob()), file)
    rootBlocks.filterIsInstance<StatementBlock>().forEach { context.addBlock(it) }
    rootBlocks.filterIsInstance<CustomBlock>().forEach { context.addFunction(it) }
    return context
}