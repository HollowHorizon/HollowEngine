@file:OptIn(ExperimentalSerializationApi::class)

package ru.hollowhorizon.hollowengine.common.codeblocks.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategory
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockProvider
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CallCustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.GetEntityVarBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.GetGlobalVarBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.GetVarInlineBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import java.io.File
import java.io.InputStream
import kotlin.reflect.KClass

class CodeBlockFormat(val blockModule: BlockProvider) {
    fun loadBlocks(file: File): List<BlockModel> = file.inputStream().use { loadBlocks(it) }
    fun loadBlocks(stream: InputStream): List<BlockModel> = json.decodeFromStream(CodeBlockSerializer(this), stream)

    @OptIn(InternalSerializationApi::class)
    private val module = SerializersModule {
        polymorphic(BlockModel::class) {
            fun appendCategory(category: BlockCategory) {
                category.blocks.forEach { block ->
                    subclass(block.type as KClass<BlockModel>, block.type.serializer())
                }
                category.subCategories.forEach { subcategory ->
                    appendCategory(subcategory)
                }
            }
            appendCategory(blockModule.rootCategory)

            // Эти блоки добавляются динамически
            subclass(GetVarInlineBlock::class)
            subclass(GetGlobalVarBlock::class)
            subclass(GetEntityVarBlock::class)
            subclass(CallCustomBlock::class)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "

        serializersModule = module
    }
}