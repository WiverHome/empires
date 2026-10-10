// DATA: местность, юниты, баланс (порт js/data.js)
package empires.logic

// Общие числа баланса
object BAL {
    const val maxHp = 100
    const val unitCap = 20             // лимит юнитов на сторону
    const val incomeVillage = 30
    const val incomeCastle = 50
    const val healBuilding = 20        // лечение на своём здании в начале хода
    const val healAura = 15            // лечение от целительницы соседям
    const val xpPerLevel = 100         // опыта на уровень
    const val xpKillBonus = 30
    const val maxLevel = 9
    const val levelAtk = 3             // +атака за уровень
    const val levelDef = 2             // +защита за уровень
    const val commanderReviveBase = 200
    const val commanderRevivePerLevel = 100
    const val graveTurns = 2           // сколько полных ходов лежит могила
    const val poisonAtk = 10           // яд: -атака
    const val poisonDef = 10           // яд: -защита
    const val poisonTurns = 3          // сколько своих ходов держится яд (лечение снимает сразу)
    const val slowMove = 2             // сковывание (шаман): -очки хода на следующий свой ход
    const val wispAtk = 10             // огонёк: +атака соседним союзникам
    const val waterDef = 15            // водный юнит: +защита в воде и на мелководье
    const val vision = 3               // туман войны: обзор юнита в клетках (летающим +1, на холме и горе +1)
    const val visionBuilding = 2       // обзор своего здания
}

// Сложность: множитель дохода ИИ и случайный разброс оценок ходов ИИ (ошибки)
class Difficulty(val name: String, val aiIncome: Double, val aiNoise: Int)

val DIFFICULTY: Map<String, Difficulty> = linkedMapOf(
    "easy" to Difficulty("Лёгкая", 0.7, 35),
    "normal" to Difficulty("Обычная", 1.0, 0),
    "hard" to Difficulty("Трудная", 1.3, 0),
)

// Местность. cost: стоимость передвижения (Infinity - непроходимо), def: бонус защиты
class TerrainDef(
    val name: String, val cost: Double, val def: Int,
    val building: Boolean = false, val income: Int = 0, val water: Boolean = false,
)

val TERRAIN: Map<String, TerrainDef> = linkedMapOf(
    "plain" to TerrainDef("Равнина", 1.0, 5),
    "road" to TerrainDef("Дорога", 1.0, 0),
    "forest" to TerrainDef("Лес", 2.0, 10),
    "hill" to TerrainDef("Холм", 2.0, 15),
    "mountain" to TerrainDef("Горы", 3.0, 20),
    "water" to TerrainDef("Вода", Double.POSITIVE_INFINITY, 0, water = true),
    "shallow" to TerrainDef("Мелководье", 3.0, 0, water = true),
    "bridge" to TerrainDef("Мост", 1.0, 0),
    "ruins" to TerrainDef("Руины", 1.0, 10),
    "village" to TerrainDef("Деревня", 1.0, 15, building = true, income = BAL.incomeVillage),
    "castle" to TerrainDef("Замок", 1.0, 20, building = true, income = BAL.incomeCastle),
)

// Символы карт миссий
val MAP_LEGEND: Map<Char, String> = linkedMapOf(
    '.' to "plain", ',' to "road", 'f' to "forest", 'h' to "hill", 'M' to "mountain",
    '~' to "water", '=' to "bridge", 'v' to "village", 'C' to "castle", 's' to "shallow", 'r' to "ruins",
)

// Оружие в руке: лист, индекс, позиция в сетке 48x48
class Weapon(val sheet: String, val idx: Int, val x: Int, val y: Int)

// Юниты. sprite: индекс в листе sheet (dungeon или town), proc - процедурный спрайт (sprite = -1)
// atk: [min, max], range: [min, max]
class UnitDef(
    val name: String, val sprite: Int = -1, val sheet: String = "dungeon", val proc: String? = null,
    val cost: Int, val move: Int, val atk: List<Int>, val def: Int, val range: List<Int>,
    val weapon: Weapon? = null,
    val canCapture: Boolean = false, val vsFlying: Int = 0, val aura: Boolean = false, val flying: Boolean = false,
    val canRaise: Boolean = false, val poison: Boolean = false, val slow: Boolean = false, val auraAtk: Boolean = false,
    val vsUndead: Int = 0, val swim: Boolean = false, val noMoveAttack: Boolean = false, val canDestroy: Boolean = false,
    val noBuy: Boolean = false, val undead: Boolean = false, val noAttack: Boolean = false, val commander: Boolean = false,
    val woods: Boolean = false,
    val desc: String,
) {
    val rangeMin get() = range[0]
    val rangeMax get() = range[1]
}

