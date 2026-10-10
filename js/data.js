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
};

// Местность. cost: стоимость передвижения (Infinity - непроходимо), def: бонус защиты
const TERRAIN = {
  plain:    { name: 'Равнина', cost: 1, def: 5 },
  road:     { name: 'Дорога',  cost: 1, def: 0 },
  forest:   { name: 'Лес',     cost: 2, def: 10 },
  hill:     { name: 'Холм',    cost: 2, def: 15 },
  mountain: { name: 'Горы',    cost: 3, def: 20 },
  water:    { name: 'Вода',    cost: Infinity, def: 0 },
  bridge:   { name: 'Мост',    cost: 1, def: 0 },
  village:  { name: 'Деревня', cost: 1, def: 15, building: true, income: BAL.incomeVillage },
  castle:   { name: 'Замок',   cost: 1, def: 20, building: true, income: BAL.incomeCastle },
};

// Символы карт миссий
const MAP_LEGEND = {
  '.': 'plain', ',': 'road', 'f': 'forest', 'h': 'hill', 'M': 'mountain',
  '~': 'water', '=': 'bridge', 'v': 'village', 'C': 'castle',
};

// Юниты. sprite: индекс в листе dungeon.png (или town.png, если sheet: 'town')
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
  golem: {
    name: 'Голем', sprite: 109, cost: 600, move: 3, atk: [60, 70], def: 20, range: [1, 1],
    weapon: { sheet: 'dungeon', idx: 117, x: 22, y: 20 },
    desc: 'Медленный и очень крепкий.',
  },
  catapult: {
    name: 'Катапульта', sprite: 57, sheet: 'town', cost: 700, move: 3, atk: [55, 70], def: 10, range: [2, 4],
    noMoveAttack: true,
    desc: 'Бьёт на 2-4 клетки, но не может стрелять после движения.',
  },
  demon: {
    name: 'Демон', sprite: 110, cost: 1000, move: 6, atk: [70, 80], def: 20, range: [1, 1],
    flying: true,
    desc: 'Летающий и смертоносный.',
  },
  ghost: {
    name: 'Призрак', sprite: 121, cost: 0, move: 4, atk: [40, 50], def: 5, range: [1, 1],
    noBuy: true,
    desc: 'Поднят чародеем из могилы.',
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
    commander: true, canCapture: true, canRaise: true, noBuy: true,
    desc: 'Тёмный жрец. Поднимает мёртвых.',
  },
};

// Порядок в меню покупки
const BUY_LIST = ['soldier', 'archer', 'healer', 'bat', 'mage', 'golem', 'catapult', 'demon'];

// Стороны
const TEAMS = {
  blue: { name: 'Королевство', color: '#3a7bd5', dark: '#1d3f75', light: '#9cc4ff' },
  red:  { name: 'Враг',        color: '#d83c3c', dark: '#6e1a1a', light: '#ffa3a3' },
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
