package ru.hollowhorizon.hollowengine.common.addons

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import kotlin.test.Test
import kotlin.test.assertEquals

class HollowAddonEventScopeTests {
    @Test
    fun `annotated addon listener is removed when its scope is cancelled`() {
        val scope = CoroutineScope(Job())
        val addon = TestAddon()
        HollowAddonEventRegistrar.registerType(TestAddon::class.java, addon, scope)

        ScopedTestEvent.post(ScopedTestEvent())
        scope.cancel()
        ScopedTestEvent.post(ScopedTestEvent())

        assertEquals(1, addon.calls)
        ScopedTestEvent.clear()
    }

    class TestAddon : HollowAddonEntrypoint {
        var calls = 0

        override suspend fun load(context: HollowAddonContext, scope: CoroutineScope) = Unit

        @SubscribeEvent
        fun onEvent(event: ScopedTestEvent) {
            calls++
        }
    }

    class ScopedTestEvent : Event {
        companion object : EventHandler<ScopedTestEvent>()
    }
}
