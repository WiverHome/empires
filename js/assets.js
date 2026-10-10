/* ============ ASSETS: листы спрайтов, процедурные тайлы, кэш юнитов ============ */
'use strict';

const PAL = {
  outline: '#3f2631', grass: '#84c669', grassDark: '#65a556', grassDeep: '#479f4a',
  dirt: '#eaa56c', dirtDark: '#cf8254', wood: '#bd6c4a', woodDark: '#763b36',
  stone: '#c0cbdc', stoneMid: '#8b9bb4', stoneDark: '#5a6988', night: '#262b44',
  water: '#2f8fd8', waterLight: '#76e4ff', foam: '#c8f2ff', roof: '#c34b35', roofLight: '#f28462',
  white: '#ffffff',
};

const SHEETS = { town: null, dungeon: null };
const TILE = 16;

function loadImage(src) {
  return new Promise((res, rej) => {
    const im = new Image();
    im.onload = () => res(im);
    im.onerror = () => rej(new Error('Не удалось загрузить ' + src));
    im.src = src;
  });
}

async function loadAssets() {
  [SHEETS.town, SHEETS.dungeon] = await Promise.all([loadImage('assets/town.png'), loadImage('assets/dungeon.png')]);
}

function makeCanvas(w, h) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const g = c.getContext('2d');
  g.imageSmoothingEnabled = false;
  return [c, g];
}

// Нарисовать тайл листа в 16-пиксельной сетке
function blit(g, sheet, idx, dx, dy) {
  const im = SHEETS[sheet];
  const cols = im.width / TILE;
  g.drawImage(im, (idx % cols) * TILE, Math.floor(idx / cols) * TILE, TILE, TILE, dx, dy, TILE, TILE);
}

const px = (g, color, x, y, w = 1, h = 1) => { g.fillStyle = color; g.fillRect(x, y, w, h); };

// Детерминированный шум для вариаций тайлов
const hash2 = (x, y) => {
  let h = (x * 374761393 + y * 668265263) >>> 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
};

/* ---------- Процедурные тайлы (в палитре Kenney) ---------- */
function drawWater(g, ox, oy, frame, land) {
  px(g, PAL.water, ox, oy, 16, 16);
  // волны
  const waves = frame ? [[2, 4], [9, 9], [4, 13], [12, 2]] : [[4, 3], [11, 8], [2, 11], [10, 14]];
  for (const [x, y] of waves) { px(g, PAL.waterLight, ox + x, oy + y, 3, 1); px(g, '#5ab4ea', ox + x + 1, oy + y + 1, 2, 1); }
  // пена у берега
  if (land.up) px(g, PAL.foam, ox, oy, 16, 1);
  if (land.down) px(g, PAL.foam, ox, oy + 15, 16, 1);
  if (land.left) px(g, PAL.foam, ox, oy, 1, 16);
  if (land.right) px(g, PAL.foam, ox + 15, oy, 1, 16);
}

function drawRoad(g, ox, oy, link) {
  const seg = (x, y, w, h) => { px(g, PAL.dirtDark, ox + x - 1, oy + y - 1, w + 2, h + 2); };
  const fill = (x, y, w, h) => { px(g, PAL.dirt, ox + x, oy + y, w, h); };
  const parts = [[5, 5, 6, 6]];
  if (link.up) parts.push([5, 0, 6, 6]);
  if (link.down) parts.push([5, 10, 6, 6]);
  if (link.left) parts.push([0, 5, 6, 6]);
  if (link.right) parts.push([10, 5, 6, 6]);
  for (const p of parts) seg(...p);
  for (const p of parts) fill(...p);
  // камешки
  px(g, PAL.dirtDark, ox + 7, oy + 7); px(g, PAL.dirtDark, ox + 9, oy + 9);
}

