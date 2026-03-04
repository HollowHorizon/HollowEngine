package ru.hollowhorizon.hollowengine.common.codeblocks.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BrokenExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BrokenStatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.flatten
import ru.hollowhorizon.hollowengine.common.codeblocks.isRoot
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.*
import java.util.*

class CodeBlockSerializer(
    private val format: CodeBlockFormat,
    private val policy: ScriptRecoveryPolicy = ScriptRecoveryPolicy.strict(),
    private val issuesCollector: MutableList<ScriptLoadIssue>? = null,
) : KSerializer<List<BlockModel>> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: List<BlockModel>) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("This serializer only works with JSON")

        val allBlocks = value.flatMap { it.flatten() }

        val jsonArray = JsonArray(allBlocks.map { block ->
            buildJsonObject {
                put("node", format.json.encodeToJsonElement(block))

                (block as? StatementBlock)?.next?.let {
                    put("next", JsonPrimitive(it.uuid.toString()))
                }

                if (block.inputs.isNotEmpty()) {
                    put("inputs", buildJsonObject {
                        block.inputs.forEach { (slotName, connectedBlock) ->
                            put(slotName, JsonPrimitive(connectedBlock.uuid.toString()))
                        }
                    })
                }
                if (block.outputs.isNotEmpty()) {
                    put("outputs", buildJsonObject {
                        block.outputs.forEach { (slotName, connectedBlock) ->
                            put(slotName, JsonPrimitive(connectedBlock.uuid.toString()))
                        }
                    })
                }

                if (block.isRoot) {
                    put("x", JsonPrimitive(block.positionX.value))
                    put("y", JsonPrimitive(block.positionY.value))
                }
                put("isCollapsed", JsonPrimitive(block.isCollapsed.value))
            }
        })

        jsonEncoder.encodeJsonElement(jsonArray)
    }

    override fun deserialize(decoder: Decoder): List<BlockModel> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer only works with JSON")

        val jsonArray = jsonDecoder.decodeJsonElement() as? JsonArray
            ?: throw SerializationException("Expected JsonArray of blocks")

        val nodeMap = linkedMapOf<UUID, BlockModel>()
        val jsonMap = linkedMapOf<UUID, JsonObject>()

        jsonArray.forEachIndexed { index, element ->
            val jsonObject = element as? JsonObject ?: return@forEachIndexed
            val nodeElement = jsonObject["node"]

            if (nodeElement == null) {
                when (policy.decodeFailureStrategy) {
                    DecodeFailureStrategy.FAIL -> throw SerializationException("Block at index $index missing 'node' field")
                    DecodeFailureStrategy.DROP_BLOCK, DecodeFailureStrategy.REPLACE_WITH_STUB -> {
                        addIssue(
                            ScriptLoadIssue.Kind.MISSING_NODE_FIELD,
                            "Block at index $index is missing 'node' field",
                            RecoveryAction.DROPPED_BLOCK
                        )
                        return@forEachIndexed
                    }
                }
            }

            val block = try {
                format.json.decodeFromJsonElement<BlockModel>(nodeElement!!)
            } catch (e: Exception) {
                handleDecodeFailure(nodeElement, index, e) ?: return@forEachIndexed
            }

            nodeMap[block.uuid] = block
            jsonMap[block.uuid] = jsonObject
        }

        fun createMissingStatementStub(owner: UUID, missing: UUID): StatementBlock {
            val stub = BrokenStatementBlock(
                reason = "Missing referenced statement block $missing",
                originalType = "missing_ref"
            )
            stub.uuid = missing
            nodeMap[missing] = stub
            addIssue(
                ScriptLoadIssue.Kind.MISSING_NEXT_BLOCK,
                "Block $owner references missing next block $missing",
                RecoveryAction.REPLACED_WITH_STUB,
                owner,
                missing
            )
            return stub
        }

        fun createMissingExpressionStub(owner: UUID, slot: String, missing: UUID): BlockModel {
            val stub = BrokenExpressionBlock(
                reason = "Missing input '$slot' block $missing",
                originalType = "missing_ref"
            )
            stub.uuid = missing
            nodeMap[missing] = stub
            addIssue(
                ScriptLoadIssue.Kind.MISSING_INPUT_BLOCK,
                "Block $owner input '$slot' references missing block $missing",
                RecoveryAction.REPLACED_WITH_STUB,
                owner,
                missing
            )
            return stub
        }

        jsonMap.forEach { (uuid, jsonObject) ->
            val currentBlock = nodeMap[uuid] ?: return@forEach

            val nextIdStr = jsonObject["next"]?.jsonPrimitive?.contentOrNull
            if (nextIdStr != null) {
                val nextUuid = parseUuid(nextIdStr)
                if (nextUuid == null) {
                    addIssue(
                        ScriptLoadIssue.Kind.INVALID_REFERENCE_FORMAT,
                        "Block $uuid has invalid 'next' reference: '$nextIdStr'",
                        RecoveryAction.REMOVED_REFERENCE,
                        uuid
                    )
                } else {
                    val nextBlock = nodeMap[nextUuid] ?: when (policy.missingReferenceStrategy) {
                        MissingReferenceStrategy.FAIL ->
                            throw SerializationException("Block $uuid refers to missing next block $nextUuid")

                        MissingReferenceStrategy.REMOVE_REFERENCE -> {
                            addIssue(
                                ScriptLoadIssue.Kind.MISSING_NEXT_BLOCK,
                                "Block $uuid references missing next block $nextUuid",
                                RecoveryAction.REMOVED_REFERENCE,
                                uuid,
                                nextUuid
                            )
                            null
                        }

                        MissingReferenceStrategy.REPLACE_WITH_STUB -> createMissingStatementStub(uuid, nextUuid)
                    }

                    if (nextBlock != null) {
                        if (currentBlock is StatementBlock && nextBlock is StatementBlock) {
                            currentBlock.next = nextBlock
                            nextBlock.parent = currentBlock
                        } else {
                            if (policy.missingReferenceStrategy == MissingReferenceStrategy.FAIL) {
                                throw SerializationException("Invalid next reference type for block $uuid")
                            }
                            addIssue(
                                ScriptLoadIssue.Kind.INVALID_NEXT_BLOCK_TYPE,
                                "Block $uuid has non-statement next reference $nextUuid",
                                RecoveryAction.REMOVED_REFERENCE,
                                uuid,
                                nextUuid
                            )
                        }
                    }
                }
            }

            val inputsObj = jsonObject["inputs"]?.jsonObject
            inputsObj?.forEach { (slotName, uuidElement) ->
                val inputUuidRaw = uuidElement.jsonPrimitive.contentOrNull ?: return@forEach
                val inputUuid = parseUuid(inputUuidRaw)
                if (inputUuid == null) {
                    addIssue(
                        ScriptLoadIssue.Kind.INVALID_REFERENCE_FORMAT,
                        "Block $uuid input '$slotName' has invalid reference '$inputUuidRaw'",
                        RecoveryAction.REMOVED_REFERENCE,
                        uuid
                    )
                    return@forEach
                }

                val inputBlock = nodeMap[inputUuid] ?: when (policy.missingReferenceStrategy) {
                    MissingReferenceStrategy.FAIL ->
                        throw SerializationException("Block $uuid input '$slotName' refers to missing block $inputUuid")

                    MissingReferenceStrategy.REMOVE_REFERENCE -> {
                        addIssue(
                            ScriptLoadIssue.Kind.MISSING_INPUT_BLOCK,
                            "Block $uuid input '$slotName' refers to missing block $inputUuid",
                            RecoveryAction.REMOVED_REFERENCE,
                            uuid,
                            inputUuid
                        )
                        null
                    }

                    MissingReferenceStrategy.REPLACE_WITH_STUB -> createMissingExpressionStub(uuid, slotName, inputUuid)
                }

                if (inputBlock != null) {
                    currentBlock.inputs[slotName] = inputBlock
                    inputBlock.parentBlock = currentBlock
                    inputBlock.parentInputName = slotName
                    inputBlock.parentOutputName = null
                }
            }

            val outputsObj = jsonObject["outputs"]?.jsonObject
            outputsObj?.forEach { (slotName, uuidElement) ->
                val outputUuidRaw = uuidElement.jsonPrimitive.contentOrNull ?: return@forEach
                val outputUuid = parseUuid(outputUuidRaw)
                if (outputUuid == null) {
                    addIssue(
                        ScriptLoadIssue.Kind.INVALID_REFERENCE_FORMAT,
                        "Block $uuid output '$slotName' has invalid reference '$outputUuidRaw'",
                        RecoveryAction.REMOVED_REFERENCE,
                        uuid
                    )
                    return@forEach
                }

                val outputBlock = nodeMap[outputUuid] ?: when (policy.missingReferenceStrategy) {
                    MissingReferenceStrategy.FAIL ->
                        throw SerializationException("Block $uuid output '$slotName' refers to missing block $outputUuid")

                    MissingReferenceStrategy.REMOVE_REFERENCE -> {
                        addIssue(
                            ScriptLoadIssue.Kind.MISSING_INPUT_BLOCK,
                            "Block $uuid output '$slotName' refers to missing block $outputUuid",
                            RecoveryAction.REMOVED_REFERENCE,
                            uuid,
                            outputUuid
                        )
                        null
                    }

                    MissingReferenceStrategy.REPLACE_WITH_STUB -> createMissingExpressionStub(uuid, slotName, outputUuid)
                }

                if (outputBlock != null) {
                    currentBlock.outputs[slotName] = outputBlock
                    outputBlock.parentBlock = currentBlock
                    outputBlock.parentInputName = null
                    outputBlock.parentOutputName = slotName
                }
            }

            jsonObject["x"]?.jsonPrimitive?.floatOrNull?.let { x ->
                currentBlock.positionX.set(x)
            }
            jsonObject["y"]?.jsonPrimitive?.floatOrNull?.let { y ->
                currentBlock.positionY.set(y)
            }
            jsonObject["isCollapsed"]?.jsonPrimitive?.booleanOrNull?.let { isCollapsed ->
                currentBlock.isCollapsed.set(isCollapsed)
            }
        }

        return nodeMap.values.filter { it.isRoot }.distinctBy { it.uuid }
    }

    private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()

    private fun handleDecodeFailure(nodeElement: JsonElement?, index: Int, e: Exception): BlockModel? {
        val nodeObj = nodeElement as? JsonObject
        val rawUuid = nodeObj?.get("uuid")?.jsonPrimitive?.contentOrNull
        val uuid = rawUuid?.let(::parseUuid) ?: UUID.randomUUID()
        val originalType = nodeObj?.get("type")?.jsonPrimitive?.contentOrNull
        val message = "Failed to decode block at index $index: ${e.message}"

        return when (policy.decodeFailureStrategy) {
            DecodeFailureStrategy.FAIL -> throw SerializationException(message, e)
            DecodeFailureStrategy.DROP_BLOCK -> {
                addIssue(
                    ScriptLoadIssue.Kind.DECODE_FAILED,
                    message,
                    RecoveryAction.DROPPED_BLOCK,
                    ownerBlockId = uuid
                )
                null
            }

            DecodeFailureStrategy.REPLACE_WITH_STUB -> {
                addIssue(
                    ScriptLoadIssue.Kind.DECODE_FAILED,
                    message,
                    RecoveryAction.REPLACED_WITH_STUB,
                    ownerBlockId = uuid
                )
                BrokenStatementBlock(reason = message, originalType = originalType).also { it.uuid = uuid }
            }
        }
    }

    private fun addIssue(
        kind: ScriptLoadIssue.Kind,
        message: String,
        action: RecoveryAction,
        ownerBlockId: UUID? = null,
        targetBlockId: UUID? = null,
    ) {
        issuesCollector?.add(
            ScriptLoadIssue(
                kind = kind,
                message = message,
                action = action,
                ownerBlockId = ownerBlockId,
                targetBlockId = targetBlockId
            )
        )
    }
}
