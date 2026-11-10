package pl.licznikdarta

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Piękny, triumfalny dźwięk wygranej (harmonijne dzwonki / celebratory victory chime arpeggio).
 * Wznoszące się czyste nuty C5 (523Hz) -> E5 (659Hz) -> G5 (784Hz) -> C6 (1046Hz),
 * zwieńczone bogatym, błyszczącym akordem C-dur (C5 + E5 + G5 + C6 + E6) z ciepłym wybrzmieniem.
 */
fun generateVictorySamples(sampleRate: Int = 22050): ShortArray {
    val durations = doubleArrayOf(0.11, 0.11, 0.11, 0.16)
    val freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
    val chordDuration = 0.65
    val totalDur = durations.sum() + chordDuration
    val totalSamples = (totalDur * sampleRate).toInt()
    val rawBuf = DoubleArray(totalSamples)

    fun addNote(startTime: Double, dur: Double, freq: Double, amp: Double, isChord: Boolean) {
        val numS = (dur * sampleRate).toInt()
        val startIdx = (startTime * sampleRate).toInt()
        val attackS = 0.006
        val decayRate = if (isChord) 3.5 else 7.0

        for (i in 0 until numS) {
            val t = i.toDouble() / sampleRate
            val env = if (t < attackS) t / attackS else exp(-(t - attackS) * decayRate)

            val sig = (0.60 * sin(2.0 * PI * freq * t) +
                       0.28 * sin(2.0 * PI * (freq * 2.0) * t) +
                       0.14 * sin(2.0 * PI * (freq * 3.0) * t) +
                       0.07 * sin(2.0 * PI * (freq * 4.0) * t)) * env * amp

            val outIdx = startIdx + i
            if (outIdx < rawBuf.size) {
                rawBuf[outIdx] += sig
            }
        }
    }

    var currTime = 0.0
    for (n in freqs.indices) {
        addNote(currTime, durations[n] + 0.12, freqs[n], 0.85, false)
        currTime += durations[n]
    }

    val chordFreqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.50, 1318.51)
    val chordAmps = doubleArrayOf(0.35, 0.30, 0.35, 0.40, 0.25)
    for (c in chordFreqs.indices) {
        addNote(currTime, chordDuration, chordFreqs[c], chordAmps[c], true)
    }

    var maxVal = 0.0
    for (s in rawBuf) {
        val a = kotlin.math.abs(s)
        if (a > maxVal) maxVal = a
    }
    val scale = if (maxVal > 0.0) 31500.0 / maxVal else 31500.0

    val buffer = ShortArray(totalSamples)
    for (i in 0 until totalSamples) {
        val v = (rawBuf[i] * scale).coerceIn(-32700.0, 32700.0)
        buffer[i] = v.toInt().toShort()
    }
    return buffer
}

/**
 * Wyrazisty, ciężki dźwięk błędu z maszyn przemysłowych ("brym" / industrial heavy machine error buzzer).
 * Ciężki, niski industrialny buczek z silnym sub-basem (fala prostokątna/nasycona z opadaniem 135Hz -> 60Hz + 45Hz sub-bass).
 */
fun generateBrymSamples(sampleRate: Int = 22050): ShortArray {
    val durationSec = 0.55
    val numSamples = (durationSec * sampleRate).toInt()
    val buffer = ShortArray(numSamples)
    var phase = 0.0
    val maxAmp = 32700.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val progress = t / durationSec

        // Opadanie częstotliwości z 140 Hz do 58 Hz
        val freq = 140.0 - 82.0 * progress.pow(0.75)
        phase += 2.0 * PI * freq / sampleRate
        if (phase > 2.0 * PI) phase -= 2.0 * PI

        // Obwiednia przemysłowego buczka alarmowego
        val env = when {
            t < 0.012 -> t / 0.012
            t < 0.42 -> 1.0
            else -> ((durationSec - t) / 0.13).coerceAtLeast(0.0)
        }

        // Industrialny buczek maszynowy (nasycona fala o ostrym, metalicznym brzmieniu falownika i rezonansu)
        val s1 = sin(phase)
        val square1 = sign(s1) * 0.50
        val subSquare = sign(sin(phase * 0.5)) * 0.35
        val harmonic = 0.30 * sin(phase * 3.0) + 0.15 * sin(phase * 5.0)
        val raw = (square1 + subSquare + harmonic) * env

        val sampleVal = (raw * maxAmp).coerceIn(-32700.0, 32700.0)
        buffer[i] = sampleVal.toInt().toShort()
    }
    return buffer
}

