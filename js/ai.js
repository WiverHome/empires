/* ============ AI: ход компьютерного игрока ============ */
// aiNextAction(st) возвращает одно действие для активной стороны; интерфейс выполняет его
// с анимацией и спрашивает снова, пока не придёт { kind: 'end' }.
'use strict';

const AI_TUNE = {
  commanderWorth: 500,
  ghostWorth: 150,
  crystalWorth: 600,
  destroyVillage: 75,        // катапульте: разрушить вражескую деревню
  destroyNeutral: 20,        // ... ничью
  repair: 80,                // отстроить руины
  poisonHit: 25,             // отравить цель, которая выживет
  slowHit: 15,               // сковать цель, которая выживет
  wispNear: 8,               // огоньку: за союзника рядом (усиление атаки)
  killBonus: 40,
  captureVillage: 70,
  captureEnemyVillage: 40,   // добавка, если деревня вражеская
  captureCastle: 250,
  raise: 90,
  terrainDef: 1.2,           // очков за единицу защиты местности
  approach: 5,               // штраф за шаг до цели
  danger: 0.25,              // доля ожидаемого урона по себе, которую считаем потерей
  lethal: 0.2,               // доля ценности юнита - штраф, если на клетке могут добить
  threatTop: 2,              // сколько сильнейших угроз по клетке учитывать
  aggrMin: 0.6, aggrMax: 1.8, // пределы агрессии (отношение сил армий)
  aggrPerTurn: 0.06,         // с каждым ходом смелее, чтобы не стоять вечно
  supportAlly: 6,            // за соседа-союзника (строй), до 2 соседей
  blockCastle: 25,           // штраф за стояние на своём замке: мешает найму
  siegeReady: 15,            // катапульте: враг в радиусе с этой клетки
  healerNearWounded: 25,
  retreatHp: 40,             // командир отступает лечиться ниже этого HP
  retreatHpUnit: 30,         // обычный воин отступает ниже этого HP
  buyChance: 0.85,
};

