/* ============ RULES: состояние партии, движение, бой, экономика ============ */
// Без DOM: файл грузится и в браузере, и в Node (tools/).
'use strict';

const key = (x, y) => x + ',' + y;
const dist = (ax, ay, bx, by) => Math.abs(ax - bx) + Math.abs(ay - by);
const DIRS = [[1, 0], [-1, 0], [0, 1], [0, -1]];

/* ---------- RNG с сидом (mulberry32, состояние лежит в state.seed) ---------- */
function rand(st) {
  let t = (st.seed = (st.seed + 0x6D2B79F5) >>> 0);
  t = Math.imul(t ^ (t >>> 15), t | 1);
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const randInt = (st, a, b) => a + Math.floor(rand(st) * (b - a + 1));

/* ---------- Создание партии из описания миссии ---------- */
function parseMap(rows) {
  const h = rows.length, w = rows[0].length;
  const terrain = [];
  for (let y = 0; y < h; y++) {
    if (rows[y].length !== w) throw new Error('map row ' + y + ' length ' + rows[y].length + ' != ' + w);
    terrain.push([...rows[y]].map(ch => {
      const t = MAP_LEGEND[ch];
      if (!t) throw new Error('unknown map char "' + ch + '" at row ' + y);
      return t;
    }));
  }
  return { w, h, terrain };
}

function createState(mission, opts = {}) {
  const { w, h, terrain } = parseMap(mission.map);
  const st = {
    missionId: mission.id,
    w, h, terrain,
    owner: {},
    units: [],
    graves: [],
    nextId: 1,
    turn: 1,
    order: mission.order || ['blue', 'red'],
    active: null,
    teams: {},
    seed: (opts.seed ?? Math.floor(Math.random() * 2 ** 32)) >>> 0,
    flags: {},
    over: null,
  };
  st.active = st.order[0];
  for (const t of st.order) {
    const md = mission.teams[t] || {};
    st.teams[t] = {
      gold: md.gold || 0,
      ai: !!md.ai,
      commanderType: md.commander || null,
      commanderDead: false,
      commanderLevel: 0,
      commanderXp: 0,
    };
  }
  // здания: по умолчанию нейтральные
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++)
      if (TERRAIN[terrain[y][x]].building) st.owner[key(x, y)] = null;
  for (const b of mission.buildings || []) st.owner[key(b.x, b.y)] = b.team;
  for (const u of mission.units || []) {
    const lvl = u.commander && opts.commanderLevel ? opts.commanderLevel : (u.level || 0);
    spawnUnit(st, u.type, u.team, u.x, u.y, { level: lvl, hp: u.hp, hold: u.hold, tag: u.tag });
  }
  for (const g of mission.graves || []) st.graves.push({ x: g.x, y: g.y, turns: 999 });
  return st;
}

function spawnUnit(st, type, team, x, y, o = {}) {
  const def = UNIT_DEFS[type];
  const u = {
    id: st.nextId++, type, team, x, y,
    hp: o.hp || BAL.maxHp, level: o.level || 0, xp: o.xp || 0,
    moved: !!o.exhausted, acted: !!o.exhausted,
  };
  if (o.hold) u.hold = true;          // ИИ: стоять на месте, пока враг не подойдёт
  if (o.tag) u.tag = o.tag;           // метка для сценария миссии
  if (def.commander) {
    st.teams[team].commanderType = type;
    st.teams[team].commanderDead = false;
    st.teams[team].commanderLevel = u.level;
  }
  st.units.push(u);
  return u;
}

/* ---------- Запросы ---------- */
const inBounds = (st, x, y) => x >= 0 && y >= 0 && x < st.w && y < st.h;
const terrainAt = (st, x, y) => st.terrain[y][x];
const unitAt = (st, x, y) => st.units.find(u => u.x === x && u.y === y) || null;
const unitById = (st, id) => st.units.find(u => u.id === id) || null;
const graveAt = (st, x, y) => st.graves.find(g => g.x === x && g.y === y) || null;
const ownerAt = (st, x, y) => st.owner[key(x, y)];
const isEnemy = (a, b) => a.team !== b.team;
const teamUnits = (st, team) => st.units.filter(u => u.team === team);
const commanderOf = (st, team) => st.units.find(u => u.team === team && UNIT_DEFS[u.type].commander) || null;

