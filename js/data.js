/* ============ DATA: местность, юниты, баланс ============ */
'use strict';

// Общие числа баланса
const BAL = {
  maxHp: 100,
  unitCap: 20,             // лимит юнитов на сторону
  incomeVillage: 30,
  incomeCastle: 50,
  healBuilding: 20,        // лечение на своём здании в начале хода
  healAura: 15,            // лечение от целительницы соседям
  xpPerLevel: 100,         // опыта на уровень
  xpKillBonus: 30,
  maxLevel: 9,
  levelAtk: 3,             // +атака за уровень
  levelDef: 2,             // +защита за уровень
  commanderReviveBase: 200,
  commanderRevivePerLevel: 100,
  graveTurns: 2,           // сколько полных ходов лежит могила
  poisonAtk: 10,           // яд: -атака
  poisonDef: 10,           // яд: -защита
  poisonTurns: 3,          // сколько своих ходов держится яд (лечение снимает сразу)
  slowMove: 2,             // сковывание (шаман): -очки хода на следующий свой ход
  wispAtk: 10,             // огонёк: +атака соседним союзникам
  waterDef: 15,            // водный юнит: +защита в воде и на мелководье
  vision: 3,               // туман войны: обзор юнита в клетках (летающим +1, на холме и горе +1)
  visionBuilding: 2,       // обзор своего здания
};

// Сложность: множитель дохода ИИ и случайный разброс оценок ходов ИИ (ошибки)
const DIFFICULTY = {
  easy:   { name: 'Лёгкая',  aiIncome: 0.7, aiNoise: 35 },
  normal: { name: 'Обычная', aiIncome: 1,   aiNoise: 0 },
  hard:   { name: 'Трудная', aiIncome: 1.3, aiNoise: 0 },
};

// Местность. cost: стоимость передвижения (Infinity - непроходимо), def: бонус защиты
const TERRAIN = {
  plain:    { name: 'Равнина', cost: 1, def: 5 },
  road:     { name: 'Дорога',  cost: 1, def: 0 },
  forest:   { name: 'Лес',     cost: 2, def: 10 },
  hill:     { name: 'Холм',    cost: 2, def: 15 },
  mountain: { name: 'Горы',    cost: 3, def: 20 },
  water:    { name: 'Вода',    cost: Infinity, def: 0, water: true },
  shallow:  { name: 'Мелководье', cost: 3, def: 0, water: true },
  bridge:   { name: 'Мост',    cost: 1, def: 0 },
  ruins:    { name: 'Руины',   cost: 1, def: 10 },
  village:  { name: 'Деревня', cost: 1, def: 15, building: true, income: BAL.incomeVillage },
  castle:   { name: 'Замок',   cost: 1, def: 20, building: true, income: BAL.incomeCastle },
};

// Символы карт миссий
const MAP_LEGEND = {
  '.': 'plain', ',': 'road', 'f': 'forest', 'h': 'hill', 'M': 'mountain',
  '~': 'water', '=': 'bridge', 'v': 'village', 'C': 'castle', 's': 'shallow', 'r': 'ruins',
};

