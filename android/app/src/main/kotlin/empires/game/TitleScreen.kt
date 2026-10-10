package empires.game

// Порт экранов js/main.js: титул, кампания по главам, схватка, правила, достижения, повторы, коды карт.

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import empires.logic.*
import kotlin.Unit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TitleScreen : AppScreen() {
    private val box = Table()

    override fun build(): Table {
        val root = Table()
        root.background = Ui.solid(C.bg)
        val col = Table()
        col.add(Ui.label("Empires", C.accent, Ui.fontTitle).centered()).growX().row()
        col.add(Ui.label("Хроники Эльдора", C.muted, Ui.fontSmall).centered()).growX().padBottom(24f).row()
        col.add(box).width(300f).row()
        col.add(Ui.small("Графика и звуки: Kenney; музыка: RandomMind, request (CC0)", C.muted).centered()).width(300f).padTop(20f)
        root.add(col).expand().center()
        renderTitle()
        return root
    }

    override fun show() { Sfx.playMusic("menu") }

    override fun onBack() { Gdx.app.exit() }

    private fun renderTitle() {
        box.clearChildren()
        fun add(b: TextButton) = box.add(b).growX().padBottom(8f).row()
        val cur = Save.current
        if (cur != null && missionById(cur.missionId) != null) {
            add(Ui.button("Продолжить\n[#4a3520]${missionById(cur.missionId)!!.title}, ход ${cur.state.turn}[]", primary = true) {
                APP.setScreen(GameScreen(cur.missionId, resume = cur))
            })
        }
        add(Ui.button(if (Save.completed.isNotEmpty()) "Кампания" else "Новая кампания", primary = cur == null) { renderMissions(null) })
        add(Ui.button("Схватка") { SkirmishSetup().open() })
        add(Ui.button("Игра рядом (2 телефона)") { NetLobby().open() })
        lateinit var db: TextButton
        db = Ui.button(difficultyLabel()) {
            val keys = DIFFICULTY.keys.toList()
            Save.setPref("difficulty", keys[(keys.indexOf(Save.prefs["difficulty"] ?: "normal") + 1) % keys.size])
            db.setText(difficultyLabel())
        }
        add(db)
        add(Ui.button("Как играть") { showRules() })
        val row = Table()
        row.add(Ui.button("Достижения\n[#a99db6]${Save.achievements.size} из ${ACHIEVEMENTS.size}[]") { showAchievements() }).growX().uniformX().padRight(8f)
        row.add(Ui.button("Повторы\n[#a99db6]${if (Save.replays.isEmpty()) "пока нет" else "последних: ${Save.replays.size}"}[]") { showReplays() }).growX().uniformX()
        box.add(row).growX().padBottom(8f).row()
        lateinit var mb: TextButton
        mb = Ui.button(musicLabel()) { toggleMusic(); mb.setText(musicLabel()) }
        add(mb)
    }

    // Сложность действует на новые партии: доход и ошибки ИИ
    private fun difficultyLabel() = "Сложность: ${DIFFICULTY.getValue(Save.prefs["difficulty"] ?: "normal").name}"

    /* ---------- Кампания по главам ---------- */
    private fun renderMissions(chapter: Int?) {
        val last = minOf(Save.unlocked, MISSIONS.size) - 1
        val ch = chapter ?: (last / MISSIONS_PER_CHAPTER)
        val m = Table()
        m.add(Ui.label("Кампания", C.gold, Ui.fontBig)).left().row()
        if (Save.heroLevel > 0) m.add(Ui.small("Уровень Ардана: ★${Save.heroLevel}")).left().row()
        val tabs = Table()
        CHAPTERS.forEachIndexed { c, name ->
            val b = Ui.button("${c + 1}. $name", primary = c == ch) { renderMissions(c) }
            b.isDisabled = c * MISSIONS_PER_CHAPTER >= Save.unlocked
            tabs.add(b).growX().uniformX().pad(2f)
        }
        m.add(tabs).growX().padTop(8f).padBottom(6f).row()
        for (i in ch * MISSIONS_PER_CHAPTER until minOf(MISSIONS.size, (ch + 1) * MISSIONS_PER_CHAPTER)) {
            val mi = MISSIONS[i]
            val open = i < Save.unlocked
            val done = mi.id in Save.completed
            val item = Table().apply { background = if (open) Ui.btnDark else Ui.btnDisabled; pad(8f) }
            item.add(Ui.label("${i + 1}", if (open) C.accent else C.muted, Ui.fontBig)).width(34f)
            val body = Table()
            body.add(Ui.label(mi.title, if (open) C.text else C.muted, Ui.fontSmall)).left().row()
            body.add(Ui.small(if (open) mi.goal else "Закрыто")).growX().left()
            item.add(body).growX()
            if (done) item.add(Ui.label(starsText(Save.stars[mi.id] ?: 1), C.accent, Ui.fontSmall)).padLeft(6f)
            if (open) item.onClick {
                Sfx.play("click")
                Save.clearCurrent()
                APP.setScreen(GameScreen(mi.id))
            }
            m.add(item).growX().padBottom(6f).row()
        }
        if (Save.current != null) m.add(Ui.small("Новая миссия заменит текущее сохранение.", C.gold)).growX().row()
        m.add(Ui.button("Назад") { APP.closeModal() }).right().padTop(6f)
        APP.openModal(m)
    }

    /* ---------- Достижения и повторы ---------- */
    private fun showAchievements() {
        val m = Table()
        m.add(Ui.label("Достижения", C.gold, Ui.fontBig)).left().row()
        for (a in ACHIEVEMENTS) {
            val got = Save.achievements.containsKey(a.id)
            val item = Table().apply { background = if (got) Ui.panelAccent else Ui.panel2; pad(6f, 8f, 6f, 8f) }
            item.add(Ui.label(if (got) "★" else "☆", if (got) C.gold else C.muted, Ui.fontBig)).padRight(8f)
            val col = Table()
            col.add(Ui.label(a.name, if (got) C.text else C.muted, Ui.fontSmall)).left().row()
            col.add(Ui.small(a.desc)).growX().left()
            item.add(col).growX()
            m.add(item).growX().padTop(6f).row()
        }
        m.add(Ui.button("Назад") { APP.closeModal(); renderTitle() }).right().padTop(10f)
        APP.openModal(m, dismiss = { APP.closeModal(); renderTitle() })
    }

    private fun showReplays() {
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))
        val m = Table()
        m.add(Ui.label("Повторы", C.gold, Ui.fontBig)).left().row()
        if (Save.replays.isEmpty()) m.add(Ui.small("Здесь появятся последние законченные партии.")).growX().padTop(8f).row()
        for (r in Save.replays) {
            val item = Table().apply { background = Ui.btnDark; pad(8f) }
            val col = Table()
            col.add(Ui.label(r.title, C.text, Ui.fontSmall)).left().row()
            val res = if (r.res == "win") "[#7dde84]победа[]" else "[#ff7a6a]поражение[]"
            col.add(Ui.small("${fmt.format(Date(r.date))} · $res · ходов: ${r.turn}")).growX().left()
            item.add(col).growX()
            item.add(Ui.label("▶", C.accent)).padLeft(6f)
            item.onClick { Sfx.play("click"); startReplay(r) }
            m.add(item).growX().padTop(6f).row()
        }
        if (Save.current != null) m.add(Ui.small("Сохранённая партия не пострадает.")).growX().padTop(6f).row()
        m.add(Ui.button("Назад") { APP.closeModal() }).right().padTop(10f)
        APP.openModal(m)
    }

    private fun startReplay(r: ReplayRec) {
        // карту могли удалить: она лежит в записи
        if (r.mapCode != null && r.mapId != null && skirmishMapById(r.mapId) == null) {
            (decodeMapCode(r.mapCode, r.mapId) as? SkirmishMap)?.let { CUSTOM_MAPS.add(it) }
        }
        if (missionById(r.missionId) == null) return
        APP.setScreen(GameScreen(r.missionId, replayRec = r))
    }

    companion object {
        val CHAPTERS = listOf("Морвен", "Болота", "Север")
        const val MISSIONS_PER_CHAPTER = 4
    }
}

