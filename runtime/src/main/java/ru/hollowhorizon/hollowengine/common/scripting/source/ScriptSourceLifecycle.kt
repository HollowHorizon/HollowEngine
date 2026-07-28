package ru.hollowhorizon.hollowengine.common.scripting.source

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.ServerRuntimeState
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.scripting.nodes.EntityNodeRuntime
import ru.hollowhorizon.hollowengine.common.scripting.reload.ReloadScriptManager
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient

/**
 * Keeps everything that runs scripts in step with the namespaces that provide them. Enabling an addon
 * starts its nodes back up and re-registers its UI and reload declarations; disabling one stops its
 * nodes without discarding their saved state.
 */
object ScriptSourceLifecycle : ScriptSourceListener {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            ScriptRegistry.addListener(this)
            installed = true
        }
    }

    override fun onScriptSourceChanged(namespace: String, available: Boolean) {
        ServerRuntimeState.servers().forEach { server ->
            runCatching {
                val nodes = server.runtimeContext.nodes
                if (available) nodes.resumeNamespace(namespace) else nodes.suspendNamespace(namespace)
            }.onFailure { HollowEngine.LOGGER.error("Failed to update server nodes of '$namespace'", it) }
        }
        runCatching {
            if (available) EntityNodeRuntime.resumeNamespace(namespace)
            else EntityNodeRuntime.suspendNamespace(namespace)
        }.onFailure { HollowEngine.LOGGER.error("Failed to update entity nodes of '$namespace'", it) }

        runCatching { ReloadScriptManager.rerun() }
            .onFailure { HollowEngine.LOGGER.error("Failed to rerun reload scripts after '$namespace' changed", it) }

        if (isPhysicalClient) {
            runCatching { reloadUiScripts() }
                .onFailure { HollowEngine.LOGGER.error("Failed to reload UI scripts after '$namespace' changed", it) }
        }
    }

    /**
     * Loaded reflectively: the UI loader is client-only code and this object also runs on a dedicated
     * server, where the class is not present at all.
     */
    private fun reloadUiScripts() {
        val loader = Class.forName("ru.hollowhorizon.hollowengine.client.ui.script.UiScriptLoader")
        val instance = loader.getDeclaredField("INSTANCE").get(null)
        loader.getMethod("reload").invoke(instance)
    }
}
