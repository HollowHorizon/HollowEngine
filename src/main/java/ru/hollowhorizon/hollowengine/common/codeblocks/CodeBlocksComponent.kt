package ru.hollowhorizon.hollowengine.common.codeblocks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.EndBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.*
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockSerializer
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
    rootBlocks.filterIsInstance<StatementBlock>().forEach {
        if (it is StartBlock && it !is EndBlock) context.addBlock(it)
        if (it is CustomBlock) context.addFunction(it)
    }
    return context
}