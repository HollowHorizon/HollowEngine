package ru.hollowhorizon.hollowengine.common.utils

import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.api.extensions.ItemStackHelper

object ItemStackUtil {
    private lateinit var factory: ItemStackHelper

    fun remainer(item: ItemStack): ItemStack? {
        return factory.getRecipeRemainerFor(item)
    }

    fun init(factory: ItemStackHelper) {
        this.factory = factory
    }
}