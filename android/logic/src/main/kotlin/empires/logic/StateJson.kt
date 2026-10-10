// STATEJSON: сериализация партии в строку JSON (для сохранений и снимков отмены хода) и обратно.
// Формат свободный, но точный: stateToJson(stateFromJson(s)) == s.
package empires.logic

/* ---------- Минимальный разборщик JSON: объекты - LinkedHashMap, массивы - List, числа - Double ---------- */
object Json {
    fun parse(s: String): Any? {
        val p = Parser(s)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != s.length) throw IllegalArgumentException("trailing data at ${p.i}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun fail(msg: String): Nothing = throw IllegalArgumentException("$msg at $i")
        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++ }

        fun value(): Any? {
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else fail("unexpected '$c'")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("bad literal")
            i += word.length
            return v
        }

        fun num(): Double {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return s.substring(st, i).toDoubleOrNull() ?: fail("bad number")
        }

        fun str(): String {
            i++ // "
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad unicode escape")
                                sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("bad unicode escape"))
                                i += 4
                            }
                            else -> fail("bad escape '$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun arr(): List<Any?> {
            i++ // [
            val out = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                ws()
                out.add(value())
                ws()
                if (i >= s.length) fail("unterminated array")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return out }
                fail("expected , or ]")
            }
        }

        fun obj(): Map<String, Any?> {
            i++ // {
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("expected key")
                val k = str()
                ws()
                if (i >= s.length || s[i] != ':') fail("expected :")
                i++
                ws()
                out[k] = value()
                ws()
                if (i >= s.length) fail("unterminated object")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return out }
                fail("expected , or }")
            }
        }
    }
}

/* ---------- Запись ---------- */
private fun StringBuilder.q(s: String?): StringBuilder = if (s == null) append("null") else append(jsonQuote(s))
private fun StringBuilder.n(v: Number?): StringBuilder = if (v == null) append("null") else append(v.toString())
private fun StringBuilder.b(v: Boolean): StringBuilder = append(if (v) "true" else "false")
private fun StringBuilder.p(v: Pos?): StringBuilder = if (v == null) append("null") else append("[").append(v.x).append(",").append(v.y).append("]")

private fun StringBuilder.action(a: Action) {
    append("{\"kind\":").q(a.kind)
    append(",\"unit\":").n(a.unit)
    append(",\"to\":").p(a.to)
    append(",\"act\":").q(a.act)
    append(",\"target\":").n(a.target)
    append(",\"grave\":").p(a.grave)
    append(",\"spot\":").p(a.spot)
    append(",\"type\":").q(a.type)
    append(",\"x\":").n(a.x)
    append(",\"y\":").n(a.y)
    append(",\"s\":").n(a.s)
    append(",\"score\":").n(a.score)
    append("}")
}

fun actionsToJson(list: List<Action>): String {
    val sb = StringBuilder("[")
    list.forEachIndexed { i, a -> if (i > 0) sb.append(','); sb.action(a) }
    return sb.append("]").toString()
}

fun stateToJson(st: GameState): String {
    val sb = StringBuilder(4096)
    sb.append("{\"missionId\":").q(st.missionId)
    sb.append(",\"w\":").append(st.w).append(",\"h\":").append(st.h)
    sb.append(",\"terrain\":[")
    st.terrain.forEachIndexed { y, row ->
        if (y > 0) sb.append(',')
        sb.append('[')
        row.forEachIndexed { x, t -> if (x > 0) sb.append(','); sb.q(t) }
        sb.append(']')
    }
    sb.append("],\"owner\":{")
    var first = true
    for ((k, o) in st.owner) { if (!first) sb.append(','); first = false; sb.q(k).append(':').q(o) }
    sb.append("},\"units\":[")
    st.units.forEachIndexed { i, u ->
        if (i > 0) sb.append(',')
        sb.append("{\"id\":").append(u.id).append(",\"type\":").q(u.type).append(",\"team\":").q(u.team)
        sb.append(",\"x\":").append(u.x).append(",\"y\":").append(u.y).append(",\"hp\":").append(u.hp)
        sb.append(",\"level\":").append(u.level).append(",\"xp\":").append(u.xp)
        sb.append(",\"moved\":").b(u.moved).append(",\"acted\":").b(u.acted)
        sb.append(",\"poison\":").append(u.poison).append(",\"slow\":").b(u.slow).append(",\"hold\":").b(u.hold)
        sb.append(",\"tag\":").q(u.tag).append(",\"goal\":").p(u.goal).append('}')
    }
    sb.append("],\"graves\":[")
    st.graves.forEachIndexed { i, g ->
        if (i > 0) sb.append(',')
        sb.append("{\"x\":").append(g.x).append(",\"y\":").append(g.y).append(",\"turns\":").append(g.turns).append('}')
    }
    sb.append("],\"nextId\":").append(st.nextId).append(",\"turn\":").append(st.turn)
    sb.append(",\"order\":[").append(st.order.joinToString(",") { jsonQuote(it) }).append(']')
    sb.append(",\"active\":").q(st.active)
    sb.append(",\"teams\":{")
    first = true
    for ((k, t) in st.teams) {
        if (!first) sb.append(','); first = false
        sb.q(k).append(":{\"gold\":").append(t.gold).append(",\"ai\":").b(t.ai).append(",\"name\":").q(t.name)
        sb.append(",\"commanderType\":").q(t.commanderType).append(",\"commanderDead\":").b(t.commanderDead)
        sb.append(",\"commanderLevel\":").append(t.commanderLevel).append(",\"commanderXp\":").append(t.commanderXp).append('}')
    }
    sb.append("},\"seed\":").append(st.seed)
    sb.append(",\"flags\":{")
    first = true
    for ((k, v) in st.flags) { if (!first) sb.append(','); first = false; sb.q(k).append(':').b(v) }
    sb.append("},\"over\":").q(st.over)
    sb.append(",\"fog\":").b(st.fog).append(",\"difficulty\":").q(st.difficulty)
    sb.append(",\"stats\":")
    val stats = st.stats
    if (stats == null) sb.append("null") else {
        sb.append('{')
        first = true
        for ((k, s) in stats) {
            if (!first) sb.append(','); first = false
            sb.q(k).append(":{\"kills\":").append(s.kills).append(",\"lost\":").append(s.lost).append(",\"raised\":").append(s.raised)
            sb.append(",\"destroyed\":").append(s.destroyed).append(",\"captured\":").append(s.captured).append('}')
        }
        sb.append('}')
    }
    sb.append(",\"winner\":").q(st.winner)
    sb.append(",\"log\":")
    val log = st.log
    if (log == null) sb.append("null") else sb.append(actionsToJson(log))
    sb.append(",\"rinit\":")
    val r = st.rinit
    if (r == null) sb.append("null") else {
        sb.append("{\"seed\":").n(r.seed).append(",\"commanderLevel\":").append(r.commanderLevel)
        sb.append(",\"difficulty\":").q(r.difficulty).append(",\"fog\":")
        if (r.fog == null) sb.append("null") else sb.b(r.fog!!)
        sb.append('}')
    }
    sb.append('}')
    return sb.toString()
}

