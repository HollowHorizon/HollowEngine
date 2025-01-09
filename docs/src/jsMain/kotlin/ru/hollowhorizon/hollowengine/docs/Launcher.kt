package ru.hollowhorizon.hollowengine.docs

import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJs

fun main() = KoolApplication(
    config = KoolConfigJs(
        canvasName = "HollowEngine Docs"
    )
) {
    launchDocs(ctx)
}