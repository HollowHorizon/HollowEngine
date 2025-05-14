package ru.hollowhorizon.hollowengine.client.gui

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockLayout
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.kool.Entity
import ru.hollowhorizon.hc.client.kool.KoolManager.MONOCRAFT
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.client.models.internal.manager.GltfManager
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader.TOOL_LAYOUT
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.story.InlineScript
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.model
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.valueOrNull

class NPCToolGui(val npc: NpcEntity) : KoolScreen() {
    var model = npc.model
    var position = Vec2f.ZERO
    val dock = Dock()

    override fun Scene.setup() {
        val options = HashSet<NpcOption>()
        NpcOptionsEvent(options::add, npc).post()

        setupUiScene()

        addNode(dock)
        dock.apply {
            borderWidth.set(IdeTheme.sizes.borderWidth)
            borderColor.set(Color("3C3C4AFF"))
            dockingSurface.sizes = IdeTheme.sizes.copy(normalText = MsdfFont(MONOCRAFT, 18f))
            dockingSurface.colors = IdeTheme.colors

            val generalPanel = GeneralPanel()
            val entityPanel = EntityPanel()
            val attributesPanel = AttributesPanel()

            val layoutLoaded = DockLayout.loadLayout(TOOL_LAYOUT, dock) {
                when (it) {
                    "hollowengine.gui.tool.general" -> generalPanel.dockable
                    "hollowengine.gui.tool.entity" -> entityPanel.dockable
                    "hollowengine.gui.tool.attributes" -> attributesPanel.dockable
                    else -> null
                }
            }
            if (!layoutLoaded) {
                dock.createNodeLayout(
                    listOf(
                        "0:row",
                        "0:row/0:leaf",
                        "0:row/1:leaf",
                    )
                )
                dock.getLeafAtPath("0:row/0:leaf")?.dock(attributesPanel.dockable)
                dock.getLeafAtPath("0:row/0:leaf")?.dock(generalPanel.dockable)
                dock.getLeafAtPath("0:row/1:leaf")?.dock(entityPanel.dockable)
            }
        }
    }

    private fun UiScope.Property(name: String, block: UiScope.() -> Unit) {
        Row(Grow.Std) {
            modifier.margin(sizes.smallGap)
            Text("$name: ") {
                modifier.alignY(AlignmentY.Center)
            }
            block()
        }
    }

    override fun onClose() {
        super.onClose()
        DockLayout.saveLayout(dock, TOOL_LAYOUT)
    }

    override fun isPauseScreen() = false

    inner class GeneralPanel : DockPanel("hollowengine.gui.tool.general", dock) {
        override val icon: String = "Not used."

        init {
            showOnToolbar = false
        }

        override fun UiScope.compose() {
            modifier.padding(sizes.smallGap)
            Column(Grow.Std) {
                Property("Имя") {
                    TextField {
                        modifier.alignY(AlignmentY.Center)
                            .text(npc.name)
                            .onChange { npc.name = it }
                            .width(Grow.Std)
                    }
                }
                Property("Модель") {
                    TextField {
                        modifier.textColor = if(ResourceLocation.isValidResourceLocation(model) && model.rl in GltfManager.allModels) colors.onBackground else Color.DARK_RED

                        modifier.alignY(AlignmentY.Center)
                            .width(Grow.Std)
                            .text(model)
                            .onChange {
                                model = it
                                if (ResourceLocation.isValidResourceLocation(it) && it.rl in GltfManager.allModels) {
                                    npc.model = it
                                }
                            }
                            .onPositioned {
                                position = Vec2f(it.leftPx, it.bottomPx)
                            }
                    }
                }
            }
            val models =
                GltfManager.allModels.map { it.toString() }.filter { it.startsWith(model, ignoreCase = true) }
                    .filter { it != model }.sorted()
            if (models.isNotEmpty()) {
                val font = MsdfFont(MONOCRAFT, 18f)
                val length = models.maxByOrNull { it.length } ?: ""
                val width = font.textDimensions(length).width.dp + sizes.smallGap * 2f + sizes.gap * 2f
                Popup(position.x, position.y) {
                    modifier.background(null).border(null).zLayer(UiSurface.LAYER_POPUP)
                        .size(
                            width,
                            (22.dp + sizes.smallGap) * models.size.coerceAtMost(10) + sizes.smallGap
                        )

                    LazyColumn(
                        withVerticalScrollbar = false,
                        withHorizontalScrollbar = false,
                        containerModifier = {
                            it.background(null)
                        }
                    ) {
                        modifier.margin(end = sizes.gap)
                        items(models) { resource ->
                            Box(Grow.Std) {
                                val color = hoverColors(1f, Color("1B1E23FF"), Color("252930FF"))
                                modifier.backgroundColor(color).padding(sizes.smallGap)
                                    .onClick {
                                        model = resource
                                        npc.model = model
                                    }

                                Text(resource) {
                                    modifier.font(font)
                                        .width(Grow.Std)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inner class AttributesPanel: DockPanel("hollowengine.gui.tool.attributes", dock) {
        override val icon = "Not used."

        init {
            showOnToolbar = false
        }

        override fun UiScope.compose() {
            modifier.padding(sizes.smallGap)

            LazyColumn {
                items(BuiltInRegistries.ATTRIBUTE.mapNotNull { (npc.getAttribute(it) ?: return@mapNotNull null) to it.descriptionId }) { (attribute, desc) ->
                    Property(desc.lang) {
                        var tempText by remember(attribute.baseValue.toString())

                        TextField {
                            modifier.width(Grow.Std)
                                .text(tempText)
                                .onChange {
                                    tempText = it

                                    it.toDoubleOrNull()?.let { c ->
                                        attribute.baseValue = c
                                    } ?: run {
                                        attribute.baseValue = 0.0
                                    }
                                }
                                .onEnterPressed {
                                    scopeSync {
                                        val result = ScriptingCompiler.compileText<InlineScript>(tempText)
                                            .execute()
                                        result.valueOrNull()?.let {
                                            (it.returnValue as? ResultValue.Value)?.let {
                                                (it.value as? Number)?.let {
                                                    attribute.baseValue = it.toDouble()
                                                    tempText = attribute.baseValue.toString()
                                                }
                                            }
                                        }
                                    }
                                }
                                .alignY(AlignmentY.Center)
                        }
                    }
                }
            }
        }
    }

    inner class EntityPanel : DockPanel("hollowengine.gui.tool.entity", dock) {
        override val icon = "Not used."

        init {
            showOnToolbar = false
        }

        override fun UiScope.compose() {
            modifier.padding(sizes.smallGap)
            Entity(npc) {
                modifier.size(Grow.Std, Grow.Std)
            }
        }
    }
}

@SubscribeEvent(100)
fun registerNpcOptions(event: NpcOptionsEvent) {
    //event.register(NpcOption("options") { NPCCreatorGui(event.npc, event.npc.id).open() })
    //event.register(NpcOption("trades") { TradeMenuGui(event.npc, true).open() })
}

data class NpcOption(val name: String, val onClick: () -> Unit)

class NpcOptionsEvent(private val generator: (NpcOption) -> Unit, val npc: NpcEntity) : Event {
    fun register(npc: NpcOption) {
        generator(npc)
    }
}