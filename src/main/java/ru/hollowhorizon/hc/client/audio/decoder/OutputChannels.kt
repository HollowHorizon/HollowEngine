package ru.hollowhorizon.hc.client.audio.decoder

class OutputChannels private constructor(val channelsOutputCode: Int) {
    init {
        require(!(channelsOutputCode < 0 || channelsOutputCode > 3)) { "channels" }
    }

    val channelCount: Int
        get() {
            val count = if (channelsOutputCode == BOTH_CHANNELS) 2 else 1
            return count
        }

    override fun equals(o: Any?): Boolean {
        var equals = false

        if (o is OutputChannels) {
            equals = o.channelsOutputCode == channelsOutputCode
        }

        return equals
    }

    override fun hashCode(): Int {
        return channelsOutputCode
    }

    companion object {
        const val BOTH_CHANNELS: Int = 0
        const val LEFT_CHANNEL: Int = 1
        const val RIGHT_CHANNEL: Int = 2
        const val DOWNMIX_CHANNELS: Int = 3

        val LEFT: OutputChannels = OutputChannels(LEFT_CHANNEL)
        val RIGHT: OutputChannels = OutputChannels(RIGHT_CHANNEL)
        val BOTH: OutputChannels = OutputChannels(BOTH_CHANNELS)
        val DOWNMIX: OutputChannels = OutputChannels(DOWNMIX_CHANNELS)

        fun fromInt(code: Int): OutputChannels {
            return when (code) {
                LEFT_CHANNEL -> LEFT
                RIGHT_CHANNEL -> RIGHT
                BOTH_CHANNELS -> BOTH
                DOWNMIX_CHANNELS -> DOWNMIX
                else -> throw IllegalArgumentException("Invalid channel code: $code")
            }
        }
    }
}
