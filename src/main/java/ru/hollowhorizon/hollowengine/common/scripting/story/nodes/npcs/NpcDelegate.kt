package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityDimensions
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserialize
import ru.hollowhorizon.hc.client.utils.nbt.serialize
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.SpawnLocation
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.mixins.EntityAccessor
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class NpcDelegate(
    var settings: NPCSettings,
    var location: SpawnLocation
) : Node(), ReadOnlyProperty<Any?, NPCProperty> {
    init {
        assert(ResourceLocation.isValidResourceLocation(settings.model)) { "Invalid model path: ${settings.model}" }
    }

    val npc: NPCEntity by lazy {
        val dimension = manager.server.levelKeys().find { it.location() == location.world.rl }
            ?: throw IllegalStateException("Dimension ${location.world} not found. Or not loaded!")
        val level = manager.server.getLevel(dimension)
            ?: throw IllegalStateException("Dimension ${location.world} not found. Or not loaded")

        val entities = level.getEntities(ModEntities.NPC_ENTITY.get()) { entity ->
            return@getEntities entity[AnimatedEntityCapability::class].model.rl == settings.model.rl && entity.displayName.string == settings.name && entity.isAlive
        }

        var isNpcSpawned = true
        val entity = entities.firstOrNull() ?: NPCEntity(level).apply {
            isNpcSpawned = false
            setPos(
                location.pos.x.toDouble() + 0.5,
                location.pos.y.toDouble(),
                location.pos.z.toDouble() + 0.5
            )
            level.addFreshEntity(this)
        }

        if (!isNpcSpawned) {
            entity.getCapability(CapabilityStorage.getCapability(AnimatedEntityCapability::class.java)).ifPresent {
                it.model = settings.model
            }
            entity.moveTo(
                location.pos.x.toDouble() + 0.5,
                location.pos.y.toDouble(),
                location.pos.z.toDouble() + 0.5,
                location.rotation.x,
                location.rotation.y
            )

            settings.data.attributes.forEach { (name, value) ->
                entity.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(name.rl) ?: return@forEach)?.baseValue =
                    value.toDouble()
            }

            (entity as EntityAccessor).setDimensions(EntityDimensions.scalable(settings.size.first, settings.size.second))
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
        npc.navigation.moveTo(location.pos.x + 0.5, location.pos.y.toDouble(), location.pos.z + 0.5, 1.0)
        return npc.tickCount < 10
    }

    override fun serializeNBT() = CompoundTag().apply {
        put("settings", NBTFormat.serialize(settings))
        put("location", NBTFormat.serialize(location))
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        settings = NBTFormat.deserialize(nbt.get("settings")!!)
        location = NBTFormat.deserialize(nbt.get("location")!!)
    }
}

typealias NPCProperty = () -> NPCEntity
