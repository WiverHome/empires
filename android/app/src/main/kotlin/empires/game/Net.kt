package empires.game

// Сетевая схватка на двух телефонах рядом. Хозяин выбирает карту и присылает id схватки и сид (RInit),
// дальше стороны обмениваются только действиями (тот же формат, что в журнале повтора).
// Правила и ИИ детерминированы, поэтому ходы ИИ на остальных сторонах считаются на обоих телефонах одинаково.

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import empires.logic.Action
import empires.logic.CUSTOM_MAPS
import empires.logic.RInit
import empires.logic.SKIRMISH_GOLD
import empires.logic.SKIRMISH_MAPS
import empires.logic.SkirmishMap
import empires.logic.decodeMapCode
import empires.logic.encodeMapCode
import empires.logic.skirmishId
import empires.logic.skirmishMapById
import kotlinx.coroutines.channels.Channel

private const val PROTOCOL = 1

class NetSession(private val link: NearbyLink, val localTeams: Set<String>) {
    private val inbox = Channel<Action>(Channel.UNLIMITED)
    var onLost: (() -> Unit)? = null
    private var closed = false

    fun sendAction(a: Action) {
        val j = Save.actionTo(a)
        j.addChild("t", JsonValue("act"))
        link.send(j.toJson(JsonWriter.OutputType.json))
    }

    // Следующее действие соперника; null - связь потеряна
    suspend fun receive(): Action? = inbox.receiveCatching().getOrNull()

    fun onMessage(j: JsonValue) {
        if (j.getString("t", "") == "act") inbox.trySend(Save.actionFrom(j))
    }

    fun lost() {
        if (closed) return
        closed = true
        inbox.close()
        onLost?.invoke()
    }

    fun close() {
        if (closed) return
        closed = true
        inbox.close()
        link.stop()
    }
}

// Лобби: создать игру (хозяин, синие) или найти игру рядом (гость, красные)
class NetLobby {
    private val link = APP.platform.nearby
    private val myName = "Игрок " + (1000..9999).random()
    private var session: NetSession? = null
    private val found = LinkedHashMap<String, String>()

    fun open() {
        val m = Table()
        m.add(Ui.label("Игра рядом", C.gold, Ui.fontBig)).left().row()
        m.add(Ui.small("Два телефона рядом, связь по Wi-Fi и Bluetooth, интернет не нужен. Один создаёт игру, другой её находит.")).growX().padTop(6f).row()
        m.add(Ui.button("Создать игру", primary = true) { host() }).growX().padTop(10f).row()
        m.add(Ui.button("Найти игру") { join() }).growX().padTop(6f).row()
        m.add(Ui.button("Назад") { APP.closeModal() }).right().padTop(10f)
        APP.openModal(m)
    }

    private fun waiting(title: String, text: String) {
        val m = Table()
        m.add(Ui.label(title, C.gold, Ui.fontBig)).left().row()
        m.add(Ui.small(text)).growX().padTop(6f).row()
        m.add(Ui.button("Отмена") { cancel() }).right().padTop(10f)
        APP.openModal(m, dismiss = { cancel() })
    }

    private fun cancel() { link.stop(); APP.closeModal() }

    private fun error(text: String) {
        link.stop()
        val m = Table()
        m.add(Ui.label("Игра рядом", C.gold, Ui.fontBig)).left().row()
        m.add(Ui.small(text, C.bad)).growX().padTop(6f).row()
        m.add(Ui.button("Понятно") { APP.closeModal() }).right().padTop(10f)
        APP.openModal(m)
    }

    private fun parse(text: String): JsonValue? = runCatching { JsonReader().parse(text) }.getOrNull()

