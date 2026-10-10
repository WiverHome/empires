/* ============ GAME: контроллер партии (ввод, ходы, ИИ, сценарий) ============ */
'use strict';

const GAME = {
  mission: null,
  st: null,
  mode: 'idle',       // idle | selected | action | targeting | raising | busy | dialog | over
  sel: null,          // id выбранного юнита
  orig: null,         // позиция до перемещения (для отмены)
  reach: null,
  fired: [],          // индексы сработавших триггеров
  aiRunning: false,
  session: 0,         // растёт при каждом запуске/выходе: старый цикл ИИ видит смену и выходит
};

const $ = sel => document.querySelector(sel);
const el = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };
const selUnit = () => GAME.sel ? unitById(GAME.st, GAME.sel) : null;
// Юнит на клетке, если игрок его видит (туман войны прячет чужих вне обзора)
function seenUnitAt(x, y) {
  const u = unitAt(GAME.st, x, y);
  if (!u || !GAME.st.fog || u.team === viewTeam()) return u;
  return visibleTiles(GAME.st, viewTeam()).has(key(x, y)) ? u : null;
}
const seen = list => GAME.st.fog ? list.filter(t => seenUnitAt(t.x, t.y)) : list;
const isHumanTurn = () => GAME.st && !GAME.st.teams[GAME.st.active].ai;
const humanTeams = () => GAME.st.order.filter(t => !GAME.st.teams[t].ai);
// Чью казну и отряд показывать: активного игрока, а в ход ИИ - последнего ходившего человека
function viewTeam() {
  const st = GAME.st;
  if (!st.teams[st.active].ai) return st.active;
  return GAME.lastHuman && st.teams[GAME.lastHuman] ? GAME.lastHuman : (humanTeams()[0] || st.order[0]);
}

/* ---------- Запуск миссии ---------- */
async function startMission(id, resume) {
  const mission = missionById(id);
  GAME.session++;
  GAME.aiRunning = false;
  GAME.mission = mission;
  GAME.fired = resume ? resume.fired.slice() : [];
  GAME.st = resume ? resume.state : createState(mission, {
    commanderLevel: mission.skirmish ? 0 : SAVE.data.heroLevel || 0,
    difficulty: SAVE.data.prefs.difficulty || 'normal',
  });
  GAME.lastHuman = null;
  GAME.sel = null;
  GAME.mode = 'busy';
  showScreen('game');
  resizeView();
  setViewState(GAME.st);
  VIEW.marks = mission.marks || null;
  const hero = commanderOf(GAME.st, viewTeam());
  if (hero) centerOn(hero.x, hero.y);
  updateHud();
  if (resume) {
    await showBanner();
    await startHumanTurn();
    return;
  }
  await showBanner(mission.title, mission.goal);
  await showDialog(mission.intro);
  const events = beginTurn(GAME.st);
  await afterTurnStart(events);
}

async function afterTurnStart(events) {
  const st = GAME.st;
  updateHud();
  await showBanner();
  const heals = events.filter(e => e.type === 'heal');
  if (heals.length && !st.teams[st.active].ai) await animEvents(heals);
  else if (heals.length) animEvents(heals);
  await runTriggers({ phase: 'turn', events });
  if (await checkOutcome()) return;
  if (st.teams[st.active].ai) runAI();
  else {
    if (humanTeams().length > 1) await passDevice();
    await startHumanTurn();
  }
}

// Игра вдвоём на одном устройстве: пауза, чтобы передать телефон
function passDevice() {
  const st = GAME.st, t = TEAMS[st.active];
  VIEW.fogTeam = st.fog ? 'none' : null;    // с туманом не показывать карту прошлого игрока
  return new Promise(resolve => {
    const m = openModal(`<h2 style="color:${t.light}">Ход: ${teamName(st, st.active)}</h2>
      <p class="muted">Передайте устройство игроку.</p>
      <div class="modal-actions"><button data-go>Начать ход</button></div>`, 'result');
    m.querySelector('[data-go]').onclick = () => { SND.play('click'); closeModal(); resolve(); };
  });
}

