package ru.hollowhorizon.hollowengine.ecs.npc

import de.fabmax.kool.modules.ui2.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapability
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.say
import ru.hollowhorizon.hollowengine.ecs.RegisterComponent

@Serializable
@kotlinx.serialization.Polymorphic
abstract class NpcComponent : Composable {
    @Transient
    lateinit var npc: NpcEntity

    open fun onInteract(player: Player, hand: InteractionHand) {}
    open fun canPickup(itemEntity: ItemEntity): Boolean = false
    open fun tick() {}
    open fun onDeath(damageSource: DamageSource) {}
}

@HollowCapability(NpcEntity::class)
class NpcComponentsCapability : CapabilityInstance() {
    val components by syncableList<@kotlinx.serialization.Polymorphic NpcComponent>()
}

@RegisterComponent("utils/greetings")
@Serializable
@Polymorphic(NpcComponent::class)
class GreetingComponent : NpcComponent() {
    var count = 0

    override fun onInteract(player: Player, hand: InteractionHand) {
        npc say "Привет! (${++count})"
    }

    override fun UiScope.compose() {
        TextField {
            modifier.text(count.toString())
                .onChange {
                    count = it.toIntOrNull() ?: return@onChange
                }
        }
    }
}