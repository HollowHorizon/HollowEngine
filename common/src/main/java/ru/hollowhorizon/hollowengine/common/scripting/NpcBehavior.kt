package ru.hollowhorizon.hollowengine.common.scripting

import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.common.scripting.kotlin.AbstractHollowScriptConfiguration
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "NPC Behavior Script",
    fileExtension = "npc.kts",
    compilationConfiguration = NpcScriptConfiguration::class
)
abstract class NpcBehaviorScript(npc: NPCEntity) : NpcBehavior(npc)

class NpcScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.*",
        "ru.hollowhorizon.hc.client.utils.*",
        "net.minecraft.world.item.ItemStack",
        "net.minecraft.world.InteractionHand",
        "net.minecraft.world.entity.player.Player",
        "net.minecraft.world.damagesource.DamageSource"
    )

    baseClass(NpcBehavior::class)
})

abstract class NpcBehavior(val npc: NPCEntity) {
    val server = npc.server!!

    // Каждый тик
    open fun onTick() {}

    // При попытке поднять предмет
    open fun onItemPickUp(item: ItemStack): Boolean {
        return false // Поднять ли нпс этот предмет
    }

    // При пкм по нпс
    open fun onInteract(player: Player, pHand: InteractionHand): Boolean {
        return true // Продолжить ли стандартные действия (меню)
    }

    // При ударе по нпс
    open fun onHurt(damageSource: DamageSource, amount: Float): Boolean {
        return true // Проходит ли урон по нпс
    }
}