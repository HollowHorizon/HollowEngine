package ru.hollowhorizon.hollowengine.common.codeblocks.model

import kotlinx.serialization.Serializable

@Serializable
abstract class StartBlock : StatementBlock() {
    abstract suspend fun trigger()

    override suspend fun execute() {
        trigger()
    }
}