async function startHumanTurn() {
  GAME.mode = 'idle';
  GAME.lastHuman = GAME.st.active;
  SAVE.saveCurrent();
  updateHud();
  const hero = commanderOf(GAME.st, GAME.st.active);
  if (hero) ensureVisible(hero.x, hero.y);
}

async function endTurnFlow() {
  deselect();
  GAME.mode = 'busy';
  const events = endTurn(GAME.st);
  await afterTurnStart(events);
}

/* ---------- Сценарий ---------- */
async function runTriggers(ctx) {
  const list = GAME.mission.triggers || [];
  for (let i = 0; i < list.length; i++) {
    if (GAME.fired.includes(i)) continue;
    const t = list[i];
    if (!t.when(GAME.st, ctx)) continue;
    GAME.fired.push(i);
    const prevMode = GAME.mode;
    if (t.say) await showDialog(t.say);
    for (const s of t.spawn || []) {
      const u = spawnNear(GAME.st, s.type, s.team, s.x, s.y);
      if (u) { await ensureVisible(u.x, u.y); await animEvents([{ type: 'buy', unit: u.id, x: u.x, y: u.y }]); }
    }
    GAME.mode = prevMode;
  }
}

async function checkOutcome() {
  const res = evaluateOutcome(GAME.st, GAME.mission);
  if (!res) return false;
  GAME.mode = 'over';
  GAME.st.over = res;
  deselect();
  SAVE.clearCurrent();
  if (res === 'win') {
    SND.play('win');
    if (!GAME.mission.skirmish) {
      await showDialog(GAME.mission.outro);
      SAVE.missionWon(GAME.mission.id, GAME.st);
    }
  } else SND.play('lose');
  showResult(res);
  return true;
}

// Общая обработка после действия: анимация, триггеры, проверка исхода
async function afterAction(events) {
  updateHud();
  await animEvents(events);
  await runTriggers({ phase: 'events', events });
  updateHud();
  return checkOutcome();
}

/* ---------- ИИ ---------- */
async function runAI() {
  if (GAME.aiRunning) return;
  GAME.aiRunning = true;
  GAME.mode = 'busy';
  const session = GAME.session;
  const alive = () => GAME.session === session && GAME.st && GAME.mode !== 'over';
  try {
    for (;;) {
      if (!alive()) return;
      await sleep(30);
      const a = aiNextAction(GAME.st, GAME.mission);
      if (a.kind === 'end') { GAME.aiRunning = false; await endTurnFlow(); return; }
      if (a.kind === 'buy') {
        await ensureVisible(a.x, a.y);
        const ev = actBuy(GAME.st, GAME.st.active, a.type, a.x, a.y);
        if (await afterAction(ev)) return;
        continue;
      }
      const u = unitById(GAME.st, a.unit);
      const shown = !VIEW.vis || isVisible(u.x, u.y) || isVisible(a.to.x, a.to.y);
      if (shown) await ensureVisible(u.x, u.y);
      if (!alive()) return;
      const reach = reachable(GAME.st, u);
      const path = pathTo(reach, a.to.x, a.to.y);
      VIEW.selected = u.id;
      actMove(GAME.st, u, a.to.x, a.to.y);
      if (path.length > 1 && shown) { SND.play('move'); await animMove(u, path); }
      if (!alive()) return;
      let ev = [];
      if (a.act === 'attack') {
        const t = unitById(GAME.st, a.target);
        VIEW.hl.targets = [t];
        await sleep(200);
        VIEW.hl.targets = null;
        ev = actAttack(GAME.st, u, t);
      } else if (a.act === 'capture') ev = actCapture(GAME.st, u);
      else if (a.act === 'raise') ev = actRaise(GAME.st, u, graveAt(GAME.st, a.grave.x, a.grave.y));
      else if (a.act === 'repair') ev = actRepair(GAME.st, u);
      else if (a.act === 'destroy') ev = actDestroy(GAME.st, u, a.spot.x, a.spot.y);
      else ev = actWait(GAME.st, u);
      VIEW.selected = null;
      if (await afterAction(ev)) return;
    }
  } finally {
    if (GAME.session === session) GAME.aiRunning = false;
  }
}

/* ---------- Выбор и действия игрока ---------- */
function deselect() {
  GAME.sel = null;
  GAME.orig = null;
  GAME.reach = null;
  clearHighlights();
  hidePopup();
  if (GAME.mode !== 'over' && GAME.mode !== 'busy') GAME.mode = 'idle';
}

