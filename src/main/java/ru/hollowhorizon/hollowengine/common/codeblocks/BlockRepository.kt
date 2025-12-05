package ru.hollowhorizon.hollowengine.common.codeblocks

class BlockProvider(val name: String, val rootCategory: BlockCategory)

class BlockCategory(val name: String) {
    val subCategories = mutableListOf<BlockCategory>()
    val blocks = mutableListOf<BlockEntry>()
}

data class BlockEntry(val name: String, val factory: () -> CodeBlock)

typealias BlockModule = BlockCategoryBuilder.() -> Unit

class BlockCategoryBuilder(private val category: BlockCategory) {

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
    fun block(name: String, factory: () -> CodeBlock) {
        category.blocks.add(BlockEntry(name, factory))
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