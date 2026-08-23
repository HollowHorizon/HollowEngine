package ru.hollowhorizon.hollowengine.common.scripting.story.functions.player

import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.attachments.api.AttachmentRegistry
import ru.hollowhorizon.hollowengine.common.attachments.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.attachments.components.PlayerArms
import ru.hollowhorizon.hollowengine.common.attachments.components.PlayerArmsComponent

/**
 * The arm shape this player renders with.
 *
 * ```kotlin
 * player.arms = PlayerArms.SLIM
 * player.arms = null // back to default player profile
 * ```
 */
var Player.arms: PlayerArms?
    get() {
        val id = ComponentDescriptorRegistry.idFor(PlayerArmsComponent::class) ?: return null
        return (AttachmentRegistry.componentsById(this)[id] as? PlayerArmsComponent)?.arms
    }
    set(value) {
        val id = ComponentDescriptorRegistry.idFor(PlayerArmsComponent::class) ?: return
        val components = AttachmentRegistry.componentsById(this)
        if (value == null) {
            components.remove(id)
            return
        }

        val updated = PlayerArmsComponent(value)
        if (components[id] == updated) return

        components[id] = updated
    }
