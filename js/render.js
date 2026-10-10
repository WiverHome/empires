/* ============ RENDER: холст карты, камера, подсветка, анимации ============ */
'use strict';

const ANIM = {
  moveStep: 90,     // мс на клетку
  lunge: 140,
  hit: 260,
  death: 420,
  floater: 900,
  water: 650,       // смена кадра воды
};

const VIEW = {
  canvas: null, g: null,
  zoom: 3, ts: 48,
  camX: 0, camY: 0,
  layers: null,
  st: null,
  hover: null,
  hl: { reach: null, threat: null, targets: null, graves: null, path: null, enemy: null },
  selected: null,
  offsets: new Map(),   // id -> { dx, dy, alpha, flash }
  floaters: [],
  shake: 0,
};

function initView(canvas) {
  VIEW.canvas = canvas;
  VIEW.g = canvas.getContext('2d');
  window.addEventListener('resize', resizeView);
  resizeView();
  requestAnimationFrame(frame);
}

function setViewState(st) {
  VIEW.st = st;
  VIEW.layers = [renderTerrainLayer(st, 0), renderTerrainLayer(st, 1)];
  clearHighlights();
  VIEW.offsets.clear();
  VIEW.floaters = [];
  zoomFit();
}

// Масштаб: по умолчанию карта целиком вписана в экран (VIEW.fitTs), приближать можно до 2.5x.
// Все координаты холста в физических пикселях (с учётом devicePixelRatio).
const ZOOM_MAX = 2.5;

function fitTs() {
  const st = VIEW.st, c = VIEW.canvas;
  if (!st) return TILE * 3;
  return Math.max(8, Math.floor(Math.min(c.width / st.w, c.height / st.h)));
}

function setTileSize(ts) {
  const c = VIEW.canvas;
  const cx = VIEW.camX + c.width / 2, cy = VIEW.camY + c.height / 2;
  const tx = cx / VIEW.ts, ty = cy / VIEW.ts;
  const fit = fitTs();
  VIEW.ts = Math.round(Math.max(fit, Math.min(fit * ZOOM_MAX, ts)));
  VIEW.zoom = VIEW.ts / TILE;
  VIEW.camX = tx * VIEW.ts - c.width / 2;
  VIEW.camY = ty * VIEW.ts - c.height / 2;
  clampCamera();
}

function zoomStep(dir) { setTileSize(dir > 0 ? VIEW.ts * 1.25 : VIEW.ts / 1.25); }
function zoomFit() { setTileSize(0); }

function resizeView() {
  const c = VIEW.canvas;
  VIEW.dpr = window.devicePixelRatio || 1;
  c.width = Math.floor(c.clientWidth * VIEW.dpr);
  c.height = Math.floor(c.clientHeight * VIEW.dpr);
  VIEW.g.imageSmoothingEnabled = false;
  zoomFit();
}

function clampCamera() {
  const st = VIEW.st;
  if (!st || !VIEW.canvas) return;
  const mw = st.w * VIEW.ts, mh = st.h * VIEW.ts, cw = VIEW.canvas.width, ch = VIEW.canvas.height;
  VIEW.camX = mw <= cw ? -(cw - mw) / 2 : Math.max(0, Math.min(mw - cw, VIEW.camX));
  VIEW.camY = mh <= ch ? -(ch - mh) / 2 : Math.max(0, Math.min(mh - ch, VIEW.camY));
}

function centerOn(x, y) {
  VIEW.camX = (x + 0.5) * VIEW.ts - VIEW.canvas.width / 2;
  VIEW.camY = (y + 0.5) * VIEW.ts - VIEW.canvas.height / 2;
  clampCamera();
}

// Плавно подвести камеру, если клетка у края экрана
function ensureVisible(x, y) {
  const st = VIEW.st;
  if (!st || (st.w * VIEW.ts <= VIEW.canvas.width && st.h * VIEW.ts <= VIEW.canvas.height)) return Promise.resolve();
  const m = VIEW.ts * 1.5, sx = x * VIEW.ts - VIEW.camX, sy = y * VIEW.ts - VIEW.camY;
  if (sx < m || sy < m || sx > VIEW.canvas.width - m - VIEW.ts || sy > VIEW.canvas.height - m - VIEW.ts) {
    const fx = VIEW.camX, fy = VIEW.camY;
    centerOn(x, y);
    const tx = VIEW.camX, ty = VIEW.camY;
    VIEW.camX = fx; VIEW.camY = fy;
    return tween(250, k => { VIEW.camX = fx + (tx - fx) * k; VIEW.camY = fy + (ty - fy) * k; });
  }
  return Promise.resolve();
}

