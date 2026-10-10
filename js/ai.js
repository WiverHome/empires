/* ============ AI: ход компьютерного игрока ============ */
// aiNextAction(st) возвращает одно действие для активной стороны; интерфейс выполняет его
// с анимацией и спрашивает снова, пока не придёт { kind: 'end' }.
'use strict';

const AI_TUNE = {
  commanderWorth: 500,
  ghostWorth: 150,
  killBonus: 40,
  captureVillage: 70,
  captureEnemyVillage: 40,   // добавка, если деревня вражеская
  captureCastle: 250,
  raise: 90,
  terrainDef: 0.6,           // очков за единицу защиты местности
  approach: 5,               // штраф за шаг до цели
  danger: 0.35,              // штраф за ожидаемый урон по себе на клетке
  healerNearWounded: 25,
  retreatHp: 40,             // командир отступает лечиться ниже этого HP
  buyChance: 0.85,
};

function unitWorth(u) {
  const d = UNIT_DEFS[u.type];
  if (d.commander) return AI_TUNE.commanderWorth;
  if (u.type === 'ghost') return AI_TUNE.ghostWorth;
  return d.cost;
}

// Поле расстояний (в стоимости движения) от набора целей для данного юнита
function distanceField(st, u, goals) {
  const INF = 1e9;
  const f = Array.from({ length: st.h }, () => new Array(st.w).fill(INF));
  const open = [];
  for (const g of goals) { f[g.y][g.x] = 0; open.push(g); }
  while (open.length) {
    open.sort((a, b) => f[a.y][a.x] - f[b.y][b.x]);
    const c = open.shift();
    for (const [dx, dy] of DIRS) {
      const x = c.x + dx, y = c.y + dy;
      if (!inBounds(st, x, y)) continue;
      const cost = moveCost(st, u, x, y);
      if (!isFinite(cost)) continue;
      const nd = f[c.y][c.x] + cost;
      if (nd < f[y][x]) { f[y][x] = nd; open.push({ x, y }); }
    }
  }
  return f;
}

// Ожидаемый урон, который враги могут нанести по клетке в свой ход (грубо)
function dangerMap(st, team) {
  const m = new Map();
  for (const e of st.units) {
    if (e.team === team) continue;
    const reach = reachable(st, { ...e, moved: false });
    const tiles = threatTiles(st, e, reach);
    const [lo, hi] = unitAtk(e);
    const pot = (lo + hi) / 2 * e.hp / BAL.maxHp;
    for (const k of tiles) m.set(k, (m.get(k) || 0) + pot);
  }
  return m;
}

function aiGoals(st, u) {
  const d = UNIT_DEFS[u.type];
  const goals = [];
  for (const e of st.units) if (isEnemy(e, u)) goals.push({ x: e.x, y: e.y });
  if (d.canCapture) {
    for (const k in st.owner) {
      if (st.owner[k] === u.team) continue;
      const [x, y] = k.split(',').map(Number);
      if (st.terrain[y][x] === 'castle' && !d.commander) continue;
      goals.push({ x, y });
    }
  }
  if (!goals.length) for (const c of buildingsOf(st, u.team, 'castle')) goals.push(c);
  return goals;
}

