package empires.game

// Набор интерфейса: цвета из style.css, шрифт с кириллицей (системный шрифт телефона через FreeType),
// кнопки и панели со скруглёнными углами. Все размеры - в dp (Stage работает в dp, см. EmpiresGame).

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable

object C {
    val bg: Color = Color.valueOf("#1a1622")
    val panel: Color = Color.valueOf("#2a2233")
    val panel2: Color = Color.valueOf("#362c42")
    val line: Color = Color.valueOf("#4d3f5c")
    val text: Color = Color.valueOf("#f1e9dc")
    val muted: Color = Color.valueOf("#a99db6")
    val gold: Color = Color.valueOf("#f2c14e")
    val accent: Color = Color.valueOf("#c9a24a")
    val good: Color = Color.valueOf("#7dde84")
    val bad: Color = Color.valueOf("#ff7a6a")
    val outline: Color = Color.valueOf("#3f2631")
    val btnText: Color = Color.valueOf("#20160c")
    val btnGold: Color = Color.valueOf("#e3bb5f")
    val btnGoldDown: Color = Color.valueOf("#b8913f")
}

object Ui : Disposable {
    lateinit var font: BitmapFont        // 15dp
    lateinit var fontSmall: BitmapFont   // 12dp
    lateinit var fontBig: BitmapFont     // 22dp
    lateinit var fontTitle: BitmapFont   // 44dp
    private val textures = ArrayList<Texture>()

    lateinit var btnGold: Drawable; lateinit var btnGoldDown: Drawable
    lateinit var btnDark: Drawable; lateinit var btnDarkDown: Drawable
    lateinit var btnDisabled: Drawable
    lateinit var panel: Drawable; lateinit var panel2: Drawable; lateinit var panelAccent: Drawable
    lateinit var white: TextureRegion
    lateinit var dim: Drawable

    private const val CHARS = FreeTypeFontGenerator.DEFAULT_CHARS +
        "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя" +
        "«»—–…·•№★☆▶◀↶✓✗×−→←↑↓♥"

    // Системные шрифты Android: берём первый найденный (лицензия не нужна, шрифт не входит в APK)
    private val FONT_CANDIDATES = listOf(
        "/system/fonts/Roboto-Regular.ttf", "/system/fonts/RobotoStatic-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf", "/system/fonts/DroidSans.ttf",
    )
    private val BOLD_CANDIDATES = listOf(
        "/system/fonts/Roboto-Bold.ttf", "/system/fonts/RobotoStatic-Bold.ttf",
        "/system/fonts/NotoSans-Bold.ttf", "/system/fonts/DroidSans-Bold.ttf",
    )

    // Значки, которых нет в Roboto (★ ▶ ← ...), берутся из системного шрифта символов и вклеиваются в основной шрифт
    private const val SYMBOLS = "★☆▶◀↶←→♥✓"
    private val SYMBOL_FONTS = listOf(
        "/system/fonts/NotoSansSymbols-Regular-Subsetted.ttf", "/system/fonts/NotoSansSymbols-Regular-Subsetted2.ttf",
        "/system/fonts/DroidSansFallback.ttf", "/system/fonts/NotoSansSymbols-Regular.ttf",
    )

