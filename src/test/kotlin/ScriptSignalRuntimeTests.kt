import de.fabmax.kool.util.Color
import kotlinx.coroutines.test.runTest
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.*
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.assertEquals

private fun signalUnsafe(): Unsafe {
    val field = Unsafe::class.java.getDeclaredField("theUnsafe")
    field.isAccessible = true
    return field.get(null) as Unsafe
}

private fun setSignalField(target: Any, name: String, value: Any?) {
    var type: Class<*>? = target.javaClass
    while (type != null) {
        runCatching {
            val field = type.getDeclaredField(name)
            field.isAccessible = true
            field.set(target, value)
            return
        }
        type = type.superclass
    }
    error("Field $name not found on ${target.javaClass.name}")
}

private fun fakeSignalSystem(): BlocksSystem {
    val system = signalUnsafe().allocateInstance(BlocksSystem::class.java) as BlocksSystem
    setSignalField(system, "dirtyListener", {})
    return system
}

private class SignalRecordingBlock(
    private val log: MutableList<String>,
) : StatementBlock() {
    override val color: Color get() = Color.WHITE

    override suspend fun execute() {
        log += "script=${currentFile().path}"
        log += "owner=${currentInstance().ownerKey}"
        log += "signal=${currentScriptSignal()?.name}"
    }

    override fun InputSlotScope.composeContent() = Unit
}

class ScriptSignalRuntimeTests {
    @Test
    fun `call signal executes handler with script and signal context`() = runTest {
        val log = mutableListOf<String>()
        val handler = ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnEventBlock("quest_signal")
        val body = SignalRecordingBlock(log)
        handler.next = body
        body.parent = handler

        val file = ScriptFile(fakeSignalSystem(), "tests/signal.bc", listOf(handler, body))
        file.callSignal(
            ScriptSignal(
                name = "quest_signal",
                scope = SignalScope.LOCAL,
                owner = OwnerKey.Global,
                sourceScriptPath = file.path,
                payload = "payload",
            )
        )

        assertEquals(
            listOf(
                "script=tests/signal.bc",
                "owner=Global",
                "signal=quest_signal",
            ),
            log,
        )
    }
}
