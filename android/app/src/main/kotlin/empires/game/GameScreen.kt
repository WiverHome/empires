package empires.game

// Порт js/game.js (контроллер партии) и js/render.js (карта, камера, подсветка, анимации).
// Структура и имена сохранены, чтобы правки в вебе было легко повторить здесь.

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import empires.logic.*
import kotlin.Unit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private const val ZOOM_MAX = 2.5f

object ANIM {
    const val moveStep = 90f
    const val lunge = 140f
    const val hit = 260f
    const val death = 420f
    const val floater = 900f
    const val water = 650f
}

class Offset { var dx = 0f; var dy = 0f; var alpha = 1f; var flash = 0f }
class Floater(val text: String, val x: Int, val y: Int, val color: Color, val t0: Float)
class Dying(val type: String, val team: String, val x: Int, val y: Int) { var alpha = 1f }
class Highlights {
    var reach: Set<String>? = null; var threat: List<String>? = null; var targets: List<empires.logic.Unit>? = null
    var graves: List<Pos>? = null; var path: List<Pos>? = null; var enemy: List<String>? = null
}

// net - сетевая партия: localTeams ходят с этого телефона, ходы остальных людей приходят по связи
class GameScreen(
    private val missionId: String,
    private val resume: SavedGame? = null,
    private val replayRec: ReplayRec? = null,
    private val net: NetSession? = null,
    private val netInit: RInit? = null,
) : AppScreen() {
    lateinit var mission: Mission
    lateinit var st: GameState
    var mode = "busy"     // idle | selected | action | targeting | raising | destroying | confirm | busy | dialog | over
    private var sel: Int? = null
    private var orig: Pos? = null
    private var reach: Map<String, ReachNode>? = null
    private var fired = ArrayList<Int>()
    private var aiRunning = false
    private var session = 0
    private val history = ArrayList<Pair<String, List<Int>>>()
    private var replay: ReplayCursor? = null
    private var pending: Pending? = null
    private var lastHuman: String? = null
    private val scope: CoroutineScope = newGameScope()
    private val anim = Animator()

    private class ReplayCursor(val log: List<Action>) { var i = 0 }
    private class Pending(val target: Int, val x: Int, val y: Int)

    // --- вид (VIEW) ---
    private var ts = 48f
    private var camX = 0f
    private var camY = 0f
    private var layers: Array<Texture>? = null
    private var hover: Pos? = null
    private var hl = Highlights()
    private var selected: Int? = null
    private val offsets = HashMap<Int, Offset>()
    private val floaters = ArrayList<Floater>()
    private var shake = 0f
    private var fogTeam: String? = null
    private var vis: Set<String>? = null
    private val dying = ArrayList<Dying>()
    private var marks: List<Pos>? = null

    // --- интерфейс ---
    private val mapView = MapView()
    private val overlay = WidgetGroup()
    private lateinit var hudTurn: Label
    private lateinit var hudGold: Label
    private lateinit var btnUndo: TextButton
    private lateinit var btnEnd: TextButton
    private val info = Table()
    private val hint = Ui.label("", C.text, Ui.fontSmall)
    private var popup: Table? = null
    private var forecastBox: Table? = null
    private var banner: Table? = null
    private var dialogBox: Table? = null

    private val selUnit get() = sel?.let { unitById(st, it) }

    /* ---------- Построение экрана ---------- */
    override fun build(): Table {
        val root = Table()
        root.background = Ui.solid(C.bg)
        // HUD
        val hud = Table().apply { background = Ui.solid(Color.valueOf("#1f1a28")); pad(6f, 10f, 6f, 10f) }
        hudTurn = Ui.label("")
        hudGold = Ui.label("")
        btnUndo = Ui.button("↶") { undoAction() }
        // значок из шрифта символов мелкий: крупный шрифт для этой кнопки
        btnUndo.label.style = com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(Ui.fontBig, btnUndo.label.style.fontColor)
        val btnMenu = Ui.button("Меню") { openGameMenu() }
        hud.add(hudTurn).padRight(12f)
        hud.add(Image(TextureRegionDrawable(APP.assets.coin))).size(20f).padRight(4f)
        hud.add(hudGold)
        hud.add().expandX()
        hud.add(btnUndo).width(44f).padRight(6f)
        hud.add(btnMenu)
        root.add(hud).growX().row()
        // карта с наложениями
        overlay.touchable = Touchable.childrenOnly
        val stack = Stack(mapView, overlay)
        root.add(stack).grow().row()
        hint.isVisible = false
        // низ: информация и конец хода
        val bottom = Table().apply { background = Ui.solid(Color.valueOf("#231d2d")); pad(6f) }
        info.top().left()
        info.add(Ui.small("Нажмите на клетку или воина", C.muted)).growX().left()
        btnEnd = Ui.button("Конец\nхода", primary = true) { endTurnClicked() }
        bottom.add(info).expand().fill().left().top().minWidth(0f)
        bottom.add(btnEnd).width(84f).growY().padLeft(6f)
        root.add(bottom).growX().height(96f)
        return root
    }

    override fun show() {
        Sfx.playMusic("battle")
        net?.onLost = { if (mode != "over") netLost() }
        scope.launch { startMission() }
    }

    override fun hide() {
        session++
        scope.stop()
        anim.clear()
        layers?.forEach { it.dispose() }
        layers = null
        net?.close()
    }

    override fun update(dt: Float) {
        anim.update(dt)
        layoutOverlays()
    }

    override fun resized() { zoomFit() }

    override fun paused() { if (::st.isInitialized && mode != "over" && replay == null && net == null) saveCurrent() }

    override fun onBack() = onCancel()

    /* ---------- Запуск миссии ---------- */
    private suspend fun startMission() {
        mission = missionById(missionId) ?: run { APP.setScreen(TitleScreen()); return }
        session++
        aiRunning = false
        replay = replayRec?.let { ReplayCursor(it.log) }
        fired = ArrayList(resume?.fired ?: emptyList())
        if (resume != null) st = resume.state
        else {
            // всё, что нужно для повтора: сид и параметры создания, затем журнал действий в st.log
            val rinit = replayRec?.rinit ?: netInit ?: RInit(
                seed = (Math.random() * 4294967296.0).toLong(),
                commanderLevel = if (mission.skirmish) 0 else Save.heroLevel,
                difficulty = Save.prefs["difficulty"] ?: "normal",
            )
            st = createState(mission, rinit)
            st.rinit = rinit
            st.log = ArrayList()
        }
        lastHuman = null
        history.clear()
        sel = null
        mode = "busy"
        setViewState()
        marks = mission.marks
        commanderOf(st, viewTeam())?.let { centerOn(it.x, it.y) }
        updateHud()
        if (resume != null) {
            showBanner()
            startHumanTurn()
            return
        }
        showBanner(mission.title, mission.goal)
        showDialog(mission.intro)
        val events = beginTurn(st)
        afterTurnStart(events)
    }

    private suspend fun afterTurnStart(events: List<Event>) {
        updateHud()
        showBanner()
        val heals = events.filter { it.type == "heal" }
        if (heals.isNotEmpty() && isHumanTurn()) animEvents(heals)
        else if (heals.isNotEmpty()) scope.launch { animEvents(heals) }
        runTriggers(TriggerCtx("turn", events))
        if (checkOutcome()) return
        if (!isHumanTurn()) scope.launch { runDriver() }
        else {
            if (humanTeams().size > 1) passDevice()
            startHumanTurn()
        }
    }

    // Игра вдвоём на одном устройстве: пауза, чтобы передать телефон
    private suspend fun passDevice() {
        val t = TEAMS.getValue(st.active)
        fogTeam = if (st.fog) "none" else null
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
            val m = Table()
            m.add(Ui.label("Ход: ${teamName(st, st.active)}", hex(t.light), Ui.fontBig)).row()
            m.add(Ui.small("Передайте устройство игроку.")).padTop(8f).row()
            m.add(Ui.button("Начать ход", primary = true) { APP.closeModal(); cont.resumeWith(Result.success(Unit)) }).padTop(14f)
            APP.openModal(m, closable = false)
        }
    }

    private fun startHumanTurn() {
        history.clear()
        mode = "idle"
        lastHuman = st.active
        saveCurrent()
        updateHud()
        commanderOf(st, st.active)?.let { h -> scope.launch { ensureVisible(h.x, h.y) } }
    }

    private suspend fun endTurnFlow() {
        deselect()
        mode = "busy"
        logAction(Action(kind = "end"))
        val events = endTurn(st)
        afterTurnStart(events)
    }

    /* ---------- Кто ходит ---------- */
    private fun isLocal(team: String) = net == null || team in net.localTeams
    fun isHumanTurn() = ::st.isInitialized && replay == null && !st.teams.getValue(st.active).ai && isLocal(st.active)
    private fun humanTeams() = st.order.filter { !st.teams.getValue(it).ai && isLocal(it) }

    // Чью казну и отряд показывать: активного игрока, а в чужой ход - последнего ходившего человека
    private fun viewTeam(): String {
        net?.let { return it.localTeams.first() }
        if (!st.teams.getValue(st.active).ai) return st.active
        return lastHuman?.takeIf { st.teams.containsKey(it) } ?: humanTeams().firstOrNull() ?: st.order[0]
    }

    // Юнит на клетке, если игрок его видит (туман войны прячет чужих вне обзора)
    private fun seenUnitAt(x: Int, y: Int): empires.logic.Unit? {
        val u = unitAt(st, x, y) ?: return null
        if (!st.fog || u.team == viewTeam()) return u
        return if (visibleTiles(st, viewTeam()).contains(key(x, y))) u else null
    }
    private fun seen(list: List<empires.logic.Unit>) = if (st.fog) list.filter { seenUnitAt(it.x, it.y) != null } else list

    /* ---------- Журнал действий, повтор, сеть ---------- */
    // a - действие в формате aiApply; s - сид перед действием (ИИ тратит случайность на выбор хода)
    private fun logAction(a: Action) {
        if (replay != null) return
        val rec = a.copy().also { it.s = st.seed }
        st.log?.add(rec)
        if (net != null && isLocal(st.active)) net.sendAction(rec)
    }

    private fun replayNext(): Action? {
        val r = replay ?: return null
        val a = r.log.getOrNull(r.i++) ?: return null
        a.s?.let { st.seed = it }
        return a
    }

    private fun showReplayEnd(res: String?) {
        mode = "over"
        val who = when {
            res == "win" && mission.skirmish && st.winner != null -> "Победа: ${teamName(st, st.winner!!)}"
            res == "win" -> "Победа"
            res != null -> "Поражение"
            else -> "Запись окончена"
        }
        val m = Table()
        m.add(Ui.label("Повтор окончен", C.gold, Ui.fontBig)).left().row()
        m.add(Ui.label(who)).left().padTop(8f).row()
        m.add(Ui.small("Ходов: ${st.turn}")).left().padTop(4f).row()
        val row = Table()
        row.add(Ui.button("Ещё раз", primary = true) { APP.setScreen(GameScreen(missionId, replayRec = replayRec)) }).padRight(8f)
        row.add(Ui.button("В меню") { APP.setScreen(TitleScreen()) })
        m.add(row).right().padTop(14f)
        APP.openModal(m, closable = false)
    }

    /* ---------- Сценарий ---------- */
    private suspend fun runTriggers(ctx: TriggerCtx) {
        mission.triggers.forEachIndexed { i, t ->
            if (i in fired) return@forEachIndexed
            if (!t.`when`(st, ctx)) return@forEachIndexed
            fired.add(i)
            val prevMode = mode
            if (t.say.isNotEmpty()) showDialog(t.say)
            for (s in t.spawn) {
                val u = spawnNear(st, s.type, s.team, s.x, s.y)
                if (u != null) { ensureVisible(u.x, u.y); animEvents(listOf(Event(type = "buy", unit = u.id, x = u.x, y = u.y))) }
            }
            mode = prevMode
        }
    }

    private suspend fun checkOutcome(): Boolean {
        val res = evaluateOutcome(st, mission) ?: return false
        mode = "over"
        st.over = res
        deselect()
        if (replay != null) { showReplayEnd(res); return true }
        // в сети итог считается для своей стороны
        val myRes = if (net != null && mission.skirmish) (if (st.winner != null && st.winner in net.localTeams) "win" else "lose") else res
        if (net == null) Save.clearCurrent()
        Save.addReplay(mission, st, res)
        if (myRes == "win") {
            Sfx.play("win")
            if (!mission.skirmish) {
                showDialog(mission.outro)
                Save.missionWon(mission.id, st)
            }
        } else Sfx.play("lose")
        grantAchievements("end", emptyList(), myRes)
        showResult(myRes)
        return true
    }

    // Общая обработка после действия: анимация, триггеры, проверка исхода
    private suspend fun afterAction(events: List<Event>): Boolean {
        trackEvents(st, events)
        grantAchievements("events", events, null)
        updateHud()
        animEvents(events)
        runTriggers(TriggerCtx("events", events))
        updateHud()
        return checkOutcome()
    }

    /* ---------- ИИ, повтор и соперник по сети: один цикл ---------- */
    private suspend fun nextAction(): Action? = when {
        replay != null -> replayNext()
        net != null && !isLocal(st.active) && !st.teams.getValue(st.active).ai -> net.receive()?.also { a -> a.s?.let { st.seed = it } }
        else -> aiNextAction(st, mission)
    }

    private suspend fun runDriver() {
        if (aiRunning) return
        aiRunning = true
        mode = "busy"
        updateHud()
        val mySession = session
        fun alive() = session == mySession && mode != "over"
        try {
            while (true) {
                if (!alive()) return
                anim.sleep(30f)
                val a = nextAction()
                if (!alive()) return
                if (a == null) {
                    aiRunning = false
                    if (net != null) { netLost(); return }
                    showReplayEnd(null); return
                }
                if (a.kind == "end") { aiRunning = false; endTurnFlow(); return }
                logAction(a)
                if (a.kind == "buy") {
                    ensureVisible(a.x!!, a.y!!)
                    val ev = actBuy(st, st.active, a.type!!, a.x!!, a.y!!)
                    if (afterAction(ev)) return
                    continue
                }
                val u = unitById(st, a.unit!!) ?: return
                val to = a.to!!
                val shown = vis == null || isVisible(u.x, u.y) || isVisible(to.x, to.y)
                if (shown) ensureVisible(u.x, u.y)
                if (!alive()) return
                val r = reachable(st, u)
                val path = pathTo(r, to.x, to.y)
                selected = u.id
                actMove(st, u, to.x, to.y)
                if (path.size > 1 && shown) { Sfx.play("move"); animMove(u, path) }
                if (!alive()) return
                if (a.act == "attack") {
                    unitById(st, a.target!!)?.let { t -> hl.targets = listOf(t); anim.sleep(200f); hl.targets = null }
                }
                val ev = aiApply(st, a)
                selected = null
                if (afterAction(ev)) return
            }
        } finally {
            if (session == mySession) aiRunning = false
        }
    }

    private fun netLost() {
        mode = "over"
        val m = Table()
        m.add(Ui.label("Связь потеряна", C.bad, Ui.fontBig)).left().row()
        m.add(Ui.small("Соперник отключился. Партию можно посмотреть в повторах, если она была закончена.")).width(300f).left().padTop(8f).row()
        m.add(Ui.button("В меню", primary = true) { APP.setScreen(TitleScreen()) }).right().padTop(14f)
        APP.openModal(m, closable = false)
    }

    /* ---------- Выбор и действия игрока ---------- */
    private fun deselect() {
        sel = null; orig = null; reach = null
        clearHighlights()
        hidePopup()
        if (mode != "over" && mode != "busy") mode = "idle"
    }

    private fun selectUnit(u: empires.logic.Unit) {
        deselect()
        Sfx.play("select")
        sel = u.id
        orig = Pos(u.x, u.y)
        val r = reachable(st, u)
        reach = r
        selected = u.id
        hl.reach = r.keys
        val threat = LinkedHashSet(threatTiles(st, u, r))
        threat.removeAll(r.keys)
        hl.threat = threat.toList()
        val inRange = seen(st.units.filter { isEnemy(it, u) && attackSpotFor(u, it) != null })
        hl.targets = inRange.ifEmpty { null }
        mode = "selected"
        showInfo(u.x, u.y)
    }

    private class Spot(val x: Int, val y: Int, val score: Double)

    // Лучшая клетка, откуда выбранный юнит может ударить цель в этом ходу:
    // меньше ответного урона, затем больше защиты местности, затем короче путь.
    private fun attackSpotFor(u: empires.logic.Unit, t: empires.logic.Unit): Spot? {
        val d = UNIT_DEFS.getValue(u.type)
        if (d.noAttack) return null
        val o = orig ?: return null
        var best: Spot? = null
        for (n in (reach ?: return null).values) {
            if (d.noMoveAttack && (n.x != o.x || n.y != o.y)) continue
            val r = dist(n.x, n.y, t.x, t.y)
            if (r < d.rangeMin || r > d.rangeMax) continue
            val f = forecast(st, u, t, n.x, n.y)
            val score = -f.counter * 10.0 + unitDef(st, u, n.x, n.y) - n.cost * 0.5
            if (best == null || score > best.score) best = Spot(n.x, n.y, score)
        }
        return best
    }

    private fun confirmAttack(u: empires.logic.Unit, t: empires.logic.Unit, spot: Spot) {
        mode = "confirm"
        pending = Pending(t.id, spot.x, spot.y)
        hl.reach = null; hl.threat = null
        hl.targets = listOf(t)
        hl.path = pathTo(reach!!, spot.x, spot.y)
        val f = forecast(st, u, t, spot.x, spot.y)
        val left = max(0, t.hp - f.dmg)
        val m = Table().apply { background = Ui.panel; pad(10f) }
        m.add(Ui.label(UNIT_DEFS.getValue(t.type).name)).left().colspan(2).row()
        m.add(Ui.small("Урон: ~${f.dmg} (${t.hp} → $left)", C.text)).left().colspan(2).row()
        m.add(if (f.kills) Ui.small("Добьёт", C.good) else Ui.small("Ответ: ~${f.counter}", if (f.counter > 0) C.bad else C.text)).left().colspan(2).row()
        m.add(Ui.button("Атаковать", primary = true) { scope.launch { executePendingAttack() } }).padTop(8f).padRight(6f)
        m.add(Ui.button("Отмена") { cancelConfirm() }).padTop(8f)
        showPopup(m, null)
    }

    private fun cancelConfirm() {
        pending = null
        hidePopup()
        val u = selUnit
        if (u != null) selectUnit(u) else deselect()
    }

    private suspend fun executePendingAttack() {
        val u = selUnit; val p = pending
        val t = p?.let { unitById(st, it.target) }
        pending = null
        hidePopup()
        if (u == null || t == null || p == null) { deselect(); return }
        val path = pathTo(reach!!, p.x, p.y)
        mode = "busy"
        hl.path = null
        actMove(st, u, p.x, p.y)
        if (path.size > 1) { Sfx.play("move"); animMove(u, path) }
        doAct(u, Action(act = "attack", target = t.id))
    }

    private suspend fun moveSelected(x: Int, y: Int) {
        val u = selUnit ?: return
        val path = pathTo(reach!!, x, y)
        mode = "busy"
        hl.reach = null; hl.threat = null; hl.path = null
        actMove(st, u, x, y)
        if (path.size > 1) { Sfx.play("move"); animMove(u, path) }
        openActionMenu(u)
    }

    private fun undoMove() {
        val u = selUnit
        val o = orig
        if (u != null && o != null) { u.x = o.x; u.y = o.y; u.moved = false }
        hidePopup()
        if (u != null) selectUnit(u) else deselect()
    }

    private fun unitTargets(u: empires.logic.Unit): List<empires.logic.Unit> {
        val d = UNIT_DEFS.getValue(u.type)
        val o = orig ?: Pos(u.x, u.y)
        if (d.noAttack || (d.noMoveAttack && (o.x != u.x || o.y != u.y))) return emptyList()
        return seen(st.units.filter { isEnemy(it, u) && dist(u.x, u.y, it.x, it.y) in d.rangeMin..d.rangeMax })
    }

    private fun openActionMenu(u: empires.logic.Unit) {
        mode = "action"
        hl.targets = null; hl.graves = null
        val opts = ArrayList<Pair<String, () -> Unit>>()
        val targets = unitTargets(u)
        if (targets.isNotEmpty()) opts.add("Атаковать" to { startTargeting(targets) })
        if (canCaptureAt(st, u)) opts.add("Захватить" to { scope.launch { doAct(u, Action(act = "capture")) }; Unit })
        if (canRepairAt(st, u)) opts.add("Отстроить" to { scope.launch { doAct(u, Action(act = "repair")) }; Unit })
        val o = orig
        val ruinable = if (o != null && o.x == u.x && o.y == u.y) destroyTargets(st, u) else emptyList()
        if (ruinable.isNotEmpty()) opts.add("Разрушить" to { startPicking("destroying", ruinable, "Выберите деревню.") })
        val graves = if (UNIT_DEFS.getValue(u.type).canRaise) gravesNear(st, u.x, u.y) else emptyList()
        if (graves.isNotEmpty() && teamUnits(st, u.team).size < BAL.unitCap) opts.add("Поднять мертвеца" to { startPicking("raising", graves.map { Pos(it.x, it.y) }, "Выберите могилу.") })
        opts.add("Ждать" to { scope.launch { doAct(u, Action(act = "wait")) }; Unit })
        opts.add("Отмена" to { undoMove() })
        val m = Table().apply { background = Ui.panel; pad(6f) }
        for ((label, fn) in opts) m.add(Ui.button(label, primary = label != "Отмена") { fn() }).growX().minWidth(150f).pad(3f).row()
        showPopup(m, Pos(u.x, u.y))
        // подсветить цели, чтобы их было видно сразу
        if (targets.isNotEmpty()) hl.threat = targets.map { key(it.x, it.y) }
    }

    private fun startTargeting(targets: List<empires.logic.Unit>) {
        hidePopup()
        mode = "targeting"
        hl.threat = null
        hl.targets = targets
        setHint("Выберите цель. «Назад» - отмена.")
    }

    private fun startPicking(m: String, spots: List<Pos>, text: String) {
        hidePopup()
        mode = m
        hl.graves = spots
        setHint("$text «Назад» - отмена.")
    }

    // Запомнить состояние перед действием. Юнит u (уже сдвинутый) возвращается на исходную клетку.
    // Сид случайностей тоже в состоянии: после отмены тот же удар даст тот же результат.
    private fun pushUndo(u: empires.logic.Unit?) {
        if (net != null) return
        val snap = st.deepCopy()
        val su = u?.let { unitById(snap, it.id) }
        val o = orig
        if (su != null && o != null) { su.x = o.x; su.y = o.y; su.moved = false }
        history.add(stateToJson(snap) to fired.toList())
        if (history.size > 30) history.removeAt(0)
    }

    private fun undoAction() {
        if (history.isEmpty() || !isHumanTurn() || mode !in listOf("idle", "selected")) return
        val (json, f) = history.removeAt(history.size - 1)
        deselect()
        st = stateFromJson(json)
        fired = ArrayList(f)
        refreshTerrain()
        clearHighlights()
        offsets.clear()
        floaters.clear()
        saveCurrent()
        updateHud()
    }

    // spec - действие в формате aiApply (act, target...), юнит уже стоит на клетке
    private suspend fun doAct(u: empires.logic.Unit, spec: Action) {
        pushUndo(u)
        hidePopup()
        setHint("")
        mode = "busy"
        hl.targets = null; hl.graves = null; hl.threat = null
        val a = spec.copy().also { it.unit = u.id; it.to = Pos(u.x, u.y) }
        logAction(a)
        val ev = aiApply(st, a)
        sel = null; orig = null; reach = null; selected = null
        if (afterAction(ev)) return
        mode = "idle"
        saveCurrent()
        updateHud()
        // сведения о клетке после удара: здоровье изменилось
        hover?.let { showInfo(it.x, it.y) }
    }

    private fun saveCurrent() {
        if (!::st.isInitialized || st.over != null || replay != null || net != null) return
        Save.saveCurrent(mission.id, st, fired)
    }

    /* ---------- Ввод ---------- */
    private fun onTileClick(x: Int, y: Int) {
        if (!isHumanTurn() || APP.modalOpen) return
        val u = seenUnitAt(x, y)
        val sl = selUnit
        when (mode) {
            "idle", "selected" -> {
                if (mode == "selected" && sl != null && reach!!.containsKey(key(x, y))) { scope.launch { moveSelected(x, y) }; return }
                if (mode == "selected" && sl != null && u != null && isEnemy(u, sl)) {
                    val spot = attackSpotFor(sl, u)
                    if (spot != null) { confirmAttack(sl, u, spot); return }
                }
                if (u != null && u.team == st.active && !u.acted) { selectUnit(u); return }
                deselect()
                if (u != null) {
                    // показать, куда может ударить чужой или походивший юнит
                    hl.enemy = threatTiles(st, u, reachable(st, u.copy())).toList()
                    selected = u.id
                    showInfo(x, y)
                    return
                }
                if (terrainAt(st, x, y) == "castle" && ownerAt(st, x, y) == st.active) { openBuy(x, y); return }
                showInfo(x, y)
            }
            "action" -> {
                val t = u?.let { uu -> unitTargets(sl!!).find { it.id == uu.id } }
                if (t != null) { scope.launch { doAct(sl!!, Action(act = "attack", target = t.id)) }; return }
                undoMove()
            }
            "targeting" -> {
                val t = u?.let { uu -> hl.targets?.find { it.id == uu.id } }
                if (t != null) scope.launch { doAct(sl!!, Action(act = "attack", target = t.id)) }
                else { setHint(""); hideForecast(); openActionMenu(sl!!) }
            }
            "confirm" -> {
                // повторное нажатие на ту же цель подтверждает атаку, на другую цель - переключает
                if (u != null && pending != null && u.id == pending!!.target) { scope.launch { executePendingAttack() }; return }
                val spot = if (u != null && isEnemy(u, sl!!)) attackSpotFor(sl, u) else null
                if (spot != null) { confirmAttack(sl!!, u!!, spot); return }
                cancelConfirm()
                if (reach?.containsKey(key(x, y)) == true) scope.launch { moveSelected(x, y) }
            }
            "raising" -> {
                val gr = hl.graves?.find { it.x == x && it.y == y }
                if (gr != null) scope.launch { doAct(sl!!, Action(act = "raise", grave = Pos(gr.x, gr.y))) }
                else { setHint(""); openActionMenu(sl!!) }
            }
            "destroying" -> {
                val v = hl.graves?.find { it.x == x && it.y == y }
                if (v != null) scope.launch { doAct(sl!!, Action(act = "destroy", spot = Pos(v.x, v.y))) }
                else { setHint(""); openActionMenu(sl!!) }
            }
        }
    }

    // На телефоне «наведение» - это касание: путь, прогноз и сведения о клетке
    private fun onTileHover(t: Pos?) {
        hover = t
        if (t == null) { hideForecast(); return }
        if (mode == "selected" && reach != null) {
            val enemy = seenUnitAt(t.x, t.y)
            val spot = if (enemy != null && isEnemy(enemy, selUnit!!)) attackSpotFor(selUnit!!, enemy) else null
            hl.path = if (reach!!.containsKey(key(t.x, t.y))) pathTo(reach!!, t.x, t.y) else spot?.let { pathTo(reach!!, it.x, it.y) }
        }
        if (mode == "targeting") {
            val target = hl.targets?.find { it.x == t.x && it.y == t.y }
            if (target != null) showForecast(selUnit!!, target) else hideForecast()
        }
        if (mode == "idle" || mode == "selected") showInfo(t.x, t.y)
    }

    private fun onCancel() {
        when (mode) {
            "targeting", "raising", "destroying" -> { setHint(""); hideForecast(); selUnit?.let { openActionMenu(it) } }
            "action" -> undoMove()
            "confirm" -> cancelConfirm()
            "selected" -> deselect()
            // смотрели зону чужого воина: сначала убрать подсветку, меню - следующим нажатием
            else -> if (hl.enemy != null || selected != null) deselect() else openGameMenu()
        }
    }

    private fun endTurnClicked() {
        if (!isHumanTurn() || mode !in listOf("idle", "selected")) return
        scope.launch { endTurnFlow() }
    }

    /* ---------- HUD, сведения, всплывающие окна ---------- */
    private fun updateHud() {
        if (!::st.isInitialized) return
        val team = st.active; val me = viewTeam()
        fogTeam = if (st.fog && replay == null) me else null
        val turnTxt = mission.turnLimit?.let { "Ход ${st.turn}/$it" } ?: "Ход ${st.turn}"
        hudTurn.setText(turnTxt)
        hudTurn.color = hex(TEAMS.getValue(team).light)
        val mine = st.teams.getValue(me)
        hudGold.setText("${mine.gold} [#7dde84]+${incomeOf(st, me)}[]  [#a99db6]${teamUnits(st, me).size}/${BAL.unitCap}[]")
        val human = isHumanTurn() && mode != "over"
        btnEnd.isDisabled = !human
        btnUndo.isDisabled = !human || history.isEmpty()
        btnUndo.isVisible = net == null && replay == null
        if (replay != null) btnEnd.setText("Повтор")
    }

    private fun showInfo(x: Int, y: Int) {
        info.clearChildren()
        val terr = TERRAIN.getValue(terrainAt(st, x, y))
        val owner = ownerAt(st, x, y)
        val parts = ArrayList<String>()
        parts.add("защита +${terr.def}")
        if (terr.building) parts.add("${owner?.let { teamName(st, it) } ?: "ничья"}, +${terr.income} золота")
        if (terr.cost.isFinite() && terr.cost > 1) parts.add("ход ${terr.cost.toInt()}")
        if (!terr.cost.isFinite()) parts.add(if (terr.water) "летающим и водным" else "только для летающих")
        else if (terr.water) parts.add("водным: ход 1, защита +${BAL.waterDef}")
        val head = Table()
        head.add(Ui.label(terr.name, C.gold, Ui.fontSmall)).padRight(6f)
        head.add(Ui.small(parts.joinToString(" · "), C.muted, wrap = true)).growX()
        info.add(head).growX().left().row()
        val u = seenUnitAt(x, y) ?: return
        val d = UNIT_DEFS.getValue(u.type)
        val (lo, hi) = unitAtk(u, st)
        val baseDef = d.def + u.level * BAL.levelDef - (if (u.poison > 0) BAL.poisonDef else 0)
        val terrDef = terrainDefFor(st, u, x, y)
        val row = Table()
        row.add(Image(TextureRegionDrawable(APP.assets.unitTexture(u.type, u.team)))).size(48f).top().padRight(8f)
        val col = Table()
        col.add(Ui.label(d.name + if (u.level > 0) "  [#ffd84a]★${u.level}[]" else "", hex(TEAMS.getValue(u.team).light), Ui.fontSmall)).left().row()
        val stats = buildString {
            append("♥ ${u.hp}")
            if (!d.noAttack) append("   Атк $lo-$hi")
            append("   Защ $baseDef${if (terrDef != 0) "+$terrDef" else ""}")
            append("   Ход ${moveOf(u)}")
            if (!d.noAttack) append("   Дальн ${if (d.rangeMin == d.rangeMax) "${d.rangeMin}" else "${d.rangeMin}-${d.rangeMax}"}")
        }
        col.add(Ui.small(stats, C.text, wrap = true)).growX().left().row()
        if (u.slow) col.add(Ui.small("Скован: −${BAL.slowMove} к ходу до конца своего хода.", C.bad)).growX().left().row()
        if (u.poison > 0) col.add(Ui.small("Отравлен: −${BAL.poisonAtk} атаки и защиты, ещё ${u.poison} х.", C.bad)).growX().left().row()
        col.add(Ui.small(d.desc)).growX().left()
        row.add(col).growX()
        info.add(row).growX().left().padTop(4f)
    }

    private var popupTile: Pos? = null
    private fun showPopup(m: Table, tile: Pos?) {
        hidePopup()
        popup = m
        popupTile = tile
        m.pack()
        overlay.addActor(m)
        layoutOverlays()
    }

    private fun hidePopup() { popup?.remove(); popup = null; hideForecast() }

    private fun showForecast(att: empires.logic.Unit, def: empires.logic.Unit) {
        hideForecast()
        val f = forecast(st, att, def, att.x, att.y)
        val left = max(0, def.hp - f.dmg)
        val box = Table().apply { background = Ui.panel; pad(8f) }
        box.add(Ui.small("Урон: ~${f.dmg} (${def.hp} → $left)", C.text)).left().row()
        box.add(if (f.kills) Ui.small("Добьёт", C.good) else Ui.small("Ответ: ~${f.counter}", if (f.counter > 0) C.bad else C.text)).left()
        box.pack()
        box.touchable = Touchable.disabled
        forecastBox = box
        overlay.addActor(box)
    }
    private fun hideForecast() { forecastBox?.remove(); forecastBox = null }

    private fun setHint(text: String) {
        hint.setText(text)
        hint.isVisible = text.isNotEmpty()
    }

    // Окна рядом с клеткой; без клетки - внизу карты (не закрывают ни цель, ни путь)
    private fun layoutOverlays() {
        val w = overlay.width; val h = overlay.height
        popup?.let { p ->
            val t = popupTile
            if (t == null) p.setPosition((w - p.width) / 2, 8f)
            else {
                var left = tileLeft(t.x) + ts + 8f
                if (left + p.width > w - 8f) left = tileLeft(t.x) - p.width - 8f
                val top = (tileBottom(t.y) + ts).coerceIn(p.height + 4f, h - 4f)
                p.setPosition(left.coerceAtLeast(8f), top - p.height)
            }
        }
        forecastBox?.let { b ->
            val t = hover ?: return@let
            var left = tileLeft(t.x) + ts + 8f
            if (left + b.width > w - 8f) left = tileLeft(t.x) - b.width - 8f
            b.setPosition(left.coerceAtLeast(8f), (tileBottom(t.y) + ts - b.height).coerceIn(4f, h - b.height - 4f))
        }
        if (hint.isVisible) {
            if (hint.parent == null) overlay.addActor(hint)
            hint.pack()
            hint.setPosition((w - hint.width) / 2, h - hint.height - 8f)
        } else hint.remove()
        banner?.let { it.width = w; it.pack(); it.width = w; it.setPosition(0f, h * 0.55f) }
        dialogBox?.let { it.width = w - 16f; it.layout(); it.height = it.prefHeight; it.setPosition(8f, 8f) }
    }

    /* ---------- Баннер хода ---------- */
    private suspend fun showBanner(title: String? = null, sub: String? = null) {
        val t = TEAMS.getValue(st.active)
        val b = Table().apply { background = Ui.solid(Color(0.1f, 0.06f, 0.13f, 0.85f)); pad(12f) }
        b.touchable = Touchable.disabled
        if (title != null) {
            b.add(Ui.label(title, C.gold, Ui.fontBig).centered()).growX().row()
            b.add(Ui.small(sub ?: "", C.text).centered()).growX().padTop(4f)
        } else {
            b.add(Ui.label(teamName(st, st.active), hex(t.light), Ui.fontBig).centered()).growX().row()
            b.add(Ui.small("Ход ${st.turn}", C.text).centered()).growX()
        }
        banner?.remove()
        banner = b
        overlay.addActor(b)
        b.fadeIn()
        layoutOverlays()
        Sfx.play("turn")
        anim.sleep(if (title != null) 1800f else 1000f)
        b.remove()
        if (banner == b) banner = null
    }

    /* ---------- Диалоги ---------- */
    private suspend fun showDialog(lines: List<Line>) {
        if (lines.isEmpty() || replay != null) return
        val prevMode = mode
        mode = "dialog"
        for ((i, l) in lines.withIndex()) {
            val sp = SPEAKERS.getValue(l.who)
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                val box = Table().apply { background = Ui.panelAccent; pad(10f) }
                box.add(Image(TextureRegionDrawable(APP.assets.portrait(sp.sprite)))).size(64f).top().padRight(10f)
                val col = Table()
                col.add(Ui.label(sp.name, hex(TEAMS.getValue(sp.team).light), Ui.fontSmall)).left().row()
                col.add(Ui.label(l.text, C.text, Ui.font, wrap = true)).growX().left().padTop(2f).row()
                col.add(Ui.small(if (i < lines.size - 1) "Далее ▶" else "Закрыть ▶", C.muted, wrap = false)).right().padTop(4f)
                box.add(col).grow()
                box.touchable = Touchable.enabled
                box.onClick {
                    Sfx.play("click")
                    box.remove()
                    dialogBox = null
                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                }
                dialogBox = box
                overlay.addActor(box)
                layoutOverlays()
            }
        }
        mode = prevMode
    }

    /* ---------- Наём ---------- */
    private fun openBuy(x: Int, y: Int) {
        val team = st.active
        val list = buyOptions(st, team)
        val m = Table()
        m.add(Ui.label("Замок: наём", C.gold, Ui.fontBig)).left()
        m.add(Ui.label("${st.teams.getValue(team).gold}", C.gold)).right().row()
        if (teamUnits(st, team).size >= BAL.unitCap) m.add(Ui.small("Достигнут предел отряда (${BAL.unitCap}).", C.gold)).colspan(2).left().row()
        val listBox = Table()
        for (type in list) {
            val d = UNIT_DEFS.getValue(type)
            val cost = buyCost(st, team, type)
            val ok = canBuy(st, team, type, x, y)
            val item = Table().apply { background = if (ok) Ui.panel2 else Ui.btnDisabled; pad(6f) }
            item.add(Image(TextureRegionDrawable(APP.assets.unitTexture(type, team)))).size(44f).padRight(8f)
            val col = Table()
            col.add(Ui.label(if (d.commander) "Вернуть: ${d.name}" else d.name, if (ok) C.text else C.muted, Ui.fontSmall)).left().row()
            val range = if (d.rangeMin == d.rangeMax) "${d.rangeMin}" else "${d.rangeMin}-${d.rangeMax}"
            col.add(Ui.small("Атк ${d.atk[0]}-${d.atk[1]} · Защ ${d.def} · Ход ${d.move} · Дальн $range", C.text)).growX().left().row()
            col.add(Ui.small(d.desc)).growX().left()
            item.add(col).growX()
            item.add(Ui.label("$cost", if (ok) C.gold else C.muted, Ui.fontSmall)).padLeft(6f)
            if (ok) item.onClick {
                Sfx.play("click")
                APP.closeModal()
                scope.launch {
                    mode = "busy"
                    pushUndo(null)
                    logAction(Action(kind = "buy", type = type, x = x, y = y))
                    val ev = actBuy(st, team, type, x, y)
                    if (afterAction(ev)) return@launch
                    mode = "idle"
                    saveCurrent()
                    updateHud()
                }
            }
            listBox.add(item).growX().padBottom(6f).row()
        }
        m.add(listBox).colspan(2).growX().padTop(8f).row()
        m.add(Ui.button("Закрыть") { APP.closeModal() }).colspan(2).right().padTop(8f)
        APP.openModal(m)
    }

    /* ---------- Итоги ---------- */
    private fun showResult(res: String) {
        if (mission.skirmish) return showSkirmishResult(res)
        val win = res == "win"
        val idx = MISSIONS.indexOfFirst { it.id == mission.id }
        val next = MISSIONS.getOrNull(idx + 1)
        val m = Table()
        m.add(Ui.label(if (win) "Победа!" else "Поражение", if (win) C.good else C.bad, Ui.fontBig)).left().row()
        val text = if (win) (if (next != null) "Открыта следующая миссия." else "Кампания пройдена. Спасибо за игру!")
            else if (st.teams.getValue("blue").commanderDead) "Ардан пал. Без командира войско отступает." else "Задача миссии провалена."
        m.add(Ui.label(text, C.text, Ui.font, wrap = true)).growX().left().padTop(8f).row()
        if (win) {
            m.add(Ui.label(starsText(missionStars(mission, st)), C.accent, Ui.fontTitle).centered()).growX().padTop(6f).row()
            m.add(Ui.small(starsHint(mission))).growX().row()
        }
        m.add(Ui.small("Ходов: ${st.turn}, павших: ${teamStats(st, "blue").lost}")).growX().left().padTop(6f).row()
        val row = Table()
        if (win && next != null) row.add(Ui.button("Следующая миссия", primary = true) { APP.setScreen(GameScreen(next.id)) }).padRight(8f)
        if (!win) row.add(Ui.button("Переиграть", primary = true) { APP.setScreen(GameScreen(mission.id)) }).padRight(8f)
        row.add(Ui.button("В меню") { APP.setScreen(TitleScreen()) })
        m.add(row).right().padTop(14f)
        APP.openModal(m, closable = false)
    }

    private fun showSkirmishResult(res: String) {
        val w = st.winner
        val hotseat = st.order.count { !st.teams.getValue(it).ai } > 1 && net == null
        val title = if (res == "win") (if (hotseat && w != null) "Победа: ${teamName(st, w)}" else "Победа!") else "Поражение"
        val m = Table()
        m.add(Ui.label(title, if (hotseat && w != null) hex(TEAMS.getValue(w).light) else if (res == "win") C.good else C.bad, Ui.fontBig)).left().row()
        m.add(Ui.label(if (res == "win") "Все противники разбиты." else "Ваши войска разбиты.")).left().padTop(8f).row()
        m.add(Ui.small("Ходов: ${st.turn}")).left().padTop(4f).row()
        val row = Table()
        if (net == null) row.add(Ui.button("Ещё раз", primary = true) { APP.setScreen(GameScreen(mission.id)) }).padRight(8f)
        row.add(Ui.button("В меню") { APP.setScreen(TitleScreen()) })
        m.add(row).right().padTop(14f)
        APP.openModal(m, closable = false)
    }

    // Проверить и выдать достижения
    private fun grantAchievements(phase: String, events: List<Event>, res: String?) {
        val ids = checkAchievements(AchCtx(
            phase = phase, st = st, mission = mission, events = events, res = res,
            humans = humanTeams(), stars = Save.stars, have = Save.achievements.keys,
        ))
        if (ids.isEmpty()) return
        for (id in ids) Save.achievements[id] = System.currentTimeMillis()
        Save.write()
        for (id in ids) APP.toast("Достижение: " + ACHIEVEMENTS.first { it.id == id }.name)
    }

    private fun openGameMenu() {
        if (!::st.isInitialized) return
        val m = Table()
        m.add(Ui.label(mission.title, C.gold, Ui.fontBig)).left().row()
        m.add(Ui.label(mission.goal, C.text, Ui.fontSmall, wrap = true)).growX().left().padTop(4f).padBottom(10f).row()
        fun add(b: TextButton) = m.add(b).growX().padBottom(6f).row()
        add(Ui.button("Продолжить", primary = true) { APP.closeModal() })
        lateinit var snd: TextButton
        snd = Ui.button(soundLabel()) { Sfx.muted = !Sfx.muted; Save.setPref("muted", Sfx.muted); snd.setText(soundLabel()) }
        add(snd)
        lateinit var mus: TextButton
        mus = Ui.button(musicLabel()) { toggleMusic(); mus.setText(musicLabel()) }
        add(mus)
        add(Ui.button("Как играть") { showRules() })
        if (replay == null && net == null) add(Ui.button("Начать заново") { if (!aiRunning) APP.setScreen(GameScreen(mission.id)) })
        add(Ui.button(when { replay != null -> "Выйти из повтора"; net != null -> "Покинуть партию"; else -> "Выйти в меню" }) {
            saveCurrent()
            APP.setScreen(TitleScreen())
        })
        APP.openModal(m)
    }

    /* =================== Вид: камера и анимации (render.js) =================== */
    private fun setViewState() {
        refreshTerrain()
        clearHighlights()
        offsets.clear()
        floaters.clear()
        zoomFit()
    }

    // Перерисовать местность (после разрушения или восстановления деревни)
    private fun refreshTerrain() {
        layers?.forEach { it.dispose() }
        layers = arrayOf(APP.assets.renderTerrain(st, 0), APP.assets.renderTerrain(st, 1))
    }

    private fun isVisible(x: Int, y: Int) = vis?.contains(key(x, y)) ?: true

    private fun fitTs(): Float {
        if (!::st.isInitialized || mapView.width <= 0) return 48f
        return max(8f, floor(min(mapView.width / st.w, mapView.height / st.h)))
    }

    private fun setTileSize(target: Float) {
        val cx = camX + mapView.width / 2; val cy = camY + mapView.height / 2
        val tx = cx / ts; val ty = cy / ts
        val fit = fitTs()
        ts = max(fit, min(fit * ZOOM_MAX, target))
        camX = tx * ts - mapView.width / 2
        camY = ty * ts - mapView.height / 2
        clampCamera()
    }
    private fun zoomFit() = setTileSize(0f)

    private fun clampCamera() {
        if (!::st.isInitialized) return
        val mw = st.w * ts; val mh = st.h * ts; val cw = mapView.width; val ch = mapView.height
        camX = if (mw <= cw) -(cw - mw) / 2 else camX.coerceIn(0f, mw - cw)
        camY = if (mh <= ch) -(ch - mh) / 2 else camY.coerceIn(0f, mh - ch)
    }

    private fun centerOn(x: Int, y: Int) {
        camX = (x + 0.5f) * ts - mapView.width / 2
        camY = (y + 0.5f) * ts - mapView.height / 2
        clampCamera()
    }

    // Плавно подвести камеру, если клетка у края экрана
    private suspend fun ensureVisible(x: Int, y: Int) {
        if (st.w * ts <= mapView.width && st.h * ts <= mapView.height) return
        val m = ts * 1.5f; val sx = x * ts - camX; val sy = y * ts - camY
        if (sx < m || sy < m || sx > mapView.width - m - ts || sy > mapView.height - m - ts) {
            val fx = camX; val fy = camY
            centerOn(x, y)
            val tx = camX; val ty = camY
            camX = fx; camY = fy
            anim.tween(250f) { k -> camX = fx + (tx - fx) * k; camY = fy + (ty - fy) * k }
        }
    }

    // Клетка в координатах mapView (y вверх): левый и нижний край
    private fun tileLeft(x: Int) = x * ts - camX
    private fun tileBottom(y: Int) = mapView.height - (y + 1) * ts + camY

    private fun screenToTile(px: Float, py: Float): Pos? {
        val x = floor((px + camX) / ts).toInt()
        val y = floor((mapView.height - py + camY) / ts).toInt()
        return if (inBounds(st, x, y)) Pos(x, y) else null
    }

    private fun clearHighlights() { hl = Highlights(); selected = null }

    private fun offsetOf(id: Int) = offsets.getOrPut(id) { Offset() }

    private suspend fun animMove(u: empires.logic.Unit, path: List<Pos>) {
        if (path.size < 2) return
        val o = offsetOf(u.id)
        val end = path.last()
        // юнит уже стоит в конце пути: смещаем его назад по пути
        for (i in 1 until path.size) {
            val a = path[i - 1]; val b = path[i]
            anim.tween(ANIM.moveStep) { k ->
                o.dx = a.x + (b.x - a.x) * k - end.x
                o.dy = a.y + (b.y - a.y) * k - end.y
            }
        }
        o.dx = 0f; o.dy = 0f
    }

    private fun floater(text: String, x: Int, y: Int, color: Color, delay: Float = 0f) {
        floaters.add(Floater(text, x, y, color, anim.clockMs + delay))
    }

    private suspend fun animEvents(events: List<Event>) {
        for (e in events) when (e.type) {
            "hit" -> {
                val att = offsetOf(e.from!!); val def = offsetOf(e.to!!)
                val dx = sign((e.x!! - e.fx!!).toFloat()) * 0.3f; val dy = sign((e.y!! - e.fy!!).toFloat()) * 0.3f
                Sfx.play("swing")
                anim.tween(ANIM.lunge) { k -> val s = sin(k * PI.toFloat()); att.dx = dx * s; att.dy = dy * s }
                Sfx.play("hit")
                floater("-${e.dmg}", e.x!!, e.y!!, hex("#ff5a4a"))
                shake = if ((e.dmg ?: 0) >= 40) 6f else 3f
                anim.tween(ANIM.hit) { k -> def.flash = 1 - k; def.dx = sin(k * 30) * 0.05f * (1 - k) }
                def.dx = 0f
            }
            "death" -> {
                // юнит уже удалён из состояния: рисуем «призрак» до конца анимации
                val ghost = Dying(e.unitType!!, e.team!!, e.x!!, e.y!!)
                dying.add(ghost)
                Sfx.play("death")
                anim.tween(ANIM.death) { k -> ghost.alpha = 1 - k }
                dying.remove(ghost)
            }
            "levelup" -> { Sfx.play("level"); floater("Уровень ${e.level}!", e.x!!, e.y!!, hex("#ffd84a"), 150f); anim.sleep(250f) }
            "capture" -> {
                Sfx.play("capture")
                floater(if (e.terrain == "castle") "Замок захвачен" else "Захват", e.x!!, e.y!!, hex(TEAMS.getValue(e.team!!).light))
                anim.sleep(350f)
            }
            "heal" -> floater("+${e.amount}", e.x!!, e.y!!, hex("#7dff8a"))
            "raise" -> { Sfx.play("raise"); val o = offsetOf(e.unit!!); anim.tween(400f) { k -> o.alpha = k } }
            "poison" -> floater("Яд", e.x!!, e.y!!, hex("#9dff5a"), 300f)
            "slow" -> floater("Скован", e.x!!, e.y!!, hex("#8fd0ff"), 300f)
            "cure" -> floater("Яд снят", e.x!!, e.y!!, hex("#c8ffd0"))
            "destroy" -> {
                val att = offsetOf(e.unit!!)
                val dx = sign((e.x!! - e.fx!!).toFloat()) * 0.3f; val dy = sign((e.y!! - e.fy!!).toFloat()) * 0.3f
                Sfx.play("swing")
                anim.tween(ANIM.lunge) { k -> val s = sin(k * PI.toFloat()); att.dx = dx * s; att.dy = dy * s }
                Sfx.play("destroy")
                shake = 8f
                refreshTerrain()
                floater("Разрушено", e.x!!, e.y!!, hex("#ffb06a"))
                anim.sleep(400f)
            }
            "repair" -> {
                Sfx.play("capture")
                refreshTerrain()
                floater("Отстроено", e.x!!, e.y!!, hex(TEAMS.getValue(e.team!!).light))
                anim.sleep(350f)
            }
            "buy" -> { Sfx.play("buy"); val o = offsetOf(e.unit!!); anim.tween(250f) { k -> o.alpha = k } }
        }
    }

    /* ---------- Карта: рисование и жесты ---------- */
    private inner class MapView : Actor() {
        private val layout = GlyphLayout()
        private val ellipse: Texture by lazy {
            val p = Pixmap(64, 20, Pixmap.Format.RGBA8888)
            p.setColor(Color.WHITE); p.fillCircle(10, 10, 10); p.fillCircle(54, 10, 10); p.fillRectangle(10, 0, 44, 20)
            Texture(p).also { p.dispose(); it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        }
        private var pinchTs = 0f

        init {
            touchable = Touchable.enabled
            addListener(object : ActorGestureListener(12f, 0.4f, 0.6f, 0.15f) {
                override fun tap(event: InputEvent?, x: Float, y: Float, count: Int, button: Int) {
                    if (!::st.isInitialized || mode == "dialog") return
                    val t = screenToTile(x, y) ?: return
                    onTileHover(t)
                    onTileClick(t.x, t.y)
                }
                override fun pan(event: InputEvent?, x: Float, y: Float, deltaX: Float, deltaY: Float) {
                    camX -= deltaX; camY += deltaY; clampCamera()
                }
                override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) { pinchTs = ts }
                override fun zoom(event: InputEvent?, initialDistance: Float, distance: Float) {
                    if (initialDistance > 0) setTileSize(pinchTs * distance / initialDistance)
                }
            })
        }

        override fun sizeChanged() { if (::st.isInitialized) zoomFit() }

        private fun Batch.rect(x: Float, y: Float, w: Float, h: Float, c: Color, a: Float = c.a) {
            setColor(c.r, c.g, c.b, a * parentA)
            draw(Ui.white, x, y, w, h)
        }
        private var parentA = 1f
        private fun Batch.frame(x: Float, y: Float, w: Float, h: Float, c: Color, lw: Float, a: Float = c.a) {
            rect(x, y, w, lw, c, a); rect(x, y + h - lw, w, lw, c, a)
            rect(x, y, lw, h, c, a); rect(x + w - lw, y, lw, h, c, a)
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            if (!::st.isInitialized || layers == null) return
            parentA = parentAlpha
            val now = anim.clockMs
            val z = ts / TILE
            var sx = 0f; var sy = 0f
            if (shake > 0) { sx = ((Math.random() - 0.5) * shake).toFloat(); sy = ((Math.random() - 0.5) * shake).toFloat(); shake *= 0.85f; if (shake < 0.5f) shake = 0f }
            val clip = Rectangle()
            val area = Rectangle(x, y, width, height)
            stage.calculateScissors(area, clip)
            batch.flush()
            if (!ScissorStack.pushScissors(clip)) return
            // экранные координаты клетки (x, y): левый-нижний угол
            val ox = x - camX + sx
            val oyTop = y + height + camY + sy
            fun tx(cx: Float) = ox + cx * ts
            fun ty(cy: Float) = oyTop - (cy + 1) * ts

            val layer = layers!![(floor(now / ANIM.water).toInt()) % 2]
            batch.setColor(1f, 1f, 1f, parentAlpha)
            batch.draw(layer, ox, oyTop - st.h * ts, st.w * ts, st.h * ts)
            vis = if (st.fog && fogTeam != null) visibleTiles(st, fogTeam!!) else null

            // флаги владельцев
            for ((k, team) in st.owner) {
                val (fx, fy) = k.split(",").map { it.toInt() }
                val flx = tx(fx.toFloat()) + 12 * z; val fly = ty(fy.toFloat()) + ts + 2 * z
                batch.rect(flx, fly - 7 * z, z, 7 * z, C.outline)
                batch.rect(flx + z, fly - 4 * z, 4 * z, 4 * z, C.outline)
                batch.rect(flx + z, fly - 3 * z, 3 * z, 3 * z, if (team != null) hex(TEAMS.getValue(team).color) else hex("#b9b9b9"))
            }
            // могилы
            batch.setColor(1f, 1f, 1f, parentAlpha)
            for (gr in st.graves) batch.draw(APP.assets.grave, tx(gr.x.toFloat()), ty(gr.y.toFloat()), ts, ts)

            // туман войны: местность видна, чужие войска - только в обзоре
            vis?.let { v ->
                val fog = Color(14 / 255f, 10 / 255f, 24 / 255f, 0.5f)
                for (yy in 0 until st.h) for (xx in 0 until st.w) if (!v.contains(key(xx, yy))) batch.rect(tx(xx.toFloat()), ty(yy.toFloat()), ts, ts, fog)
            }

            // подсветка
            val pulse = 0.5f + 0.5f * sin(now / 180f)
            fun fill(keys: Collection<String>, c: Color, edge: Color? = null) {
                for (k in keys) {
                    val (kx, ky) = k.split(",").map { it.toInt() }
                    batch.rect(tx(kx.toFloat()), ty(ky.toFloat()), ts, ts, c)
                    if (edge != null) batch.frame(tx(kx.toFloat()), ty(ky.toFloat()), ts, ts, edge, 1f)
                }
            }
            hl.enemy?.let { fill(it, Color(1f, 0.27f, 0.24f, 0.28f)) }
            hl.reach?.let { fill(it, Color(0.35f, 0.67f, 1f, 0.38f), Color(0.75f, 0.88f, 1f, 0.55f)) }
            hl.threat?.let { fill(it, Color(1f, 0.27f, 0.24f, 0.22f)) }
            val lw = max(2f, z * 0.75f)
            hl.targets?.forEach { t -> batch.frame(tx(t.x.toFloat()), ty(t.y.toFloat()), ts, ts, Color(1f, 0.31f, 0.24f, 1f), lw, 0.6f + 0.4f * pulse) }
            hl.graves?.forEach { t -> batch.frame(tx(t.x.toFloat()), ty(t.y.toFloat()), ts, ts, Color(0.63f, 1f, 0.78f, 1f), lw, 0.6f + 0.4f * pulse) }
            hl.path?.let { p -> drawPath(batch, p, ::tx, ::ty, z) }
            // цели миссии (куда довести кристалл и т.п.)
            marks?.forEach { m -> batch.frame(tx(m.x.toFloat()), ty(m.y.toFloat()), ts, ts, hex("#ffd84a"), max(2f, z), 0.5f + 0.5f * pulse) }

            // курсор (последняя нажатая клетка)
            hover?.let { h ->
                val bob = (pulse * z).roundToInt().toFloat()
                batch.setColor(1f, 1f, 1f, 0.9f * parentAlpha)
                batch.draw(cursorRegion(), tx(h.x.toFloat()) - bob, ty(h.y.toFloat()) - bob, ts + 2 * bob, ts + 2 * bob)
            }

            // юниты (сверху вниз, чтобы нижние перекрывали верхние)
            for (u in st.units.sortedBy { it.y }) if (u.team == fogTeam || isVisible(u.x, u.y)) drawUnit(batch, u, now, ::tx, ::ty, z)
            for (d in dying) {
                if (!isVisible(d.x, d.y)) continue
                batch.setColor(1f, 1f, 1f, d.alpha * parentAlpha)
                batch.draw(APP.assets.unitTexture(d.type, d.team, TEAMS.getValue(d.team).flip), tx(d.x.toFloat()) - z, ty(d.y.toFloat()) - z, ts + 2 * z, ts + 2 * z)
            }

            // всплывающие тексты
            floaters.removeAll { now - it.t0 >= ANIM.floater }
            val f = Ui.font
            val base = f.data.scaleX
            f.data.setScale(base * (z * 5.5f / 15f))
            for (fl in floaters) {
                val k = (now - fl.t0) / ANIM.floater
                if (k < 0 || !isVisible(fl.x, fl.y)) continue
                val cx = tx(fl.x.toFloat()) + ts / 2
                val cy = ty(fl.y.toFloat()) + ts - ts * 0.3f + k * ts * 0.6f
                val a = (if (k > 0.7f) (1 - k) / 0.3f else 1f) * parentAlpha
                outlinedText(batch, f, fl.text, cx, cy, fl.color, a, z)
            }
            f.data.setScale(base)
            batch.setColor(Color.WHITE)
            batch.flush()
            ScissorStack.popScissors()
        }

        private var cursor: com.badlogic.gdx.graphics.g2d.TextureRegion? = null
        private fun cursorRegion(): com.badlogic.gdx.graphics.g2d.TextureRegion = cursor ?: run {
            val p = APP.assets.tilePixmap("dungeon", 60)
            com.badlogic.gdx.graphics.g2d.TextureRegion(Texture(p)).also { p.dispose(); cursor = it }
        }

        private fun outlinedText(batch: Batch, f: BitmapFont, text: String, cx: Float, cy: Float, color: Color, a: Float, z: Float) {
            layout.setText(f, text)
            val lx = cx - layout.width / 2
            val o = max(1f, z * 0.6f)
            f.setColor(0.1f, 0.06f, 0.13f, a)
            for ((dx, dy) in listOf(-o to 0f, o to 0f, 0f to -o, 0f to o)) f.draw(batch, text, lx + dx, cy + dy)
            f.setColor(color.r, color.g, color.b, a)
            f.draw(batch, text, lx, cy)
            f.setColor(Color.WHITE)
        }

        private fun drawPath(batch: Batch, path: List<Pos>, tx: (Float) -> Float, ty: (Float) -> Float, z: Float) {
            if (path.size < 2) return
            val w = z * 2
            val c = Color(1f, 1f, 1f, 0.85f)
            for (i in 1 until path.size) {
                val a = path[i - 1]; val b = path[i]
                val ax = tx(a.x.toFloat()) + ts / 2; val ay = ty(a.y.toFloat()) + ts / 2
                val bx = tx(b.x.toFloat()) + ts / 2; val by = ty(b.y.toFloat()) + ts / 2
                batch.rect(min(ax, bx) - w / 2, min(ay, by) - w / 2, abs(bx - ax) + w, abs(by - ay) + w, c)
            }
            val e = path.last()
            val r = z * 3
            batch.setColor(1f, 1f, 1f, parentA)
            batch.draw(ellipse, tx(e.x.toFloat()) + ts / 2 - r, ty(e.y.toFloat()) + ts / 2 - r, r * 2, r * 2)
        }

        private fun drawUnit(batch: Batch, u: empires.logic.Unit, now: Float, tx: (Float) -> Float, ty: (Float) -> Float, z: Float) {
            val o = offsets[u.id] ?: Offset()
            val done = u.acted && u.team == st.active
            val idle = if (!done && u.team == st.active) (sin(now / 260f + u.id) * 0.6f).roundToInt() else 0
            val ux = tx(u.x + o.dx); val uyTop = ty(u.y + o.dy) + ts
            val team = TEAMS.getValue(u.team)
            // подставка цвета стороны: сразу видно, свой или чужой
            val bw = ts * 0.84f; val bh = ts * 0.26f
            val bx = ux + ts / 2 - bw / 2; val by = uyTop - ts + 2.5f * z - bh / 2
            val edge = if (done) Color(0.08f, 0.04f, 0.12f, 0.6f) else hex(team.dark)
            val ew = max(1f, z * 0.8f)
            batch.setColor(edge.r, edge.g, edge.b, edge.a * o.alpha * parentA)
            batch.draw(ellipse, bx - ew, by - ew, bw + 2 * ew, bh + 2 * ew)
            val fillC = if (done) Color(0.16f, 0.12f, 0.2f, 0.45f) else hex(team.color).cpy().also { it.a = 0.6f }
            batch.setColor(fillC.r, fillC.g, fillC.b, fillC.a * o.alpha * parentA)
            batch.draw(ellipse, bx, by, bw, bh)
            val spr = APP.assets.unitTexture(u.type, u.team, team.flip, done)
            val sy = uyTop - ts - idle * z
            batch.setColor(1f, 1f, 1f, o.alpha * parentA)
            batch.draw(spr, ux - z, sy - z, ts + 2 * z, ts + 2 * z)
            if (o.flash > 0) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
                batch.setColor(1f, 1f, 1f, o.flash * 0.8f * parentA)
                batch.draw(spr, ux - z, sy - z, ts + 2 * z, ts + 2 * z)
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            }
            // выбранный юнит
            if (selected == u.id) batch.frame(ux, uyTop - ts, ts, ts, Color.WHITE, 2f)
            val dark = Color(0.1f, 0.06f, 0.13f, 0.85f)
            val f = Ui.fontSmall
            val base = f.data.scaleX
            // HP
            if (u.hp < BAL.maxHp) {
                f.data.setScale(base * (z * 4.5f / 12.5f))
                val txt = u.hp.toString()
                layout.setText(f, txt)
                val rx = ux + ts - z * 0.5f; val by0 = uyTop - ts + z * 0.5f
                batch.rect(rx - layout.width - z, by0, layout.width + z * 1.5f, z * 5f, dark)
                f.color = if (u.hp > 50) Color.WHITE else if (u.hp > 25) hex("#ffd84a") else hex("#ff6a5a")
                f.draw(batch, txt, rx - layout.width, by0 + z * 4.6f)
                f.color = Color.WHITE
                f.data.setScale(base)
            }
            // яд: зелёная капля; сковывание: голубая цепь (значки справа сверху)
            var badgeTop = uyTop
            if (u.poison > 0) {
                val bxx = ux + ts - z * 6
                batch.rect(bxx, badgeTop - z * 6, z * 6, z * 6, dark)
                batch.setColor(hex("#7dff3a"))
                batch.draw(ellipse, bxx + z * 1.3f, badgeTop - z * 5.6f, z * 3.4f, z * 3.4f)
                batch.rect(bxx + z * 2.4f, badgeTop - z * 2.8f, z * 1.2f, z * 2f, hex("#7dff3a"))
                badgeTop -= z * 6
            }
            if (u.slow) {
                val bxx = ux + ts - z * 6
                batch.rect(bxx, badgeTop - z * 6, z * 6, z * 6, dark)
                val c = hex("#8fd0ff"); val w = z * 0.9f
                batch.frame(bxx + z * 0.9f, badgeTop - z * 3.5f, z * 2.6f, z * 2.6f, c, w)
                batch.frame(bxx + z * 2.5f, badgeTop - z * 5.1f, z * 2.6f, z * 2.6f, c, w)
            }
            if (u.level > 0) {
                f.data.setScale(base * (z * 4f / 12.5f))
                batch.rect(ux, uyTop - z * 5, z * 6, z * 5, dark)
                f.color = hex("#ffd84a")
                f.draw(batch, "★${u.level}", ux + z * 0.3f, uyTop - z * 0.5f)
                f.color = Color.WHITE
                f.data.setScale(base)
            }
            batch.setColor(Color.WHITE)
        }
    }
}

fun starsText(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)
fun soundLabel() = "Звук: " + if (Sfx.muted) "выкл" else "вкл"
fun musicLabel() = "Музыка: " + if (Sfx.musicMuted) "выкл" else "вкл"
fun toggleMusic() { Sfx.musicMuted = !Sfx.musicMuted; Save.setPref("musicMuted", Sfx.musicMuted) }
