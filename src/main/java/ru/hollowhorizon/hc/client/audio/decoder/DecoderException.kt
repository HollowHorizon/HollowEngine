package ru.hollowhorizon.hc.client.audio.decoder

import ru.hollowhorizon.hc.client.audio.formats.Mp3Format

class DecoderException(msg: String, t: Throwable? = null) : JavaLayerException(msg, t) {
    private var errorCode: Int = Mp3Format.UNKNOWN_ERROR

    constructor(errorcode: Int, t: Throwable? = null) : this(getErrorString(errorcode), t) {
        this.errorCode = errorcode
    }

    companion object {
        fun getErrorString(errorcode: Int) = "Decoder errorcode " + Integer.toHexString(errorcode)
    }
}