function buildingsOf(st, team, kind) {
  const out = [];
  for (const k in st.owner) {
    if (st.owner[k] !== team) continue;
    const [x, y] = k.split(',').map(Number);
    if (!kind || st.terrain[y][x] === kind) out.push({ x, y });
  }
  return out;
}

function unitAtk(u) {
  const d = UNIT_DEFS[u.type];
  return [d.atk[0] + u.level * BAL.levelAtk, d.atk[1] + u.level * BAL.levelAtk];
}
function unitDef(st, u, x = u.x, y = u.y) {
  const d = UNIT_DEFS[u.type];
  const terr = d.flying ? 0 : TERRAIN[terrainAt(st, x, y)].def;
  return d.def + u.level * BAL.levelDef + terr;
}

function moveCost(st, u, x, y) {
  if (UNIT_DEFS[u.type].flying) return 1;
  return TERRAIN[terrainAt(st, x, y)].cost;
}

// Клетки, куда юнит может встать: Map key -> {x, y, cost, prev}
function reachable(st, u) {
  const move = UNIT_DEFS[u.type].move;
  const best = new Map();
  best.set(key(u.x, u.y), { x: u.x, y: u.y, cost: 0, prev: null });
  const open = [best.get(key(u.x, u.y))];
  while (open.length) {
    open.sort((a, b) => a.cost - b.cost);
    const cur = open.shift();
    for (const [dx, dy] of DIRS) {
      const nx = cur.x + dx, ny = cur.y + dy;
      if (!inBounds(st, nx, ny)) continue;
      const other = unitAt(st, nx, ny);
      if (other && isEnemy(other, u)) continue;           // враги перекрывают путь
      const c = cur.cost + moveCost(st, u, nx, ny);
      if (c > move) continue;
      const k = key(nx, ny);
      const old = best.get(k);
      if (old && old.cost <= c) continue;
      const node = { x: nx, y: ny, cost: c, prev: key(cur.x, cur.y) };
      best.set(k, node);
      open.push(node);
    }
  }
  // через союзников проходить можно, вставать нельзя
  for (const [k, n] of best) {
    const other = unitAt(st, n.x, n.y);
    if (other && other.id !== u.id) best.delete(k);
  }
  return best;
}

function pathTo(reach, x, y) {
  const path = [];
  let n = reach.get(key(x, y));
  while (n) { path.unshift({ x: n.x, y: n.y }); n = n.prev ? reach.get(n.prev) : null; }
  return path;
}

function canAttackFrom(u, fromX, fromY) {
  const d = UNIT_DEFS[u.type];
  if (d.noMoveAttack && (fromX !== u.x || fromY !== u.y)) return false;
  return true;
}

// Враги в радиусе атаки с клетки (fromX, fromY)
function targetsFrom(st, u, fromX, fromY) {
  if (!canAttackFrom(u, fromX, fromY)) return [];
  const [rMin, rMax] = UNIT_DEFS[u.type].range;
  return st.units.filter(t => isEnemy(t, u) && t.hp > 0 &&
    dist(fromX, fromY, t.x, t.y) >= rMin && dist(fromX, fromY, t.x, t.y) <= rMax);
}

// Все клетки, которые юнит может атаковать в этом ходу (для подсветки)
function threatTiles(st, u, reach = reachable(st, u)) {
  const out = new Set();
  const [rMin, rMax] = UNIT_DEFS[u.type].range;
  for (const n of reach.values()) {
    if (!canAttackFrom(u, n.x, n.y)) continue;
    for (let dy = -rMax; dy <= rMax; dy++)
      for (let dx = -rMax; dx <= rMax; dx++) {
        const d = Math.abs(dx) + Math.abs(dy);
        if (d < rMin || d > rMax) continue;
        const x = n.x + dx, y = n.y + dy;
        if (inBounds(st, x, y)) out.add(key(x, y));
      }
  }
  return out;
}

