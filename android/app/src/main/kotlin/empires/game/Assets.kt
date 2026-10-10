package empires.game

// Порт js/assets.js: листы Kenney, процедурные тайлы в их палитре, спрайты юнитов с обводкой цвета стороны.
// Рисование то же самое (только прямоугольники и копирование тайлов), поэтому картинка совпадает с вебом.

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import empires.logic.GameState
import empires.logic.TEAMS
import empires.logic.TILE_SPRITES
import empires.logic.UNIT_DEFS
import empires.logic.inBounds
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PAL {
    const val outline = "#3f2631"; const val grass = "#84c669"; const val grassDark = "#65a556"; const val grassDeep = "#479f4a"
    const val dirt = "#eaa56c"; const val dirtDark = "#cf8254"; const val wood = "#bd6c4a"; const val woodDark = "#763b36"
    const val stone = "#c0cbdc"; const val stoneMid = "#8b9bb4"; const val stoneDark = "#5a6988"; const val night = "#262b44"
    const val water = "#2f8fd8"; const val waterLight = "#76e4ff"; const val foam = "#c8f2ff"; const val roof = "#c34b35"; const val roofLight = "#f28462"
    const val white = "#ffffff"
}

const val TILE = 16
const val SPR_GRID = 48
const val SPR_PAD = 3
const val SPR_SIZE = SPR_GRID + SPR_PAD * 2

// Детерминированный шум для вариаций тайлов (как hash2 в JS: 32-битная арифметика)
fun hash2(x: Int, y: Int): Double {
    var h = x * 374761393 + y * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
}

private val colorCache = HashMap<String, Color>()
fun hex(c: String): Color = colorCache.getOrPut(c) { Color.valueOf(c) }

class Assets : Disposable {
    val sheets = HashMap<String, Pixmap>()
    private val unitCache = HashMap<String, Texture>()
    private val compCache = HashMap<String, Pixmap>()
    private val portraitCache = HashMap<Int, Texture>()
    lateinit var grave: Texture
    lateinit var coin: Texture

    fun load() {
        sheets["town"] = Pixmap(Gdx.files.internal("town.png"))
        sheets["dungeon"] = Pixmap(Gdx.files.internal("dungeon.png"))
        grave = Texture(tilePixmap("dungeon", TILE_SPRITES.grave))
        coin = Texture(tilePixmap("town", TILE_SPRITES.coin))
    }

    private fun Pixmap.px(color: String, x: Int, y: Int, w: Int = 1, h: Int = 1) {
        setColor(hex(color))
        fillRectangle(x, y, w, h)
    }

    // Нарисовать тайл листа в 16-пиксельной сетке
    private fun Pixmap.blit(sheet: String, idx: Int, dx: Int, dy: Int) {
        val im = sheets.getValue(sheet)
        val cols = im.width / TILE
        drawPixmap(im, (idx % cols) * TILE, (idx / cols) * TILE, TILE, TILE, dx, dy, TILE, TILE)
    }

    fun tilePixmap(sheet: String, idx: Int): Pixmap {
        val p = Pixmap(TILE, TILE, Pixmap.Format.RGBA8888)
        p.blending = Pixmap.Blending.None
        p.blit(sheet, idx, 0, 0)
        p.blending = Pixmap.Blending.SourceOver
        return p
    }

    /* ---------- Процедурные тайлы ---------- */
    private class Sides(val up: Boolean, val down: Boolean, val left: Boolean, val right: Boolean)

    private fun Pixmap.drawWater(ox: Int, oy: Int, frame: Int, land: Sides) {
        px(PAL.water, ox, oy, 16, 16)
        val waves = if (frame != 0) listOf(2 to 4, 9 to 9, 4 to 13, 12 to 2) else listOf(4 to 3, 11 to 8, 2 to 11, 10 to 14)
        for ((x, y) in waves) { px(PAL.waterLight, ox + x, oy + y, 3, 1); px("#5ab4ea", ox + x + 1, oy + y + 1, 2, 1) }
        if (land.up) px(PAL.foam, ox, oy, 16, 1)
        if (land.down) px(PAL.foam, ox, oy + 15, 16, 1)
        if (land.left) px(PAL.foam, ox, oy, 1, 16)
        if (land.right) px(PAL.foam, ox + 15, oy, 1, 16)
    }

