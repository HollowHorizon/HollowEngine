package ru.hollowhorizon.hollowengine

import de.fabmax.kool.util.RingBuffer
import kotlinx.datetime.Clock
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.plugins.Plugin
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.console.LogMessage
import ru.hollowhorizon.hollowengine.common.config.HollowCoreConfig
import ru.hollowhorizon.hollowengine.common.config.hollowConfig
import ru.hollowhorizon.hollowengine.common.utils.molang.compiler.MolangCompiler
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
        logLock.withLock {
            logMessages += msg
            if (msg.isAccepted) filteredLogMessages += msg
        }
    }

    companion object {
        val logLock = ReentrantLock()

        private const val MAX_MESSAGES = 10000

        val logMessages = RingBuffer<LogMessage>(MAX_MESSAGES)
        val filteredLogMessages = RingBuffer<LogMessage>(MAX_MESSAGES)

        @JvmStatic
        fun attach() {
            // Настройка кастомного аппендера для log4j
            val logger = LogManager.getRootLogger() as org.apache.logging.log4j.core.Logger
            val appender = ConsoleAppender()
            appender.start()
            logger.addAppender(appender)
            logger.level = Level.TRACE
        }
    }
}