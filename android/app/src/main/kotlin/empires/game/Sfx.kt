package empires.game

// Порт js/sound.js: те же файлы из assets/audio (CC0), та же карта событий
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable

object Sfx : Disposable {
    // Имя события -> варианты файлов (берётся случайный)
    private val FILES = mapOf(
        "click" to listOf("metalClick"),
        "select" to listOf("cloth1"),
        "move" to listOf("footstep00", "footstep02", "footstep04", "footstep06"),
        "swing" to listOf("drawKnife1", "drawKnife2"),
        "hit" to listOf("chop", "knifeSlice"),
        "death" to listOf("dropLeather"),
        "level" to listOf("level"),
        "capture" to listOf("doorOpen_1"),
        "buy" to listOf("handleCoins"),
        "raise" to listOf("creak1"),
        "destroy" to listOf("doorClose_4"),
        "turn" to listOf("bookFlip1"),
        "win" to listOf("win"),
        "lose" to listOf("lose"),
    )
    private val MUSIC = mapOf("menu" to "audio/music/menu.mp3", "battle" to "audio/music/battle.ogg")

    var muted = false
    var musicMuted = false
        set(v) { field = v; if (v) music?.pause() else playMusic(track) }
    private const val VOLUME = 0.6f
    private const val MUSIC_VOLUME = 0.35f

    private val sounds = HashMap<String, Sound>()
    private var music: Music? = null
    private var musicTrack: String? = null
    private var track: String? = null

    fun load() {
        for (name in FILES.values.flatten().toSet()) sounds[name] = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/$name.ogg"))
    }

    fun play(name: String) {
        if (muted) return
        val list = FILES[name] ?: return
        sounds[list.random()]?.play(VOLUME)
    }

    // Музыка: "menu" | "battle" | null
    fun playMusic(t: String?) {
        track = t
        if (musicTrack == t && music != null) { if (!musicMuted && !music!!.isPlaying) music!!.play(); return }
        music?.dispose(); music = null; musicTrack = null
        if (t == null || musicMuted) return
        music = Gdx.audio.newMusic(Gdx.files.internal(MUSIC.getValue(t))).apply {
            isLooping = true
            volume = MUSIC_VOLUME
            play()
        }
        musicTrack = t
    }

    fun pause() { music?.pause() }
    fun resume() { if (!musicMuted) music?.play() }

    override fun dispose() {
        sounds.values.forEach { it.dispose() }
        music?.dispose()
    }
}
