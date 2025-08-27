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

package ru.hollowhorizon.hollowengine.common.utils

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ItemStack
import java.util.*

fun ItemStack.getArmorTexture(entity: Entity, slot: EquipmentSlot): ResourceLocation {
    val item = item as ArmorItem
    //? if >=1.21 {
    /*var texture = item.material.registeredName
    *///?} else {
    var texture = item.material.name
    //?}
    var domain = "minecraft"
    val idx = texture.indexOf(':')
    if (idx != -1) {
        domain = texture.substring(0, idx)
        texture = texture.substring(idx + 1)
    }
    val path = String.format(
        Locale.ROOT, "%s:textures/models/armor/%s_layer_%d%s.png", domain, texture,
        (if (slot == EquipmentSlot.LEGS) 2 else 1), ""
    )

    return path.rl
}