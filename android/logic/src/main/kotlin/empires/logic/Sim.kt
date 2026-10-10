// SIM: ИИ против ИИ на миссиях кампании и картах схватки (порт tools/sim.js)
// ./gradlew :logic:run --args="--runs 20 --mission m2 --turns 40 --replay"
// --replay: каждую партию записать (действие + сид, как в игре) и проиграть заново, итог должен совпасть
package empires.logic

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.math.BigDecimal
import java.math.RoundingMode

private class PlayResult(val st: GameState, val out: String?, val rec: List<Action>)

// log: null - играет ИИ и пишет журнал; список - проигрывает журнал
private fun play(m: Mission, seed: Long, log: List<Action>?, maxTurns: Int): PlayResult {
    val st = createState(m, CreateOpts(seed = seed))
    st.teams["blue"]!!.ai = true
    val fired = HashSet<Int>()
    val rec = ArrayList<Action>()
    fun runTriggers(ctx: TriggerCtx) {
        m.triggers.forEachIndexed { i, t ->
            if (fired.contains(i) || !t.`when`(st, ctx)) return@forEachIndexed
            fired.add(i)
            for (s in t.spawn) spawnNear(st, s.type, s.team, s.x, s.y)
        }
    }
    beginTurn(st)
    var out: String? = null
    var steps = 0
    runTriggers(TriggerCtx("turn"))
    while (out == null && st.turn <= maxTurns) {
        if (++steps > 20000) throw IllegalStateException("stuck in " + m.id)
        val a: Action
        if (log != null) {
            if (steps - 1 >= log.size) break
            a = log[steps - 1]
            st.seed = a.s!!
        } else {
            a = aiNextAction(st, m)
            a.s = st.seed
            rec.add(a.copy())
        }
        val turnBefore = st.active
        val events = aiApply(st, a)
        runTriggers(TriggerCtx("events", events))
        if (a.kind == "end" && st.active != turnBefore) runTriggers(TriggerCtx("turn"))
        out = evaluateOutcome(st, m)
    }
    return PlayResult(st, out, rec)
}

fun main(args: Array<String>) {
    val stdout = PrintStream(FileOutputStream(FileDescriptor.out), false, "UTF-8")
    fun println(s: String) { stdout.print(s + "\n") }
    fun arg(n: String, d: String?): String? {
        val i = args.indexOf("--$n")
        return if (i >= 0 && i + 1 < args.size) args[i + 1] else d
    }
    val runs = arg("runs", "20")!!.toInt()
    val maxTurns = arg("turns", "40")!!.toInt()
    val only = arg("mission", null)
    val checkReplay = args.contains("--replay")
    var replayFails = 0

    val missions = MISSIONS + SKIRMISH_MAPS.map { skirmishMission(skirmishId(it.id, 1000, "a".repeat(it.slots.size)))!! }
    for (m in missions) {
        if (only != null && m.id != only) continue
        var win = 0
        var lose = 0
        var timeout = 0
        val turns = ArrayList<Int>()
        for (r in 0 until runs) {
            val (st, out, rec) = play(m, (r + 1).toLong(), null, maxTurns).let { Triple(it.st, it.out, it.rec) }
            when (out) { "win" -> win++; "lose" -> lose++; else -> timeout++ }
            turns.add(st.turn)
            if (checkReplay) {
                val again = play(m, (r + 1).toLong(), rec, maxTurns)
                if (stateToJson(again.st) != stateToJson(st)) { replayFails++; println("  replay mismatch: ${m.id} run ${r + 1}") }
            }
        }
        val avg = BigDecimal(turns.sum().toDouble() / turns.size).setScale(1, RoundingMode.HALF_UP).toPlainString()
        println("${m.id} ${m.title}: win $win, lose $lose, timeout $timeout, avg turns $avg")
    }
    if (checkReplay) println(if (replayFails != 0) "REPLAY: $replayFails mismatches" else "REPLAY: all match")
    stdout.flush()
}
