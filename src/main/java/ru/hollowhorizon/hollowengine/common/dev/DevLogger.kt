package ru.hollowhorizon.hollowengine.common.dev

import net.minecraft.ChatFormatting
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptInstance
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.VariableMap
import java.util.concurrent.ConcurrentHashMap

object DevLoggerConfig {
    var ENABLED = true
    var SHOW_STACK_DEPTH = true
    var SHOW_VARIABLES = true
    var SHOW_TIMING = true
    var COLORIZE_OUTPUT = true
    var MAX_VARIABLE_COUNT = 5
    var MIN_EXECUTION_TIME_MS = 1L
}

data class BlockExecutionRecord(
    val block: BlockModel,
    val scriptPath: String,
    val startTime: Long,
    var endTime: Long = 0,
    val stackDepth: Int,
    val variables: Map<String, Any?> = emptyMap()
) {
    val executionTimeNanos: Long get() = endTime - startTime
    val executionTimeMs: Double get() = executionTimeNanos / 1_000_000.0
    val isSlow: Boolean get() = executionTimeMs >= DevLoggerConfig.MIN_EXECUTION_TIME_MS
}

class DevLogger private constructor() {
    private val enabled = { DevLoggerConfig.ENABLED }

    private val executionHistory = ConcurrentHashMap<String, MutableList<BlockExecutionRecord>>()
    private val activeTraces = ConcurrentHashMap<String, ActiveTrace>()

    fun startTrace(scriptInstance: ScriptInstance) {
        if (!enabled()) return

        val traceId = "${scriptInstance.ownerFile.path}::${scriptInstance.instanceId}"
        val trace = ActiveTrace(
            scriptPath = scriptInstance.ownerFile.path,
            startTime = System.nanoTime(),
            traceId = traceId
        )
        activeTraces[traceId] = trace

        logTraceStart(trace)
    }

    fun logBlockExecution(
        scriptInstance: ScriptInstance,
        block: BlockModel,
        stackDepth: Int,
        variables: VariableMap
    ) {
        if (!enabled()) return

        val trace = activeTraces.values.find {
            it.scriptPath == scriptInstance.ownerFile.path && !it.completed
        } ?: return

        val record = BlockExecutionRecord(
            block = block,
            scriptPath = scriptInstance.ownerFile.path,
            startTime = trace.startTime,
            stackDepth = stackDepth,
            variables = if (DevLoggerConfig.SHOW_VARIABLES) variables.toList().take(DevLoggerConfig.MAX_VARIABLE_COUNT).toMap() else emptyMap()
        )

        trace.addRecord(record)
        logBlockRecord(record)
    }

    fun endTrace(scriptInstance: ScriptInstance) {
        if (!enabled()) return

        val trace = activeTraces.values.find {
            it.scriptPath == scriptInstance.ownerFile.path && !it.completed
        } ?: return

        trace.complete()
        val history = executionHistory.getOrPut(scriptInstance.ownerFile.path) { mutableListOf() }
        history.addAll(trace.records)

        logTraceEnd(trace)
        activeTraces.remove(trace.traceId)
    }

    fun getExecutionHistory(scriptPath: String): List<BlockExecutionRecord> {
        return executionHistory[scriptPath] ?: emptyList()
    }

    fun getSlowExecutions(scriptPath: String): List<BlockExecutionRecord> {
        return executionHistory[scriptPath]?.filter { it.isSlow } ?: emptyList()
    }

    fun clearHistory() {
        executionHistory.clear()
        activeTraces.clear()
    }

    private fun logTraceStart(trace: ActiveTrace) {
        val message = buildString {
            append(getColor(ChatFormatting.GREEN))
            append("▶ START TRACE")
            append(getReset())
            append(" │ ")
            append(getColor(ChatFormatting.AQUA))
            append(trace.scriptPath)
            append(getReset())
        }
        HollowCore.LOGGER.info(message)
    }

