package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_DOWN
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_RIGHT
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.AutoRuns

class AutoRunsPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.autoruns", dock) {
    override val icon = "hollowengine:textures/gui/icons/autoruns.svg"

    val levelComponents =
        ComponentRegistry.asSequence().filter { Level::class.java.isAssignableFrom(it.value.type) }.map {
            it.key.location
        }.toList()
    val levelComponentsPopup: AutoPopup = AutoPopup().apply {
        popupContent = Composable {
            val components = levelComponents.filter { it.toString() !in AutoRuns.content.levelComponents }

            LazyColumn(height = (Dp.fromPx(sizes.normalText.lineHeight) + sizes.smallGap * 2) * 7.coerceAtMost(components.size)) {
                items(components) { component ->
                    Text(component.toString()) {
                        modifier.margin(horizontal = sizes.smallGap).padding(sizes.smallGap).width(Grow.Std)
                            .background(
                                RoundRectBackground(
                                    hoverColors(
                                        color = colors.backgroundVariant,
                                        hoverColor = colors.background.mulRgb(1.15f)
                                    ), sizes.smallGap
                                )
                            )
                            .onClick {
                                AutoRuns.content.levelComponents[component.toString()] = false
                                levelComponentsPopup.hide()
                            }
                    }
                }
            }
        }
    }

    val categories: List<Pair<String, UiScope.(filter: MutableStateValue<String>) -> Unit>> = listOf(
        "События" to { Events(it) },
        "Компоненты сервера" to { LevelComponentsPopup(it) },
        "Компоненты сущностей" to { Placeholder() },
    )

    override fun UiScope.compose() {
        modifier.margin(sizes.smallGap)

        Box(Grow.Std, Grow.Std) {
            LazyColumn {
                items(categories) { (name, content) ->
                    val isCollapsed = remember { mutableStateOf(false) }
                    val filter = remember { mutableStateOf("") }
                    Column(Grow.Std) {
                        Header(name, isCollapsed, filter)
                        if (!isCollapsed.use()) {
                            content(filter)
                        }
                    }
                }
            }
            levelComponentsPopup()
        }
    }

    private fun UiScope.Events(value: MutableStateValue<String>) {
        DirectoryManager.eventScripts.filter { it.toReadablePath().contains(value.use(), ignoreCase = true) }
            .forEach { event ->
                Autorun(event.toReadablePath())
            }
    }

    private fun UiScope.Placeholder() {
        mutableListOf(
            "Скрипт А",
            "Скрипт Б",
            "Скрипт В",
            "Скрипт Г",
            "Скрипт Д",
        ).forEach {
            Autorun(it)
        }
    }

    private fun UiScope.Header(
        name: String,
        isCollapsed: MutableStateValue<Boolean>,
        filter: MutableStateValue<String>,
    ) {
        Row(Grow.Std) {
            modifier.padding(sizes.smallGap)
                .background(
                    RoundRectBackground(
                        hoverColors(
                            color = colors.backgroundVariant.mulRgb(1.1f),
                            hoverColor = colors.background.mulRgb(1.15f)
                        ), sizes.smallGap
                    )
                )


            Arrow {
                modifier.rotation(if (isCollapsed.use()) ROTATION_RIGHT else ROTATION_DOWN)
                    .size(20.dp, 20.dp)
                    .onClick { isCollapsed.set(!isCollapsed.value) }
                    .alignY(AlignmentY.Center)
                    .margin(horizontal = sizes.smallGap)
            }

            Image("hollowengine:textures/gui/icons/autoruns.svg") {
                modifier.size(20.dp, 20.dp)
                    .alignY(AlignmentY.Center)
                    .margin(horizontal = sizes.smallGap)
            }

            Text(name) {
                modifier.textAlignY(AlignmentY.Center).alignY(AlignmentY.Center)
            }
            Box(Grow.Std) {}
            TextField(filter.use()) {
                modifier.margin(horizontal = sizes.smallGap)
                modifier.onChange {
                    filter.set(it)
                }
                    .hint("Фильтр")
            }
        }
    }

    private fun UiScope.Autorun(name: String) {
        Row(Grow.Std) {
            modifier.margin(horizontal = sizes.smallGap).padding(sizes.smallGap)
                .background(
                    RoundRectBackground(
                        hoverColors(
                            color = colors.backgroundVariant,
                            hoverColor = colors.background.mulRgb(1.15f)
                        ), sizes.smallGap
                    )
                )

            Text(name) {
                modifier.alignY(AlignmentY.Center)
            }

            Box(Grow.Std) { }

            Text("Автозапуск:") {
                modifier.alignY(AlignmentY.Center)
                    .padding(horizontal = sizes.smallGap)
            }

            Checkbox(name in AutoRuns.content.events) {
                modifier.onToggle {
                    if (it) AutoRuns.content.events.add(name)
                    else AutoRuns.content.events.remove(name)
                }.alignY(AlignmentY.Center)
            }
        }
    }

    private fun UiScope.LevelComponentsPopup(filter: MutableStateValue<String>) {
        AutoRuns.content.levelComponents.filter { it.key.contains(filter.use(), ignoreCase = true) }.forEach { component ->
            Row(Grow.Std) {
                modifier.margin(horizontal = sizes.smallGap).padding(sizes.smallGap)
                    .background(
                        RoundRectBackground(
                            hoverColors(
                                color = colors.backgroundVariant,
                                hoverColor = colors.background.mulRgb(1.15f)
                            ), sizes.smallGap
                        )
                    )

                Image("hollowengine:textures/gui/icons/autocomplete_package.svg") {
                    modifier.size(20.dp, 20.dp)
                        .alignY(AlignmentY.Center)
                        .margin(horizontal = sizes.smallGap)
                }

                Text(component.key) {}

                Box(Grow.Std) {}

                Text("Только первый запуск:") {
                    modifier.alignY(AlignmentY.Center)
                        .padding(horizontal = sizes.smallGap)
                }

                Checkbox(AutoRuns.content.levelComponents[component.key]) {
                    modifier.onToggle {
                        AutoRuns.content.levelComponents[component.key] = it
                    }.alignY(AlignmentY.Center)
                }

                Image("hollowengine:textures/gui/icons/remove.png") {
                    val hoverListener = hoverColors(0.5f, Color("AAFF5588"), Color.WHITE)
                    modifier.tint(hoverListener)
                        .size(20.dp, 20.dp)
                        .alignY(AlignmentY.Center)
                        .margin(horizontal = sizes.smallGap)
                        .onClick {
                            AutoRuns.content.levelComponents.remove(component.key)
                        }
                }
            }
        }

        Button("Добавить компонент") {
            modifier.onClick {
                levelComponentsPopup.show(Vec2f(it.screenPosition.x, it.screenPosition.y))
            }.colors(textColor = Color.WHITE, textHoverColor = Color.WHITE)
                .alignX(AlignmentX.Center)
                .margin(sizes.smallGap)
        }
    }
}