function selectUnit(u) {
  deselect();
  SND.play('select');
  GAME.sel = u.id;
  GAME.orig = { x: u.x, y: u.y };
  GAME.reach = reachable(GAME.st, u);
  VIEW.selected = u.id;
  VIEW.hl.reach = GAME.reach;
  const threat = threatTiles(GAME.st, u, GAME.reach);
  for (const k of GAME.reach.keys()) threat.delete(k);
  VIEW.hl.threat = [...threat];
  const inRange = seen(GAME.st.units.filter(t => isEnemy(t, u) && attackSpotFor(u, t)));
  VIEW.hl.targets = inRange.length ? inRange : null;
  GAME.mode = 'selected';
  showInfo(u.x, u.y);
}

/* ---------- Быстрая атака: нажатие на врага в зоне досягаемости ---------- */
// Лучшая клетка, откуда выбранный юнит может ударить цель в этом ходу:
// меньше ответного урона, затем больше защиты местности, затем короче путь.
function attackSpotFor(u, t) {
  const d = UNIT_DEFS[u.type];
  if (d.noAttack) return null;
  let best = null;
  for (const n of GAME.reach.values()) {
    if (d.noMoveAttack && (n.x !== GAME.orig.x || n.y !== GAME.orig.y)) continue;
    const r = dist(n.x, n.y, t.x, t.y);
    if (r < d.range[0] || r > d.range[1]) continue;
    const f = forecast(GAME.st, u, t, n.x, n.y);
    const score = -f.counter * 10 + unitDef(GAME.st, u, n.x, n.y) - n.cost * 0.5;
    if (!best || score > best.score) best = { x: n.x, y: n.y, score };
  }
  return best;
}

function confirmAttack(u, t, spot) {
  GAME.mode = 'confirm';
  GAME.pending = { target: t.id, x: spot.x, y: spot.y };
  VIEW.hl.reach = null;
  VIEW.hl.threat = null;
  VIEW.hl.targets = [t];
  VIEW.hl.path = pathTo(GAME.reach, spot.x, spot.y);
  const f = forecast(GAME.st, u, t, spot.x, spot.y);
  const left = Math.max(0, t.hp - f.dmg);
  const m = $('#popup');
  m.innerHTML = `<div class="confirm-info"><b>${UNIT_DEFS[t.type].name}</b>
    <div>Урон: <b>~${f.dmg}</b> (${t.hp} → ${left})</div>
    ${f.kills ? '<div class="good">Добьёт</div>' : `<div>Ответ: <b class="${f.counter ? 'bad' : ''}">~${f.counter}</b></div>`}</div>`;
  const go = el('button', '', 'Атаковать');
  go.onclick = e => { e.stopPropagation(); SND.play('click'); executePendingAttack(); };
  const no = el('button', 'secondary', 'Отмена');
  no.onclick = e => { e.stopPropagation(); SND.play('click'); cancelConfirm(); };
  const btns = el('div', 'confirm-btns');
  btns.append(go, no);
  m.append(btns);
  m.classList.add('confirm');
  m.hidden = false;
  // внизу карты, над панелью информации: не закрывает ни цель, ни путь
  const r = $('#map-wrap').getBoundingClientRect();
  m.style.left = Math.max(8, r.left + (r.width - m.offsetWidth) / 2) + 'px';
  m.style.top = (r.bottom - m.offsetHeight - 8) + 'px';
}

function cancelConfirm() {
  GAME.pending = null;
  hidePopup();
  const u = selUnit();
  if (u) selectUnit(u); else deselect();
}

async function executePendingAttack() {
  const u = selUnit(), p = GAME.pending;
  const t = p && unitById(GAME.st, p.target);
  GAME.pending = null;
  hidePopup();
  if (!u || !t) { deselect(); return; }
  const path = pathTo(GAME.reach, p.x, p.y);
  GAME.mode = 'busy';
  VIEW.hl.path = null;
  actMove(GAME.st, u, p.x, p.y);
  if (path.length > 1) { SND.play('move'); await animMove(u, path); }
  doAct(u, () => actAttack(GAME.st, u, t));
}

