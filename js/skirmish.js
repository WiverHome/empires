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
  {
    id: 'isles', title: 'Архипелаг', desc: 'Двое, острова и броды: водяным здесь раздолье.',
    map: rotateMap([
      '~s..,C,..s~',
      '~.v.,,,.v.~',
      's..f...f..s',
      '~s~~=~=~~s~',
      '..v.s.s.v..',
      'f.h.~~~.h.f',
    ], 'v..s=r=s..v'),
    slots: [
      { team: 'blue', castle: [5, 12], cmd: [5, 11] },
      { team: 'red', castle: [5, 0], cmd: [5, 1] },
    ],
  },
  {
    id: 'thicket', title: 'Чаща', desc: 'Двое, длинная лесная карта, в центре руины.',
    map: rotateMap([
      'ff.v.C.v.ff',
      'f..,,,,,..f',
      '.,,f.f.f,,.',
      '.,ff.h.ff,.',
      'v,f..h..f,v',
      '.,.ff.ff.,.',
      'ff,..f..,ff',
    ], 'h.,,frf,,.h'),
    slots: [
      { team: 'blue', castle: [5, 14], cmd: [5, 13] },
      { team: 'red', castle: [5, 0], cmd: [5, 1] },
    ],
  },
  {
    id: 'forts', title: 'Крепости', desc: 'До четырёх сторон, руины и ничейный замок в центре.',
    map: mirrorMap([
      'M.f.v.',
      '.C,,,,',
      'f,.h.,',
      '.,h..r',
      'v,..f.',
      '.,r.,C',
    ]),
    slots: [
      { team: 'blue', castle: [1, 9], cmd: [2, 9] },
      { team: 'yellow', castle: [1, 1], cmd: [2, 1] },
      { team: 'red', castle: [9, 1], cmd: [8, 1] },
      { team: 'green', castle: [9, 9], cmd: [8, 9] },
    ],
  },
  {
    id: 'fjords', title: 'Фьорды', desc: 'Двое, протоки, броды и леса: раздолье волкам и водяным.',
    map: rotateMap([
      'ff~.,C,.~ff',
      'f.~.,,,.~.f',
      'v.s..f..s.v',
      '~~s.fff.s~~',
      '.,,,.f.,,,.',
      'ff.v.,.v.ff',
    ], 'ss~~=.=~~ss'),
    slots: [
      { team: 'blue', castle: [5, 12], cmd: [5, 11] },
      { team: 'red', castle: [5, 0], cmd: [5, 1] },
    ],
  },
  {
    id: 'cross', title: 'Перекрёсток', desc: 'До четырёх сторон, дороги сходятся у руин в центре.',
    map: mirrorMap([
      'ff..v,',
      'fC,,,,',
      '.,ff.,',
      '.,f.h,',
      'v,.h.,',
      ',,,,,r',
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

/* ---------- Код карты для обмена: "EMP1:" + base64url(JSON {t, m, s}) ---------- */
const MAP_CODE_PREFIX = 'EMP1:';
const MAP_CODE_TEAMS = ['blue', 'red', 'green', 'yellow'];

function encodeMapCode(sm) {
  const json = JSON.stringify({ t: sm.title, m: sm.map, s: sm.slots.map(s => [s.team, ...s.castle, ...s.cmd]) });
  let bin = '';
  for (const b of new TextEncoder().encode(json)) bin += String.fromCharCode(b);
  return MAP_CODE_PREFIX + btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

// Разобрать код карты: объект карты (как в CUSTOM_MAPS, с новым id) или строка ошибки
function decodeMapCode(code, id) {
  code = String(code || '').replace(/\s+/g, '');
  if (!code.startsWith(MAP_CODE_PREFIX)) return 'Это не код карты.';
  let d;
  try {
    const bin = atob(code.slice(MAP_CODE_PREFIX.length).replace(/-/g, '+').replace(/_/g, '/'));
    d = JSON.parse(new TextDecoder().decode(Uint8Array.from(bin, c => c.charCodeAt(0))));
  } catch (e) { return 'Код повреждён.'; }
  const rows = d && d.m;
  if (!Array.isArray(rows) || !rows.length || typeof rows[0] !== 'string') return 'Код повреждён.';
  const h = rows.length, w = rows[0].length;
  if (w < 7 || w > 11 || h < 7 || h > 15) return 'Неподходящий размер карты.';
  if (rows.some(r => typeof r !== 'string' || r.length !== w || [...r].some(c => !MAP_LEGEND[c]))) return 'Код повреждён.';
  const at = (x, y) => (x >= 0 && y >= 0 && x < w && y < h) ? MAP_LEGEND[rows[y][x]] : null;
  const seen = new Set(), slots = [];
  for (const s of Array.isArray(d.s) ? d.s : []) {
    const [team, cx, cy, x, y] = s || [];
    if (!MAP_CODE_TEAMS.includes(team) || seen.has(team)) return 'Код повреждён.';
    if (at(cx, cy) !== 'castle') return 'Код повреждён.';
    const t = at(x, y);
    if (!t || !isFinite(TERRAIN[t].cost) || t === 'castle' || t === 'village') return 'Код повреждён.';
    seen.add(team);
    slots.push({ team, castle: [cx, cy], cmd: [x, y] });
  }
  if (slots.length < 2) return 'На карте меньше двух сторон.';
  const title = String(d.t || 'Чужая карта').slice(0, 24);
  return { id, title, desc: `Своя карта ${w}x${h}, сторон: ${slots.length}.`, custom: true, map: rows.slice(), slots };
}

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
