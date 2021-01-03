package bass

import jouvieje.bass.Bass
import jouvieje.bass.BassInit
import jouvieje.bass.defines.BASS_ATTRIB.BASS_ATTRIB_FREQ
import jouvieje.bass.defines.BASS_ATTRIB.BASS_ATTRIB_VOL
import jouvieje.bass.defines.BASS_POS
import jouvieje.bass.defines.BASS_SAMPLE
import org.openrndr.KEY_ARROW_LEFT
import org.openrndr.KEY_ARROW_RIGHT
import org.openrndr.KEY_SPACEBAR
import org.openrndr.Program

open class Channel() {
    open fun setPosition(seconds: Double) {

    }

    open fun setPitch(pitch: Double) {

    }

    open fun setVolume(volume: Double) {

    }

    open fun getPosition(): Double {
        return 0.0
    }

    open fun play() {
    }

    open fun pause() {
    }

    open fun resume() {
    }
}

class BassChannel(val channel: Int) : Channel() {
    override fun setPosition(seconds: Double) {
        val offset = Bass.BASS_ChannelSeconds2Bytes(channel, seconds)
        Bass.BASS_ChannelSetPosition(channel, offset, BASS_POS.BASS_POS_BYTE)
    }

    override fun setPitch(pitch: Double) {
        Bass.BASS_ChannelSetAttribute(channel, BASS_ATTRIB_FREQ, (pitch * 44100).toFloat())
    }

    override fun setVolume(volume: Double) {
        Bass.BASS_ChannelSetAttribute(channel, BASS_ATTRIB_VOL, volume.toFloat())
    }

    override fun getPosition(): Double {
        val currentOffset = Bass.BASS_ChannelGetPosition(channel, BASS_POS.BASS_POS_BYTE)
        return Bass.BASS_ChannelBytes2Seconds(channel, currentOffset)
    }

    override fun play() {
        Bass.BASS_ChannelPlay(channel, false)
    }

    override fun pause() {
        Bass.BASS_ChannelPause(channel)
    }

    override fun resume() {
        Bass.BASS_ChannelPlay(channel, false)
    }
}

fun initBass() {
    BassInit.loadLibraries()
    Bass.BASS_Init(-1, 44100, 0, null, null)
}

fun Program.playMusic(path: String, timescale: Double = 1.0, scrubbable: Boolean = true, loop: Boolean = true, dummy:Boolean = false): Channel {

    if (!dummy) {
        initBass()
        val stream = Bass.BASS_StreamCreateFile(false, path, 0, 0, if (loop) BASS_SAMPLE.BASS_SAMPLE_LOOP else 0)
        val channel = BassChannel(stream.asInt()).apply {
            play()
        }
        var pitch = 1.0
        var volume = 1.0
        var paused = false
        if (scrubbable) {
            keyboard.keyDown.listen {
                if (it.key == KEY_ARROW_RIGHT) {
                    channel.setPosition(channel.getPosition() + timescale)
                }
                if (it.key == KEY_ARROW_LEFT) {
                    channel.setPosition((channel.getPosition() - timescale).coerceAtLeast(0.0))
                }

                if (it.key == KEY_SPACEBAR) {
                    paused = !paused
                    if (paused) {
                        channel.pause()
                    } else {
                        channel.resume()
                    }
                }

            }
            keyboard.character.listen {
                if (it.character == 'q') {
                    pitch /= 2.0
                    channel.setPitch(pitch)
                }
                if (it.character == 'w') {
                    pitch *= 2.0
                    channel.setPitch(pitch)
                }
                if (it.character == 'm') {
                    volume = 1.0 - volume
                    channel.setVolume(volume)
                }
                if (it.character == 'p') {
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
    } else {
        return Channel()
    }
}