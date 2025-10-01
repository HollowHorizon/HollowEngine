/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package ru.hollowhorizon.hollowengine

import de.fabmax.kool.util.Time
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.plugins.Plugin
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.ConsolePanel.Companion.filteredLogMessages
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.ConsolePanel.Companion.logMessages
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.ConsolePanel.LogMessage
import ru.hollowhorizon.hollowengine.common.config.HollowCoreConfig
import ru.hollowhorizon.hollowengine.common.config.hollowConfig
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.molang.compiler.MolangCompiler

object HollowCore {
    const val MODID: String = "hollowengine"
    val platform: Platform =
    //? if forge {
            /*Platform.FORGE
            *///?} elif neoforge {
            /*Platform.NEOFORGE
            *///?} else {
        Platform.FABRIC
    //?}

    @JvmField
    val LOGGER: Logger = ru.hollowhorizon.hollowengine.LOGGER
    val config by hollowConfig(::HollowCoreConfig, "hollowcore")

    init {
        // Настройка кастомного аппендера для log4j
        val logger = LogManager.getRootLogger() as org.apache.logging.log4j.core.Logger
        val appender = ConsoleAppender()
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.TRACE


        MolangCompiler
    }

    enum class Platform {
        FABRIC, FORGE, NEOFORGE
    }
}

@Plugin(name = "ConsoleAppender", category = "Core", elementType = Appender.ELEMENT_TYPE)
class ConsoleAppender : AbstractAppender("ConsoleAppender", null, null, true) {

    override fun append(event: LogEvent) {
        val msg = LogMessage(
            event.level.standardLevel,
            event.loggerName,
            event.message.formattedMessage,
            Clock.System.now()
        )
        Minecraft.getInstance().coroutineScope.launch {
            logMessages += msg
            if(msg.isAccepted) filteredLogMessages += msg
        }
    }
}