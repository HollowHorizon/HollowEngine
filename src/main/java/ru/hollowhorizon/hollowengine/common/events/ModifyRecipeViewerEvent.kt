package ru.hollowhorizon.hollowengine.common.events

import mezz.jei.api.helpers.IPlatformFluidHelper
import mezz.jei.api.recipe.IRecipeManager
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.registration.*
import mezz.jei.api.runtime.IJeiRuntime
import mezz.jei.api.runtime.config.IJeiConfigManager
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.LOGGER
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.mcTranslate
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.compact.util.hide
import ru.hollowhorizon.hollowengine.common.compact.util.hideWithin
import ru.hollowhorizon.hollowengine.common.compact.util.recipeCategoryId
import ru.hollowhorizon.hollowengine.common.compact.util.recipeManager

open class ModifyRecipeViewerEvent: Event {
    class RegisterItemSubtypes(val reg: ISubtypeRegistration): ModifyRecipeViewerEvent()

    class RegisterFluidSubtypes<T>(val reg: ISubtypeRegistration, val helper: IPlatformFluidHelper<T>): ModifyRecipeViewerEvent()

    class RegisterIngredients(val reg: IModIngredientRegistration): ModifyRecipeViewerEvent()

    class RegisterExtraIngredients(val reg: IExtraIngredientRegistration): ModifyRecipeViewerEvent()

    class RegisterIngredientAliases(val reg: IIngredientAliasRegistration): ModifyRecipeViewerEvent()

    class RegisterCategories(val reg: IRecipeCategoryRegistration): ModifyRecipeViewerEvent()

    class RegisterVanillaCategoryExtensions(val reg: IVanillaCategoryExtensionRegistration): ModifyRecipeViewerEvent()

    class RegisterRecipes(val reg: IRecipeRegistration): ModifyRecipeViewerEvent() {
        fun addItemInfo(ingredient: ItemStack, vararg info: Component) {
            reg.addItemStackInfo(ingredient, *info)
        }

        fun addItemInfo(ingredient: ItemStack, info: List<Component>) {
            reg.addItemStackInfo(ingredient, *info.toTypedArray())
        }

        fun addItemInfo(ingredient: ItemStack, info: Component) {
            reg.addItemStackInfo(ingredient, info)
        }

        fun addItemInfo(ingredient: ItemStack, info: String) {
            reg.addItemStackInfo(ingredient, info.mcTranslate)
        }

        fun addItemInfo(ingredient: ItemStack, info: Array<String>) {
            reg.addItemStackInfo(ingredient, *info.map { it.mcTranslate }.toTypedArray())
        }
    }

    class RegisterRecipeTransferHandlers(val reg: IRecipeTransferRegistration): ModifyRecipeViewerEvent()

    class RegisterRecipeCatalysts(val reg: IRecipeCatalystRegistration): ModifyRecipeViewerEvent()

    class RegisterGuiHandlers(val reg: IGuiHandlerRegistration): ModifyRecipeViewerEvent()

    class RegisterAdvanced(val reg: IAdvancedRegistration): ModifyRecipeViewerEvent()

    class RegisterRuntime(val reg: IRuntimeRegistration): ModifyRecipeViewerEvent()

    class RegisterOnRuntimeAvailable(val jeiRuntime: IJeiRuntime): ModifyRecipeViewerEvent() {
        fun hideRecipe(categoryId: ResourceLocation, recipeId: ResourceLocation) {
            val manager = jeiRuntime.recipeManager
            manager.createRecipeCategoryLookup()
                .includeHidden()
                .get()
                .filter { categoryId == it.recipeCategoryId }
                .findAny()
                .ifPresent { this.hide(recipeId, categoryId, manager, it) }
        }

        fun hideCategory(categoryId: ResourceLocation) {
            val manager = jeiRuntime.recipeManager
            manager.createRecipeCategoryLookup()
                .includeHidden()
                .get()
                .filter { categoryId == it.recipeCategoryId }
                .findAny()
                .ifPresent { it.hide(manager) }
        }

        private fun <T> hide(recipeId: ResourceLocation, categoryId: ResourceLocation, manager: IRecipeManager, category: IRecipeCategory<T>) {
            recipeManager.byKey(recipeId).ifPresent { this.hide(recipeId, categoryId, manager, category, it) }
        }

        private fun <T, U> hide(recipeId: ResourceLocation, categoryId: ResourceLocation, manager: IRecipeManager, category: IRecipeCategory<T>, recipe: U) {
            try {
                category.hideWithin(JavaHacks.forceCast(recipe), manager)
            } catch (e: IllegalArgumentException) {
                LOGGER.error("Unable to hide target recipe '$recipeId' within category '$categoryId' due to an error; maybe the recipe is not removable?", e)
            }
        }
    }

    class RegisterOnConfigManagerAvailable(val configManager: IJeiConfigManager): ModifyRecipeViewerEvent()
}
