package ru.hollowhorizon.hollowengine.ecs.npc

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.say
import ru.hollowhorizon.hollowengine.ecs.Component
import ru.hollowhorizon.hollowengine.ecs.RegisterComponent

open class NpcComponent : Component {
    lateinit var npc: NpcEntity

    open fun onInteract(player: Player, hand: InteractionHand) {}
    open fun canPickup(itemEntity: ItemEntity): Boolean = false
    open fun tick() {}
    open fun onDeath(damageSource: DamageSource) {}
}

@RegisterComponent("Приветствие")
class Example : NpcComponent() {
    var count = 0

    override fun onInteract(player: Player, hand: InteractionHand) {
        npc say "Привет! (${++count})"
    }

    override fun load(tag: CompoundTag) {
        count = tag.getInt("count")
    }

    override fun save(tag: CompoundTag) {
        tag.putInt("count", count)
    }
}