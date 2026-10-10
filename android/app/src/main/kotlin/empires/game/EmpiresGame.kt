package empires.game

// Приложение: одна сцена Scene2D в dp, слои - экран (титул или партия), окна, всплывающие сообщения.
// Аналог main.js (экраны, ввод, старт) и общих частей game.js (openModal, toast).

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport

lateinit var APP: EmpiresGame

class EmpiresGame(val platform: Platform) : ApplicationAdapter() {
    lateinit var stage: Stage
    lateinit var assets: Assets
    val screenLayer = WidgetGroup().apply { setFillParent(true) }
    private val modalLayer = WidgetGroup().apply { setFillParent(true) }
    private val toastLayer = WidgetGroup().apply { setFillParent(true) }
    var screen: AppScreen? = null
        private set
    private var modal: Table? = null
    private var modalDismiss: (() -> Unit)? = null
    private val tasks = ArrayList<() -> Unit>()

    override fun create() {
        APP = this
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        val vp = ScreenViewport().apply { unitsPerPixel = 1f / Gdx.graphics.density.coerceAtLeast(1f) }
        stage = Stage(vp)
        stage.addActor(screenLayer)
        stage.addActor(modalLayer)
        stage.addActor(toastLayer)
        // слои ловят касания только своими детьми, иначе пустой слой окон закрыл бы экран
        screenLayer.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly
        modalLayer.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly
        toastLayer.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled
        Ui.load()
        assets = Assets().also { it.load() }
        Sfx.load()
        Save.load()
        Sfx.muted = Save.prefBool("muted")
        Sfx.musicMuted = Save.prefBool("musicMuted")
        Gdx.input.inputProcessor = InputMultiplexer(object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { onBack(); return true }
                return false
            }
        }, stage)
        setScreen(TitleScreen())
    }

    fun setScreen(s: AppScreen) {
        closeModal()
        screen?.hide()
        screenLayer.clearChildren()
        screen = s
        val root = s.build()
        root.setFillParent(true)
        screenLayer.addActor(root)
        s.show()
    }

    // Кнопка «Назад» телефона: закрыть окно, иначе отменить выбор на экране
    private fun onBack() {
        if (modal != null) { modalDismiss?.invoke() ?: closeModal(); return }
        screen?.onBack()
    }

    /* ---------- Окна ---------- */
    val modalOpen get() = modal != null

    // Окно поверх экрана. dismiss - что делать по «Назад» и нажатию мимо окна (null - закрыть).
    fun openModal(content: Table, dismiss: (() -> Unit)? = null, closable: Boolean = true): Table {
        closeModal()
        val overlay = Table()
        overlay.setFillParent(true)
        overlay.background = Ui.dim
        overlay.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
        content.background = Ui.panel
        content.pad(16f)
        content.top().left()
        val w = (stage.width - 32f).coerceAtMost(440f)
        val box = Container(Ui.scroll(content).also { it.setFadeScrollBars(false) }).width(w).maxHeight(stage.height - 32f)
        box.fill()
        overlay.add(box)
        overlay.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (event.target == overlay && closable) { dismiss?.invoke() ?: closeModal() }
            }
        })
        modal = overlay
        modalDismiss = if (closable) dismiss else ({})
        modalLayer.addActor(overlay)
        return content
    }

    fun closeModal() {
        // поле ввода в окне могло открыть клавиатуру: убрать её вместе с окном
        if (modal != null) { stage.unfocusAll(); Gdx.input.setOnscreenKeyboardVisible(false) }
        modal?.remove()
        modal = null
        modalDismiss = null
    }

    /* ---------- Всплывающие сообщения ---------- */
    private val toastQueue = ArrayDeque<String>()
    fun toast(text: String) {
        toastQueue.addLast(text)
        if (toastQueue.size == 1) showNextToast()
    }
    private fun showNextToast() {
        val text = toastQueue.firstOrNull() ?: return
        Sfx.play("level")
        val l = Ui.label(text, C.gold, wrap = true).also { it.setAlignment(Align.center) }
        val box = Container(l).apply { background = Ui.panelAccent; pad(10f, 16f, 10f, 16f) }
        box.width((stage.width - 32f).coerceAtMost(360f))
        box.pack()
        box.setPosition((stage.width - box.width) / 2, stage.height - box.height - 64f)
        box.color.a = 0f
        box.addAction(Actions.sequence(Actions.fadeIn(0.25f), Actions.delay(2.4f), Actions.fadeOut(0.25f), Actions.run {
            box.remove(); toastQueue.removeFirst(); showNextToast()
        }))
        toastLayer.addActor(box)
    }

    // Выполнить в начале следующего кадра (из корутин и колбэков других потоков)
    fun post(f: () -> Unit) = synchronized(tasks) { tasks.add(f) }

    override fun render() {
        val run = synchronized(tasks) { tasks.toList().also { tasks.clear() } }
        run.forEach { it() }
        Gdx.gl.glClearColor(C.bg.r, C.bg.g, C.bg.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val dt = Gdx.graphics.deltaTime.coerceAtMost(0.1f)
        screen?.update(dt)
        stage.act(dt)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        screen?.resized()
    }

    override fun pause() { Sfx.pause(); screen?.paused() }
    override fun resume() { Sfx.resume() }

    override fun dispose() {
        stage.dispose(); assets.dispose(); Ui.dispose(); Sfx.dispose()
        platform.nearby.stop()
    }
}

// Экран приложения: строит корневой Table, получает кадры и «Назад»
abstract class AppScreen {
    abstract fun build(): Table
    open fun show() {}
    open fun hide() {}
    open fun update(dt: Float) {}
    open fun resized() {}
    open fun paused() {}
    open fun onBack() {}
}

fun Actor.fadeIn() { color.a = 0f; addAction(Actions.fadeIn(0.15f)) }
fun Label.centered(): Label { setAlignment(Align.center); return this }