function planUnit(st, u, danger) {
  const d = UNIT_DEFS[u.type];
  const reach = reachable(st, u);
  const wounded = d.commander && u.hp < AI_TUNE.retreatHp;
  let goals = aiGoals(st, u);
  if (wounded) {
    const safe = buildingsOf(st, u.team);
    if (safe.length) goals = safe;
  }
  const field = goals.length ? distanceField(st, u, goals) : null;
  const curDist = field ? field[u.y][u.x] : 0;
  const hold = u.hold && !st.units.some(e => isEnemy(e, u) && dist(e.x, e.y, u.x, u.y) <= d.move + d.range[1] + 2);
  const allies = teamUnits(st, u.team);
  let best = null;

  for (const n of reach.values()) {
    if (hold && (n.x !== u.x || n.y !== u.y)) continue;
    let base = unitDef(st, u, n.x, n.y) * AI_TUNE.terrainDef;
    if (field) {
      const fd = Math.min(field[n.y][n.x], 999);
      base -= fd * AI_TUNE.approach;
      // не стоять на месте без дела, если цель далеко
      if (n.x === u.x && n.y === u.y && curDist > d.move && !wounded) base -= 10;
    }
    const dang = danger.get(key(n.x, n.y)) || 0;
    base -= dang * AI_TUNE.danger * (d.commander ? 2 : 1) * unitWorth(u) / 400;
    if (d.aura) base += allies.filter(a => a !== u && a.hp < BAL.maxHp && dist(a.x, a.y, n.x, n.y) === 1).length * AI_TUNE.healerNearWounded;
    if (wounded && TERRAIN[terrainAt(st, n.x, n.y)].building && ownerAt(st, n.x, n.y) === u.team) base += 80;

    const consider = (score, act, extra) => {
      if (!best || score > best.score) best = { score, unit: u.id, to: { x: n.x, y: n.y }, act, ...extra };
    };
    consider(base, 'wait');

    if (canCaptureAt(st, u, n.x, n.y)) {
      const t = terrainAt(st, n.x, n.y);
      let v = t === 'castle' ? AI_TUNE.captureCastle : AI_TUNE.captureVillage;
      if (ownerAt(st, n.x, n.y)) v += AI_TUNE.captureEnemyVillage;
      consider(base + v, 'capture');
    }
    if (d.canRaise) {
      const gs = st.graves.filter(g => dist(g.x, g.y, n.x, n.y) === 1 && !(unitAt(st, g.x, g.y) && unitAt(st, g.x, g.y).id !== u.id) && !(g.x === n.x && g.y === n.y));
      if (gs.length && teamUnits(st, u.team).length < BAL.unitCap) consider(base + AI_TUNE.raise, 'raise', { grave: { x: gs[0].x, y: gs[0].y } });
    }
    for (const t of targetsFrom(st, u, n.x, n.y)) {
      const f = forecast(st, u, t, n.x, n.y);
      let v = f.dmg * unitWorth(t) / 100 - f.counter * unitWorth(u) / 100;
      if (f.kills) v += AI_TUNE.killBonus + unitWorth(t) / 10;
      if (UNIT_DEFS[t.type].commander) v *= 1.3;
      // атакующий всё равно стоит на клетке: опасность уже учтена в base
      consider(base + v + 20, 'attack', { target: t.id });
    }
  }
  return best;
}

function aiBuy(st, team, mission) {
  const t = st.teams[team];
  const md = (mission && mission.teams[team]) || {};
  const allowed = md.buy || BUY_LIST;
  const castles = buildingsOf(st, team, 'castle').filter(c => !unitAt(st, c.x, c.y));
  if (!castles.length || teamUnits(st, team).length >= BAL.unitCap) return null;
  const c = castles[0];
  if (t.commanderDead && t.commanderType && canBuy(st, team, t.commanderType, c.x, c.y))
    return { kind: 'buy', type: t.commanderType, x: c.x, y: c.y };
  if (rand(st) > AI_TUNE.buyChance) return null;
  const mine = teamUnits(st, team);
  const soldiers = mine.filter(u => u.type === 'soldier').length;
  const neutralVillages = Object.keys(st.owner).filter(k => st.owner[k] !== team).length;
  if (allowed.includes('soldier') && soldiers < 2 && neutralVillages > 0 && canBuy(st, team, 'soldier', c.x, c.y))
    return { kind: 'buy', type: 'soldier', x: c.x, y: c.y };
  // самый дорогой из доступных, с небольшой случайностью
  const opts = allowed.filter(ty => canBuy(st, team, ty, c.x, c.y)).sort((a, b) => UNIT_DEFS[b].cost - UNIT_DEFS[a].cost);
  if (!opts.length) return null;
  const pick = opts[Math.min(opts.length - 1, Math.floor(rand(st) * Math.min(3, opts.length)))];
  return { kind: 'buy', type: pick, x: c.x, y: c.y };
}

function aiNextAction(st, mission) {
  const team = st.active;
  const ready = st.units.filter(u => u.team === team && !u.acted);
  if (ready.length) {
    const danger = dangerMap(st, team);
    let best = null;
    for (const u of ready) {
      const p = planUnit(st, u, danger);
      // юниты с атакой ходят первыми: они открывают дорогу остальным
      if (p && (!best || p.score + (p.act === 'attack' ? 1000 : 0) > best.score + (best.act === 'attack' ? 1000 : 0))) best = p;
    }
    if (best) return { kind: 'unit', ...best };
  }
  const b = aiBuy(st, team, mission);
  if (b) return b;
  return { kind: 'end' };
}

// Применить действие ИИ к состоянию (без анимации): для Node-симуляций
function aiApply(st, a) {
  if (a.kind === 'buy') return actBuy(st, st.active, a.type, a.x, a.y);
  if (a.kind === 'end') return endTurn(st);
  const u = unitById(st, a.unit);
  actMove(st, u, a.to.x, a.to.y);
  if (a.act === 'attack') return actAttack(st, u, unitById(st, a.target));
  if (a.act === 'capture') return actCapture(st, u);
  if (a.act === 'raise') return actRaise(st, u, graveAt(st, a.grave.x, a.grave.y));
  return actWait(st, u);
}
