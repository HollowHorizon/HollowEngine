package ru.hollowhorizon.hollowengine.common.codeblocks.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
abstract class StatementBlock: BlockModel() {
    @Transient
    var next: StatementBlock? = null

    @Transient
    var parent: StatementBlock? = null
}