function drawBridge(g, ox, oy, horizontal) {
  if (horizontal) {
    px(g, PAL.woodDark, ox, oy + 2, 16, 12);
    for (let x = 0; x < 16; x += 3) px(g, x % 6 ? PAL.wood : PAL.dirtDark, ox + x, oy + 3, 2, 10);
    px(g, PAL.outline, ox, oy + 2, 16, 1); px(g, PAL.outline, ox, oy + 13, 16, 1);
  } else {
    px(g, PAL.woodDark, ox + 2, oy, 12, 16);
    for (let y = 0; y < 16; y += 3) px(g, y % 6 ? PAL.wood : PAL.dirtDark, ox + 3, oy + y, 10, 2);
    px(g, PAL.outline, ox + 2, oy, 1, 16); px(g, PAL.outline, ox + 13, oy, 1, 16);
  }
}

function drawMountain(g, ox, oy, v) {
  // треугольный пик: левая сторона светлая, правая в тени, снежная шапка
  const peak = 7 + Math.floor(v * 3);
  for (let y = 2; y < 15; y++) {
    const half = Math.floor((y - 1) * 0.62);
    const x0 = peak - half, x1 = peak + half;
    px(g, PAL.outline, ox + x0 - 1, oy + y, x1 - x0 + 3, 1);
  }
  for (let y = 3; y < 14; y++) {
    const half = Math.floor((y - 2) * 0.62);
    const x0 = peak - half, x1 = peak + half;
    px(g, PAL.stone, ox + x0, oy + y, peak - x0 + 1, 1);
    px(g, PAL.stoneMid, ox + peak + 1, oy + y, x1 - peak, 1);
    if (y > 9) px(g, PAL.stoneDark, ox + peak + 1 + Math.floor((x1 - peak) / 2), oy + y, Math.ceil((x1 - peak) / 2), 1);
    if (y < 6) px(g, PAL.white, ox + x0, oy + y, x1 - x0 + 1, 1);
  }
  px(g, PAL.outline, ox + 1, oy + 14, 14, 1);
}

function drawHill(g, ox, oy) {
  const mound = (cx, cy, r) => {
    for (let y = -r; y <= 0; y++) {
      const w = Math.round(Math.sqrt(r * r - y * y) * 1.4);
      px(g, PAL.outline, ox + cx - w - 1, oy + cy + y, w * 2 + 3, 1);
    }
    for (let y = -r + 1; y <= 0; y++) {
      const w = Math.round(Math.sqrt(r * r - y * y) * 1.4) - 1;
      px(g, PAL.grassDark, ox + cx - w, oy + cy + y, w * 2 + 1, 1);
      px(g, PAL.grass, ox + cx - w, oy + cy + y, Math.max(1, w), 1);
    }
    px(g, PAL.outline, ox + cx - Math.round(r * 1.4) - 1, oy + cy + 1, Math.round(r * 2.8) + 3, 1);
  };
  mound(10, 11, 5);
  mound(5, 13, 4);
}

function drawHouse(g, ox, oy) {
  // стены
  px(g, PAL.outline, ox + 2, oy + 7, 12, 8);
  px(g, PAL.dirt, ox + 3, oy + 8, 10, 6);
  px(g, PAL.dirtDark, ox + 3, oy + 13, 10, 1);
  // дверь и окно
  px(g, PAL.outline, ox + 6, oy + 10, 3, 4); px(g, PAL.woodDark, ox + 7, oy + 11, 1, 3);
  px(g, PAL.outline, ox + 10, oy + 9, 2, 2); px(g, PAL.waterLight, ox + 10, oy + 9, 1, 1);
  // крыша
  for (let i = 0; i < 6; i++) {
    px(g, PAL.outline, ox + 1 + i, oy + 7 - i, 14 - i * 2, 1);
    if (i > 0) { px(g, PAL.roof, ox + 2 + i, oy + 7 - i, 12 - i * 2, 1); px(g, PAL.roofLight, ox + 2 + i, oy + 7 - i, 2, 1); }
  }
  px(g, PAL.roof, ox + 2, oy + 7, 12, 1);
}

