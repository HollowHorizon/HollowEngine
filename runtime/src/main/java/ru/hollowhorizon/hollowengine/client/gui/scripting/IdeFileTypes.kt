package ru.hollowhorizon.hollowengine.client.gui.scripting

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ScriptFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.animations.AnimationControllerFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.codeblocks.CodeBlocksFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.GLTFFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.ItemPrefabEditorFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.PrefabEditorFile
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent

@SubscribeEvent
fun registerDefaultFileTypes(event: RegisterFileTypeEvent) {
    event.registerSuffix(
        ".controller.json",
        { path, bytes -> AnimationControllerFile(path, bytes) }
    )
    
    event.register(
        listOf(".kts", ".kt"),
        { path, bytes -> ScriptFile(path) }
    )
    
    event.register(
        ".json",
        { path, bytes -> ScriptFile(path) }
    )
    
    event.register(".bc", { path, bytes -> CodeBlocksFile(path, bytes) })
    event.register(".txt", { path, bytes -> TextFile(path, bytes) })
    
    event.register(
        listOf(".gltf", ".glb", ".geo.json", ".fbx"),
        { path, _ -> GLTFFile(path) }
    )

    event.registerSuffix(
        ".entity.prefab",
        { path, bytes -> PrefabEditorFile(path, bytes) }
    )
    event.registerSuffix(
        ".item.prefab",
        { path, bytes -> ItemPrefabEditorFile(path, bytes) }
    )
    
    event.register(".png", { path, bytes -> ImageFile(path, bytes) })
}
