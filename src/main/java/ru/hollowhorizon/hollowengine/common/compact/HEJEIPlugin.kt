package ru.hollowhorizon.hollowengine.common.compact

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.helpers.IPlatformFluidHelper
import mezz.jei.api.registration.*
import mezz.jei.api.runtime.IJeiRuntime
import mezz.jei.api.runtime.config.IJeiConfigManager
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.events.ModifyRecipeViewerEvent

@JeiPlugin
class HEJEIPlugin: IModPlugin {
    override fun registerItemSubtypes(registration: ISubtypeRegistration) {
        ModifyRecipeViewerEvent.RegisterItemSubtypes(registration).post()
    }

    override fun <T : Any?> registerFluidSubtypes(
        registration: ISubtypeRegistration,
        platformFluidHelper: IPlatformFluidHelper<T>
    ) {
        ModifyRecipeViewerEvent.RegisterFluidSubtypes(registration, platformFluidHelper).post()
    }

    override fun registerIngredients(registration: IModIngredientRegistration) {
        ModifyRecipeViewerEvent.RegisterIngredients(registration).post()
    }

    override fun registerExtraIngredients(registration: IExtraIngredientRegistration) {
        ModifyRecipeViewerEvent.RegisterExtraIngredients(registration).post()
    }

    override fun registerIngredientAliases(registration: IIngredientAliasRegistration) {
        ModifyRecipeViewerEvent.RegisterIngredientAliases(registration).post()
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        ModifyRecipeViewerEvent.RegisterCategories(registration).post()
    }

    override fun registerVanillaCategoryExtensions(registration: IVanillaCategoryExtensionRegistration) {
        ModifyRecipeViewerEvent.RegisterVanillaCategoryExtensions(registration).post()
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        ModifyRecipeViewerEvent.RegisterRecipes(registration).post()
    }

    override fun registerRecipeTransferHandlers(registration: IRecipeTransferRegistration) {
        ModifyRecipeViewerEvent.RegisterRecipeTransferHandlers(registration).post()
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        ModifyRecipeViewerEvent.RegisterRecipeCatalysts(registration).post()
    }

    override fun registerGuiHandlers(registration: IGuiHandlerRegistration) {
        ModifyRecipeViewerEvent.RegisterGuiHandlers(registration).post()
    }

    override fun registerAdvanced(registration: IAdvancedRegistration) {
        ModifyRecipeViewerEvent.RegisterAdvanced(registration).post()
    }

    override fun registerRuntime(registration: IRuntimeRegistration) {
        ModifyRecipeViewerEvent.RegisterRuntime(registration).post()
    }

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        ModifyRecipeViewerEvent.RegisterOnRuntimeAvailable(jeiRuntime).post()
    }

    override fun onConfigManagerAvailable(configManager: IJeiConfigManager) {
        ModifyRecipeViewerEvent.RegisterOnConfigManagerAvailable(configManager).post()
    }

    override fun getPluginUid(): ResourceLocation = "${HollowEngine.MODID}:he_plugin".rl
}
