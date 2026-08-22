package ru.hollowhorizon.hollowengine.client.models.fbx

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.manager.ModelLoader
import ru.hollowhorizon.hollowengine.client.models.internal.manager.ModelSide
import ru.hollowhorizon.hollowengine.client.models.util.startsWith
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.models.ModelResourceIO
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FbxModelLoader: ModelLoader {
    override val supportedFormats = setOf("fbx")

    override suspend fun load(location: ResourceLocation, side: ModelSide): Model {
        return import(location, side).convert(location)
    }

    fun import(location: ResourceLocation, side: ModelSide = ModelSide.CLIENT): Document {
        val bytes = ByteBuffer.wrap(location.readModelBytes(side)).order(ByteOrder.nativeOrder())

        val tokens = ArrayList<Token>()
        buffer = bytes

        var isBinary = false
        if (bytes.startsWith("Kaydara FBX Binary")) {
            isBinary = true
            tokenizeBinary(tokens, bytes)
        } else tokenize(tokens, bytes)

        val parser = Parser(tokens, isBinary)

        return Document(parser)
    }

    private fun ResourceLocation.readModelBytes(side: ModelSide): ByteArray =
        when (side) {
            ModelSide.CLIENT -> stream.readBytes()
            ModelSide.SERVER -> ModelResourceIO.open(this).use { it.readBytes() }
        }
}

lateinit var buffer: ByteBuffer