/**
 * Dźwięk klawisza maszyny do pisania lub klawiatury mechanicznej (typewriter key clack / mechanical switch click).
 * Posiada wyrazisty, ostry mechaniczny atak (snap) oraz rezonans mechanizmu maszyny do pisania i sprężyny.
 */
fun generateKeyClickMechSamples(sampleRate: Int = 22050): ShortArray {
    val durationSec = 0.045 // 45ms
    val numSamples = (durationSec * sampleRate).toInt()
    val buffer = ShortArray(numSamples)
    var seed = 123456789L

    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        val noise = (seed.toDouble() / 0x7fffffffL) * 2.0 - 1.0

        val snapEnv = exp(-t * 950.0)
        val snap = noise * snapEnv * 0.50

        val clackEnv = exp(-t * 130.0)
        val clack = (0.55 * sin(2.0 * PI * 960.0 * t) +
                     0.35 * sin(2.0 * PI * 1440.0 * t) +
                     0.25 * sin(2.0 * PI * 420.0 * t)) * clackEnv

        val pingEnv = exp(-t * 240.0)
        val ping = (0.40 * sin(2.0 * PI * 3200.0 * t) +
                    0.25 * sin(2.0 * PI * 4800.0 * t) +
                    0.18 * sin(2.0 * PI * 2150.0 * t)) * pingEnv

        val raw = snap + clack + ping
        val sampleVal = (raw * 21000.0).coerceIn(-32700.0, 32700.0)
        buffer[i] = sampleVal.toInt().toShort()
    }
    return buffer
}

fun generateKeyClickModernSamples(sampleRate: Int = 22050): ShortArray {
    val durationSec = 0.020 // 20ms
    val numSamples = (durationSec * sampleRate).toInt()
    val buffer = ShortArray(numSamples)
    var seed = 987654321L
    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        val noise = (seed.toDouble() / 0x7fffffffL) * 2.0 - 1.0
        val env = exp(-t * 320.0)
        val click = (noise * 0.45 + 0.55 * sin(2.0 * PI * 3000.0 * t)) * env
        buffer[i] = (click.coerceIn(-1.0, 1.0) * 25000.0).toInt().toShort()
    }
    return buffer
}

fun generateKeyClickWoodSamples(sampleRate: Int = 22050): ShortArray {
    val durationSec = 0.038 // 38ms
    val numSamples = (durationSec * sampleRate).toInt()
    val buffer = ShortArray(numSamples)
    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val env = exp(-t * 160.0)
        val wave = (0.75 * sin(2.0 * PI * 720.0 * t) + 0.25 * sin(2.0 * PI * 1180.0 * t)) * env
        buffer[i] = (wave.coerceIn(-1.0, 1.0) * 27000.0).toInt().toShort()
    }
    return buffer
}

fun generateKeyClickHeavySamples(sampleRate: Int = 22050): ShortArray {
    val durationSec = 0.055 // 55ms
    val numSamples = (durationSec * sampleRate).toInt()
    val buffer = ShortArray(numSamples)
    var seed = 555555555L
    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        val noise = (seed.toDouble() / 0x7fffffffL) * 2.0 - 1.0
        val snap = noise * exp(-t * 600.0) * 0.4
        val thump = sin(2.0 * PI * 180.0 * t) * exp(-t * 90.0) * 0.6
        val metal = sin(2.0 * PI * 1450.0 * t) * exp(-t * 200.0) * 0.3
        val s = (snap + thump + metal).coerceIn(-1.0, 1.0)
        buffer[i] = (s * 28000.0).toInt().toShort()
    }
    return buffer
}

