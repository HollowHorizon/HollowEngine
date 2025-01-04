package ru.hollowhorizon.hollowengine.docs

import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.math.Vec2i

fun main() = KoolApplication(
    config = KoolConfigJvm(
        windowTitle = "HollowEngine Docs",
        windowSize = Vec2i(480, 480),
    )
) {
    launchDocs(ctx)
}