    /* ---------- Хозяин ---------- */
    private fun host() {
        val addr = localAddress()?.let { "\nАдрес в сети Wi-Fi: $it:$LAN_PORT" } ?: ""
        waiting("Создать игру", "Ждём соперника. На втором телефоне выберите «Игра рядом» → «Найти игру».\nВаше имя: $myName$addr")
        link.host(myName, object : NearbyListener {
            override fun onConnected(peerName: String) {
                link.send(JsonValue(JsonValue.ValueType.`object`).apply {
                    addChild("t", JsonValue("hello")); addChild("v", JsonValue(PROTOCOL.toLong()))
                }.toJson(JsonWriter.OutputType.json))
                pickMap(peerName, Save.prefs["net.map"] ?: SKIRMISH_MAPS[0].id, Save.prefs["net.gold"]?.toIntOrNull() ?: 1000, Save.prefs["net.fog"] == "1")
            }
            override fun onMessage(text: String) { parse(text)?.let { session?.onMessage(it) } }
            override fun onDisconnected() { session?.lost() ?: error("Соперник отключился.") }
            override fun onError(text: String) = error(text)
        })
    }

    private fun pickMap(peer: String, mapId: String, gold: Int, fog: Boolean) {
        val all = SKIRMISH_MAPS + CUSTOM_MAPS
        val sm = skirmishMapById(mapId) ?: SKIRMISH_MAPS[0]
        val mi = all.indexOfFirst { it.id == sm.id }.coerceAtLeast(0)
        val m = Table()
        m.add(Ui.label("Соперник: $peer", C.good, Ui.fontSmall)).left().row()
        m.add(Ui.label("Выберите карту", C.gold, Ui.fontBig)).left().padTop(4f).row()
        val pick = Table()
        pick.add(Ui.button("◀") { pickMap(peer, all[(mi - 1 + all.size) % all.size].id, gold, fog) }).width(44f).growY()
        val mid = Table().apply { background = Ui.panelAccent; pad(6f) }
        mid.add(Ui.label(sm.title, C.accent, Ui.fontSmall).centered()).growX().row()
        mid.add(Ui.small(sm.desc).centered()).growX()
        pick.add(mid).growX().pad(0f, 6f, 0f, 6f)
        pick.add(Ui.button("▶") { pickMap(peer, all[(mi + 1) % all.size].id, gold, fog) }).width(44f).growY()
        m.add(pick).growX().padTop(8f).row()
        val extra = sm.slots.size - 2
        if (extra > 0) m.add(Ui.small("Вы - ${empires.logic.TEAMS.getValue(sm.slots[0].team).label}, соперник - ${empires.logic.TEAMS.getValue(sm.slots[1].team).label}, остальными сторонами играет ИИ.")).growX().padTop(4f).row()
        else m.add(Ui.small("Вы - ${empires.logic.TEAMS.getValue(sm.slots[0].team).label}, соперник - ${empires.logic.TEAMS.getValue(sm.slots[1].team).label}.")).growX().padTop(4f).row()
        val gr = Table()
        for (g in SKIRMISH_GOLD) gr.add(Ui.button("$g", primary = g == gold) { pickMap(peer, sm.id, g, fog) }).growX().uniformX().pad(2f)
        m.add(gr).growX().padTop(6f).row()
        val fr = Table()
        fr.add(Ui.button("Без тумана", primary = !fog) { pickMap(peer, sm.id, gold, false) }).growX().uniformX().pad(2f)
        fr.add(Ui.button("Туман войны", primary = fog) { pickMap(peer, sm.id, gold, true) }).growX().uniformX().pad(2f)
        m.add(fr).growX().padTop(4f).row()
        val row = Table()
        row.add(Ui.button("Отмена") { cancel() }).padRight(8f)
        row.add(Ui.button("Начать", primary = true) {
            Save.prefs["net.map"] = sm.id; Save.prefs["net.gold"] = gold.toString(); Save.prefs["net.fog"] = if (fog) "1" else "0"; Save.write()
            val sides = "hh" + "a".repeat(extra)
            val mission = skirmishId(sm.id, gold, sides, fog)
            val rinit = RInit((Math.random() * 4294967296.0).toLong(), 0, Save.prefs["difficulty"] ?: "normal")
            val j = JsonValue(JsonValue.ValueType.`object`)
            j.addChild("t", JsonValue("start"))
            j.addChild("mission", JsonValue(mission))
            j.addChild("rinit", Save.rinitTo(rinit))
            if (sm.custom) { j.addChild("mapCode", JsonValue(encodeMapCode(sm))); j.addChild("mapId", JsonValue(sm.id)) }
            link.send(j.toJson(JsonWriter.OutputType.json))
            val s = NetSession(link, setOf(sm.slots[0].team))
            session = s
            APP.setScreen(GameScreen(mission, net = s, netInit = rinit))
        })
        m.add(row).right().padTop(10f)
        APP.openModal(m, dismiss = { cancel() })
    }

