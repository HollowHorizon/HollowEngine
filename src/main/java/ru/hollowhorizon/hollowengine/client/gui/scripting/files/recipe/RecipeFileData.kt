package ru.hollowhorizon.hollowengine.client.gui.scripting.files.recipe

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.RecipeType
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.textLine
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData

class RecipeFileData(name: String, path: String) : FileData(name, path) {

    override fun save() {}

    override fun UiScope.compose() {
        modifier.backgroundColor(colors.backgroundVariant)

        val recipes =
            Minecraft.getInstance().connection?.recipeManager?.getAllRecipesFor(RecipeType.CRAFTING)?.chunked(3)
                ?: return


        LazyColumn {
            items(recipes) { chunk ->
                Row {
                    chunk.forEach {
                        Recipe(it)
                    }
                }
            }
        }
    }

    fun UiScope.Recipe(recipe: CraftingRecipe) {
        Box {
            modifier.padding(sizes.gap)
                .border(RoundRectBorder(Color.WHITE, sizes.smallGap, sizes.borderWidth))
                .background(RoundRectBackground(colors.background, sizes.smallGap))
                .margin(horizontal = sizes.smallGap, vertical = sizes.smallGap * 0.5f)

            Row {
                Column {
                    val ingredients = recipe.ingredients.map { it.items.firstOrNull() ?: ItemStack.EMPTY }
                    val paddedIngredients = ingredients + List(9 - ingredients.size) { ItemStack.EMPTY }

                    paddedIngredients.chunked(3).forEach { chunk ->
                        Row {
                            chunk.forEach { item ->
                                Slot(item, 48.dp)
                            }
                        }
                    }
                }
                Arrow {
                    modifier.size(50.dp, 50.dp).alignY(AlignmentY.Center).colors(colors.onBackground, colors.onBackground)
                }
                Slot(recipe.getResultItem(Minecraft.getInstance().connection!!.registryAccess()), 55.dp) {
                    modifier.alignY(AlignmentY.Center)
                }
            }
        }
    }

    fun UiScope.Slot(stack: ItemStack, size: Dimension, block: UiScope.() -> Unit = {}) {
        Item(stack) {
            modifier.border(RoundRectBorder(Color.WHITE, sizes.smallGap, sizes.borderWidth)).size(size, size)
                .margin(horizontal = sizes.smallGap, vertical = sizes.smallGap * 0.5f)
                .padding(sizes.smallGap * 0.5f)
            block()

            val state = remember { TooltipState(0.0) }
            modifier.hoverListener(state)

            if (!stack.isEmpty && state.use()) surface.popup().apply {
                surface.popup().apply {
                    modifier
                        .margin(
                            top = Dp.fromPx(state.pointerY.use()) + sizes.smallGap,
                            start = Dp.fromPx(state.pointerX.use()) + sizes.smallGap
                        )
                        .onPositioned { state.popupNode = it }
                        .layout(CellLayout)
                        .background(UiRenderer { node ->
                            node.apply {
                                getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                                    .localRoundRect(0f, 0f, widthPx, heightPx, sizes.smallGap.px, colors.background)
                                colors.onBackground.let {
                                    getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                                        .localRoundRectBorder(
                                            0f,
                                            0f,
                                            widthPx,
                                            heightPx,
                                            sizes.smallGap.px,
                                            sizes.borderWidth.px,
                                            it
                                        )
                                }
                            }
                        })
                        .zLayer(UiSurface.LAYER_POPUP)
                    Column {
                        modifier.padding(sizes.gap)

                        stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.NORMAL).forEach {
                            Row {
                                it.textLine().spans.forEach { text ->
                                    Text(text.first) {
                                        modifier.font(text.second.font.derive(18f))
                                            .textColor(text.second.color)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class TooltipState(val delay: Double = 1.0) : MutableStateValue<Boolean>(false), Hoverable {
    private var enterTime = 0.0
    val pointerX = mutableStateOf(0f)
    val pointerY = mutableStateOf(0f)
    var popupNode: UiNode? = null

    override fun onEnter(ev: PointerEvent) {
        enterTime = Time.gameTime
    }

    override fun onHover(ev: PointerEvent) {
        if (Time.gameTime - enterTime > delay) {
            val maxX = popupNode?.let { it.surface.viewport.rightPx - it.widthPx - 20f } ?: 1e9f
            val maxY = popupNode?.let { it.surface.viewport.bottomPx - it.heightPx - 20f } ?: 1e9f
            pointerX.set(ev.pointer.pos.x.coerceIn(0f, maxX))
            pointerY.set(ev.pointer.pos.y.coerceIn(0f, maxY))
            set(true)
        }
    }

    override fun onExit(ev: PointerEvent) {
        set(false)
    }
}