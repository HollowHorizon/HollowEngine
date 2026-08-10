package ru.hollowhorizon.hollowengine.common.scripting.ui

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.ui.*
import ru.hollowhorizon.hollowengine.common.ui.hud.VanillaHudLayers

/**
 * Base class of `.ui.kts` scripts. The script body declares screens and HUD overlays; both are
 * addressed by id, which is what the server opens and what other scripts anchor against.
 *
 * ```kotlin
 * val Title = dataKey<String>("title") { "No quest" }
 *
 * screen("mypack:quest_log") {
 *     pausesGame = false
 *     content {
 *         Column(modifier = Modifier.style("mypack:ui/quest.hss")) {
 *             Text(data[Title])
 *             Box(modifier = Modifier.onClick { send { putString("action", "accept") } }) { Text("Accept") }
 *         }
 *     }
 * }
 * ```
 */
abstract class UiScript {
    fun screen(id: String, body: UiScreenBuilder.() -> Unit) {
        val location = ResourceLocation.parse(id)
        UiDefinitionRegistry.register(UiScreenBuilder(location).apply(body).build())
    }

    fun overlay(id: String, body: UiOverlayBuilder.() -> Unit) {
        val location = ResourceLocation.parse(id)
        UiDefinitionRegistry.register(UiOverlayBuilder(location).apply(body).build())
    }
}

class UiScreenBuilder internal constructor(private val id: ResourceLocation) {
    /** Narration title; also what the game shows for the screen. */
    var title: String = id.path

    /** Whether Escape closes the screen. Turn off for screens the server must dismiss itself. */
    var closeOnEscape: Boolean = true

    /** Whether the screen pauses a singleplayer world while open. */
    var pausesGame: Boolean = false

    /** Recomposes the screen every frame; enable only for content that tracks live game state. */
    var rebuildEveryFrame: Boolean = false

    /**
     * The GUI scale this screen lays itself out at, independent of the player's video setting:
     * `guiScale = UiGuiScale.Auto` or `guiScale(3)`. A screen built around fixed pixel sizes wants
     * this, at scale 4 a window meant to take half the screen takes all of it.
     */
    var guiScale: UiGuiScale = UiGuiScale.Inherit

    /** Shorthand for `guiScale = UiGuiScale.of(factor)`; `0` means auto. */
    fun guiScale(factor: Int) {
        guiScale = UiGuiScale.of(factor)
    }

    /**
     * How long the screen keeps drawing after the server closes it, in milliseconds the room its
     * exit animation needs. The server does not wait for it: the session ends at once and whatever
     * opened the screen carries on, while the closing frames play out here.
     */
    var exitDuration: Long = 0L

    private var content: UiContent? = null

    fun content(body: UiContent) {
        content = body
    }

    internal fun build(): UiScreenDefinition = UiScreenDefinition(
        id = id,
        title = title,
        closeOnEscape = closeOnEscape,
        pausesGame = pausesGame,
        rebuildEveryFrame = rebuildEveryFrame,
        guiScale = guiScale,
        exitDuration = exitDuration,
        content = content ?: error("UI screen '$id' declares no content { } block"),
    )
}

class UiOverlayBuilder internal constructor(private val id: ResourceLocation) {
    /**
     * The HUD layer this overlay draws next to; see [VanillaHudLayers]. Accepts bare vanilla names
     * (`"crosshair"`) as well as fully qualified ids.
     */
    var anchor: String = VanillaHudLayers.HOTBAR.toString()

    /** Whether this overlay draws before or after its [anchor]. */
    var placement: HudPlacement = HudPlacement.AFTER

    /**
     * Shows the overlay as soon as the script loads, without waiting for the server. This is the
     * mode for client-owned HUD, a hint about the block under the crosshair has no business
     * round-tripping through the server every frame.
     */
    var autoShow: Boolean = false

    /**
     * Whether this overlay takes pointer/keyboard input. Draw-only ([OverlayInput.NONE]) is the
     * default; set [OverlayInput.INTERACTIVE] for an overlay the player clicks. The engine does not
     * force a round trip either way — an interactive overlay can still handle everything client-side.
     */
    var input: OverlayInput = OverlayInput.NONE

    /**
     * Whether the overlay also draws (and takes input, if interactive) on top of an open screen,
     * rather than only over the in-game HUD. Use it for things that must stay visible above a menu.
     */
    var aboveScreens: Boolean = false

    /** Recomposes the overlay every frame; enable for content that tracks live game state per frame. */
    var rebuildEveryFrame: Boolean = false

    private var content: UiContent? = null

    fun content(body: UiContent) {
        content = body
    }

    /** Shorthand for `input = OverlayInput.INTERACTIVE`. */
    fun interactive() {
        input = OverlayInput.INTERACTIVE
    }

    internal fun build(): UiOverlayDefinition = UiOverlayDefinition(
        id = id,
        anchor = VanillaHudLayers.parse(anchor),
        placement = placement,
        autoShow = autoShow,
        input = input,
        aboveScreens = aboveScreens,
        rebuildEveryFrame = rebuildEveryFrame,
        content = content ?: error("UI overlay '$id' declares no content { } block"),
    )
}
