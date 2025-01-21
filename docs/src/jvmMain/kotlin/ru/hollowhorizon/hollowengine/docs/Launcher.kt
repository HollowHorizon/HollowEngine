package ru.hollowhorizon.hollowengine.docs

import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.scene.scene

fun main() = KoolApplication(
    config = KoolConfigJvm(
        windowTitle = "HollowEngine Docs",
        windowSize = Vec2i(480, 480),
    )
) {
    scene {
        DOCS_GENERATOR()
    }
}