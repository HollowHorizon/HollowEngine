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

package ru.hollowhorizon.hc.client.sounds

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.packs.resources.IoSupplier
import net.minecraft.server.packs.resources.ResourceManager
import ru.hollowhorizon.hc.HollowCore
import java.io.InputStream

object HollowSoundHandler {
    val MODS = hashSetOf<String>()

    @JvmStatic
    fun createJson(manager: ResourceManager, modId: String): IoSupplier<InputStream> {
        val soundsJson = JsonObject()

        manager.listResources("sounds") {
            it.namespace == modId && it.path.endsWith(".ogg")
        }.map {
            it.key
        }.forEach { location ->
            val soundName = location.path.substringAfter("sounds/").substringBeforeLast(".")
            soundsJson.add(soundName, JsonObject().apply {
                addProperty("category", "master")
                add(
                    "sounds",
                    JsonArray().apply {
                        add(
                            location.namespace + ":" + location.path.substringAfter("sounds/").substringBeforeLast(".")
                        )
                    })
            })
        }

        HollowCore.LOGGER.info("Creating sounds.json for $modId: $soundsJson")
        return IoSupplier { soundsJson.toString().byteInputStream() }
    }
}