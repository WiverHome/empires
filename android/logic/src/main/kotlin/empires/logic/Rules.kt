// RULES: состояние партии, движение, бой, экономика (порт js/rules.js). Без графики и платформенных API.
package empires.logic

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/* ---------- Модель состояния ---------- */
interface XY { val x: Int; val y: Int }
data class Pos(override val x: Int, override val y: Int) : XY

// Юнит. poison: оставшиеся ходы яда (0 - нет), slow: скован, hold: ИИ стоит на месте, tag: метка сценария, goal: цель ИИ
class Unit(
    val id: Int, var type: String, var team: String, override var x: Int, override var y: Int,
    var hp: Int, var level: Int = 0, var xp: Int = 0, var moved: Boolean = false, var acted: Boolean = false,
    var poison: Int = 0, var slow: Boolean = false, var hold: Boolean = false, var tag: String? = null, var goal: Pos? = null,
) : XY {
    fun copy(): Unit = Unit(id, type, team, x, y, hp, level, xp, moved, acted, poison, slow, hold, tag, goal)
}

class Grave(val x: Int, val y: Int, var turns: Int)

class TeamState(
    var gold: Int, var ai: Boolean, var name: String?, var commanderType: String?,
    var commanderDead: Boolean = false, var commanderLevel: Int = 0, var commanderXp: Int = 0,
)

class TeamStats(var kills: Int = 0, var lost: Int = 0, var raised: Int = 0, var destroyed: Int = 0, var captured: Int = 0)

// Параметры создания партии (они же rinit для повтора): сид, уровень командира, сложность, туман
class CreateOpts(var seed: Long? = null, var commanderLevel: Int = 0, var difficulty: String? = null, var fog: Boolean? = null)
typealias RInit = CreateOpts

class GameState(
    var missionId: String, var w: Int, var h: Int, var terrain: Array<Array<String>>,
    var owner: LinkedHashMap<String, String?> = LinkedHashMap(),
    var units: MutableList<Unit> = ArrayList(),
    var graves: MutableList<Grave> = ArrayList(),
    var nextId: Int = 1, var turn: Int = 1,
    var order: List<String> = listOf("blue", "red"),
    var active: String = "blue",
    var teams: LinkedHashMap<String, TeamState> = LinkedHashMap(),
    var seed: Long = 0,
    var flags: MutableMap<String, Boolean> = LinkedHashMap(),
    var over: String? = null,
    var fog: Boolean = false,
    var difficulty: String = "normal",
    var stats: LinkedHashMap<String, TeamStats>? = null,
    var winner: String? = null,
    var log: MutableList<Action>? = null,
    var rinit: RInit? = null,
)

// Событие для анимации: поля зависят от type (hit, death, capture, levelup, ...)
class Event(
    val type: String,
    val unit: Int? = null, val team: String? = null, val level: Int? = null,
    val x: Int? = null, val y: Int? = null, val from: Int? = null, val to: Int? = null, val toTag: String? = null,
    val dmg: Int? = null, val fx: Int? = null, val fy: Int? = null, val unitType: String? = null,
    val prev: String? = null, val terrain: String? = null, val amount: Int? = null, val cost: Int? = null,
    val gold: Int? = null,
)

// Действие ИИ (и запись журнала): kind = "unit" | "buy" | "end"
class Action(
    var kind: String? = null, var unit: Int? = null, var to: Pos? = null, var act: String? = null, var target: Int? = null,
    var grave: Pos? = null, var spot: Pos? = null, var type: String? = null, var x: Int? = null, var y: Int? = null,
    var s: Long? = null, var score: Double? = null,
) {
    fun copy() = Action(kind, unit, to, act, target, grave, spot, type, x, y, s, score)
}

class ReachNode(val x: Int, val y: Int, val cost: Double, val prev: String?)
class Forecast(val dmg: Int, val counter: Int, val kills: Boolean)
class SpawnOpts(
    val level: Int = 0, val hp: Int? = null, val xp: Int = 0, val exhausted: Boolean = false,
    val hold: Boolean = false, val tag: String? = null, val goal: Pos? = null,
)