async function moveSelected(x, y) {
  const u = selUnit();
  const path = pathTo(GAME.reach, x, y);
  GAME.mode = 'busy';
  VIEW.hl.reach = VIEW.hl.threat = VIEW.hl.path = null;
  actMove(GAME.st, u, x, y);
  if (path.length > 1) { SND.play('move'); await animMove(u, path); }
  openActionMenu(u);
}

function undoMove() {
  const u = selUnit();
  if (u && GAME.orig) { u.x = GAME.orig.x; u.y = GAME.orig.y; u.moved = false; }
  hidePopup();
  if (u) selectUnit(u); else deselect();
}

function unitTargets(u) {
  const d = UNIT_DEFS[u.type];
  if (d.noAttack || (d.noMoveAttack && (GAME.orig.x !== u.x || GAME.orig.y !== u.y))) return [];
  const [rMin, rMax] = d.range;
  return seen(GAME.st.units.filter(t => isEnemy(t, u) && dist(u.x, u.y, t.x, t.y) >= rMin && dist(u.x, u.y, t.x, t.y) <= rMax));
}

function openActionMenu(u) {
  GAME.mode = 'action';
  VIEW.hl.targets = null; VIEW.hl.graves = null;
  const opts = [];
  const targets = unitTargets(u);
  if (targets.length) opts.push(['Атаковать', () => startTargeting(u, targets)]);
  if (canCaptureAt(GAME.st, u)) opts.push(['Захватить', () => doAct(u, () => actCapture(GAME.st, u))]);
  if (canRepairAt(GAME.st, u)) opts.push(['Отстроить', () => doAct(u, () => actRepair(GAME.st, u))]);
  const ruinable = GAME.orig.x === u.x && GAME.orig.y === u.y ? destroyTargets(GAME.st, u) : [];
  if (ruinable.length) opts.push(['Разрушить', () => startDestroying(u, ruinable)]);
  const graves = UNIT_DEFS[u.type].canRaise ? gravesNear(GAME.st, u.x, u.y) : [];
  if (graves.length && teamUnits(GAME.st, u.team).length < BAL.unitCap) opts.push(['Поднять мертвеца', () => startRaising(u, graves)]);
  opts.push(['Ждать', () => doAct(u, () => actWait(GAME.st, u))]);
  opts.push(['Отмена', undoMove]);
  showPopupMenu(u.x, u.y, opts);
  // подсветить цели, чтобы их было видно сразу
  if (targets.length) VIEW.hl.threat = targets.map(t => key(t.x, t.y));
}

function startTargeting(u, targets) {
  hidePopup();
  GAME.mode = 'targeting';
  VIEW.hl.threat = null;
  VIEW.hl.targets = targets;
  setHint('Выберите цель. Esc - назад.');
}

function startRaising(u, graves) {
  hidePopup();
  GAME.mode = 'raising';
  VIEW.hl.graves = graves;
  setHint('Выберите могилу. Esc - назад.');
}

function startDestroying(u, spots) {
  hidePopup();
  GAME.mode = 'destroying';
  VIEW.hl.graves = spots;
  setHint('Выберите деревню. Esc - назад.');
}

async function doAct(u, fn) {
  hidePopup();
  setHint('');
  GAME.mode = 'busy';
  VIEW.hl.targets = VIEW.hl.graves = VIEW.hl.threat = null;
  const ev = fn();
  GAME.sel = null; GAME.orig = null; GAME.reach = null; VIEW.selected = null;
  if (await afterAction(ev)) return;
  GAME.mode = 'idle';
  SAVE.saveCurrent();
  updateHud();
}

