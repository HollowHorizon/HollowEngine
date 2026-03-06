package ru.hollowhorizon.hollowengine.common.codeblocks.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.RepeatPolicy

@Serializable
abstract class StartBlock : StatementBlock() {
    @SerialName("repeat_policy")
    var repeatPolicy: RepeatPolicy = RepeatPolicy.RESTART

    @SerialName("branch_group_key")
    var branchGroupKey: String? = null

    abstract suspend fun trigger()

    override suspend fun execute() {
        trigger()
    }
}
