package ru.hollowhorizon.hollowengine.addons.acoustic

import kotlinx.coroutines.CoroutineScope
import ru.hollowhorizon.hollowengine.addons.acoustic.client.AcousticClientIntegration
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonContext
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonEntrypoint
import ru.hollowhorizon.hollowengine.common.addons.publish
import ru.hollowhorizon.hollowengine.common.addons.project.HollowProject
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.scripting.NODE_SCRIPT_EXTENSION
import ru.hollowhorizon.hollowengine.common.scripting.RELOAD_SCRIPT_EXTENSION
import ru.hollowhorizon.hollowengine.common.scripting.ScriptDefaultImportsEvent
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient

class AcousticAddon : HollowAddonEntrypoint {
    override suspend fun load(context: HollowAddonContext, scope: CoroutineScope) {
        if (isPhysicalClient) AcousticClientIntegration.install(context)
        context.hostServices.publish<AcousticIntegration>(AcousticIntegrationAdapter())
    }

    @SubscribeEvent
    fun addScriptImports(event: ScriptDefaultImportsEvent) {
        if ("hollowengine-acoustic" in HollowProject.properties().dependsOn &&
            (event.suffix == NODE_SCRIPT_EXTENSION || event.suffix == RELOAD_SCRIPT_EXTENSION)) {
            event.defaultImports += "ru.hollowhorizon.hollowengine.addons.acoustic.*"
        }
    }
}
