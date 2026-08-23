package ru.hollowhorizon.hollowengine.common.scripting.story.functions.entities

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.attachments.api.AttachmentRegistry
import ru.hollowhorizon.hollowengine.common.attachments.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.attachments.components.MaterialsComponent
import ru.hollowhorizon.hollowengine.common.attachments.components.with
import ru.hollowhorizon.hollowengine.common.attachments.components.without
import ru.hollowhorizon.hollowengine.common.models.MaterialSource
import ru.hollowhorizon.hollowengine.common.models.PlayerSkinPart
import ru.hollowhorizon.hollowengine.common.utils.rl

/**
 * What an entity wears, by material name.
 *
 * ```kotlin
 * npc.materials["skin"] = texture("mypack:textures/entity/guard.png")
 * player.materials["cape"] = cape("Notch")
 * npc.materials.reset("skin")
 * ```
 *
 * The names are the model's own, as its file or its `.hemeta` gives them. `skin` and `cape` mean the same
 * on a player without a model, so one line dresses either.
 */
val Entity.materials: EntityMaterials get() = EntityMaterials(this)

@JvmInline
value class EntityMaterials(private val entity: Entity) {
    val names: Set<String> get() = component()?.materials?.keys ?: emptySet()

    operator fun get(name: String): MaterialSource? = component()?.materials?.get(name)

    operator fun set(name: String, source: MaterialSource) {
        update { it.with(name, source) }
    }

    /** Gives the material back the look the model was authored with. */
    fun reset(name: String) {
        update { it.without(name) }
    }

    /** Undresses the entity completely: every material back to the model's own. */
    fun clear() {
        update { MaterialsComponent() }
    }

    private fun component(): MaterialsComponent? {
        val id = ComponentDescriptorRegistry.idFor(MaterialsComponent::class) ?: return null
        return AttachmentRegistry.componentsById(entity)[id] as? MaterialsComponent
    }

    private fun update(change: (MaterialsComponent) -> MaterialsComponent) {
        val id = ComponentDescriptorRegistry.idFor(MaterialsComponent::class) ?: return
        val components = AttachmentRegistry.componentsById(entity)
        val current = components[id] as? MaterialsComponent ?: MaterialsComponent()
        val updated = change(current)
        if (updated == current) return

        components[id] = updated
    }
}

/** A texture out of a resource pack. */
fun texture(
    texture: String,
    normal: String? = null,
    specular: String? = null,
    color: String? = null,
    slim: Boolean? = null,
): MaterialSource = MaterialSource.Texture(
    texture = texture.rl,
    normal = normal?.rl,
    specular = specular?.rl,
    color = color,
    slim = slim,
)

/** The skin of a player, by nickname or uuid. */
fun skin(player: String): MaterialSource = MaterialSource.Player(player, PlayerSkinPart.SKIN)

/** The cape of a player, by nickname or uuid. */
fun cape(player: String): MaterialSource = MaterialSource.Player(player, PlayerSkinPart.CAPE)

/** The elytra texture of a player, by nickname or uuid. */
fun elytra(player: String): MaterialSource = MaterialSource.Player(player, PlayerSkinPart.ELYTRA)
