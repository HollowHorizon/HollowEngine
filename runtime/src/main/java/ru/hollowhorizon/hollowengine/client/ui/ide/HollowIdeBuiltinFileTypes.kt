package ru.hollowhorizon.hollowengine.client.ui.ide

import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeImageDocument

internal fun HollowIdeFileTypeRegistry.registerBuiltinFileTypes(
    modelEditor: HollowIdeFileEditor,
    imageEditor: HollowIdeFileEditor,
    videoEditor: HollowIdeFileEditor,
    textEditor: HollowIdeFileEditor,
) {
    register(
        HollowIdeFileType.extensions(
            id = "model",
            extensions = listOf(".gltf", ".glb", ".fbx", ".geo.json"),
            priority = 200,
            requiresContent = false,
            loader = { _, _ -> HollowIdeReadOnlyDocument },
            editor = modelEditor,
        ),
    )
    register(
        HollowIdeFileType.extensions(
            id = "video",
            extensions = listOf(".mp4"),
            priority = 150,
            requiresContent = false,
            loader = { _, _ -> HollowIdeReadOnlyDocument },
            editor = videoEditor,
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