    private fun Pixmap.drawRoad(ox: Int, oy: Int, link: Sides) {
        val parts = mutableListOf(intArrayOf(5, 5, 6, 6))
        if (link.up) parts.add(intArrayOf(5, 0, 6, 6))
        if (link.down) parts.add(intArrayOf(5, 10, 6, 6))
        if (link.left) parts.add(intArrayOf(0, 5, 6, 6))
        if (link.right) parts.add(intArrayOf(10, 5, 6, 6))
        for (p in parts) px(PAL.dirtDark, ox + p[0] - 1, oy + p[1] - 1, p[2] + 2, p[3] + 2)
        for (p in parts) px(PAL.dirt, ox + p[0], oy + p[1], p[2], p[3])
        px(PAL.dirtDark, ox + 7, oy + 7); px(PAL.dirtDark, ox + 9, oy + 9)
    }

    private fun Pixmap.drawBridge(ox: Int, oy: Int, horizontal: Boolean) {
        if (horizontal) {
            px(PAL.woodDark, ox, oy + 2, 16, 12)
            for (x in 0 until 16 step 3) px(if (x % 6 != 0) PAL.wood else PAL.dirtDark, ox + x, oy + 3, 2, 10)
            px(PAL.outline, ox, oy + 2, 16, 1); px(PAL.outline, ox, oy + 13, 16, 1)
        } else {
            px(PAL.woodDark, ox + 2, oy, 12, 16)
            for (y in 0 until 16 step 3) px(if (y % 6 != 0) PAL.wood else PAL.dirtDark, ox + 3, oy + y, 10, 2)
            px(PAL.outline, ox + 2, oy, 1, 16); px(PAL.outline, ox + 13, oy, 1, 16)
        }
    }

    private fun Pixmap.drawMountain(ox: Int, oy: Int, v: Double) {
        val peak = 7 + floor(v * 3).toInt()
        for (y in 2 until 15) {
            val half = floor((y - 1) * 0.62).toInt()
            val x0 = peak - half; val x1 = peak + half
            px(PAL.outline, ox + x0 - 1, oy + y, x1 - x0 + 3, 1)
        }
        for (y in 3 until 14) {
            val half = floor((y - 2) * 0.62).toInt()
            val x0 = peak - half; val x1 = peak + half
            px(PAL.stone, ox + x0, oy + y, peak - x0 + 1, 1)
            px(PAL.stoneMid, ox + peak + 1, oy + y, x1 - peak, 1)
            if (y > 9) px(PAL.stoneDark, ox + peak + 1 + (x1 - peak) / 2, oy + y, ceil((x1 - peak) / 2.0).toInt(), 1)
            if (y < 6) px(PAL.white, ox + x0, oy + y, x1 - x0 + 1, 1)
        }
        px(PAL.outline, ox + 1, oy + 14, 14, 1)
    }

    private fun Pixmap.drawHill(ox: Int, oy: Int) {
        fun jsRound(v: Double) = floor(v + 0.5).toInt()
        fun mound(cx: Int, cy: Int, r: Int) {
            for (y in -r..0) {
                val w = jsRound(sqrt((r * r - y * y).toDouble()) * 1.4)
                px(PAL.outline, ox + cx - w - 1, oy + cy + y, w * 2 + 3, 1)
            }
            for (y in (-r + 1)..0) {
                val w = jsRound(sqrt((r * r - y * y).toDouble()) * 1.4) - 1
                px(PAL.grassDark, ox + cx - w, oy + cy + y, w * 2 + 1, 1)
                px(PAL.grass, ox + cx - w, oy + cy + y, maxOf(1, w), 1)
            }
            px(PAL.outline, ox + cx - jsRound(r * 1.4) - 1, oy + cy + 1, jsRound(r * 2.8) + 3, 1)
        }
        mound(10, 11, 5)
        mound(5, 13, 4)
    }

