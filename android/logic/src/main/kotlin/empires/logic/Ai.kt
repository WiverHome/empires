// AI: ход компьютерного игрока (порт js/ai.js)
// aiNextAction(st) возвращает одно действие для активной стороны; интерфейс выполняет его
// с анимацией и спрашивает снова, пока не придёт Action(kind = "end").
package empires.logic

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object AI_TUNE {
    const val commanderWorth = 500.0
    const val ghostWorth = 150.0
    const val crystalWorth = 600.0
    const val destroyVillage = 75.0        // катапульте: разрушить вражескую деревню
    const val destroyNeutral = 20.0        // ... ничью
    const val repair = 80.0                // отстроить руины
    const val poisonHit = 25.0             // отравить цель, которая выживет
    const val slowHit = 15.0               // сковать цель, которая выживет
    const val wispNear = 8.0               // огоньку: за союзника рядом (усиление атаки)
    const val killBonus = 40.0
    const val captureVillage = 70.0
    const val captureEnemyVillage = 40.0   // добавка, если деревня вражеская
    const val captureCastle = 250.0
    const val raise = 90.0
    const val terrainDef = 1.2             // очков за единицу защиты местности
    const val approach = 5.0               // штраф за шаг до цели
    const val danger = 0.25                // доля ожидаемого урона по себе, которую считаем потерей
    const val lethal = 0.2                 // доля ценности юнита - штраф, если на клетке могут добить
    const val threatTop = 2                // сколько сильнейших угроз по клетке учитывать
    const val aggrMin = 0.6                // пределы агрессии (отношение сил армий)
    const val aggrMax = 1.8
    const val aggrPerTurn = 0.06           // с каждым ходом смелее, чтобы не стоять вечно
    const val supportAlly = 6.0            // за соседа-союзника (строй), до 2 соседей
    const val blockCastle = 25.0           // штраф за стояние на своём замке: мешает найму
    const val siegeReady = 15.0            // катапульте: враг в радиусе с этой клетки
    const val healerNearWounded = 25.0
    const val retreatHp = 40.0             // командир отступает лечиться ниже этого HP
    const val retreatHpUnit = 30.0         // обычный воин отступает ниже этого HP
    const val buyChance = 0.85
}

class AiCtx(val threats: Map<String, List<Unit>>, val aggr: Double, val noise: Int)

fun unitWorth(u: Unit): Double {
    val d = defOf(u)
    if (d.commander) return AI_TUNE.commanderWorth
    if (u.type == "ghost") return AI_TUNE.ghostWorth
    if (u.type == "crystal") return AI_TUNE.crystalWorth
    return d.cost.toDouble()
}

// Поле расстояний (в стоимости движения) от набора целей для данного юнита
fun distanceField(st: GameState, u: Unit, goals: List<XY>): Array<DoubleArray> {
    val INF = 1e9
    val f = Array(st.h) { DoubleArray(st.w) { INF } }
    val open = ArrayList<XY>()
    for (g in goals) { f[g.y][g.x] = 0.0; open.add(g) }
    while (open.isNotEmpty()) {
        open.sortBy { f[it.y][it.x] }
        val c = open.removeAt(0)
        for (dir in DIRS) {
            val x = c.x + dir[0]
            val y = c.y + dir[1]
            if (!inBounds(st, x, y)) continue
            val cost = moveCost(st, u, x, y)
            if (cost.isInfinite() || cost.isNaN()) continue
            val nd = f[c.y][c.x] + cost
            if (nd < f[y][x]) { f[y][x] = nd; open.add(Pos(x, y)) }
        }
    }
    return f
}

// Какие враги могут ударить по какой клетке в свой ход: key -> [враг]
fun threatMap(st: GameState, team: String): LinkedHashMap<String, MutableList<Unit>> {
    val m = LinkedHashMap<String, MutableList<Unit>>()
    for (e in st.units) {
        if (e.team == team) continue
        val reach = reachable(st, e)
        for (k in threatTiles(st, e, reach)) m.getOrPut(k) { ArrayList() }.add(e)
    }
    return m
}

