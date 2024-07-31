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

package ru.hollowhorizon.hollowengine.common.scripting

import net.darkhax.gamestages.GameStageHelper
import net.minecraft.ChatFormatting
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.SeatContainer
import ru.hollowhorizon.hollowengine.common.scripting.story.progressManager
import ru.hollowhorizon.hollowengine.common.util.SafeGetter
import ru.hollowhorizon.hollowengine.common.util.filter
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun main() {
    val book = item(
        "minecraft:written_book",
        1,
        "{author:\"Dev\",filtered_title:\"Hollow\",pages:['{\"text\":\"Hello...\"}','{\"text\":\"There is letters\"}','{\"text\":\"на русском тоже!\\n\\n\\nда\"}'],title:\"Hollow\"}"
    )
}

fun AnimatedEntityCapability.skin(name: String) = "skins/$name"

val MinecraftServer.players get() = SafeGetter(playerList::getPlayers)

val SeatContainer.north: Direction
    get() = Direction.SOUTH

val SeatContainer.south: Direction
    get() = Direction.NORTH

val SeatContainer.west: Direction
    get() = Direction.EAST

val SeatContainer.east: Direction
    get() = Direction.WEST

fun ServerPlayer.hasStage(stage: String) = GameStageHelper.hasStage(this, stage)

fun ServerPlayer.addStage(stage: String) = GameStageHelper.addStage(this, stage)
fun ServerPlayer.removeStage(stage: String) = GameStageHelper.removeStage(this, stage)
val ServerPlayer.stages get() = GameStageHelper.getPlayerData(this)?.stages ?: emptyList()

val ServerPlayer.stringName: String get() = this.displayName.string

fun StoryStateMachine.test() {
    val players by server.players.filter { it.hasStage("progressable") }

    players.progressManager.addMessage { "Напиши привет в чат!" }
}

fun String.color(color: Int): Component {
    val tr = this.mcTranslate
    val style = tr.style.withColor(color)
    return ComponentUtils.mergeStyles(tr, style)
}

fun String.color(color: String): Component {
    val tr = this.mcTranslate
    val style = tr.style.withColor(Integer.valueOf(color))
    return ComponentUtils.mergeStyles(tr, style)
}

val String.bold: Component
    get() {
        val tr = this.mcTranslate
        val style = tr.style.withBold(true)
        return ComponentUtils.mergeStyles(tr, style)
    }

val String.italic: Component
    get() {
        val tr = this.mcTranslate
        val style = tr.style.withItalic(true)
        return ComponentUtils.mergeStyles(tr, style)
    }

val String.obfuscated: Component
    get() {
        val tr = this.mcTranslate
        val style = tr.style.withObfuscated(true)
        return ComponentUtils.mergeStyles(tr, style)
    }

val String.strikethrough: Component
    get() {
        val tr = this.mcTranslate
        val style = tr.style.withStrikethrough(true)
        return ComponentUtils.mergeStyles(tr, style)
    }

val String.underline: String
    get() {
        val tr = this.mcTranslate
        val style = tr.style.withUnderlined(true)
        return ComponentUtils.mergeStyles(tr, style).string
    }

fun item(item: String, count: Int = 1, nbt: CompoundTag? = null) = ItemStack(
    ForgeRegistries.ITEMS.getValue(item.rl) ?: throw IllegalStateException("Item $item not found!"),
    count,
    nbt
)

fun item(item: String, count: Int = 1, nbt: String): ItemStack {
    return item(item, count, TagParser.parseTag(nbt))
}

fun tag(tag: String): TagKey<Item> {
    val manager = ForgeRegistries.ITEMS.tags() ?: throw IllegalStateException("Tag $tag not found!")
    return manager.createTagKey(tag.rl)
}

fun <T> runtime(default: () -> T) = RuntimeVariable(default)
fun <T> runtime() =
    RuntimeVariable<T> { throw IllegalStateException("Default value not found, runtime property does not exists") }

val RUNTIME_PROPERTIES = mutableMapOf<String, Any?>()

class RuntimeVariable<T>(val default: () -> T) : ReadWriteProperty<Any?, T> {

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return RUNTIME_PROPERTIES.computeIfAbsent(property.name) { default() } as T
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        RUNTIME_PROPERTIES[property.name] = value
    }
}
