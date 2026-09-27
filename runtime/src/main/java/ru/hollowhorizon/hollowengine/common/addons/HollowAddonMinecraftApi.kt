package ru.hollowhorizon.hollowengine.common.addons

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.coroutines.RuntimeDispatcherState
import ru.hollowhorizon.hollowengine.common.utils.currentServerOrNull

/** Addon-scoped scheduling on Minecraft's server and client threads. */
interface HollowAddonMinecraftApi {
    val addonId: String
    val dispatchers: HollowAddonMinecraftDispatchers
}

/** Dispatches addon work only while the owning addon is active. */
interface HollowAddonMinecraftDispatchers {
    fun serverOrNull(): CoroutineDispatcher?

    fun clientOrNull(): CoroutineDispatcher?

    fun executeServer(action: () -> Unit): Boolean

    fun executeClient(action: () -> Unit): Boolean
}

val HollowAddonContext.minecraft: HollowAddonMinecraftApi
    get() = koin.get()

internal class OwnedHollowAddonMinecraftApi(
    override val addonId: String,
    private val addonScope: CoroutineScope,
    private val classLoader: ClassLoader,
) : HollowAddonMinecraftApi {
    override val dispatchers: HollowAddonMinecraftDispatchers = OwnedMinecraftDispatchers(addonScope, classLoader)
}

private class OwnedMinecraftDispatchers(
    private val addonScope: CoroutineScope,
    private val classLoader: ClassLoader,
) : HollowAddonMinecraftDispatchers {
    override fun serverOrNull(): CoroutineDispatcher? = currentServerOrNull()?.let { server ->
        runCatching { RuntimeDispatcherState.serverDispatcher(server) }.getOrNull()
    }

    override fun clientOrNull(): CoroutineDispatcher? {
        if (!HollowAddonRuntimeEnvironment.isClient) return null
        return ClientAccess.dispatcherOrNull()
    }

    override fun executeServer(action: () -> Unit): Boolean {
        val server = currentServerOrNull() ?: return false
        if (!addonScope.isActive) return false
        val guarded = {
            if (addonScope.isActive) withHollowAddonClassLoader(classLoader, action)
        }
        if (server.isSameThread) guarded() else server.execute(guarded)
        return true
    }

    override fun executeClient(action: () -> Unit): Boolean {
        if (!HollowAddonRuntimeEnvironment.isClient || !addonScope.isActive) return false
        return ClientAccess.execute(addonScope.coroutineContext[Job], classLoader, action)
    }

    private object ClientAccess {
        fun dispatcherOrNull(): CoroutineDispatcher? {
            val client = Minecraft.getInstance()
            return runCatching { RuntimeDispatcherState.clientDispatcher(client) }.getOrNull()
        }

        fun execute(job: Job?, classLoader: ClassLoader, action: () -> Unit): Boolean {
            val client = Minecraft.getInstance()
            client.execute {
                if (job?.isActive != false) withHollowAddonClassLoader(classLoader, action)
            }
            return true
        }
    }
}
