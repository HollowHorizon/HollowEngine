package ru.hollowhorizon.hollowengine.common.scripting

import ru.hollowhorizon.hollowengine.common.scripting.reload.ReloadScript
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryScript

object DefaultScriptDefinitions {
    fun providers(): List<ScriptClassProvider> {
        return listOf(
            ScriptClassProvider(".kts", "kotlin.Any"),
            ScriptClassProvider(
                extension = ".story.kts",
                baseClass = StoryScript::class.qualifiedName!!,
                defaultImports = listOf(
                    "net.minecraft.nbt.CompoundTag",
                    "net.minecraft.world.entity.Entity",
                    "net.minecraft.world.entity.LivingEntity",
                    "net.minecraft.world.entity.player.Player",
                    "net.minecraft.world.item.ItemStack",
                    "net.minecraft.world.phys.Vec3",
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.*",
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects.*",
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.*",
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.*",
                ),
            ),
            ScriptClassProvider(
                extension = ".reload.kts",
                baseClass = ReloadScript::class.qualifiedName!!,
                defaultImports = listOf(
                    "net.minecraft.resources.ResourceLocation",
                    "net.minecraft.world.item.ItemStack",
                    "net.minecraft.world.item.Items",
                    "net.minecraft.world.item.crafting.Ingredient",
                    "ru.hollowhorizon.hollowengine.common.scripting.reload.ingredient",
                    "ru.hollowhorizon.hollowengine.common.scripting.reload.ingredientTag",
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.item",
                    "ru.hollowhorizon.hollowengine.common.utils.rl",
                )
            ),
            ScriptClassProvider(
                extension = ".animation-controller.kts",
                baseClass = "ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationController",
                defaultImports = listOf(
                    "net.minecraft.world.entity.LivingEntity",
                    "ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationController",
                    "ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationSystem",
                    "ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode",
                )
            ),
        )
    }
}
