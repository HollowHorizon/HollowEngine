package ru.hollowhorizon.hc.common.utils.cbor

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor

@OptIn(ExperimentalSerializationApi::class)
val CborFormat = Cbor {
    ignoreUnknownKeys = true
}