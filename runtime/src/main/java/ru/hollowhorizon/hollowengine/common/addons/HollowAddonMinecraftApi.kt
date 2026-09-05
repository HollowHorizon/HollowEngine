package ru.hollowhorizon.hollowengine.common.addons

import com.mojang.brigadier.CommandDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.RuntimeDispatcherState
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.eventListenerOf
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterClientCommandsEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hollowengine.common.utils.currentServerOrNull
import kotlin.reflect.KClass

/** Reversible runtime interactions with Minecraft. Frozen game registries intentionally live outside this API. */
interface HollowAddonMinecraftApi {
    val addonId: String
    val dispatchers: HollowAddonMinecraftDispatchers

    fun <T : Event> subscribe(
        type: KClass<T>,
        priority: Int = 0,
        listener: (T) -> Unit,
    ): HollowAddonRegistration

    fun registerCommands(
        priority: Int = 0,
        registration: (CommandDispatcher<CommandSourceStack>) -> Unit,
    ): HollowAddonRegistration

    fun registerClientCommands(
        priority: Int = 0,
        registration: (CommandDispatcher<SharedSuggestionProvider>) -> Unit,
    ): HollowAddonRegistration
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

inline fun <reified T : Event> HollowAddonMinecraftApi.subscribe(
    priority: Int = 0,
    noinline listener: (T) -> Unit,
): HollowAddonRegistration = subscribe(T::class, priority, listener)

internal class OwnedHollowAddonMinecraftApi(
    override val addonId: String,
    private val addonScope: CoroutineScope,
    private val classLoader: ClassLoader,
) : HollowAddonMinecraftApi {
    override val dispatchers: HollowAddonMinecraftDispatchers = OwnedMinecraftDispatchers(addonScope, classLoader)

    override fun <T : Event> subscribe(
        type: KClass<T>,
        priority: Int,
        listener: (T) -> Unit,
    ): HollowAddonRegistration {
        val parentJob = requireNotNull(addonScope.coroutineContext[Job]) {
            "Addon '$addonId' scope must contain a Job"
        }
        val subscriptionJob = SupervisorJob(parentJob)
        val subscriptionScope = CoroutineScope(addonScope.coroutineContext + subscriptionJob)
        val eventListener = eventListenerOf(priority) { event: T ->
            if (!subscriptionJob.isActive) return@eventListenerOf
            withHollowAddonClassLoader(classLoader) {
                runCatching { listener(event) }
                    .onFailure { failure ->
                        HollowEngine.LOGGER.error(
                            "Addon '{}' failed while handling event '{}'",
                            addonId,
                            type.qualifiedName,
                            failure,
                        )
                    }
            }
        }
        val handler = EventHandler.get(type)
        return try {
            handler.register(subscriptionScope, eventListener)
            AddonScopeRegistration(subscriptionJob)
        } catch (failure: Throwable) {
            subscriptionJob.cancel()
            throw failure
        }
    }

    override fun registerCommands(
        priority: Int,
        registration: (CommandDispatcher<CommandSourceStack>) -> Unit,
    ): HollowAddonRegistration = subscribe(RegisterCommandsEvent::class, priority) { event ->
        registration(event.dispatcher)
    }

    override fun registerClientCommands(
        priority: Int,
        registration: (CommandDispatcher<SharedSuggestionProvider>) -> Unit,
    ): HollowAddonRegistration {
        check(HollowAddonRuntimeEnvironment.isClient) {
            "Client commands cannot be registered on a dedicated server"
        }
        return subscribe(RegisterClientCommandsEvent::class, priority) { event ->
            registration(event.dispatcher)
        }
    }
}

private class AddonScopeRegistration(
    private val job: Job,
) : HollowAddonRegistration {
    override val isActive: Boolean
        get() = job.isActive

    override fun close() {
        job.cancel()
    }
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
