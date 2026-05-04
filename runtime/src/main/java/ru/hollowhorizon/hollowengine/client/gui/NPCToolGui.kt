package ru.hollowhorizon.hollowengine.client.gui

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockLayout
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader.TOOL_LAYOUT
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Entity
import ru.hollowhorizon.hollowengine.client.kool.KoolManager.MONOCRAFT
import ru.hollowhorizon.hollowengine.client.kool.KoolScreen
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.isValidRL
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.generated.Assets

class NPCToolGui(val npc: NpcEntity) : KoolScreen() {
    var model = ""
    var position = Vec2f.ZERO
    val dock = Dock(scene)

    override fun Scene.setup() {
        val options = HashSet<NpcOption>()
        NpcOptionsEvent.post(NpcOptionsEvent(options::add, npc))

        setupUiScene()

        addNode(dock)
        dock.apply {
            borderWidth.set(IdeTheme.sizes.borderWidth)
            borderColor.set(Color("3C3C4AFF"))
            dockingSurface.sizes = IdeTheme.sizes.copy(normalText = MsdfFont(MONOCRAFT, 18f))
            dockingSurface.colors = IdeTheme.colors

            //TODO: После обновления анимаций больше нет, разве что их контроллер

            val generalPanel = GeneralPanel()
            //val animationsPanel = AnimationsPanel()
            val attributesPanel = AttributesPanel()

            val entityPanel = EntityPanel()

            val layoutLoaded = DockLayout.loadLayout(TOOL_LAYOUT, dock) {
                when (it) {
                    "hollowengine.gui.tool.general" -> generalPanel.dockable
                    "hollowengine.gui.tool.entity" -> entityPanel.dockable
                    "hollowengine.gui.tool.attributes" -> attributesPanel.dockable
                    //      "hollowengine.gui.tool.animations" -> animationsPanel.dockable
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
                //dock.getLeafAtPath("0:row/0:leaf")?.dock(animationsPanel.dockable)
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
        override val icon = Assets.Hollowengine.Textures.Gui.Icons.FILE

        override fun UiScope.compose() {
            modifier.padding(sizes.smallGap)
            Column(Grow.Std) {
                Property("hollowengine.gui.npc_tool.name".lang) {
                    TextField {
                        modifier.alignY(AlignmentY.Center)
                            .text(npc.name)
                            .onChange { npc.name = it; UpdateNamePacket(it, npc.id).send() }
                            .width(Grow.Std)
                    }
                }
                Property("hollowengine.gui.npc_tool.model".lang) {
                    TextField {
                        modifier.textColor =
                            if (model.isValidRL() && model.rl in HollowModelManager.allModels) colors.onBackground else Color.DARK_RED

                        modifier.alignY(AlignmentY.Center)
                            .width(Grow.Std)
                            .text(model)
                            .onChange {
                                model = it
                                if (model.isValidRL() && it.rl in HollowModelManager.allModels) {
                                    //npc.model = it
                                }
                            }
                            .onPositioned {
                                position = Vec2f(it.leftPx, it.bottomPx)
                            }
                    }
                }
            }
            val models =
                HollowModelManager.allModels.map { it.toString() }.filter { it.startsWith(model, ignoreCase = true) }
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
                                val isHovered by modifier.hoverable()
                                val color by animateColorAsState(
                                    if (isHovered) Color("1B1E23FF") else Color("252930FF"),
                                    tween(easing = Easing.easeOutQuart)
                                )
                                modifier.backgroundColor(color).padding(sizes.smallGap)
                                    .onClick {
                                        model = resource
                                        //npc.model = model
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

//    inner class AnimationsPanel : DockPanel("hollowengine.gui.tool.animations", dock) {
//        override val icon = "Not used."
//        private val animationTypes = AnimationType.entries.map {
//            "hollowengine.gui.tool.animations.${it.name.lowercase()}".lang to it
//        }
//
//        init {
//            showOnToolbar = false
//        }
//
//
//        override fun UiScope.compose() {
//            modifier.padding(sizes.smallGap)
//
//            val player = GltfManager.getOrCreate(npc.model.rl).animationPlayer
//            val animationNames = player.nameToAnimationMap.keys
//            val animations = npc[AnimatedEntityCapability::class].animations
//            val currentAnimations by remember(HashMap(player.typeToAnimationMap.mapValues { it.value.name } + animations))
//            var lastAnimationType = AnimationType.IDLE
//
//            LazyColumn {
//                items(animationTypes) { (name, animation) ->
//                    Property(name) {
//                        TextField {
//
//                            modifier.textColor =
//                                if (currentAnimations[animation] in animationNames) colors.onBackground else Color.DARK_RED
//
//                            modifier.alignY(AlignmentY.Center)
//                                .width(Grow.Std)
//                                .text(currentAnimations[animation] ?: "")
//                                .onChange {
//                                    position = Vec2f(uiNode.leftPx, uiNode.bottomPx)
//                                    currentAnimations[animation] = it
//                                    lastAnimationType = animation
//                                    if (it in animationNames || it.isEmpty()) {
//                                        animations[animation] = it
//                                        UpdateAnimationPacket(it, animation, npc.id).send()
//                                    }
//                                }
//                        }
//                    }
//                }
//            }
//
//            val completions =
//                animationNames.filter { it.startsWith(currentAnimations[lastAnimationType] ?: "", ignoreCase = true) }
//                    .filter { it != currentAnimations[lastAnimationType] }.sorted()
//            if (completions.isNotEmpty() && currentAnimations[lastAnimationType]?.isNotEmpty() == true) {
//                val font = MsdfFont(MONOCRAFT, 18f)
//                val length = completions.maxByOrNull { it.length } ?: ""
//                val width = font.textDimensions(length).width.dp + sizes.smallGap * 2f + sizes.gap * 2f
//                Popup(position.x, position.y) {
//                    modifier.background(null).border(null).zLayer(UiSurface.LAYER_POPUP)
//                        .size(
//                            width,
//                            (22.dp + sizes.smallGap) * completions.size.coerceAtMost(10) + sizes.smallGap
//                        )
//
//                    LazyColumn(
//                        withVerticalScrollbar = false,
//                        withHorizontalScrollbar = false,
//                        containerModifier = {
//                            it.background(null)
//                        }
//                    ) {
//                        modifier.margin(end = sizes.gap)
//                        items(completions) { resource ->
//                            Box(Grow.Std) {
//                                val color = hoverColors(1f, Color("1B1E23FF"), Color("252930FF"))
//                                modifier.backgroundColor(color).padding(sizes.smallGap)
//                                    .onClick {
//                                        animations[lastAnimationType] = resource
//                                        currentAnimations[lastAnimationType] = resource
//                                        UpdateAnimationPacket(resource, lastAnimationType, npc.id).send()
//                                    }
//
//                                Text(resource) {
//                                    modifier.font(font)
//                                        .width(Grow.Std)
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }

    inner class AttributesPanel : DockPanel("hollowengine.gui.tool.attributes", dock) {
        override val icon = Assets.Hollowengine.Textures.Gui.Icons.FILE

        override fun UiScope.compose() {
            modifier.padding(sizes.smallGap)


            LazyColumn {
                items(
                    BuiltInRegistries.ATTRIBUTE
                        .holders().toList()
                        .filter { npc.attributes.hasAttribute(it) }
                ) { attribute ->
                    val desc = attribute.value().descriptionId
                    val attributeInstance = npc.getAttribute(attribute)!!
                    val location = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value())?.toString() ?: "unknown"
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

                                }
                                .alignY(AlignmentY.Center)
                        }
                    }
                }
            }
        }
    }

    inner class EntityPanel : DockPanel("hollowengine.gui.tool.entity", dock) {
        override val icon = Assets.Hollowengine.Textures.Gui.Icons.NPCS

        override fun UiScope.compose() {
            modifier.padding(sizes.smallGap)
            Entity({ npc }) {
                modifier.size(Grow.Std, Grow.Std)
            }
        }
    }
}

@ClientOnly
@SubscribeEvent(100)
fun registerNpcOptions(event: NpcOptionsEvent) {
    //event.register(NpcOption("options") { NPCCreatorGui(event.npc, event.npc.id).open() })
    //event.register(NpcOption("trades") { TradeMenuGui(event.npc, true).open() })
}

data class NpcOption(val name: String, val onClick: () -> Unit)

class NpcOptionsEvent(private val generator: (NpcOption) -> Unit, val npc: NpcEntity) : ClientEvent {
    fun register(npc: NpcOption) {
        generator(npc)
    }

    companion object : EventHandler<NpcOptionsEvent>()
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class UpdateNamePacket(private val name: String, private val npcId: Int) : HollowPacket {
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
class UpdateAttributePacket(
    private val attribute: String,
    private val value: Double,
    private val npcId: Int,
) : HollowPacket {
    override fun handle(player: Player) {
        if (player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            (player.level().getEntity(npcId) as? LivingEntity)?.let {
                val attr = BuiltInRegistries.ATTRIBUTE.getHolder(attribute.rl).orElseThrow()
                it.attributes.getInstance(attr)?.baseValue = value
            }
        }
    }
}