package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.NpcContainer
import java.util.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class NpcDelegate(
    val builder: IContextBuilder,
    val settings: () -> NpcContainer
) : Node(), ReadOnlyProperty<Any?, NPCProperty> {

    val npc: NPCEntity by lazy {
        val settings = settings()
        check(ResourceLocation.isValidResourceLocation(settings.model)) { "Invalid model path: ${settings.model}" }

        val dimension = manager.server.levelKeys().find { it.location() == settings.world.rl }
            ?: throw IllegalStateException("Dimension ${settings.world} not found. Or not loaded!")
        val level = manager.server.getLevel(dimension)
            ?: throw IllegalStateException("Dimension ${settings.world} not found. Or not loaded")

        val entities = level.getEntities(ModEntities.NPC_ENTITY.get()) { entity: NPCEntity ->
            return@getEntities entity[AnimatedEntityCapability::class].model == settings.model &&
                    entity.displayName.string == settings.name &&
                    UUID.fromString(entity[NPCCapability::class].teamUUID) == manager.team.id &&
                    entity.isAlive
        }

        var isNpcSpawned = true
        val entity = entities.firstOrNull() ?: NPCEntity(level).apply {
            isNpcSpawned = false
            setPos(settings.pos.x, settings.pos.y, settings.pos.z)
            level.addFreshEntity(this)
        }

        if (!isNpcSpawned) {
            entity[AnimatedEntityCapability::class].apply {
                model = settings.model
                animations.clear()
                animations.putAll(settings.animations)
                textures.clear()
                textures.putAll(settings.textures)
                transform = settings.transform
                switchHeadRot = settings.switchHeadRot
                subModels.clear()
                subModels.putAll(settings.subModels)
            }
            entity[NPCCapability::class].teamUUID = manager.team.id.toString()
            entity.moveTo(
                settings.pos.x, settings.pos.y, settings.pos.z, settings.rotation.x, settings.rotation.y
            )

            settings.attributes.attributes.forEach { (name, value) ->
                entity.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(name.rl) ?: return@forEach)?.baseValue =
                    value.toDouble()
            }

            entity.setDimensions(settings.size)
            entity.refreshDimensions()

            entity.isCustomNameVisible = settings.showName && settings.name.isNotEmpty()
            entity.customName = settings.name.mcText
        }

        entity
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): NPCProperty {
        return NPCProperty(builder) { npc }
    }

    override fun tick(): Boolean {
        npc
        return false
    }

    override fun serializeNBT() = CompoundTag()

    override fun deserializeNBT(nbt: CompoundTag) {}
}