val UNIT_DEFS: Map<String, UnitDef> = linkedMapOf(
    "soldier" to UnitDef(
        name = "Мечник",
        sprite = 98,
        cost = 150,
        move = 4,
        atk = listOf(50, 55),
        def = 5,
        range = listOf(1, 1),
        weapon = Weapon("dungeon", 104, 24, 14),
        canCapture = true,
        desc = "Дешёвая пехота. Захватывает деревни.",
    ),
    "archer" to UnitDef(
        name = "Лучник",
        sprite = 112,
        cost = 250,
        move = 4,
        atk = listOf(50, 55),
        def = 5,
        range = listOf(1, 2),
        weapon = Weapon("town", 118, 22, 12),
        vsFlying = 25,
        desc = "Стреляет на 1-2 клетки. Силён против летающих.",
    ),
    "healer" to UnitDef(
        name = "Целительница",
        sprite = 99,
        cost = 300,
        move = 4,
        atk = listOf(30, 40),
        def = 5,
        range = listOf(1, 1),
        weapon = Weapon("dungeon", 130, 24, 10),
        aura = true,
        desc = "В начале хода лечит соседних союзников.",
    ),
    "bat" to UnitDef(
        name = "Нетопырь",
        sprite = 120,
        cost = 300,
        move = 6,
        atk = listOf(45, 50),
        def = 5,
        range = listOf(1, 1),
        flying = true,
        desc = "Быстрый, летает над водой и горами. Не получает защиту от местности.",
    ),
    "wolf" to UnitDef(
        name = "Волк",
        sprite = 124,
        cost = 250,
        move = 6,
        atk = listOf(40, 45),
        def = 5,
        range = listOf(1, 1),
        woods = true,
        desc = "Быстрый и дешёвый. По лесу ходит как по равнине.",
    ),
    "mage" to UnitDef(
        name = "Чародей",
        sprite = 84,
        cost = 400,
        move = 4,
        atk = listOf(45, 50),
        def = 5,
        range = listOf(1, 2),
        weapon = Weapon("dungeon", 129, 24, 10),
        canRaise = true,
        desc = "Бьёт на 1-2 клетки. Поднимает призрака из могилы рядом.",
    ),
    "spider" to UnitDef(
        name = "Паук",
        sprite = 122,
        cost = 350,
        move = 5,
        atk = listOf(40, 45),
        def = 5,
        range = listOf(1, 1),
        poison = true,
        desc = "Отравляет цель: яд снижает атаку и защиту, пока его не вылечат.",
    ),
    "shaman" to UnitDef(
        name = "Шаман",
        sprite = 86,
        cost = 350,
        move = 4,
        atk = listOf(35, 40),
        def = 5,
        range = listOf(1, 2),
        weapon = Weapon("dungeon", 128, 24, 10),
        slow = true,
        desc = "Бьёт на 1-2 клетки. Сковывает цель: в свой следующий ход она пройдёт на 2 клетки меньше.",
    ),
    "wisp" to UnitDef(
        name = "Огонёк",
        proc = "wisp",
        cost = 450,
        move = 5,
        atk = listOf(35, 40),
        def = 5,
        range = listOf(1, 1),
        auraAtk = true,
        vsUndead = 30,
        desc = "Усиливает атаку соседних союзников. Жжёт нежить.",
    ),
    "undine" to UnitDef(
        name = "Водяной",
        sprite = 108,
        cost = 300,
        move = 4,
        atk = listOf(50, 55),
        def = 10,
        range = listOf(1, 1),
        swim = true,
        desc = "Плавает по воде и мелководью, в воде крепче.",
    ),
    "golem" to UnitDef(
        name = "Голем",
        sprite = 109,
        cost = 600,
        move = 3,
        atk = listOf(60, 70),
        def = 20,
        range = listOf(1, 1),
        weapon = Weapon("dungeon", 117, 22, 20),
        desc = "Медленный и очень крепкий.",
    ),
    "catapult" to UnitDef(
        name = "Катапульта",
        sprite = 57,
        sheet = "town",
        cost = 700,
        move = 3,
        atk = listOf(55, 70),
        def = 10,
        range = listOf(2, 4),
        noMoveAttack = true,
        canDestroy = true,
        desc = "Бьёт на 2-4 клетки, но не может стрелять после движения. Разрушает деревни.",
    ),
    "demon" to UnitDef(
        name = "Демон",
        sprite = 110,
        cost = 1000,
        move = 6,
        atk = listOf(70, 80),
        def = 20,
        range = listOf(1, 1),
        flying = true,
        desc = "Летающий и смертоносный.",
    ),
    "ghost" to UnitDef(
        name = "Призрак",
        sprite = 121,
        cost = 0,
        move = 4,
        atk = listOf(40, 50),
        def = 5,
        range = listOf(1, 1),
        noBuy = true,
        undead = true,
        desc = "Поднят чародеем из могилы.",
    ),
    "crystal" to UnitDef(
        name = "Кристалл",
        proc = "crystal",
        cost = 0,
        move = 3,
        atk = listOf(0, 0),
        def = 15,
        range = listOf(0, 0),
        noBuy = true,
        noAttack = true,
        desc = "Святыня. Не сражается. Его нужно довести до цели.",
    ),
    "hero" to UnitDef(
        name = "Ардан",
        sprite = 97,
        cost = 0,
        move = 5,
        atk = listOf(55, 65),
        def = 15,
        range = listOf(1, 1),
        weapon = Weapon("dungeon", 106, 24, 12),
        canCapture = true,
        noBuy = true,
        commander = true,
        desc = "Командир. Захватывает замки.",
    ),
    "warlord" to UnitDef(
        name = "Бран",
        sprite = 87,
        cost = 0,
        move = 5,
        atk = listOf(55, 65),
        def = 15,
        range = listOf(1, 1),
        weapon = Weapon("dungeon", 119, 22, 12),
        canCapture = true,
        noBuy = true,
        commander = true,
        desc = "Вожак разбойников.",
    ),
    "necro" to UnitDef(
        name = "Морвен",
        sprite = 111,
        cost = 0,
        move = 5,
        atk = listOf(55, 65),
        def = 15,
        range = listOf(1, 2),
        weapon = Weapon("dungeon", 131, 24, 8),
        canCapture = true,
        canRaise = true,
        noBuy = true,
        undead = true,
        commander = true,
        desc = "Тёмный жрец. Поднимает мёртвых.",
    ),
    "haar" to UnitDef(
        name = "Хаар",
        sprite = 96,
        cost = 0,
        move = 5,
        atk = listOf(55, 65),
        def = 20,
        range = listOf(1, 1),
        weapon = Weapon("dungeon", 118, 22, 12),
        canCapture = true,
        slow = true,
        noBuy = true,
        commander = true,
        desc = "Северный вождь. Его удар сковывает.",
    ),
    "witch" to UnitDef(
        name = "Ильва",
        sprite = 100,
        cost = 0,
        move = 5,
        atk = listOf(50, 60),
        def = 15,
        range = listOf(1, 2),
        weapon = Weapon("dungeon", 130, 24, 10),
        canCapture = true,
        poison = true,
        noBuy = true,
        commander = true,
        desc = "Болотная ведьма. Её удар отравляет.",
    ),
)

