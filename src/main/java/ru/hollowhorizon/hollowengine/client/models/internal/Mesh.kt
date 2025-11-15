package ru.hollowhorizon.hollowengine.client.models.internal

data class Mesh(
    val primitives: List<Primitive>,
    val weights: FloatArray,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Mesh

        if (primitives != other.primitives) return false
        if (!weights.contentEquals(other.weights)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = primitives.hashCode()
        result = 31 * result + weights.contentHashCode()
        return result
    }
}