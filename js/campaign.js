/* ============ CAMPAIGN: миссии, диалоги, триггеры ============ */
// Карта: строки из символов MAP_LEGEND (data.js). Здания нейтральны, если не указаны в buildings.
// triggers: { when(st, ctx) -> bool, say: [...], spawn: [...] }, ctx = { phase: 'turn' | 'events', events }
// check(st) -> 'win' | 'lose' | null дополняет общие правила (гибель Ардана = поражение).
'use strict';

const SPEAKERS = {
  hero:   { name: 'Ардан',     sprite: 97,  team: 'blue' },
  lira:   { name: 'Лира',      sprite: 99,  team: 'blue' },
  scout:  { name: 'Разведчик', sprite: 98,  team: 'blue' },
  elder:  { name: 'Староста',  sprite: 100, team: 'blue' },
  bandit: { name: 'Разбойник', sprite: 85,  team: 'red' },
  bran:   { name: 'Бран',      sprite: 87,  team: 'red' },
  morven: { name: 'Морвен',    sprite: 111, team: 'red' },
};

const onTurn = (n, team = 'blue') => (st, ctx) => ctx.phase === 'turn' && st.turn === n && st.active === team;
const onEvent = pred => (st, ctx) => ctx.phase === 'events' && ctx.events.some(e => pred(e, st));

