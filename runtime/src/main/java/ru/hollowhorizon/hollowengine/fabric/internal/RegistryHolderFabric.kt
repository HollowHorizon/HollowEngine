package ru.hollowhorizon.hollowengine.fabric.internal


import net.minecraft.commands.synchronization.ArgumentTypeInfo
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.stats.StatType
import net.minecraft.util.valueproviders.FloatProviderType
import net.minecraft.util.valueproviders.IntProviderType
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.SensorType
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.entity.animal.CatVariant
import net.minecraft.world.entity.animal.FrogVariant
import net.minecraft.world.entity.npc.VillagerProfession
import net.minecraft.world.entity.npc.VillagerType
import net.minecraft.world.entity.schedule.Activity
import net.minecraft.world.entity.schedule.Schedule
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Instrument
import net.minecraft.world.item.Item
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.gameevent.PositionSourceType
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType
import net.minecraft.world.level.levelgen.carver.WorldCarver
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.featuresize.FeatureSizeType
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProviderType
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType
import net.minecraft.world.level.levelgen.heightproviders.HeightProviderType
import net.minecraft.world.level.levelgen.placement.PlacementModifierType
import net.minecraft.world.level.levelgen.structure.StructureType
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType
import net.minecraft.world.level.levelgen.structure.templatesystem.PosRuleTestType
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTestType
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.RuleBlockEntityModifierType
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType
import net.minecraft.world.level.storage.loot.providers.nbt.LootNbtProviderType
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType
import net.minecraft.world.level.storage.loot.providers.score.LootScoreProviderType
import ru.hollowhorizon.hollowengine.client.utils.HollowPack
import ru.hollowhorizon.hollowengine.common.objects.blocks.BlockItemProperties
import ru.hollowhorizon.hollowengine.common.objects.items.CreativeTab
import ru.hollowhorizon.hollowengine.common.registry.AutoModelType
import ru.hollowhorizon.hollowengine.common.registry.IRegistryHolder
import ru.hollowhorizon.hollowengine.common.registry.system.Holder
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryState
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryVersion
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KProperty


class FabricRegistry<T : Any>(val registry: Registry<T>) :
    ru.hollowhorizon.hollowengine.common.registry.system.MutableRegistry<T> {
    override val key: ResourceLocation = registry.key().location()
    override val state: RegistryState = RegistryState.REGISTERING
    override val size: Int get() = registry.size()

    override fun getId(value: T): Int = registry.getId(value)

    override fun getById(id: Int): T? = registry.getHolder(id).getOrNull()?.value()

    override fun getHolder(id: Int): Holder<T>? {
        val holder = registry.getHolder(id).getOrNull() ?: return null
        return Holder<T>(holder.key().location(), id).apply {
            this.value = holder.value()
        }
    }

    override fun getOrNull(key: ResourceLocation): T? {
        return registry.get(key)
    }

    override fun getHolder(key: ResourceLocation): Holder<T>? {
        val holder =
            registry.getHolder(ResourceKey.create(registry.key(), key)).getOrNull()
                ?: return null
        return Holder<T>(key, registry.getId(holder.value())).apply {
            this.value = holder.value()
        }
    }

    override fun contains(key: ResourceLocation): Boolean {
        return registry.containsKey(key)
    }

    override fun iterator(): Iterator<Holder<T>> {
        return registry.holders().map {
            Holder<T>(it.key().location(), getId(it.value())).apply {
                this.value = it.value()
            }
        }.iterator()
    }

    override val version: RegistryVersion = RegistryVersion(1, 0, 0)

    override fun register(
        key: ResourceLocation,
        supplier: () -> T,
    ): Holder<T> {
        val item = supplier()
        Registry.register(registry, key, item)
        return Holder<T>(key, registry.getId(item)).apply {
            this.value = item
        }
    }

    override fun unregister(key: ResourceLocation): Boolean {
        throw UnsupportedOperationException("Unregister is not supported in Fabric")
    }

    override fun bake() {
        // NO-OP
    }

    override fun freeze() {
        // NO-OP
    }

    override fun unfreeze() {
        // NO-OP
    }

    override fun unbake() {
        // NO-OP
    }
}

