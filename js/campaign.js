/* ============ CAMPAIGN: миссии, диалоги, триггеры ============ */
// Карта: строки из символов MAP_LEGEND (data.js). Здания нейтральны, если не указаны в buildings.
// triggers: { when(st, ctx) -> bool, say: [...], spawn: [...] }, ctx = { phase: 'turn' | 'events', events }
// check(st) -> 'win' | 'lose' | null дополняет общие правила (гибель Ардана = поражение).
'use strict';

const SPEAKERS = {
  hero:   { name: 'Ардан',     sprite: 97,  team: 'blue' },
  lira:   { name: 'Лира',      sprite: 99,  team: 'blue' },
  scout:  { name: 'Разведчик', sprite: 98,  team: 'blue' },
  elder:  { name: 'Староста',  sprite: 86,  team: 'blue' },
  witch:  { name: 'Ильва',     sprite: 100, team: 'red' },
  bandit: { name: 'Разбойник', sprite: 85,  team: 'red' },
  bran:   { name: 'Бран',      sprite: 87,  team: 'red' },
  morven: { name: 'Морвен',    sprite: 111, team: 'red' },
  sif:    { name: 'Сиф',       sprite: 88,  team: 'blue' },
  haar:   { name: 'Хаар',      sprite: 96,  team: 'red' },
};

const onTurn = (n, team = 'blue') => (st, ctx) => ctx.phase === 'turn' && st.turn === n && st.active === team;
const onEvent = pred => (st, ctx) => ctx.phase === 'events' && ctx.events.some(e => pred(e, st));