/* ---------- Ввод ---------- */
function onTileClick(x, y) {
  const st = GAME.st;
  if (!st || !isHumanTurn()) return;
  const u = seenUnitAt(x, y);
  const sel = selUnit();
  switch (GAME.mode) {
    case 'idle':
    case 'selected': {
      if (GAME.mode === 'selected' && sel && GAME.reach.has(key(x, y))) { moveSelected(x, y); return; }
      if (GAME.mode === 'selected' && sel && u && isEnemy(u, sel)) {
        const spot = attackSpotFor(sel, u);
        if (spot) { confirmAttack(sel, u, spot); return; }
      }
      if (u && u.team === st.active && !u.acted) { selectUnit(u); return; }
      deselect();
      if (u) {
        // показать, куда может ударить чужой или походивший юнит
        VIEW.hl.enemy = [...threatTiles(st, u, reachable(st, { ...u }))];
        VIEW.selected = u.id;
        showInfo(x, y);
        return;
      }
      if (terrainAt(st, x, y) === 'castle' && ownerAt(st, x, y) === st.active) { openBuy(x, y); return; }
      showInfo(x, y);
      return;
    }
    case 'action': {
      const t = u && unitTargets(sel).find(o => o.id === u.id);
      if (t) { doAct(sel, () => actAttack(st, sel, t)); return; }
      undoMove();
      return;
    }
    case 'targeting': {
      const t = u && VIEW.hl.targets.find(o => o.id === u.id);
      if (t) doAct(sel, () => actAttack(st, sel, t));
      else { setHint(''); hideForecast(); openActionMenu(sel); }
      return;
    }
    case 'confirm': {
      // повторное нажатие на ту же цель подтверждает атаку, на другую цель - переключает
      if (u && GAME.pending && u.id === GAME.pending.target) { executePendingAttack(); return; }
      const spot = u && isEnemy(u, sel) && attackSpotFor(sel, u);
      if (spot) { confirmAttack(sel, u, spot); return; }
      cancelConfirm();
      if (GAME.reach.has(key(x, y))) moveSelected(x, y);
      return;
    }
    case 'raising': {
      const gr = VIEW.hl.graves.find(g => g.x === x && g.y === y);
      if (gr) doAct(sel, () => actRaise(st, sel, gr));
      else { setHint(''); openActionMenu(sel); }
      return;
    }
    case 'destroying': {
      const v = VIEW.hl.graves.find(g => g.x === x && g.y === y);
      if (v) doAct(sel, () => actDestroy(st, sel, v.x, v.y));
      else { setHint(''); openActionMenu(sel); }
      return;
    }
  }
}

function onTileHover(t) {
  VIEW.hover = t;
  if (!t || !GAME.st) { hideForecast(); return; }
  if (GAME.mode === 'selected' && GAME.reach) {
    const enemy = seenUnitAt(t.x, t.y);
    const spot = enemy && isEnemy(enemy, selUnit()) && attackSpotFor(selUnit(), enemy);
    VIEW.hl.path = GAME.reach.has(key(t.x, t.y)) ? pathTo(GAME.reach, t.x, t.y) : spot ? pathTo(GAME.reach, spot.x, spot.y) : null;
  }
  if (GAME.mode === 'targeting') {
    const target = VIEW.hl.targets.find(o => o.x === t.x && o.y === t.y);
    if (target) showForecast(selUnit(), target); else hideForecast();
  }
  if (GAME.mode === 'idle' || GAME.mode === 'selected') showInfo(t.x, t.y);
}

function onCancel() {
  if (GAME.mode === 'targeting' || GAME.mode === 'raising' || GAME.mode === 'destroying') { setHint(''); hideForecast(); openActionMenu(selUnit()); }
  else if (GAME.mode === 'action') undoMove();
  else if (GAME.mode === 'confirm') cancelConfirm();
  else if (GAME.mode === 'selected' || GAME.mode === 'idle') deselect();
}

/* ---------- Интерфейс: HUD, инфо, всплывающие окна ---------- */
function updateHud() {
  const st = GAME.st;
  if (!st) return;
  const team = st.active, t = TEAMS[team], me = viewTeam();
  VIEW.fogTeam = st.fog ? me : null;
  const turnTxt = GAME.mission.turnLimit ? `Ход ${st.turn}/${GAME.mission.turnLimit}` : `Ход ${st.turn}`;
  $('#hud-turn').innerHTML = `<span class="chip" style="background:${t.color}"></span>${turnTxt}<span class="hide-narrow"> · ${teamName(st, team)}</span>`;
  const mine = st.teams[me];
  $('#hud-gold').innerHTML = `<span class="coin" style="outline:2px solid ${TEAMS[me].color}"></span>${mine.gold} <small>+${incomeOf(st, me)}</small>`;
  $('#hud-units').textContent = `Отряд ${teamUnits(st, me).length}/${BAL.unitCap}`;
  const human = isHumanTurn() && GAME.mode !== 'over';
  $('#btn-end').disabled = !human;
  const allDone = human && teamUnits(st, team).every(u => u.acted);
  $('#btn-end').classList.toggle('pulse', allDone);
}

