package ru.hollowhorizon.hollowengine.common.scripting.nodes

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.scripting.ScriptLoader
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptFingerprint
import ru.hollowhorizon.hollowengine.common.scripting.compiling.CompiledScript
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry

/** One reload pass shares fingerprinting and compilation across all instances of each node script. */
internal class NodeReloadPlan(
    private val fingerprint: (String) -> ScriptFingerprint.Fingerprint? = {
        ScriptFingerprint.compute(ScriptRegistry.parse(it))
    },
    private val compile: (String) -> Result<CompiledScript> = {
        ScriptLoader.compile(ScriptRegistry.parse(it))
    },
) {
    private val fingerprints = mutableMapOf<String, Result<ScriptFingerprint.Fingerprint?>>()
    private val programs = mutableMapOf<String, Result<CompiledScript>>()

    fun replacement(
        path: String,
        previous: ScriptFingerprint.Fingerprint?,
        expectedReceivers: Int? = null,
    ): CompiledScript? {
        val current = fingerprints.getOrPut(path) {
            runCatching { fingerprint(path) }
                .onFailure {
                    HollowEngine.LOGGER.error(
                        "Cannot check node '$path' for changes",
                        it
                    )
                }
        }.getOrNull() ?: return null
        if (current == previous) return null

        return programs.getOrPut(path) {
            runCatching { compile(path).getOrThrow() }.mapCatching { program ->
                check(program.fingerprint == current) {
                    "No current bytecode for '$path'; install the compiler addon to rebuild changed scripts"
                }
                check(NodeScript::class.java.isAssignableFrom(program.type.java)) { "'$path' is no longer a node script" }
                check(expectedReceivers == null || program.implicitReceiverCount == expectedReceivers) {
                    "The attachment target of '$path' changed; detach it and attach it to a compatible host"
                }
                program
            }.onFailure { HollowEngine.LOGGER.error("Cannot reload node '$path'; keeping its running instances", it) }
        }.getOrNull()
    }
}

internal object NodeScriptReload {
    fun reloadChanged(server: MinecraftServer) {
        val plan = NodeReloadPlan()
        runCatching { server.runtimeContext.nodes.reloadChanged(plan) }.onFailure {
            HollowEngine.LOGGER.error(
                "Failed to reload server node scripts",
                it
            )
        }
        runCatching {
            EntityNodeRuntime.reloadChanged(
                server,
                plan
            )
        }.onFailure { HollowEngine.LOGGER.error("Failed to reload entity node scripts", it) }
    }
}
