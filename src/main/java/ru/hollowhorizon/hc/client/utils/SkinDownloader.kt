/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hc.client.utils

import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.HollowCore.MODID
import ru.hollowhorizon.hc.common.utils.ModList
import ru.hollowhorizon.hc.common.utils.rl
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