package ru.hollowhorizon.hollowengine.common.scripting

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
        "ru.hollowhorizon.hc.client.utils.*"
    )

    baseClass(NpcBehavior::class)
})

abstract class NpcBehavior(val npc: NPCEntity) {
    fun onTick() {
    }

    fun onItemPickUp(item: ItemStack): Boolean {
        return false
    }

    fun onInteract(player: Player): Boolean {
        return false
    }
}