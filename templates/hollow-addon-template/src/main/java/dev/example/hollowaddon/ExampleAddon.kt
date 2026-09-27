package dev.example.hollowaddon

import kotlinx.coroutines.CoroutineScope
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonContext
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonEntrypoint

class ExampleAddon : HollowAddonEntrypoint {
    override suspend fun load(context: HollowAddonContext, scope: CoroutineScope) {
        context.hostServices.log("${context.descriptor.id} loaded")
    }
}