    private fun logBlockRecord(record: BlockExecutionRecord) {
        val prefix = "  ".repeat(record.stackDepth.coerceAtMost(10))
        val blockName = getBlockDisplayName(record.block)

        val message = buildString {
            append(prefix)
            append(getColor(ChatFormatting.WHITE))
            append("├─ ")
            append(getColor(if (record.isSlow) ChatFormatting.RED else ChatFormatting.GRAY))
            append(blockName)
            append(getReset())

            if (DevLoggerConfig.SHOW_TIMING && record.isSlow) {
                append(getColor(ChatFormatting.YELLOW))
                append(" [${String.format("%.3f", record.executionTimeMs)}ms]")
                append(getReset())
            }

            if (DevLoggerConfig.SHOW_VARIABLES && record.variables.isNotEmpty()) {
                append(" │ ")
                append(getColor(ChatFormatting.DARK_PURPLE))
                append(formatVariables(record.variables))
                append(getReset())
            }
        }

        HollowCore.LOGGER.info(message)
    }

    private fun logTraceEnd(trace: ActiveTrace) {
        val totalTimeMs = (trace.endTime - trace.startTime) / 1_000_000.0
        val blockCount = trace.records.size
        val slowCount = trace.records.count { it.isSlow }

        val message = buildString {
            append(getColor(ChatFormatting.RED))
            append("▼ END TRACE")
            append(getReset())
            append(" │ ")
            append(getColor(ChatFormatting.AQUA))
            append(trace.scriptPath)
            append(getReset())
            append(" │ ")
            append(getColor(ChatFormatting.GREEN))
            append("$blockCount blocks")
            append(getReset())
            if (slowCount > 0) {
                append(getColor(ChatFormatting.RED))
                append(", $slowCount slow")
                append(getReset())
            }
            append(getColor(ChatFormatting.YELLOW))
            append(String.format(" [%.3fms]", totalTimeMs))
            append(getReset())
        }
        HollowCore.LOGGER.info(message)
    }

    private fun getBlockDisplayName(block: BlockModel): String {
        return when (block) {
            is StatementBlock -> {
                val simpleName = block.javaClass.simpleName.removeSuffix("Block")
                simpleName.ifEmpty { "Block" }
            }
            else -> "Expression"
        }
    }

    private fun formatVariables(variables: Map<String, Any?>): String {
        return variables.entries.joinToString(", ") { (name, value) ->
            "$name=${formatValue(value)}"
        }
    }

    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"$value\""
            is Double -> String.format("%.2f", value)
            is Float -> String.format("%.2f", value)
            else -> value.toString()
        }
    }

    private fun getColor(formatting: ChatFormatting): String {
        return if (DevLoggerConfig.COLORIZE_OUTPUT) formatting.toString() else ""
    }

    private fun getReset(): String {
        return if (DevLoggerConfig.COLORIZE_OUTPUT) ChatFormatting.RESET.toString() else ""
    }

    private class ActiveTrace(
        val scriptPath: String,
        val startTime: Long,
        val traceId: String
    ) {
        val records = mutableListOf<BlockExecutionRecord>()
        var completed = false
        var endTime: Long = 0

        fun addRecord(record: BlockExecutionRecord) {
            records.add(record)
        }

        fun complete() {
            completed = true
            val completionTime = System.nanoTime()
            endTime = completionTime
            for (rec in records) {
                rec.endTime = completionTime
            }
        }
    }

    companion object {
        val INSTANCE = DevLogger()
    }
}

object DevLogs {
    val logger: DevLogger get() = DevLogger.INSTANCE

    fun startTrace(scriptInstance: ScriptInstance) = logger.startTrace(scriptInstance)
    fun logBlockExecution(scriptInstance: ScriptInstance, block: BlockModel, stackDepth: Int, variables: VariableMap) =
        logger.logBlockExecution(scriptInstance, block, stackDepth, variables)
    fun endTrace(scriptInstance: ScriptInstance) = logger.endTrace(scriptInstance)
    fun getHistory(scriptPath: String) = logger.getExecutionHistory(scriptPath)
    fun getSlow(scriptPath: String) = logger.getSlowExecutions(scriptPath)
    fun clear() = logger.clearHistory()
}