    private fun Pixmap.drawHouse(ox: Int, oy: Int) {
        px(PAL.outline, ox + 2, oy + 7, 12, 8)
        px(PAL.dirt, ox + 3, oy + 8, 10, 6)
        px(PAL.dirtDark, ox + 3, oy + 13, 10, 1)
        px(PAL.outline, ox + 6, oy + 10, 3, 4); px(PAL.woodDark, ox + 7, oy + 11, 1, 3)
        px(PAL.outline, ox + 10, oy + 9, 2, 2); px(PAL.waterLight, ox + 10, oy + 9, 1, 1)
        for (i in 0 until 6) {
            px(PAL.outline, ox + 1 + i, oy + 7 - i, 14 - i * 2, 1)
            if (i > 0) { px(PAL.roof, ox + 2 + i, oy + 7 - i, 12 - i * 2, 1); px(PAL.roofLight, ox + 2 + i, oy + 7 - i, 2, 1) }
        }
        px(PAL.roof, ox + 2, oy + 7, 12, 1)
    }

    private fun Pixmap.drawCastle(ox: Int, oy: Int) {
        px(PAL.outline, ox + 1, oy + 4, 14, 12)
        px(PAL.stone, ox + 2, oy + 5, 12, 10)
        px(PAL.stoneMid, ox + 2, oy + 12, 12, 3)
        for (x in 1 until 15 step 3) { px(PAL.outline, ox + x, oy + 2, 3, 3); px(PAL.stone, ox + x + 1, oy + 3, 1, 2) }
        px(PAL.stoneMid, ox + 4, oy + 7, 3, 1); px(PAL.stoneMid, ox + 9, oy + 8, 3, 1); px(PAL.stoneMid, ox + 3, oy + 10, 2, 1)
        px(PAL.outline, ox + 6, oy + 9, 4, 7); px(PAL.night, ox + 7, oy + 10, 2, 6)
        px(PAL.stoneDark, ox + 7, oy + 12, 2, 1)
    }

    private fun Pixmap.drawShallow(ox: Int, oy: Int, frame: Int, x: Int, y: Int) {
        px("#5ab4ea", ox, oy, 16, 16)
        val v = hash2(x * 7 + 3, y * 11 + 5)
        val spots = if (v < 0.5) listOf(intArrayOf(2, 3, 5, 3), intArrayOf(9, 10, 4, 3)) else listOf(intArrayOf(8, 2, 5, 3), intArrayOf(3, 11, 5, 2))
        for (s in spots) { px("#d9c48a", ox + s[0], oy + s[1], s[2], s[3]); px("#bfa86e", ox + s[0], oy + s[1] + s[3] - 1, s[2], 1) }
        val waves = if (frame != 0) listOf(1 to 7, 11 to 6, 6 to 14) else listOf(3 to 8, 12 to 7, 7 to 13)
        for ((wx, wy) in waves) px(PAL.foam, ox + wx, oy + wy, 3, 1)
    }

    private fun Pixmap.drawRuins(ox: Int, oy: Int) {
        px(PAL.outline, ox + 2, oy + 9, 5, 6); px(PAL.dirtDark, ox + 3, oy + 10, 3, 4)
        px(PAL.outline, ox + 9, oy + 11, 6, 4); px(PAL.dirtDark, ox + 10, oy + 12, 4, 2)
        px(PAL.outline, ox + 2, oy + 6, 2, 3); px(PAL.dirt, ox + 3, oy + 7, 1, 2)
        px(PAL.stoneMid, ox + 7, oy + 13, 2, 2); px(PAL.stoneDark, ox + 6, oy + 5, 2, 1)
        px(PAL.roof, ox + 10, oy + 8, 3, 1); px(PAL.outline, ox + 10, oy + 9, 3, 1)
        px("#4a4a4a", ox + 5, oy + 3, 1, 3); px("#6a6a6a", ox + 6, oy + 2, 1, 2)
    }

    private fun Pixmap.drawGrass(ox: Int, oy: Int, x: Int, y: Int) {
        px(PAL.grass, ox, oy, 16, 16)
        val v = hash2(x, y)
        if (v < 0.25) blit("town", if (v < 0.12) 1 else 2, ox, oy)
    }

    private fun Pixmap.drawForest(ox: Int, oy: Int, x: Int, y: Int) {
        val v = hash2(x * 3 + 1, y * 5 + 2)
        val trees = TILE_SPRITES.trees
        blit("town", trees[floor(v * trees.size).toInt()], ox, oy)
    }