function screenToTile(px, py) {
  const x = Math.floor((px + VIEW.camX) / VIEW.ts), y = Math.floor((py + VIEW.camY) / VIEW.ts);
  return VIEW.st && inBounds(VIEW.st, x, y) ? { x, y } : null;
}

function clearHighlights() {
  VIEW.hl = { reach: null, threat: null, targets: null, graves: null, path: null, enemy: null };
  VIEW.selected = null;
}

/* ---------- Твины и анимации ---------- */
function tween(ms, fn) {
  return new Promise(res => {
    const t0 = performance.now();
    const step = now => {
      const k = Math.min(1, (now - t0) / ms);
      fn(k);
      if (k < 1) requestAnimationFrame(step); else res();
    };
    requestAnimationFrame(step);
  });
}
const sleep = ms => new Promise(r => setTimeout(r, ms));
const ease = k => k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2;

function offsetOf(id) {
  if (!VIEW.offsets.has(id)) VIEW.offsets.set(id, { dx: 0, dy: 0, alpha: 1, flash: 0 });
  return VIEW.offsets.get(id);
}

async function animMove(u, path) {
  if (path.length < 2) return;
  const o = offsetOf(u.id);
  const end = path[path.length - 1];
  // юнит уже стоит в конце пути: смещаем его назад по пути
  for (let i = 1; i < path.length; i++) {
    const a = path[i - 1], b = path[i];
    await tween(ANIM.moveStep, k => {
      o.dx = (a.x + (b.x - a.x) * k - end.x);
      o.dy = (a.y + (b.y - a.y) * k - end.y);
    });
  }
  o.dx = o.dy = 0;
}

function floater(text, x, y, color, delay = 0) {
  VIEW.floaters.push({ text, x, y, color, t0: performance.now() + delay });
}

async function animEvents(events) {
  for (const e of events) {
    if (e.type === 'hit') {
      const att = offsetOf(e.from), def = offsetOf(e.to);
      const dx = Math.sign(e.x - e.fx) * 0.3, dy = Math.sign(e.y - e.fy) * 0.3;
      SND.play('swing');
      await tween(ANIM.lunge, k => { const s = Math.sin(k * Math.PI); att.dx = dx * s; att.dy = dy * s; });
      SND.play('hit');
      floater('-' + e.dmg, e.x, e.y, '#ff5a4a');
      VIEW.shake = e.dmg >= 40 ? 6 : 3;
      await tween(ANIM.hit, k => { def.flash = 1 - k; def.dx = Math.sin(k * 30) * 0.05 * (1 - k); });
      def.dx = 0;
    } else if (e.type === 'death') {
      // юнит уже удалён из состояния: рисуем «призрак» до конца анимации
      VIEW.dying = VIEW.dying || [];
      const ghost = { type: e.unitType, team: e.team, x: e.x, y: e.y, alpha: 1 };
      VIEW.dying.push(ghost);
      SND.play('death');
      await tween(ANIM.death, k => { ghost.alpha = 1 - k; });
      VIEW.dying = VIEW.dying.filter(d => d !== ghost);
    } else if (e.type === 'levelup') {
      SND.play('level');
      floater('Уровень ' + e.level + '!', e.x, e.y, '#ffd84a', 150);
      await sleep(250);
    } else if (e.type === 'capture') {
      SND.play('capture');
      floater(e.terrain === 'castle' ? 'Замок захвачен' : 'Захват', e.x, e.y, TEAMS[e.team].light);
      await sleep(350);
    } else if (e.type === 'heal') {
      floater('+' + e.amount, e.x, e.y, '#7dff8a');
    } else if (e.type === 'raise') {
      SND.play('raise');
      const o = offsetOf(e.unit);
      await tween(400, k => { o.alpha = k; });
    } else if (e.type === 'buy') {
      SND.play('buy');
      const o = offsetOf(e.unit);
      await tween(250, k => { o.alpha = k; });
    }
  }
}

