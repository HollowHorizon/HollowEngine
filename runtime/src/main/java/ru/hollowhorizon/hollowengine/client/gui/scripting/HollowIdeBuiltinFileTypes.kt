package ru.hollowhorizon.hollowengine.client.gui.scripting

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.image.HollowIdeImageDocument

internal fun HollowIdeFileTypeRegistry.registerBuiltinFileTypes(
    modelEditor: HollowIdeFileEditor,
    imageEditor: HollowIdeFileEditor,
    textEditor: HollowIdeFileEditor,
) {
    register(
        HollowIdeFileType.extensions(
            id = "model",
            extensions = listOf(".gltf", ".glb", ".fbx", ".geo.json"),
            priority = 200,
            loader = { _, _ -> HollowIdeReadOnlyDocument },
            editor = modelEditor,
        ),
    )
    register(
        HollowIdeFileType.extensions(
            id = "image",
            extensions = listOf(".png", ".jpg", ".jpeg"),
            priority = 100,
            loader = ::HollowIdeImageDocument,
            editor = imageEditor,
        ),
    )
    register(
        HollowIdeFileType.fallback(
            id = BuiltinTextFileTypeId,
            matcher = { _, bytes -> bytes.isProbablyText() },
            loader = { _, bytes -> HollowIdeTextDocument(bytes.toString(Charsets.UTF_8)) },
            editor = textEditor,
        ),
    )
}
