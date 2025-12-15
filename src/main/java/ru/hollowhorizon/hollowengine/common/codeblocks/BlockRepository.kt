package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import kotlin.reflect.KClass

class BlockProvider(val name: String, val rootCategory: BlockCategory)

fun BlockProvider.findColorFor(block: BlockModel): Color = rootCategory.findColorFor(block) ?: rootCategory.color
fun BlockCategory.findColorFor(block: BlockModel): Color? {
    return if (block::class in blocks.map { it.type }) color
    else subCategories.firstNotNullOfOrNull { it.findColorFor(block) }
}

class BlockCategory(val name: String, val color: Color, val icon: String? = null) {
    val subCategories = mutableListOf<BlockCategory>()
    val blocks = mutableListOf<BlockEntry<*>>()

    val dynamicGenerators = mutableListOf<BlocksScope.() -> List<BlockEntry<*>>>()
    fun entries(scope: BlocksScope): List<BlockEntry<*>> = blocks + dynamicGenerators.flatMap { it(scope) }
}

data class BlockEntry<T : BlockModel>(
    val name: String,
    val icon: String? = null,
    val factory: () -> T,
    val type: KClass<T>,
)

fun interface BlockModule {
    fun BlockCategoryBuilder.build()
}

class BlockCategoryBuilder(@PublishedApi internal val category: BlockCategory) {

    /**
     * Создает подкатегорию.
     */
    fun category(name: String, color: Color, icon: String?, setup: BlockCategoryBuilder.() -> Unit) {
        val sub = BlockCategory(name, color, icon)
        category.subCategories.add(sub)
        BlockCategoryBuilder(sub).setup()
    }

    fun categoryAfter(index: Int, name: String, color: Color, icon: String?, setup: BlockCategoryBuilder.() -> Unit) {
        val sub = BlockCategory(name, color, icon)
        category.subCategories.add(index.coerceAtMost(category.subCategories.size), sub)
        BlockCategoryBuilder(sub).setup()
    }

    /**
     * Добавляет блок в текущую категорию.
     */
    inline fun <reified T : BlockModel> block(name: String, noinline factory: () -> T) {
        category.blocks.add(BlockEntry(name, null, {
            val block = factory()
            block.color = category.color
            block
        }, T::class))
    }

    inline fun <reified T : BlockModel> blockWithColor(name: String, color: Color, noinline factory: () -> T) {
        category.blocks.add(BlockEntry(name, null, {
            val block = factory()
            block.color = color
            block
        }, T::class))
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
    fun create(name: String, color: Color = Color("A666EA"), setup: BlockModule): BlockProvider {
        val root = BlockCategory(name, color)
        val builder = BlockCategoryBuilder(root)
        with(setup) { builder.build() }
        return BlockProvider(name, root)
    }
}