function unitWorth(u) {
  const d = UNIT_DEFS[u.type];
  if (d.commander) return AI_TUNE.commanderWorth;
  if (u.type === 'ghost') return AI_TUNE.ghostWorth;
  if (u.type === 'crystal') return AI_TUNE.crystalWorth;
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

// Какие враги могут ударить по какой клетке в свой ход: key -> [враг]
function threatMap(st, team) {
  const m = new Map();
  for (const e of st.units) {
    if (e.team === team) continue;
    const reach = reachable(st, { ...e, moved: false, acted: false });
    for (const k of threatTiles(st, e, reach)) {
      if (!m.has(k)) m.set(k, []);
      m.get(k).push(e);
    }
  }
  return m;
}

// Ожидаемый урон по юниту u на клетке (x, y): сумма сильнейших угроз (кроме skipId - её убьём)
function expectedHit(st, u, x, y, threats, skipId) {
  const list = threats.get(key(x, y));
  if (!list) return 0;
  const me = { ...u, x, y, hp: BAL.maxHp };
  const hits = [];
  for (const e of list) {
    if (e.id === skipId) continue;
    // катапульта не стреляет после движения: опасна только в своём радиусе с места
    const ed = UNIT_DEFS[e.type];
    if (ed.noMoveAttack) {
      const r = dist(e.x, e.y, x, y);
      if (r < ed.range[0] || r > ed.range[1]) continue;
    }
    hits.push(damageOf(st, e, me, 'avg', e, me));
  }
  hits.sort((a, b) => b - a);
  return hits.slice(0, AI_TUNE.threatTop).reduce((a, b) => a + b, 0);
}

// Отношение сил: >1 - мы сильнее и можно наступать, <1 - держим строй
function aggression(st, team) {
  let mine = 0, theirs = 0;
  for (const u of st.units) {
    const w = unitWorth(u) * u.hp / BAL.maxHp;
    if (u.team === team) mine += w; else theirs += w;
  }
  const r = theirs ? mine / theirs : AI_TUNE.aggrMax;
  return Math.max(AI_TUNE.aggrMin, Math.min(AI_TUNE.aggrMax, r)) * (1 + st.turn * AI_TUNE.aggrPerTurn);
}

function aiGoals(st, u) {
  const d = UNIT_DEFS[u.type];
  if (u.goal) return [u.goal];
  const goals = [];
  if (!d.noAttack) for (const e of st.units) if (isEnemy(e, u)) goals.push({ x: e.x, y: e.y });
  if (d.canCapture) {
    for (const k in st.owner) {
      if (st.owner[k] === u.team) continue;
      const [x, y] = k.split(',').map(Number);
      if (st.terrain[y][x] === 'castle' && !d.commander) continue;
      goals.push({ x, y });
    }
    for (let y = 0; y < st.h; y++) for (let x = 0; x < st.w; x++) if (st.terrain[y][x] === 'ruins') goals.push({ x, y });
  }
  // небоевые держатся поближе к своим
  if (d.noAttack) for (const a of teamUnits(st, u.team)) if (a !== u) goals.push({ x: a.x, y: a.y });
  if (!goals.length) for (const c of buildingsOf(st, u.team, 'castle')) goals.push(c);
  return goals;
}

function planUnit(st, u, ctx) {
  const d = UNIT_DEFS[u.type];
  const reach = reachable(st, u);
  const allies = teamUnits(st, u.team);
  const worth = unitWorth(u);
  const wounded = u.type !== 'ghost' && u.hp < (d.commander ? AI_TUNE.retreatHp : AI_TUNE.retreatHpUnit);
  let goals = aiGoals(st, u);
  if (wounded) {
    const safe = buildingsOf(st, u.team).concat(allies.filter(a => a !== u && UNIT_DEFS[a.type].aura));
    if (safe.length) goals = safe;
  }
  const field = goals.length ? distanceField(st, u, goals) : null;
  const curDist = field ? field[u.y][u.x] : 0;
  const hold = u.hold && !st.units.some(e => isEnemy(e, u) && dist(e.x, e.y, u.x, u.y) <= d.move + d.range[1] + 2);
  // командир и раненые берегутся сильнее; при перевесе сил - смелее
  const caution = (d.commander ? 1.5 : 1) * (wounded ? 1.5 : 1) / ctx.aggr;
  const risk = (x, y, skipId) => {
    const hit = Math.min(expectedHit(st, u, x, y, ctx.threats, skipId), BAL.maxHp);
    let cost = hit * worth / 100 * AI_TUNE.danger;
    if (hit >= u.hp) cost += worth * AI_TUNE.lethal;
    return cost * caution;
  };
  let best = null;

  for (const n of reach.values()) {
    if (hold && (n.x !== u.x || n.y !== u.y)) continue;
    const terr = terrainAt(st, n.x, n.y);
    let base = unitDef(st, u, n.x, n.y) * AI_TUNE.terrainDef;
    if (field) {
      base -= Math.min(field[n.y][n.x], 999) * AI_TUNE.approach;
      // не стоять на месте без дела, если цель далеко
      if (n.x === u.x && n.y === u.y && curDist > d.move && !wounded) base -= 10;
    }
    const near = allies.filter(a => a !== u && dist(a.x, a.y, n.x, n.y) === 1);
    base += Math.min(2, near.length) * AI_TUNE.supportAlly;
    if (d.aura) base += near.filter(a => a.hp < BAL.maxHp || a.poison).length * AI_TUNE.healerNearWounded;
    if (d.auraAtk) base += Math.min(3, near.length) * AI_TUNE.wispNear;
    const own = ownerAt(st, n.x, n.y) === u.team;
    if (wounded && TERRAIN[terr].building && own) base += 80;
    if (terr === 'castle' && own && !wounded) base -= AI_TUNE.blockCastle;
    if (d.noMoveAttack && st.units.some(e => isEnemy(e, u) && dist(e.x, e.y, n.x, n.y) >= d.range[0] && dist(e.x, e.y, n.x, n.y) <= d.range[1]))
      base += AI_TUNE.siegeReady;
    const tileRisk = risk(n.x, n.y);

    const consider = (score, act, extra) => {
      if (ctx.noise) score += rand(st) * ctx.noise;
      if (!best || score > best.score) best = { score, unit: u.id, to: { x: n.x, y: n.y }, act, ...extra };
    };
    consider(base - tileRisk, 'wait');

    if (canCaptureAt(st, u, n.x, n.y)) {
      let v = terr === 'castle' ? AI_TUNE.captureCastle : AI_TUNE.captureVillage;
      if (ownerAt(st, n.x, n.y)) v += AI_TUNE.captureEnemyVillage;
      consider(base - tileRisk + v, 'capture');
    }
    if (canRepairAt(st, u, n.x, n.y)) consider(base - tileRisk + AI_TUNE.repair, 'repair');
    for (const v of destroyTargets(st, u, n.x, n.y))
      consider(base - tileRisk + (ownerAt(st, v.x, v.y) ? AI_TUNE.destroyVillage : AI_TUNE.destroyNeutral), 'destroy', { spot: v });
    if (d.canRaise) {
      const gs = st.graves.filter(g => dist(g.x, g.y, n.x, n.y) === 1 && !(unitAt(st, g.x, g.y) && unitAt(st, g.x, g.y).id !== u.id) && !(g.x === n.x && g.y === n.y));
      if (gs.length && allies.length < BAL.unitCap) consider(base - tileRisk + AI_TUNE.raise, 'raise', { grave: { x: gs[0].x, y: gs[0].y } });
    }
    for (const t of targetsFrom(st, u, n.x, n.y)) {
      const f = forecast(st, u, t, n.x, n.y);
      let v = f.dmg * unitWorth(t) / 100 - f.counter * worth / 100;
      if (f.kills) v += AI_TUNE.killBonus + unitWorth(t) / 10;
      else if (d.poison && !t.poison && !UNIT_DEFS[t.type].undead) v += AI_TUNE.poisonHit;
      if (!f.kills && d.slow && !t.slow) v += AI_TUNE.slowHit;
      if (UNIT_DEFS[t.type].commander) v *= 1.3;
      // после атаки юнит остаётся на клетке; убитая цель в свой ход уже не ударит
      const r = f.kills ? risk(n.x, n.y, t.id) : tileRisk;
      consider(base - r + v + 10, 'attack', { target: t.id });
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
    const ctx = { threats: threatMap(st, team), aggr: aggression(st, team), noise: (DIFFICULTY[st.difficulty] || DIFFICULTY.normal).aiNoise };
    let best = null;
    for (const u of ready) {
      const p = planUnit(st, u, ctx);
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
  if (a.act === 'repair') return actRepair(st, u);
  if (a.act === 'destroy') return actDestroy(st, u, a.spot.x, a.spot.y);
  return actWait(st, u);
}
