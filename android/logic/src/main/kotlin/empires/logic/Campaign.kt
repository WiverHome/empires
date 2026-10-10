// CAMPAIGN: миссии, диалоги, триггеры (порт js/campaign.js)
// Карта: строки из символов MAP_LEGEND (Data.kt). Здания нейтральны, если не указаны в buildings.
// triggers: when(st, ctx) -> Boolean, ctx = TriggerCtx(phase "turn" | "events", events)
// check(st) -> "win" | "lose" | null дополняет общие правила (гибель Ардана = поражение).
package empires.logic

class Speaker(val name: String, val sprite: Int, val team: String)
class Line(val who: String, val text: String)
class TeamCfg(
    val gold: Int = 0, val ai: Boolean = false, val buy: List<String>? = null,
    val name: String? = null, val commander: String? = null,
)
class Building(val x: Int, val y: Int, val team: String)
class UnitSpawn(
    val type: String, val team: String, val x: Int, val y: Int,
    val commander: Boolean = false, val hold: Boolean = false, val tag: String? = null, val goal: Pos? = null,
    val level: Int = 0, val hp: Int? = null,
)
class Spawn(val type: String, val team: String, val x: Int, val y: Int)
class TriggerCtx(val phase: String, val events: List<Event> = emptyList())
class Trigger(
    val `when`: (GameState, TriggerCtx) -> Boolean,
    val say: List<Line> = emptyList(),
    val spawn: List<Spawn> = emptyList(),
)
class Mission(
    val id: String, val title: String, val goal: String, val map: List<String>,
    val teams: Map<String, TeamCfg>, val buildings: List<Building> = emptyList(), val units: List<UnitSpawn> = emptyList(),
    val graves: List<Pos> = emptyList(), val marks: List<Pos> = emptyList(),
    val intro: List<Line> = emptyList(), val outro: List<Line> = emptyList(),
    val triggers: List<Trigger> = emptyList(), val check: ((GameState) -> String?)? = null,
    val turnLimit: Int? = null, val par: Int? = null, val maxLost: Int? = null,
    val order: List<String>? = null, val fog: Boolean = false, val skirmish: Boolean = false,
)

val SPEAKERS: Map<String, Speaker> = linkedMapOf(
    "hero" to Speaker("Ардан", 97, "blue"),
    "lira" to Speaker("Лира", 99, "blue"),
    "scout" to Speaker("Разведчик", 98, "blue"),
    "elder" to Speaker("Староста", 86, "blue"),
    "witch" to Speaker("Ильва", 100, "red"),
    "bandit" to Speaker("Разбойник", 85, "red"),
    "bran" to Speaker("Бран", 87, "red"),
    "morven" to Speaker("Морвен", 111, "red"),
    "sif" to Speaker("Сиф", 88, "blue"),
    "haar" to Speaker("Хаар", 96, "red"),
)

fun onTurn(n: Int, team: String = "blue"): (GameState, TriggerCtx) -> Boolean =
    { st, ctx -> ctx.phase == "turn" && st.turn == n && st.active == team }
fun onEvent(pred: (Event, GameState) -> Boolean): (GameState, TriggerCtx) -> Boolean =
    { st, ctx -> ctx.phase == "events" && ctx.events.any { pred(it, st) } }