// Ожидаемый урон по юниту u на клетке (x, y): сумма сильнейших угроз (кроме skipId - её убьём)
fun expectedHit(st: GameState, u: Unit, x: Int, y: Int, threats: Map<String, List<Unit>>, skipId: Int? = null): Int {
    val list = threats[key(x, y)] ?: return 0
    val me = u.copy()
    me.x = x; me.y = y; me.hp = BAL.maxHp
    val hits = ArrayList<Int>()
    for (e in list) {
        if (skipId != null && e.id == skipId) continue
        // катапульта не стреляет после движения: опасна только в своём радиусе с места
        val ed = defOf(e)
        if (ed.noMoveAttack) {
            val r = dist(e.x, e.y, x, y)
            if (r < ed.range[0] || r > ed.range[1]) continue
        }
        hits.add(damageOf(st, e, me, "avg", e, me))
    }
    hits.sortDescending()
    return hits.take(AI_TUNE.threatTop).sum()
}

// Отношение сил: >1 - мы сильнее и можно наступать, <1 - держим строй
fun aggression(st: GameState, team: String): Double {
    var mine = 0.0
    var theirs = 0.0
    for (u in st.units) {
        val w = unitWorth(u) * u.hp / BAL.maxHp
        if (u.team == team) mine += w else theirs += w
    }
    val r = if (theirs != 0.0) mine / theirs else AI_TUNE.aggrMax
    return max(AI_TUNE.aggrMin, min(AI_TUNE.aggrMax, r)) * (1 + st.turn * AI_TUNE.aggrPerTurn)
}

fun aiGoals(st: GameState, u: Unit): List<XY> {
    val d = defOf(u)
    val g0 = u.goal
    if (g0 != null) return listOf(g0)
    val goals = ArrayList<XY>()
    if (!d.noAttack) for (e in st.units) if (isEnemy(e, u)) goals.add(Pos(e.x, e.y))
    if (d.canCapture) {
        for ((k, o) in st.owner) {
            if (o == u.team) continue
            val c = k.indexOf(',')
            val x = k.substring(0, c).toInt()
            val y = k.substring(c + 1).toInt()
            if (st.terrain[y][x] == "castle" && !d.commander) continue
            goals.add(Pos(x, y))
        }
        for (y in 0 until st.h) for (x in 0 until st.w) if (st.terrain[y][x] == "ruins") goals.add(Pos(x, y))
    }
    // небоевые держатся поближе к своим
    if (d.noAttack) for (a in teamUnits(st, u.team)) if (a !== u) goals.add(Pos(a.x, a.y))
    if (goals.isEmpty()) for (c in buildingsOf(st, u.team, "castle")) goals.add(c)
    return goals
}

