package ru.hollowhorizon.hollowengine.client

import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraft.client.util.InputMappings
import net.minecraft.util.math.vector.Matrix4f
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.client.registry.ClientRegistry
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.screen.ProgressManagerScreen
import ru.hollowhorizon.hollowengine.common.capabilities.storyTeam

object ClientEvents {
    const val HS_CATEGORY = "key.categories.mod.hollowengine"
    val OPEN_EVENT_LIST = KeyBinding(keyBindName("event_list"), GLFW.GLFW_KEY_GRAVE_ACCENT, HS_CATEGORY)

    val PROJ_MAT = Matrix4f().apply { setIdentity() }
    val VIEW_MAT = Matrix4f().apply { setIdentity() }

    private fun keyBindName(name: String): String {
        return java.lang.String.format("key.%s.%s", HollowEngine.MODID, name)
    }

    @JvmStatic
    fun renderLast(event: RenderWorldLastEvent) {
        PROJ_MAT.set(event.projectionMatrix)
        VIEW_MAT.set(event.matrixStack.last().pose())
    }

    @JvmStatic
    fun onKeyPressed(event: InputEvent.KeyInputEvent) {
        if(OPEN_EVENT_LIST.isActiveAndMatches(InputMappings.getKey(event.key, event.scanCode)) && Minecraft.getInstance().screen == null) {
            val manager = Minecraft.getInstance().player?.storyTeam()?.progressManager

            if(manager!=null) Minecraft.getInstance().setScreen(ProgressManagerScreen(manager))
        }
    }

    fun initKeys() {
        ClientRegistry.registerKeyBinding(OPEN_EVENT_LIST)
    }
}