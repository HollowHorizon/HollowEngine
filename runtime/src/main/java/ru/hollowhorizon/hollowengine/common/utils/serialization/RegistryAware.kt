package ru.hollowhorizon.hollowengine.common.utils.serialization

import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.core.RegistryAccess
import ru.hollowhorizon.hollowengine.common.utils.registryAccess

/**
 * A format that knows which side's registries the data it reads or writes belongs to.
 */
interface RegistryAware {
    val registries: RegistryAccess
}

val Encoder.registries: RegistryAccess get() = (this as? RegistryAware)?.registries ?: registryAccess
val Decoder.registries: RegistryAccess get() = (this as? RegistryAware)?.registries ?: registryAccess