val MISSIONS: List<Mission> = listOf(
    Mission(
        id = "m1",
        title = "Пограничная застава",
        goal = "Разбейте всех разбойников. Ардан должен выжить.",
        par = 8,
        map = listOf(
            "ff..hh..ff",
            "f..,,,v..f",
            "..,,..,,..",
            ".,..ff..,.",
            ".,..ff..,h",
            ".,......,.",
            "h,.v..f.,.",
            ".,,...ff,.",
            "..,....,,.",
            "f.,,,,,,.f",
            "ff...,...f",
            "f....,..hh",
            "ff..,,...f",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 0), "red" to TeamCfg(gold = 0, ai = true)),
        buildings = listOf(Building(6, 1, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 5, 11, commander = true),
            UnitSpawn("soldier", "blue", 4, 12),
            UnitSpawn("soldier", "blue", 6, 11),
            UnitSpawn("archer", "blue", 3, 11),
            UnitSpawn("soldier", "red", 6, 1, hold = true),
            UnitSpawn("soldier", "red", 4, 2),
            UnitSpawn("soldier", "red", 7, 3),
            UnitSpawn("archer", "red", 8, 0, hold = true),
        ),
        intro = listOf(Line("scout", "Сэр Ардан! Разбойники заняли деревню на севере и грабят каждого, кто идёт по тракту."),
            Line("hero", "Значит, пора им напомнить, чья это земля. Отряд, за мной!"),
            Line("scout", "Выберите воина, чтобы увидеть, куда он может пойти. Красным подсвечены цели для атаки."),
            Line("scout", "Лес и холмы дают защиту. Мечники умеют захватывать деревни: встаньте на неё и выберите «Захват».")),
        triggers = listOf(
            Trigger(
                `when` = onEvent { e, _ -> e.type == "capture" && e.team == "blue" },
                say = listOf(Line("elder", "Спасибо, рыцарь! Каждая деревня под вашей защитой приносит золото в казну каждый ход.")),
            ),
            Trigger(
                `when` = onEvent { e, _ -> e.type == "levelup" && e.team != "red" },
                say = listOf(Line("hero", "Опыт закаляет. С каждым уровнем воин бьёт сильнее и держит удар лучше.")),
            ),
        ),
        outro = listOf(Line("bandit", "Пощадите! Это Бран велел нам... Он собирает людей за рекой!"),
            Line("hero", "Бран? Тогда идём к реке. Этот край будет спокоен.")),
    ),
    Mission(
        id = "m2",
        title = "Деревни у реки",
        goal = "Захватите замок Брана или разбейте его войско. В своём замке можно нанимать воинов.",
        par = 16,
        map = listOf(
            "h..v..f.,Ch",
            "..,,,,,,,.v",
            ".f,...ff...",
            ".,,.h...,.f",
            ".,..h.v.,..",
            ".,......,ff",
            "~=~~~~~~=~~",
            "~=~~~~~~=~~",
            "f,..ff..,..",
            ".,.v....,..",
            ".,,,,,,,,.h",
            "f..f...,..v",
            "hh....,,...",
            ".v.,,,,...f",
            "ff.C,...hhf",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 400), "red" to TeamCfg(gold = 400, ai = true, buy = listOf("soldier", "archer", "bat", "healer"))),
        buildings = listOf(Building(3, 14, "blue"), Building(1, 13, "blue"), Building(9, 0, "red"), Building(10, 1, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 4, 14, commander = true),
            UnitSpawn("soldier", "blue", 3, 13),
            UnitSpawn("archer", "blue", 2, 14),
            UnitSpawn("warlord", "red", 8, 0),
            UnitSpawn("soldier", "red", 9, 1),
            UnitSpawn("soldier", "red", 7, 2),
            UnitSpawn("archer", "red", 10, 2),
        ),
        intro = listOf(Line("hero", "Этот замок у реки снова наш. Отсюда можно нанимать воинов."),
            Line("scout", "Нажмите на свой замок, когда он свободен, чтобы нанять отряд. Золото приходит от замков и деревень в начале хода."),
            Line("bran", "Рыцарь из столицы? Ха! Здесь мои деревни и моя река. Ребята, встречайте гостя!")),
        triggers = listOf(
            Trigger(
                `when` = onEvent { e, _ -> e.type == "death" && e.unitType == "warlord" },
                say = listOf(Line("bran", "Проклятье... Я ещё вернусь, пока цел мой замок!"),
            Line("scout", "Командира можно вернуть за золото в замке. Захватите замок Брана, и ему некуда будет вернуться!")),
            ),
            Trigger(
                `when` = onTurn(4),
                say = listOf(Line("scout", "Нетопыри Брана летают над рекой. Лучники бьют летающих особенно больно.")),
            ),
        ),
        check = { st ->
            if (ownerAt(st, 9, 0) == "blue") "win" else null
        },
        outro = listOf(Line("bran", "Ладно, ладно! Сдаюсь! Это всё Морвен, жрец из Серых холмов. Он платил нам мертвецким золотом!"),
            Line("hero", "Мертвецким? Похоже, беды только начинаются.")),
    ),
    Mission(
        id = "m3",
        title = "Мост на Серой реке",
        goal = "Продержитесь 12 ходов. Не отдайте свой замок, Ардан должен выжить.",
        par = 13,
        turnLimit = 12,
        map = listOf(
            "MM..C..hhMM",
            "M.,,,,,,..M",
            "h.,.ff.,.vh",
            ".v,..f.,...",
            "..,....,,,.",
            "f.,.hh...,f",
            "~~=~~~~~~=~",
            "~~=~~~~~~=~",
            "..,...h..,.",
            ".h,..v...,.",
            "..,,,,,,,,.",
            "f....,...ff",
            ".v...,..v..",
            "hh...C...hh",
            "MMh.....hMM",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 600), "red" to TeamCfg(gold = 300, ai = true, buy = listOf("bat", "mage", "golem", "soldier"))),
        buildings = listOf(Building(5, 13, "blue"), Building(5, 9, "blue"), Building(4, 0, "red"), Building(9, 2, "red"), Building(1, 3, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 5, 12, commander = true),
            UnitSpawn("soldier", "blue", 2, 8),
            UnitSpawn("soldier", "blue", 9, 8),
            UnitSpawn("archer", "blue", 4, 10),
            UnitSpawn("mage", "blue", 6, 10),
            UnitSpawn("necro", "red", 5, 1, hold = true, tag = "boss"),
            UnitSpawn("ghost", "red", 2, 4),
            UnitSpawn("ghost", "red", 8, 4),
            UnitSpawn("ghost", "red", 5, 3),
            UnitSpawn("bat", "red", 6, 2),
            UnitSpawn("golem", "red", 3, 1),
        ),
        graves = listOf(Pos(3, 4), Pos(6, 4)),
        intro = listOf(Line("hero", "Серая река. За ней холмы Морвена. Мосты нужно удержать любой ценой."),
            Line("morven", "Сколько живых пришло умереть у моей реки... Каждый павший станет моим воином."),
            Line("scout", "Павшие оставляют могилы. Чародей может поднять из могилы призрака, и враг тоже!")),
        triggers = listOf(
            Trigger(
                `when` = onTurn(3),
                say = listOf(Line("lira", "Ардан! Король прислал подмогу. Я подлечу раненых, держите меня рядом с ними.")),
                spawn = listOf(Spawn("healer", "blue", 3, 14), Spawn("soldier", "blue", 7, 14)),
            ),
            Trigger(
                `when` = onTurn(6, "red"),
                say = listOf(Line("morven", "Восстань, дитя пепла!")),
                spawn = listOf(Spawn("demon", "red", 4, 1)),
            ),
            Trigger(
                `when` = onTurn(10),
                say = listOf(Line("hero", "Ещё немного! Рассвет близко, нежить слабеет.")),
            ),
        ),
        check = { st ->
            if (ownerAt(st, 5, 13) != "blue") "lose"
            else if (st.turn > 12 && st.active == "blue") "win"
            else if (ownerAt(st, 4, 0) == "blue") "win"
            else null
        },
        outro = listOf(Line("morven", "Солнце... Довольно. Мы ещё встретимся, рыцарь, в моей цитадели."),
            Line("lira", "Он отступил за холмы. Нужно идти следом, пока он не собрал новое войско.")),
    ),
    Mission(
        id = "m4",
        title = "Цитадель Морвена",
        goal = "Победите Морвена. Ардан должен выжить.",
        par = 10,
        map = listOf(
            "MMh.,C,.hMM",
            "Mv..,,,..vM",
            "h..f.,.f..h",
            ".f..,,,..f.",
            "...,,.,,...",
            "Mh.,...,.hM",
            "~~~=~~~=~~~",
            ".v.,...,.v.",
            "f..,.h.,..f",
            "..,,...,,..",
            ".h,..v..,h.",
            "..,,,,,,,..",
            "f.v..,..v.f",
            "hh...C...hh",
            "MMM.....MMM",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 700), "red" to TeamCfg(gold = 500, ai = true, buy = listOf("bat", "mage", "golem", "demon", "archer"))),
        buildings = listOf(Building(5, 13, "blue"), Building(5, 0, "red"), Building(1, 1, "red"), Building(9, 1, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 5, 12, commander = true),
            UnitSpawn("healer", "blue", 4, 12),
            UnitSpawn("soldier", "blue", 3, 11),
            UnitSpawn("soldier", "blue", 7, 11),
            UnitSpawn("archer", "blue", 6, 12),
            UnitSpawn("mage", "blue", 5, 11),
            UnitSpawn("necro", "red", 5, 1, hold = true, tag = "boss"),
            UnitSpawn("golem", "red", 3, 5, hold = true),
            UnitSpawn("golem", "red", 7, 5, hold = true),
            UnitSpawn("mage", "red", 5, 3),
            UnitSpawn("ghost", "red", 2, 4),
            UnitSpawn("ghost", "red", 8, 4),
            UnitSpawn("bat", "red", 0, 3),
            UnitSpawn("bat", "red", 10, 3),
            UnitSpawn("archer", "red", 6, 2, hold = true),
        ),
        graves = listOf(Pos(4, 2), Pos(6, 4), Pos(3, 3)),
        intro = listOf(Line("lira", "Цитадель Морвена. Мосты сторожат големы, их не обойти без потерь."),
            Line("hero", "Наймём катапульту: она бьёт издалека, хоть и не стреляет после движения."),
            Line("morven", "Ты пришёл сам, рыцарь. Как любезно. Твоё тело послужит мне лучше, чем служило королю.")),
        triggers = listOf(
            Trigger(
                `when` = onEvent { e, _ -> e.type == "hit" && e.toTag == "boss" },
                say = listOf(Line("morven", "Ты посмел ранить меня?! Мертвецы, ко мне!")),
            ),
            Trigger(
                `when` = onTurn(8, "red"),
                say = listOf(Line("morven", "Из глубин холмов, ко мне, мои летуны!")),
                spawn = listOf(Spawn("bat", "red", 1, 2), Spawn("bat", "red", 9, 2)),
            ),
        ),
        check = { st ->
            if (st.flags["bossDead"] == true) "win" else null
        },
        outro = listOf(Line("morven", "Нет... тьма... не отпускает..."),
            Line("lira", "Всё кончено. Мертвые наконец спят спокойно."),
            Line("hero", "Возвращаемся домой. Королевство снова в безопасности.")),
    ),
    Mission(
        id = "m5",
        title = "Болотная тропа",
        goal = "Разбейте всех врагов на болоте. Ардан должен выжить.",
        par = 14,
        map = listOf(
            "ff.hh.v.hff",
            "f..,,,,..vf",
            ".ss..s..ss.",
            "s~s.ss.s~ss",
            ".ss.s~s.ss.",
            "..,.ssf.,..",
            ".f,,..,,h..",
            ".sss.f.ss..",
            "s~s..,..sss",
            ".s..f.,h...",
            "..,,,,,,.v.",
            "f..h.,.v..f",
            "hf.......ff",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 0), "red" to TeamCfg(gold = 0, ai = true)),
        buildings = listOf(Building(6, 0, "red"), Building(9, 1, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 5, 12, commander = true),
            UnitSpawn("healer", "blue", 4, 12),
            UnitSpawn("soldier", "blue", 3, 11),
            UnitSpawn("soldier", "blue", 7, 11),
            UnitSpawn("archer", "blue", 6, 12),
            UnitSpawn("spider", "red", 3, 3),
            UnitSpawn("spider", "red", 7, 3),
            UnitSpawn("spider", "red", 5, 6, hold = true),
            UnitSpawn("soldier", "red", 2, 1),
            UnitSpawn("soldier", "red", 6, 0, hold = true),
            UnitSpawn("archer", "red", 9, 1, hold = true),
            UnitSpawn("spider", "red", 8, 5),
            UnitSpawn("archer", "red", 3, 1),
        ),
        intro = listOf(Line("hero", "Морвен пал, но с юга, из болот, тянет гнилью. Земля вокруг мёртвая."),
            Line("lira", "Это работа болотной ведьмы Ильвы. Говорят, её нельзя убить обычной сталью."),
            Line("scout", "Мелководье тяжело для пехоты: каждая клетка стоит три хода. Идите по тропам.")),
        triggers = listOf(
            Trigger(
                `when` = onEvent { e, _ -> e.type == "poison" },
                say = listOf(Line("lira", "Яд пауков! Отравленный теряет силу и защиту. Подойдите ко мне или встаньте на свою деревню, и яд сойдёт.")),
            ),
            Trigger(
                `when` = onTurn(3),
                say = listOf(Line("hero", "Что за свет над тропой?"),
            Line("lira", "Огонёк! Он с нами. Соседние союзники рядом с ним бьют сильнее, а нежить он жжёт.")),
                spawn = listOf(Spawn("wisp", "blue", 5, 12)),
            ),
        ),
        outro = listOf(Line("bandit", "Ильва платит нам болотным золотом... её логово в самом сердце топи."),
            Line("hero", "Тогда идём в топь. Лира, держись рядом.")),
    ),
    Mission(
        id = "m6",
        title = "Кристалл Света",
        goal = "Доведите Кристалл до алтаря (золотая рамка) за 14 ходов. Если Кристалл погибнет, миссия провалена.",
        par = 12,
        turnLimit = 14,
        map = listOf(
            "MMh.,r,.hMM",
            "Mf..,,,..fM",
            "h..f.,.f..h",
            ".f.,,,,,.f.",
            "..,..f..,..",
            ".h,.....,h.",
            "~~~=~~~=~~~",
            "~~~=~~~=~~~",
            ".f,.....,f.",
            "..,,.h.,,..",
            ".h..,,,..h.",
            "f..v.,.v..f",
            "..,,,,,,,..",
            "hh...C...hh",
            "MM.......MM",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 300), "red" to TeamCfg(gold = 200, ai = true, buy = listOf("spider", "archer", "soldier"))),
        buildings = listOf(Building(5, 13, "blue")),
        units = listOf(
            UnitSpawn("hero", "blue", 5, 12, commander = true),
            UnitSpawn("crystal", "blue", 5, 11, goal = Pos(5, 0)),
            UnitSpawn("healer", "blue", 4, 12),
            UnitSpawn("soldier", "blue", 3, 11),
            UnitSpawn("soldier", "blue", 7, 11),
            UnitSpawn("archer", "blue", 6, 12),
            UnitSpawn("mage", "blue", 4, 11),
            UnitSpawn("spider", "red", 3, 5),
            UnitSpawn("spider", "red", 7, 5),
            UnitSpawn("soldier", "red", 2, 3),
            UnitSpawn("soldier", "red", 8, 3),
            UnitSpawn("archer", "red", 4, 1, hold = true),
            UnitSpawn("archer", "red", 6, 1, hold = true),
        ),
        marks = listOf(Pos(5, 0)),
        intro = listOf(Line("elder", "Рыцарь, в нашем храме хранился Кристалл Света. Только его сияние очищает топь от яда."),
            Line("elder", "Отнесите его на древний алтарь на севере. Он отмечен золотой рамкой."),
            Line("lira", "Кристалл не сражается и ходит медленно. Если его разобьют, надежды не останется. Прикрывайте его!")),
        triggers = listOf(
            Trigger(
                `when` = onTurn(6),
                say = listOf(Line("scout", "Впереди два моста через реку. Враги ждут за ними. Не торопите Кристалл вперёд бойцов.")),
            ),
        ),
        check = { st ->
            val c = st.units.find { it.type == "crystal" && it.team == "blue" }
            if (c != null && c.x == 5 && c.y == 0) "win"
            else if (st.flags["crystalLost"] == true) "lose"
            else if (st.turn > 14 && st.active == "blue") "lose"
            else null
        },
        outro = listOf(Line("elder", "Свет вернулся! Смотрите, топь светлеет, а чары слабеют."),
            Line("lira", "Ильва теперь знает, что мы идём. Дальше её путь лежит через устье реки.")),
    ),
    Mission(
        id = "m7",
        title = "Устье реки",
        goal = "Захватите замок красных или разбейте их войско. В вашем замке можно нанимать воинов.",
        par = 14,
        map = listOf(
            "hh.,C,..hhM",
            ".v,,,,,,.v.",
            "ss.f..f.sss",
            "~ss..r.ss~~",
            "~~s.ff.s~~~",
            "~~ss...ss~~",
            "~~~s.r.s~~~",
            "~~ss...ss~~",
            "rss..h..ss~",
            ".ss.,,,.ss.",
            ".v.f,.,f.v.",
            "..,,,.,,...",
            "f..v...v..f",
            "hh..C....hh",
            "MMf.....fMM",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 600), "red" to TeamCfg(gold = 200, ai = true, buy = listOf("undine", "catapult", "soldier", "archer"))),
        buildings = listOf(Building(4, 13, "blue"), Building(3, 12, "blue"), Building(7, 12, "blue"), Building(1, 10, "blue"), Building(9, 10, "blue"), Building(4, 0, "red"), Building(1, 1, "red"), Building(9, 1, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 4, 12, commander = true),
            UnitSpawn("healer", "blue", 5, 12),
            UnitSpawn("soldier", "blue", 3, 11),
            UnitSpawn("soldier", "blue", 6, 11),
            UnitSpawn("archer", "blue", 5, 11),
            UnitSpawn("warlord", "red", 4, 1, hold = true),
            UnitSpawn("undine", "red", 2, 3),
            UnitSpawn("undine", "red", 7, 3, hold = true),
            UnitSpawn("catapult", "red", 5, 2, hold = true),
            UnitSpawn("archer", "red", 6, 2, hold = true),
        ),
        intro = listOf(Line("scout", "В устье реки хозяйничают слуги Ильвы. У них водяные: те плавают по воде и мелководью и там крепки."),
            Line("hero", "Нам тоже нужны водяные. Наймём их в замке, они пройдут там, где пехота увязнет."),
            Line("lira", "Осторожно: красные катапульты разрушают деревни, а на их месте остаются руины.")),
        triggers = listOf(
            Trigger(
                `when` = onEvent { e, _ -> e.type == "destroy" },
                say = listOf(Line("scout", "Деревню разрушили! Мечник или Ардан могут встать на руины и выбрать «Отстроить»: деревня снова станет нашей.")),
            ),
            Trigger(
                `when` = onTurn(5),
                say = listOf(Line("hero", "Руины тоже дают защиту. Можно укрыться за их стенами.")),
            ),
        ),
        check = { st ->
            if (ownerAt(st, 4, 0) == "blue") "win" else null
        },
        outro = listOf(Line("hero", "Устье наше. Дорога к топям открыта."),
            Line("lira", "Ильва ждёт нас в своём логове. Там нас ждёт последний бой.")),
    ),
    Mission(
        id = "m8",
        title = "Логово Ильвы",
        goal = "Победите ведьму Ильву. Ардан должен выжить.",
        par = 14,
        map = listOf(
            "ffh..C..hff",
            "fs.,,,,,.sf",
            "s~s..f..s~s",
            "~~ss.,.ss~~",
            "s~s.f.f.s~s",
            ".ss.,,,.ss.",
            "..r.,.,.r..",
            "f.v.,.,.v.f",
            ".h.,,.,,.h.",
            "..f.....f..",
            "h..v...v..h",
            "hh...C...hh",
            "MM.......MM",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 1000), "red" to TeamCfg(gold = 300, ai = true, buy = listOf("spider", "undine", "golem", "demon"))),
        buildings = listOf(Building(5, 11, "blue"), Building(3, 10, "blue"), Building(7, 10, "blue"), Building(5, 0, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 5, 10, commander = true),
            UnitSpawn("healer", "blue", 4, 10),
            UnitSpawn("wisp", "blue", 6, 10),
            UnitSpawn("soldier", "blue", 4, 9),
            UnitSpawn("soldier", "blue", 6, 9),
            UnitSpawn("archer", "blue", 5, 9),
            UnitSpawn("witch", "red", 5, 1, hold = true, tag = "boss"),
            UnitSpawn("golem", "red", 4, 4, hold = true),
            UnitSpawn("spider", "red", 3, 5),
            UnitSpawn("spider", "red", 7, 5),
            UnitSpawn("golem", "red", 6, 4, hold = true),
            UnitSpawn("undine", "red", 1, 3),
            UnitSpawn("undine", "red", 9, 3),
            UnitSpawn("archer", "red", 5, 3, hold = true),
        ),
        intro = listOf(Line("lira", "Вот и логово. Гниль идёт отсюда, из самого сердца топи."),
            Line("witch", "Рыцарь и его целительница. Мои пауки давно ждут гостей. Топь вас не отпустит."),
            Line("hero", "Держитесь вместе: яд снимает Лира или родная деревня. Огонёк усилит тех, кто рядом.")),
        triggers = listOf(
            Trigger(
                `when` = onEvent { e, _ -> e.type == "hit" && e.toTag == "boss" },
                say = listOf(Line("witch", "Ты посмел ранить меня?! Топь, подними своих детей!")),
            ),
            Trigger(
                `when` = onTurn(7, "red"),
                say = listOf(Line("witch", "Из глубины, ко мне!")),
                spawn = listOf(Spawn("spider", "red", 4, 1), Spawn("spider", "red", 6, 1)),
            ),
        ),
        check = { st ->
            if (st.flags["bossDead"] == true) "win" else null
        },
        outro = listOf(Line("witch", "Топь... вернётся... она всегда возвращается..."),
            Line("lira", "Воздух чист. Яд уходит из земли."),
            Line("hero", "Эта земля свободна. Но с севера дует холодный ветер...")),
    ),
    Mission(
        id = "m9",
        title = "Снежная дорога",
        goal = "Разбейте передовой отряд северян. Ардан должен выжить.",
        par = 12,
        map = listOf(
            "MMh.v.,.hMM",
            "Mf..,,,..fM",
            "h..f.,.f..h",
            ".v..,,,..v.",
            "ff.,.h.,.ff",
            "...,...,...",
            "M..,.M.,..M",
            ".f.,,,,,.f.",
            "...v...v...",
            "hf.......fh",
            "..,,,,,,,..",
            ".f...v...f.",
            "MM..,,,..MM",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 0), "red" to TeamCfg(gold = 0, ai = true)),
        buildings = listOf(Building(4, 0, "red"), Building(1, 3, "red"), Building(9, 3, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 5, 12, commander = true),
            UnitSpawn("soldier", "blue", 4, 12),
            UnitSpawn("soldier", "blue", 6, 12),
            UnitSpawn("archer", "blue", 5, 11),
            UnitSpawn("healer", "blue", 3, 11),
            UnitSpawn("shaman", "red", 4, 3),
            UnitSpawn("shaman", "red", 6, 3),
            UnitSpawn("soldier", "red", 3, 5),
            UnitSpawn("soldier", "red", 7, 5),
            UnitSpawn("archer", "red", 5, 2, hold = true),
            UnitSpawn("soldier", "red", 4, 0, hold = true),
            UnitSpawn("bat", "red", 8, 1),
        ),
        intro = listOf(Line("hero", "Прошла зима после победы над Ильвой. А теперь с севера бегут люди."),
            Line("sif", "Я Сиф, из северных деревень. Вождь Хаар ведёт орду на юг, и его шаманы сковывают ноги воинам."),
            Line("hero", "Тогда встретим его передовой отряд здесь, на снежной дороге.")),
        triggers = listOf(
            Trigger(
                `when` = onEvent { e, _ -> e.type == "slow" },
                say = listOf(Line("sif", "Скован! В свой следующий ход этот воин пройдёт на две клетки меньше. Бейте шаманов первыми.")),
            ),
            Trigger(
                `when` = onTurn(2),
                say = listOf(Line("sif", "Не все шаманы служат Хаару. Мой брат пойдёт с вами, его посох тоже сковывает.")),
                spawn = listOf(Spawn("shaman", "blue", 5, 12)),
            ),
        ),
        outro = listOf(Line("bandit", "Хаар собрал всех северных разбойников. Даже Бран теперь служит ему!"),
            Line("hero", "Бран? Старый знакомый. Значит, следующая цель - его форт.")),
    ),
    Mission(
        id = "m10",
        title = "Форт Брана",
        goal = "Захватите замок Брана командиром. Ардан должен выжить.",
        par = 14,
        map = listOf(
            "MMf.,C,.fMM",
            "Mh..,,,..hM",
            "f.v.,.,.v.f",
            "..ff,.,ff..",
            "h...,,,...h",
            "~~=~~~~~=~~",
            ".s.......s.",
            "f..h.v.h..f",
            ".,,,,,,,,,.",
            ".,.f...f.,.",
            "v,.......,v",
            ".,,..h..,,.",
            "f..,,,,,..f",
            "h..,.C.,..h",
            "MM.v...v.MM",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 800), "red" to TeamCfg(gold = 200, ai = true, buy = listOf("shaman", "soldier", "archer", "catapult"))),
        buildings = listOf(Building(5, 13, "blue"), Building(3, 14, "blue"), Building(7, 14, "blue"), Building(5, 0, "red"), Building(2, 2, "red"), Building(8, 2, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 5, 12, commander = true),
            UnitSpawn("soldier", "blue", 4, 12),
            UnitSpawn("soldier", "blue", 6, 12),
            UnitSpawn("shaman", "blue", 5, 11),
            UnitSpawn("healer", "blue", 3, 12),
            UnitSpawn("archer", "blue", 7, 12),
            UnitSpawn("warlord", "red", 5, 1, hold = true),
            UnitSpawn("catapult", "red", 4, 2, hold = true),
            UnitSpawn("soldier", "red", 2, 4),
            UnitSpawn("soldier", "red", 8, 4),
            UnitSpawn("shaman", "red", 5, 4),
            UnitSpawn("archer", "red", 4, 3, hold = true),
        ),
        intro = listOf(Line("bran", "Ардан! Опять ты. Но теперь у меня стены и катапульты, а за спиной - Хаар!"),
            Line("lira", "Катапульты бьют издалека, но после движения стрелять не могут. Подходите к ним вплотную."),
            Line("hero", "Мосты на флангах. Возьмём их и ударим по замку.")),
        triggers = listOf(
            Trigger(
                `when` = onTurn(6, "red"),
                say = listOf(Line("bran", "Ребята, все на стены!")),
                spawn = listOf(Spawn("soldier", "red", 5, 1), Spawn("shaman", "red", 4, 1)),
            ),
        ),
        check = { st ->
            if (ownerAt(st, 5, 0) == "blue") "win" else null
        },
        outro = listOf(Line("bran", "Сдаюсь, опять сдаюсь! Хаар идёт к Ледяному мосту, хочет отрезать вас от дома."),
            Line("sif", "Если мост падёт, юг останется без защиты. Нужно спешить.")),
    ),
    Mission(
        id = "m11",
        title = "Ледяной мост",
        goal = "Продержитесь 10 ходов. Не отдайте свой замок, Ардан должен выжить.",
        par = 11,
        turnLimit = 10,
        map = listOf(
            "MM..,C,..MM",
            "M...,,,...M",
            ".f.v,.,v.f.",
            "..,,,.,,,..",
            "h.,..h..,.h",
            "MM,.MMM.,MM",
            "~~=~~~~~=~~",
            "~~=~~~~~=~~",
            "M.,.....,.M",
            "..,.f.f.,..",
            "v.,,,,,,,.v",
            ".h...,...h.",
            "f..f.,.f..f",
            "h.v..C..v.h",
            "MM.......MM",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 500), "red" to TeamCfg(gold = 600, ai = true, buy = listOf("shaman", "soldier", "archer", "golem", "bat"))),
        buildings = listOf(Building(5, 13, "blue"), Building(2, 13, "blue"), Building(8, 13, "blue"), Building(0, 10, "blue"), Building(10, 10, "blue"), Building(5, 0, "red"), Building(3, 2, "red"), Building(7, 2, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 5, 12, commander = true),
            UnitSpawn("soldier", "blue", 2, 9),
            UnitSpawn("soldier", "blue", 8, 9),
            UnitSpawn("archer", "blue", 3, 10),
            UnitSpawn("archer", "blue", 7, 10),
            UnitSpawn("shaman", "blue", 5, 11),
            UnitSpawn("healer", "blue", 4, 12),
            UnitSpawn("warlord", "red", 5, 1, hold = true),
            UnitSpawn("soldier", "red", 2, 3),
            UnitSpawn("soldier", "red", 8, 3),
            UnitSpawn("shaman", "red", 4, 3),
            UnitSpawn("shaman", "red", 6, 3),
            UnitSpawn("golem", "red", 5, 4),
            UnitSpawn("archer", "red", 2, 4),
            UnitSpawn("archer", "red", 8, 4),
        ),
        intro = listOf(Line("sif", "Хаар бросил к мосту всё войско. Если удержим переправы десять ходов, орда выдохнется."),
            Line("hero", "Два моста - две двери. Встанем у выходов и не пустим их на наш берег."),
            Line("lira", "Берегитесь нетопырей: им вода не помеха.")),
        triggers = listOf(
            Trigger(
                `when` = onTurn(4, "red"),
                say = listOf(Line("haar", "Крылья севера, летите через реку!")),
                spawn = listOf(Spawn("bat", "red", 4, 1), Spawn("bat", "red", 6, 1)),
            ),
            Trigger(
                `when` = onTurn(7, "red"),
                say = listOf(Line("haar", "Каменные стражи, ломайте их строй!")),
                spawn = listOf(Spawn("golem", "red", 5, 2), Spawn("shaman", "red", 5, 3)),
            ),
        ),
        check = { st ->
            if (ownerAt(st, 5, 13) != "blue") "lose"
            else if (st.turn > 10 && st.active == "blue") "win"
            else if (ownerAt(st, 5, 0) == "blue") "win"
            else null
        },
        outro = listOf(Line("haar", "Отступаем к Трону! Пусть рыцарь придёт ко мне сам."),
            Line("hero", "Придём. Это последний поход.")),
    ),
    Mission(
        id = "m12",
        title = "Трон Севера",
        goal = "Победите вождя Хаара. Ардан должен выжить.",
        par = 16,
        map = listOf(
            "MMh.,C,.hMM",
            "Mh..,,,..hM",
            "f.r.,.,.r.f",
            ".,,,,h,,,,.",
            "f.,.MMM.,.f",
            "..,..v..,..",
            "hh,.....,hh",
            "..,,.r.,,..",
            "v.f.....f.v",
            ".,,,,h,,,,.",
            ".,.f...f.,.",
            "f,..,,,..,f",
            "h.v..h..v.h",
            "M...,C,...M",
            "MMf.....fMM",
        ),
        teams = linkedMapOf("blue" to TeamCfg(gold = 1000), "red" to TeamCfg(gold = 400, ai = true, buy = listOf("shaman", "golem", "catapult", "demon", "archer"))),
        buildings = listOf(Building(5, 13, "blue"), Building(2, 12, "blue"), Building(8, 12, "blue"), Building(5, 0, "red")),
        units = listOf(
            UnitSpawn("hero", "blue", 5, 12, commander = true),
            UnitSpawn("healer", "blue", 4, 13),
            UnitSpawn("shaman", "blue", 6, 13),
            UnitSpawn("wisp", "blue", 4, 12),
            UnitSpawn("soldier", "blue", 6, 12),
            UnitSpawn("archer", "blue", 3, 11),
            UnitSpawn("golem", "blue", 7, 11),
            UnitSpawn("haar", "red", 5, 1, hold = true, tag = "boss"),
            UnitSpawn("golem", "red", 4, 3, hold = true),
            UnitSpawn("golem", "red", 6, 3, hold = true),
            UnitSpawn("catapult", "red", 5, 3, hold = true),
            UnitSpawn("shaman", "red", 3, 4),
            UnitSpawn("shaman", "red", 7, 4),
            UnitSpawn("archer", "red", 2, 3),
            UnitSpawn("archer", "red", 8, 3),
            UnitSpawn("bat", "red", 1, 6),
        ),
        intro = listOf(Line("haar", "Юг размяк, рыцарь. Мои чары скуют твоих воинов, как лёд сковывает реку."),
            Line("sif", "Его удар тоже сковывает. Не подставляйте ему тех, кто нужен в следующем ходу."),
            Line("hero", "Огонёк, Лира, держитесь рядом. Идём на Трон.")),
        triggers = listOf(
            Trigger(
                `when` = onEvent { e, _ -> e.type == "hit" && e.toTag == "boss" },
                say = listOf(Line("haar", "Ты силён, южанин... Шаманы, ко мне!")),
                spawn = listOf(Spawn("shaman", "red", 4, 1), Spawn("shaman", "red", 6, 1)),
            ),
            Trigger(
                `when` = onTurn(8, "red"),
                say = listOf(Line("haar", "Дух метели, приди!")),
                spawn = listOf(Spawn("demon", "red", 5, 2)),
            ),
        ),
        check = { st ->
            if (st.flags["bossDead"] == true) "win" else null
        },
        outro = listOf(Line("haar", "Лёд... тает..."),
            Line("sif", "Север свободен. Мои люди вернутся домой."),
            Line("hero", "Хроники Эльдора дописаны. Пора и нам домой.")),
    ),
)