// Порядок в меню покупки
val BUY_LIST: List<String> = listOf("soldier", "archer", "wolf", "healer", "undine", "bat", "spider", "shaman", "mage", "wisp", "golem", "catapult", "demon")

// Стороны. name - в кампании, label - в схватке, flip - смотрит влево (стоит справа или сверху)
class TeamDef(val name: String, val label: String, val color: String, val dark: String, val light: String, val flip: Boolean = false)

val TEAMS: Map<String, TeamDef> = linkedMapOf(
    "blue" to TeamDef("Королевство", "Синие", "#3a7bd5", "#1d3f75", "#9cc4ff"),
    "red" to TeamDef("Враг", "Красные", "#d83c3c", "#6e1a1a", "#ffa3a3", flip = true),
    "green" to TeamDef("Зелёные", "Зелёные", "#3fa34d", "#1d5226", "#a6e8ae", flip = true),
    "yellow" to TeamDef("Жёлтые", "Жёлтые", "#e0b53a", "#6e5414", "#ffe39a"),
)

// Спрайты местности и предметов (индексы в town.png / dungeon.png)
object TILE_SPRITES {
    val grass = listOf(0, 0, 0, 0, 1, 2)
    const val road = 25
    val trees = listOf(16, 28, 16, 28, 27)
    const val hill = 43
    const val village = 67
    const val villageWall = 86
    const val castle = 103
    const val grave = 64        // dungeon
    const val coin = 93
}
