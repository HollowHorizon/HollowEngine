package ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar

import de.fabmax.kool.KeyValueStore
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ScriptFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.codeblocks.CodeBlocksFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.sendToast
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.kool.minecraft.SamplerMode
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksComponent
import ru.hollowhorizon.hollowengine.common.codeblocks.createScript
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.registry.ModComponents
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.compiling.start
import ru.hollowhorizon.hollowengine.common.utils.literal

@SubscribeEvent
fun leftBarContents(event: TitleBarCreationEvent.Start) = event.append {
    modifier.padding(vertical = Dimensions.PaddingNormal)

    Box(
        Dimensions.PaddingLarge + Dimensions.PaddingSmall + Dimensions.PaddingMedium * 2f,
        Dimensions.PaddingLarge + Dimensions.PaddingSmall
    ) {
        modifier.alignY(AlignmentY.Center)
        Logo()

    }

    if (ScriptingEnvironmentOverlay.isCollapsed) return@append

    val overlay = remember { ItemPopupMenu<Unit>("Title-File-Overlay") }
    overlay()
    TextButton("File") {
        overlay.hide()
        overlay.show(Vec2f(it.screenPosition), SubMenuItem {
            item("Новый проект") {

            }
            item("Открыть проект") {

            }
            item("Экспортировать проект") {

            }
            item("Закрыть проект") {

            }
            divider()
            item("Перезагрузить ресурсы", icons.RELOAD_MC) {
                Minecraft.getInstance().reloadResourcePacks()
            }
            item("Сбросить индексы") {
            }
            divider()
            item("Выход", icons.EXIT) {
                Minecraft.getInstance().screen?.onClose()
            }
        }, Unit)
    }
    TextButton("Edit")
    val windowOverlay = remember { ItemPopupMenu<Unit>("Title-Window-Overlay") }
    windowOverlay()
    TextButton("Windows") {
        windowOverlay.show(Vec2f(it.screenPosition), SubMenuItem {
            val size = LayoutLoader.LAYOUTS.size
            LayoutLoader.LAYOUTS.values.forEachIndexed { i, window ->
                item(window.name) {
                    window.open()
                }
                if (i != size - 1) divider()
            }
        }, Unit)
    }
    TextButton("Search")

    val settingsOverlay = remember { ItemPopupMenu<Unit>("Title-Settings-Overlay") }
    settingsOverlay()
    TextButton("Info") {
        settingsOverlay.show(Vec2f(it.screenPosition), SubMenuItem {

        }, Unit)
    }
}

fun UiScope.Logo() {
    val isHovered by modifier.hoverable()
    val factor by animateFloatAsState(if (isHovered) 1f else 0f, tween(easing = Easing.easeOutQuart))
    val size = Dimensions.PaddingLarge + Dimensions.PaddingSmall * factor
    modifier.onClick {
        ScriptingEnvironmentOverlay.isCollapsed = !ScriptingEnvironmentOverlay.isCollapsed
    }
    Image("hollowengine:textures/gui/logo/logo.svg", SamplerMode.LINEAR) {
        modifier.size(size, size).align(AlignmentX.Center, AlignmentY.Center)
            .tint(Color.WHITE.withAlpha(0.75f + 0.25f * factor))
    }
}

fun UiScope.TextButton(text: String, onClick: (PointerEvent) -> Unit = {}) {
    Box {
        modifier.alignY(AlignmentY.Center)
            .margin(horizontal = Dimensions.PaddingMedium)
            .padding(Dimensions.PaddingNormal)
        val isHovered by modifier.hoverable()
        val color by animateColorAsState(
            if (isHovered) ColorTheme.UI.ForegroundSecondary else ColorTheme.UI.BackgroundSecondary,
            tween(easing = Easing.easeOutQuart)
        )

        modifier.background(RoundRectBackground(color, sizes.smallGap))

        Text(text) {
            modifier
                .onClick {
                    onClick(it)
                }
        }
    }
}

@SubscribeEvent
fun rightBarContents(event: TitleBarCreationEvent.End) = event.append {
    if (IdeContent.files.isEmpty() || ScriptingEnvironmentOverlay.isCollapsed) return@append

    val items =
        IdeContent.files.filter { it.value is ScriptFileData || it.value is CodeBlocksFileData }.map { (key, file) ->
            key to Composable {
                Row {
                    Box {
                        modifier.alignY(AlignmentY.Center)
                        Image(file.icon.toString()) {
                            modifier.margin(end = sizes.smallGap).size(24.dp, 24.dp)
                                .imageSize(ImageSize.Stretch)
                        }
                    }

                    Box {
                        Text(file.fileName) {
                            modifier
                                .alignY(AlignmentY.Center)
                                .textColor(colors.onBackground)
                        }
                    }
                }
            }
        }
    val itemIndex = remember { mutableStateOf(KeyValueStore.getInt("ide.file_index", -1)) }

    ComboBox.apply {
        comboBox("Empty", items.map { it.second }, itemIndex)
    }

    if (itemIndex.use() != -1) Box {
        val file = items.getOrNull(itemIndex.use())?.first ?: run {
            itemIndex.set(-1)
            return@Box
        }

        ActionButton(24.dp, "hollowengine:textures/gui/icons/play.png") {
            IdeContent.files.values.forEach { it.save() }
            StartScriptPacket(file).send()
        }
    }
}

private fun UiScope.ActionButton(
    buttonSize: Dimension,
    icon: String,
    action: () -> Unit,
) {
    val isHovered by modifier.hoverable()
    val color by animateColorAsState(
        if (isHovered) colors.background else ColorTheme.UI.BackgroundGeneral,
        tween(easing = Easing.easeOutQuart)
    )

    modifier.padding(horizontal = sizes.smallGap)
        .background(RoundRectBackground(color, sizes.smallGap))
        .onClick {
            action()
        }

    Image(icon) {
        modifier.size(buttonSize, buttonSize).alignY(AlignmentY.Center)
    }
}


@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class StartScriptPacket(val path: String) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("You don't have permissions to start scripts!".literal)
            return
        } else {
            val file = path.fromReadablePath()

            if (file.name.endsWith(".bc")) {
                (player.server as ComponentDispatcher).container.get<CodeBlocksComponent>(ModComponents.CODE_BLOCKS_COMPONENT)
                    ?.contexts?.add(createScript(file).also { it.launch() })
            } else {
                val result = ScriptingEnvironment.INSTANCE.compiler.compile(file)
                if (result.isFailure) {
                    HollowEngine.LOGGER.info(result.exceptionOrNull())
                } else {
                    result.getOrThrow().start()
                }
            }
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class CloseScreenPacket : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().screen?.onClose()
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class StopScriptPacket(val path: String) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("You don't have permissions to start scripts!".literal)
            return
        } else {
            val file = path.fromReadablePath()

            //stopScript(file)

            player.sendToast("Скрипт успешно остановлен.".literal)
        }
    }
}