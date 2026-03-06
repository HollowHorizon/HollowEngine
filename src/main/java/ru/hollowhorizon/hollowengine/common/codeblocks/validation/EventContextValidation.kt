package ru.hollowhorizon.hollowengine.common.codeblocks.validation

import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.EventOutputVariableBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.root

interface EventContextProvider {
    fun availableEventOutputs(): Set<String>
}

object EventContextValidationRule : ValidationRule {
    override fun validate(rootBlocks: List<BlockModel>): List<ValidationIssue> {
        return rootBlocks.allBlocks()
            .filterIsInstance<EventOutputVariableBlock>()
            .mapNotNull { block ->
                val root = block.root as? StartBlock ?: return@mapNotNull ValidationIssue(
                    block.uuid.toString(),
                    "Event output variable can only be used inside an event branch."
                )
                val provider = root as? EventContextProvider ?: return@mapNotNull ValidationIssue(
                    block.uuid.toString(),
                    "Event output variable can only be used inside an event block that exposes event context."
                )
                if (block.variableName !in provider.availableEventOutputs()) {
                    ValidationIssue(
                        block.uuid.toString(),
                        "Unknown event output '${block.variableName}' for ${root::class.simpleName}."
                    )
                } else {
                    null
                }
            }
    }
}
