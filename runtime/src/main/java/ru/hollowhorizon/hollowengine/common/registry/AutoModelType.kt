package ru.hollowhorizon.hollowengine.common.registry

/**
 * Possibility to select automatic model generation.
 */
fun interface AutoModelType {
    fun modelId(): String
    fun blockStateId(): String = "default"

    companion object {
        val DEFAULT = AutoModelType { "item/generated" }
        val HANDHELD = AutoModelType { "item/handheld" }
        val CUBE_ALL = AutoModelType { "block/cube_all" }
        fun custom(type: String, blockState: String = "default") = object : AutoModelType {
            override fun modelId() = type
            override fun blockStateId() = blockState
        }
    }
}