    /* ---------- Гость ---------- */
    private fun join() {
        found.clear()
        showFound()
        link.discover(myName, object : NearbyListener {
            override fun onFound(endpointId: String, name: String) { found[endpointId] = name; showFound() }
            override fun onLost(endpointId: String) { found.remove(endpointId); showFound() }
            override fun onConnected(peerName: String) {
                waiting("Подключено", "Соперник $peerName выбирает карту…")
            }
            override fun onMessage(text: String) {
                val j = parse(text) ?: return
                when (j.getString("t", "")) {
                    "hello" -> if (j.getInt("v", 0) != PROTOCOL) error("У соперника другая версия игры. Обновите игру на обоих телефонах.")
                    "start" -> start(j)
                    else -> session?.onMessage(j)
                }
            }
            override fun onDisconnected() { session?.lost() ?: error("Соперник отключился.") }
            override fun onError(text: String) = error(text)
        })
    }

    private fun showFound() {
        val m = Table()
        m.add(Ui.label("Найти игру", C.gold, Ui.fontBig)).left().row()
        if (found.isEmpty()) m.add(Ui.small("Ищем игры рядом… На другом телефоне выберите «Создать игру».")).growX().padTop(6f).row()
        for ((id, name) in found) m.add(Ui.button(name, primary = true) { waiting("Подключение", "Подключаемся к $name…"); link.connect(id) }).growX().padTop(6f).row()
        val row = Table()
        row.add(Ui.button("Ввести адрес") { enterAddress() }).padRight(8f)
        row.add(Ui.button("Отмена") { cancel() })
        m.add(row).right().padTop(10f)
        APP.openModal(m, dismiss = { cancel() })
    }

    // Если рассылка в сети не доходит: адрес хозяина виден у него на экране
    private fun enterAddress() {
        val m = Table()
        m.add(Ui.label("Адрес игры", C.gold, Ui.fontBig)).left().row()
        m.add(Ui.small("Адрес показан на телефоне хозяина, например 192.168.1.5:$LAN_PORT.")).growX().padTop(6f).row()
        val field = Ui.textField(Save.prefs["net.addr"] ?: "")
        field.messageText = "192.168.1.5:$LAN_PORT"
        m.add(field).growX().height(44f).padTop(8f).row()
        val row = Table()
        row.add(Ui.button("Назад") { showFound() }).padRight(8f)
        row.add(Ui.button("Подключиться", primary = true) {
            val a = field.text.trim().let { if (':' in it) it else "$it:$LAN_PORT" }
            Save.setPref("net.addr", a)
            waiting("Подключение", "Подключаемся к $a…")
            link.connect("lan:$a")
        })
        m.add(row).right().padTop(10f)
        APP.openModal(m, dismiss = { showFound() })
    }

    private fun localAddress(): String? = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private fun start(j: JsonValue) {
        val mission = j.getString("mission")
        val mapCode = j.getString("mapCode", null)
        val mapId = j.getString("mapId", null)
        if (mapCode != null && mapId != null && skirmishMapById(mapId) == null) {
            (decodeMapCode(mapCode, mapId) as? SkirmishMap)?.let { CUSTOM_MAPS.add(it) }
        }
        val sm = skirmishMapById(mission.split(":")[1]) ?: return error("Не удалось открыть карту соперника.")
        val rinit = Save.rinitFrom(j.get("rinit"))
        val s = NetSession(link, setOf(sm.slots[1].team))
        session = s
        APP.setScreen(GameScreen(mission, net = s, netInit = rinit))
    }
}
