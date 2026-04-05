package ru.hollowhorizon.hollowengine.neoforge.internal

//? if neoforge {

/*import net.minecraft.commands.synchronization.ArgumentTypeInfo
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
import net.minecraft.world.item.BlockItem
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
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister
import ru.hollowhorizon.hollowengine.client.utils.HollowPack
import ru.hollowhorizon.hollowengine.common.objects.blocks.BlockItemProperties
import ru.hollowhorizon.hollowengine.common.objects.items.CreativeTab
import ru.hollowhorizon.hollowengine.common.registry.AutoModelType
import ru.hollowhorizon.hollowengine.common.registry.IRegistryHolder
import java.util.function.Supplier
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
class RegistryHolderNeoForge<T : Any>(
    val modBus: IEventBus,
    val location: ResourceLocation,
    val registry: Registry<T>? = null,
    val autoModel: AutoModelType?,
    supplier: () -> T,
    val target: Class<T>,
) : IRegistryHolder<T> {
    val registryType: DeferredRegister<T> = with(target) {
        fun KClass<*>.isAssigned(): Boolean = this.java.isAssignableFrom(this@with)
        fun <T> liteRegister(reg: Registry<T>): DeferredRegister<T> {
            val lreg by lazy { DeferredRegister.create(reg, location.namespace) }
            return lreg
        }
        fun <T> liteRegister(reg: ResourceKey<out Registry<T>>): DeferredRegister<T> {
            val lreg by lazy { DeferredRegister.create(reg, location.namespace) }
            return lreg
        }

        when {
            GameEvent::class.isAssigned() -> liteRegister(Registries.GAME_EVENT)
            SoundEvent::class.isAssigned() -> liteRegister(BuiltInRegistries.SOUND_EVENT)
            Fluid::class.isAssigned() -> liteRegister(BuiltInRegistries.FLUID)
            MobEffect::class.isAssigned() -> liteRegister(BuiltInRegistries.MOB_EFFECT)
            Block::class.isAssigned() -> liteRegister(BuiltInRegistries.BLOCK)
            EntityType::class.isAssigned() -> liteRegister(BuiltInRegistries.ENTITY_TYPE)
            Item::class.isAssigned() -> liteRegister(BuiltInRegistries.ITEM)
            Potion::class.isAssigned() -> liteRegister(BuiltInRegistries.POTION)
            ParticleType::class.isAssigned() -> liteRegister(BuiltInRegistries.PARTICLE_TYPE)
            BlockEntityType::class.isAssigned() -> liteRegister(BuiltInRegistries.BLOCK_ENTITY_TYPE)
            ChunkStatus::class.isAssigned() -> liteRegister(BuiltInRegistries.CHUNK_STATUS)
            RuleTestType::class.isAssigned() -> liteRegister(Registries.RULE_TEST)
            RuleBlockEntityModifierType::class.isAssigned() -> liteRegister(Registries.RULE_BLOCK_ENTITY_MODIFIER)
            PosRuleTestType::class.isAssigned() -> liteRegister(Registries.POS_RULE_TEST)
            MenuType::class.isAssigned() -> liteRegister(BuiltInRegistries.MENU)
            RecipeType::class.isAssigned() -> liteRegister(BuiltInRegistries.RECIPE_TYPE)
            RecipeSerializer::class.isAssigned() -> liteRegister(BuiltInRegistries.RECIPE_SERIALIZER)
            Attribute::class.isAssigned() -> liteRegister(BuiltInRegistries.ATTRIBUTE)
            PositionSourceType::class.isAssigned() -> liteRegister(Registries.POSITION_SOURCE_TYPE)
            ArgumentTypeInfo::class.isAssigned() -> liteRegister(BuiltInRegistries.COMMAND_ARGUMENT_TYPE)
            StatType::class.isAssigned() -> liteRegister(BuiltInRegistries.STAT_TYPE)
            VillagerType::class.isAssigned() -> liteRegister(Registries.VILLAGER_TYPE)
            VillagerProfession::class.isAssigned() -> liteRegister(BuiltInRegistries.VILLAGER_PROFESSION)
            PoiType::class.isAssigned() -> liteRegister(BuiltInRegistries.POINT_OF_INTEREST_TYPE)
            MemoryModuleType::class.isAssigned() -> liteRegister(BuiltInRegistries.MEMORY_MODULE_TYPE)
            SensorType::class.isAssigned() -> liteRegister(BuiltInRegistries.SENSOR_TYPE)
            Schedule::class.isAssigned() -> liteRegister(BuiltInRegistries.SCHEDULE)
            Activity::class.isAssigned() -> liteRegister(BuiltInRegistries.ACTIVITY)
            LootPoolEntryType::class.isAssigned() -> liteRegister(Registries.LOOT_POOL_ENTRY_TYPE)
            LootItemFunctionType::class.isAssigned() -> liteRegister(Registries.LOOT_FUNCTION_TYPE)
            LootItemConditionType::class.isAssigned() -> liteRegister(Registries.LOOT_CONDITION_TYPE)
            LootNumberProviderType::class.isAssigned() -> liteRegister(Registries.LOOT_NUMBER_PROVIDER_TYPE)
            LootNbtProviderType::class.isAssigned() -> liteRegister(Registries.LOOT_NBT_PROVIDER_TYPE)
            LootScoreProviderType::class.isAssigned() -> liteRegister(Registries.LOOT_SCORE_PROVIDER_TYPE)
            FloatProviderType::class.isAssigned() -> liteRegister(Registries.FLOAT_PROVIDER_TYPE)
            IntProviderType::class.isAssigned() -> liteRegister(Registries.INT_PROVIDER_TYPE)
            HeightProviderType::class.isAssigned() -> liteRegister(Registries.HEIGHT_PROVIDER_TYPE)
            BlockPredicateType::class.isAssigned() -> liteRegister(Registries.BLOCK_PREDICATE_TYPE)
            WorldCarver::class.isAssigned() -> liteRegister(BuiltInRegistries.CARVER)
            Feature::class.isAssigned() -> liteRegister(BuiltInRegistries.FEATURE)
            StructurePlacementType::class.isAssigned() -> liteRegister(Registries.STRUCTURE_PLACEMENT)
            StructurePieceType::class.isAssigned() -> liteRegister(Registries.STRUCTURE_PIECE)
            StructureType::class.isAssigned() -> liteRegister(Registries.STRUCTURE_TYPE)
            PlacementModifierType::class.isAssigned() -> liteRegister(Registries.PLACEMENT_MODIFIER_TYPE)
            BlockStateProviderType::class.isAssigned() -> liteRegister(BuiltInRegistries.BLOCKSTATE_PROVIDER_TYPE)
            FoliagePlacerType::class.isAssigned() -> liteRegister(BuiltInRegistries.FOLIAGE_PLACER_TYPE)
            TreeDecoratorType::class.isAssigned() -> liteRegister(BuiltInRegistries.TREE_DECORATOR_TYPE)
            FeatureSizeType::class.isAssigned() -> liteRegister(Registries.FEATURE_SIZE_TYPE)
            StructureProcessorType::class.isAssigned() -> liteRegister(Registries.STRUCTURE_PROCESSOR)
            StructurePoolElementType::class.isAssigned() -> liteRegister(Registries.STRUCTURE_POOL_ELEMENT)
            CatVariant::class.isAssigned() -> liteRegister(Registries.CAT_VARIANT)
            FrogVariant::class.isAssigned() -> liteRegister(Registries.FROG_VARIANT)
            BannerPattern::class.isAssigned() -> liteRegister(Registries.BANNER_PATTERN)
            Instrument::class.isAssigned() -> liteRegister(Registries.INSTRUMENT)
            CreativeModeTab::class.isAssigned() -> liteRegister(Registries.CREATIVE_MODE_TAB)
            //? if >=1.21 {
            /^DataComponentType::class.isAssigned() -> liteRegister(BuiltInRegistries.DATA_COMPONENT_TYPE.key())
            ^///?}

                registry != null -> liteRegister(registry.key())

            else -> throw UnsupportedOperationException("Unsupported registry object: ${target.simpleName}")
        }
    } as DeferredRegister<T>

    private val result =
        registryType.register(location.path, supplier).apply {
            when {
                Block::class.java.isAssignableFrom(target) -> {
                    if (autoModel != null) HollowPack.addBlockModel(location, autoModel)

                    if (BlockItemProperties::class.java.isAssignableFrom(target)) {
                        val items: DeferredRegister<Item> =
                            DeferredRegister.create(BuiltInRegistries.ITEM, location.namespace)
                        items.register(
                            location.path,
                            Supplier {
                                val block = this.get() as Block
                                if (block is CreativeTab) {
                                    object : BlockItem(block, (block as BlockItemProperties).properties),
                                        CreativeTab by block {}
                                } else {
                                    BlockItem(block, (block as BlockItemProperties).properties)
                                }
                            })
                        items.register(modBus)
                        if (autoModel != null) {
                            if (autoModel == AutoModelType.CUBE_ALL) HollowPack.addItemModel(
                                location,
                                AutoModelType.custom("${location.namespace}:block/${location.path}")
                            )
                            else HollowPack.addItemModel(location, AutoModelType.custom("builtin/entity"))
                        }
                    }
                }

                Item::class.java.isAssignableFrom(target) -> {
                    if (autoModel != null) HollowPack.addItemModel(location, autoModel)
                }
            }
            registryType.register(modBus)
        }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return result.get()
    }
}

*///?}