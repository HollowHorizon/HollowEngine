package ru.hollowhorizon.hollowengine.common.ui

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.compileHss
import ru.hollowhorizon.hollowengine.common.data.dataKey
import ru.hollowhorizon.hollowengine.common.scripting.ui.UiScript
import ru.hollowhorizon.hollowengine.common.ui.hud.VanillaHudLayers
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.test.*

/**
 * Compiles the shape a real `.ui.kts` body has composables, data reads and `send` from an event
 * handler, so a change to the widget or scope API that would break every UI script fails here
 * rather than at script-compile time in game.
 */
class UiScriptContentTest {
    @AfterTest
    fun tearDown() = UiDefinitionRegistry.clear()

    private val Title = dataKey<String>("title") { "No quest" }
    private val Progress = dataKey<Int>("progress") { 0 }
    private val Entries = dataKey<List<String>>("entries") { emptyList() }

    @Test
    fun `a server-driven screen body compiles against the widget and scope API`() {
        object : UiScript() {
            init {
                screen("mypack:quest_log") {
                    pausesGame = false
                    content {
                        Column(id = "quest-root", modifier = Modifier.padding(12.px).gap(6.px)) {
                            Text(data[Title])
                            Text(data[Progress].toString())
                            data[Entries].forEach { entry -> Text(entry) }
                            Box(modifier = Modifier.onClick { send { putString("action", "accept") } }) {
                                Text("Accept")
                            }
                        }
                    }
                }
            }
        }

        assertNotNull(UiDefinitionRegistry.screen("mypack:quest_log".rl))
    }

    @Test
    fun `a client-only overlay body compiles without touching the server`() {
        object : UiScript() {
            init {
                overlay("mypack:block_hint") {
                    anchor = "crosshair"
                    placement = HudPlacement.AFTER
                    autoShow = true
                    content {
                        // A purely client-side overlay never calls send(); nothing forces a round trip.
                        if (data[Title].isNotEmpty()) {
                            Box(modifier = Modifier.padding(4.px)) { Text(data[Title]) }
                        }
                    }
                }
            }
        }

        assertNotNull(UiDefinitionRegistry.overlay("mypack:block_hint".rl))
    }

    @Test
    fun `an interactive per-frame overlay compiles with input opted in`() {
        object : UiScript() {
            init {
                overlay("mypack:radial") {
                    anchor = "hotbar"
                    interactive()
                    aboveScreens = true
                    rebuildEveryFrame = true
                    content {
                        Box(modifier = Modifier.padding(8.px).onClick { send { putString("action", "pick") } }) {
                            Text(data[Title])
                        }
                    }
                }
            }
        }

        val definition = UiDefinitionRegistry.overlay("mypack:radial".rl)!!
        assertTrue(definition.isInteractive)
        assertTrue(definition.aboveScreens)
        assertTrue(definition.rebuildEveryFrame)
    }

    @Test
    fun `a custom bar anchored between vanilla layers compiles`() {
        val fraction = dataKey<Float>("stamina") { 1f }
        object : UiScript() {
            init {
                overlay("mypack:stamina") {
                    anchor = "experience_bar"
                    placement = HudPlacement.BEFORE
                    content {
                        val value = data[fraction].coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .align(UiAlign.CENTER, UiAlign.END)
                                .size(182.px, 5.px)
                                .background(UiColor(0f, 0f, 0f, 0.5f))
                                .borderRadius(2f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((182f * value).px, 5.px)
                                    .background(UiColor(0.3f, 0.8f, 1f, 0.9f)),
                            )
                        }
                    }
                }
            }
        }

        assertEquals(VanillaHudLayers.EXPERIENCE_BAR, UiDefinitionRegistry.overlay("mypack:stamina".rl)!!.anchor)
    }

    @Test
    fun `a screen styled with inline HSS and a texture compiles`() {
        val styles = compileHss(
            """
            #root { padding: 10px; gap: 6px; background: image("hollowengine:textures/gui/dialogues/dialogue_box.png"); image-fit: 9-slice 16px; }
            .btn { padding: 5px 10px; transition: background 120ms ease-out; }
            .btn:hover { background: #5f8ccd; }
            """.trimIndent()
        )
        object : UiScript() {
            init {
                screen("mypack:styled") {
                    content {
                        Column(id = "root", modifier = Modifier.style(styles)) {
                            Image("hollowengine:textures/gui/npc_menu/character.png", modifier = Modifier.size(24.px, 24.px))
                            Box(tags = listOf("btn"), modifier = Modifier.onClick { close() }) { Text(data[Title]) }
                        }
                    }
                }
            }
        }

        assertNotNull(UiDefinitionRegistry.screen("mypack:styled".rl))
    }
}