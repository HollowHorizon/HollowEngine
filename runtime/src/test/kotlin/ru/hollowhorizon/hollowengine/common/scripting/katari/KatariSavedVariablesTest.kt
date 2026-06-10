package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.EmptyValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.model.*
import kotlinx.coroutines.test.runTest
import ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots.PlayerSnapshot
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KatariSavedVariablesTest {
    @Test
    fun `saved variable snapshot preserves generic list values`() = runTest {
        val codec = createHollowKatariEditorBindings().snapshotCodec
        val symbolTable = codec.symbolTable()
        val original = ListValue(
            listOf(StringValue("first", symbolTable), StringValue("second", symbolTable)),
            symbolTable.StringType,
            symbolTable,
        )

        val tag = serializeKatariSavedRuntimeValue(original, codec)
        val restored = restoreKatariSavedRuntimeValue(tag, codec, EmptyValueRestoreContext)

        val list = assertIs<KotlinValueHolder<*>>(restored)
        val values = assertIs<List<RuntimeValue>>(list.value).map { value -> assertIs<StringValue>(value).value }
        assertEquals(listOf("first", "second"), values)
        assertEquals("String", assertIs<DelegatedValue<*>>(restored).typeArguments.single().name)
    }

    @Test
    fun `saved variable snapshot preserves primitive values`() = runTest {
        val codec = createHollowKatariEditorBindings().snapshotCodec
        val symbolTable = codec.symbolTable()

        val tag = serializeKatariSavedRuntimeValue(IntValue(42, symbolTable), codec)
        val restored = restoreKatariSavedRuntimeValue(tag, codec, EmptyValueRestoreContext)

        assertEquals(42, assertIs<IntValue>(restored).value)
    }

    @Test
    fun `saved variable snapshot restores host references lazily`() = runTest {
        val codec = createHollowKatariEditorBindings().snapshotCodec
        val symbolTable = codec.symbolTable()
        val uuid = UUID.randomUUID()
        val original = NarrativeHostValue("Player", PlayerSnapshot(uuid), symbolTable)

        val tag = serializeKatariSavedRuntimeValue(original, codec)
        val restored = restoreKatariSavedRuntimeValue(tag, codec, EmptyValueRestoreContext)

        val host = assertIs<NarrativeHostValue>(restored)
        val snapshot = assertIs<PlayerSnapshot>(host.value)
        assertEquals("Player", host.typeId)
        assertEquals(uuid, snapshot.uuid)
    }
}