    private fun mergeSymbols(f: BitmapFont, px: Int) {
        for (path in SYMBOL_FONTS) {
            val missing = SYMBOLS.filter { !f.data.hasGlyph(it) }
            if (missing.isEmpty()) return
            val file = Gdx.files.absolute(path)
            if (!file.exists()) continue
            val g = FreeTypeFontGenerator(file)
            val sym = runCatching {
                g.generateFont(FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                    size = px; characters = missing
                    minFilter = Texture.TextureFilter.Linear; magFilter = Texture.TextureFilter.Linear
                })
            }.getOrNull()
            g.dispose()
            if (sym == null) continue
            val offset = f.regions.size
            f.regions.addAll(sym.regions)
            // выровнять по базовой линии основного шрифта
            val shift = f.data.ascent - sym.data.ascent
            for (c in missing) {
                val gl = sym.data.getGlyph(c) ?: continue
                if (gl.width == 0) continue
                gl.page += offset
                gl.yoffset -= shift.toInt()
                f.data.setGlyph(c.code, gl)
            }
        }
    }

    fun load() {
        val density = Gdx.graphics.density.coerceAtLeast(1f)
        val regular = FONT_CANDIDATES.map { Gdx.files.absolute(it) }.firstOrNull { it.exists() }
        val bold = BOLD_CANDIDATES.map { Gdx.files.absolute(it) }.firstOrNull { it.exists() } ?: regular
        fun gen(file: com.badlogic.gdx.files.FileHandle?, dp: Float, isBold: Boolean): BitmapFont {
            if (file == null) return BitmapFont().also { it.data.setScale(dp / 15f) }
            val g = FreeTypeFontGenerator(file)
            val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (dp * density).toInt()
                characters = CHARS
                minFilter = Texture.TextureFilter.Linear
                magFilter = Texture.TextureFilter.Linear
                if (isBold && file == regular) { borderWidth = 0.6f * density; borderColor = Color.WHITE }
            }
            val f = g.generateFont(p)
            g.dispose()
            mergeSymbols(f, p.size)
            f.data.setScale(1f / density)
            f.setUseIntegerPositions(false)
            f.data.markupEnabled = true
            return f
        }
        font = gen(bold, 15f, true)
        fontSmall = gen(regular, 12.5f, false)
        fontBig = gen(bold, 22f, true)
        fontTitle = gen(bold, 44f, true)

        SYMBOLS.filter { !font.data.hasGlyph(it) }.takeIf { it.isNotEmpty() }?.let { Gdx.app.log("Empires", "нет значков в шрифте: $it") }
        white = TextureRegion(tex(Pixmap(2, 2, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() }))
        btnGold = rounded(C.btnGold, C.outline, shadow = true)
        btnGoldDown = rounded(C.btnGoldDown, C.outline, shadow = false)
        btnDark = rounded(C.panel2, C.line, shadow = true)
        btnDarkDown = rounded(C.line, C.line, shadow = false)
        btnDisabled = rounded(Color.valueOf("#2f2838"), Color.valueOf("#3a3045"), shadow = false)
        panel = rounded(C.panel, Color.valueOf("#5a3b4a"), shadow = false)
        panel2 = rounded(C.panel2, C.line, shadow = false)
        panelAccent = rounded(C.panel2, C.accent, shadow = false)
        dim = TextureRegionDrawable(white).tint(Color(0.04f, 0.02f, 0.05f, 0.6f))
        primaryStyle = style(true)
        secondaryStyle = style(false)
    }

    private fun tex(p: Pixmap): Texture { val t = Texture(p); p.dispose(); textures.add(t); return t }

    // Скруглённый прямоугольник 2dp-рамкой как девятикусочная картинка; shadow - тёмная полоска снизу (как box-shadow)
    private fun rounded(fill: Color, border: Color, shadow: Boolean): Drawable {
        val d = Gdx.graphics.density.coerceAtLeast(1f)
        val r = (8 * d).toInt().coerceAtLeast(3)
        val b = (2 * d).toInt().coerceAtLeast(1)
        val sh = if (shadow) (3 * d).toInt() else 0
        val size = r * 2 + 4
        val p = Pixmap(size, size + sh, Pixmap.Format.RGBA8888)
        p.blending = Pixmap.Blending.None
        fun rr(x: Int, y: Int, w: Int, h: Int, rad: Int, c: Color) {
            p.setColor(c)
            p.fillRectangle(x + rad, y, w - 2 * rad, h)
            p.fillRectangle(x, y + rad, w, h - 2 * rad)
            p.fillCircle(x + rad, y + rad, rad); p.fillCircle(x + w - rad - 1, y + rad, rad)
            p.fillCircle(x + rad, y + h - rad - 1, rad); p.fillCircle(x + w - rad - 1, y + h - rad - 1, rad)
        }
        if (sh > 0) rr(0, sh, size, size, r, C.outline)
        rr(0, 0, size, size, r, border)
        rr(b, b, size - 2 * b, size - 2 * b, (r - b).coerceAtLeast(1), fill)
        val t = tex(p)
        val np = NinePatch(TextureRegion(t), r + 1, r + 1, r + 1, r + 1 + sh)
        np.scale(1f / d, 1f / d)
        return NinePatchDrawable(np).apply {
            leftWidth = 10f; rightWidth = 10f; topHeight = 7f; bottomHeight = 7f + sh / d
        }
    }

    fun solid(c: Color): Drawable = TextureRegionDrawable(white).tint(c)

    /* ---------- Фабрики виджетов ---------- */
    fun label(text: String, color: Color = C.text, f: BitmapFont = font, wrap: Boolean = false): Label =
        WrapLabel(text, Label.LabelStyle(f, color)).also { it.wrap = wrap }

    // Label с переносом сообщает ширину текста (не больше ширины окна), а не 0:
    // иначе в ячейке без growX текст вытягивается в столбик по одной букве
    class WrapLabel(text: CharSequence, style: LabelStyle) : Label(text, style) {
        private val probe = com.badlogic.gdx.graphics.g2d.GlyphLayout()
        override fun getPrefWidth(): Float {
            if (!wrap) return super.getPrefWidth()
            probe.setText(style.font, text)
            val maxW = (APP.stage.width - 72f).coerceAtLeast(120f).coerceAtMost(400f)
            return minOf(probe.width, maxW)
        }
        // сжиматься можно до любой ширины: таблица ужмёт подпись, если места не хватает
        override fun getMinWidth(): Float = if (wrap) 0f else super.getMinWidth()
    }

    fun small(text: String, color: Color = C.muted, wrap: Boolean = true) = label(text, color, fontSmall, wrap)

    private fun style(primary: Boolean) = TextButton.TextButtonStyle().apply {
        font = this@Ui.font
        up = if (primary) btnGold else btnDark
        down = if (primary) btnGoldDown else btnDarkDown
        disabled = btnDisabled
        fontColor = if (primary) C.btnText else C.text
        downFontColor = fontColor
        disabledFontColor = Color.valueOf("#6f6380")
    }
    // Android может оставить процесс живым после выхода: при новом запуске всё строится заново (load)
    private lateinit var primaryStyle: TextButton.TextButtonStyle
    private lateinit var secondaryStyle: TextButton.TextButtonStyle

    // Кнопка: primary - золотая, иначе тёмная. Клик - звук и действие.
    fun button(text: String, primary: Boolean = false, sound: Boolean = true, onClick: () -> Unit): TextButton {
        val b = TextButton(text, if (primary) primaryStyle else secondaryStyle)
        b.label.setWrap(false)
        b.label.setAlignment(com.badlogic.gdx.utils.Align.center)
        b.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (b.isDisabled) return
                if (sound) Sfx.play("click")
                onClick()
            }
        })
        return b
    }

    fun textField(text: String): TextField {
        val st = TextField.TextFieldStyle().apply {
            font = this@Ui.font; fontColor = C.text
            background = panel2; focusedBackground = panelAccent
            cursor = solid(C.gold).also { it.minWidth = 2f }
            selection = solid(Color(0.79f, 0.64f, 0.29f, 0.5f))
            messageFont = fontSmall; messageFontColor = C.muted
        }
        return TextField(text, st)
    }

    fun scroll(actor: Actor): ScrollPane = ScrollPane(actor, ScrollPane.ScrollPaneStyle()).apply {
        setScrollingDisabled(true, false); setFadeScrollBars(true); setOverscroll(false, true)
    }

    override fun dispose() {
        if (::font.isInitialized) { font.dispose(); fontSmall.dispose(); fontBig.dispose(); fontTitle.dispose() }
        textures.forEach { it.dispose() }
        textures.clear()
    }
}

fun Actor.onClick(f: () -> Unit): Actor {
    touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
    addListener(object : ClickListener() { override fun clicked(event: InputEvent?, x: Float, y: Float) = f() })
    return this
}
