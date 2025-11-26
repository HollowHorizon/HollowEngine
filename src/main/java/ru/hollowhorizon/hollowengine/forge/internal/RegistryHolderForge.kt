package ru.hollowhorizon.hollowengine.forge.internal

//? if forge {
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.commands.synchronization.ArgumentTypeInfo
import net.minecraft.core.Registry
import net.minecraft.core.particles.ParticleType
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
import net.minecraft.world.entity.decoration.PaintingVariant
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
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BannerPattern
import net.minecraft.world.level.block.entity.BlockEntityType
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
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.IForgeRegistry
import ru.hollowhorizon.hollowengine.client.utils.HollowPack
import ru.hollowhorizon.hollowengine.common.objects.blocks.BlockItemProperties
import ru.hollowhorizon.hollowengine.common.objects.items.CreativeTab
import ru.hollowhorizon.hollowengine.common.registry.AutoModelType
import ru.hollowhorizon.hollowengine.common.registry.IRegistryHolder
import ru.hollowhorizon.hollowengine.common.registry.system.Holder
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryState
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryVersion
import ru.hollowhorizon.hollowengine.common.registry.system.keyOf
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class ForgeRegistry<T: Any>(val registry: Registry<T>): ru.hollowhorizon.hollowengine.common.registry.system.MutableRegistry<T> {
    override val key: ResourceLocation = registry.key().location()
    override val state: RegistryState = RegistryState.REGISTERING
    override val size: Int get() = registry.size()

    override fun getId(value: T): Int = registry.getId(value)

    override fun getById(id: Int): T? = registry.getHolder(id).getOrNull()?.value()

    override fun getHolder(id: Int): Holder<T>? {
        val holder = registry.getHolder(id).getOrNull() ?: return null
        return Holder<T>(keyOf(holder.key().location().namespace, holder.key().location().path), id).apply {
            this.value = holder.value()
        }
    }

    override fun getOrNull(key: ru.hollowhorizon.hollowengine.common.registry.system.ResourceKey<T>): T? {
        return registry.get(key.location)
    }

    override fun getHolder(key: ru.hollowhorizon.hollowengine.common.registry.system.ResourceKey<T>): Holder<T>? {
        val holder = registry.getHolder(net.minecraft.resources.ResourceKey.create(registry.key(), key.location)).getOrNull() ?: return null
        return Holder(key, registry.getId(holder.value())).apply {
            this.value = holder.value()
        }
    }

    override fun contains(key: ru.hollowhorizon.hollowengine.common.registry.system.ResourceKey<T>): Boolean {
        return registry.containsKey(key.location)
    }

    override fun iterator(): Iterator<Holder<T>> {
        return registry.holders().map {
            Holder<T>(keyOf(it.key().location().namespace, it.key().location().path), getId(it.value())).apply {
                this.value = it.value()
            }
        }.iterator()
    }

    override val version: RegistryVersion = RegistryVersion(1, 0, 0)

    override fun register(
        key: ru.hollowhorizon.hollowengine.common.registry.system.ResourceKey<T>,
        supplier: () -> T,
    ): Holder<T> {
        val item = supplier()
        Registry.register(registry, key.location, item)
        return Holder(key, registry.getId(item)).apply {
            this.value = item
        }
    }

    override fun unregister(key: ru.hollowhorizon.hollowengine.common.registry.system.ResourceKey<T>): Boolean {
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
class RegistryHolderForge<T : Any>(
    val location: ResourceLocation,
    val registry: Registry<T>? = null,
    val autoModel: AutoModelType?,
    supplier: () -> T,
    val target: Class<T>,
) : IRegistryHolder<T> {
    val registryType: DeferredRegister<T> = with(target) {
        fun KClass<*>.isAssigned(): Boolean = this.java.isAssignableFrom(this@with)
        fun <T> liteRegister(reg: IForgeRegistry<T>): DeferredRegister<T> {
            val lreg by lazy { DeferredRegister.create(reg, location.namespace) }
            return lreg
        }
        fun <T> liteRegister(reg: ResourceKey<out Registry<T>>): DeferredRegister<T> {
            val lreg by lazy { DeferredRegister.create(reg, location.namespace) }
            return lreg
        }

        when {
            GameEvent::class.isAssigned() -> liteRegister(Registries.GAME_EVENT)
            SoundEvent::class.isAssigned() -> liteRegister(ForgeRegistries.SOUND_EVENTS)
            Fluid::class.isAssigned() -> liteRegister(ForgeRegistries.FLUIDS)
            MobEffect::class.isAssigned() -> liteRegister(ForgeRegistries.MOB_EFFECTS)
            Block::class.isAssigned() -> liteRegister(ForgeRegistries.BLOCKS)
            Enchantment::class.isAssigned() -> liteRegister(ForgeRegistries.ENCHANTMENTS)
            EntityType::class.isAssigned() -> liteRegister(ForgeRegistries.ENTITY_TYPES)
            Item::class.isAssigned() -> liteRegister(ForgeRegistries.ITEMS)
            Potion::class.isAssigned() -> liteRegister(ForgeRegistries.POTIONS)
            ParticleType::class.isAssigned() -> liteRegister(ForgeRegistries.PARTICLE_TYPES)
            BlockEntityType::class.isAssigned() -> liteRegister(ForgeRegistries.BLOCK_ENTITY_TYPES)
            PaintingVariant::class.isAssigned() -> liteRegister(ForgeRegistries.PAINTING_VARIANTS)
            ChunkStatus::class.isAssigned() -> liteRegister(ForgeRegistries.CHUNK_STATUS)
            RuleTestType::class.isAssigned() -> liteRegister(Registries.RULE_TEST)
            RuleBlockEntityModifierType::class.isAssigned() -> liteRegister(Registries.RULE_BLOCK_ENTITY_MODIFIER)
            PosRuleTestType::class.isAssigned() -> liteRegister(Registries.POS_RULE_TEST)
            MenuType::class.isAssigned() -> liteRegister(ForgeRegistries.MENU_TYPES)
            RecipeType::class.isAssigned() -> liteRegister(ForgeRegistries.RECIPE_TYPES)
            RecipeSerializer::class.isAssigned() -> liteRegister(ForgeRegistries.RECIPE_SERIALIZERS)
            Attribute::class.isAssigned() -> liteRegister(ForgeRegistries.ATTRIBUTES)
            PositionSourceType::class.isAssigned() -> liteRegister(Registries.POSITION_SOURCE_TYPE)
            ArgumentTypeInfo::class.isAssigned() -> liteRegister(ForgeRegistries.COMMAND_ARGUMENT_TYPES)
            StatType::class.isAssigned() -> liteRegister(ForgeRegistries.STAT_TYPES)
            VillagerType::class.isAssigned() -> liteRegister(Registries.VILLAGER_TYPE)
            VillagerProfession::class.isAssigned() -> liteRegister(ForgeRegistries.VILLAGER_PROFESSIONS)
            PoiType::class.isAssigned() -> liteRegister(ForgeRegistries.POI_TYPES)
            MemoryModuleType::class.isAssigned() -> liteRegister(ForgeRegistries.MEMORY_MODULE_TYPES)
            SensorType::class.isAssigned() -> liteRegister(ForgeRegistries.SENSOR_TYPES)
            Schedule::class.isAssigned() -> liteRegister(ForgeRegistries.SCHEDULES)
            Activity::class.isAssigned() -> liteRegister(ForgeRegistries.ACTIVITIES)
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
            WorldCarver::class.isAssigned() -> liteRegister(ForgeRegistries.WORLD_CARVERS)
            Feature::class.isAssigned() -> liteRegister(ForgeRegistries.FEATURES)
            StructurePlacementType::class.isAssigned() -> liteRegister(Registries.STRUCTURE_PLACEMENT)
            StructurePieceType::class.isAssigned() -> liteRegister(Registries.STRUCTURE_PIECE)
            StructureType::class.isAssigned() -> liteRegister(Registries.STRUCTURE_TYPE)
            PlacementModifierType::class.isAssigned() -> liteRegister(Registries.PLACEMENT_MODIFIER_TYPE)
            BlockStateProviderType::class.isAssigned() -> liteRegister(ForgeRegistries.BLOCK_STATE_PROVIDER_TYPES)
            FoliagePlacerType::class.isAssigned() -> liteRegister(ForgeRegistries.FOLIAGE_PLACER_TYPES)
            TreeDecoratorType::class.isAssigned() -> liteRegister(ForgeRegistries.TREE_DECORATOR_TYPES)
            FeatureSizeType::class.isAssigned() -> liteRegister(Registries.FEATURE_SIZE_TYPE)
            StructureProcessorType::class.isAssigned() -> liteRegister(Registries.STRUCTURE_PROCESSOR)
            StructurePoolElementType::class.isAssigned() -> liteRegister(Registries.STRUCTURE_POOL_ELEMENT)
            CatVariant::class.isAssigned() -> liteRegister(Registries.CAT_VARIANT)
            FrogVariant::class.isAssigned() -> liteRegister(Registries.FROG_VARIANT)
            BannerPattern::class.isAssigned() -> liteRegister(Registries.BANNER_PATTERN)
            Instrument::class.isAssigned() -> liteRegister(Registries.INSTRUMENT)
            CreativeModeTab::class.isAssigned() -> liteRegister(Registries.CREATIVE_MODE_TAB)
            //? if >=1.21 {
            /*DataComponentType::class.isAssigned() -> liteRegister(BuiltInRegistries.DATA_COMPONENT_TYPE.key())
            *///?}

            registry != null -> liteRegister(registry.key())

            else -> throw UnsupportedOperationException("Unsupported registry object: ${target.simpleName}")
        }
    } as DeferredRegister<T>

    private val result: net.minecraftforge.registries.RegistryObject<T> =
        registryType.register(location.path, supplier).apply {
            when {
                Block::class.java.isAssignableFrom(target) -> {
                    if (autoModel != null) HollowPack.addBlockModel(location, autoModel)

                    if (BlockItemProperties::class.java.isAssignableFrom(target)) {
                        val items: DeferredRegister<Item> =
                            DeferredRegister.create(ForgeRegistries.ITEMS, location.namespace)
                        items.register(
                            location.path
                        ) {
                            val block = this.get() as Block
                            if (block is CreativeTab) {
                                object : BlockItem(block, (block as BlockItemProperties).properties),
                                    CreativeTab by block {}
                            } else {
                                BlockItem(block, (block as BlockItemProperties).properties)
                            }
                        }
                        items.register(FMLJavaModLoadingContext.get().modEventBus)
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
            registryType.register(FMLJavaModLoadingContext.get().modEventBus)
        }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return result.get()
    }
}
//?}
