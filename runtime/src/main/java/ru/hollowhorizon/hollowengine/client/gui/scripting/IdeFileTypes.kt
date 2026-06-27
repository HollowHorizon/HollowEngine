package ru.hollowhorizon.hollowengine.client.gui.scripting

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ScriptFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.GLTFFile
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent

@SubscribeEvent
@ClientOnly
fun registerDefaultFileTypes(event: RegisterFileTypeEvent) {
    event.register(
        listOf(".kts", ".kt", ".ktr"),
        { path, bytes -> ScriptFile(path) }
    )
    
    event.register(
        ".json",
        { path, bytes -> ScriptFile(path) }
    )

    event.register(
        listOf(".ui", ".hss"),
        { path, bytes -> ScriptFile(path) }
    )

    event.register(
        listOf(".gltf", ".glb", ".geo.json", ".fbx"),
        { path, _ -> GLTFFile(path) }
    )

    event.register(".png", { path, bytes -> ImageFile(path, bytes) })
}
