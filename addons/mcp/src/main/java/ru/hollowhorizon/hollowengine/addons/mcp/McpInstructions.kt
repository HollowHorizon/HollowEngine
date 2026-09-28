package ru.hollowhorizon.hollowengine.addons.mcp

import ru.hollowhorizon.hollowengine.addons.mcp.tools.ServerAccess
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient

const val COMPILER_MISSING = "The Kotlin compiler addon is not installed in this game, so Kotlin code can be " +
    "neither analyzed nor compiled. Ask the user to put the HollowEngineCompiler jar from the same " +
    "HollowEngine release into the mods folder (or hollowengine/addons) and restart the game."

/**
 * What an agent reads when it connects: the working guide shipped as `instructions.md` next to the
 * docs, with this game's paths filled in, followed by the game as it is right now.
 */
internal class McpInstructions(classLoader: ClassLoader) {
    private val guide = classLoader.getResourceAsStream(GUIDE)?.bufferedReader()?.use { it.readText() }
        ?.replace("{scripts}", DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toString())
        ?.replace("{log}", DirectoryManager.HOLLOW_ENGINE.resolveSibling("logs").resolve("latest.log").toString())
        ?.trimEnd()
        .orEmpty()

    fun render(): String = buildString {
        appendLine(guide)
        appendLine()
        appendLine("## Right now")
        appendLine()
        appendLine("This is ${sideDescription()}.")
        if (!ScriptingEnvironment.isAvailable()) appendLine(COMPILER_MISSING)
    }.trimEnd()

    private fun sideDescription(): String {
        val server = ServerAccess.current()
        return when {
            !isPhysicalClient -> "a dedicated server"
            server != null -> "a game client with a local world open; screenshot is available"
            else -> "a game client in the menu or on a remote server, so only client-side tools and snippets " +
                "work until a local world is opened; screenshot is available"
        }
    }

    private companion object {
        const val GUIDE = "hollowengine-mcp/instructions.md"
    }
}
