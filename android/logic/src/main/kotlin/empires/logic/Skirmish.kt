// SKIRMISH: свободная схватка на симметричных картах (порт js/skirmish.js)
// Схватка описывается строкой id "sk:<карта>:<золото>:<стороны>[:f]", где стороны - по символу на слот
// карты: h - игрок, a - ИИ, n - нет; f в конце - туман войны. Так id сохраняется и перезапускается как id миссии кампании.
package empires.logic

import java.util.Base64

class Slot(val team: String, val castle: Pos, val cmd: Pos)
class SkirmishMap(
    val id: String, val title: String, val desc: String, val map: List<String>,
    val slots: List<Slot>, val custom: Boolean = false,
)

// Половина карты + средняя строка, остальное - поворот на 180° (честно для двоих)
fun rotateMap(top: List<String>, middle: String): List<String> {
    val rev = { s: String -> s.reversed() }
    return top + middle + top.reversed().map(rev)
}

// Четверть карты (с центральными строкой и столбцом), отражённая по обеим осям (для четверых)
fun mirrorMap(quarter: List<String>): List<String> {
    val row = { r: String -> r + r.substring(0, r.length - 1).reversed() }
    val half = quarter.map(row)
    return half + half.subList(0, half.size - 1).reversed()
}

private fun slot(team: String, cx: Int, cy: Int, x: Int, y: Int) = Slot(team, Pos(cx, cy), Pos(x, y))

val SKIRMISH_MAPS: List<SkirmishMap> = listOf(
    SkirmishMap(
        "valley", "Речная долина", "Двое, река с двумя мостами и бродом по холму.",
        rotateMap(
            listOf(
                "Mf..,C,..fM",
                "f..v,,,v..f",
                "..h.,.,.h..",
                "v..ff,ff..v",
                ".,.....h.,.",
                "hf.v....f.h",
            ), "~~=~~h~~=~~",
        ),
        listOf(slot("blue", 5, 12, 5, 11), slot("red", 5, 0, 5, 1)),
    ),
    SkirmishMap(
        "pass", "Горный перевал", "Двое, замки по разные стороны гор.",
        rotateMap(
            listOf(
                "Mh.v..,C.fM",
                "h..,,,,,..f",
                ".v.,.ff.,.v",
                "..,,.ff.,..",
                "ff,.hhh.,ff",
                "f.,..v..,.f",
            ), "~~=~~M~~=~~",
        ),
        listOf(slot("blue", 3, 12, 3, 11), slot("red", 7, 0, 7, 1)),
    ),
    SkirmishMap(
        "four", "Четыре замка", "До четырёх сторон, в центре ничейный замок.",
        mirrorMap(
            listOf(
                "ff.,..",
                "fC,,.v",
                ".,.f.,",
                ".,fh.,",
                "..h.f,",
                "v,,,,C",
            ),
        ),
        listOf(slot("blue", 1, 9, 2, 9), slot("yellow", 1, 1, 2, 1), slot("red", 9, 1, 8, 1), slot("green", 9, 9, 8, 9)),
    ),
    SkirmishMap(
        "delta", "Дельта", "Двое, протоки и мелководье: раздолье для водяных.",
        rotateMap(
            listOf(
                "Mf.v,C,..ss",
                "f..,,,,.ss~",
                ".v.,.ss.s~~",
                "ss,,s~~ss..",
                "~s..s~~.v.,",
                "~~s...h..,.",
            ), "h.ss~~~ss.h",
        ),
        listOf(slot("blue", 5, 12, 5, 11), slot("red", 5, 0, 5, 1)),
    ),
    SkirmishMap(
        "ruins", "Старые руины", "Двое, разорённый край: руины можно отстроить.",
        rotateMap(
            listOf(
                "Mh..,C,.hfM",
                "f.r.,,,.r.f",
                "..,,,.,,,..",
                "v.,.ff.f,.r",
                ".r,..h...,.",
                "f.,.r...v,f",
            ), "h.,..M..,.h",
        ),
        listOf(slot("blue", 5, 12, 5, 11), slot("red", 5, 0, 5, 1)),
    ),
    SkirmishMap(
        "lake", "Озёрный край", "До четырёх сторон вокруг большого озера.",
        mirrorMap(
            listOf(
                "Mf.,..",
                "fC,,.v",
                ".,..s.",
                ".,.ss~",
                ".vs~~~",
                "..s~~~",
            ),
        ),
        listOf(slot("blue", 1, 9, 2, 9), slot("yellow", 1, 1, 2, 1), slot("red", 9, 1, 8, 1), slot("green", 9, 9, 8, 9)),
    ),
    SkirmishMap(
        "isles", "Архипелаг", "Двое, острова и броды: водяным здесь раздолье.",
        rotateMap(
            listOf(
                "~s..,C,..s~",
                "~.v.,,,.v.~",
                "s..f...f..s",
                "~s~~=~=~~s~",
                "..v.s.s.v..",
                "f.h.~~~.h.f",
            ), "v..s=r=s..v",
        ),
        listOf(slot("blue", 5, 12, 5, 11), slot("red", 5, 0, 5, 1)),
    ),
    SkirmishMap(
        "thicket", "Чаща", "Двое, длинная лесная карта, в центре руины.",
        rotateMap(
            listOf(
                "ff.v.C.v.ff",
                "f..,,,,,..f",
                ".,,f.f.f,,.",
                ".,ff.h.ff,.",
                "v,f..h..f,v",
                ".,.ff.ff.,.",
                "ff,..f..,ff",
            ), "h.,,frf,,.h",
        ),
        listOf(slot("blue", 5, 14, 5, 13), slot("red", 5, 0, 5, 1)),
    ),
    SkirmishMap(
        "forts", "Крепости", "До четырёх сторон, руины и ничейный замок в центре.",
        mirrorMap(
            listOf(
                "M.f.v.",
                ".C,,,,",
                "f,.h.,",
                ".,h..r",
                "v,..f.",
                ".,r.,C",
            ),
        ),
        listOf(slot("blue", 1, 9, 2, 9), slot("yellow", 1, 1, 2, 1), slot("red", 9, 1, 8, 1), slot("green", 9, 9, 8, 9)),
    ),
)

