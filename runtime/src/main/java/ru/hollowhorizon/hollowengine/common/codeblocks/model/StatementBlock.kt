package ru.hollowhorizon.hollowengine.common.codeblocks.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.*

@Serializable
abstract class StatementBlock : BlockModel() {

    @Transient
    var next: StatementBlock? = null

    @Transient
    var parent: StatementBlock? = null
}

fun StatementBlock.find(uuid: UUID): StatementBlock? = if (this.uuid == uuid) this else next?.find(uuid)
