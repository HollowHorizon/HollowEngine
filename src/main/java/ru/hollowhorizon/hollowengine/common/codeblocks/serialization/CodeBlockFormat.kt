package ru.hollowhorizon.hollowengine.common.codeblocks.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BoolBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.PrintBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.StringValueBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.WhileBlock

object CodeBlockFormat {
    private val module = SerializersModule {
        // Немного позже добавлю автоматическое сканирование/генерацию
        polymorphic(CodeBlock::class) {
            subclass(PrintBlock::class, PrintBlock.serializer())
            subclass(StringValueBlock::class, StringValueBlock.serializer())
            subclass(WhileBlock::class, WhileBlock.serializer())
            subclass(BoolBlock::class, BoolBlock.serializer())
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    val Json = Json {
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "

        serializersModule = module
    }
}