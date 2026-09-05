package ru.hollowhorizon.hollowengine.addons.acoustic

import kotlinx.coroutines.CoroutineScope
import ru.hollowhorizon.hollowengine.addons.acoustic.client.AcousticClientIntegration
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonContext
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonEntrypoint
import ru.hollowhorizon.hollowengine.common.addons.publish
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticIntegration
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient

class AcousticAddon : HollowAddonEntrypoint {
    override suspend fun load(context: HollowAddonContext, scope: CoroutineScope) {
        if (isPhysicalClient) AcousticClientIntegration.install(context)
        context.hostServices.publish<AcousticIntegration>(AcousticIntegrationAdapter())
    }
}
