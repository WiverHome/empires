package empires.game

// Порт js/editor.js: редактор своих карт для схватки. Рисование пальцем по сетке, кисти местности и замков сторон.

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import empires.logic.MAP_LEGEND
import empires.logic.Pos
import empires.logic.SkirmishMap
import empires.logic.Slot
import empires.logic.TEAMS
import empires.logic.TERRAIN
import kotlin.math.floor
import kotlin.math.min

private const val EDITOR_MIN_W = 7
private const val EDITOR_MAX_W = 11
private const val EDITOR_MIN_H = 7
private const val EDITOR_MAX_H = 15
private val EDITOR_TEAMS = listOf("blue", "red", "green", "yellow")

class Brush(val id: String, val terrain: String, val team: String?, val name: String)

private val EDITOR_BRUSHES: List<Brush> by lazy {
    listOf("plain", "road", "forest", "hill", "mountain", "water", "shallow", "bridge", "village", "ruins").map { Brush(it, it, null, TERRAIN.getValue(it).name) } +
        Brush("castle", "castle", null, "Ничей замок") +
        EDITOR_TEAMS.map { Brush("team:$it", "castle", it, "Замок: " + TEAMS.getValue(it).label) }
}

class MapEditor(private val existing: SkirmishMap?, private val onDone: (Result?) -> Unit) {
    sealed class Result {
        class Saved(val map: SkirmishMap) : Result()
        class Deleted(val id: String) : Result()
    }

    private var w = 11
    private var h = 13
    private var terrain = ArrayList<ArrayList<String>>()
    private val castles = LinkedHashMap<String, Pos>()
    private var brush = EDITOR_BRUSHES[0]
    private var confirmDel = false
    private var title = existing?.title ?: "Моя карта"
    private var err = ""
    private var tex: Texture? = null
    private val icons = HashMap<String, Texture>()

    init {
        if (existing != null) {
            w = existing.map[0].length; h = existing.map.size
            terrain = ArrayList(existing.map.map { r -> ArrayList(r.map { ch -> MAP_LEGEND[ch] ?: "plain" }) })
            existing.slots.forEach { castles[it.team] = Pos(it.castle.x, it.castle.y) }
        } else {
            terrain = ArrayList((0 until h).map { ArrayList(List(w) { "plain" }) })
            castles["blue"] = Pos(5, h - 1); castles["red"] = Pos(5, 0)
            terrain[h - 1][5] = "castle"; terrain[0][5] = "castle"
        }
    }

    private fun redraw() {
        tex?.dispose()
        val p = APP.assets.renderTerrainPixmap(w, h, { x, y -> if (x in 0 until w && y in 0 until h) terrain[y][x] else null }, 0)
        for ((team, c) in castles) mark(p, c.x * TILE, c.y * TILE, TILE, team)
        tex = Texture(p)
        p.dispose()
    }

    // Рамка и уголок цвета стороны на её замке
    private fun mark(p: Pixmap, x: Int, y: Int, s: Int, team: String) {
        val col = Color.valueOf(TEAMS.getValue(team).color)
        p.setColor(col)
        val lw = maxOf(1, s / 8)
        p.fillRectangle(x, y, s, lw); p.fillRectangle(x, y + s - lw, s, lw)
        p.fillRectangle(x, y, lw, s); p.fillRectangle(x + s - lw, y, lw, s)
        p.fillRectangle(x, y, (s * 0.4).toInt(), (s * 0.4).toInt())
    }

    private fun icon(b: Brush): Texture = icons.getOrPut(b.id) {
        val p = APP.assets.renderTerrainPixmap(1, 1, { x, y -> if (x == 0 && y == 0) b.terrain else null }, 0)
        if (b.team != null) mark(p, 0, 0, TILE, b.team)
        Texture(p).also { p.dispose() }
    }

    fun open() {
        redraw()
        val m = Table()
        m.add(Ui.label(if (existing != null) "Изменить карту" else "Новая карта", C.gold, Ui.fontBig)).left().row()
        // холст: ширина окна, высота - сколько останется на экране
        val maxW = min(APP.stage.width - 64f, 400f)
        val maxH = (APP.stage.height - 380f).coerceAtLeast(160f)
        val sc = min(maxW / (w * TILE), maxH / (h * TILE))
        m.add(Canvas()).size(floor(w * TILE * sc), floor(h * TILE * sc)).padTop(6f).row()
        // кисти
        val brushes = Table()
        EDITOR_BRUSHES.forEachIndexed { i, b ->
            val cell = Table().apply { background = if (b == brush) Ui.panelAccent else Ui.panel2; pad(3f) }
            cell.add(Image(TextureRegionDrawable(icon(b)))).size(26f)
            cell.touchable = Touchable.enabled
            cell.onClick { Sfx.play("click"); brush = b; open() }
            brushes.add(cell).pad(2f)
            if (i % 8 == 7) brushes.row()
        }
        m.add(brushes).padTop(6f).row()
        m.add(Ui.small(brush.name, C.muted, wrap = false)).padTop(2f).row()
        val size = Table()
        size.add(Ui.small("Ширина", C.text, wrap = false)).padRight(4f)
        size.add(Ui.button("−") { resize(-1, 0) }).width(36f)
        size.add(Ui.label("$w", C.text, Ui.fontSmall)).width(26f).also { (it.actor as com.badlogic.gdx.scenes.scene2d.ui.Label).centered() }
        size.add(Ui.button("+") { resize(1, 0) }).width(36f).padRight(10f)
        size.add(Ui.small("Высота", C.text, wrap = false)).padRight(4f)
        size.add(Ui.button("−") { resize(0, -1) }).width(36f)
        size.add(Ui.label("$h", C.text, Ui.fontSmall)).width(26f).also { (it.actor as com.badlogic.gdx.scenes.scene2d.ui.Label).centered() }
        size.add(Ui.button("+") { resize(0, 1) }).width(36f)
        m.add(size).padTop(6f).row()
        val field = Ui.textField(title)
        field.maxLength = 24
        field.messageText = "Название карты"
        field.setTextFieldListener { f, _ -> title = f.text }
        m.add(field).growX().height(40f).padTop(6f).row()
        if (err.isNotEmpty()) m.add(Ui.small(err, C.gold)).growX().padTop(4f).row()
        val row = Table()
        if (existing != null) row.add(Ui.button(if (confirmDel) "Точно удалить?" else "Удалить") {
            if (!confirmDel) { confirmDel = true; open() } else finish(Result.Deleted(existing.id))
        }).padRight(6f)
        row.add(Ui.button("Отмена") { finish(null) }).padRight(6f)
        row.add(Ui.button("Сохранить", primary = true) {
            val res = buildMap(title.trim().ifEmpty { "Моя карта" }, existing?.id ?: ("c" + System.currentTimeMillis().toString(36)))
            if (res is String) { err = res; open() } else finish(Result.Saved(res as SkirmishMap))
        })
        m.add(row).right().padTop(8f)
        APP.openModal(m, dismiss = { finish(null) })
    }

