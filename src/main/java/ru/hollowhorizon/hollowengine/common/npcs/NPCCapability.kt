package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

@HollowCapabilityV2(NPCEntity::class)
class NPCCapability: CapabilityInstance() {
    var hitboxMode by syncable(HitboxMode.PULLING)
    var icon by syncable(NpcIcon.EMPTY)
}

@Serializable
class NpcIcon private constructor(
    val image: @Serializable(ForResourceLocation::class) ResourceLocation,
    var scale: Float = 1f,
    var offsetY: Float = 0f
) {
    companion object {
        fun create(image: String, scale: Float = 1f, offsetY: Float = 0f) = NpcIcon(image.rl, scale, offsetY)

        val EMPTY = NpcIcon(ResourceLocation("hollowengine:textures/gui/icons/empty.png"))
        val DIALOGUE = NpcIcon(ResourceLocation("hollowengine:textures/gui/icons/dialogue.png"))
        val QUESTION = NpcIcon(ResourceLocation("hollowengine:textures/gui/icons/question.png"))
        val WARN = NpcIcon(ResourceLocation("hollowengine:textures/gui/icons/warn.png"))
    }

    override fun equals(other: Any?): Boolean {
        if(other !is NpcIcon) return false
        return this.image == other.image
    }

    override fun hashCode(): Int {
        return image.hashCode()
    }
}