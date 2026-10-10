/* ============ SKIRMISH: свободная схватка на симметричных картах ============ */
// Схватка описывается строкой id 'sk:<карта>:<золото>:<стороны>[:f]', где стороны - по символу на слот
// карты: h - игрок, a - ИИ, n - нет; f в конце - туман войны. Так id сохраняется и перезапускается как id миссии кампании.
'use strict';

// Половина карты + средняя строка, остальное - поворот на 180° (честно для двоих)
function rotateMap(top, middle) {
  const rev = s => [...s].reverse().join('');
  return [...top, middle, ...top.slice().reverse().map(rev)];
}
// Четверть карты (с центральными строкой и столбцом), отражённая по обеим осям (для четверых)
function mirrorMap(quarter) {
  const row = r => r + [...r.slice(0, -1)].reverse().join('');
  const half = quarter.map(row);
  return [...half, ...half.slice(0, -1).reverse()];
}

const SKIRMISH_MAPS = [
  {
    id: 'valley', title: 'Речная долина', desc: 'Двое, река с двумя мостами и бродом по холму.',
    map: rotateMap([
      'Mf..,C,..fM',
      'f..v,,,v..f',
      '..h.,.,.h..',
      'v..ff,ff..v',
      '.,.....h.,.',
      'hf.v....f.h',
    ], '~~=~~h~~=~~'),
    slots: [
      { team: 'blue', castle: [5, 12], cmd: [5, 11] },
      { team: 'red', castle: [5, 0], cmd: [5, 1] },
    ],
  },
  {
    id: 'pass', title: 'Горный перевал', desc: 'Двое, замки по разные стороны гор.',
    map: rotateMap([
      'Mh.v..,C.fM',
      'h..,,,,,..f',
      '.v.,.ff.,.v',
      '..,,.ff.,..',
      'ff,.hhh.,ff',
      'f.,..v..,.f',
    ], '~~=~~M~~=~~'),
    slots: [
      { team: 'blue', castle: [3, 12], cmd: [3, 11] },
      { team: 'red', castle: [7, 0], cmd: [7, 1] },
    ],
  },
  {
    id: 'four', title: 'Четыре замка', desc: 'До четырёх сторон, в центре ничейный замок.',
    map: mirrorMap([
      'ff.,..',
      'fC,,.v',
      '.,.f.,',
      '.,fh.,',
      '..h.f,',
      'v,,,,C',
    ]),
    slots: [
      { team: 'blue', castle: [1, 9], cmd: [2, 9] },
      { team: 'yellow', castle: [1, 1], cmd: [2, 1] },
      { team: 'red', castle: [9, 1], cmd: [8, 1] },
      { team: 'green', castle: [9, 9], cmd: [8, 9] },
    ],
  },
  {
    id: 'delta', title: 'Дельта', desc: 'Двое, протоки и мелководье: раздолье для водяных.',
    map: rotateMap([
      'Mf.v,C,..ss',
      'f..,,,,.ss~',
      '.v.,.ss.s~~',
      'ss,,s~~ss..',
      '~s..s~~.v.,',
      '~~s...h..,.',
    ], 'h.ss~~~ss.h'),
    slots: [
      { team: 'blue', castle: [5, 12], cmd: [5, 11] },
      { team: 'red', castle: [5, 0], cmd: [5, 1] },
    ],
  },
  {
    id: 'ruins', title: 'Старые руины', desc: 'Двое, разорённый край: руины можно отстроить.',
    map: rotateMap([
      'Mh..,C,.hfM',
      'f.r.,,,.r.f',
      '..,,,.,,,..',
      'v.,.ff.f,.r',
      '.r,..h...,.',
      'f.,.r...v,f',
    ], 'h.,..M..,.h'),
    slots: [
      { team: 'blue', castle: [5, 12], cmd: [5, 11] },
      { team: 'red', castle: [5, 0], cmd: [5, 1] },
    ],
  },
  {
    id: 'lake', title: 'Озёрный край', desc: 'До четырёх сторон вокруг большого озера.',
    map: mirrorMap([
      'Mf.,..',
      'fC,,.v',
      '.,..s.',
      '.,.ss~',
      '.vs~~~',
      '..s~~~',
    ]),
    slots: [
      { team: 'blue', castle: [1, 9], cmd: [2, 9] },
      { team: 'yellow', castle: [1, 1], cmd: [2, 1] },
      { team: 'red', castle: [9, 1], cmd: [8, 1] },
      { team: 'green', castle: [9, 9], cmd: [8, 9] },
    ],
  },
];

// Свои карты игрока (заполняет main.js из сохранения), формат как у SKIRMISH_MAPS
const CUSTOM_MAPS = [];
const skirmishMapById = id => SKIRMISH_MAPS.find(m => m.id === id) || CUSTOM_MAPS.find(m => m.id === id) || null;

const SKIRMISH_GOLD =[500, 1000, 2000];
// Командиры сторон в схватке
const SKIRMISH_CMD = { blue: 'hero', red: 'warlord', green: 'necro', yellow: 'warlord' };

const skirmishId = (mapId, gold, sides, fog) => `sk:${mapId}:${gold}:${sides}${fog ? ':f' : ''}`;
const isSkirmishId = id => typeof id === 'string' && id.startsWith('sk:');

// Собрать объект миссии по id схватки (null, если id некорректен)
function skirmishMission(id) {
  const [, mapId, goldStr, sides, flags] = id.split(':');
  const sm = skirmishMapById(mapId);
  if (!sm || !sides || sides.length !== sm.slots.length) return null;
  const teams = {}, order = [], buildings = [], units = [];
  sm.slots.forEach((s, i) => {
    const kind = sides[i];
    if (kind === 'n') return;
    order.push(s.team);
    teams[s.team] = { gold: +goldStr || 0, ai: kind === 'a', name: TEAMS[s.team].label, commander: SKIRMISH_CMD[s.team] };
    buildings.push({ x: s.castle[0], y: s.castle[1], team: s.team });
    units.push({ type: SKIRMISH_CMD[s.team], team: s.team, x: s.cmd[0], y: s.cmd[1], commander: true });
  });
  return {
    id, skirmish: true, title: sm.title, fog: flags === 'f',
    goal: 'Разбейте все войска противников и займите их замки.',
    map: sm.map, order, teams, buildings, units,
  };
}

// Итог схватки: побеждает последняя уцелевшая сторона; игрок проигрывает, когда разбиты все люди
function skirmishOutcome(st) {
  const alive = st.order.filter(t => !isEliminated(st, t));
  const humans = st.order.filter(t => !st.teams[t].ai);
  const players = humans.length ? humans : ['blue'];   // без людей (симуляция) играем за синих
  if (!players.some(t => alive.includes(t))) return 'lose';
  if (alive.length === 1) { st.winner = alive[0]; return 'win'; }
  return null;
}
