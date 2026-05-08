package ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar

import de.fabmax.kool.KeyValueStore
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.editor.GizmoEditMode
import ru.hollowhorizon.hollowengine.client.editor.TransformGizmoEditor
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ScriptFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.sendToast
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.kool.minecraft.SamplerMode
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.BlocksSystemSavedData
import ru.hollowhorizon.hollowengine.common.config.EditMode
import ru.hollowhorizon.hollowengine.common.config.HollowEngineConfig
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.util.DesktopUtil
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.openUrl
import ru.hollowhorizon.hollowengine.generated.Assets

@SubscribeEvent
@ClientOnly
fun leftBarContents(event: TitleBarCreationEvent.Start) = event.append {
    modifier.padding(vertical = Dimensions.PaddingNormal)

    if (HollowEngineConfig.editMode == EditMode.DISABLED) return@append
    if (HollowEngineConfig.editMode == EditMode.CHAT_ONLY && Minecraft.getInstance().screen !is ChatScreen) return@append

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
    TextButton("hollowengine.gui.ide.file".lang) {
        overlay.hide()
        overlay.show(Vec2f(it.screenPosition), SubMenuItem {
            item("hollowengine.gui.ide.file.reload_client_resources".lang, icons.RELOAD_MC) {
                Minecraft.getInstance().reloadResourcePacks()
            }
            item("hollowengine.gui.ide.file.reload_server_resources".lang, icons.RELOAD_MC) {
                ReloadServerResourcesPacket().send()
            }
            item("hollowengine.gui.ide.file.open_mod_folder".lang, Assets.Hollowengine.Textures.Gui.Logo.LOGO) {
                DesktopUtil.openInExplorer(DirectoryManager.HOLLOW_ENGINE.toFile())
            }
        }, Unit)
    }
    val windowOverlay = remember { ItemPopupMenu<Unit>("Title-Window-Overlay") }
    windowOverlay()
    TextButton("hollowengine.gui.ide.windows".lang) {
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

    if (Minecraft.getInstance().player?.hasPermissions(PlayerPermissions.GAMEMASTER) == true) {
        val toolsOverlay = remember { ItemPopupMenu<Unit>("Title-Tools-Overlay") }
        toolsOverlay()
        TextButton("hollowengine.gui.ide.tools".lang) {
            toolsOverlay.show(Vec2f(it.screenPosition), buildToolsMenu(toolsOverlay), Unit)
        }
    }

    val helpOverlay = remember { ItemPopupMenu<Unit>("Title-Help-Overlay") }
    helpOverlay()
    TextButton("hollowengine.gui.ide.help".lang) {
        helpOverlay.show(Vec2f(it.screenPosition), SubMenuItem {
            item("Telegram".lang, icons.DOCS_SVG) {
                openUrl("https://t.me/hollowengine")
            }
            item("Discord".lang, icons.DOCS_SVG) {
                openUrl("https://discord.gg/qKpPhkwGCY")
            }
        }, Unit)
    }
}

private fun buildToolsMenu(overlay: ItemPopupMenu<Unit>): SubMenuItem<Unit> = SubMenuItem {
    item(
        "${if (TransformGizmoEditor.isEnabled) "●" else "○"} " + "hollowengine.gui.ide.gizmo".lang,
        closeOnClick = false
    ) {
        TransformGizmoEditor.toggleEnabled()
        overlay.updateMenu(buildToolsMenu(overlay))
    }
    divider()
    item(
        "${if (TransformGizmoEditor.mode == GizmoEditMode.TRANSLATE) "●" else "○"} " + "hollowengine.gui.ide.gizmo.translate".lang,
        closeOnClick = false
    ) {
        TransformGizmoEditor.setMode(GizmoEditMode.TRANSLATE)
        overlay.updateMenu(buildToolsMenu(overlay))
    }
    item(
        "${if (TransformGizmoEditor.mode == GizmoEditMode.ROTATE) "●" else "○"} " + "hollowengine.gui.ide.gizmo.rotate".lang,
        closeOnClick = false
    ) {
        TransformGizmoEditor.setMode(GizmoEditMode.ROTATE)
        overlay.updateMenu(buildToolsMenu(overlay))
    }
    item(
        "${if (TransformGizmoEditor.mode == GizmoEditMode.SCALE) "●" else "○"} " + "hollowengine.gui.ide.gizmo.scale".lang,
        closeOnClick = false
    ) {
        TransformGizmoEditor.setMode(GizmoEditMode.SCALE)
        overlay.updateMenu(buildToolsMenu(overlay))
    }
}

fun UiScope.Logo() {
    val isHovered by modifier.hoverable()
    val factor by animateFloatAsState(if (isHovered) 1f else 0f, tween(easing = Easing.easeOutQuart))
    val size = Dimensions.PaddingLarge + Dimensions.PaddingSmall * factor

    val buttonOverlay = remember { ItemPopupMenu<Unit>("Editor-Tools-Overlay") }
    buttonOverlay()

    modifier.onClick {
        if (it.isLeftClick) {
            ScriptingEnvironmentOverlay.isCollapsed = !ScriptingEnvironmentOverlay.isCollapsed
        } else if (it.isRightClick) {
            buttonOverlay.show(Vec2f(it.screenPosition), SubMenuItem {
                item("Show") {
                    HollowEngineConfig.editMode = EditMode.ENABLED
                    if (ScriptingEnvironmentOverlay.isCollapsed) {
                        ScriptingEnvironmentOverlay.isCollapsed = false
                    }
                }
                item("Collapse") {
                    if (!ScriptingEnvironmentOverlay.isCollapsed) {
                        ScriptingEnvironmentOverlay.isCollapsed = true
                    }
                }
                item("Hide") {
                    HollowEngineConfig.editMode = EditMode.DISABLED
                }
                item("Show only in chat menu") {
                    HollowEngineConfig.editMode = EditMode.CHAT_ONLY
                }
            }, Unit)
        }
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
@ClientOnly
fun rightBarContents(event: TitleBarCreationEvent.End) = event.append {
    if (IdeContent.files.isEmpty() || ScriptingEnvironmentOverlay.isCollapsed) return@append

    val items =
        IdeContent.files.filter { it.value is ScriptFile }.map { (key, file) ->
            key to Composable {
                Row {
                    modifier.alignY(AlignmentY.Center)

                    Box {
                        Image(file.icon.toString()) {
                            modifier.margin(end = Dimensions.PaddingMedium)
                                .size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        }
                    }

                    Box {
                        Text(file.filePath.substringAfterLast('/')) {
                            modifier
                                .alignY(AlignmentY.Center)
                                .textColor(colors.onBackground)
                        }
                    }
                }
            }
        }
    val itemIndex = remember { mutableStateOf(KeyValueStore.getInt("ide.file_index", -1)) }

    ComboBox("hollowengine.gui.ide.file_picker.empty".lang, items.map { it.second }, itemIndex)

    if (itemIndex.use() != -1 && items.getOrNull(itemIndex.use()) == null) {
        itemIndex.set(-1)
    }
}

private fun UiScope.ActionButton(
    buttonSize: Dimension,
    icon: ResourceLocation,
    action: () -> Unit,
) {
    val isHovered by modifier.hoverable()
    val color by animateColorAsState(
        if (isHovered) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary,
        tween(easing = Easing.easeOutQuart)
    )

    modifier.padding(Dimensions.PaddingNormal)
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
            player.sendSystemMessage("hollowengine.gui.ide.script.no_permissions_start".lang.literal)
            return
        } else {
            val file = path.fromReadablePath()
            val server = player.server ?: return

            if (file.name.endsWith(".bc")) {
                val blocksSystem = BlocksSystemSavedData.get(server)
                blocksSystem.reloadScripts()

                val script = blocksSystem.scripts[path]
                if (script != null) {
                    script.setEnabled(true)
                }
            } else if(file.name.endsWith(".ktr")) {
                val result = server.runtimeContext.katari.run(path, player as ServerPlayer)
                if (result.isSuccess) {
                    player.sendSystemMessage("Katari script started: $path (${result.getOrThrow()})".literal)
                } else {
                    val error = result.exceptionOrNull()
                    HollowEngine.LOGGER.error("Katari script start failed", error)
                    player.sendSystemMessage("Katari script start failed: ${error?.message ?: "Unknown error"}".literal)
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
            player.sendSystemMessage("hollowengine.gui.ide.script.no_permissions_start".lang.literal)
            return
        } else {
            val file = path.fromReadablePath()

            if (file.name.endsWith(".bc")) {
                val server = player.server ?: return
                val blocksSystem = BlocksSystemSavedData.get(server)
                blocksSystem.scripts[path]?.setEnabled(false)
            }

            player.sendToast("hollowengine.gui.ide.script.stopped".lang.literal)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class ReloadServerResourcesPacket : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("hollowengine.gui.ide.file.no_permissions_reload_server".lang.literal)
            return
        }
        val server = player.server ?: return
        server.commands.performPrefixedCommand(player.createCommandSourceStack(), "reload")
    }
}