const MISSIONS = [
  {
    id: 'm1',
    par: 8,
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
    par: 16,
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
    par: 13,
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
    par: 10,
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

  // ===== Глава 2: болотная ведьма =====
  {
    id: 'm5',
    par: 14,
    title: 'Болотная тропа',
    goal: 'Разбейте всех врагов на болоте. Ардан должен выжить.',
    map: [
      'ff.hh.v.hff',
      'f..,,,,..vf',
      '.ss..s..ss.',
      's~s.ss.s~ss',
      '.ss.s~s.ss.',
      '..,.ssf.,..',
      '.f,,..,,h..',
      '.sss.f.ss..',
      's~s..,..sss',
      '.s..f.,h...',
      '..,,,,,,.v.',
      'f..h.,.v..f',
      'hf.......ff',
    ],
    teams: { blue: { gold: 0 }, red: { gold: 0, ai: true } },
    buildings: [{ x: 6, y: 0, team: 'red' }, { x: 9, y: 1, team: 'red' }],
    units: [
      { type: 'hero', team: 'blue', x: 5, y: 12, commander: true },
      { type: 'healer', team: 'blue', x: 4, y: 12 },
      { type: 'soldier', team: 'blue', x: 3, y: 11 },
      { type: 'soldier', team: 'blue', x: 7, y: 11 },
      { type: 'archer', team: 'blue', x: 6, y: 12 },
      { type: 'spider', team: 'red', x: 3, y: 3 },
      { type: 'spider', team: 'red', x: 7, y: 3 },
      { type: 'spider', team: 'red', x: 5, y: 6, hold: true },
      { type: 'soldier', team: 'red', x: 2, y: 1 },
      { type: 'soldier', team: 'red', x: 6, y: 0, hold: true },
      { type: 'archer', team: 'red', x: 9, y: 1, hold: true },
      { type: 'spider', team: 'red', x: 8, y: 5 },
      { type: 'archer', team: 'red', x: 3, y: 1 },
    ],
    intro: [
      { who: 'hero', text: 'Морвен пал, но с юга, из болот, тянет гнилью. Земля вокруг мёртвая.' },
      { who: 'lira', text: 'Это работа болотной ведьмы Ильвы. Говорят, её нельзя убить обычной сталью.' },
      { who: 'scout', text: 'Мелководье тяжело для пехоты: каждая клетка стоит три хода. Идите по тропам.' },
    ],
    triggers: [
      {
        when: onEvent(e => e.type === 'poison'),
        say: [{ who: 'lira', text: 'Яд пауков! Отравленный теряет силу и защиту. Подойдите ко мне или встаньте на свою деревню, и яд сойдёт.' }],
      },
      {
        when: onTurn(3),
        say: [{ who: 'hero', text: 'Что за свет над тропой?' },
              { who: 'lira', text: 'Огонёк! Он с нами. Соседние союзники рядом с ним бьют сильнее, а нежить он жжёт.' }],
        spawn: [{ type: 'wisp', team: 'blue', x: 5, y: 12 }],
      },
    ],
    outro: [
      { who: 'bandit', text: 'Ильва платит нам болотным золотом... её логово в самом сердце топи.' },
      { who: 'hero', text: 'Тогда идём в топь. Лира, держись рядом.' },
    ],
  },

  {
    id: 'm6',
    par: 12,
    title: 'Кристалл Света',
    goal: 'Доведите Кристалл до алтаря (золотая рамка) за 14 ходов. Если Кристалл погибнет, миссия провалена.',
    turnLimit: 14,
    map: [
      'MMh.,r,.hMM',
      'Mf..,,,..fM',
      'h..f.,.f..h',
      '.f.,,,,,.f.',
      '..,..f..,..',
      '.h,.....,h.',
      '~~~=~~~=~~~',
      '~~~=~~~=~~~',
      '.f,.....,f.',
      '..,,.h.,,..',
      '.h..,,,..h.',
      'f..v.,.v..f',
      '..,,,,,,,..',
      'hh...C...hh',
      'MM.......MM',
    ],
    marks: [{ x: 5, y: 0 }],
    teams: {
      blue: { gold: 300 },
      red: { gold: 200, ai: true, buy: ['spider', 'archer', 'soldier'] },
    },
    buildings: [{ x: 5, y: 13, team: 'blue' }],
    units: [
      { type: 'hero', team: 'blue', x: 5, y: 12, commander: true },
      { type: 'crystal', team: 'blue', x: 5, y: 11, goal: { x: 5, y: 0 } },
      { type: 'healer', team: 'blue', x: 4, y: 12 },
      { type: 'soldier', team: 'blue', x: 3, y: 11 },
      { type: 'soldier', team: 'blue', x: 7, y: 11 },
      { type: 'archer', team: 'blue', x: 6, y: 12 },
      { type: 'mage', team: 'blue', x: 4, y: 11 },
      { type: 'spider', team: 'red', x: 3, y: 5 },
      { type: 'spider', team: 'red', x: 7, y: 5 },
      { type: 'soldier', team: 'red', x: 2, y: 3 },
      { type: 'soldier', team: 'red', x: 8, y: 3 },
      { type: 'archer', team: 'red', x: 4, y: 1, hold: true },
      { type: 'archer', team: 'red', x: 6, y: 1, hold: true },
    ],
    intro: [
      { who: 'elder', text: 'Рыцарь, в нашем храме хранился Кристалл Света. Только его сияние очищает топь от яда.' },
      { who: 'elder', text: 'Отнесите его на древний алтарь на севере. Он отмечен золотой рамкой.' },
      { who: 'lira', text: 'Кристалл не сражается и ходит медленно. Если его разобьют, надежды не останется. Прикрывайте его!' },
    ],
    triggers: [
      {
        when: onTurn(6),
        say: [{ who: 'scout', text: 'Впереди два моста через реку. Враги ждут за ними. Не торопите Кристалл вперёд бойцов.' }],
      },
    ],
    check(st) {
      const c = st.units.find(u => u.type === 'crystal' && u.team === 'blue');
      if (c && c.x === 5 && c.y === 0) return 'win';
      if (st.flags.crystalLost) return 'lose';
      if (st.turn > 14 && st.active === 'blue') return 'lose';
      return null;
    },
    outro: [
      { who: 'elder', text: 'Свет вернулся! Смотрите, топь светлеет, а чары слабеют.' },
      { who: 'lira', text: 'Ильва теперь знает, что мы идём. Дальше её путь лежит через устье реки.' },
    ],
  },

  {
    id: 'm7',
    par: 14,
    title: 'Устье реки',
    goal: 'Захватите замок красных или разбейте их войско. В вашем замке можно нанимать воинов.',
    map: [
      'hh.,C,..hhM',
      '.v,,,,,,.v.',
      'ss.f..f.sss',
      '~ss..r.ss~~',
      '~~s.ff.s~~~',
      '~~ss...ss~~',
      '~~~s.r.s~~~',
      '~~ss...ss~~',
      'rss..h..ss~',
      '.ss.,,,.ss.',
      '.v.f,.,f.v.',
      '..,,,.,,...',
      'f..v...v..f',
      'hh..C....hh',
      'MMf.....fMM',
    ],
    teams: {
      blue: { gold: 600 },
      red: { gold: 200, ai: true, buy: ['undine', 'catapult', 'soldier', 'archer'] },
    },
    buildings: [
      { x: 4, y: 13, team: 'blue' }, { x: 3, y: 12, team: 'blue' }, { x: 7, y: 12, team: 'blue' },
      { x: 1, y: 10, team: 'blue' }, { x: 9, y: 10, team: 'blue' },
      { x: 4, y: 0, team: 'red' }, { x: 1, y: 1, team: 'red' }, { x: 9, y: 1, team: 'red' },
    ],
    units: [
      { type: 'hero', team: 'blue', x: 4, y: 12, commander: true },
      { type: 'healer', team: 'blue', x: 5, y: 12 },
      { type: 'soldier', team: 'blue', x: 3, y: 11 },
      { type: 'soldier', team: 'blue', x: 6, y: 11 },
      { type: 'archer', team: 'blue', x: 5, y: 11 },
      { type: 'warlord', team: 'red', x: 4, y: 1, hold: true },
      { type: 'undine', team: 'red', x: 2, y: 3 },
      { type: 'undine', team: 'red', x: 7, y: 3, hold: true },
      { type: 'catapult', team: 'red', x: 5, y: 2, hold: true },
      { type: 'archer', team: 'red', x: 6, y: 2, hold: true },
    ],
    intro: [
      { who: 'scout', text: 'В устье реки хозяйничают слуги Ильвы. У них водяные: те плавают по воде и мелководью и там крепки.' },
      { who: 'hero', text: 'Нам тоже нужны водяные. Наймём их в замке, они пройдут там, где пехота увязнет.' },
      { who: 'lira', text: 'Осторожно: красные катапульты разрушают деревни, а на их месте остаются руины.' },
    ],
    triggers: [
      {
        when: onEvent(e => e.type === 'destroy'),
        say: [{ who: 'scout', text: 'Деревню разрушили! Мечник или Ардан могут встать на руины и выбрать «Отстроить»: деревня снова станет нашей.' }],
      },
      {
        when: onTurn(5),
        say: [{ who: 'hero', text: 'Руины тоже дают защиту. Можно укрыться за их стенами.' }],
      },
    ],
    check(st) {
      if (ownerAt(st, 4, 0) === 'blue') return 'win';
      return null;
    },
    outro: [
      { who: 'hero', text: 'Устье наше. Дорога к топям открыта.' },
      { who: 'lira', text: 'Ильва ждёт нас в своём логове. Там нас ждёт последний бой.' },
    ],
  },

  {
    id: 'm8',
    par: 14,
    title: 'Логово Ильвы',
    goal: 'Победите ведьму Ильву. Ардан должен выжить.',
    map: [
      'ffh..C..hff',
      'fs.,,,,,.sf',
      's~s..f..s~s',
      '~~ss.,.ss~~',
      's~s.f.f.s~s',
      '.ss.,,,.ss.',
      '..r.,.,.r..',
      'f.v.,.,.v.f',
      '.h.,,.,,.h.',
      '..f.....f..',
      'h..v...v..h',
      'hh...C...hh',
      'MM.......MM',
    ],
    teams: {
      blue: { gold: 1000 },
      red: { gold: 300, ai: true, buy: ['spider', 'undine', 'golem', 'demon'] },
    },
    buildings: [
      { x: 5, y: 11, team: 'blue' }, { x: 3, y: 10, team: 'blue' }, { x: 7, y: 10, team: 'blue' },
      { x: 5, y: 0, team: 'red' },
    ],
    units: [
      { type: 'hero', team: 'blue', x: 5, y: 10, commander: true },
      { type: 'healer', team: 'blue', x: 4, y: 10 },
      { type: 'wisp', team: 'blue', x: 6, y: 10 },
      { type: 'soldier', team: 'blue', x: 4, y: 9 },
      { type: 'soldier', team: 'blue', x: 6, y: 9 },
      { type: 'archer', team: 'blue', x: 5, y: 9 },
      { type: 'witch', team: 'red', x: 5, y: 1, hold: true, tag: 'boss' },
      { type: 'golem', team: 'red', x: 4, y: 4, hold: true },
      { type: 'spider', team: 'red', x: 3, y: 5 },
      { type: 'spider', team: 'red', x: 7, y: 5 },
      { type: 'golem', team: 'red', x: 6, y: 4, hold: true },
      { type: 'undine', team: 'red', x: 1, y: 3 },
      { type: 'undine', team: 'red', x: 9, y: 3 },
      { type: 'archer', team: 'red', x: 5, y: 3, hold: true },
    ],
    intro: [
      { who: 'lira', text: 'Вот и логово. Гниль идёт отсюда, из самого сердца топи.' },
      { who: 'witch', text: 'Рыцарь и его целительница. Мои пауки давно ждут гостей. Топь вас не отпустит.' },
      { who: 'hero', text: 'Держитесь вместе: яд снимает Лира или родная деревня. Огонёк усилит тех, кто рядом.' },
    ],
    triggers: [
      {
        when: onEvent(e => e.type === 'hit' && e.toTag === 'boss'),
        say: [{ who: 'witch', text: 'Ты посмел ранить меня?! Топь, подними своих детей!' }],
      },
      {
        when: onTurn(7, 'red'),
        say: [{ who: 'witch', text: 'Из глубины, ко мне!' }],
        spawn: [{ type: 'spider', team: 'red', x: 4, y: 1 }, { type: 'spider', team: 'red', x: 6, y: 1 }],
      },
    ],
    check(st) {
      if (st.flags.bossDead) return 'win';
      return null;
    },
    outro: [
      { who: 'witch', text: 'Топь... вернётся... она всегда возвращается...' },
      { who: 'lira', text: 'Воздух чист. Яд уходит из земли.' },
      { who: 'hero', text: 'Эта земля свободна. Но с севера дует холодный ветер...' },
    ],
  },

  // ===== Глава 3: север =====
  {
    id: 'm9',
    par: 12,
    title: 'Снежная дорога',
    goal: 'Разбейте передовой отряд северян. Ардан должен выжить.',
    map: [
      'MMh.v.,.hMM',
      'Mf..,,,..fM',
      'h..f.,.f..h',
      '.v..,,,..v.',
      'ff.,.h.,.ff',
      '...,...,...',
      'M..,.M.,..M',
      '.f.,,,,,.f.',
      '...v...v...',
      'hf.......fh',
      '..,,,,,,,..',
      '.f...v...f.',
      'MM..,,,..MM',
    ],
    teams: { blue: { gold: 0 }, red: { gold: 0, ai: true } },
    buildings: [{ x: 4, y: 0, team: 'red' }, { x: 1, y: 3, team: 'red' }, { x: 9, y: 3, team: 'red' }],
    units: [
      { type: 'hero', team: 'blue', x: 5, y: 12, commander: true },
      { type: 'soldier', team: 'blue', x: 4, y: 12 },
      { type: 'soldier', team: 'blue', x: 6, y: 12 },
      { type: 'archer', team: 'blue', x: 5, y: 11 },
      { type: 'healer', team: 'blue', x: 3, y: 11 },
      { type: 'shaman', team: 'red', x: 4, y: 3 },
      { type: 'shaman', team: 'red', x: 6, y: 3 },
      { type: 'soldier', team: 'red', x: 3, y: 5 },
      { type: 'soldier', team: 'red', x: 7, y: 5 },
      { type: 'archer', team: 'red', x: 5, y: 2, hold: true },
      { type: 'soldier', team: 'red', x: 4, y: 0, hold: true },
      { type: 'bat', team: 'red', x: 8, y: 1 },
    ],
    intro: [
      { who: 'hero', text: 'Прошла зима после победы над Ильвой. А теперь с севера бегут люди.' },
      { who: 'sif', text: 'Я Сиф, из северных деревень. Вождь Хаар ведёт орду на юг, и его шаманы сковывают ноги воинам.' },
      { who: 'hero', text: 'Тогда встретим его передовой отряд здесь, на снежной дороге.' },
    ],
    triggers: [
      {
        when: onEvent(e => e.type === 'slow'),
        say: [{ who: 'sif', text: 'Скован! В свой следующий ход этот воин пройдёт на две клетки меньше. Бейте шаманов первыми.' }],
      },
      {
        when: onTurn(2),
        say: [{ who: 'sif', text: 'Не все шаманы служат Хаару. Мой брат пойдёт с вами, его посох тоже сковывает.' }],
        spawn: [{ type: 'shaman', team: 'blue', x: 5, y: 12 }],
      },
    ],
    outro: [
      { who: 'bandit', text: 'Хаар собрал всех северных разбойников. Даже Бран теперь служит ему!' },
      { who: 'hero', text: 'Бран? Старый знакомый. Значит, следующая цель - его форт.' },
    ],
  },

  {
    id: 'm10',
    par: 14,
    title: 'Форт Брана',
    goal: 'Захватите замок Брана командиром. Ардан должен выжить.',
    map: [
      'MMf.,C,.fMM',
      'Mh..,,,..hM',
      'f.v.,.,.v.f',
      '..ff,.,ff..',
      'h...,,,...h',
      '~~=~~~~~=~~',
      '.s.......s.',
      'f..h.v.h..f',
      '.,,,,,,,,,.',
      '.,.f...f.,.',
      'v,.......,v',
      '.,,..h..,,.',
      'f..,,,,,..f',
      'h..,.C.,..h',
      'MM.v...v.MM',
    ],
    teams: {
      blue: { gold: 800 },
      red: { gold: 200, ai: true, buy: ['shaman', 'soldier', 'archer', 'catapult'] },
    },
    buildings: [
      { x: 5, y: 13, team: 'blue' }, { x: 3, y: 14, team: 'blue' }, { x: 7, y: 14, team: 'blue' },
      { x: 5, y: 0, team: 'red' }, { x: 2, y: 2, team: 'red' }, { x: 8, y: 2, team: 'red' },
    ],
    units: [
      { type: 'hero', team: 'blue', x: 5, y: 12, commander: true },
      { type: 'soldier', team: 'blue', x: 4, y: 12 },
      { type: 'soldier', team: 'blue', x: 6, y: 12 },
      { type: 'shaman', team: 'blue', x: 5, y: 11 },
      { type: 'healer', team: 'blue', x: 3, y: 12 },
      { type: 'archer', team: 'blue', x: 7, y: 12 },
      { type: 'warlord', team: 'red', x: 5, y: 1, hold: true },
      { type: 'catapult', team: 'red', x: 4, y: 2, hold: true },
      { type: 'soldier', team: 'red', x: 2, y: 4 },
      { type: 'soldier', team: 'red', x: 8, y: 4 },
      { type: 'shaman', team: 'red', x: 5, y: 4 },
      { type: 'archer', team: 'red', x: 4, y: 3, hold: true },
    ],
    intro: [
      { who: 'bran', text: 'Ардан! Опять ты. Но теперь у меня стены и катапульты, а за спиной - Хаар!' },
      { who: 'lira', text: 'Катапульты бьют издалека, но после движения стрелять не могут. Подходите к ним вплотную.' },
      { who: 'hero', text: 'Мосты на флангах. Возьмём их и ударим по замку.' },
    ],
    triggers: [
      {
        when: onTurn(6, 'red'),
        say: [{ who: 'bran', text: 'Ребята, все на стены!' }],
        spawn: [{ type: 'soldier', team: 'red', x: 5, y: 1 }, { type: 'shaman', team: 'red', x: 4, y: 1 }],
      },
    ],
    check(st) {
      if (ownerAt(st, 5, 0) === 'blue') return 'win';
      return null;
    },
    outro: [
      { who: 'bran', text: 'Сдаюсь, опять сдаюсь! Хаар идёт к Ледяному мосту, хочет отрезать вас от дома.' },
      { who: 'sif', text: 'Если мост падёт, юг останется без защиты. Нужно спешить.' },
    ],
  },

  {
    id: 'm11',
    par: 11,
    title: 'Ледяной мост',
    goal: 'Продержитесь 10 ходов. Не отдайте свой замок, Ардан должен выжить.',
    turnLimit: 10,
    map: [
      'MM..,C,..MM',
      'M...,,,...M',
      '.f.v,.,v.f.',
      '..,,,.,,,..',
      'h.,..h..,.h',
      'MM,.MMM.,MM',
      '~~=~~~~~=~~',
      '~~=~~~~~=~~',
      'M.,.....,.M',
      '..,.f.f.,..',
      'v.,,,,,,,.v',
      '.h...,...h.',
      'f..f.,.f..f',
      'h.v..C..v.h',
      'MM.......MM',
    ],
    teams: {
      blue: { gold: 500 },
      red: { gold: 600, ai: true, buy: ['shaman', 'soldier', 'archer', 'golem', 'bat'] },
    },
    buildings: [
      { x: 5, y: 13, team: 'blue' }, { x: 2, y: 13, team: 'blue' }, { x: 8, y: 13, team: 'blue' },
      { x: 0, y: 10, team: 'blue' }, { x: 10, y: 10, team: 'blue' },
      { x: 5, y: 0, team: 'red' }, { x: 3, y: 2, team: 'red' }, { x: 7, y: 2, team: 'red' },
    ],
    units: [
      { type: 'hero', team: 'blue', x: 5, y: 12, commander: true },
      { type: 'soldier', team: 'blue', x: 2, y: 9 },
      { type: 'soldier', team: 'blue', x: 8, y: 9 },
      { type: 'archer', team: 'blue', x: 3, y: 10 },
      { type: 'archer', team: 'blue', x: 7, y: 10 },
      { type: 'shaman', team: 'blue', x: 5, y: 11 },
      { type: 'healer', team: 'blue', x: 4, y: 12 },
      { type: 'warlord', team: 'red', x: 5, y: 1, hold: true },
      { type: 'soldier', team: 'red', x: 2, y: 3 },
      { type: 'soldier', team: 'red', x: 8, y: 3 },
      { type: 'shaman', team: 'red', x: 4, y: 3 },
      { type: 'shaman', team: 'red', x: 6, y: 3 },
      { type: 'golem', team: 'red', x: 5, y: 4 },
      { type: 'archer', team: 'red', x: 2, y: 4 },
      { type: 'archer', team: 'red', x: 8, y: 4 },
    ],
    intro: [
      { who: 'sif', text: 'Хаар бросил к мосту всё войско. Если удержим переправы десять ходов, орда выдохнется.' },
      { who: 'hero', text: 'Два моста - две двери. Встанем у выходов и не пустим их на наш берег.' },
      { who: 'lira', text: 'Берегитесь нетопырей: им вода не помеха.' },
    ],
    triggers: [
      {
        when: onTurn(4, 'red'),
        say: [{ who: 'haar', text: 'Крылья севера, летите через реку!' }],
        spawn: [{ type: 'bat', team: 'red', x: 4, y: 1 }, { type: 'bat', team: 'red', x: 6, y: 1 }],
      },
      {
        when: onTurn(7, 'red'),
        say: [{ who: 'haar', text: 'Каменные стражи, ломайте их строй!' }],
        spawn: [{ type: 'golem', team: 'red', x: 5, y: 2 }, { type: 'shaman', team: 'red', x: 5, y: 3 }],
      },
    ],
    check(st) {
      if (ownerAt(st, 5, 13) !== 'blue') return 'lose';
      if (st.turn > 10 && st.active === 'blue') return 'win';
      if (ownerAt(st, 5, 0) === 'blue') return 'win';
      return null;
    },
    outro: [
      { who: 'haar', text: 'Отступаем к Трону! Пусть рыцарь придёт ко мне сам.' },
      { who: 'hero', text: 'Придём. Это последний поход.' },
    ],
  },

  {
    id: 'm12',
    par: 16,
    title: 'Трон Севера',
    goal: 'Победите вождя Хаара. Ардан должен выжить.',
    map: [
      'MMh.,C,.hMM',
      'Mh..,,,..hM',
      'f.r.,.,.r.f',
      '.,,,,h,,,,.',
      'f.,.MMM.,.f',
      '..,..v..,..',
      'hh,.....,hh',
      '..,,.r.,,..',
      'v.f.....f.v',
      '.,,,,h,,,,.',
      '.,.f...f.,.',
      'f,..,,,..,f',
      'h.v..h..v.h',
      'M...,C,...M',
      'MMf.....fMM',
    ],
    teams: {
      blue: { gold: 1000 },
      red: { gold: 400, ai: true, buy: ['shaman', 'golem', 'catapult', 'demon', 'archer'] },
    },
    buildings: [
      { x: 5, y: 13, team: 'blue' }, { x: 2, y: 12, team: 'blue' }, { x: 8, y: 12, team: 'blue' },
      { x: 5, y: 0, team: 'red' },
    ],
    units: [
      { type: 'hero', team: 'blue', x: 5, y: 12, commander: true },
      { type: 'healer', team: 'blue', x: 4, y: 13 },
      { type: 'shaman', team: 'blue', x: 6, y: 13 },
      { type: 'wisp', team: 'blue', x: 4, y: 12 },
      { type: 'soldier', team: 'blue', x: 6, y: 12 },
      { type: 'archer', team: 'blue', x: 3, y: 11 },
      { type: 'golem', team: 'blue', x: 7, y: 11 },
      { type: 'haar', team: 'red', x: 5, y: 1, hold: true, tag: 'boss' },
      { type: 'golem', team: 'red', x: 4, y: 3, hold: true },
      { type: 'golem', team: 'red', x: 6, y: 3, hold: true },
      { type: 'catapult', team: 'red', x: 5, y: 3, hold: true },
      { type: 'shaman', team: 'red', x: 3, y: 4 },
      { type: 'shaman', team: 'red', x: 7, y: 4 },
      { type: 'archer', team: 'red', x: 2, y: 3 },
      { type: 'archer', team: 'red', x: 8, y: 3 },
      { type: 'bat', team: 'red', x: 1, y: 6 },
    ],
    intro: [
      { who: 'haar', text: 'Юг размяк, рыцарь. Мои чары скуют твоих воинов, как лёд сковывает реку.' },
      { who: 'sif', text: 'Его удар тоже сковывает. Не подставляйте ему тех, кто нужен в следующем ходу.' },
      { who: 'hero', text: 'Огонёк, Лира, держитесь рядом. Идём на Трон.' },
    ],
    triggers: [
      {
        when: onEvent(e => e.type === 'hit' && e.toTag === 'boss'),
        say: [{ who: 'haar', text: 'Ты силён, южанин... Шаманы, ко мне!' }],
        spawn: [{ type: 'shaman', team: 'red', x: 4, y: 1 }, { type: 'shaman', team: 'red', x: 6, y: 1 }],
      },
      {
        when: onTurn(8, 'red'),
        say: [{ who: 'haar', text: 'Дух метели, приди!' }],
        spawn: [{ type: 'demon', team: 'red', x: 5, y: 2 }],
      },
    ],
    check(st) {
      if (st.flags.bossDead) return 'win';
      return null;
    },
    outro: [
      { who: 'haar', text: 'Лёд... тает...' },
      { who: 'sif', text: 'Север свободен. Мои люди вернутся домой.' },
      { who: 'hero', text: 'Хроники Эльдора дописаны. Пора и нам домой.' },
    ],
  },
];

const missionById = id => isSkirmishId(id) ? skirmishMission(id) : MISSIONS.find(m => m.id === id);

// Победа/поражение: общие правила кампании + check() миссии
function evaluateOutcome(st, mission) {
  if (mission.skirmish) return skirmishOutcome(st);
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