function drawCastle(g, ox, oy) {
  px(g, PAL.outline, ox + 1, oy + 4, 14, 12);
  px(g, PAL.stone, ox + 2, oy + 5, 12, 10);
  px(g, PAL.stoneMid, ox + 2, oy + 12, 12, 3);
  // зубцы
  for (let x = 1; x < 15; x += 3) { px(g, PAL.outline, ox + x, oy + 2, 3, 3); px(g, PAL.stone, ox + x + 1, oy + 3, 1, 2); }
  // кладка
  px(g, PAL.stoneMid, ox + 4, oy + 7, 3, 1); px(g, PAL.stoneMid, ox + 9, oy + 8, 3, 1); px(g, PAL.stoneMid, ox + 3, oy + 10, 2, 1);
  // ворота
  px(g, PAL.outline, ox + 6, oy + 9, 4, 7); px(g, PAL.night, ox + 7, oy + 10, 2, 6);
  px(g, PAL.stoneDark, ox + 7, oy + 12, 2, 1);
}

function drawGrass(g, ox, oy, x, y) {
  px(g, PAL.grass, ox, oy, 16, 16);
  const v = hash2(x, y);
  if (v < 0.25) blit(g, 'town', v < 0.12 ? 1 : 2, ox, oy);
}

function drawForest(g, ox, oy, x, y) {
  const v = hash2(x * 3 + 1, y * 5 + 2);
  const trees = TILE_SPRITES.trees;
  blit(g, 'town', trees[Math.floor(v * trees.length)], ox, oy);
}

/* ---------- Пререндер карты (2 кадра для анимации воды) ---------- */
function renderTerrainLayer(st, frame) {
  const [c, g] = makeCanvas(st.w * TILE, st.h * TILE);
  const T = (x, y) => inBounds(st, x, y) ? st.terrain[y][x] : null;
  const isWater = (x, y) => T(x, y) === 'water';
  const linky = t => t === 'road' || t === 'bridge' || t === 'village' || t === 'castle';
  for (let y = 0; y < st.h; y++) for (let x = 0; x < st.w; x++) {
    const t = T(x, y), ox = x * TILE, oy = y * TILE;
    if (t === 'water' || t === 'bridge') {
      drawWater(g, ox, oy, frame, {
        up: T(x, y - 1) && !['water', 'bridge'].includes(T(x, y - 1)),
        down: T(x, y + 1) && !['water', 'bridge'].includes(T(x, y + 1)),
        left: T(x - 1, y) && !['water', 'bridge'].includes(T(x - 1, y)),
        right: T(x + 1, y) && !['water', 'bridge'].includes(T(x + 1, y)),
      });
      if (t === 'bridge') drawBridge(g, ox, oy, (T(x - 1, y) && !isWater(x - 1, y)) || (T(x + 1, y) && !isWater(x + 1, y)));
      continue;
    }
    drawGrass(g, ox, oy, x, y);
    if (t === 'road') {
      const l = (dx, dy) => linky(T(x + dx, y + dy));
      drawRoad(g, ox, oy, { up: l(0, -1), down: l(0, 1), left: l(-1, 0), right: l(1, 0) });
    }
    else if (t === 'forest') drawForest(g, ox, oy, x, y);
    else if (t === 'hill') drawHill(g, ox, oy);
    else if (t === 'mountain') drawMountain(g, ox, oy, hash2(x, y));
    else if (t === 'village') drawHouse(g, ox, oy);
    else if (t === 'castle') drawCastle(g, ox, oy);
  }
  return c;
}

/* ---------- Спрайты юнитов: оружие в руке, обводка цветом стороны, отражение, серый вариант ---------- */
// Спрайт собирается в сетке 48x48: фигурка из листа в 3 раза крупнее, оружие (UNIT_DEFS.weapon) в 2 раза,
// поэтому оружие получается тоньше фигурки и читается отдельно. Вокруг - обводка 3px цвета стороны.
// Итоговый холст 54x54 рисуется на клетку так же, как раньше 18x18 (поля по 1/16 клетки).
const SPR_GRID = 48, SPR_PAD = 3, SPR_SIZE = SPR_GRID + SPR_PAD * 2;
const unitCache = new Map();