fun generateKeyClickBeepSamples(sampleRate: Int = 22050): ShortArray {
    val durationSec = 0.032 // 32ms
    val numSamples = (durationSec * sampleRate).toInt()
    val buffer = ShortArray(numSamples)
    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val attack = (i.toDouble() / (0.002 * sampleRate)).coerceAtMost(1.0)
        val release = ((numSamples - 1 - i).toDouble() / (0.005 * sampleRate)).coerceIn(0.0, 1.0)
        val env = attack * release
        val wave = sin(2.0 * PI * 1760.0 * t) * env
        buffer[i] = (wave.coerceIn(-1.0, 1.0) * 22000.0).toInt().toShort()
    }
    return buffer
}

fun generateKeyClickSamples(sampleRate: Int = 22050): ShortArray = generateKeyClickMechSamples(sampleRate)

/**
 * Dźwięk przełącznika jak z szafy sterowniczej / maszyny przemysłowej (ciężki dwustopniowy styk przekaźnika / heavy industrial relay switch "clack-chunk").
 */
fun generateSwitchSamples(sampleRate: Int = 22050): ShortArray {
    val durationSec = 0.052 // 52ms
    val numSamples = (durationSec * sampleRate).toInt()
    val buffer = ShortArray(numSamples)
    val maxAmp = 32700.0

    var phaseA = 0.0
    var phaseB = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        // Dwa impulsy: pierwszy mechaniczny cam-release (700Hz), po 14ms drugi zapięcia styku (240Hz punch)
        phaseA += 2.0 * PI * 700.0 / sampleRate
        if (phaseA > 2.0 * PI) phaseA -= 2.0 * PI

        phaseB += 2.0 * PI * 240.0 / sampleRate
        if (phaseB > 2.0 * PI) phaseB -= 2.0 * PI

        val envA = exp(-t * 200.0)
        val t2 = (t - 0.014).coerceAtLeast(0.0)
        val envB = if (t >= 0.014) exp(-t2 * 150.0) * 1.35 else 0.0

        val raw = 0.60 * sin(phaseA) * envA + (0.80 * sin(phaseB) + 0.30 * sin(phaseB * 2.5)) * envB
        val sampleVal = (raw * maxAmp).coerceIn(-32700.0, 32700.0)
        buffer[i] = sampleVal.toInt().toShort()
    }
    return buffer
}

/**
 * Dźwięk klikania liczb / rzutu lotki - wyrazisty mechaniczny dźwięk klawisza maszyny do pisania.
 */
fun generateThrowSamples(sampleRate: Int = 22050): ShortArray {
    return generateKeyClickSamples(sampleRate)
}

class GameAudio(context: Context? = null) {
    private val victorySamples by lazy { generateVictorySamples() }
    private val brymSamples by lazy { generateBrymSamples() }
    private val keyClickMechSamples by lazy { generateKeyClickMechSamples() }
    private val keyClickModernSamples by lazy { generateKeyClickModernSamples() }
    private val keyClickWoodSamples by lazy { generateKeyClickWoodSamples() }
    private val keyClickHeavySamples by lazy { generateKeyClickHeavySamples() }
    private val keyClickBeepSamples by lazy { generateKeyClickBeepSamples() }
    private val switchSamples by lazy { generateSwitchSamples() }
    private val throwSamples by lazy { generateThrowSamples() }

    private var soundPool: SoundPool? = null
    private var appContext: Context? = null
    private var soundClickMechId = 0
    private var soundClickModernId = 0
    private var soundClickWoodId = 0
    private var soundClickHeavyId = 0
    private var soundClickBeepId = 0
    private var soundSwitchId = 0
    private var soundThrowId = 0
    private var soundBrymId = 0
    private var soundVictoryId = 0
    private var sound100Id = 0
    private var sound140Id = 0
    private var sound180Id = 0
    private var soundLaughId = 0

    init {
        val c = context?.applicationContext
        appContext = c
        if (c != null) {
            initSoundPool(c)
        }
    }

