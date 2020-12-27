package bass
import jouvieje.bass.Bass
import jouvieje.bass.BassInit
import jouvieje.bass.defines.BASS_ATTRIB.BASS_ATTRIB_FREQ
import jouvieje.bass.defines.BASS_ATTRIB.BASS_ATTRIB_VOL
import jouvieje.bass.defines.BASS_POS
import jouvieje.bass.defines.BASS_SAMPLE
import org.openrndr.KEY_ARROW_LEFT
import org.openrndr.KEY_ARROW_RIGHT
import org.openrndr.Program

class Channel(val channel: Int) {
    fun setPosition(seconds: Double) {
        val offset = Bass.BASS_ChannelSeconds2Bytes(channel, seconds)
        Bass.BASS_ChannelSetPosition(channel, offset, BASS_POS.BASS_POS_BYTE)
    }

    fun setPitch(pitch: Double) {
        Bass.BASS_ChannelSetAttribute(channel, BASS_ATTRIB_FREQ, (pitch*44100).toFloat())
    }

    fun setVolume(volume: Double) {
        Bass.BASS_ChannelSetAttribute(channel, BASS_ATTRIB_VOL, volume.toFloat())
    }

    fun getPosition(): Double {
        val currentOffset = Bass.BASS_ChannelGetPosition(channel, BASS_POS.BASS_POS_BYTE)
        return Bass.BASS_ChannelBytes2Seconds(channel, currentOffset)
    }

    fun play() {
        Bass.BASS_ChannelPlay(channel, false)
    }

    fun pause() {
        Bass.BASS_ChannelPause(channel)
    }

    fun resume() {
        Bass.BASS_ChannelPlay(channel, false)
    }



}

fun initBass() {
    BassInit.loadLibraries()
    Bass.BASS_Init(-1, 44100, 0, null, null)
}

fun Program.playMusic(path: String, scrubbable:Boolean = true): Channel {

    initBass()
    val stream = Bass.BASS_StreamCreateFile(false, path, 0, 0, BASS_SAMPLE.BASS_SAMPLE_LOOP)

    val channel =  Channel(stream.asInt()).apply {
        play()
    }

    var pitch = 1.0
    var volume = 1.0
    var paused = false
    if (scrubbable) {

        keyboard.keyDown.listen {
            if (it.key == KEY_ARROW_RIGHT) {
                channel.setPosition(channel.getPosition() + 5.0)
            }
            if (it.key == KEY_ARROW_LEFT) {
                channel.setPosition((channel.getPosition() - 5.0).coerceAtLeast(0.0))
            }

       }
        keyboard.character.listen {
            if (it.character=='q') {
                pitch/=2.0
                println("setting pitch to $pitch")
                channel.setPitch(pitch)
            }
            if (it.character=='w') {
                pitch*=2.0
                channel.setPitch(pitch)
            }
            if (it.character=='m') {
                volume = 1.0 - volume
                channel.setVolume(volume)
            }
            if (it.character=='p') {
                paused = !paused
                if (paused) {
                    channel.pause()
                } else {
                    channel.resume()
                }
            }
        }

        clock = { channel.getPosition() }
    }
    return channel
}