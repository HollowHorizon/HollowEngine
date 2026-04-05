import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.models.internal.manager.ModelReloadCoordinator
import ru.hollowhorizon.hollowengine.client.models.internal.manager.PreparedModelUpdate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ModelReloadCoordinatorTests {
    @Test
    fun `reload targets include cached models without metadata`() {
        val cached = setOf(ResourceLocation("test", "models/custom/no_meta.gltf"))
        val indexed = setOf(ResourceLocation("test", "models/indexed/with_meta.gltf"))

        val targets = ModelReloadCoordinator.reloadTargets(cached, indexed)

        assertEquals(setOf(cached.first(), indexed.first()), targets)
    }

    @Test
    fun `failed reload keeps previous model alive`() {
        val empty = Any()
        val current = Any()

        val swap = ModelReloadCoordinator.resolveSwap(
            current = current,
            prepared = PreparedModelUpdate(exists = true, loaded = Result.failure(IllegalStateException("boom"))),
            empty = empty,
        )

        assertSame(current, swap.next)
        assertNull(swap.retired)
    }

    @Test
    fun `successful reload retires previous model`() {
        val empty = Any()
        val current = Any()
        val loaded = Any()

        val swap = ModelReloadCoordinator.resolveSwap(
            current = current,
            prepared = PreparedModelUpdate(exists = true, loaded = Result.success(loaded)),
            empty = empty,
        )

        assertSame(loaded, swap.next)
        assertSame(current, swap.retired)
    }

    @Test
    fun `missing model clears cache entry`() {
        val empty = Any()
        val current = Any()

        val swap = ModelReloadCoordinator.resolveSwap(
            current = current,
            prepared = PreparedModelUpdate<Any>(exists = false),
            empty = empty,
        )

        assertSame(empty, swap.next)
        assertSame(current, swap.retired)
    }
}
