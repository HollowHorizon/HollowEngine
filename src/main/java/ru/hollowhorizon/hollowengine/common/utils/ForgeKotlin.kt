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

import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcher
import ru.hollowhorizon.hollowengine.client.utils.registryAccess
import ru.hollowhorizon.hollowengine.common.capabilities.CapabilityInstance
import kotlin.reflect.KClass

/**
 * Checks if the game is running in a production environment.
 *
 * @return `true` if the game is in production mode, otherwise `false`.
 */
val isProduction: Boolean
    get() {
        //? if neoforge {
        /*return net.neoforged.fml.loading.FMLLoader.isProduction()
        *///?} elif forge {
        /*return net.minecraftforge.fml.loading.FMLLoader.isProduction()
        *///?} else {
        return !net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment
        //?}
    }
/**
 * Checks if the current thread is the logical client thread.
 *
 * @return `true` if running on the logical client thread, otherwise `false`.
 */
val isLogicalClient get() = isPhysicalClient && RenderSystem.isOnRenderThread()
/**
 * Determines if the game is running on a physical client.
 *
 * @return `true` if running on the client side, otherwise `false`.
 */
val isPhysicalClient: Boolean
    get() {
        //? if neoforge {
        /*return net.neoforged.fml.loading.FMLLoader.getDist().isClient
        *///?} elif forge {
        /*return net.minecraftforge.fml.loading.FMLLoader.getDist().isClient
        *///?} else {
        return net.fabricmc.loader.api.FabricLoader.getInstance().environmentType == net.fabricmc.api.EnvType.CLIENT
        //?}
    }

val RANDOM = RandomSource.create()

/**
 * Stores the current Minecraft server instance.
 */
lateinit var currentServer: MinecraftServer

/**
 * Retrieves a capability instance from an object implementing [ICapabilityDispatcher].
 *
 * @param capability The capability class type.
 * @return The instance of the requested capability.
 * @throws IllegalStateException if the capability is not supported.
 */
operator fun <O, T : CapabilityInstance> O.get(capability: KClass<T>): T = get(capability.java)

@Suppress("UNCHECKED_CAST")
operator fun <O, T : CapabilityInstance> O.get(capability: Class<T>): T = when (this) {
    is ICapabilityDispatcher -> this.capabilities[capability.name] as T
    else -> throw IllegalStateException("Unsupported capability type: $capability")
}

/**
 * Converts a string to a Minecraft resource location.
 */
val String.rl: ResourceLocation get() =
    //? if >= 1.21 {
    /*ResourceLocation.parse(this)
    *///?} else {
    ResourceLocation.tryParse(this) ?: error("Unsupported string format: $this")
    //?}

/**
 * Converts a string to a literal Minecraft text component.
 *
 * @return A MutableComponent representing the literal text.
 */
@Deprecated("Use String.literal instead.", ReplaceWith("this.literal"))
val String.mcText: MutableComponent get() = Component.literal(this)
val String.literal: MutableComponent get() = Component.literal(this)
val String.mcTranslate: MutableComponent get() = Component.translatable(this)
fun String.mcTranslate(vararg args: Any) = Component.translatable(this, *args)

/**
 * Appends one part to another.
 */
operator fun MutableComponent.plus(other: Component): MutableComponent = this.copy().append(other)
operator fun MutableComponent.plus(text: String): MutableComponent = this.copy().append(text)

// Additional helper methods for text formatting and interaction
fun MutableComponent.colored(color: Int): MutableComponent = this.withStyle { it.withColor(color) }
fun MutableComponent.colored(color: ChatFormatting): MutableComponent = this.withStyle { it.withColor(color) }
fun MutableComponent.bold(): MutableComponent = this.withStyle { it.withBold(true) }
fun MutableComponent.italic(): MutableComponent = this.withStyle { it.withItalic(true) }
fun MutableComponent.obfuscated(): MutableComponent = this.withStyle { it.withObfuscated(true) }
fun MutableComponent.underlined(): MutableComponent = this.withStyle { it.withUnderlined(true) }
fun MutableComponent.strikethrough(): MutableComponent = this.withStyle { it.withStrikethrough(true) }
fun MutableComponent.font(font: ResourceLocation) = this.withStyle { it.withFont(font) }
fun MutableComponent.onClickUrl(url: String): MutableComponent =
    this.withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url)) }

fun MutableComponent.onClickCommand(command: String): MutableComponent =
    this.withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command)) }

fun MutableComponent.onClickSuggestion(command: String): MutableComponent =
    this.withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)) }

fun MutableComponent.onClickCopy(text: String): MutableComponent =
    this.withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text)) }

fun MutableComponent.onHoverText(text: Component): MutableComponent =
    this.withStyle { it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, text)) }

fun MutableComponent.onHoverText(text: String) = onHoverText(Component.literal(text))
fun MutableComponent.onHoverItem(item: ItemStack) =
    this.withStyle { it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackInfo(item))) }

fun MutableComponent.onHoverEntity(entity: Entity) = this.withStyle {
    it.withHoverEvent(
        HoverEvent(
            HoverEvent.Action.SHOW_ENTITY,
            HoverEvent.EntityTooltipInfo(entity.type, entity.uuid, entity.name)
        )
    )
}

/**
 * Memoizes a function, caching its results to improve performance.
 *
 * @return A memoized version of the function.
 */
fun <A, B> ((A) -> B).memoize(): (A) -> B {
    val cache: MutableMap<A, B> = Object2ObjectOpenHashMap()
    return {
        cache.getOrPut(it) { this(it) }
    }
}

/**
 * Saves an ItemStack to a CompoundTag.
 *
 * @return A CompoundTag representing the saved ItemStack.
 */
fun ItemStack.save() = CompoundTag().apply {
    //? if >= 1.21 {
    /*if(!isEmpty) save(registryAccess)
    *///?} else {
    this@save.save(this)
    //?}
}

/**
 * Reads an ItemStack from a CompoundTag.
 *
 * @return An ItemStack instance loaded from the CompoundTag.
 */
fun CompoundTag.readItem() =
    //? if >= 1.21 {
    /*if(isEmpty) ItemStack.EMPTY else ItemStack.parse(registryAccess, this).orElseThrow()
    *///?} else {
    ItemStack.of(this)
    //?}
