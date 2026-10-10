package empires.game

// Порт SAVE из js/main.js: прогресс кампании, настройки, свои карты, текущая партия, звёзды, достижения, повторы.
// Хранится в Preferences одной JSON-строкой. Свои карты лежат кодами EMP1 - тем же форматом, что и в вебе.

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import empires.logic.Action
import empires.logic.CUSTOM_MAPS
import empires.logic.GameState
import empires.logic.MISSIONS
import empires.logic.Mission
import empires.logic.Pos
import empires.logic.RInit
import empires.logic.SkirmishMap
import empires.logic.decodeMapCode
import empires.logic.deepCopy
import empires.logic.encodeMapCode
import empires.logic.missionStars
import empires.logic.skirmishMapById
import empires.logic.stateFromJson
import empires.logic.stateToJson

class SavedGame(val missionId: String, val fired: List<Int>, val state: GameState)

class ReplayRec(
    val missionId: String, val title: String, val res: String, val turn: Int, val date: Long,
    val rinit: RInit, val log: List<Action>, val mapCode: String?, val mapId: String?,
)

object Save {
    private const val KEY = "empires.save.v1"
    private const val REPLAYS_KEEP = 5

    var unlocked = 1
    var heroLevel = 0
    val completed = ArrayList<String>()
    val stars = LinkedHashMap<String, Int>()
    val achievements = LinkedHashMap<String, Long>()
    val prefs = LinkedHashMap<String, String>()
    val maps = ArrayList<SkirmishMap>()
    var current: SavedGame? = null
    val replays = ArrayList<ReplayRec>()

    private val store by lazy { Gdx.app.getPreferences(KEY) }

    fun load() {
        val raw = store.getString("data", "")
        if (raw.isNotEmpty()) runCatching { read(JsonReader().parse(raw)) }.onFailure { Gdx.app.error("Empires", "битое сохранение", it) }
        CUSTOM_MAPS.clear()
        CUSTOM_MAPS.addAll(maps)
        // в кампании могли появиться новые миссии: открыть следующую за последней пройденной
        for (id in completed) {
            val idx = MISSIONS.indexOfFirst { it.id == id }
            if (idx >= 0) unlocked = maxOf(unlocked, minOf(MISSIONS.size, idx + 2))
        }
    }

    // Каждый раздел читается отдельно: ошибка в одном не должна стереть остальные при следующей записи
    private fun section(name: String, f: () -> Unit) { runCatching(f).onFailure { Gdx.app.error("Empires", "сохранение: раздел $name", it) } }

    private fun read(j: JsonValue) {
        unlocked = j.getInt("unlocked", 1)
        heroLevel = j.getInt("heroLevel", 0)
        section("completed") { j.get("completed")?.forEach { completed.add(it.asString()) } }
        section("stars") { j.get("stars")?.forEach { stars[it.name] = it.asInt() } }
        section("achievements") { j.get("achievements")?.forEach { achievements[it.name] = it.asLong() } }
        section("prefs") { j.get("prefs")?.forEach { prefs[it.name] = it.asString() } }
        j.get("maps")?.forEach { m ->
            section("maps") {
                val res = decodeMapCode(m.getString("code"), m.getString("id"))
                if (res is SkirmishMap) maps.add(res) else Gdx.app.error("Empires", "своя карта не прочиталась: $res")
            }
        }
        j.get("current")?.let { c ->
            runCatching { current = SavedGame(c.getString("missionId"), c.get("fired").asIntArray().toList(), stateFromJson(c.getString("state"))) }
                .onFailure { Gdx.app.error("Empires", "не удалось прочитать сохранение партии", it) }
        }
        j.get("replays")?.forEach { r ->
            runCatching {
                replays.add(ReplayRec(
                    r.getString("missionId"), r.getString("title"), r.getString("res"), r.getInt("turn"), r.getLong("date"),
                    rinitFrom(r.get("rinit")), r.get("log").map { actionFrom(it) },
                    r.getString("mapCode", null), r.getString("mapId", null),
                ))
            }
        }
    }

    fun write() {
        val j = JsonValue(JsonValue.ValueType.`object`)
        j.addChild("unlocked", JsonValue(unlocked.toLong()))
        j.addChild("heroLevel", JsonValue(heroLevel.toLong()))
        j.addChild("completed", arr(completed.map { JsonValue(it) }))
        j.addChild("stars", obj(stars.mapValues { JsonValue(it.value.toLong()) }))
        j.addChild("achievements", obj(achievements.mapValues { JsonValue(it.value) }))
        j.addChild("prefs", obj(prefs.mapValues { JsonValue(it.value) }))
        j.addChild("maps", arr(maps.map { obj(mapOf("id" to JsonValue(it.id), "code" to JsonValue(encodeMapCode(it)))) }))
        current?.let { c ->
            j.addChild("current", obj(mapOf(
                "missionId" to JsonValue(c.missionId),
                "fired" to arr(c.fired.map { JsonValue(it.toLong()) }),
                "state" to JsonValue(stateToJson(c.state)),
            )))
        }
        j.addChild("replays", arr(replays.map { r ->
            obj(buildMap {
                put("missionId", JsonValue(r.missionId)); put("title", JsonValue(r.title)); put("res", JsonValue(r.res))
                put("turn", JsonValue(r.turn.toLong())); put("date", JsonValue(r.date))
                put("rinit", rinitTo(r.rinit)); put("log", arr(r.log.map { actionTo(it) }))
                r.mapCode?.let { put("mapCode", JsonValue(it)) }
                r.mapId?.let { put("mapId", JsonValue(it)) }
            })
        }))
        store.putString("data", j.toJson(JsonWriter.OutputType.json))
        store.flush()
    }

