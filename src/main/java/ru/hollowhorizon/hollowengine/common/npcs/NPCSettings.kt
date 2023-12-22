package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.Serializable

@Serializable
data class Attributes(val attributes: Map<String, Float> = mapOf()) {
    constructor(vararg attributes: Pair<String, Float>) : this(attributes.toMap())
}