/* ---------- Чтение ---------- */
private fun Any?.int(): Int = (this as Double).toInt()
private fun Any?.intOrNull(): Int? = (this as Double?)?.toInt()
private fun Any?.long(): Long = (this as Double).toLong()
private fun Any?.str(): String = this as String
private fun Any?.bool(): Boolean = this as Boolean
private fun Any?.pos(): Pos? { val l = this as List<*>? ?: return null; return Pos(l[0].int(), l[1].int()) }
private fun Any?.map(): Map<*, *> = this as Map<*, *>
private fun Any?.list(): List<*> = this as List<*>

private fun actionFrom(v: Any?): Action {
    val m = v.map()
    return Action(
        kind = m["kind"] as String?, unit = m["unit"].intOrNull(), to = m["to"].pos(), act = m["act"] as String?,
        target = m["target"].intOrNull(), grave = m["grave"].pos(), spot = m["spot"].pos(), type = m["type"] as String?,
        x = m["x"].intOrNull(), y = m["y"].intOrNull(), s = (m["s"] as Double?)?.toLong(), score = m["score"] as Double?,
    )
}

fun actionsFromJson(json: String): MutableList<Action> = Json.parse(json).list().map { actionFrom(it) }.toMutableList()

fun stateFromJson(json: String): GameState {
    val m = Json.parse(json).map()
    val terrain = m["terrain"].list().map { row -> row.list().map { it.str() }.toTypedArray() }.toTypedArray()
    val st = GameState(m["missionId"].str(), m["w"].int(), m["h"].int(), terrain)
    for ((k, o) in m["owner"].map()) st.owner[k as String] = o as String?
    st.units = m["units"].list().map { v ->
        val u = v.map()
        Unit(
            u["id"].int(), u["type"].str(), u["team"].str(), u["x"].int(), u["y"].int(), u["hp"].int(),
            u["level"].int(), u["xp"].int(), u["moved"].bool(), u["acted"].bool(),
            u["poison"].int(), u["slow"].bool(), u["hold"].bool(), u["tag"] as String?, u["goal"].pos(),
        )
    }.toMutableList()
    st.graves = m["graves"].list().map { v -> val g = v.map(); Grave(g["x"].int(), g["y"].int(), g["turns"].int()) }.toMutableList()
    st.nextId = m["nextId"].int()
    st.turn = m["turn"].int()
    st.order = m["order"].list().map { it.str() }
    st.active = m["active"].str()
    for ((k, v) in m["teams"].map()) {
        val t = v.map()
        st.teams[k as String] = TeamState(
            t["gold"].int(), t["ai"].bool(), t["name"] as String?, t["commanderType"] as String?,
            t["commanderDead"].bool(), t["commanderLevel"].int(), t["commanderXp"].int(),
        )
    }
    st.seed = m["seed"].long()
    for ((k, v) in m["flags"].map()) st.flags[k as String] = v as Boolean
    st.over = m["over"] as String?
    st.fog = m["fog"].bool()
    st.difficulty = m["difficulty"].str()
    val stats = m["stats"]
    if (stats != null) {
        val out = LinkedHashMap<String, TeamStats>()
        for ((k, v) in stats.map()) {
            val s = v.map()
            out[k as String] = TeamStats(s["kills"].int(), s["lost"].int(), s["raised"].int(), s["destroyed"].int(), s["captured"].int())
        }
        st.stats = out
    }
    st.winner = m["winner"] as String?
    val log = m["log"]
    if (log != null) st.log = log.list().map { actionFrom(it) }.toMutableList()
    val r = m["rinit"]
    if (r != null) {
        val rm = r.map()
        st.rinit = RInit(seed = (rm["seed"] as Double?)?.toLong(), commanderLevel = rm["commanderLevel"].int(),
            difficulty = rm["difficulty"] as String?, fog = rm["fog"] as Boolean?)
    }
    return st
}

fun GameState.deepCopy(): GameState = stateFromJson(stateToJson(this))