@Suppress("UNCHECKED_CAST")
class RegistryHolderFabric<T : Any>(
    val location: ResourceLocation,
    val registry: Registry<T>? = null,
    val autoModel: AutoModelType?,
    supplier: () -> T,
    val target: Class<T>,
) : IRegistryHolder<T> {
    val registryType: Registry<T> = with(target) {
        fun KClass<*>.isAssigned(): Boolean = this.java.isAssignableFrom(this@with)

        when {
            GameEvent::class.isAssigned() -> BuiltInRegistries.GAME_EVENT
            SoundEvent::class.isAssigned() -> BuiltInRegistries.SOUND_EVENT
            Fluid::class.isAssigned() -> BuiltInRegistries.FLUID
            MobEffect::class.isAssigned() -> BuiltInRegistries.MOB_EFFECT
            Block::class.isAssigned() -> BuiltInRegistries.BLOCK
            EntityType::class.isAssigned() -> BuiltInRegistries.ENTITY_TYPE
            Item::class.isAssigned() -> BuiltInRegistries.ITEM
            Potion::class.isAssigned() -> BuiltInRegistries.POTION
            ParticleType::class.isAssigned() -> BuiltInRegistries.PARTICLE_TYPE
            BlockEntityType::class.isAssigned() -> BuiltInRegistries.BLOCK_ENTITY_TYPE
            ChunkStatus::class.isAssigned() -> BuiltInRegistries.CHUNK_STATUS
            RuleTestType::class.isAssigned() -> BuiltInRegistries.RULE_TEST
            RuleBlockEntityModifierType::class.isAssigned() -> BuiltInRegistries.RULE_BLOCK_ENTITY_MODIFIER
            PosRuleTestType::class.isAssigned() -> BuiltInRegistries.POS_RULE_TEST
            MenuType::class.isAssigned() -> BuiltInRegistries.MENU
            RecipeType::class.isAssigned() -> BuiltInRegistries.RECIPE_TYPE
            RecipeSerializer::class.isAssigned() -> BuiltInRegistries.RECIPE_SERIALIZER
            Attribute::class.isAssigned() -> BuiltInRegistries.ATTRIBUTE
            PositionSourceType::class.isAssigned() -> BuiltInRegistries.POSITION_SOURCE_TYPE
            ArgumentTypeInfo::class.isAssigned() -> BuiltInRegistries.COMMAND_ARGUMENT_TYPE
            StatType::class.isAssigned() -> BuiltInRegistries.STAT_TYPE
            VillagerType::class.isAssigned() -> BuiltInRegistries.VILLAGER_TYPE
            VillagerProfession::class.isAssigned() -> BuiltInRegistries.VILLAGER_PROFESSION
            PoiType::class.isAssigned() -> BuiltInRegistries.POINT_OF_INTEREST_TYPE
            MemoryModuleType::class.isAssigned() -> BuiltInRegistries.MEMORY_MODULE_TYPE
            SensorType::class.isAssigned() -> BuiltInRegistries.SENSOR_TYPE
            Schedule::class.isAssigned() -> BuiltInRegistries.SCHEDULE
            Activity::class.isAssigned() -> BuiltInRegistries.ACTIVITY
            LootPoolEntryType::class.isAssigned() -> BuiltInRegistries.LOOT_POOL_ENTRY_TYPE
            LootItemFunctionType::class.isAssigned() -> BuiltInRegistries.LOOT_FUNCTION_TYPE
            LootItemConditionType::class.isAssigned() -> BuiltInRegistries.LOOT_CONDITION_TYPE
            LootNumberProviderType::class.isAssigned() -> BuiltInRegistries.LOOT_NUMBER_PROVIDER_TYPE
            LootNbtProviderType::class.isAssigned() -> BuiltInRegistries.LOOT_NBT_PROVIDER_TYPE
            LootScoreProviderType::class.isAssigned() -> BuiltInRegistries.LOOT_SCORE_PROVIDER_TYPE
            FloatProviderType::class.isAssigned() -> BuiltInRegistries.FLOAT_PROVIDER_TYPE
            IntProviderType::class.isAssigned() -> BuiltInRegistries.INT_PROVIDER_TYPE
            HeightProviderType::class.isAssigned() -> BuiltInRegistries.HEIGHT_PROVIDER_TYPE
            BlockPredicateType::class.isAssigned() -> BuiltInRegistries.BLOCK_PREDICATE_TYPE
            WorldCarver::class.isAssigned() -> BuiltInRegistries.CARVER
            Feature::class.isAssigned() -> BuiltInRegistries.FEATURE
            StructurePlacementType::class.isAssigned() -> BuiltInRegistries.STRUCTURE_PLACEMENT
            StructurePieceType::class.isAssigned() -> BuiltInRegistries.STRUCTURE_PIECE
            StructureType::class.isAssigned() -> BuiltInRegistries.STRUCTURE_TYPE
            PlacementModifierType::class.isAssigned() -> BuiltInRegistries.PLACEMENT_MODIFIER_TYPE
            BlockStateProviderType::class.isAssigned() -> BuiltInRegistries.BLOCKSTATE_PROVIDER_TYPE
            FoliagePlacerType::class.isAssigned() -> BuiltInRegistries.FOLIAGE_PLACER_TYPE
            TreeDecoratorType::class.isAssigned() -> BuiltInRegistries.TREE_DECORATOR_TYPE
            FeatureSizeType::class.isAssigned() -> BuiltInRegistries.FEATURE_SIZE_TYPE
            StructureProcessorType::class.isAssigned() -> BuiltInRegistries.STRUCTURE_PROCESSOR
            StructurePoolElementType::class.isAssigned() -> BuiltInRegistries.STRUCTURE_POOL_ELEMENT
            CatVariant::class.isAssigned() -> BuiltInRegistries.CAT_VARIANT
            FrogVariant::class.isAssigned() -> BuiltInRegistries.FROG_VARIANT
            Instrument::class.isAssigned() -> BuiltInRegistries.INSTRUMENT
            CreativeModeTab::class.isAssigned() -> BuiltInRegistries.CREATIVE_MODE_TAB
            DataComponentType::class.isAssigned() -> BuiltInRegistries.DATA_COMPONENT_TYPE


            registry != null -> registry

            else -> throw UnsupportedOperationException("Unsupported registry object: ${target.simpleName}")
        }
    } as Registry<T>

    private val result: T = Registry.register(registryType, location, supplier()).apply {
        when {
            Block::class.java.isAssignableFrom(target) -> {
                if (autoModel != null) HollowPack.addBlockModel(location, autoModel)

                if (BlockItemProperties::class.java.isAssignableFrom(target)) {
                    val block = this as Block
                    val item = if (block is CreativeTab) {
                        object : BlockItem(block, (block as BlockItemProperties).properties), CreativeTab by block {}
                    } else {
                        BlockItem(block, (block as BlockItemProperties).properties)
                    }
                    Registry.register(BuiltInRegistries.ITEM, location, item)
                    if (autoModel != null) {
                        if (autoModel == AutoModelType.CUBE_ALL) HollowPack.addItemModel(
                            location, AutoModelType.custom("${location.namespace}:block/${location.path}")
                        )
                        else HollowPack.addItemModel(location, AutoModelType.custom("builtin/entity"))
                    }
                }
            }

            Item::class.java.isAssignableFrom(target) -> {
                if (autoModel != null) HollowPack.addItemModel(location, autoModel)
            }
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return result
    }
}
//?}