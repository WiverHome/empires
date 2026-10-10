// ACHIEVEMENTS: статистика партии, звёзды миссий, достижения (порт js/achievements.js, без графики)
package empires.logic

// Статистика стороны в партии: лежит в st.stats, поэтому переживает сохранение и отмену хода
fun teamStats(st: GameState, team: String): TeamStats {
    val m = st.stats ?: LinkedHashMap<String, TeamStats>().also { st.stats = it }
    return m.getOrPut(team) { TeamStats() }
}

// Учесть события действия. Убийцу берём из удара перед смертью.
fun trackEvents(st: GameState, events: List<Event>) {
    var lastFrom: String? = null
    for (e in events) {
        when (e.type) {
            "hit" -> { val a = unitById(st, e.from!!); lastFrom = a?.team ?: st.active }
            "death" -> {
                if (defOf(e.unitType!!).noAttack) continue
                teamStats(st, e.team!!).lost++
                val killer = if (lastFrom != null && lastFrom != e.team) lastFrom else st.active
                if (killer != e.team) teamStats(st, killer).kills++
            }
            "raise" -> teamStats(st, st.active).raised++
            "destroy" -> teamStats(st, st.active).destroyed++
            "capture" -> teamStats(st, st.active).captured++
        }
    }
}

// Звёзды за победу в миссии кампании: победа, не дольше par ходов, не больше maxLost павших
const val STARS_MAX_LOST = 3
fun missionStars(mission: Mission, st: GameState): Int {
    val s = teamStats(st, "blue")
    var n = 1
    val par = mission.par
    if (par != null && par != 0 && st.turn <= par) n++
    if (s.lost <= (mission.maxLost ?: STARS_MAX_LOST)) n++
    return n
}
fun starsHint(mission: Mission): String =
    "★ победа · ★ не дольше ${mission.par} ходов · ★ не больше ${mission.maxLost ?: STARS_MAX_LOST} павших"

// Контекст проверки: phase = "events" | "end" | "map". stars - звёзды миссий кампании, have - уже полученные достижения
class AchCtx(
    val phase: String, val st: GameState? = null, val mission: Mission? = null,
    val events: List<Event> = emptyList(), val res: String? = null, val humans: List<String> = emptyList(),
    val stars: Map<String, Int> = emptyMap(), val have: Set<String> = emptySet(),
)

class Achievement(val id: String, val name: String, val desc: String, val check: (AchCtx) -> Boolean)

private fun humanWon(c: AchCtx): Boolean =
    c.phase == "end" && c.res == "win" && (!c.mission!!.skirmish || c.humans.contains(c.st!!.winner))
private fun vsAi(c: AchCtx): Boolean = c.st!!.order.any { c.st.teams[it]!!.ai }

val ACHIEVEMENTS: List<Achievement> = listOf(
    Achievement("first_win", "Первая победа", "Выиграйте любую партию.") { humanWon(it) },
    Achievement("chapter1", "Конец Морвена", "Пройдите первую главу кампании.") { humanWon(it) && it.mission!!.id == "m4" },
    Achievement("chapter2", "Тишина на болотах", "Пройдите вторую главу кампании.") { humanWon(it) && it.mission!!.id == "m8" },
    Achievement("chapter3", "Хроники завершены", "Пройдите третью главу кампании.") { humanWon(it) && it.mission!!.id == "m12" },
    Achievement("three_stars", "Безупречно", "Получите три звезды в миссии.") {
        humanWon(it) && !it.mission!!.skirmish && missionStars(it.mission, it.st!!) == 3
    },
    Achievement("all_stars", "Летописец", "Соберите все звёзды кампании.") { c -> MISSIONS.all { c.stars[it.id] == 3 } },
    Achievement("skirmish_win", "Полководец", "Победите ИИ в схватке.") { humanWon(it) && it.mission!!.skirmish && vsAi(it) },
    Achievement("free_for_all", "Царь горы", "Победите в схватке трёх и более сторон.") {
        humanWon(it) && it.mission!!.skirmish && it.st!!.order.size >= 3
    },
    Achievement("fog_win", "Вслепую", "Победите в схватке с туманом войны.") {
        humanWon(it) && it.mission!!.skirmish && it.st!!.fog && vsAi(it)
    },
    Achievement("hard_win", "Железная воля", "Победите ИИ на сложной сложности.") {
        humanWon(it) && vsAi(it) && it.st!!.difficulty == "hard"
    },
    Achievement("blitz", "Молниеносно", "Победите в схватке с ИИ за 12 ходов или быстрее.") {
        humanWon(it) && it.mission!!.skirmish && vsAi(it) && it.st!!.turn <= 12
    },
    Achievement("no_losses", "Ни шагу назад", "Победите, не потеряв ни одного воина.") { c ->
        humanWon(c) && c.humans.all { teamStats(c.st!!, it).lost == 0 }
    },
    Achievement("hotseat", "Друзья-соперники", "Доиграйте партию вдвоём на одном телефоне.") { it.phase == "end" && it.humans.size >= 2 },
    Achievement("veteran", "Ветеран", "Поднимите воина до 5 уровня.") { c ->
        c.events.any { it.type == "levelup" && it.level!! >= 5 && c.humans.contains(it.team) }
    },
    Achievement("necromancer", "Тёмное искусство", "Поднимите призрака из могилы.") { c ->
        c.humans.contains(c.st!!.active) && c.events.any { it.type == "raise" }
    },
    Achievement("siege", "Осадный мастер", "Разрушьте деревню катапультой.") { c ->
        c.humans.contains(c.st!!.active) && c.events.any { it.type == "destroy" }
    },
    Achievement("full_army", "Великая армия", "Соберите отряд из ${BAL.unitCap} воинов.") { c ->
        c.humans.any { teamUnits(c.st!!, it).size >= BAL.unitCap }
    },
    Achievement("map_maker", "Картограф", "Создайте свою карту в редакторе.") { it.phase == "map" },
)

// Новые достижения (id), которых ещё нет в ctx.have
fun checkAchievements(ctx: AchCtx): List<String> {
    if (ctx.phase == "map") return if (ctx.have.contains("map_maker")) emptyList() else listOf("map_maker")
    return ACHIEVEMENTS.filter { !ctx.have.contains(it.id) && it.check(ctx) }.map { it.id }
}