function gravesNear(st, x, y) {
  return st.graves.filter(g => dist(g.x, g.y, x, y) === 1 && !unitAt(st, g.x, g.y));
}

function canCaptureAt(st, u, x = u.x, y = u.y) {
  const d = UNIT_DEFS[u.type];
  const t = terrainAt(st, x, y);
  if (!d.canCapture || !TERRAIN[t].building) return false;
  if (ownerAt(st, x, y) === u.team) return false;
  if (t === 'castle' && !d.commander) return false;
  return true;
}

/* ---------- Бой ---------- */
// Урон без случайности: mode 'min' | 'max' | 'avg' | 'roll'
function damageOf(st, att, def, mode, attPos, defPos) {
  const a = UNIT_DEFS[att.type];
  const [lo, hi] = unitAtk(att);
  let base = mode === 'min' ? lo : mode === 'max' ? hi : mode === 'avg' ? (lo + hi) / 2 : randInt(st, lo, hi);
  if (a.vsFlying && UNIT_DEFS[def.type].flying) base += a.vsFlying;
  const dp = defPos || def;
  const raw = Math.max(0, base - unitDef(st, def, dp.x, dp.y));
  return Math.min(def.hp, Math.round(raw * att.hp / BAL.maxHp));
}

// Прогноз для интерфейса и ИИ: урон атаки и ответный урон (средние)
function forecast(st, att, def, fromX = att.x, fromY = att.y) {
  const pos = { x: fromX, y: fromY };
  const dmg = damageOf(st, att, def, 'avg', pos, def);
  const left = def.hp - dmg;
  let counter = 0;
  if (left > 0 && canCounter(def, att, fromX, fromY)) {
    const fake = { ...def, hp: left };
    counter = damageOf(st, fake, att, 'avg', def, pos);
  }
  return { dmg, counter, kills: left <= 0 };
}

function canCounter(def, att, attX, attY) {
  const d = UNIT_DEFS[def.type];
  const r = dist(def.x, def.y, attX, attY);
  return r >= d.range[0] && r <= d.range[1];
}

function gainXp(st, u, amount, events) {
  if (u.level >= BAL.maxLevel) return;
  u.xp += amount;
  while (u.xp >= BAL.xpPerLevel && u.level < BAL.maxLevel) {
    u.xp -= BAL.xpPerLevel;
    u.level++;
    events.push({ type: 'levelup', unit: u.id, team: u.team, level: u.level, x: u.x, y: u.y });
  }
  if (UNIT_DEFS[u.type].commander) st.teams[u.team].commanderLevel = u.level;
}

function strike(st, att, def, events) {
  const dmg = damageOf(st, att, def, 'roll');
  def.hp -= dmg;
  if (def.hold) delete def.hold;      // атакованный перестаёт стоять на месте
  events.push({ type: 'hit', from: att.id, to: def.id, toTag: def.tag, dmg, x: def.x, y: def.y, fx: att.x, fy: att.y });
  let xp = dmg;
  if (def.hp <= 0) { xp += BAL.xpKillBonus; killUnit(st, def, events); }
  gainXp(st, att, xp, events);
  return def.hp <= 0;
}

function killUnit(st, u, events) {
  st.units = st.units.filter(o => o.id !== u.id);
  const d = UNIT_DEFS[u.type];
  if (u.tag === 'boss') st.flags.bossDead = true;
  if (d.commander) {
    st.teams[u.team].commanderDead = true;
    st.teams[u.team].commanderLevel = u.level;
  }
  if (u.type !== 'ghost') {
    st.graves = st.graves.filter(g => !(g.x === u.x && g.y === u.y));
    st.graves.push({ x: u.x, y: u.y, turns: BAL.graveTurns * st.order.length });
  }
  events.push({ type: 'death', unit: u.id, unitType: u.type, team: u.team, x: u.x, y: u.y });
}

/* ---------- Действия (все возвращают список событий для анимации) ---------- */
function actMove(st, u, x, y) {
  u.x = x; u.y = y; u.moved = true;
  return [];
}

