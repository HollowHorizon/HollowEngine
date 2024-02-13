package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.next

class NPCProperty(val builder: IContextBuilder, val npc: () -> NPCEntity) : () -> NPCEntity by npc

infix fun NPCProperty.next(action: SimpleNode.() -> Unit) = builder.apply { next(action) }

var NPCProperty.hitboxMode
    get(): HitboxMode = this()[NPCCapability::class].hitboxMode
    set(value) {
        builder.apply {
            next {
                this@hitboxMode()[NPCCapability::class].hitboxMode = value
            }
        }
    }

var NPCProperty.icon
    get(): NpcIcon = this()[NPCCapability::class].icon
    set(value) {
        builder.apply {
            next {
                this@icon()[NPCCapability::class].icon = value
            }
        }
    }

var NPCProperty.invulnerable
    get() = this().isInvulnerable
    set(value) {
        builder.apply {
            next {
                this@invulnerable().isInvulnerable = value
            }
        }
    }

infix fun NPCProperty.giveLeftHand(item: () -> ItemStack?) {
    builder.apply {
        next {
            this@giveLeftHand().setItemInHand(InteractionHand.OFF_HAND, item() ?: ItemStack.EMPTY)
        }
    }
}

infix fun NPCProperty.giveRightHand(item: () -> ItemStack?) {
    builder.apply {
        next {
            this@giveRightHand().setItemInHand(InteractionHand.MAIN_HAND, item() ?: ItemStack.EMPTY)
        }
    }
}

infix fun NPCProperty.configure(body: AnimatedEntityCapability.() -> Unit) {
    builder.apply {
        next {
            this@configure()[AnimatedEntityCapability::class].apply(body)
        }
    }
}

fun NPCProperty.despawn() = next { this@despawn().remove(Entity.RemovalReason.DISCARDED) }