package ru.hollowhorizon.hollowengine.common.codeblocks.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import ru.hollowhorizon.hollowengine.common.codeblocks.flatten
import ru.hollowhorizon.hollowengine.common.codeblocks.isRoot
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

import java.util.*

class CodeBlockSerializer(val format: CodeBlockFormat) : KSerializer<List<BlockModel>> {
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

        val nodeMap = mutableMapOf<UUID, BlockModel>()
        val jsonMap = mutableMapOf<UUID, JsonObject>()

        jsonArray.forEachIndexed { index, element ->
            val jsonObject = element.jsonObject

            val nodeElement = jsonObject["node"]
                ?: throw SerializationException("Block at index $index missing 'node' field")

            val block = try {
                format.json.decodeFromJsonElement<BlockModel>(nodeElement)
            } catch (e: Exception) {
                throw SerializationException("Failed to decode block at index $index: ${e.message}", e)
            }

            nodeMap[block.uuid] = block
            jsonMap[block.uuid] = jsonObject
        }

        jsonMap.forEach { (uuid, jsonObject) ->
            val currentBlock = nodeMap[uuid]!!

            val nextIdStr = jsonObject["next"]?.jsonPrimitive?.content
            if (nextIdStr != null) {
                val nextUuid = UUID.fromString(nextIdStr)
                val nextBlock = nodeMap[nextUuid]
                    ?: throw SerializationException("Block $uuid refers to missing next block $nextUuid")

                currentBlock as StatementBlock
                nextBlock as StatementBlock

                currentBlock.next = nextBlock
                nextBlock.parent = currentBlock
            }

            val inputsObj = jsonObject["inputs"]?.jsonObject
            inputsObj?.forEach { (slotName, uuidElement) ->
                val inputUuid = UUID.fromString(uuidElement.jsonPrimitive.content)
                val inputBlock = nodeMap[inputUuid]
                    ?: throw SerializationException("Block $uuid input '$slotName' refers to missing block $inputUuid")

                currentBlock.inputs[slotName] = inputBlock
                inputBlock.parentBlock = currentBlock
                inputBlock.parentInputName = slotName
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

        return nodeMap.values.filter { it.isRoot }
    }
}