const MISSIONS = [
  {
    id: 'm1',
    title: 'Пограничная застава',
    goal: 'Разбейте всех разбойников. Ардан должен выжить.',
    map: [
      'ff..hh..ff',
      'f..,,,v..f',
      '..,,..,,..',
      '.,..ff..,.',
      '.,..ff..,h',
      '.,......,.',
      'h,.v..f.,.',
      '.,,...ff,.',
      '..,....,,.',
      'f.,,,,,,.f',
      'ff...,...f',
      'f....,..hh',
      'ff..,,...f',
    ],
    teams: { blue: { gold: 0 }, red: { gold: 0, ai: true } },
    buildings: [{ x: 6, y: 1, team: 'red' }],
    units: [
      { type: 'hero', team: 'blue', x: 5, y: 11, commander: true },
      { type: 'soldier', team: 'blue', x: 4, y: 12 },
      { type: 'soldier', team: 'blue', x: 6, y: 11 },
      { type: 'archer', team: 'blue', x: 3, y: 11 },
      { type: 'soldier', team: 'red', x: 6, y: 1, hold: true },
      { type: 'soldier', team: 'red', x: 4, y: 2 },
      { type: 'soldier', team: 'red', x: 7, y: 3 },
      { type: 'archer', team: 'red', x: 8, y: 0, hold: true },
    ],
    intro: [
      { who: 'scout', text: 'Сэр Ардан! Разбойники заняли деревню на севере и грабят каждого, кто идёт по тракту.' },
      { who: 'hero', text: 'Значит, пора им напомнить, чья это земля. Отряд, за мной!' },
      { who: 'scout', text: 'Выберите воина, чтобы увидеть, куда он может пойти. Красным подсвечены цели для атаки.' },
      { who: 'scout', text: 'Лес и холмы дают защиту. Мечники умеют захватывать деревни: встаньте на неё и выберите «Захват».' },
    ],
    triggers: [
      {
        when: onEvent(e => e.type === 'capture' && e.team === 'blue'),
        say: [{ who: 'elder', text: 'Спасибо, рыцарь! Каждая деревня под вашей защитой приносит золото в казну каждый ход.' }],
      },
      {
        when: onEvent(e => e.type === 'levelup' && e.team !== 'red'),
        say: [{ who: 'hero', text: 'Опыт закаляет. С каждым уровнем воин бьёт сильнее и держит удар лучше.' }],
      },
    ],
    outro: [
      { who: 'bandit', text: 'Пощадите! Это Бран велел нам... Он собирает людей за рекой!' },
      { who: 'hero', text: 'Бран? Тогда идём к реке. Этот край будет спокоен.' },
    ],
  },

  {
    id: 'm2',
    title: 'Деревни у реки',
    goal: 'Захватите замок Брана или разбейте его войско. В своём замке можно нанимать воинов.',
    map: [
      'h..v..f.,Ch',
      '..,,,,,,,.v',
      '.f,...ff...',
      '.,,.h...,.f',
      '.,..h.v.,..',
      '.,......,ff',
      '~=~~~~~~=~~',
      '~=~~~~~~=~~',
      'f,..ff..,..',
      '.,.v....,..',
      '.,,,,,,,,.h',
      'f..f...,..v',
      'hh....,,...',
      '.v.,,,,...f',
      'ff.C,...hhf',
    ],
    teams: {
      blue: { gold: 400 },
      red: { gold: 400, ai: true, buy: ['soldier', 'archer', 'bat', 'healer'] },
    },
    buildings: [
      { x: 3, y: 14, team: 'blue' }, { x: 1, y: 13, team: 'blue' },
      { x: 9, y: 0, team: 'red' }, { x: 10, y: 1, team: 'red' },
    ],
    units: [
      { type: 'hero', team: 'blue', x: 4, y: 14, commander: true },
      { type: 'soldier', team: 'blue', x: 3, y: 13 },
      { type: 'archer', team: 'blue', x: 2, y: 14 },
      { type: 'warlord', team: 'red', x: 8, y: 0 },
      { type: 'soldier', team: 'red', x: 9, y: 1 },
      { type: 'soldier', team: 'red', x: 7, y: 2 },
      { type: 'archer', team: 'red', x: 10, y: 2 },
    ],
    intro: [
      { who: 'hero', text: 'Этот замок у реки снова наш. Отсюда можно нанимать воинов.' },
      { who: 'scout', text: 'Нажмите на свой замок, когда он свободен, чтобы нанять отряд. Золото приходит от замков и деревень в начале хода.' },
      { who: 'bran', text: 'Рыцарь из столицы? Ха! Здесь мои деревни и моя река. Ребята, встречайте гостя!' },
    ],
    triggers: [
      {
        when: onEvent(e => e.type === 'death' && e.unitType === 'warlord'),
        say: [{ who: 'bran', text: 'Проклятье... Я ещё вернусь, пока цел мой замок!' },
              { who: 'scout', text: 'Командира можно вернуть за золото в замке. Захватите замок Брана, и ему некуда будет вернуться!' }],
      },
      {
        when: onTurn(4),
        say: [{ who: 'scout', text: 'Нетопыри Брана летают над рекой. Лучники бьют летающих особенно больно.' }],
      },
    ],
    check(st) {
      if (ownerAt(st, 9, 0) === 'blue') return 'win';
      return null;
    },
    outro: [
      { who: 'bran', text: 'Ладно, ладно! Сдаюсь! Это всё Морвен, жрец из Серых холмов. Он платил нам мертвецким золотом!' },
      { who: 'hero', text: 'Мертвецким? Похоже, беды только начинаются.' },
    ],
  },

  {
    id: 'm3',
    title: 'Мост на Серой реке',
    goal: 'Продержитесь 12 ходов. Не отдайте свой замок, Ардан должен выжить.',
    turnLimit: 12,
    map: [
      'MM..C..hhMM',
      'M.,,,,,,..M',
      'h.,.ff.,.vh',
      '.v,..f.,...',
      '..,....,,,.',
      'f.,.hh...,f',
      '~~=~~~~~~=~',
      '~~=~~~~~~=~',
      '..,...h..,.',
      '.h,..v...,.',
      '..,,,,,,,,.',
      'f....,...ff',
      '.v...,..v..',
      'hh...C...hh',
      'MMh.....hMM',
    ],
    teams: {
      blue: { gold: 600 },
      red: { gold: 300, ai: true, buy: ['bat', 'mage', 'golem', 'soldier'] },
    },
    buildings: [
      { x: 5, y: 13, team: 'blue' }, { x: 5, y: 9, team: 'blue' },
      { x: 4, y: 0, team: 'red' }, { x: 9, y: 2, team: 'red' }, { x: 1, y: 3, team: 'red' },
    ],
    units: [
      { type: 'hero', team: 'blue', x: 5, y: 12, commander: true },
      { type: 'soldier', team: 'blue', x: 2, y: 8 },
      { type: 'soldier', team: 'blue', x: 9, y: 8 },
      { type: 'archer', team: 'blue', x: 4, y: 10 },
      { type: 'mage', team: 'blue', x: 6, y: 10 },
      { type: 'necro', team: 'red', x: 5, y: 1, hold: true, tag: 'boss' },
      { type: 'ghost', team: 'red', x: 2, y: 4 },
      { type: 'ghost', team: 'red', x: 8, y: 4 },
      { type: 'ghost', team: 'red', x: 5, y: 3 },
      { type: 'bat', team: 'red', x: 6, y: 2 },
      { type: 'golem', team: 'red', x: 3, y: 1 },
    ],
    graves: [{ x: 3, y: 4 }, { x: 6, y: 4 }],
    intro: [
      { who: 'hero', text: 'Серая река. За ней холмы Морвена. Мосты нужно удержать любой ценой.' },
      { who: 'morven', text: 'Сколько живых пришло умереть у моей реки... Каждый павший станет моим воином.' },
      { who: 'scout', text: 'Павшие оставляют могилы. Чародей может поднять из могилы призрака, и враг тоже!' },
    ],
    triggers: [
      {
        when: onTurn(3),
        say: [{ who: 'lira', text: 'Ардан! Король прислал подмогу. Я подлечу раненых, держите меня рядом с ними.' }],
        spawn: [
          { type: 'healer', team: 'blue', x: 3, y: 14 },
          { type: 'soldier', team: 'blue', x: 7, y: 14 },
        ],
      },
      {
        when: onTurn(6, 'red'),
        say: [{ who: 'morven', text: 'Восстань, дитя пепла!' }],
        spawn: [{ type: 'demon', team: 'red', x: 4, y: 1 }],
      },
      {
        when: onTurn(10),
        say: [{ who: 'hero', text: 'Ещё немного! Рассвет близко, нежить слабеет.' }],
      },
    ],
    check(st) {
      if (ownerAt(st, 5, 13) !== 'blue') return 'lose';
      if (st.turn > 12 && st.active === 'blue') return 'win';
      if (ownerAt(st, 4, 0) === 'blue') return 'win';
      return null;
    },
    outro: [
      { who: 'morven', text: 'Солнце... Довольно. Мы ещё встретимся, рыцарь, в моей цитадели.' },
      { who: 'lira', text: 'Он отступил за холмы. Нужно идти следом, пока он не собрал новое войско.' },
    ],
  },

  {
    id: 'm4',
    title: 'Цитадель Морвена',
    goal: 'Победите Морвена. Ардан должен выжить.',
    map: [
      'MMh.,C,.hMM',
      'Mv..,,,..vM',
      'h..f.,.f..h',
      '.f..,,,..f.',
      '...,,.,,...',
      'Mh.,...,.hM',
      '~~~=~~~=~~~',
      '.v.,...,.v.',
      'f..,.h.,..f',
      '..,,...,,..',
      '.h,..v..,h.',
      '..,,,,,,,..',
      'f.v..,..v.f',
      'hh...C...hh',
      'MMM.....MMM',
    ],
    teams: {
      blue: { gold: 700 },
      red: { gold: 500, ai: true, buy: ['bat', 'mage', 'golem', 'demon', 'archer'] },
    },
    buildings: [
      { x: 5, y: 13, team: 'blue' },
      { x: 5, y: 0, team: 'red' }, { x: 1, y: 1, team: 'red' }, { x: 9, y: 1, team: 'red' },
    ],
    units: [
      { type: 'hero', team: 'blue', x: 5, y: 12, commander: true },
      { type: 'healer', team: 'blue', x: 4, y: 12 },
      { type: 'soldier', team: 'blue', x: 3, y: 11 },
      { type: 'soldier', team: 'blue', x: 7, y: 11 },
      { type: 'archer', team: 'blue', x: 6, y: 12 },
      { type: 'mage', team: 'blue', x: 5, y: 11 },
      { type: 'necro', team: 'red', x: 5, y: 1, hold: true, tag: 'boss' },
      { type: 'golem', team: 'red', x: 3, y: 5, hold: true },
      { type: 'golem', team: 'red', x: 7, y: 5, hold: true },
      { type: 'mage', team: 'red', x: 5, y: 3 },
      { type: 'ghost', team: 'red', x: 2, y: 4 },
      { type: 'ghost', team: 'red', x: 8, y: 4 },
      { type: 'bat', team: 'red', x: 0, y: 3 },
      { type: 'bat', team: 'red', x: 10, y: 3 },
      { type: 'archer', team: 'red', x: 6, y: 2, hold: true },
    ],
    graves: [{ x: 4, y: 2 }, { x: 6, y: 4 }, { x: 3, y: 3 }],
    intro: [
      { who: 'lira', text: 'Цитадель Морвена. Мосты сторожат големы, их не обойти без потерь.' },
      { who: 'hero', text: 'Наймём катапульту: она бьёт издалека, хоть и не стреляет после движения.' },
      { who: 'morven', text: 'Ты пришёл сам, рыцарь. Как любезно. Твоё тело послужит мне лучше, чем служило королю.' },
    ],
    triggers: [
      {
        when: onEvent(e => e.type === 'hit' && e.toTag === 'boss'),
        say: [{ who: 'morven', text: 'Ты посмел ранить меня?! Мертвецы, ко мне!' }],
      },
      {
        when: onTurn(8, 'red'),
        say: [{ who: 'morven', text: 'Из глубин холмов, ко мне, мои летуны!' }],
        spawn: [{ type: 'bat', team: 'red', x: 1, y: 2 }, { type: 'bat', team: 'red', x: 9, y: 2 }],
      },
    ],
    check(st) {
      if (st.flags.bossDead) return 'win';
      return null;
    },
    outro: [
      { who: 'morven', text: 'Нет... тьма... не отпускает...' },
      { who: 'lira', text: 'Всё кончено. Мертвые наконец спят спокойно.' },
      { who: 'hero', text: 'Возвращаемся домой. Королевство снова в безопасности.' },
    ],
  },
];

const missionById = id => MISSIONS.find(m => m.id === id);

// Победа/поражение: общие правила кампании + check() миссии
function evaluateOutcome(st, mission) {
  if (st.teams.blue.commanderDead) return 'lose';
  const own = mission.check && mission.check(st);
  if (own) return own;
  if (isEliminated(st, 'red')) return 'win';
  if (isEliminated(st, 'blue')) return 'lose';
  return null;
}

// Поставить юнита в ближайшую свободную проходимую клетку
function spawnNear(st, type, team, x, y) {
  const fake = { type, team };
  const seen = new Set(), q = [{ x, y }];
  while (q.length) {
    const c = q.shift(), k = key(c.x, c.y);
    if (seen.has(k) || !inBounds(st, c.x, c.y)) continue;
    seen.add(k);
    if (!unitAt(st, c.x, c.y) && isFinite(moveCost(st, fake, c.x, c.y))) return spawnUnit(st, type, team, c.x, c.y);
    for (const [dx, dy] of DIRS) q.push({ x: c.x + dx, y: c.y + dy });
  }
  return null;
}
