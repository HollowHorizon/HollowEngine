package ru.hollowhorizon.hollowengine.common.addons

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class HollowAddonServiceTests {
    @Test
    fun `owned services are removed during cleanup`() {
        val registry = HollowAddonServiceRegistry()
        val owner = registry.ownedBy("test-addon")
        val service = TestService()

        owner.publishService(TestService::class, service)
        assertSame(service, registry.findService(TestService::class))

        owner.cleanup()
        assertNull(registry.findService(TestService::class))
    }

    private class TestService
}