    private fun finish(r: Result?) {
        tex?.dispose(); tex = null
        icons.values.forEach { it.dispose() }; icons.clear()
        APP.closeModal()
        onDone(r)
    }

    private fun resize(dw: Int, dh: Int) {
        val nw = (w + dw).coerceIn(EDITOR_MIN_W, EDITOR_MAX_W)
        val nh = (h + dh).coerceIn(EDITOR_MIN_H, EDITOR_MAX_H)
        terrain = ArrayList((0 until nh).map { y -> ArrayList((0 until nw).map { x -> terrain.getOrNull(y)?.getOrNull(x) ?: "plain" }) })
        w = nw; h = nh
        castles.entries.removeAll { it.value.x >= nw || it.value.y >= nh }
        err = ""
        open()
    }

    private fun paint(x: Int, y: Int) {
        if (x !in 0 until w || y !in 0 until h) return
        castles.entries.removeAll { it.value.x == x && it.value.y == y }
        brush.team?.let { t ->
            castles[t]?.let { old -> terrain[old.y][old.x] = "plain" }
            castles[t] = Pos(x, y)
        }
        terrain[y][x] = brush.terrain
        redraw()
    }

    // Собрать карту из состояния редактора или вернуть строку ошибки (как editorBuildMap)
    private fun buildMap(name: String, id: String): Any {
        val legend = MAP_LEGEND.entries.associate { (ch, t) -> t to ch }
        val teams = EDITOR_TEAMS.filter { castles.containsKey(it) }
        if (teams.size < 2) return "Нужны замки хотя бы двух сторон."
        val used = HashSet<String>()
        val slots = ArrayList<Slot>()
        for (team in teams) {
            val c = castles.getValue(team)
            var cmd: Pos? = null
            for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) {
                val x = c.x + dx; val y = c.y + dy
                if (x < 0 || y < 0 || x >= w || y >= h || "$x,$y" in used) continue
                val t = terrain[y][x]
                if (!TERRAIN.getValue(t).cost.isFinite() || t == "castle" || t == "village") continue
                cmd = Pos(x, y); break
            }
            if (cmd == null) return "У замка «${TEAMS.getValue(team).label}» нет свободной клетки для командира рядом."
            used.add("${cmd.x},${cmd.y}")
            slots.add(Slot(team, Pos(c.x, c.y), cmd))
        }
        return SkirmishMap(
            id = id, title = name, desc = "Своя карта ${w}x$h, сторон: ${slots.size}.",
            map = terrain.map { row -> row.map { legend.getValue(it) }.joinToString("") }, slots = slots, custom = true,
        )
    }

    private inner class Canvas : Actor() {
        private var last: String? = null
        init {
            touchable = Touchable.enabled
            addListener(object : InputListener() {
                override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                    // пока рисуем, окно не прокручивается
                    firstAscendant(com.badlogic.gdx.scenes.scene2d.ui.ScrollPane::class.java)?.setFlickScroll(false)
                    cell(x, y)?.let { (cx, cy) -> last = "$cx,$cy"; paint(cx, cy) }
                    return true
                }
                override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
                    val c = cell(x, y) ?: return
                    if ("${c.first},${c.second}" != last) { last = "${c.first},${c.second}"; paint(c.first, c.second) }
                }
                override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) {
                    last = null
                    firstAscendant(com.badlogic.gdx.scenes.scene2d.ui.ScrollPane::class.java)?.setFlickScroll(true)
                }
            })
        }
        private fun cell(x: Float, y: Float): Pair<Int, Int>? {
            val cx = floor(x / width * w).toInt(); val cy = floor((height - y) / height * h).toInt()
            return if (cx in 0 until w && cy in 0 until h) cx to cy else null
        }
        override fun draw(batch: Batch, parentAlpha: Float) {
            val t = tex ?: return
            batch.setColor(1f, 1f, 1f, parentAlpha)
            batch.draw(t, x, y, width, height)
        }
    }
}