function tilePixels(sheet, idx) {
  const [c, g] = makeCanvas(TILE, TILE);
  blit(g, sheet, idx, 0, 0);
  return g.getImageData(0, 0, TILE, TILE).data;
}

function composeUnit(type) {
  const def = UNIT_DEFS[type];
  const N = SPR_GRID, comp = new Uint8ClampedArray(N * N * 4);
  const put = (src, sx, sy, scale, ox, oy) => {
    const i = (sy * TILE + sx) * 4;
    if (!src[i + 3]) return;
    for (let a = 0; a < scale; a++) for (let b = 0; b < scale; b++) {
      const x = ox + sx * scale + b, y = oy + sy * scale + a;
      if (x < 0 || y < 0 || x >= N || y >= N) continue;
      comp.set([src[i], src[i + 1], src[i + 2], 255], (y * N + x) * 4);
    }
  };
  const body = tilePixels(def.sheet || 'dungeon', def.sprite);
  for (let y = 0; y < TILE; y++) for (let x = 0; x < TILE; x++) put(body, x, y, 3, 0, 0);
  if (def.weapon) {
    const w = def.weapon, src = tilePixels(w.sheet, w.idx);
    for (let y = 0; y < TILE; y++) for (let x = 0; x < TILE; x++) put(src, x, y, 2, w.x, w.y);
  }
  return comp;
}

function unitSprite(type, team, opts = {}) {
  const flip = !!opts.flip, grey = !!opts.grey;
  const k = type + '|' + team + '|' + flip + '|' + grey;
  if (unitCache.has(k)) return unitCache.get(k);
  const ck = 'comp|' + type;
  if (!unitCache.has(ck)) unitCache.set(ck, composeUnit(type));
  const src = unitCache.get(ck), N = SPR_GRID, P = SPR_PAD, S = SPR_SIZE;
  const [c, g] = makeCanvas(S, S);
  const out = g.createImageData(S, S);
  const col = hexRgb(TEAMS[team].color);
  const solid = (x, y) => x >= 0 && y >= 0 && x < N && y < N && src[(y * N + x) * 4 + 3] > 0;
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const sx = flip ? N - 1 - (x - P) : x - P, sy = y - P;
    const o = (y * S + x) * 4;
    if (solid(sx, sy)) {
      const i = (sy * N + sx) * 4;
      let r = src[i], gg = src[i + 1], b = src[i + 2];
      if (grey) { const l = r * 0.3 + gg * 0.59 + b * 0.11; r = gg = b = l * 0.75; }
      out.data[o] = r; out.data[o + 1] = gg; out.data[o + 2] = b; out.data[o + 3] = 255;
      continue;
    }
    let near = false;
    for (let dy = -P; dy <= P && !near; dy++)
      for (let dx = -P; dx <= P; dx++)
        if (dx * dx + dy * dy <= P * P + 1 && solid(sx + dx, sy + dy)) { near = true; break; }
    if (near) {
      const f = grey ? 0.55 : 1;
      out.data[o] = col[0] * f; out.data[o + 1] = col[1] * f; out.data[o + 2] = col[2] * f; out.data[o + 3] = 255;
    }
  }
  g.putImageData(out, 0, 0);
  unitCache.set(k, c);
  return c;
}

function hexRgb(h) {
  const n = parseInt(h.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

// Иконка тайла/юнита для DOM (портреты, меню покупки)
function spriteDataUrl(type, team) {
  return unitSprite(type, team).toDataURL();
}

function portraitDataUrl(spriteIdx, team, scale = 4) {
  const [c, g] = makeCanvas(TILE * scale, TILE * scale);
  const [t, tg] = makeCanvas(TILE, TILE);
  blit(tg, 'dungeon', spriteIdx, 0, 0);
  g.drawImage(t, 0, 0, c.width, c.height);
  return c.toDataURL();
}

function graveSprite() {
  const k = 'grave';
  if (unitCache.has(k)) return unitCache.get(k);
  const [c, g] = makeCanvas(TILE, TILE);
  blit(g, 'dungeon', TILE_SPRITES.grave, 0, 0);
  unitCache.set(k, c);
  return c;
}
