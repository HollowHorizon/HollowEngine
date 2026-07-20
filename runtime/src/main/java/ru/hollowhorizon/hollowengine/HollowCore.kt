package ru.hollowhorizon.hollowengine

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.config.plugins.Plugin
import ru.hollowhorizon.hollowengine.common.logging.HollowLogStore
import ru.hollowhorizon.hollowengine.common.utils.molang.compiler.MolangCompiler

object HollowCore {
    const val MODID: String = "hollowengine"

    @JvmField
    val LOGGER: Logger = ru.hollowhorizon.hollowengine.LOGGER

    init {
        MolangCompiler
    }
}

@Plugin(name = "ConsoleAppender", category = "Core", elementType = Appender.ELEMENT_TYPE)
class ConsoleAppender : AbstractAppender(AppenderName, null, null, true, Property.EMPTY_ARRAY) {
    override fun append(event: LogEvent) {
        val message = buildString {
            append(event.message.formattedMessage)
            event.thrown?.let { throwable ->
                if (isNotEmpty()) appendLine()
                append(throwable.stackTraceToString())
            }
        }
        HollowLogStore.append(
            level = event.level.standardLevel,
            loggerName = event.loggerName,
            message = message,
            timeMillis = event.timeMillis,
        )
    }

    companion object {
        @Synchronized
        @JvmStatic
        fun attach() {
            val logger = LogManager.getRootLogger() as org.apache.logging.log4j.core.Logger
            if (logger.appenders.containsKey(AppenderName)) return
            val appender = ConsoleAppender()
            appender.start()
            logger.addAppender(appender)
        }

        private const val AppenderName = "ConsoleAppender"
    }
}

enum class Platform {
    FABRIC, FORGE, NEOFORGE
}