fun missionById(id: String): Mission? = if (isSkirmishId(id)) skirmishMission(id) else MISSIONS.firstOrNull { it.id == id }

// Победа/поражение: общие правила кампании + check() миссии
fun evaluateOutcome(st: GameState, mission: Mission): String? {
    if (mission.skirmish) return skirmishOutcome(st)
    if (st.teams["blue"]!!.commanderDead) return "lose"
    val own = mission.check?.invoke(st)
    if (own != null) return own
    if (isEliminated(st, "red")) return "win"
    if (isEliminated(st, "blue")) return "lose"
    return null
}

// Поставить юнита в ближайшую свободную проходимую клетку
fun spawnNear(st: GameState, type: String, team: String, x: Int, y: Int): Unit? {
    val seen = HashSet<String>()
    val q = ArrayDeque<Pos>()
    q.addLast(Pos(x, y))
    while (q.isNotEmpty()) {
        val c = q.removeFirst()
        val k = key(c.x, c.y)
        if (seen.contains(k) || !inBounds(st, c.x, c.y)) continue
        seen.add(k)
        if (unitAt(st, c.x, c.y) == null && !moveCostFor(st, type, c.x, c.y).isInfinite()) return spawnUnit(st, type, team, c.x, c.y)
        for (dir in DIRS) q.addLast(Pos(c.x + dir[0], c.y + dir[1]))
    }
    return null
}
