package ru.hollowhorizon.hollowengine.client.gui

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_DOWN
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockLayout
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.kool.Entity
import ru.hollowhorizon.hc.client.kool.KoolManager.MONOCRAFT
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.client.models.internal.animations.AnimationType
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.internal.manager.GltfManager
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.utils.literal
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader.TOOL_LAYOUT
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.loadMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.inline.InlineScript
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.model
import ru.hollowhorizon.hollowengine.common.util.Node
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.util.toNode
import ru.hollowhorizon.hollowengine.ecs.ComponentRegistry
import ru.hollowhorizon.hollowengine.ecs.RegisterComponent
import ru.hollowhorizon.hollowengine.ecs.npc.NpcComponentsCapability
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
            val animationsPanel = AnimationsPanel()
            val attributesPanel = AttributesPanel()
            val componentsPanel = ComponentsPanel()

            val entityPanel = EntityPanel()

            val layoutLoaded = DockLayout.loadLayout(TOOL_LAYOUT, dock) {
                when (it) {
                    "hollowengine.gui.tool.general" -> generalPanel.dockable
                    "hollowengine.gui.tool.entity" -> entityPanel.dockable
                    "hollowengine.gui.tool.attributes" -> attributesPanel.dockable
                    "hollowengine.gui.tool.components" -> componentsPanel.dockable
                    "hollowengine.gui.tool.animations" -> animationsPanel.dockable
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
                dock.getLeafAtPath("0:row/0:leaf")?.dock(componentsPanel.dockable)
                dock.getLeafAtPath("0:row/0:leaf")?.dock(attributesPanel.dockable)
                dock.getLeafAtPath("0:row/0:leaf")?.dock(animationsPanel.dockable)
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
                            .onChange { npc.name = it; UpdateNamePacket(it, npc.id).send() }
                            .width(Grow.Std)
                    }
                }
                Property("Модель") {
                    TextField {
                        modifier.textColor =
                            if (ResourceLocation.isValidResourceLocation(model) && model.rl in GltfManager.allModels) colors.onBackground else Color.DARK_RED

                        modifier.alignY(AlignmentY.Center)
                            .width(Grow.Std)
                            .text(model)
                            .onChange {
                                model = it
                                if (ResourceLocation.isValidResourceLocation(it) && it.rl in GltfManager.allModels) {
                                    npc.model = it
                                    UpdateModelPacket(it, npc.id).send()
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
                                        UpdateModelPacket(model, npc.id).send()
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

    inner class AnimationsPanel : DockPanel("hollowengine.gui.tool.animations", dock) {
        override val icon = "Not used."
        private val animationTypes = AnimationType.entries.map {
            "hollowengine.gui.tool.animations.${it.name.lowercase()}".lang to it
        }

        init {
            showOnToolbar = false
        }


        override fun UiScope.compose() {
            modifier.padding(sizes.smallGap)

            val player = GltfManager.getOrCreate(npc.model.rl).animationPlayer
            val animationNames = player.nameToAnimationMap.keys
            val animations = npc[AnimatedEntityCapability::class].animations
            val currentAnimations by remember(HashMap(player.typeToAnimationMap.mapValues { it.value.name } + animations))
            var lastAnimationType = AnimationType.IDLE

            LazyColumn {
                items(animationTypes) { (name, animation) ->
                    Property(name) {
                        TextField {

                            modifier.textColor =
                                if (currentAnimations[animation] in animationNames) colors.onBackground else Color.DARK_RED

                            modifier.alignY(AlignmentY.Center)
                                .width(Grow.Std)
                                .text(currentAnimations[animation] ?: "")
                                .onChange {
                                    position = Vec2f(uiNode.leftPx, uiNode.bottomPx)
                                    currentAnimations[animation] = it
                                    lastAnimationType = animation
                                    if (it in animationNames || it.isEmpty()) {
                                        animations[animation] = it
                                        UpdateAnimationPacket(it, animation, npc.id).send()
                                    }
                                }
                        }
                    }
                }
            }

            val completions =
                animationNames.filter { it.startsWith(currentAnimations[lastAnimationType] ?: "", ignoreCase = true) }
                    .filter { it != currentAnimations[lastAnimationType] }.sorted()
            if (completions.isNotEmpty() && currentAnimations[lastAnimationType]?.isNotEmpty() == true) {
                val font = MsdfFont(MONOCRAFT, 18f)
                val length = completions.maxByOrNull { it.length } ?: ""
                val width = font.textDimensions(length).width.dp + sizes.smallGap * 2f + sizes.gap * 2f
                Popup(position.x, position.y) {
                    modifier.background(null).border(null).zLayer(UiSurface.LAYER_POPUP)
                        .size(
                            width,
                            (22.dp + sizes.smallGap) * completions.size.coerceAtMost(10) + sizes.smallGap
                        )

                    LazyColumn(
                        withVerticalScrollbar = false,
                        withHorizontalScrollbar = false,
                        containerModifier = {
                            it.background(null)
                        }
                    ) {
                        modifier.margin(end = sizes.gap)
                        items(completions) { resource ->
                            Box(Grow.Std) {
                                val color = hoverColors(1f, Color("1B1E23FF"), Color("252930FF"))
                                modifier.backgroundColor(color).padding(sizes.smallGap)
                                    .onClick {
                                        animations[lastAnimationType] = resource
                                        currentAnimations[lastAnimationType] = resource
                                        UpdateAnimationPacket(resource, lastAnimationType, npc.id).send()
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

    inner class AttributesPanel : DockPanel("hollowengine.gui.tool.attributes", dock) {
        override val icon = "Not used."

        init {
            showOnToolbar = false
        }

        override fun UiScope.compose() {
            modifier.padding(sizes.smallGap)

            LazyColumn {
                items(BuiltInRegistries.ATTRIBUTE.filter { npc.attributes.hasAttribute(it) }) { attribute ->
                    val desc = attribute.descriptionId
                    val attributeInstance = npc.getAttribute(attribute)!!
                    val location = BuiltInRegistries.ATTRIBUTE.getKey(attribute)?.toString() ?: "unknown"
                    Property(desc.lang) {
                        var tempText by remember(attributeInstance.baseValue.toString())

                        TextField {
                            modifier.width(Grow.Std)
                                .text(tempText)
                                .onChange {
                                    tempText = it

                                    it.toDoubleOrNull()?.let { c ->
                                        attributeInstance.baseValue = c
                                        UpdateAttributePacket(location, c, npc.id).send()
                                    } ?: run {
                                        attributeInstance.baseValue = 0.0
                                        UpdateAttributePacket(location, 0.0, npc.id).send()
                                    }
                                }
                                .onEnterPressed {
                                    scopeSync {
                                        val result = ScriptingCompiler.compileText<InlineScript>(tempText)
                                            .execute()
                                        result.valueOrNull()?.let {
                                            (it.returnValue as? ResultValue.Value)?.let {
                                                (it.value as? Number)?.let {
                                                    attributeInstance.baseValue = it.toDouble()
                                                    UpdateAttributePacket(location, it.toDouble(), npc.id).send()
                                                    tempText = attributeInstance.baseValue.toString()
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

    inner class ComponentsPanel : DockPanel("hollowengine.gui.tool.components", dock) {
        override val icon = "Not used."

        init {
            showOnToolbar = false
        }

        override fun UiScope.compose() {
            modifier.padding(sizes.smallGap)

            val components =
                npc.components.map { it.javaClass.getAnnotation(RegisterComponent::class.java).location to it }

            Column(Grow.Std, Grow.Std) {
                modifier.margin(vertical = sizes.smallGap)
                val componentsPopup = remember { ItemPopupMenu<Node>("scene-item-popup") }
                componentsPopup()
                LazyColumn(containerModifier = { it.background(null) }) {
                    items(components) { (name, component) ->
                        Column(Grow.Std) {
                            modifier.padding(sizes.smallGap)
                                .border(RectBorder(Color.WHITE, sizes.borderWidth))

                            var isExpanded by remember(false)

                            Row(Grow.Std) {
                                modifier.backgroundColor(colors.backgroundMid)
                                    .padding(sizes.smallGap)

                                Text("component.hollowengine.${name.replace('/', '.')}".lang) {
                                    modifier.width(Grow.Std)
                                }
                                Arrow(isHoverable = false) {
                                    modifier.rotation(if (isExpanded) ROTATION_DOWN else ArrowScope.ROTATION_LEFT)
                                        .size(14.dp, 14.dp)
                                        .alignY(AlignmentY.Center)
                                        .colors(arrowColor = Color.WHITE, Color.WHITE)
                                        .onClick { isExpanded = !isExpanded }
                                }
                                CloseButton(
                                    background = colors.backgroundMid,
                                    backgroundHover = colors.backgroundMid.mulRgb(1.2f),
                                    foreground = Color.WHITE,
                                    foregroundHover = Color.WHITE
                                ) {
                                    npc[NpcComponentsCapability::class].components
                                        .removeIf { it.javaClass.getAnnotation(RegisterComponent::class.java).location == name }
                                }
                            }
                            if (isExpanded) {
                                divider()
                                Column(width = Grow.Std) {
                                    modifier.padding(sizes.smallGap)
                                    component()
                                }
                            }
                        }
                    }
                }
                Row {
                    modifier.align(AlignmentX.Center, AlignmentY.Bottom)
                    Button("Добавить компонент") {
                        modifier.textColor(Color.WHITE)
                        modifier.textHoverColor = Color.WHITE
                        modifier.onClick {
                            val node = ComponentRegistry.NPC_COMPONENTS.keys.toNode()
                            componentsPopup.hide()
                            componentsPopup.show(it.screenPosition, loadMenu(node) {
                                ComponentRegistry.NPC_COMPONENTS[it.path]?.let {
                                    npc.components.add(it(npc))
                                }
                            }, node)
                        }
                    }
                    Box {
                        modifier.margin(sizes.smallGap)
                    }
                    Button("Сихнронизировать") {
                        modifier.textColor(Color.WHITE)
                        modifier.textHoverColor = Color.WHITE
                        modifier.onClick {
                            npc[NpcComponentsCapability::class].isChanged = true
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

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class UpdateNamePacket(private val name: String, private val npcId: Int) : HollowPacket<UpdateNamePacket> {
    override fun handle(player: Player) {
        if (player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            player.level().getEntity(npcId)?.let {
                it.customName = name.literal
                it.isCustomNameVisible = name.isNotEmpty()
            }
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class UpdateModelPacket(private val model: String, private val npcId: Int) : HollowPacket<UpdateNamePacket> {
    override fun handle(player: Player) {
        if (player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            player.level().getEntity(npcId)?.get(AnimatedEntityCapability::class)?.let {
                it.model = model
            }
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class UpdateAnimationPacket(
    private val animationName: String,
    private val animationType: AnimationType,
    private val npcId: Int,
) : HollowPacket<UpdateNamePacket> {
    override fun handle(player: Player) {
        if (player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            player.level().getEntity(npcId)?.get(AnimatedEntityCapability::class)?.let {
                it.animations[animationType] = animationName
            }
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class UpdateAttributePacket(
    private val attribute: String,
    private val value: Double,
    private val npcId: Int,
) : HollowPacket<UpdateAttributePacket> {
    override fun handle(player: Player) {
        if (player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            (player.level().getEntity(npcId) as? LivingEntity)?.let {
                it.attributes.getInstance(BuiltInRegistries.ATTRIBUTE.get(attribute.rl)!!)?.baseValue = value
            }
        }
    }
}