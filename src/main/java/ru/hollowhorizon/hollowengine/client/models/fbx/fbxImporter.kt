package ru.hollowhorizon.hollowengine.client.models.fbx

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.manager.ModelLoader
import ru.hollowhorizon.hollowengine.client.models.util.startsWith
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FbxModelLoader: ModelLoader {
    override val supportedFormats = setOf("fbx")

    override suspend fun load(location: ResourceLocation): AnimatedModel {
        return AnimatedModel(import(location).convert(location))
    }

    fun import(location: ResourceLocation): Document {
        val bytes = ByteBuffer.wrap(location.stream.readBytes()).order(ByteOrder.nativeOrder())

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
}

lateinit var buffer: ByteBuffer
