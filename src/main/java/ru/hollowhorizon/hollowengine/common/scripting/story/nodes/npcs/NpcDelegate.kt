package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityDimensions
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.mixins.EntityAccessor
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class NpcDelegate(
    val settings: IContextBuilder.NpcContainer,
) : Node(), ReadOnlyProperty<Any?, NPCProperty> {
    init {
        assert(ResourceLocation.isValidResourceLocation(settings.model)) { "Invalid model path: ${settings.model}" }
    }

    val npc: NPCEntity by lazy {
        val dimension = manager.server.levelKeys().find { it.location() == settings.world.rl }
            ?: throw IllegalStateException("Dimension ${settings.world} not found. Or not loaded!")
        val level = manager.server.getLevel(dimension)
            ?: throw IllegalStateException("Dimension ${settings.world} not found. Or not loaded")

        val entities = level.getEntities(ModEntities.NPC_ENTITY.get()) { entity: NPCEntity ->
            return@getEntities entity[AnimatedEntityCapability::class].model == settings.model && entity.displayName.string == settings.name && entity.isAlive
        }

        var isNpcSpawned = true
        val entity = entities.firstOrNull() ?: NPCEntity(level).apply {
            isNpcSpawned = false
            setPos(settings.pos.x, settings.pos.y, settings.pos.z)
            level.addFreshEntity(this)
        }

        if (!isNpcSpawned) {
            entity.getCapability(CapabilityStorage.getCapability(AnimatedEntityCapability::class.java)).ifPresent {
                it.model = settings.model
                it.animations.clear()
                it.animations.putAll(settings.animations)
                it.textures.clear()
                it.textures.putAll(settings.textures)
                it.transform = settings.transform
            }
            entity.moveTo(
                settings.pos.x + 0.5, settings.pos.y, settings.pos.z + 0.5, settings.rotation.x, settings.rotation.y
            )

            settings.attributes.attributes.forEach { (name, value) ->
                entity.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(name.rl) ?: return@forEach)?.baseValue =
                    value.toDouble()
            }

            (entity as EntityAccessor).setDimensions(
                EntityDimensions.scalable(
                    settings.size.first, settings.size.second
                )
            )
            entity.refreshDimensions()

            entity.isCustomNameVisible = this.settings.showName && settings.name.isNotEmpty()
            entity.customName = settings.name.mcText
        }

        entity
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): () -> NPCEntity {
        return { npc }
    }

    override fun tick(): Boolean {
        npc
        return false
    }

    override fun serializeNBT() = CompoundTag()

    override fun deserializeNBT(nbt: CompoundTag) {}
}

typealias NPCProperty = () -> NPCEntity
