package ru.hollowhorizon.hollowengine.common.codeblocks

import kotlin.reflect.KClass

class BlockProvider(val name: String, val rootCategory: BlockCategory)

class BlockCategory(val name: String) {
    val subCategories = mutableListOf<BlockCategory>()
    val blocks = mutableListOf<BlockEntry<*>>()
}

data class BlockEntry<T : CodeBlock>(val name: String, val factory: () -> T, val type: KClass<T>)

typealias BlockModule = BlockCategoryBuilder.() -> Unit

class BlockCategoryBuilder(@PublishedApi internal val category: BlockCategory) {

    /**
     * Создает подкатегорию.
     */
    fun category(name: String, setup: BlockCategoryBuilder.() -> Unit) {
        val sub = BlockCategory(name)
        category.subCategories.add(sub)
        BlockCategoryBuilder(sub).setup()
    }

    /**
     * Добавляет блок в текущую категорию.
     */
    inline fun <reified T : CodeBlock> block(name: String, noinline factory: () -> T) {
        category.blocks.add(BlockEntry(name, factory, T::class))
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
    fun create(name: String, setup: BlockModule): BlockProvider {
        val root = BlockCategory(name)
        val builder = BlockCategoryBuilder(root)
        builder.setup()
        return BlockProvider(name, root)
    }
}