package ru.hollowhorizon.hollowengine.addons.mcp.client

import net.minecraft.client.Minecraft

/** Puts [text] on the clipboard through this game's own window, on the render thread that owns it. */
internal fun copyToClipboard(text: String) {
    val minecraft = Minecraft.getInstance()
    minecraft.execute { minecraft.keyboardHandler.clipboard = text }
}
