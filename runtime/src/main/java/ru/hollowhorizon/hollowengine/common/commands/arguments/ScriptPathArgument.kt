package ru.hollowhorizon.hollowengine.common.commands.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry

/**
 * A script path as commands accept it: `scripts/nodes/quest.node.kts` for the local directory,
 * `my-addon:nodes/quest.node.kts` for a namespace. Unlike a plain string argument it takes `/` and `:`
 * without quotes, and unlike a greedy string it stops at the next space, so a path can be followed by
 * further arguments.
 */
class ScriptPathArgument : ArgumentType<ScriptId> {
    override fun parse(reader: StringReader): ScriptId {
        val start = reader.cursor

        val raw = if (reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) {
            reader.readQuotedString()
        } else {
            while (reader.canRead() && isAllowed(reader.peek())) reader.skip()
            reader.string.substring(start, reader.cursor)
        }
        if (raw.isEmpty()) {
            reader.cursor = start
            throw EXPECTED_PATH.createWithContext(reader)
        }
        val id = runCatching { ScriptRegistry.parse(raw) }.getOrElse {
            reader.cursor = start
            throw EXPECTED_PATH.createWithContext(reader)
        }
        if (id.path.isEmpty()) {
            reader.cursor = start
            throw EXPECTED_PATH.createWithContext(reader)
        }
        return id
    }

    override fun getExamples(): Collection<String> = EXAMPLES

    private fun isAllowed(character: Char): Boolean =
        character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' ||
            character == '_' || character == '.' || character == '-' || character == '/' || character == ':'

    companion object {
        private val EXAMPLES = listOf("scripts/nodes/example.node.kts", "my-addon:nodes/example.node.kts")

        private val EXPECTED_PATH = SimpleCommandExceptionType(
            Component.translatable("hollowengine.commands.expected_script_path"),
        )

        fun scriptPath(): ScriptPathArgument = ScriptPathArgument()

        fun getScript(context: CommandContext<CommandSourceStack>, name: String): ScriptId =
            context.getArgument(name, ScriptId::class.java)
    }
}
