package ru.hollowhorizon.hollowengine.common.codeblocks.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategory
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockProvider
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import kotlin.reflect.KClass

class CodeBlockFormat(val blockModule: BlockProvider) {
    @OptIn(InternalSerializationApi::class)
    private val module = SerializersModule {
        polymorphic(CodeBlock::class) {
            fun appendCategory(category: BlockCategory) {
                category.blocks.forEach { block ->
                    subclass(block.type as KClass<CodeBlock>, block.type.serializer())
                }
                category.subCategories.forEach { subcategory ->
                    appendCategory(subcategory)
                }
            }
            appendCategory(blockModule.rootCategory)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "

        serializersModule = module
    }
}