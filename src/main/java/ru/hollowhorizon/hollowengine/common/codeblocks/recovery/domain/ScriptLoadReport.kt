package ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain

import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel

data class ScriptLoadReport(
    val blocks: List<BlockModel>,
    val issues: List<ScriptLoadIssue>,
) {
    val hasIssues: Boolean get() = issues.isNotEmpty()
}