    private fun initSoundPool(context: Context) {
        Thread {
            try {
                val pool = SoundPool.Builder()
                    .setMaxStreams(8)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()

                fun writeWav(name: String, samples: ShortArray): File {
                    val file = File(context.cacheDir, name)
                    val dataSize = samples.size * 2
                    val header = ByteArray(44)
                    val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    bb.put("RIFF".toByteArray())
                    bb.putInt(36 + dataSize)
                    bb.put("WAVEfmt ".toByteArray())
                    bb.putInt(16) // Subchunk1Size
                    bb.putShort(1) // PCM
                    bb.putShort(1) // Mono
                    bb.putInt(22050) // Sample rate
                    bb.putInt(22050 * 2) // Byte rate
                    bb.putShort(2) // Block align
                    bb.putShort(16) // Bits per sample
                    bb.put("data".toByteArray())
                    bb.putInt(dataSize)

                    val pcmBytes = ByteArray(dataSize)
                    ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)

                    file.outputStream().use { fos ->
                        fos.write(header)
                        fos.write(pcmBytes)
                    }
                    return file
                }

                val fClickMech = writeWav("snd_click_mech.wav", keyClickMechSamples)
                val fClickModern = writeWav("snd_click_modern.wav", keyClickModernSamples)
                val fClickWood = writeWav("snd_click_wood.wav", keyClickWoodSamples)
                val fClickHeavy = writeWav("snd_click_heavy.wav", keyClickHeavySamples)
                val fClickBeep = writeWav("snd_click_beep.wav", keyClickBeepSamples)
                val fSwitch = writeWav("snd_switch.wav", switchSamples)
                val fThrow = writeWav("snd_throw.wav", throwSamples)
                val fBrym = writeWav("snd_brym.wav", brymSamples)
                val fVic = writeWav("snd_victory.wav", victorySamples)

                soundClickMechId = pool.load(fClickMech.absolutePath, 1)
                soundClickModernId = pool.load(fClickModern.absolutePath, 1)
                soundClickWoodId = pool.load(fClickWood.absolutePath, 1)
                soundClickHeavyId = pool.load(fClickHeavy.absolutePath, 1)
                soundClickBeepId = pool.load(fClickBeep.absolutePath, 1)
                soundSwitchId = pool.load(fSwitch.absolutePath, 1)
                soundThrowId = pool.load(fThrow.absolutePath, 1)
                soundBrymId = pool.load(fBrym.absolutePath, 1)
                try {
                    soundVictoryId = pool.load(context, R.raw.voice_win, 1)
                    sound100Id = pool.load(context, R.raw.voice_100, 1)
                    sound140Id = pool.load(context, R.raw.voice_140, 1)
                    sound180Id = pool.load(context, R.raw.voice_180, 1)
                    soundLaughId = pool.load(context, R.raw.laugh, 1)
                } catch (_: Throwable) {
                    soundVictoryId = pool.load(fVic.absolutePath, 1)
                }
                soundPool = pool
            } catch (_: Throwable) {}
        }.start()
    }

    fun click(sound: KeySound = KeySound.MECHANICAL) {
        val pool = soundPool
        val id = when (sound) {
            KeySound.MECHANICAL -> soundClickMechId
            KeySound.MODERN -> soundClickModernId
            KeySound.WOOD -> soundClickWoodId
            KeySound.HEAVY -> soundClickHeavyId
            KeySound.BEEP -> soundClickBeepId
        }
        if (pool != null && id != 0) {
            pool.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            val samples = when (sound) {
                KeySound.MECHANICAL -> keyClickMechSamples
                KeySound.MODERN -> keyClickModernSamples
                KeySound.WOOD -> keyClickWoodSamples
                KeySound.HEAVY -> keyClickHeavySamples
                KeySound.BEEP -> keyClickBeepSamples
            }
            playPcm(samples)
        }
    }

    fun switchSound() {
        val pool = soundPool
        if (pool != null && soundSwitchId != 0) {
            pool.play(soundSwitchId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            playPcm(switchSamples)
        }
    }

    fun throwSound(mult: Int = 1) {
        val pool = soundPool
        if (pool != null && soundThrowId != 0) {
            val rate = when (mult) { 2 -> 1.08f; 3 -> 1.18f; else -> 1.0f }
            pool.play(soundThrowId, 1.0f, 1.0f, 1, 0, rate)
        } else {
            playPcm(throwSamples)
        }
    }

    private fun playRawVoice(soundId: Int, resId: Int, fallbackSamples: ShortArray? = null) {
        val pool = soundPool
        val played = if (pool != null && soundId != 0) {
            pool.play(soundId, 1.0f, 1.0f, 2, 0, 1.0f)
        } else 0
        if (played == 0) {
            Thread {
                val ctx = appContext
                if (ctx != null) {
                    try {
                        val mp = android.media.MediaPlayer.create(ctx, resId)
                        mp?.setOnCompletionListener { it.release() }
                        mp?.start()
                    } catch (_: Throwable) {
                        if (fallbackSamples != null) playPcm(fallbackSamples)
                    }
                } else if (fallbackSamples != null) {
                    playPcm(fallbackSamples)
                }
            }.start()
        }
    }

    fun playVictory() {
        playRawVoice(soundVictoryId, R.raw.voice_win, victorySamples)
    }

    fun play100() {
        playRawVoice(sound100Id, R.raw.voice_100)
    }

    fun play140() {
        playRawVoice(sound140Id, R.raw.voice_140)
    }

    fun play180() {
        playRawVoice(sound180Id, R.raw.voice_180)
    }

    fun playLaugh() {
        val ctx = appContext
        if (ctx != null && CustomAudioManager.playSoundForScore(ctx, 0)) {
            return
        }
        playRawVoice(soundLaughId, R.raw.laugh)
    }

    fun playAnnounceIfSpecial(score: Int): Boolean {
        val ctx = appContext
        if (ctx != null && CustomAudioManager.playSoundForScore(ctx, score)) {
            return true
        }
        return when (score) {
            180 -> { play180(); true }
            140 -> { play140(); true }
            100 -> { play100(); true }
            else -> false
        }
    }

    fun playBrym() {
        val pool = soundPool
        if (pool != null && soundBrymId != 0) {
            pool.play(soundBrymId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            playPcm(brymSamples)
        }
    }

    fun win() = playVictory()
    fun bust() = playBrym()

    private fun playPcm(samples: ShortArray, sampleRate: Int = 22050) {
        Thread {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.setVolume(1.0f)
                track.play()
                val durationMs = (samples.size * 1000L) / sampleRate
                Thread.sleep(durationMs + 30)
                track.stop()
                track.release()
            } catch (_: Throwable) {}
        }.start()
    }
}

class GameTts(context: Context, rate: Float = 1.0f, pitch: Float = 1.0f) {
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("pl", "PL")
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
        }
    }
    fun speak(text: String) {
        val toSpeak = if (text == "0" || text.trim() == "0") "zero" else text
        tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, "dart-tts")
    }
    fun setRate(r: Float) { tts.setSpeechRate(r) }
    fun setPitch(p: Float) { tts.setPitch(p) }
    fun stop() { tts.stop() }
    fun shutdown() { tts.shutdown() }
}

fun polishNumber(n: Int): String {
    val units = listOf("zero", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem",
        "osiem", "dziewięć", "dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście",
        "piętnaście", "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście")
    val tens = listOf("", "", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt",
        "sześćdziesiąt", "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt")
    val hundreds = listOf("", "sto", "dwieście", "trzysta", "czterysta", "pięćset",
        "sześćset", "siedemset", "osiemset", "dziewięćset")
    return when {
        n < 20 -> units[n]
        n < 100 -> tens[n / 10] + if (n % 10 != 0) " " + units[n % 10] else ""
        n < 1000 -> hundreds[n / 100] + if (n % 100 != 0) " " + polishNumber(n % 100) else ""
        else -> n.toString()
    }
}
