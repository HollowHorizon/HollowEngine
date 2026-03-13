@file:OptIn(ExperimentalSerializationApi::class)

package ru.hollowhorizon.hollowengine.common.codeblocks.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategory
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockProvider
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BrokenExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BrokenStatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CallCustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.EventOutputVariableBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.GetVarInlineBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.ScriptLoadIssue
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.ScriptLoadReport
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.ScriptRecoveryPolicy
import java.io.File
import java.io.InputStream
import kotlin.reflect.KClass

class CodeBlockFormat(
    val blockModule: BlockProvider,
    val recoveryPolicy: ScriptRecoveryPolicy = ScriptRecoveryPolicy.lenient(),
) {
    fun loadBlocks(file: File): List<BlockModel> = file.inputStream().use { loadBlocks(it) }

    fun loadBlocks(stream: InputStream): List<BlockModel> =
        json.decodeFromStream(CodeBlockSerializer(this, ScriptRecoveryPolicy.strict()), stream)

    fun loadBlocksWithRecovery(file: File): ScriptLoadReport = file.inputStream().use { loadBlocksWithRecovery(it) }

    fun loadBlocksWithRecovery(stream: InputStream): ScriptLoadReport {
        val issues = mutableListOf<ScriptLoadIssue>()
        val blocks = json.decodeFromStream(CodeBlockSerializer(this, recoveryPolicy, issues), stream)
        return ScriptLoadReport(blocks, issues)
    }

    fun encodeBlocks(blocks: List<BlockModel>): String =
        json.encodeToString(CodeBlockSerializer(this), blocks)

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

            // These blocks are created dynamically and should always be resolvable.
            subclass(GetVarInlineBlock::class)
            subclass(CallCustomBlock::class)
            subclass(EventOutputVariableBlock::class)
            subclass(BrokenStatementBlock::class)
            subclass(BrokenExpressionBlock::class)
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