fun key(x: Int, y: Int): String = "$x,$y"
fun dist(ax: Int, ay: Int, bx: Int, by: Int): Int = abs(ax - bx) + abs(ay - by)
val DIRS: List<IntArray> = listOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

fun defOf(u: Unit): UnitDef = UNIT_DEFS[u.type]!!
fun defOf(type: String): UnitDef = UNIT_DEFS[type]!!
// Math.round из JS: к ближайшему, половина вверх
fun jsRound(x: Double): Double = floor(x + 0.5)

/* ---------- RNG с сидом (mulberry32, состояние лежит в state.seed как uint32) ---------- */
fun rand(st: GameState): Double {
    st.seed = (st.seed + 0x6D2B79F5L) and 0xFFFFFFFFL
    var t = st.seed.toInt()
    t = (t xor (t ushr 15)) * (t or 1)
    t = t xor (t + (t xor (t ushr 7)) * (t or 61))
    return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
}
fun randInt(st: GameState, a: Int, b: Int): Int = a + floor(rand(st) * (b - a + 1)).toInt()

/* ---------- Создание партии из описания миссии ---------- */
fun parseMap(rows: List<String>): Triple<Int, Int, Array<Array<String>>> {
    val h = rows.size
    val w = rows[0].length
    val terrain = Array(h) { y ->
        if (rows[y].length != w) throw IllegalArgumentException("map row $y length ${rows[y].length} != $w")
        Array(w) { x ->
            MAP_LEGEND[rows[y][x]] ?: throw IllegalArgumentException("unknown map char \"${rows[y][x]}\" at row $y")
        }
    }
    return Triple(w, h, terrain)
}

fun createState(mission: Mission, opts: CreateOpts = CreateOpts()): GameState {
    val (w, h, terrain) = parseMap(mission.map)
    val st = GameState(mission.id, w, h, terrain)
    st.order = mission.order ?: listOf("blue", "red")
    st.active = st.order[0]
    st.seed = (opts.seed ?: kotlin.random.Random.nextLong()) and 0xFFFFFFFFL
    st.fog = opts.fog ?: mission.fog
    st.difficulty = opts.difficulty ?: "normal"
    for (t in st.order) {
        val md = mission.teams[t] ?: TeamCfg()
        st.teams[t] = TeamState(md.gold, md.ai, md.name, md.commander)
    }
    // здания: по умолчанию нейтральные
    for (y in 0 until h) for (x in 0 until w) if (TERRAIN[terrain[y][x]]!!.building) st.owner[key(x, y)] = null
    for (b in mission.buildings) st.owner[key(b.x, b.y)] = b.team
    for (u in mission.units) {
        val lvl = if (u.commander && opts.commanderLevel != 0) opts.commanderLevel else u.level
        spawnUnit(st, u.type, u.team, u.x, u.y, SpawnOpts(level = lvl, hp = u.hp, hold = u.hold, tag = u.tag, goal = u.goal))
    }
    for (g in mission.graves) st.graves.add(Grave(g.x, g.y, 999))
    return st
}

fun spawnUnit(st: GameState, type: String, team: String, x: Int, y: Int, o: SpawnOpts = SpawnOpts()): Unit {
    val def = UNIT_DEFS[type]!!
    val u = Unit(
        st.nextId++, type, team, x, y,
        hp = if (o.hp != null && o.hp != 0) o.hp else BAL.maxHp, level = o.level, xp = o.xp,
        moved = o.exhausted, acted = o.exhausted,
    )
    if (o.hold) u.hold = true          // ИИ: стоять на месте, пока враг не подойдёт
    if (o.tag != null && o.tag != "") u.tag = o.tag        // метка для сценария миссии
    if (o.goal != null) u.goal = o.goal        // ИИ: идти к этой клетке (кристалл в симуляции)
    if (def.commander) {
        val t = st.teams[team]!!
        t.commanderType = type
        t.commanderDead = false
        t.commanderLevel = u.level
    }
    st.units.add(u)
    return u
}