fun planUnit(st: GameState, u: Unit, ctx: AiCtx): Action? {
    val d = defOf(u)
    val reach = reachable(st, u)
    val allies = teamUnits(st, u.team)
    val worth = unitWorth(u)
    val wounded = u.type != "ghost" && u.hp < (if (d.commander) AI_TUNE.retreatHp else AI_TUNE.retreatHpUnit)
    var goals: List<XY> = aiGoals(st, u)
    if (wounded) {
        val safe = ArrayList<XY>(buildingsOf(st, u.team))
        for (a in allies) if (a !== u && defOf(a).aura) safe.add(a)
        if (safe.isNotEmpty()) goals = safe
    }
    val field = if (goals.isNotEmpty()) distanceField(st, u, goals) else null
    val curDist = if (field != null) field[u.y][u.x] else 0.0
    val hold = u.hold && !st.units.any { e -> isEnemy(e, u) && dist(e.x, e.y, u.x, u.y) <= d.move + d.range[1] + 2 }
    // командир и раненые берегутся сильнее; при перевесе сил - смелее
    val caution = (if (d.commander) 1.5 else 1.0) * (if (wounded) 1.5 else 1.0) / ctx.aggr
    fun risk(x: Int, y: Int, skipId: Int?): Double {
        val hit = min(expectedHit(st, u, x, y, ctx.threats, skipId), BAL.maxHp)
        var cost = hit * worth / 100 * AI_TUNE.danger
        if (hit >= u.hp) cost += worth * AI_TUNE.lethal
        return cost * caution
    }
    var best: Action? = null

    for (n in reach.values) {
        if (hold && (n.x != u.x || n.y != u.y)) continue
        val terr = terrainAt(st, n.x, n.y)
        var base = unitDef(st, u, n.x, n.y) * AI_TUNE.terrainDef
        if (field != null) {
            base -= min(field[n.y][n.x], 999.0) * AI_TUNE.approach
            // не стоять на месте без дела, если цель далеко
            if (n.x == u.x && n.y == u.y && curDist > d.move && !wounded) base -= 10
        }
        val near = allies.filter { it !== u && dist(it.x, it.y, n.x, n.y) == 1 }
        base += min(2, near.size) * AI_TUNE.supportAlly
        if (d.aura) base += near.count { it.hp < BAL.maxHp || it.poison != 0 } * AI_TUNE.healerNearWounded
        if (d.auraAtk) base += min(3, near.size) * AI_TUNE.wispNear
        val own = ownerAt(st, n.x, n.y) == u.team
        if (wounded && TERRAIN[terr]!!.building && own) base += 80
        if (terr == "castle" && own && !wounded) base -= AI_TUNE.blockCastle
        if (d.noMoveAttack && st.units.any { e -> isEnemy(e, u) && dist(e.x, e.y, n.x, n.y) >= d.range[0] && dist(e.x, e.y, n.x, n.y) <= d.range[1] })
            base += AI_TUNE.siegeReady
        val tileRisk = risk(n.x, n.y, null)

        fun consider(score0: Double, act: String, target: Int? = null, grave: Pos? = null, spot: Pos? = null) {
            var score = score0
            if (ctx.noise != 0) score += rand(st) * ctx.noise
            val b = best
            if (b == null || score > b.score!!) best = Action(kind = "unit", score = score, unit = u.id, to = Pos(n.x, n.y), act = act, target = target, grave = grave, spot = spot)
        }
        consider(base - tileRisk, "wait")

        if (canCaptureAt(st, u, n.x, n.y)) {
            var v = if (terr == "castle") AI_TUNE.captureCastle else AI_TUNE.captureVillage
            if (ownerAt(st, n.x, n.y) != null) v += AI_TUNE.captureEnemyVillage
            consider(base - tileRisk + v, "capture")
        }
        if (canRepairAt(st, u, n.x, n.y)) consider(base - tileRisk + AI_TUNE.repair, "repair")
        for (v in destroyTargets(st, u, n.x, n.y))
            consider(base - tileRisk + (if (ownerAt(st, v.x, v.y) != null) AI_TUNE.destroyVillage else AI_TUNE.destroyNeutral), "destroy", spot = v)
        if (d.canRaise) {
            val gs = st.graves.filter { g ->
                val ua = unitAt(st, g.x, g.y)
                dist(g.x, g.y, n.x, n.y) == 1 && !(ua != null && ua.id != u.id) && !(g.x == n.x && g.y == n.y)
            }
            if (gs.isNotEmpty() && allies.size < BAL.unitCap) consider(base - tileRisk + AI_TUNE.raise, "raise", grave = Pos(gs[0].x, gs[0].y))
        }
        for (t in targetsFrom(st, u, n.x, n.y)) {
            val f = forecast(st, u, t, n.x, n.y)
            var v = f.dmg * unitWorth(t) / 100 - f.counter * worth / 100
            if (f.kills) v += AI_TUNE.killBonus + unitWorth(t) / 10
            else if (d.poison && t.poison == 0 && !defOf(t).undead) v += AI_TUNE.poisonHit
            if (!f.kills && d.slow && !t.slow) v += AI_TUNE.slowHit
            if (defOf(t).commander) v *= 1.3
            // после атаки юнит остаётся на клетке; убитая цель в свой ход уже не ударит
            val r = if (f.kills) risk(n.x, n.y, t.id) else tileRisk
            consider(base - r + v + 10, "attack", target = t.id)
        }
    }
    return best
}

