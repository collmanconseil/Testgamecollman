package com.testgamecollman.luciole

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.pow

/**
 * Petit synthétiseur façon micro-ordinateur 8 bits : deux voix en ondes carrées
 * pour la musique, une voix pour les bruitages. Tout est calculé à la volée.
 */
class ChipAudio(private val song: Song?) {

    /** Mélodie et basse : suites de (note MIDI ou 0 pour un silence, durée en doubles croches). */
    class Song(val bpm: Int, lead: IntArray, bass: IntArray) {
        val leadNote: IntArray
        val leadStart: IntArray
        val leadLen: IntArray
        val bassNote: IntArray
        val bassStart: IntArray
        val bassLen: IntArray
        val steps: Int

        init {
            val l = expand(lead)
            val b = expand(bass)
            steps = l[0].size
            require(b[0].size == steps) { "mélodie ${l[0].size} pas, basse ${b[0].size} pas" }
            leadNote = l[0]; leadStart = l[1]; leadLen = l[2]
            bassNote = b[0]; bassStart = b[1]; bassLen = b[2]
        }

        private fun expand(pairs: IntArray): Array<IntArray> {
            var total = 0
            for (i in pairs.indices step 2) total += pairs[i + 1]
            val note = IntArray(total)
            val start = IntArray(total)
            val len = IntArray(total)
            var pos = 0
            for (i in pairs.indices step 2) {
                for (k in 0 until pairs[i + 1]) {
                    note[pos + k] = pairs[i]
                    start[pos + k] = pos
                    len[pos + k] = pairs[i + 1]
                }
                pos += pairs[i + 1]
            }
            return arrayOf(note, start, len)
        }
    }

    @Volatile private var running = false
    @Volatile var musicOn = true
    @Volatile private var sfxRequest: FloatArray? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "chip-audio").also { it.start() }
    }

    fun stop() {
        running = false
        try {
            thread?.join(500)
        } catch (_: InterruptedException) {
        }
        thread = null
    }

    /** Bruitage : glissando de [from] à [to] hertz pendant [seconds]. */
    fun sfx(from: Float, to: Float, seconds: Float) {
        sfxRequest = floatArrayOf(from, to, seconds)
    }

    private fun freq(midi: Int) = 440f * 2f.pow((midi - 69) / 12f)

    private fun loop() {
        val track = try {
            val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also { it.play() }
        } catch (e: Exception) {
            running = false
            return
        }

        val buf = ShortArray(512)
        val stepSamples = if (song != null) RATE * 60 / song.bpm / 4 else 1
        var sample = 0L
        var leadPhase = 0f
        var bassPhase = 0f
        var sfxPhase = 0f
        var sfxFrom = 0f
        var sfxTo = 0f
        var sfxTotal = 0
        var sfxLeft = 0

        try {
            while (running) {
                sfxRequest?.let {
                    sfxRequest = null
                    sfxFrom = it[0]
                    sfxTo = it[1]
                    sfxTotal = (it[2] * RATE).toInt().coerceAtLeast(1)
                    sfxLeft = sfxTotal
                }
                for (i in buf.indices) {
                    var out = 0f
                    if (song != null && musicOn) {
                        val step = ((sample / stepSamples) % song.steps).toInt()
                        val inStep = (sample % stepSamples).toInt()

                        val ln = song.leadNote[step]
                        if (ln > 0) {
                            val t = ((step - song.leadStart[step]) * stepSamples + inStep).toFloat() /
                                (song.leadLen[step] * stepSamples)
                            if (t < 0.88f) {
                                leadPhase += freq(ln) / RATE
                                if (leadPhase >= 1f) leadPhase -= 1f
                                out += (if (leadPhase < 0.5f) 1f else -1f) * 0.13f * (1f - 0.6f * t)
                            }
                        }
                        val bn = song.bassNote[step]
                        if (bn > 0) {
                            val t = ((step - song.bassStart[step]) * stepSamples + inStep).toFloat() /
                                (song.bassLen[step] * stepSamples)
                            if (t < 0.7f) {
                                bassPhase += freq(bn) / RATE
                                if (bassPhase >= 1f) bassPhase -= 1f
                                out += (if (bassPhase < 0.25f) 1f else -1f) * 0.09f
                            }
                        }
                        sample++
                    }
                    if (sfxLeft > 0) {
                        val t = 1f - sfxLeft.toFloat() / sfxTotal
                        sfxPhase += (sfxFrom + (sfxTo - sfxFrom) * t) / RATE
                        if (sfxPhase >= 1f) sfxPhase -= 1f
                        out += (if (sfxPhase < 0.5f) 1f else -1f) * 0.2f * (1f - 0.5f * t)
                        sfxLeft--
                    }
                    buf[i] = (out.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
                track.write(buf, 0, buf.size)
            }
        } catch (e: Exception) {
            // Le son est un plus : en cas de souci audio, le jeu continue en silence.
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
    }

    companion object {
        private const val RATE = 22050

        // Notes MIDI.
        private const val G2 = 43; private const val A2 = 45; private const val F2 = 41
        private const val C3 = 48; private const val F3 = 53; private const val G3 = 55; private const val A3 = 57
        private const val C4 = 60; private const val F4 = 65; private const val G4 = 67; private const val A4 = 69
        private const val B4 = 71; private const val C5 = 72; private const val D5 = 74; private const val E5 = 76
        private const val F5 = 77; private const val G5 = 79; private const val A5 = 81; private const val C6 = 84

        /** Air original du jeu de plateforme : huit mesures en do majeur (do, la mineur, fa, sol). */
        val BIP_THEME: Song by lazy {
            Song(
                bpm = 138,
                lead = intArrayOf(
                    E5, 2, G5, 2, E5, 2, C5, 2, D5, 2, E5, 2, C5, 4,
                    A4, 2, C5, 2, E5, 2, C5, 2, D5, 2, C5, 2, A4, 4,
                    F4, 2, A4, 2, C5, 2, A4, 2, C5, 2, D5, 2, C5, 4,
                    D5, 2, B4, 2, G4, 2, B4, 2, D5, 2, G5, 2, D5, 2, 0, 2,
                    C5, 1, E5, 1, G5, 2, E5, 1, G5, 1, C6, 2, G5, 2, E5, 2, G5, 4,
                    A4, 1, C5, 1, E5, 2, C5, 1, E5, 1, A5, 2, E5, 2, C5, 2, E5, 4,
                    F5, 2, E5, 2, D5, 2, C5, 2, A4, 2, C5, 2, F5, 4,
                    G5, 2, F5, 2, D5, 2, B4, 2, G4, 4, 0, 4,
                ),
                bass = bassLine(C3, C4) + bassLine(A2, A3) + bassLine(F2, F3) + bassLine(G2, G3) +
                    bassLine(C3, C4) + bassLine(A2, A3) + bassLine(F2, F3) + bassLine(G2, G3),
            )
        }

        private fun bassLine(low: Int, high: Int) =
            intArrayOf(low, 2, high, 2, low, 2, high, 2, low, 2, high, 2, low, 2, high, 2)
    }
}
