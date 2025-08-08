package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.Container
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import ru.hollowhorizon.hc.client.kool.Item
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.recipe.RecipeFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors


class RecipeEditorPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.recipes", dock) {
    override val icon = "hollowengine:textures/gui/icons/recipes.svg"

    override fun UiScope.compose() {
        modifier.margin(sizes.smallGap)

        val manager = Minecraft.getInstance().connection?.recipeManager ?: return

        LazyColumn {
            items(RECIPE_TYPES) { recipeType ->
                Box {
                    modifier.padding(sizes.smallGap * 0.5f).margin(sizes.smallGap * 0.5f)
                        .width(Grow.Std)
                        .backgroundColor(hoverColors(0.5f, colors.background, IdeTheme.hoveredColors.background))
                        .onClick {
                            IdeContent.openFile(RecipeFileData("Crafting Recipes", "files.recipes"))
                        }

                    val recipeItem = manager.getAllRecipesFor(recipeType as RecipeType<Recipe<Container>>)
                        .firstOrNull()?.toastSymbol ?: Items.CRAFTING_TABLE.defaultInstance

                    Row {
                        modifier.align(AlignmentX.Center, AlignmentY.Center)

                        Item(recipeItem) {
                            modifier.size(24.dp, 24.dp).alignY(AlignmentY.Center)
                                .margin(end = sizes.smallGap)
                                .border(null)
                        }
                        Text(BuiltInRegistries.RECIPE_TYPE.getKey(recipeType).toString()) {
                            modifier.alignY(AlignmentY.Center)
                        }
                    }
                }
            }
        }
    }

    companion object {
        val RECIPE_TYPES = BuiltInRegistries.RECIPE_TYPE.toList()
    }
}


