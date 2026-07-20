package ru.hollowhorizon.hollowengine.common.addons

data class HollowAddonStatus(
    val descriptor: HollowAddonDescriptor,
    val state: HollowAddonState,
    val fileName: String,
    val details: String? = null,
)

enum class HollowAddonState {
    LOADED,
    DISABLED,
    RESTART_REQUIRED,
    WAITING_FOR_DEPENDENCIES,
    REJECTED,
    INACTIVE,
}

data class HollowAddonOperationResult(
    val successful: Boolean,
    val message: String,
)
