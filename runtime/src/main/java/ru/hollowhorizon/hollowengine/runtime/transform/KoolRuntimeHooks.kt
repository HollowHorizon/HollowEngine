package ru.hollowhorizon.hollowengine.runtime.transform

import de.fabmax.kool.input.PlatformInput
import de.fabmax.kool.modules.ui2.docking.DockNode
import de.fabmax.kool.modules.ui2.docking.DockNodeLeaf
import de.fabmax.kool.modules.ui2.docking.Dockable
import kotlin.Unit
import kotlin.jvm.functions.Function1
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.kool.window.MCInput
import ru.hollowhorizon.hollowengine.runtime.transform.kool.DockNodeInvoker

object KoolRuntimeHooks {
    @JvmStatic
    fun platformInput(): PlatformInput = MCInput()

    @JvmStatic
    fun copyToClipboard(text: String) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text)
    }

    @JvmStatic
    fun getStringFromClipboard(receiver: Function1<String?, Unit>) {
        val clipboard = Minecraft.getInstance().keyboardHandler.clipboard
        val text = clipboard.ifEmpty { null }
        receiver.invoke(text)
    }

    @JvmStatic
    fun receiveInsertItem(node: DockNode, dockable: Dockable, slotPosition: DockNode.SlotPosition) {
        if (node is DockNodeLeaf) {
            val isFile = IdeContent.files.values.any { file -> file.dockable == dockable }
            if (isFile && node.dockedItems.any(LayoutLoader::contains) && slotPosition == DockNode.SlotPosition.Center) {
                return
            }

            val isPanel = LayoutLoader.LAYOUTS.values.any { panel -> panel.dockable == dockable }
            if (isPanel && node.dockedItems.any(IdeContent::contains) && slotPosition == DockNode.SlotPosition.Center) {
                return
            }
        }

        (node as DockNodeInvoker).callInsertItem(dockable, slotPosition)
    }
}
