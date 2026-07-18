package ru.hollowhorizon.hollowengine.common.scripting.nodes

import kotlinx.coroutines.launch
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.common.events.entity.EntityEvent
import ru.hollowhorizon.hollowengine.common.events.entity.LivingEntityDeathEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent

/**
 * Fires when a player interacts with the bound entity. Filtered to the server side and the main hand,
 * so it runs once per interaction.
 */
context(entity: LivingEntity, script: NodeScript)
fun onInteract(block: suspend (PlayerInteractEvent.EntityInteract) -> Unit) {
    PlayerInteractEvent.EntityInteract.subscribe(script) { event ->
        if (event.target === entity && event.hand == InteractionHand.MAIN_HAND && !event.player.level().isClientSide) {
            script.launch { block(event) }
        }
    }
}

/** Fires when the bound entity takes damage. */
context(entity: LivingEntity, script: NodeScript)
fun onHurt(block: suspend (EntityEvent.Hurt) -> Unit) {
    EntityEvent.Hurt.subscribe(script) { event ->
        if (event.entity === entity) script.launch { block(event) }
    }
}

/** Fires when the bound entity dies. */
context(entity: LivingEntity, script: NodeScript)
fun onDie(block: suspend (LivingEntityDeathEvent) -> Unit) {
    LivingEntityDeathEvent.subscribe(script) { event ->
        if (event.entity === entity) script.launch { block(event) }
    }
}
