package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.EmptyValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.model.DelegatedValue
import com.sunnychung.lib.multiplatform.kotlite.model.IntValue
import com.sunnychung.lib.multiplatform.kotlite.model.KotlinValueHolder
import com.sunnychung.lib.multiplatform.kotlite.model.ListValue
import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.StringValue
import kotlinx.coroutines.test.runTest
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
}