fun aiBuy(st: GameState, team: String, mission: Mission?): Action? {
    val t = st.teams[team]!!
    val md = mission?.teams?.get(team)
    val allowed = md?.buy ?: BUY_LIST
    val castles = buildingsOf(st, team, "castle").filter { unitAt(st, it.x, it.y) == null }
    if (castles.isEmpty() || teamUnits(st, team).size >= BAL.unitCap) return null
    val c = castles[0]
    val ct = t.commanderType
    if (t.commanderDead && ct != null && canBuy(st, team, ct, c.x, c.y))
        return Action(kind = "buy", type = ct, x = c.x, y = c.y)
    if (rand(st) > AI_TUNE.buyChance) return null
    val mine = teamUnits(st, team)
    val soldiers = mine.count { it.type == "soldier" }
    val neutralVillages = st.owner.keys.count { st.owner[it] != team }
    if (allowed.contains("soldier") && soldiers < 2 && neutralVillages > 0 && canBuy(st, team, "soldier", c.x, c.y))
        return Action(kind = "buy", type = "soldier", x = c.x, y = c.y)
    // самый дорогой из доступных, с небольшой случайностью
    val opts = allowed.filter { canBuy(st, team, it, c.x, c.y) }.sortedWith { a, b -> defOf(b).cost.compareTo(defOf(a).cost) }
    if (opts.isEmpty()) return null
    val pick = opts[min(opts.size - 1, floor(rand(st) * min(3, opts.size)).toInt())]
    return Action(kind = "buy", type = pick, x = c.x, y = c.y)
}

fun aiNextAction(st: GameState, mission: Mission? = null): Action {
    val team = st.active
    val ready = st.units.filter { it.team == team && !it.acted }
    if (ready.isNotEmpty()) {
        val ctx = AiCtx(threatMap(st, team), aggression(st, team), (DIFFICULTY[st.difficulty] ?: DIFFICULTY["normal"]!!).aiNoise)
        var best: Action? = null
        for (u in ready) {
            val p = planUnit(st, u, ctx)
            // юниты с атакой ходят первыми: они открывают дорогу остальным
            if (p != null && (best == null || p.score!! + (if (p.act == "attack") 1000 else 0) > best.score!! + (if (best.act == "attack") 1000 else 0))) best = p
        }
        if (best != null) return best
    }
    val b = aiBuy(st, team, mission)
    if (b != null) return b
    return Action(kind = "end")
}

// Применить действие ИИ к состоянию (без анимации): для симуляций
fun aiApply(st: GameState, a: Action): List<Event> {
    if (a.kind == "buy") return actBuy(st, st.active, a.type!!, a.x!!, a.y!!)
    if (a.kind == "end") return endTurn(st)
    val u = unitById(st, a.unit!!)!!
    actMove(st, u, a.to!!.x, a.to!!.y)
    if (a.act == "attack") return actAttack(st, u, unitById(st, a.target!!)!!)
    if (a.act == "capture") return actCapture(st, u)
    if (a.act == "raise") return actRaise(st, u, graveAt(st, a.grave!!.x, a.grave!!.y)!!)
    if (a.act == "repair") return actRepair(st, u)
    if (a.act == "destroy") return actDestroy(st, u, a.spot!!.x, a.spot!!.y)
    return actWait(st, u)
}
