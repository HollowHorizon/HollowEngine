package ru.hollowhorizon.hollowengine.common.events.client.render

import com.mojang.blaze3d.platform.Window
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.jetbrains.annotations.ApiStatus
import ru.hollowhorizon.hollowengine.common.events.Cancelable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.utils.rl

enum class GuiOverlay(val location: ResourceLocation) {
    VIGNETTE("vignette".rl),
    SPYGLASS("spyglass".rl),
    HELMET("helmet".rl),
    FROSTBITE("frostbite".rl),
    PORTAL("portal".rl),
    HOTBAR("hotbar".rl),
    CROSSHAIR("crosshair".rl),
    BOSS_EVENT_PROGRESS("boss_event_progress".rl),
    PLAYER_HEALTH("player_health".rl),
    ARMOR_LEVEL("armor_level".rl),
    FOOD_LEVEL("food_level".rl),
    AIR_LEVEL("air_level".rl),
    MOUNT_HEALTH("mount_health".rl),
    JUMP_BAR("jump_bar".rl),
    EXPERIENCE_BAR("experience_bar".rl),
    ITEM_NAME("item_name".rl),
    SLEEP_FADE("sleep_fade".rl),
    POTION_ICONS("potion_icons".rl),
    DEBUG_TEXT("debug_text".rl),
    FPS_GRAPH("fps_graph".rl),
    RECORD_OVERLAY("record_overlay".rl),
    TITLE_TEXT("title_text".rl),
    SUBTITLES("subtitles".rl),
    SCOREBOARD("scoreboard".rl),
    CHAT_PANEL("chat_panel".rl),
    PLAYER_LIST("player_list".rl);

    companion object {
        operator fun get(name: ResourceLocation): GuiOverlay? {
            return entries.find { it.location == name }
        }
    }
}

abstract class RenderOverlayEvent @ApiStatus.Internal protected constructor(
    val window: Window,
    val guiGraphics: GuiGraphics,
    val partialTick: Float,
    val overlay: GuiOverlay,
) : Event {
    class Pre(
        window: Window,
        guiGraphics: GuiGraphics,
        partialTick: Float,
        overlay: GuiOverlay,
    ) : RenderOverlayEvent(window, guiGraphics, partialTick, overlay), Cancelable {
        override var isCanceled: Boolean = false
    }

    class Post(
        window: Window,
        guiGraphics: GuiGraphics,
        partialTick: Float,
        overlay: GuiOverlay,
    ) : RenderOverlayEvent(window, guiGraphics, partialTick, overlay)
}