/* ---------- Правила ---------- */
fun showRules() {
    val m = Table()
    m.add(Ui.label("Как играть", C.gold, Ui.fontBig)).left().row()
    val rules = listOf(
        "Нажмите на своего воина: синим подсвечено, куда он дойдёт, красным - куда сможет ударить. Затем выберите клетку и действие.",
        "Урон зависит от атаки, защиты цели и местности под ней, а также от здоровья атакующего: раненые бьют слабее.",
        "Быстрая атака: выберите воина и нажмите на врага в рамке. Воин сам подойдёт на лучшую клетку, останется подтвердить удар.",
        "Если цель выжила и достаёт до вас, она отвечает ударом. Нажмите на цель, чтобы увидеть прогноз.",
        "Мечники и командиры захватывают деревни, замки захватывает только командир. Каждое здание приносит золото в начале хода и лечит своих на ${BAL.healBuilding}.",
        "В свободном своём замке можно нанимать воинов. Нанятый воин ходит со следующего хода.",
        "За урон воины получают опыт. Уровень даёт +${BAL.levelAtk} к атаке и +${BAL.levelDef} к защите.",
        "Павшие оставляют могилы на ${BAL.graveTurns} хода. Чародей может поднять из могилы призрака.",
        "Паук отравляет: −${BAL.poisonAtk} к атаке и −${BAL.poisonDef} к защите на ${BAL.poisonTurns} хода. Яд сразу снимает целительница рядом или своё здание.",
        "Шаман сковывает цель: в свой следующий ход она пройдёт на ${BAL.slowMove} клетки меньше.",
        "Огонёк даёт соседним союзникам +${BAL.wispAtk} к атаке и особенно силён против нежити.",
        "Волк быстрый и по лесу ходит как по равнине.",
        "Водяной плавает по воде и мелководью и получает там +${BAL.waterDef} к защите.",
        "Катапульта, не двигаясь, может разрушить чужую деревню. Руины отстраивает мечник или командир.",
        "В тумане войны видно только то, что рядом с вашими воинами и зданиями.",
        "Управление: перетаскивание - двигать карту, два пальца - масштаб, «Назад» - отмена. Кнопка ↶ отменяет действия текущего хода (результат удара от этого не изменится).",
    )
    for (r in rules) m.add(Ui.label("• $r", C.text, Ui.fontSmall, wrap = true)).growX().left().padTop(4f).row()
    m.add(Ui.label("Воины", C.gold)).left().padTop(12f).row()
    for (t in BUY_LIST) {
        val d = UNIT_DEFS.getValue(t)
        val row = Table()
        row.add(Image(TextureRegionDrawable(APP.assets.unitTexture(t, "blue")))).size(36f).padRight(6f).top()
        val col = Table()
        val range = if (d.rangeMin == d.rangeMax) "${d.rangeMin}" else "${d.rangeMin}-${d.rangeMax}"
        col.add(Ui.label(d.name, C.text, Ui.fontSmall)).left().row()
        col.add(Ui.small("Цена ${d.cost} · Атк ${d.atk[0]}-${d.atk[1]} · Защ ${d.def} · Ход ${d.move} · Дальн $range", C.text)).growX().left().row()
        col.add(Ui.small(d.desc)).growX().left()
        row.add(col).growX()
        m.add(row).growX().padTop(6f).row()
    }
    m.add(Ui.label("Местность", C.gold)).left().padTop(12f).row()
    for (t in TERRAIN.values) {
        m.add(Ui.small("${t.name}: ход ${if (t.cost.isFinite()) t.cost.toInt().toString() else "-"}, защита +${t.def}", C.text)).growX().left().row()
    }
    m.add(Ui.button("Понятно", primary = true) { APP.closeModal() }).right().padTop(10f)
    APP.openModal(m)
}