// Свои карты игрока (заполняет приложение из сохранения), формат как у SKIRMISH_MAPS
val CUSTOM_MAPS: MutableList<SkirmishMap> = ArrayList()
fun skirmishMapById(id: String?): SkirmishMap? =
    SKIRMISH_MAPS.firstOrNull { it.id == id } ?: CUSTOM_MAPS.firstOrNull { it.id == id }

/* ---------- Код карты для обмена: "EMP1:" + base64url(JSON {t, m, s}) ---------- */
const val MAP_CODE_PREFIX = "EMP1:"
val MAP_CODE_TEAMS = listOf("blue", "red", "green", "yellow")

// JSON.stringify для строки: экранирование как в JS (непарные суррогаты как \udxxx)
fun jsonQuote(s: String): String {
    val sb = StringBuilder("\"")
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\b' -> sb.append("\\b")
            c == '\u000C' -> sb.append("\\f")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append("\\u").append(String.format(java.util.Locale.ROOT, "%04x", c.code))
            Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1]) -> { sb.append(c).append(s[i + 1]); i++ }
            Character.isSurrogate(c) -> sb.append("\\u").append(String.format(java.util.Locale.ROOT, "%04x", c.code))
            else -> sb.append(c)
        }
        i++
    }
    return sb.append('"').toString()
}

fun encodeMapCode(sm: SkirmishMap): String {
    val sb = StringBuilder()
    sb.append("{\"t\":").append(jsonQuote(sm.title)).append(",\"m\":[")
    sb.append(sm.map.joinToString(",") { jsonQuote(it) })
    sb.append("],\"s\":[")
    sb.append(sm.slots.joinToString(",") { "[${jsonQuote(it.team)},${it.castle.x},${it.castle.y},${it.cmd.x},${it.cmd.y}]" })
    sb.append("]}")
    val bin = sb.toString().toByteArray(Charsets.UTF_8)
    return MAP_CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bin)
}