function showInfo(x, y) {
  const st = GAME.st, box = $('#info');
  const terr = TERRAIN[terrainAt(st, x, y)];
  const owner = ownerAt(st, x, y);
  let html = `<div class="terr"><b>${terr.name}</b> <span>защита +${terr.def}</span>`;
  if (terr.building) html += ` <span>${owner ? teamName(st, owner) : 'ничья'}, +${terr.income} золота</span>`;
  if (isFinite(terr.cost) && terr.cost > 1) html += ` <span>ход ${terr.cost}</span>`;
  if (!isFinite(terr.cost)) html += ` <span>${terr.water ? 'летающим и водным' : 'только для летающих'}</span>`;
  else if (terr.water) html += ` <span>водным: ход 1, защита +${BAL.waterDef}</span>`;
  html += '</div>';
  const u = seenUnitAt(x, y);
  if (u) {
    const d = UNIT_DEFS[u.type];
    const [lo, hi] = unitAtk(u, st);
    const baseDef = d.def + u.level * BAL.levelDef - (u.poison ? BAL.poisonDef : 0);
    const terrDef = terrainDefFor(st, u, x, y);
    html += `<div class="unit"><img src="${spriteDataUrl(u.type, u.team, 3)}" alt="">
      <div><b style="color:${TEAMS[u.team].light}">${d.name}</b>${u.level ? ` <span class="lvl">★${u.level}</span>` : ''}
      <div class="stats">
        <span title="Здоровье">❤ ${u.hp}</span>
        ${d.noAttack ? '' : `<span title="Атака">⚔ ${lo}-${hi}</span>`}
        <span title="Защита (своя + местность)">⛨ ${baseDef}${terrDef ? '+' + terrDef : ''}</span>
        <span title="Очки хода">➜ ${d.move}</span>
        ${d.noAttack ? '' : `<span title="Дальность">◎ ${d.range[0] === d.range[1] ? d.range[0] : d.range.join('-')}</span>`}
      </div>
      <div class="xp"><i style="width:${u.level >= BAL.maxLevel ? 100 : u.xp / BAL.xpPerLevel * 100}%"></i></div>
      ${u.poison ? `<div class="bad">Отравлен: −${BAL.poisonAtk} атаки и защиты, ещё ${u.poison} х.</div>` : ''}
      <div class="desc">${d.desc}</div></div></div>`;
  }
  box.innerHTML = html;
  box.hidden = false;
}

function showPopupMenu(x, y, opts) {
  const m = $('#popup');
  m.innerHTML = '';
  for (const [label, fn] of opts) {
    const b = el('button', '', label);
    b.onclick = e => { e.stopPropagation(); SND.play('click'); fn(); };
    m.appendChild(b);
  }
  m.hidden = false;
  placeNearTile(m, x, y);
}

function hidePopup() { const p = $('#popup'); p.hidden = true; p.classList.remove('confirm'); hideForecast(); }

function placeNearTile(node, x, y) {
  const r = VIEW.canvas.getBoundingClientRect(), k = 1 / VIEW.dpr, ts = VIEW.ts * k;
  let left = r.left + (x + 1) * ts - VIEW.camX * k + 8;
  let top = r.top + y * ts - VIEW.camY * k;
  const w = node.offsetWidth, h = node.offsetHeight;
  if (left + w > window.innerWidth - 8) left = r.left + x * ts - VIEW.camX * k - w - 8;
  top = Math.max(r.top + 4, Math.min(r.bottom - h - 4, top));
  left = Math.max(8, left);
  node.style.left = left + 'px';
  node.style.top = top + 'px';
}

