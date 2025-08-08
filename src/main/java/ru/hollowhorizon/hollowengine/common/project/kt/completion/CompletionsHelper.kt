package ru.hollowhorizon.hollowengine.common.project.kt.completion

import org.jetbrains.kotlin.resolve.scopes.SyntheticScope
import org.jetbrains.kotlin.resolve.scopes.SyntheticScopes
import org.jetbrains.kotlin.synthetic.JavaSyntheticScopes

fun SyntheticScopes.forceEnableSamAdapters(): SyntheticScopes {
    return if (this !is JavaSyntheticScopes)
        this
    else
        object : SyntheticScopes {
            override val scopes: Collection<SyntheticScope> =
                this@forceEnableSamAdapters.scopesWithForceEnabledSamAdapters
        }
}