package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import com.mojang.brigadier.suggestion.Suggestion
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.spi.StandardLevel
import ru.hollowhorizon.hollowengine.ConsoleAppender.Companion.filteredLogMessages
import ru.hollowhorizon.hollowengine.ConsoleAppender.Companion.logLock
import ru.hollowhorizon.hollowengine.ConsoleAppender.Companion.logMessages
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.console.ConsoleSuggestionProvider
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.console.LogMessage
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.generated.Assets
import kotlin.concurrent.withLock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ConsolePanel(dock: Dock) : DockPanel("hollowengine.gui.ide.console", dock) {
    override val icon = Assets.Hollowengine.Textures.Gui.Icons.CONSOLE

    private val lineProvider = LogLineProvider()

    private val isScrollLock = mutableStateOf(true)

    private var commandInput = ""
    private var suggestions = mutableListOf<Suggestion>()
    private var position = Vec2f.ZERO

    init {
        LogMessage.updateFonts()
    }

    override fun UiScope.compose() {
        Box(Grow.Std, Grow.Std) {
            Column(Grow.Std, Grow.Std) {
                modifier.onClick {
                    suggestions.clear()
                }

                console()

                Row(Grow.Std) {
                    TextField(commandInput) {
                        modifier.width(Grow.Std).margin(horizontal = sizes.gap, vertical = sizes.smallGap)
                            .alignY(AlignmentY.Center).hint("Enter command...").onEnterPressed {
                                executeCommand(commandInput)
                                commandInput = ""
                            }.onChange {
                                commandInput = it
                                suggestions =
                                    ConsoleSuggestionProvider.suggest(commandInput, commandInput.length).toMutableList()
                                position = Vec2f(uiNode.leftPx, uiNode.topPx)
                            }

                    }
                    Button("hollowengine.gui.console.execute".lang) {
                        modifier.margin(
                            start = sizes.gap, end = sizes.gap, top = sizes.smallGap, bottom = sizes.smallGap
                        ).alignY(AlignmentY.Center)
                            .colors(
                                textColor = ColorTheme.UI.WhiteReplacement,
                                textHoverColor = ColorTheme.UI.WhiteReplacement,
                                buttonColor = ColorTheme.UI.BackgroundElements,
                                buttonHoverColor = ColorTheme.UI.BackgroundAccent
                            )
                            .onClick {
                                executeCommand(commandInput)
                                commandInput = ""
                            }
                    }
                }
            }
            Suggestions()
        }
    }

    override fun UiScope.drawHeaderLeft(color: Color) {

        Text("hollowengine.gui.console.level".lang) {
            modifier.alignY(AlignmentY.Center).margin(horizontal = sizes.smallGap)
        }
        ComboBox {
            modifier.width(150.dp).margin(horizontal = sizes.gap).alignY(AlignmentY.Center)
                .items(StandardLevel.entries).selectedIndex(LogMessage.minLevel.use().ordinal).onItemSelected {
                    LogMessage.minLevel.set(StandardLevel.entries[it])
                    updateFilter()
                }
                .padding(vertical = 0.dp)
        }

        Text("hollowengine.gui.console.filter".lang) {
            modifier.alignY(AlignmentY.Center)
        }
        var filterText by remember("")
        TextField(filterText) {
            modifier.margin(horizontal = sizes.gap)
                .colors(lineColor = colors.secondaryVariant, lineColorFocused = colors.secondary)
                .alignY(AlignmentY.Center).hint("hollowengine.gui.console.filter_hint".lang)
                .onEnterPressed { surface.requestFocus(null) }.onChange {
                    filterText = it
                    LogMessage.messageFilter = if (it.isBlank()) null else {
                        try {
                            Regex(it)
                        } catch (e: Exception) {
                            // Логируем ошибку с использованием log4j
                            LogManager.getLogger().warn("Invalid filter regex: ${e.message}")
                            LogMessage.messageFilter
                        }
                    }
                    updateFilter()
                }
        }
    }

    override fun UiScope.drawHeaderRight(color: Color) {
        Image("hollowengine:textures/gui/icons/auto_scroll.svg") {
            val isHovered by modifier.hoverable()
            val hoverColor by animateColorAsState(if(isHovered) colors.background else Color.WHITE, tween(easing = Easing.easeOutQuart))
            val backgroundColor by animateColorAsState(if(isScrollLock.use()) Color("00b003") else hoverColor, tween(easing = Easing.easeOutQuart))

            modifier.margin(horizontal = sizes.smallGap)
                .tint(backgroundColor)
                .onClick {
                    isScrollLock.set(!isScrollLock.value)
                }
                .size(26.dp, 26.dp)
                .alignY(AlignmentY.Center)
        }
    }

    private fun executeCommand(command: String) {
        if (command.isNotBlank()) {
            val client = Minecraft.getInstance()
            if (client.connection != null) {
                client.connection?.sendCommand(command.trimStart('/'))
                LogManager.getLogger().info("Executed command: $command")
            } else {
                LogManager.getLogger().warn("No network handler available for command execution.")
            }
        }
    }

    private fun updateFilter() = logLock.withLock {
        filteredLogMessages.clear()
        filteredLogMessages += logMessages.filter { it.isAccepted }
    }

    private fun UiScope.console() {
        val listState = rememberListState()
        TextArea(
            lineProvider = lineProvider,
            state = listState,
            scrollPaneModifier = { it.margin(horizontal = sizes.gap) },
        ) {
            modifier.lastLineBottomPadding(sizes.largeGap).backgroundColor(null).onWheelY { ev ->
                if (ev.pointer.scroll.y > 0.0) {
                    isScrollLock.set(false)
                } else if (ev.pointer.scroll.y < 0.0 && listState.itemsTo == listState.numTotalItems - 1) {
                    isScrollLock.set(true)
                }
            }

            installDefaultSelectionHandler()

            linesHolder.modifier.isAutoScrollToEnd(isScrollLock.use())
        }
    }

    private fun UiScope.Suggestions() {
        if (suggestions.isEmpty()) return
        val longestLine = suggestions.maxByOrNull { it.text.length }?.text ?: ""
        val width = sizes.normalText.textDimensions(longestLine).width.dp + sizes.smallGap * 2f + sizes.gap * 2f
        val height = (22.dp + sizes.smallGap) * suggestions.size.coerceAtMost(10) + sizes.smallGap

        Popup(position.x, position.y - height.px) {
            modifier.background(null).border(null).zLayer(UiSurface.LAYER_POPUP).size(
                width, height
            )

            LazyColumn(
                withVerticalScrollbar = true, withHorizontalScrollbar = false, containerModifier = {
                    it.background(null)
                }) {
                modifier.margin(end = sizes.gap)
                items(suggestions) { resource ->
                    Box(Grow.Std) {
                        val isHovered by modifier.hoverable()
                        val hoverColor by animateColorAsState(
                            if(isHovered) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary,
                            tween(easing = Easing.easeOutQuart)
                        )

                        modifier.backgroundColor(hoverColor).padding(sizes.smallGap).onClick {
                            commandInput = resource.apply(commandInput)
                        }

                        Text(resource.text + (resource.tooltip?.let { " (${it.string})" } ?: "")) {
                            modifier.width(Grow.Std)
                        }
                    }
                }
            }
        }
    }

    private inner class LogLineProvider : TextLineProvider {
        override val size: Int get() = logLock.withLock { filteredLogMessages.size }
        override fun get(index: Int): TextLine {
            logLock.withLock {
                val logLine = filteredLogMessages[index]
                if (!logLine.isTextValid) {
                    logLine.updateText()
                }
                return logLine.text
            }
        }
    }
}