function showForecast(att, def) {
  const f = forecast(GAME.st, att, def);
  const box = $('#forecast');
  const left = Math.max(0, def.hp - f.dmg);
  box.innerHTML = `<div>Урон: <b>~${f.dmg}</b> (${def.hp} → ${left})</div>` +
    (f.kills ? '<div class="good">Добьёт</div>' : `<div>Ответ: <b class="${f.counter ? 'bad' : ''}">~${f.counter}</b></div>`);
  box.hidden = false;
  placeNearTile(box, def.x, def.y);
}
function hideForecast() { $('#forecast').hidden = true; }

function setHint(text) {
  const h = $('#hint');
  h.textContent = text;
  h.hidden = !text;
}

/* ---------- Баннер хода ---------- */
async function showBanner(title, sub) {
  const st = GAME.st, b = $('#banner');
  const t = TEAMS[st.active];
  b.innerHTML = title
    ? `<div class="b-title">${title}</div><div class="b-sub">${sub}</div>`
    : `<div class="b-title" style="color:${t.light}">${teamName(st, st.active)}</div><div class="b-sub">Ход ${st.turn}</div>`;
  b.style.setProperty('--team', title ? '#c9a24a' : t.color);
  b.hidden = false;
  b.classList.remove('show');
  void b.offsetWidth;
  b.classList.add('show');
  SND.play('turn');
  await sleep(title ? 1800 : 1000);
  b.hidden = true;
}

/* ---------- Диалоги ---------- */
function showDialog(lines) {
  if (!lines || !lines.length) return Promise.resolve();
  return new Promise(resolve => {
    const box = $('#dialog');
    const prevMode = GAME.mode;
    GAME.mode = 'dialog';
    let i = 0;
    const render = () => {
      const l = lines[i], sp = SPEAKERS[l.who];
      box.innerHTML = `<img class="portrait" src="${portraitDataUrl(sp.sprite, sp.team, 5)}" alt="">
        <div class="d-body"><div class="d-name" style="color:${TEAMS[sp.team].light}">${sp.name}</div>
        <div class="d-text">${l.text}</div><div class="d-next">${i < lines.length - 1 ? 'Далее ▸' : 'Закрыть ▸'}</div></div>`;
    };
    const next = e => {
      if (e && e.type === 'keydown' && !['Enter', ' ', 'Escape'].includes(e.key)) return;
      if (e) e.preventDefault();
      SND.play('click');
      i++;
      if (i >= lines.length) {
        box.hidden = true;
        box.onclick = null;
        window.removeEventListener('keydown', next, true);
        GAME.mode = prevMode;
        resolve();
      } else render();
    };
    box.onclick = () => next();
    window.addEventListener('keydown', next, true);
    render();
    box.hidden = false;
  });
}

/* ---------- Модальные окна ---------- */
function openModal(html, cls = '') {
  const root = $('#modal-root');
  root.innerHTML = `<div class="modal ${cls}">${html}</div>`;
  root.hidden = false;
  return root.firstChild;
}
function closeModal() { const r = $('#modal-root'); r.hidden = true; r.innerHTML = ''; }

function openBuy(x, y) {
  const st = GAME.st, team = st.active;
  const list = buyOptions(st, team);
  const m = openModal(`<h2>Замок: наём</h2><div class="gold-line"><span class="coin"></span>${st.teams[team].gold}</div><div class="buy-list"></div>
    <div class="modal-actions"><button class="secondary" data-close>Закрыть</button></div>`, 'buy');
  const listBox = m.querySelector('.buy-list');
  for (const type of list) {
    const d = UNIT_DEFS[type];
    const cost = buyCost(st, team, type);
    const ok = canBuy(st, team, type, x, y);
    const b = el('button', 'buy-item' + (ok ? '' : ' disabled'));
    b.innerHTML = `<img src="${spriteDataUrl(type, team, 3)}" alt=""><div class="bi-body"><b>${d.commander ? 'Вернуть: ' + d.name : d.name}</b>
      <div class="bi-stats">⚔ ${d.atk.join('-')} ⛨ ${d.def} ➜ ${d.move} ◎ ${d.range[0] === d.range[1] ? d.range[0] : d.range.join('-')}</div>
      <div class="bi-desc">${d.desc}</div></div><div class="bi-cost"><span class="coin"></span>${cost}</div>`;
    if (ok) b.onclick = async () => {
      closeModal();
      GAME.mode = 'busy';
      const ev = actBuy(st, team, type, x, y);
      if (await afterAction(ev)) return;
      GAME.mode = 'idle';
      SAVE.saveCurrent();
    };
    listBox.appendChild(b);
  }
  if (teamUnits(st, team).length >= BAL.unitCap) listBox.insertAdjacentHTML('beforebegin', `<p class="warn">Достигнут предел отряда (${BAL.unitCap}).</p>`);
  m.querySelector('[data-close]').onclick = closeModal;
}

