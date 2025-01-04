package ru.hollowhorizon.hollowengine.docs

import de.fabmax.kool.KoolContext
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color

fun launchDocs(ctx: KoolContext) {
    if(Docs in ctx.scenes) return

    ctx.scenes += Docs
}

fun closeDocs(ctx: KoolContext) {
    ctx.scenes -= Docs
}

object Docs: Scene() {
    init {
        setupUiScene()

        addPanelSurface {
            modifier.align(AlignmentX.Center, AlignmentY.Center)

            Box {
                modifier.backgroundColor(Color.DARK_BLUE)
                    .padding(sizes.gap)

                Text("Hello docs!") {}
            }
        }
    }
}