    fun prefBool(k: String) = prefs[k] == "1"
    fun setPref(k: String, v: Boolean) = setPref(k, if (v) "1" else "0")
    fun setPref(k: String, v: String) { prefs[k] = v; write() }

    fun saveCurrent(missionId: String, st: GameState, fired: List<Int>) {
        current = SavedGame(missionId, fired.toList(), st.deepCopy())
        write()
    }
    fun clearCurrent() { current = null; write() }

    fun missionWon(id: String, st: GameState) {
        val idx = MISSIONS.indexOfFirst { it.id == id }
        stars[id] = maxOf(stars[id] ?: 0, missionStars(MISSIONS[idx], st))
        if (id !in completed) completed.add(id)
        unlocked = maxOf(unlocked, minOf(MISSIONS.size, idx + 2))
        // уровень Ардана переходит в следующую миссию
        heroLevel = maxOf(heroLevel, st.teams.getValue("blue").commanderLevel)
        write()
    }

    fun saveMap(m: SkirmishMap) {
        val i = maps.indexOfFirst { it.id == m.id }
        if (i >= 0) maps[i] = m else maps.add(m)
        syncMaps()
    }
    fun deleteMap(id: String) { maps.removeAll { it.id == id }; syncMaps() }
    private fun syncMaps() { CUSTOM_MAPS.clear(); CUSTOM_MAPS.addAll(maps); write() }

    // Последние партии для повтора (журнал действий + сид). Своя карта сохраняется кодом.
    fun addReplay(mission: Mission, st: GameState, res: String) {
        val log = st.log ?: return
        val rinit = st.rinit ?: return
        val sm = if (mission.skirmish) skirmishMapById(mission.id.split(":")[1]) else null
        replays.add(0, ReplayRec(
            mission.id, mission.title, res, st.turn, System.currentTimeMillis(), rinit, log.toList(),
            sm?.takeIf { it.custom }?.let { encodeMapCode(it) }, sm?.takeIf { it.custom }?.id,
        ))
        while (replays.size > REPLAYS_KEEP) replays.removeAt(replays.size - 1)
        write()
    }

    /* ---------- JSON-помощники ---------- */
    private fun arr(items: List<JsonValue>) = JsonValue(JsonValue.ValueType.array).also { a -> items.forEach { a.addChild(it) } }
    private fun obj(items: Map<String, JsonValue>) = JsonValue(JsonValue.ValueType.`object`).also { o -> items.forEach { (k, v) -> o.addChild(k, v) } }

    private fun posTo(p: Pos) = obj(mapOf("x" to JsonValue(p.x.toLong()), "y" to JsonValue(p.y.toLong())))
    private fun posFrom(j: JsonValue?) = j?.let { Pos(it.getInt("x"), it.getInt("y")) }

    fun actionTo(a: Action): JsonValue = obj(buildMap {
        a.kind?.let { put("kind", JsonValue(it)) }
        a.unit?.let { put("unit", JsonValue(it.toLong())) }
        a.to?.let { put("to", posTo(it)) }
        a.act?.let { put("act", JsonValue(it)) }
        a.target?.let { put("target", JsonValue(it.toLong())) }
        a.grave?.let { put("grave", posTo(it)) }
        a.spot?.let { put("spot", posTo(it)) }
        a.type?.let { put("type", JsonValue(it)) }
        a.x?.let { put("x", JsonValue(it.toLong())) }
        a.y?.let { put("y", JsonValue(it.toLong())) }
        a.s?.let { put("s", JsonValue(it)) }
    })

    fun actionFrom(j: JsonValue) = Action(
        kind = j.getString("kind", null), unit = j.get("unit")?.asInt(), to = posFrom(j.get("to")),
        act = j.getString("act", null), target = j.get("target")?.asInt(), grave = posFrom(j.get("grave")),
        spot = posFrom(j.get("spot")), type = j.getString("type", null), x = j.get("x")?.asInt(), y = j.get("y")?.asInt(),
        s = j.get("s")?.asLong(),
    )

    fun rinitTo(r: RInit) = obj(mapOf(
        "seed" to JsonValue(r.seed ?: 0L), "commanderLevel" to JsonValue(r.commanderLevel.toLong()), "difficulty" to JsonValue(r.difficulty ?: "normal"),
    ))
    fun rinitFrom(j: JsonValue) = RInit(seed = j.getLong("seed"), commanderLevel = j.getInt("commanderLevel"), difficulty = j.getString("difficulty"))
}