function showResult(res) {
  if (GAME.mission.skirmish) return showSkirmishResult(res);
  const win = res === 'win';
  const idx = MISSIONS.findIndex(m => m.id === GAME.mission.id);
  const next = MISSIONS[idx + 1];
  const m = openModal(`<h2 class="${win ? 'good' : 'bad'}">${win ? 'Победа!' : 'Поражение'}</h2>
    <p>${win ? (next ? 'Открыта следующая миссия.' : 'Кампания пройдена. Спасибо за игру!') : GAME.st.teams.blue.commanderDead ? 'Ардан пал. Без командира войско отступает.' : 'Задача миссии провалена.'}</p>
    <p class="muted">Ходов: ${GAME.st.turn}</p>
    <div class="modal-actions">
      ${win && next ? '<button data-next>Следующая миссия</button>' : ''}
      ${!win ? '<button data-retry>Переиграть</button>' : ''}
      <button class="secondary" data-menu>В меню</button>
    </div>`, 'result');
  const nx = m.querySelector('[data-next]');
  if (nx) nx.onclick = () => { closeModal(); startMission(next.id); };
  const rt = m.querySelector('[data-retry]');
  if (rt) rt.onclick = () => { closeModal(); startMission(GAME.mission.id); };
  m.querySelector('[data-menu]').onclick = () => { closeModal(); quitToTitle(); };
}

function showSkirmishResult(res) {
  const st = GAME.st, w = st.winner;
  const hotseat = humanTeams().length > 1;
  const title = res === 'win' ? (hotseat ? `Победа: ${teamName(st, w)}` : 'Победа!') : 'Поражение';
  const m = openModal(`<h2 class="${res === 'win' ? 'good' : 'bad'}"${hotseat && w ? ` style="color:${TEAMS[w].light}"` : ''}>${title}</h2>
    <p>${res === 'win' ? 'Все противники разбиты.' : 'Ваши войска разбиты.'}</p>
    <p class="muted">Ходов: ${st.turn}</p>
    <div class="modal-actions">
      <button data-retry>Ещё раз</button>
      <button class="secondary" data-menu>В меню</button>
    </div>`, 'result');
  m.querySelector('[data-retry]').onclick = () => { closeModal(); startMission(GAME.mission.id); };
  m.querySelector('[data-menu]').onclick = () => { closeModal(); quitToTitle(); };
}

function openGameMenu() {
  if (!GAME.st) return;
  const m = openModal(`<h2>${GAME.mission.title}</h2><p class="goal">${GAME.mission.goal}</p>
    <div class="menu-col">
      <button data-resume>Продолжить</button>
      <button data-sound class="secondary">Звук: ${SND.muted ? 'выкл' : 'вкл'}</button>
      <button data-rules class="secondary">Как играть</button>
      <button data-restart class="secondary">Начать заново</button>
      <button data-quit class="secondary">Выйти в меню</button>
    </div>`);
  m.querySelector('[data-resume]').onclick = closeModal;
  m.querySelector('[data-sound]').onclick = e => { SND.muted = !SND.muted; SAVE.setPref('muted', SND.muted); e.target.textContent = 'Звук: ' + (SND.muted ? 'выкл' : 'вкл'); };
  m.querySelector('[data-rules]').onclick = showRules;
  m.querySelector('[data-restart]').onclick = () => { if (GAME.aiRunning) return; closeModal(); startMission(GAME.mission.id); };
  m.querySelector('[data-quit]').onclick = () => { closeModal(); quitToTitle(); };
}

function quitToTitle() {
  GAME.session++;
  GAME.aiRunning = false;
  GAME.mode = 'over';
  GAME.st = null;
  VIEW.st = null;
  showScreen('title');
  renderTitle();
}
