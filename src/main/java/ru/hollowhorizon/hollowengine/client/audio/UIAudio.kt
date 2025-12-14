package ru.hollowhorizon.hollowengine.client.audio

import ru.hollowhorizon.hollowengine.client.audio.formats.WavFormat
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.utils.rl

object UIAudio {
    val CONNECT = SoundPlayer(SoundBuffer("hollowengine:audio/ui/connect.wav".rl.stream.use(WavFormat::read)))
    val SELECT = SoundPlayer(SoundBuffer("hollowengine:audio/ui/select.wav".rl.stream.use(WavFormat::read)))
}