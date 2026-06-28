package ru.hollowhorizon.hollowengine.common.scripting

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.scripting.annotations.Import
import ru.hollowhorizon.hollowengine.common.scripting.components.ComponentScript
import ru.hollowhorizon.hollowengine.common.scripting.reload.ReloadScript
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider as Provider

object DefaultScriptDefinitions {
    fun providers(): List<Provider> {
        return buildList {
            this += Provider(
                "kts", "kotlin.Any", defaultImports = listOf(
                    Import::class.qualifiedName!!
                )
            )
            this += Provider(
                extension = "reload.kts",
                baseClass = ReloadScript::class.qualifiedName!!,
                defaultImports = listOf(
                    "ru.hollowhorizon.hollowengine.common.scripting.annotations.*",
                    ResourceLocation::class.qualifiedName!!,
                    ItemStack::class.qualifiedName!!,
                    SubscribeEvent::class.qualifiedName!!,
                    Import::class.qualifiedName!!,
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.item",
                    "ru.hollowhorizon.hollowengine.common.utils.rl",
                )
            )
            this += Provider(
                extension = "node.kts",
                baseClass = ComponentScript::class.qualifiedName!!,
                defaultImports = listOf(
                    "ru.hollowhorizon.hollowengine.common.coroutines.*",
                    "ru.hollowhorizon.hollowengine.common.scripting.annotations.*",
                    "kotlinx.coroutines.delay",
                    "kotlinx.coroutines.yield",
                    ResourceLocation::class.qualifiedName!!,
                    "net.minecraft.nbt.CompoundTag",
                    "net.minecraft.world.entity.Entity",
                    "net.minecraft.world.entity.LivingEntity",
                    "net.minecraft.world.entity.player.Player",
                    ItemStack::class.qualifiedName!!,
                    "net.minecraft.world.phys.Vec3",
                    "net.minecraft.core.BlockPos",
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.*",
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects.*",
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.*",
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.*",
                    "ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode",
                ),
                implicitReceivers = listOf(
                    MinecraftServer::class
                )
            )
        }
    }
}
