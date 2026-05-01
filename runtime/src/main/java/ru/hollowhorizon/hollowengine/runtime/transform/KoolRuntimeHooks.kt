package ru.hollowhorizon.hollowengine.runtime.transform

import de.fabmax.kool.input.PlatformInput
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.kool.window.MCInput
import kotlin.jvm.functions.Function1

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

}
