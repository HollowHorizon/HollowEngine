package ru.hollowhorizon.hollowengine.common.scripting.story

import ru.hollowhorizon.hc.common.scripting.kotlin.AbstractHollowScriptConfiguration
import ru.hollowhorizon.hc.common.scripting.kotlin.HollowScriptConfiguration
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.SequenceNode
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    displayName = "Story Event",
    fileExtension = "se.kts",
    compilationConfiguration = StoryEventConfiguration::class
)
class StoryEvent: SequenceNode() {
}

class StoryEventConfiguration: AbstractHollowScriptConfiguration({

})