    /* ---------- Пререндер карты (2 кадра для анимации воды) ---------- */
    // terrain[y][x] - id местности; для редактора можно передать произвольную сетку
    fun renderTerrainPixmap(w: Int, h: Int, terrain: (Int, Int) -> String?, frame: Int): Pixmap {
        val g = Pixmap(w * TILE, h * TILE, Pixmap.Format.RGBA8888)
        g.blending = Pixmap.Blending.SourceOver
        fun wet(t: String?) = t == "water" || t == "bridge" || t == "shallow"
        fun linky(t: String?) = t == "road" || t == "bridge" || t == "village" || t == "castle"
        for (y in 0 until h) for (x in 0 until w) {
            val t = terrain(x, y); val ox = x * TILE; val oy = y * TILE
            if (t == "shallow") { g.drawShallow(ox, oy, frame, x, y); continue }
            if (t == "water" || t == "bridge") {
                fun land(dx: Int, dy: Int): Boolean { val n = terrain(x + dx, y + dy); return n != null && !wet(n) }
                g.drawWater(ox, oy, frame, Sides(land(0, -1), land(0, 1), land(-1, 0), land(1, 0)))
                if (t == "bridge") {
                    fun notWater(dx: Int) = terrain(x + dx, y).let { it != null && it != "water" }
                    g.drawBridge(ox, oy, notWater(-1) || notWater(1))
                }
                continue
            }
            g.drawGrass(ox, oy, x, y)
            when (t) {
                "road" -> {
                    fun l(dx: Int, dy: Int) = linky(terrain(x + dx, y + dy))
                    g.drawRoad(ox, oy, Sides(l(0, -1), l(0, 1), l(-1, 0), l(1, 0)))
                }
                "forest" -> g.drawForest(ox, oy, x, y)
                "hill" -> g.drawHill(ox, oy)
                "mountain" -> g.drawMountain(ox, oy, hash2(x, y))
                "village" -> g.drawHouse(ox, oy)
                "castle" -> g.drawCastle(ox, oy)
                "ruins" -> g.drawRuins(ox, oy)
            }
        }
        return g
    }

    fun renderTerrain(st: GameState, frame: Int): Texture {
        val p = renderTerrainPixmap(st.w, st.h, { x, y -> if (inBounds(st, x, y)) st.terrain[y][x] else null }, frame)
        val t = Texture(p)
        p.dispose()
        return t
    }

    /* ---------- Спрайты юнитов ---------- */
    // Процедурные фигурки 16x16 для юнитов, которых нет в листах
    private fun procPixmap(kind: String): Pixmap {
        val g = Pixmap(TILE, TILE, Pixmap.Format.RGBA8888)
        if (kind == "wisp") {
            g.px(PAL.outline, 5, 3, 6, 10); g.px(PAL.outline, 4, 4, 8, 8); g.px(PAL.outline, 3, 6, 10, 4)
            g.px("#ffb53a", 5, 4, 6, 8); g.px("#ffb53a", 4, 6, 8, 4)
            g.px("#ffe27a", 5, 5, 6, 6); g.px("#fff7c8", 6, 6, 3, 3)
            g.px(PAL.outline, 6, 8, 1, 2); g.px(PAL.outline, 9, 8, 1, 2)
            g.px(PAL.outline, 7, 1, 2, 2); g.px("#ffe27a", 7, 2, 1, 1)
            g.px("#fff7c8", 2, 3, 1, 1); g.px("#fff7c8", 13, 5, 1, 1); g.px("#ffe27a", 12, 12, 1, 1); g.px("#ffe27a", 2, 12, 1, 1)
        } else if (kind == "crystal") {
            g.px(PAL.outline, 3, 12, 10, 3); g.px(PAL.stoneMid, 4, 13, 8, 1)
            val rows = listOf(7 to 2, 6 to 4, 5 to 6, 5 to 6, 5 to 6, 5 to 6, 6 to 4, 7 to 2)
            rows.forEachIndexed { i, (x0, w) -> g.px(PAL.outline, x0 - 1, 3 + i, w + 2, 1) }
            g.px(PAL.outline, 7, 2, 2, 1)
            rows.forEachIndexed { i, (x0, w) -> g.px("#3fd6c6", x0, 3 + i, w, 1); g.px("#a8fff4", x0, 3 + i, maxOf(1, w / 2 - 1), 1) }
            g.px("#ffffff", 6, 5, 1, 2)
        }
        return g
    }