// Разобрать код карты: SkirmishMap (как в CUSTOM_MAPS, с новым id) или String с текстом ошибки
fun decodeMapCode(code: String?, id: String): Any {
    // пробелы (как \s в JS) и BOM убираются; флаг (?U) в регулярках Android не поддерживает
    val c = (code ?: "").filter { !it.isWhitespace() && it != '﻿' && it != ' ' }
    if (!c.startsWith(MAP_CODE_PREFIX)) return "Это не код карты."
    val d: Any?
    try {
        val b64 = c.substring(MAP_CODE_PREFIX.length).replace('-', '+').replace('_', '/')
        val bin = Base64.getDecoder().decode(b64)
        d = Json.parse(String(bin, Charsets.UTF_8))
    } catch (e: Exception) { return "Код повреждён." }
    val dm = d as? Map<*, *> ?: return "Код повреждён."
    val rowsAny = dm["m"] as? List<*> ?: return "Код повреждён."
    if (rowsAny.isEmpty() || rowsAny[0] !is String) return "Код повреждён."
    val h = rowsAny.size
    val w = (rowsAny[0] as String).length
    if (w < 7 || w > 11 || h < 7 || h > 15) return "Неподходящий размер карты."
    val rows = ArrayList<String>()
    for (r in rowsAny) {
        if (r !is String || r.length != w || r.any { !MAP_LEGEND.containsKey(it) }) return "Код повреждён."
        rows.add(r)
    }
    fun at(x: Int, y: Int): String? = if (x >= 0 && y >= 0 && x < w && y < h) MAP_LEGEND[rows[y][x]] else null
    fun num(v: Any?): Int? {
        val dd = v as? Double ?: return null
        return if (dd == Math.floor(dd) && !dd.isInfinite() && Math.abs(dd) < 1e9) dd.toInt() else null
    }
    val seen = HashSet<String>()
    val slots = ArrayList<Slot>()
    for (s in (dm["s"] as? List<*>) ?: emptyList<Any?>()) {
        val arr = s as? List<*> ?: return "Код повреждён."
        val team = arr.getOrNull(0) as? String
        if (team == null || !MAP_CODE_TEAMS.contains(team) || seen.contains(team)) return "Код повреждён."
        val cx = num(arr.getOrNull(1)); val cy = num(arr.getOrNull(2))
        val x = num(arr.getOrNull(3)); val y = num(arr.getOrNull(4))
        if (cx == null || cy == null || x == null || y == null) return "Код повреждён."
        if (at(cx, cy) != "castle") return "Код повреждён."
        val t = at(x, y)
        if (t == null || TERRAIN[t]!!.cost.isInfinite() || t == "castle" || t == "village") return "Код повреждён."
        seen.add(team)
        slots.add(Slot(team, Pos(cx, cy), Pos(x, y)))
    }
    if (slots.size < 2) return "На карте меньше двух сторон."
    val tv = dm["t"]
    val ts = when {
        tv == null || tv == false || tv == "" || tv == 0.0 -> "Чужая карта"
        tv is String -> tv
        tv is Double -> if (tv == Math.floor(tv) && Math.abs(tv) < 1e15) tv.toLong().toString() else tv.toString()
        else -> tv.toString()
    }
    val title = if (ts.length > 24) ts.substring(0, 24) else ts
    return SkirmishMap(id, title, "Своя карта ${w}x${h}, сторон: ${slots.size}.", rows, slots, custom = true)
}

val SKIRMISH_GOLD: List<Int> = listOf(500, 1000, 2000)
// Командиры сторон в схватке
val SKIRMISH_CMD: Map<String, String> = linkedMapOf("blue" to "hero", "red" to "warlord", "green" to "necro", "yellow" to "warlord")

fun skirmishId(mapId: String, gold: Int, sides: String, fog: Boolean = false): String =
    "sk:$mapId:$gold:$sides${if (fog) ":f" else ""}"
fun isSkirmishId(id: String?): Boolean = id != null && id.startsWith("sk:")

// Собрать объект миссии по id схватки (null, если id некорректен)
fun skirmishMission(id: String): Mission? {
    val parts = id.split(":")
    val mapId = parts.getOrNull(1)
    val goldStr = parts.getOrNull(2)
    val sides = parts.getOrNull(3)
    val flags = parts.getOrNull(4)
    val sm = skirmishMapById(mapId)
    if (sm == null || sides == null || sides.isEmpty() || sides.length != sm.slots.size) return null
    val gold = goldStr?.trim()?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }?.toInt() ?: 0
    val teams = LinkedHashMap<String, TeamCfg>()
    val order = ArrayList<String>()
    val buildings = ArrayList<Building>()
    val units = ArrayList<UnitSpawn>()
    sm.slots.forEachIndexed { i, s ->
        val kind = sides[i]
        if (kind == 'n') return@forEachIndexed
        order.add(s.team)
        teams[s.team] = TeamCfg(gold = gold, ai = kind == 'a', name = TEAMS[s.team]!!.label, commander = SKIRMISH_CMD[s.team])
        buildings.add(Building(s.castle.x, s.castle.y, s.team))
        units.add(UnitSpawn(SKIRMISH_CMD[s.team]!!, s.team, s.cmd.x, s.cmd.y, commander = true))
    }
    return Mission(
        id = id, skirmish = true, title = sm.title, fog = flags == "f",
        goal = "Разбейте все войска противников и займите их замки.",
        map = sm.map, order = order, teams = teams, buildings = buildings, units = units,
    )
}

// Итог схватки: побеждает последняя уцелевшая сторона; игрок проигрывает, когда разбиты все люди
fun skirmishOutcome(st: GameState): String? {
    val alive = st.order.filter { !isEliminated(st, it) }
    val humans = st.order.filter { !st.teams[it]!!.ai }
    val players = if (humans.isNotEmpty()) humans else listOf("blue")   // без людей (симуляция) играем за синих
    if (players.none { alive.contains(it) }) return "lose"
    if (alive.size == 1) { st.winner = alive[0]; return "win" }
    return null
}