/* ---------- Запросы ---------- */
fun inBounds(st: GameState, x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < st.w && y < st.h
fun terrainAt(st: GameState, x: Int, y: Int): String = st.terrain[y][x]
fun unitAt(st: GameState, x: Int, y: Int): Unit? = st.units.firstOrNull { it.x == x && it.y == y }
fun unitById(st: GameState, id: Int): Unit? = st.units.firstOrNull { it.id == id }
fun graveAt(st: GameState, x: Int, y: Int): Grave? = st.graves.firstOrNull { it.x == x && it.y == y }
fun ownerAt(st: GameState, x: Int, y: Int): String? = st.owner[key(x, y)]
fun isEnemy(a: Unit, b: Unit): Boolean = a.team != b.team
fun teamUnits(st: GameState, team: String): List<Unit> = st.units.filter { it.team == team }
fun commanderOf(st: GameState, team: String): Unit? = st.units.firstOrNull { it.team == team && defOf(it).commander }

fun buildingsOf(st: GameState, team: String, kind: String? = null): List<Pos> {
    val out = ArrayList<Pos>()
    for ((k, o) in st.owner) {
        if (o != team) continue
        val c = k.indexOf(',')
        val x = k.substring(0, c).toInt()
        val y = k.substring(c + 1).toInt()
        if (kind == null || st.terrain[y][x] == kind) out.add(Pos(x, y))
    }
    return out
}

// Огонёк рядом с клеткой (x, y): союзник u, не он сам
fun wispNear(st: GameState, u: Unit, x: Int, y: Int): Boolean =
    st.units.any { it.id != u.id && it.team == u.team && defOf(it).auraAtk && dist(it.x, it.y, x, y) == 1 }

// Атака с учётом уровня, яда и ауры огонька (если передано состояние; pos - откуда бьёт). Результат [min, max]
fun unitAtk(u: Unit, st: GameState?, pos: XY = u): IntArray {
    val d = defOf(u)
    var b = u.level * BAL.levelAtk
    if (u.poison != 0) b -= BAL.poisonAtk
    if (st != null && !d.noAttack && wispNear(st, u, pos.x, pos.y)) b += BAL.wispAtk
    return intArrayOf(max(0, d.atk[0] + b), max(0, d.atk[1] + b))
}
// Защита: своя + уровень + местность (водному - бонус в воде), яд снижает
fun terrainDefFor(st: GameState, u: Unit, x: Int, y: Int): Int {
    val d = defOf(u)
    val t = TERRAIN[terrainAt(st, x, y)]!!
    if (d.flying) return 0
    return t.def + (if (d.swim && t.water) BAL.waterDef else 0)
}
fun unitDef(st: GameState, u: Unit, x: Int = u.x, y: Int = u.y): Int {
    val d = defOf(u)
    return d.def + u.level * BAL.levelDef + terrainDefFor(st, u, x, y) - (if (u.poison != 0) BAL.poisonDef else 0)
}

// Стоимость хода юнита типа type по клетке (Infinity - непроходимо)
fun moveCostFor(st: GameState, type: String, x: Int, y: Int): Double {
    val d = defOf(type)
    if (d.flying) return 1.0
    val t = TERRAIN[terrainAt(st, x, y)]!!
    if (d.swim && t.water) return 1.0
    if (d.woods && terrainAt(st, x, y) == "forest") return 1.0   // волк: лес не замедляет
    return t.cost
}
fun moveCost(st: GameState, u: Unit, x: Int, y: Int): Double = moveCostFor(st, u.type, x, y)

fun moveOf(u: Unit): Int = max(1, defOf(u).move - (if (u.slow) BAL.slowMove else 0))

// Клетки, куда юнит может встать: key -> ReachNode(x, y, cost, prev)
fun reachable(st: GameState, u: Unit): LinkedHashMap<String, ReachNode> {
    val move = moveOf(u)
    val best = LinkedHashMap<String, ReachNode>()
    val start = ReachNode(u.x, u.y, 0.0, null)
    best[key(u.x, u.y)] = start
    val open = ArrayList<ReachNode>()
    open.add(start)
    while (open.isNotEmpty()) {
        open.sortBy { it.cost }
        val cur = open.removeAt(0)
        for (dir in DIRS) {
            val nx = cur.x + dir[0]
            val ny = cur.y + dir[1]
            if (!inBounds(st, nx, ny)) continue
            val other = unitAt(st, nx, ny)
            if (other != null && isEnemy(other, u)) continue           // враги перекрывают путь
            val c = cur.cost + moveCost(st, u, nx, ny)
            if (c > move) continue
            val k = key(nx, ny)
            val old = best[k]
            if (old != null && old.cost <= c) continue
            val node = ReachNode(nx, ny, c, key(cur.x, cur.y))
            best[k] = node
            open.add(node)
        }
    }
    // через союзников проходить можно, вставать нельзя
    val drop = ArrayList<String>()
    for ((k, n) in best) {
        val other = unitAt(st, n.x, n.y)
        if (other != null && other.id != u.id) drop.add(k)
    }
    for (k in drop) best.remove(k)
    return best
}

fun pathTo(reach: Map<String, ReachNode>, x: Int, y: Int): List<Pos> {
    val path = ArrayList<Pos>()
    var n = reach[key(x, y)]
    while (n != null) {
        path.add(0, Pos(n.x, n.y))
        n = if (n.prev != null) reach[n.prev] else null
    }
    return path
}

fun canAttackFrom(u: Unit, fromX: Int, fromY: Int): Boolean {
    val d = defOf(u)
    if (d.noAttack) return false
    if (d.noMoveAttack && (fromX != u.x || fromY != u.y)) return false
    return true
}

// Враги в радиусе атаки с клетки (fromX, fromY)
fun targetsFrom(st: GameState, u: Unit, fromX: Int, fromY: Int): List<Unit> {
    if (!canAttackFrom(u, fromX, fromY)) return emptyList()
    val r = defOf(u).range
    return st.units.filter {
        isEnemy(it, u) && it.hp > 0 && dist(fromX, fromY, it.x, it.y) >= r[0] && dist(fromX, fromY, it.x, it.y) <= r[1]
    }
}

// Все клетки, которые юнит может атаковать в этом ходу (для подсветки)
fun threatTiles(st: GameState, u: Unit, reach: Map<String, ReachNode> = reachable(st, u)): LinkedHashSet<String> {
    val out = LinkedHashSet<String>()
    val r = defOf(u).range
    val rMin = r[0]
    val rMax = r[1]
    for (n in reach.values) {
        if (!canAttackFrom(u, n.x, n.y)) continue
        for (dy in -rMax..rMax)
            for (dx in -rMax..rMax) {
                val d = abs(dx) + abs(dy)
                if (d < rMin || d > rMax) continue
                val x = n.x + dx
                val y = n.y + dy
                if (inBounds(st, x, y)) out.add(key(x, y))
            }
    }
    return out
}

fun gravesNear(st: GameState, x: Int, y: Int): List<Grave> =
    st.graves.filter { dist(it.x, it.y, x, y) == 1 && unitAt(st, it.x, it.y) == null }

// Катапульта: деревни в радиусе, которые можно разрушить (чужие и ничьи, не замки)
fun destroyTargets(st: GameState, u: Unit, fromX: Int = u.x, fromY: Int = u.y): List<Pos> {
    val d = defOf(u)
    if (!d.canDestroy || !canAttackFrom(u, fromX, fromY)) return emptyList()
    val out = ArrayList<Pos>()
    for ((k, o) in st.owner) {
        val c = k.indexOf(',')
        val x = k.substring(0, c).toInt()
        val y = k.substring(c + 1).toInt()
        if (st.terrain[y][x] != "village" || o == u.team || unitAt(st, x, y) != null) continue
        val r = dist(fromX, fromY, x, y)
        if (r >= d.range[0] && r <= d.range[1]) out.add(Pos(x, y))
    }
    return out
}

// Отстроить руины может тот, кто захватывает деревни
fun canRepairAt(st: GameState, u: Unit, x: Int = u.x, y: Int = u.y): Boolean =
    defOf(u).canCapture && terrainAt(st, x, y) == "ruins"

fun canCaptureAt(st: GameState, u: Unit, x: Int = u.x, y: Int = u.y): Boolean {
    val d = defOf(u)
    val t = terrainAt(st, x, y)
    if (!d.canCapture || !TERRAIN[t]!!.building) return false
    if (ownerAt(st, x, y) == u.team) return false
    if (t == "castle" && !d.commander) return false
    return true
}

/* ---------- Бой ---------- */
// Урон без случайности: mode "min" | "max" | "avg" | "roll"
fun damageOf(st: GameState, att: Unit, def: Unit, mode: String, attPos: XY? = null, defPos: XY? = null): Int {
    val a = defOf(att)
    val ap = unitAtk(att, st, attPos ?: att)
    val lo = ap[0]
    val hi = ap[1]
    var base: Double = when (mode) {
        "min" -> lo.toDouble()
        "max" -> hi.toDouble()
        "avg" -> (lo + hi) / 2.0
        else -> randInt(st, lo, hi).toDouble()
    }
    val dd = defOf(def)
    if (a.vsFlying != 0 && dd.flying) base += a.vsFlying
    if (a.vsUndead != 0 && dd.undead) base += a.vsUndead
    val dp = defPos ?: def
    val raw = max(0.0, base - unitDef(st, def, dp.x, dp.y))
    return min(def.hp.toDouble(), jsRound(raw * att.hp / BAL.maxHp)).toInt()
}

// Прогноз для интерфейса и ИИ: урон атаки и ответный урон (средние)
fun forecast(st: GameState, att: Unit, def: Unit, fromX: Int = att.x, fromY: Int = att.y): Forecast {
    val pos = Pos(fromX, fromY)
    val dmg = damageOf(st, att, def, "avg", pos, def)
    val left = def.hp - dmg
    var counter = 0
    if (left > 0 && canCounter(def, att, fromX, fromY)) {
        val fake = def.copy()
        fake.hp = left
        counter = damageOf(st, fake, att, "avg", def, pos)
    }
    return Forecast(dmg, counter, left <= 0)
}

fun canCounter(def: Unit, att: Unit, attX: Int, attY: Int): Boolean {
    val d = defOf(def)
    if (d.noAttack) return false
    val r = dist(def.x, def.y, attX, attY)
    return r >= d.range[0] && r <= d.range[1]
}

fun gainXp(st: GameState, u: Unit, amount: Int, events: MutableList<Event>) {
    if (u.level >= BAL.maxLevel) return
    u.xp += amount
    while (u.xp >= BAL.xpPerLevel && u.level < BAL.maxLevel) {
        u.xp -= BAL.xpPerLevel
        u.level++
        events.add(Event("levelup", unit = u.id, team = u.team, level = u.level, x = u.x, y = u.y))
    }
    if (defOf(u).commander) st.teams[u.team]!!.commanderLevel = u.level
}

fun strike(st: GameState, att: Unit, def: Unit, events: MutableList<Event>): Boolean {
    val dmg = damageOf(st, att, def, "roll")
    def.hp -= dmg
    if (def.hold) def.hold = false      // атакованный перестаёт стоять на месте
    events.add(Event("hit", from = att.id, to = def.id, toTag = def.tag, dmg = dmg, x = def.x, y = def.y, fx = att.x, fy = att.y))
    var xp = dmg
    val ad = defOf(att)
    val dd = defOf(def)
    if (def.hp <= 0) {
        xp += BAL.xpKillBonus
        killUnit(st, def, events)
    } else if (ad.poison && !dd.undead && !dd.noAttack) {
        if (def.poison == 0) events.add(Event("poison", unit = def.id, x = def.x, y = def.y))
        def.poison = BAL.poisonTurns
    }
    // сковывание - только в ход атакующего (ответный удар не сковывает): держится до конца хода цели
    if (def.hp > 0 && ad.slow && att.team == st.active && !def.slow && !dd.noAttack) {
        def.slow = true
        events.add(Event("slow", unit = def.id, x = def.x, y = def.y))
    }
    gainXp(st, att, xp, events)
    return def.hp <= 0
}

fun killUnit(st: GameState, u: Unit, events: MutableList<Event>) {
    st.units = st.units.filter { it.id != u.id }.toMutableList()
    val d = defOf(u)
    if (u.tag == "boss") st.flags["bossDead"] = true
    if (u.type == "crystal") st.flags["crystalLost"] = true
    if (d.commander) {
        val t = st.teams[u.team]!!
        t.commanderDead = true
        t.commanderLevel = u.level
    }
    if (u.type != "ghost" && u.type != "crystal") {
        st.graves = st.graves.filter { !(it.x == u.x && it.y == u.y) }.toMutableList()
        st.graves.add(Grave(u.x, u.y, BAL.graveTurns * st.order.size))
    }
    events.add(Event("death", unit = u.id, unitType = u.type, team = u.team, x = u.x, y = u.y))
}

/* ---------- Действия (все возвращают список событий для анимации) ---------- */
fun actMove(st: GameState, u: Unit, x: Int, y: Int): List<Event> {
    u.x = x; u.y = y; u.moved = true
    return emptyList()
}

fun actAttack(st: GameState, u: Unit, target: Unit): List<Event> {
    val events = ArrayList<Event>()
    val fromX = u.x
    val fromY = u.y
    val killed = strike(st, u, target, events)
    if (!killed && unitById(st, u.id) != null && canCounter(target, u, fromX, fromY)) strike(st, target, u, events)
    u.moved = true; u.acted = true
    return events
}

fun actCapture(st: GameState, u: Unit): List<Event> {
    val prev = ownerAt(st, u.x, u.y)
    st.owner[key(u.x, u.y)] = u.team
    u.moved = true; u.acted = true
    return listOf(Event("capture", unit = u.id, team = u.team, prev = prev, x = u.x, y = u.y, terrain = terrainAt(st, u.x, u.y)))
}

fun actRaise(st: GameState, u: Unit, g: Grave): List<Event> {
    st.graves = st.graves.filter { it !== g }.toMutableList()
    val gh = spawnUnit(st, "ghost", u.team, g.x, g.y, SpawnOpts(exhausted = true))
    u.moved = true; u.acted = true
    return listOf(Event("raise", unit = gh.id, x = g.x, y = g.y))
}

fun actDestroy(st: GameState, u: Unit, x: Int, y: Int): List<Event> {
    val prev = ownerAt(st, x, y)
    st.terrain[y][x] = "ruins"
    st.owner.remove(key(x, y))
    u.moved = true; u.acted = true
    return listOf(Event("destroy", unit = u.id, team = u.team, prev = prev, x = x, y = y, fx = u.x, fy = u.y))
}

fun actRepair(st: GameState, u: Unit): List<Event> {
    st.terrain[u.y][u.x] = "village"
    st.owner[key(u.x, u.y)] = u.team
    u.moved = true; u.acted = true
    return listOf(Event("repair", unit = u.id, team = u.team, x = u.x, y = u.y))
}

fun actWait(st: GameState, u: Unit): List<Event> { u.moved = true; u.acted = true; return emptyList() }

fun canBuy(st: GameState, team: String, type: String, x: Int, y: Int): Boolean {
    val t = st.teams[team]!!
    if (terrainAt(st, x, y) != "castle" || ownerAt(st, x, y) != team || unitAt(st, x, y) != null) return false
    if (teamUnits(st, team).size >= BAL.unitCap) return false
    return t.gold >= buyCost(st, team, type)
}

fun buyCost(st: GameState, team: String, type: String): Int {
    val d = defOf(type)
    if (d.commander) return BAL.commanderReviveBase + BAL.commanderRevivePerLevel * st.teams[team]!!.commanderLevel
    return d.cost
}

// Что можно купить в замке: обычные юниты + воскрешение павшего командира
fun buyOptions(st: GameState, team: String): List<String> {
    val list = BUY_LIST.toMutableList()
    val t = st.teams[team]!!
    val ct = t.commanderType
    if (t.commanderDead && ct != null) list.add(0, ct)
    return list
}

fun actBuy(st: GameState, team: String, type: String, x: Int, y: Int): List<Event> {
    val cost = buyCost(st, team, type)
    st.teams[team]!!.gold -= cost
    val lvl = if (defOf(type).commander) st.teams[team]!!.commanderLevel else 0
    val u = spawnUnit(st, type, team, x, y, SpawnOpts(exhausted = true, level = lvl))
    return listOf(Event("buy", unit = u.id, x = x, y = y, cost = cost))
}

/* ---------- Смена хода ---------- */
fun incomeOf(st: GameState, team: String): Int {
    var g = 0
    for (b in buildingsOf(st, team)) g += TERRAIN[terrainAt(st, b.x, b.y)]!!.income
    if (st.teams[team]!!.ai) g = (jsRound(g * (DIFFICULTY[st.difficulty] ?: DIFFICULTY["normal"]!!).aiIncome / 5) * 5).toInt()
    return g
}

fun endTurn(st: GameState): List<Event> {
    val events = ArrayList<Event>()
    for (u in teamUnits(st, st.active)) u.slow = false   // сковывание прошло
    // разбитые стороны пропускают ход
    var idx = st.order.indexOf(st.active)
    for (i in st.order.indices) {
        idx = (idx + 1) % st.order.size
        if (idx == 0) st.turn++
        if (!isEliminated(st, st.order[idx])) break
    }
    st.active = st.order[idx]
    // могилы исчезают со временем
    for (g in st.graves) g.turns--
    st.graves = st.graves.filter { it.turns > 0 }.toMutableList()
    events.addAll(beginTurn(st))
    return events
}

fun beginTurn(st: GameState): List<Event> {
    val team = st.active
    val events = ArrayList<Event>()
    val gold = incomeOf(st, team)
    st.teams[team]!!.gold += gold
    events.add(Event("income", team = team, gold = gold))
    val mine = teamUnits(st, team)
    for (u in mine) {
        u.moved = false; u.acted = false
        var heal = 0
        val t = terrainAt(st, u.x, u.y)
        val onBuilding = TERRAIN[t]!!.building && ownerAt(st, u.x, u.y) == team
        val healer = mine.any { it !== u && defOf(it).aura && dist(it.x, it.y, u.x, u.y) == 1 }
        if (onBuilding) heal += BAL.healBuilding
        if (healer) heal += BAL.healAura
        // яд: лечение снимает сразу, иначе слабеет с каждым ходом
        if (u.poison != 0) {
            if (onBuilding || healer) u.poison = 0 else u.poison--
            if (u.poison == 0) events.add(Event("cure", unit = u.id, x = u.x, y = u.y))
        }
        heal = min(heal, BAL.maxHp - u.hp)
        if (heal > 0) { u.hp += heal; events.add(Event("heal", unit = u.id, amount = heal, x = u.x, y = u.y)) }
    }
    return events
}

/* ---------- Туман войны ---------- */
// Клетки, которые видит сторона: обзор юнитов и своих зданий
fun visibleTiles(st: GameState, team: String): LinkedHashSet<String> {
    val vis = LinkedHashSet<String>()
    fun look(cx: Int, cy: Int, r: Int) {
        for (dy in -r..r)
            for (dx in -r..r)
                if (abs(dx) + abs(dy) <= r && inBounds(st, cx + dx, cy + dy)) vis.add(key(cx + dx, cy + dy))
    }
    for (u in st.units) {
        if (u.team != team) continue
        val t = terrainAt(st, u.x, u.y)
        look(u.x, u.y, BAL.vision + (if (defOf(u).flying) 1 else 0) + (if (t == "hill" || t == "mountain") 1 else 0))
    }
    for (b in buildingsOf(st, team)) look(b.x, b.y, BAL.visionBuilding)
    return vis
}

fun teamName(st: GameState, team: String): String = st.teams[team]!!.name ?: TEAMS[team]!!.name

// Сторона разбита: нет юнитов и нет замков
fun isEliminated(st: GameState, team: String): Boolean =
    teamUnits(st, team).isEmpty() && buildingsOf(st, team, "castle").isEmpty()
