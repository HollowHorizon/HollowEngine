package ru.hollowhorizon.hollowengine.common.project.kt.util

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine

object LoggingMessageCollector: MessageCollector {
	override fun clear() {}

	override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
		HollowEngine.LOGGER.debug("Kotlin compiler: [{}] {} @ {}", severity, message, location)
	}

	override fun hasErrors() = false
}
