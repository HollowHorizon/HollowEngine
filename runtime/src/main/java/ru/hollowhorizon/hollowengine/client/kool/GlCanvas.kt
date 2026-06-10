package ru.hollowhorizon.hollowengine.client.kool

import de.fabmax.kool.modules.ui2.ImageScope
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.size
import net.minecraft.world.item.ItemStack
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.client.render.render

inline fun UiScope.Item(stack: ItemStack, scopeName: String? = null, block: ImageScope.() -> Unit = {}) =
    GlCanvas(scopeName, {
        GL33.glDepthFunc(GL33.GL_LEQUAL)
        stack.render(x, y, width, height)
    }) {
        modifier.size(16.dp, 16.dp)
        block()
    }
