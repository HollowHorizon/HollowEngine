package ru.hollowhorizon.hollowengine.client.utils

import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowCore.MODID
import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.io.File
import java.net.URL
import java.util.*

object SkinDownloader {
    fun downloadSkin(skin: String): ResourceLocation {
        val hasHollowEngine = ModList.isLoaded("hollowengine")
        val mod = if (hasHollowEngine) "hollowengine" else MODID
        val textureLocation = "$mod:skins/${skin.lowercase()}.png".rl
        val original =
            Minecraft.getInstance().textureManager.getTexture(textureLocation, MissingTextureAtlasSprite.getTexture())

        if (original == MissingTextureAtlasSprite.getTexture()) {
            val url = "https://skins.danielraybone.com/v1/profile/$skin"
            val connection = URL(url).openConnection()
            val text = connection.getInputStream().bufferedReader().readText()
            val base64 =
                JsonParser.parseString(text).asJsonObject["assets"].asJsonObject["skin"].asJsonObject["base64"].asString
            val textureJson = Base64.getDecoder().decode(base64)

            Minecraft.getInstance().textureManager.register(
                textureLocation, DynamicTexture(
                    NativeImage.read(textureJson.inputStream())
                )
            )
            if (hasHollowEngine) {
                File(".")
                    .resolve("hollowengine/assets/hollowengine/textures/skins/${skin.lowercase()}.png")
                    .apply {
                        parentFile?.mkdirs()
                    }.writeBytes(textureJson)
            }
        }
        return textureLocation
    }
}