    // Фигурка в 3 раза крупнее, оружие в 2 раза, в сетке 48x48 (только непрозрачные пиксели)
    private fun composeUnit(type: String): Pixmap = compCache.getOrPut(type) {
        val def = UNIT_DEFS.getValue(type)
        val n = SPR_GRID
        val comp = Pixmap(n, n, Pixmap.Format.RGBA8888)
        comp.blending = Pixmap.Blending.None
        fun put(src: Pixmap, sx: Int, sy: Int, scale: Int, ox: Int, oy: Int) {
            val c = src.getPixel(sx, sy)
            if (c and 0xff == 0) return
            comp.setColor(c or 0xff)
            comp.fillRectangle(ox + sx * scale, oy + sy * scale, scale, scale)
        }
        val body = if (def.proc != null) procPixmap(def.proc!!) else tilePixmap(def.sheet, def.sprite)
        for (y in 0 until TILE) for (x in 0 until TILE) put(body, x, y, 3, 0, 0)
        body.dispose()
        def.weapon?.let { w ->
            val src = tilePixmap(w.sheet, w.idx)
            for (y in 0 until TILE) for (x in 0 until TILE) put(src, x, y, 2, w.x, w.y)
            src.dispose()
        }
        comp
    }

    // Спрайт 54x54: обводка 3px цветом стороны, по желанию отражённый и серый (походивший)
    fun unitPixmap(type: String, team: String, flip: Boolean = false, grey: Boolean = false): Pixmap {
        val src = composeUnit(type)
        val n = SPR_GRID; val p = SPR_PAD; val s = SPR_SIZE
        val out = Pixmap(s, s, Pixmap.Format.RGBA8888)
        out.blending = Pixmap.Blending.None
        val col = hex(TEAMS.getValue(team).color)
        fun solid(x: Int, y: Int) = x in 0 until n && y in 0 until n && (src.getPixel(x, y) and 0xff) != 0
        for (y in 0 until s) for (x in 0 until s) {
            val sx = if (flip) n - 1 - (x - p) else x - p
            val sy = y - p
            if (solid(sx, sy)) {
                val c = src.getPixel(sx, sy)
                var r = (c ushr 24) and 0xff; var gg = (c ushr 16) and 0xff; var b = (c ushr 8) and 0xff
                if (grey) { val l = (r * 0.3 + gg * 0.59 + b * 0.11) * 0.75; r = l.toInt(); gg = r; b = r }
                out.drawPixel(x, y, (r shl 24) or (gg shl 16) or (b shl 8) or 0xff)
                continue
            }
            var near = false
            loop@ for (dy in -p..p) for (dx in -p..p)
                if (dx * dx + dy * dy <= p * p + 1 && solid(sx + dx, sy + dy)) { near = true; break@loop }
            if (near) {
                val f = if (grey) 0.55f else 1f
                out.drawPixel(x, y, Color.rgba8888(col.r * f, col.g * f, col.b * f, 1f))
            }
        }
        return out
    }

    fun unitTexture(type: String, team: String, flip: Boolean = false, grey: Boolean = false): Texture =
        unitCache.getOrPut("$type|$team|$flip|$grey") { val pm = unitPixmap(type, team, flip, grey); Texture(pm).also { pm.dispose() } }

    fun unitRegion(type: String, team: String, flip: Boolean = false, grey: Boolean = false) = TextureRegion(unitTexture(type, team, flip, grey))

    // Портрет для диалогов: тайл листа dungeon
    fun portrait(spriteIdx: Int): Texture = portraitCache.getOrPut(spriteIdx) { val pm = tilePixmap("dungeon", spriteIdx); Texture(pm).also { pm.dispose() } }

    override fun dispose() {
        sheets.values.forEach { it.dispose() }
        compCache.values.forEach { it.dispose() }
        unitCache.values.forEach { it.dispose() }
        portraitCache.values.forEach { it.dispose() }
        if (::grave.isInitialized) grave.dispose()
        if (::coin.isInitialized) coin.dispose()
    }
}
