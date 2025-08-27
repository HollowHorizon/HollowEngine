package ru.hollowhorizon.hc.client.audio.decoder


class BitstreamException(msg: String, t: Throwable?) : JavaLayerException(msg, t) {
    var errorCode: Int = Bitstream.UNKNOWN_ERROR
        private set

    constructor(errorcode: Int, t: Throwable?) : this(getErrorString(errorcode), t) {
        this.errorCode = errorcode
    }

    companion object {
        fun getErrorString(errorcode: Int): String {
            return "Bitstream errorcode " + Integer.toHexString(errorcode)
        }
    }
}
