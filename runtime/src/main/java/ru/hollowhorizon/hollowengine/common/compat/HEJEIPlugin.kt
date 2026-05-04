package ru.hollowhorizon.hollowengine.common.compat

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.helpers.IPlatformFluidHelper
import mezz.jei.api.registration.*
import mezz.jei.api.runtime.IJeiRuntime
import mezz.jei.api.runtime.config.IJeiConfigManager
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.events.ModifyRecipeViewerEvent
import ru.hollowhorizon.hollowengine.common.utils.rl

@JeiPlugin
class HEJEIPlugin : IModPlugin {
    override fun registerItemSubtypes(registration: ISubtypeRegistration) {
        ModifyRecipeViewerEvent.RegisterItemSubtypes.post(ModifyRecipeViewerEvent.RegisterItemSubtypes(registration))
    }

    override fun <T : Any?> registerFluidSubtypes(
        registration: ISubtypeRegistration,
        platformFluidHelper: IPlatformFluidHelper<T>,
    ) {
        ModifyRecipeViewerEvent.RegisterFluidSubtypes.post(
            ModifyRecipeViewerEvent.RegisterFluidSubtypes(
                registration,
                platformFluidHelper
            )
        )
    }

    override fun registerIngredients(registration: IModIngredientRegistration) {
        ModifyRecipeViewerEvent.RegisterIngredients.post(ModifyRecipeViewerEvent.RegisterIngredients(registration))
    }

    override fun registerExtraIngredients(registration: IExtraIngredientRegistration) {
        ModifyRecipeViewerEvent.RegisterExtraIngredients.post(
            ModifyRecipeViewerEvent.RegisterExtraIngredients(
                registration
            )
        )
    }

    override fun registerIngredientAliases(registration: IIngredientAliasRegistration) {
        ModifyRecipeViewerEvent.RegisterIngredientAliases.post(
            ModifyRecipeViewerEvent.RegisterIngredientAliases(
                registration
            )
        )
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        ModifyRecipeViewerEvent.RegisterCategories.post(ModifyRecipeViewerEvent.RegisterCategories(registration))
    }

    override fun registerVanillaCategoryExtensions(registration: IVanillaCategoryExtensionRegistration) {
        ModifyRecipeViewerEvent.RegisterVanillaCategoryExtensions.post(
            ModifyRecipeViewerEvent.RegisterVanillaCategoryExtensions(
                registration
            )
        )
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        ModifyRecipeViewerEvent.RegisterRecipes.post(ModifyRecipeViewerEvent.RegisterRecipes(registration))
    }

    override fun registerRecipeTransferHandlers(registration: IRecipeTransferRegistration) {
        ModifyRecipeViewerEvent.RegisterRecipeTransferHandlers.post(
            ModifyRecipeViewerEvent.RegisterRecipeTransferHandlers(
                registration
            )
        )
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        ModifyRecipeViewerEvent.RegisterRecipeCatalysts.post(
            ModifyRecipeViewerEvent.RegisterRecipeCatalysts(
                registration
            )
        )
    }

    override fun registerGuiHandlers(registration: IGuiHandlerRegistration) {
        ModifyRecipeViewerEvent.RegisterGuiHandlers.post(ModifyRecipeViewerEvent.RegisterGuiHandlers(registration))
    }

    override fun registerAdvanced(registration: IAdvancedRegistration) {
        ModifyRecipeViewerEvent.RegisterAdvanced.post(ModifyRecipeViewerEvent.RegisterAdvanced(registration))
    }

    override fun registerRuntime(registration: IRuntimeRegistration) {
        ModifyRecipeViewerEvent.RegisterRuntime.post(ModifyRecipeViewerEvent.RegisterRuntime(registration))
    }

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        ModifyRecipeViewerEvent.RegisterOnRuntimeAvailable.post(
            ModifyRecipeViewerEvent.RegisterOnRuntimeAvailable(jeiRuntime)
        )
    }

    override fun onConfigManagerAvailable(configManager: IJeiConfigManager) {
        ModifyRecipeViewerEvent.RegisterOnConfigManagerAvailable.post(
            ModifyRecipeViewerEvent.RegisterOnConfigManagerAvailable(
                configManager
            )
        )
    }

    override fun getPluginUid(): ResourceLocation = "${HollowEngine.MODID}:he_plugin".rl
}
