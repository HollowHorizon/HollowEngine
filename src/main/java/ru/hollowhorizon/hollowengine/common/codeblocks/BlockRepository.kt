package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.modules.ui2.mutableStateOf
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import kotlin.reflect.KClass

open class BlockProvider(val name: String, val rootCategory: BlockCategory) {
    fun resolveDisplayName(block: BlockModel, scope: BlocksScope): String? {
        return findDisplayName(block::class, rootCategory.items(scope), scope)
    }

    fun applyDisplayNames(root: BlockModel, scope: BlocksScope) {
        root.displayName = resolveDisplayName(root, scope) ?: root.displayName
        root.inputs.values.forEach { applyDisplayNames(it, scope) }
        (root as? StatementBlock)?.next?.let { applyDisplayNames(it, scope) }
    }

    private fun findDisplayName(type: KClass<out BlockModel>, items: List<CategoryItem>, scope: BlocksScope): String? {
        items.forEach { item ->
            when (item) {
                is BlockCategory -> {
                    findDisplayName(type, item.items(scope), scope)?.let { return it }
                }
                is BlockEntry<*> -> if (item.type == type) return item.name
            }
        }
        return null
    }
}

class BlockCategory(val name: String, val icon: ResourceLocation? = null): CategoryItem {
    val isExpanded = mutableStateOf(false)
    val subCategories = mutableListOf<BlockCategory>()
    val blocks = mutableListOf<BlockEntry<*>>()

    val dynamicGenerators = mutableListOf<BlocksScope.() -> List<BlockEntry<*>>>()
    fun entries(scope: BlocksScope): List<BlockEntry<*>> = blocks + dynamicGenerators.flatMap { it(scope) }
    fun items(scope: BlocksScope): List<CategoryItem> = subCategories + entries(scope)
}

sealed interface CategoryItem

data class BlockEntry<T : BlockModel>(
    val name: String,
    val icon: ResourceLocation? = null,
    private val factory: () -> T,
    val type: KClass<T>,
): CategoryItem {
    val previewItem by lazy {
        factory().also {
            it.displayName = name
            it.applyDefaults(recursive = true)
        }
    }

    fun createItem() = factory().also {
        it.displayName = name
        it.applyDefaults(recursive = true)
    }
}

fun interface BlockModule {
    fun BlockCategoryBuilder.build()
}

class BlockCategoryBuilder(@PublishedApi internal val category: BlockCategory) {

    /**
     * Создает подкатегорию.
     */
    fun category(name: String, icon: ResourceLocation?, setup: BlockCategoryBuilder.() -> Unit) {
        val sub = BlockCategory(name, icon)
        category.subCategories.add(sub)
        BlockCategoryBuilder(sub).setup()
    }

    fun categoryAfter(index: Int, name: String, icon: ResourceLocation?, setup: BlockCategoryBuilder.() -> Unit) {
        val sub = BlockCategory(name, icon)
        category.subCategories.add(index.coerceAtMost(category.subCategories.size), sub)
        BlockCategoryBuilder(sub).setup()
    }

    /**
     * Добавляет блок в текущую категорию.
     */
    inline fun <reified T : BlockModel> block(name: String, noinline factory: () -> T) {
        category.blocks.add(BlockEntry(name, icons.FILE_CODEBLOCKS, factory, T::class))
    }

    fun dynamicBlocks(generator: BlocksScope.() -> List<BlockEntry<*>>) {
        category.dynamicGenerators.add(generator)
    }

    /**
     * Включает готовый модуль (набор категорий/блоков) в текущую категорию.
     * Это позволяет переиспользовать код (например, математику).
     */
    fun include(module: BlockModule) {
        with(module) { build() }
    }
}

object BlockRepository {
    fun create(name: String, setup: BlockModule): BlockProvider {
        val root = BlockCategory(name)
        val builder = BlockCategoryBuilder(root)
        with(setup) { builder.build() }
        return BlockProvider(name, root)
    }
}