/* ---------- Кадр ---------- */
function frame(now) {
  requestAnimationFrame(frame);
  const { g, canvas, st } = VIEW;
  g.fillStyle = '#1a1622';
  g.fillRect(0, 0, canvas.width, canvas.height);
  if (!st || !VIEW.layers) return;
  const ts = VIEW.ts, z = VIEW.zoom;
  let sx = 0, sy = 0;
  if (VIEW.shake > 0) { sx = (Math.random() - 0.5) * VIEW.shake; sy = (Math.random() - 0.5) * VIEW.shake; VIEW.shake *= 0.85; if (VIEW.shake < 0.5) VIEW.shake = 0; }
  g.save();
  g.translate(Math.round(-VIEW.camX + sx), Math.round(-VIEW.camY + sy));
  g.imageSmoothingEnabled = false;

  const layer = VIEW.layers[Math.floor(now / ANIM.water) % 2];
  g.drawImage(layer, 0, 0, st.w * ts, st.h * ts);

  // флаги владельцев
  for (const k in st.owner) {
    const [x, y] = k.split(',').map(Number);
    drawFlag(g, x, y, st.owner[k]);
  }
  // могилы
  for (const gr of st.graves) g.drawImage(graveSprite(), gr.x * ts, gr.y * ts, ts, ts);

  // подсветка
  const hl = VIEW.hl;
  const pulse = 0.5 + 0.5 * Math.sin(now / 180);
  if (hl.enemy) fillTiles(g, hl.enemy, 'rgba(255,70,60,0.28)');
  if (hl.reach) fillTiles(g, [...hl.reach.keys()], 'rgba(90,170,255,0.38)', 'rgba(190,225,255,0.55)');
  if (hl.threat) fillTiles(g, hl.threat, 'rgba(255,70,60,0.22)');
  if (hl.targets) for (const t of hl.targets) frameTile(g, t.x, t.y, `rgba(255,80,60,${0.6 + 0.4 * pulse})`, 3);
  if (hl.graves) for (const t of hl.graves) frameTile(g, t.x, t.y, `rgba(160,255,200,${0.6 + 0.4 * pulse})`, 3);
  if (hl.path) drawPath(g, hl.path);

  // курсор
  if (VIEW.hover) {
    const h = VIEW.hover;
    const bob = Math.round(pulse * z);
    g.globalAlpha = 0.9;
    const s = SHEETS.dungeon;
    g.drawImage(s, (60 % 12) * TILE, Math.floor(60 / 12) * TILE, TILE, TILE, h.x * ts - bob, h.y * ts - bob, ts + 2 * bob, ts + 2 * bob);
    g.globalAlpha = 1;
  }

  // юниты (сверху вниз, чтобы нижние перекрывали верхние)
  const units = st.units.slice().sort((a, b) => a.y - b.y);
  for (const u of units) drawUnit(g, u, now);
  for (const d of VIEW.dying || []) {
    g.globalAlpha = d.alpha;
    g.drawImage(unitSprite(d.type, d.team, { flip: d.team === 'red' }), d.x * ts - z, d.y * ts - z, ts + 2 * z, ts + 2 * z);
    g.globalAlpha = 1;
  }

  // всплывающие тексты
  VIEW.floaters = VIEW.floaters.filter(f => now - f.t0 < ANIM.floater);
  g.textAlign = 'center';
  g.font = `bold ${Math.round(z * 5.5)}px "Trebuchet MS", sans-serif`;
  for (const f of VIEW.floaters) {
    const k = (now - f.t0) / ANIM.floater;
    if (k < 0) continue;
    const x = (f.x + 0.5) * ts, y = f.y * ts + ts * 0.3 - k * ts * 0.6;
    g.globalAlpha = k > 0.7 ? (1 - k) / 0.3 : 1;
    g.lineWidth = z * 1.5;
    g.strokeStyle = '#1a1020';
    g.strokeText(f.text, x, y);
    g.fillStyle = f.color;
    g.fillText(f.text, x, y);
    g.globalAlpha = 1;
  }
  g.restore();
}

function fillTiles(g, keys, fill, edge) {
  const ts = VIEW.ts;
  g.fillStyle = fill;
  for (const k of keys) {
    const [x, y] = k.split(',').map(Number);
    g.fillRect(x * ts, y * ts, ts, ts);
  }
  if (edge) {
    g.strokeStyle = edge;
    g.lineWidth = 1;
    for (const k of keys) {
      const [x, y] = k.split(',').map(Number);
      g.strokeRect(x * ts + 0.5, y * ts + 0.5, ts - 1, ts - 1);
    }
  }
}

