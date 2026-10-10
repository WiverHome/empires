package empires.game

// Порт renderSkirmishSetup из js/main.js: карта (◀ ▶), стороны, золото, туман. Выбор запоминается.

import com.badlogic.gdx.scenes.scene2d.ui.Table
import empires.logic.CUSTOM_MAPS
import empires.logic.SKIRMISH_GOLD
import empires.logic.SKIRMISH_MAPS
import empires.logic.SkirmishMap
import empires.logic.TEAMS
import empires.logic.skirmishId
import empires.logic.skirmishMapById

class SkirmishSetup {
    private var map = Save.prefs["sk.map"] ?: SKIRMISH_MAPS[0].id
    private var gold = Save.prefs["sk.gold"]?.toIntOrNull() ?: 1000
    private var fog = Save.prefs["sk.fog"] == "1"
    private val sides = LinkedHashMap<String, Char>()

    init {
        Save.prefs["sk.sides"]?.split(",")?.forEach { p -> if (p.length == 3 && p[1] == '=') sides[p.take(1)] = p[2] }
    }

    private fun mapOf(): SkirmishMap = skirmishMapById(map) ?: SKIRMISH_MAPS[0]
    private fun sidesStr() = mapOf().slots.mapIndexed { i, s -> sides[s.team.take(1)] ?: if (i == 0) 'h' else 'a' }.joinToString("")

    fun open() {
        val sm = mapOf()
        val all = SKIRMISH_MAPS + CUSTOM_MAPS
        val mi = all.indexOfFirst { it.id == sm.id }.coerceAtLeast(0)
        val ss = sidesStr()
        val active = ss.count { it != 'n' }
        val ok = active >= 2 && 'h' in ss
        val m = Table()
        m.add(Ui.label("Схватка", C.gold, Ui.fontBig)).left().row()
        // выбор карты
        val pick = Table()
        pick.add(Ui.button("◀") { map = all[(mi - 1 + all.size) % all.size].id; open() }).width(44f).growY()
        val mid = Table().apply { background = Ui.panelAccent; pad(6f) }
        mid.add(Ui.label(sm.title, C.accent, Ui.fontSmall).centered()).growX().row()
        mid.add(Ui.small("${mi + 1} из ${all.size} · ${if (sm.custom) "своя · " else ""}${sm.desc}").centered()).growX()
        pick.add(mid).growX().pad(0f, 6f, 0f, 6f)
        pick.add(Ui.button("▶") { map = all[(mi + 1) % all.size].id; open() }).width(44f).growY()
        m.add(pick).growX().padTop(8f).row()
        // свои карты
        val tools = Table()
        tools.add(Ui.button("Новая карта") { MapEditor(null) { reopen(it) }.open() }).growX().uniformX().pad(2f)
        if (sm.custom) {
            tools.add(Ui.button("Изменить") { MapEditor(sm) { reopen(it) }.open() }).growX().uniformX().pad(2f).row()
            tools.add(Ui.button("Поделиться") { showMapCode(sm) { open() } }).growX().uniformX().pad(2f)
        }
        tools.add(Ui.button("Ввести код") { importMapCode { nm -> if (nm != null) { Save.saveMap(nm); map = nm.id; Save.write() }; open() } }).growX().pad(2f)
        m.add(tools).growX().padTop(4f).row()
        // стороны
        sm.slots.forEachIndexed { i, s ->
            val row = Table()
            row.add(Ui.label(TEAMS.getValue(s.team).label, hex(TEAMS.getValue(s.team).light), Ui.fontSmall)).width(80f).left()
            for ((k, name) in listOf('h' to "Игрок", 'a' to "ИИ", 'n' to "Нет")) {
                row.add(Ui.button(name, primary = ss[i] == k) { sides[s.team.take(1)] = k; open() }).growX().uniformX().pad(2f)
            }
            m.add(row).growX().padTop(4f).row()
        }
        val gr = Table()
        gr.add(Ui.label("Золото", C.text, Ui.fontSmall)).width(80f).left()
        for (g in SKIRMISH_GOLD) gr.add(Ui.button("$g", primary = g == gold) { gold = g; open() }).growX().uniformX().pad(2f)
        m.add(gr).growX().padTop(4f).row()
        val fr = Table()
        fr.add(Ui.label("Туман", C.text, Ui.fontSmall)).width(80f).left()
        fr.add(Ui.button("Нет", primary = !fog) { fog = false; open() }).growX().uniformX().pad(2f)
        fr.add(Ui.button("Есть", primary = fog) { fog = true; open() }).growX().uniformX().pad(2f)
        m.add(fr).growX().padTop(4f).row()
        if (!ok) m.add(Ui.small("Нужно хотя бы две стороны и один игрок.", C.gold)).growX().padTop(6f).row()
        else if (Save.current != null) m.add(Ui.small("Новая партия заменит текущее сохранение.", C.gold)).growX().padTop(6f).row()
        val row = Table()
        row.add(Ui.button("Назад") { APP.closeModal() }).padRight(8f)
        val go = Ui.button("В бой", primary = true) {
            savePrefs()
            Save.clearCurrent()
            APP.setScreen(GameScreen(skirmishId(sm.id, gold, sidesStr(), fog)))
        }
        go.isDisabled = !ok
        row.add(go)
        m.add(row).right().padTop(10f)
        APP.openModal(m)
    }

    private fun reopen(res: MapEditor.Result?) {
        when (res) {
            is MapEditor.Result.Saved -> {
                val isNew = skirmishMapById(res.map.id) == null
                Save.saveMap(res.map)
                map = res.map.id
                if (isNew) grantMapMaker()
            }
            is MapEditor.Result.Deleted -> {
                Save.deleteMap(res.id)
                if (map == res.id) map = SKIRMISH_MAPS[0].id
            }
            null -> {}
        }
        open()
    }

    private fun grantMapMaker() {
        if (Save.achievements.containsKey("map_maker")) return
        Save.achievements["map_maker"] = System.currentTimeMillis()
        Save.write()
        APP.toast("Достижение: Картограф")
    }

    private fun savePrefs() {
        Save.prefs["sk.map"] = map
        Save.prefs["sk.gold"] = gold.toString()
        Save.prefs["sk.fog"] = if (fog) "1" else "0"
        Save.prefs["sk.sides"] = sides.entries.joinToString(",") { "${it.key}=${it.value}" }
        Save.write()
    }
}
