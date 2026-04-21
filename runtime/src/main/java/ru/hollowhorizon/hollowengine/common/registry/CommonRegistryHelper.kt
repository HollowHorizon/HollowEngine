package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.commands.synchronization.ArgumentTypeInfo
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
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
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Instrument
import net.minecraft.world.item.Item
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BannerPattern
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
import ru.hollowhorizon.hollowengine.api.AutoModelType
import ru.hollowhorizon.hollowengine.api.RegistryHelper
import ru.hollowhorizon.hollowengine.client.utils.HollowPack

object CommonRegistryHelper : RegistryHelper {
    val REGISTRY_MAP: MutableMap<Class<*>, ResourceKey<out Registry<*>>> = HashMap()

    init {
        register(GameEvent::class.java, Registries.GAME_EVENT)
        register(SoundEvent::class.java, BuiltInRegistries.SOUND_EVENT.key())
        register(Fluid::class.java, BuiltInRegistries.FLUID.key())
        register(MobEffect::class.java, BuiltInRegistries.MOB_EFFECT.key())
        register(Block::class.java, BuiltInRegistries.BLOCK.key())
        register(EntityType::class.java, BuiltInRegistries.ENTITY_TYPE.key())
        register(Item::class.java, BuiltInRegistries.ITEM.key())
        register(Potion::class.java, BuiltInRegistries.POTION.key())
        register(ParticleType::class.java, BuiltInRegistries.PARTICLE_TYPE.key())
        register(BlockEntityType::class.java, BuiltInRegistries.BLOCK_ENTITY_TYPE.key())
        register(ChunkStatus::class.java, BuiltInRegistries.CHUNK_STATUS.key())
        register(RuleTestType::class.java, Registries.RULE_TEST)
        register(RuleBlockEntityModifierType::class.java, Registries.RULE_BLOCK_ENTITY_MODIFIER)
        register(PosRuleTestType::class.java, Registries.POS_RULE_TEST)
        register(MenuType::class.java, BuiltInRegistries.MENU.key())
        register(RecipeType::class.java, BuiltInRegistries.RECIPE_TYPE.key())
        register(RecipeSerializer::class.java, BuiltInRegistries.RECIPE_SERIALIZER.key())
        register(Attribute::class.java, BuiltInRegistries.ATTRIBUTE.key())
        register(PositionSourceType::class.java, Registries.POSITION_SOURCE_TYPE)
        register(ArgumentTypeInfo::class.java, BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key())
        register(StatType::class.java, BuiltInRegistries.STAT_TYPE.key())
        register(VillagerType::class.java, Registries.VILLAGER_TYPE)
        register(VillagerProfession::class.java, BuiltInRegistries.VILLAGER_PROFESSION.key())
        register(PoiType::class.java, BuiltInRegistries.POINT_OF_INTEREST_TYPE.key())
        register(MemoryModuleType::class.java, BuiltInRegistries.MEMORY_MODULE_TYPE.key())
        register(SensorType::class.java, BuiltInRegistries.SENSOR_TYPE.key())
        register(Schedule::class.java, BuiltInRegistries.SCHEDULE.key())
        register(Activity::class.java, BuiltInRegistries.ACTIVITY.key())
        register(LootPoolEntryType::class.java, Registries.LOOT_POOL_ENTRY_TYPE)
        register(LootItemFunctionType::class.java, Registries.LOOT_FUNCTION_TYPE)
        register(LootItemConditionType::class.java, Registries.LOOT_CONDITION_TYPE)
        register(LootNumberProviderType::class.java, Registries.LOOT_NUMBER_PROVIDER_TYPE)
        register(LootNbtProviderType::class.java, Registries.LOOT_NBT_PROVIDER_TYPE)
        register(LootScoreProviderType::class.java, Registries.LOOT_SCORE_PROVIDER_TYPE)
        register(FloatProviderType::class.java, Registries.FLOAT_PROVIDER_TYPE)
        register(IntProviderType::class.java, Registries.INT_PROVIDER_TYPE)
        register(HeightProviderType::class.java, Registries.HEIGHT_PROVIDER_TYPE)
        register(BlockPredicateType::class.java, Registries.BLOCK_PREDICATE_TYPE)
        register(WorldCarver::class.java, BuiltInRegistries.CARVER.key())
        register(Feature::class.java, BuiltInRegistries.FEATURE.key())
        register(StructurePlacementType::class.java, Registries.STRUCTURE_PLACEMENT)
        register(StructurePieceType::class.java, Registries.STRUCTURE_PIECE)
        register(StructureType::class.java, Registries.STRUCTURE_TYPE)
        register(PlacementModifierType::class.java, Registries.PLACEMENT_MODIFIER_TYPE)
        register(
            BlockStateProviderType::class.java,
            BuiltInRegistries.BLOCKSTATE_PROVIDER_TYPE.key()
        )
        register(FoliagePlacerType::class.java, BuiltInRegistries.FOLIAGE_PLACER_TYPE.key())
        register(TreeDecoratorType::class.java, BuiltInRegistries.TREE_DECORATOR_TYPE.key())
        register(FeatureSizeType::class.java, Registries.FEATURE_SIZE_TYPE)
        register(StructureProcessorType::class.java, Registries.STRUCTURE_PROCESSOR)
        register(StructurePoolElementType::class.java, Registries.STRUCTURE_POOL_ELEMENT)
        register(CatVariant::class.java, Registries.CAT_VARIANT)
        register(FrogVariant::class.java, Registries.FROG_VARIANT)
        register(BannerPattern::class.java, Registries.BANNER_PATTERN)
        register(Instrument::class.java, Registries.INSTRUMENT)
        register(CreativeModeTab::class.java, Registries.CREATIVE_MODE_TAB)

        register(DataComponentType::class.java, BuiltInRegistries.DATA_COMPONENT_TYPE.key())
    }

    private fun register(type: Class<*>, key: ResourceKey<out Registry<*>>) {
        REGISTRY_MAP.put(type, key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> registry(type: Class<T>): ResourceKey<out Registry<T>> {
        var type: Class<*>? = type
        do {
            REGISTRY_MAP[type!!]?.let { return it as ResourceKey<out Registry<T>> }
            type = type.superclass
        } while (type != null)
        error("Registry with type $type not found!")
    }

    override fun addBlockModel(
        location: ResourceLocation,
        model: AutoModelType,
    ) {
        HollowPack.addBlockModel(location, model)
    }

    override fun addItemModel(
        location: ResourceLocation,
        model: AutoModelType,
    ) {
        HollowPack.addItemModel(location, model)
    }
}