// Юниты. sprite: индекс в листе dungeon.png (или town.png, если sheet: 'town'), proc - процедурный спрайт (assets.js)
// atk: [min, max], range: [min, max]
// weapon: оружие в руке (лист, индекс, позиция в сетке 48x48: фигурка рисуется в 3 раза крупнее, оружие в 2 раза)
const UNIT_DEFS = {
  soldier: {
    name: 'Мечник', sprite: 98, cost: 150, move: 4, atk: [50, 55], def: 5, range: [1, 1],
    weapon: { sheet: 'dungeon', idx: 104, x: 24, y: 14 },
    canCapture: true,
    desc: 'Дешёвая пехота. Захватывает деревни.',
  },
  archer: {
    name: 'Лучник', sprite: 112, cost: 250, move: 4, atk: [50, 55], def: 5, range: [1, 2],
    weapon: { sheet: 'town', idx: 118, x: 22, y: 12 },
    vsFlying: 25,
    desc: 'Стреляет на 1-2 клетки. Силён против летающих.',
  },
  healer: {
    name: 'Целительница', sprite: 99, cost: 300, move: 4, atk: [30, 40], def: 5, range: [1, 1],
    weapon: { sheet: 'dungeon', idx: 130, x: 24, y: 10 },
    aura: true,
    desc: 'В начале хода лечит соседних союзников.',
  },
  bat: {
    name: 'Нетопырь', sprite: 120, cost: 300, move: 6, atk: [45, 50], def: 5, range: [1, 1],
    flying: true,
    desc: 'Быстрый, летает над водой и горами. Не получает защиту от местности.',
  },
  mage: {
    name: 'Чародей', sprite: 84, cost: 400, move: 4, atk: [45, 50], def: 5, range: [1, 2],
    weapon: { sheet: 'dungeon', idx: 129, x: 24, y: 10 },
    canRaise: true,
    desc: 'Бьёт на 1-2 клетки. Поднимает призрака из могилы рядом.',
  },
  spider: {
    name: 'Паук', sprite: 122, cost: 350, move: 5, atk: [40, 45], def: 5, range: [1, 1],
    poison: true,
    desc: 'Отравляет цель: яд снижает атаку и защиту, пока его не вылечат.',
  },
  shaman: {
    name: 'Шаман', sprite: 86, cost: 350, move: 4, atk: [35, 40], def: 5, range: [1, 2],
    weapon: { sheet: 'dungeon', idx: 128, x: 24, y: 10 },
    slow: true,
    desc: 'Бьёт на 1-2 клетки. Сковывает цель: в свой следующий ход она пройдёт на 2 клетки меньше.',
  },
  wisp: {
    name: 'Огонёк', proc: 'wisp', cost: 450, move: 5, atk: [35, 40], def: 5, range: [1, 1],
    auraAtk: true, vsUndead: 30,
    desc: 'Усиливает атаку соседних союзников. Жжёт нежить.',
  },
  undine: {
    name: 'Водяной', sprite: 108, cost: 300, move: 4, atk: [50, 55], def: 10, range: [1, 1],
    swim: true,
    desc: 'Плавает по воде и мелководью, в воде крепче.',
  },
  golem: {
    name: 'Голем', sprite: 109, cost: 600, move: 3, atk: [60, 70], def: 20, range: [1, 1],
    weapon: { sheet: 'dungeon', idx: 117, x: 22, y: 20 },
    desc: 'Медленный и очень крепкий.',
  },
  catapult: {
    name: 'Катапульта', sprite: 57, sheet: 'town', cost: 700, move: 3, atk: [55, 70], def: 10, range: [2, 4],
    noMoveAttack: true, canDestroy: true,
    desc: 'Бьёт на 2-4 клетки, но не может стрелять после движения. Разрушает деревни.',
  },
  demon: {
    name: 'Демон', sprite: 110, cost: 1000, move: 6, atk: [70, 80], def: 20, range: [1, 1],
    flying: true,
    desc: 'Летающий и смертоносный.',
  },
  ghost: {
    name: 'Призрак', sprite: 121, cost: 0, move: 4, atk: [40, 50], def: 5, range: [1, 1],
    noBuy: true, undead: true,
    desc: 'Поднят чародеем из могилы.',
  },
  crystal: {
    name: 'Кристалл', proc: 'crystal', cost: 0, move: 3, atk: [0, 0], def: 15, range: [0, 0],
    noBuy: true, noAttack: true,
    desc: 'Святыня. Не сражается. Его нужно довести до цели.',
  },
  // Командиры: захватывают замки, воскресают за золото
  hero: {
    name: 'Ардан', sprite: 97, cost: 0, move: 5, atk: [55, 65], def: 15, range: [1, 1],
    weapon: { sheet: 'dungeon', idx: 106, x: 24, y: 12 },
    commander: true, canCapture: true, noBuy: true,
    desc: 'Командир. Захватывает замки.',
  },
  warlord: {
    name: 'Бран', sprite: 87, cost: 0, move: 5, atk: [55, 65], def: 15, range: [1, 1],
    weapon: { sheet: 'dungeon', idx: 119, x: 22, y: 12 },
    commander: true, canCapture: true, noBuy: true,
    desc: 'Вожак разбойников.',
  },
  necro: {
    name: 'Морвен', sprite: 111, cost: 0, move: 5, atk: [55, 65], def: 15, range: [1, 2],
    weapon: { sheet: 'dungeon', idx: 131, x: 24, y: 8 },
    commander: true, canCapture: true, canRaise: true, noBuy: true, undead: true,
    desc: 'Тёмный жрец. Поднимает мёртвых.',
  },
  haar: {
    name: 'Хаар', sprite: 96, cost: 0, move: 5, atk: [55, 65], def: 20, range: [1, 1],
    weapon: { sheet: 'dungeon', idx: 118, x: 22, y: 12 },
    commander: true, canCapture: true, slow: true, noBuy: true,
    desc: 'Северный вождь. Его удар сковывает.',
  },
  witch: {
    name: 'Ильва', sprite: 100, cost: 0, move: 5, atk: [50, 60], def: 15, range: [1, 2],
    weapon: { sheet: 'dungeon', idx: 130, x: 24, y: 10 },
    commander: true, canCapture: true, poison: true, noBuy: true,
    desc: 'Болотная ведьма. Её удар отравляет.',
  },
};

// Порядок в меню покупки
const BUY_LIST = ['soldier', 'archer', 'healer', 'undine', 'bat', 'spider', 'shaman', 'mage', 'wisp', 'golem', 'catapult', 'demon'];

// Стороны
const TEAMS = {
// name - в кампании, label - в схватке, flip - смотрит влево (стоит справа или сверху)
  blue:   { name: 'Королевство', label: 'Синие',   color: '#3a7bd5', dark: '#1d3f75', light: '#9cc4ff' },
  red:    { name: 'Враг',        label: 'Красные', color: '#d83c3c', dark: '#6e1a1a', light: '#ffa3a3', flip: true },
  green:  { name: 'Зелёные',     label: 'Зелёные', color: '#3fa34d', dark: '#1d5226', light: '#a6e8ae', flip: true },
  yellow: { name: 'Жёлтые',      label: 'Жёлтые',  color: '#e0b53a', dark: '#6e5414', light: '#ffe39a' },
};

// Спрайты местности и предметов (индексы в town.png / dungeon.png)
const TILE_SPRITES = {
  grass: [0, 0, 0, 0, 1, 2],
  road: 25,
  trees: [16, 28, 16, 28, 27],
  hill: 43,
  village: 67,
  villageWall: 86,
  castle: 103,
  grave: 64,        // dungeon
  coin: 93,
};