function frameTile(g, x, y, color, w) {
  const ts = VIEW.ts;
  g.strokeStyle = color;
  g.lineWidth = w;
  g.strokeRect(x * ts + w / 2, y * ts + w / 2, ts - w, ts - w);
}

function drawPath(g, path) {
  if (path.length < 2) return;
  const ts = VIEW.ts;
  g.strokeStyle = 'rgba(255,255,255,0.85)';
  g.lineWidth = VIEW.zoom * 2;
  g.lineCap = g.lineJoin = 'round';
  g.beginPath();
  path.forEach((p, i) => (i ? g.lineTo : g.moveTo).call(g, (p.x + 0.5) * ts, (p.y + 0.5) * ts));
  g.stroke();
  const e = path[path.length - 1];
  g.fillStyle = '#fff';
  g.beginPath();
  g.arc((e.x + 0.5) * ts, (e.y + 0.5) * ts, VIEW.zoom * 3, 0, Math.PI * 2);
  g.fill();
}

function drawFlag(g, x, y, team) {
  const z = VIEW.zoom, ts = VIEW.ts;
  const ox = x * ts + 12 * z, oy = y * ts - 2 * z;
  g.fillStyle = PAL.outline;
  g.fillRect(ox, oy, z, 7 * z);
  const col = team ? TEAMS[team].color : '#b9b9b9';
  g.fillStyle = PAL.outline;
  g.fillRect(ox + z, oy - z * 0, 4 * z, 3 * z + z);
  g.fillStyle = col;
  g.fillRect(ox + z, oy, 3 * z, 3 * z);
}

function drawUnit(g, u, now) {
  const st = VIEW.st, ts = VIEW.ts, z = VIEW.zoom;
  const o = VIEW.offsets.get(u.id) || { dx: 0, dy: 0, alpha: 1, flash: 0 };
  const done = u.acted && u.team === st.active;
  const idle = !done && u.team === st.active ? Math.round(Math.sin(now / 260 + u.id) * 0.6) : 0;
  const x = (u.x + o.dx) * ts, y = (u.y + o.dy) * ts + idle * z;
  g.globalAlpha = o.alpha;
  // тень
  g.fillStyle = 'rgba(20,10,30,0.35)';
  g.beginPath();
  g.ellipse(x + ts / 2, (u.y + o.dy) * ts + ts - 2 * z, ts * 0.32, z * 2, 0, 0, Math.PI * 2);
  g.fill();
  const spr = unitSprite(u.type, u.team, { flip: u.team === 'red', grey: done });
  g.drawImage(spr, x - z, y - z, ts + 2 * z, ts + 2 * z);
  if (o.flash > 0) {
    g.globalAlpha = o.flash * 0.8;
    g.globalCompositeOperation = 'lighter';
    g.drawImage(spr, x - z, y - z, ts + 2 * z, ts + 2 * z);
    g.globalCompositeOperation = 'source-over';
  }
  g.globalAlpha = 1;
  // выбранный юнит
  if (VIEW.selected === u.id) frameTile(g, u.x + o.dx, u.y + o.dy, '#fff', 2);
  // HP и уровень
  if (u.hp < BAL.maxHp) {
    const txt = String(u.hp);
    g.font = `bold ${Math.round(z * 4.5)}px "Trebuchet MS", sans-serif`;
    g.textAlign = 'right';
    const tx = x + ts - z * 0.5, ty = (u.y + o.dy) * ts + ts - z * 0.5;
    const w = g.measureText(txt).width;
    g.fillStyle = 'rgba(26,16,32,0.85)';
    g.fillRect(tx - w - z, ty - z * 4.5, w + z * 1.5, z * 5);
    g.fillStyle = u.hp > 50 ? '#fff' : u.hp > 25 ? '#ffd84a' : '#ff6a5a';
    g.fillText(txt, tx, ty);
  }
  if (u.level > 0) {
    g.font = `bold ${Math.round(z * 4)}px "Trebuchet MS", sans-serif`;
    g.textAlign = 'left';
    g.fillStyle = 'rgba(26,16,32,0.85)';
    g.fillRect(x, (u.y + o.dy) * ts, z * 6, z * 5);
    g.fillStyle = '#ffd84a';
    g.fillText('★' + u.level, x + z * 0.3, (u.y + o.dy) * ts + z * 4.3);
  }
}
