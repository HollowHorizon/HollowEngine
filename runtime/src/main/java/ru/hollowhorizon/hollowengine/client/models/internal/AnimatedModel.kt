package ru.hollowhorizon.hollowengine.client.models.internal


import ru.hollowhorizon.hollowengine.client.models.internal.animations.AnimationClip


class AnimatedModel(val model: Model) {
    val animations: Map<String, AnimationClip> = model.animations.associateBy { it.name }
    val nodes = model.walkNodes().toList()

    fun destroy() {
        model.walkNodes().mapNotNull { it.mesh }.flatMap { it.primitives }.forEach(Primitive::destroy)
    }

    companion object {
        val EMPTY = AnimatedModel(Model(0, listOf(), setOf(), listOf()))
    }
}
