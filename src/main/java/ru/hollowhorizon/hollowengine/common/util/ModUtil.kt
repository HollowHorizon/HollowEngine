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

package ru.hollowhorizon.hollowengine.common.util

import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.moddiscovery.ModInfo
import ru.hollowhorizon.hollowengine.HollowEngine

object ModUtil {
    fun updateModNames() {
        val optionalMod = ModList.get().getModContainerById(HollowEngine.MODID)

        if (!optionalMod.isPresent) return

        val mod = optionalMod.get().modInfo

        if (mod.displayName != "HollowEngine Legacy") {
            val displayNameSetter = ModInfo::class.java.getDeclaredField("displayName")

            displayNameSetter.isAccessible = true
            displayNameSetter[mod] = "HollowEngine Legacy"
        }
    }
}