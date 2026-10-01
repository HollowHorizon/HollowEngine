package ru.hollowhorizon.hollowengine.common.utils.math

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class VectorSerializerTest {
    /** NBT and JSON both number the components of a list strictly, so a skipped index fails on write. */
    private fun <T> assertRoundTrips(serializer: KSerializer<T>, value: T) {
        assertEquals(value, NBTFormat.deserialize(serializer, NBTFormat.serialize(serializer, value)))
        assertEquals(value, Json.decodeFromString(serializer, Json.encodeToString(serializer, value)))
    }

    @Test
    fun `every vector round-trips with all of its components`() {
        assertRoundTrips(Vec3f.Vec3Serializer, Vec3f(1f, 2f, 3f))
        assertRoundTrips(Vec3d.Vec3Serializer, Vec3d(1.0, 2.0, 3.0))
        assertRoundTrips(Vec3i.Vec3Serializer, Vec3i(1, 2, 3))
        assertRoundTrips(Vec4f.Vec4Serializer, Vec4f(1f, 2f, 3f, 4f))
        assertRoundTrips(Vec4d.Vec4Serializer, Vec4d(1.0, 2.0, 3.0, 4.0))
        assertRoundTrips(Vec4i.Vec4Serializer, Vec4i(1, 2, 3, 4))
    }
}