/* ---------- Коды карт ---------- */
fun showMapCode(sm: SkirmishMap, back: () -> Unit) {
    val code = encodeMapCode(sm)
    val m = Table()
    m.add(Ui.label(sm.title, C.gold, Ui.fontBig)).left().row()
    m.add(Ui.small("Отправьте этот код. Друг откроет «Схватка», «Ввести код» и вставит его.")).growX().padTop(6f).row()
    val code1 = Ui.label(code, C.text, Ui.fontSmall, wrap = true)
    m.add(Table().apply { background = Ui.panel2; pad(8f); add(code1).growX() }).growX().padTop(8f).row()
    val state = Ui.small("")
    m.add(state).growX().row()
    val row = Table()
    row.add(Ui.button("Назад") { back() }).padRight(6f)
    row.add(Ui.button("Копировать") { Gdx.app.clipboard.contents = code; state.setText("Скопировано.") }).padRight(6f)
    row.add(Ui.button("Отправить", primary = true) { APP.platform.shareText("Код карты", code) })
    m.add(row).right().padTop(10f)
    APP.openModal(m, dismiss = back)
}

// Ввод кода карты: done(map) с новой картой или done(null) при отмене
fun importMapCode(done: (SkirmishMap?) -> Unit) {
    val m = Table()
    m.add(Ui.label("Карта по коду", C.gold, Ui.fontBig)).left().row()
    m.add(Ui.small("Скопируйте код из сообщения и нажмите «Вставить».")).growX().padTop(6f).row()
    val field = Ui.textField("")
    field.messageText = "EMP1:…"
    m.add(field).growX().height(44f).padTop(8f).row()
    val err = Ui.small("", C.gold)
    m.add(err).growX().row()
    val row = Table()
    row.add(Ui.button("Назад") { done(null) }).padRight(6f)
    row.add(Ui.button("Вставить") { field.text = Gdx.app.clipboard.contents ?: "" }).padRight(6f)
    row.add(Ui.button("Добавить", primary = true) {
        when (val res = decodeMapCode(field.text, "c" + System.currentTimeMillis().toString(36))) {
            is SkirmishMap -> done(res)
            else -> err.setText(res.toString())
        }
    })
    m.add(row).right().padTop(10f)
    APP.openModal(m, dismiss = { done(null) })
}