function actAttack(st, u, target) {
  const events = [];
  const fromX = u.x, fromY = u.y;
  const killed = strike(st, u, target, events);
  if (!killed && unitById(st, u.id) && canCounter(target, u, fromX, fromY)) strike(st, target, u, events);
  u.moved = u.acted = true;
  return events;
}

function actCapture(st, u) {
  const prev = ownerAt(st, u.x, u.y);
  st.owner[key(u.x, u.y)] = u.team;
  u.moved = u.acted = true;
  return [{ type: 'capture', unit: u.id, team: u.team, prev, x: u.x, y: u.y, terrain: terrainAt(st, u.x, u.y) }];
}

function actRaise(st, u, g) {
  st.graves = st.graves.filter(o => o !== g);
  const gh = spawnUnit(st, 'ghost', u.team, g.x, g.y, { exhausted: true });
  u.moved = u.acted = true;
  return [{ type: 'raise', unit: gh.id, x: g.x, y: g.y }];
}

function actWait(st, u) { u.moved = u.acted = true; return []; }

function canBuy(st, team, type, x, y) {
  const t = st.teams[team];
  if (terrainAt(st, x, y) !== 'castle' || ownerAt(st, x, y) !== team || unitAt(st, x, y)) return false;
  if (teamUnits(st, team).length >= BAL.unitCap) return false;
  return t.gold >= buyCost(st, team, type);
}

function buyCost(st, team, type) {
  const d = UNIT_DEFS[type];
  if (d.commander) return BAL.commanderReviveBase + BAL.commanderRevivePerLevel * st.teams[team].commanderLevel;
  return d.cost;
}

// Что можно купить в замке: обычные юниты + воскрешение павшего командира
function buyOptions(st, team) {
  const list = BUY_LIST.slice();
  const t = st.teams[team];
  if (t.commanderDead && t.commanderType) list.unshift(t.commanderType);
  return list;
}

function actBuy(st, team, type, x, y) {
  const cost = buyCost(st, team, type);
  st.teams[team].gold -= cost;
  const lvl = UNIT_DEFS[type].commander ? st.teams[team].commanderLevel : 0;
  const u = spawnUnit(st, type, team, x, y, { exhausted: true, level: lvl });
  return [{ type: 'buy', unit: u.id, x, y, cost }];
}

/* ---------- Смена хода ---------- */
function incomeOf(st, team) {
  let g = 0;
  for (const b of buildingsOf(st, team)) g += TERRAIN[terrainAt(st, b.x, b.y)].income || 0;
  return g;
}

function endTurn(st) {
  const events = [];
  const idx = st.order.indexOf(st.active);
  const next = st.order[(idx + 1) % st.order.length];
  if (idx === st.order.length - 1) st.turn++;
  st.active = next;
  // могилы исчезают со временем
  for (const g of st.graves) g.turns--;
  st.graves = st.graves.filter(g => g.turns > 0);
  events.push(...beginTurn(st));
  return events;
}

function beginTurn(st) {
  const team = st.active, events = [];
  const gold = incomeOf(st, team);
  st.teams[team].gold += gold;
  events.push({ type: 'income', team, gold });
  const mine = teamUnits(st, team);
  for (const u of mine) {
    u.moved = u.acted = false;
    let heal = 0;
    const t = terrainAt(st, u.x, u.y);
    if (TERRAIN[t].building && ownerAt(st, u.x, u.y) === team) heal += BAL.healBuilding;
    if (mine.some(o => o !== u && UNIT_DEFS[o.type].aura && dist(o.x, o.y, u.x, u.y) === 1)) heal += BAL.healAura;
    heal = Math.min(heal, BAL.maxHp - u.hp);
    if (heal > 0) { u.hp += heal; events.push({ type: 'heal', unit: u.id, amount: heal, x: u.x, y: u.y }); }
  }
  return events;
}

// Сторона разбита: нет юнитов и нет замков
function isEliminated(st, team) {
  return teamUnits(st, team).length === 0 && buildingsOf(st, team, 'castle').length === 0;
}
