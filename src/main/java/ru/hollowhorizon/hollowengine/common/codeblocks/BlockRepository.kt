package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import kotlin.reflect.KClass

class BlockProvider(val name: String, val rootCategory: BlockCategory)

fun BlockProvider.findColorFor(block: CodeBlock): Color = rootCategory.findColorFor(block) ?: rootCategory.color
fun BlockCategory.findColorFor(block: CodeBlock): Color? {
    return if(block::class in blocks.map { it.type }) color
    else subCategories.firstNotNullOfOrNull { it.findColorFor(block) }
}

class BlockCategory(val name: String, val color: Color) {
    val subCategories = mutableListOf<BlockCategory>()
    val blocks = mutableListOf<BlockEntry<*>>()
}

data class BlockEntry<T : CodeBlock>(val name: String, val factory: () -> T, val type: KClass<T>)

typealias BlockModule = BlockCategoryBuilder.() -> Unit

class BlockCategoryBuilder(@PublishedApi internal val category: BlockCategory) {

    /**
     * Создает подкатегорию.
     */
    fun category(name: String, color: Color, setup: BlockCategoryBuilder.() -> Unit) {
        val sub = BlockCategory(name, color)
        category.subCategories.add(sub)
        BlockCategoryBuilder(sub).setup()
    }

    /**
     * Добавляет блок в текущую категорию.
     */
    inline fun <reified T : CodeBlock> block(name: String, noinline factory: () -> T) {
        category.blocks.add(BlockEntry(name, {
            val block = factory()
            block.color = category.color
            block
        }, T::class))
    }

    /**
     * Включает готовый модуль (набор категорий/блоков) в текущую категорию.
     * Это позволяет переиспользовать код (например, математику).
     */
    fun include(module: BlockModule) {
        this.module()
    }
}

object BlockRepository {
    fun create(name: String, color: Color = MdColor.LIGHT_BLUE, setup: BlockModule): BlockProvider {
        val root = BlockCategory(name, color)
        val builder = BlockCategoryBuilder(root)
        builder.setup()
        return BlockProvider(name, root)
    }
}