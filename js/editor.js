/* ============ EDITOR: редактор своих карт для схватки ============ */
'use strict';

const EDITOR_MIN_W = 7, EDITOR_MAX_W = 11, EDITOR_MIN_H = 7, EDITOR_MAX_H = 15;
const EDITOR_TEAMS = ['blue', 'red', 'green', 'yellow'];
const EDITOR_BRUSHES = [
  ...['plain', 'road', 'forest', 'hill', 'mountain', 'water', 'shallow', 'bridge', 'village', 'ruins'].map(t => ({ id: t, terrain: t, name: TERRAIN[t].name })),
  { id: 'castle', terrain: 'castle', name: 'Ничей замок' },
  ...EDITOR_TEAMS.map(t => ({ id: 'team:' + t, terrain: 'castle', team: t, name: 'Замок: ' + TEAMS[t].label })),
];

// Иконка кисти: одна клетка местности, для замков стороны - с рамкой цвета
function editorBrushIcon(b, size) {
  const src = renderTerrainLayer({ w: 1, h: 1, terrain: [[b.terrain]] }, 0);
  const c = document.createElement('canvas');
  c.width = c.height = size;
  const g = c.getContext('2d');
  g.imageSmoothingEnabled = false;
  g.drawImage(src, 0, 0, size, size);
  if (b.team) editorMark(g, 0, 0, size, b.team);
  return c;
}
function editorMark(g, x, y, s, team) {
  g.strokeStyle = TEAMS[team].color; g.lineWidth = Math.max(2, s / 8);
  g.strokeRect(x + g.lineWidth / 2, y + g.lineWidth / 2, s - g.lineWidth, s - g.lineWidth);
  g.fillStyle = TEAMS[team].color;
  g.fillRect(x, y, s * 0.4, s * 0.4);
}

// Собрать карту из состояния редактора или вернуть строку ошибки
function editorBuildMap(ed, title, id) {
  const legend = {};
  for (const k in MAP_LEGEND) legend[MAP_LEGEND[k]] = k;
  const teams = EDITOR_TEAMS.filter(t => ed.castles[t]);
  if (teams.length < 2) return 'Нужны замки хотя бы двух сторон.';
  const used = new Set(), slots = [];
  for (const team of teams) {
    const [cx, cy] = ed.castles[team];
    let cmd = null;
    for (const [dx, dy] of [[0, -1], [0, 1], [-1, 0], [1, 0]]) {
      const x = cx + dx, y = cy + dy;
      if (x < 0 || y < 0 || x >= ed.w || y >= ed.h || used.has(x + ',' + y)) continue;
      const t = ed.terrain[y][x];
      if (!isFinite(TERRAIN[t].cost) || t === 'castle' || t === 'village') continue;
      cmd = [x, y]; break;
    }
    if (!cmd) return `У замка «${TEAMS[team].label}» нет свободной клетки для командира рядом.`;
    used.add(cmd.join(','));
    slots.push({ team, castle: [cx, cy], cmd });
  }
  return {
    id, title, desc: `Своя карта ${ed.w}x${ed.h}, сторон: ${slots.length}.`, custom: true,
    map: ed.terrain.map(row => row.map(t => legend[t]).join('')), slots,
  };
}

// existing - карта из CUSTOM_MAPS или null. onDone({ map }) после сохранения, ({ deleted: id }) после удаления, (null) при отмене.
function openMapEditor(existing, onDone) {
  const ed = { w: 11, h: 13, terrain: [], castles: {} };
  if (existing) {
    ed.w = existing.map[0].length; ed.h = existing.map.length;
    ed.terrain = existing.map.map(r => [...r].map(ch => MAP_LEGEND[ch] || 'plain'));
    existing.slots.forEach(s => { ed.castles[s.team] = s.castle.slice(); });
  } else {
    ed.terrain = Array.from({ length: ed.h }, () => Array(ed.w).fill('plain'));
    ed.castles.blue = [5, ed.h - 1]; ed.castles.red = [5, 0];
    ed.terrain[ed.h - 1][5] = 'castle'; ed.terrain[0][5] = 'castle';
  }
  let brush = EDITOR_BRUSHES[0], confirmDel = false, err = '';

  const m = openModal(`<h2>${existing ? 'Изменить карту' : 'Новая карта'}</h2>
    <canvas class="ed-canvas"></canvas>
    <div class="ed-brushes"></div>
    <div class="ed-row"><span class="ed-brush-name muted"></span>
      <span class="ed-size">Ширина <button class="secondary icon" data-dw="-1">−</button><b class="ed-w"></b><button class="secondary icon" data-dw="1">+</button></span>
      <span class="ed-size">Высота <button class="secondary icon" data-dh="-1">−</button><b class="ed-h"></b><button class="secondary icon" data-dh="1">+</button></span></div>
    <input class="ed-title" type="text" maxlength="24" placeholder="Название карты">
    <p class="warn ed-err" hidden></p>
    <div class="modal-actions">${existing ? '<button class="secondary" data-del>Удалить</button>' : ''}<button class="secondary" data-cancel>Отмена</button><button data-save>Сохранить</button></div>`, 'editor');
  const cv = m.querySelector('.ed-canvas'), g = cv.getContext('2d');
  const title = m.querySelector('.ed-title');
  title.value = existing ? existing.title : 'Моя карта';

  const showErr = text => { err = text; const p = m.querySelector('.ed-err'); p.textContent = text; p.hidden = !text; };
  const layout = () => {
    if (!m.isConnected) return;
    // холст подгоняется под доступное место: ширина окна и высота экрана за вычетом остальных элементов
    const maxW = Math.min(m.clientWidth - 28, 520 - 40);
    const maxH = Math.max(120, window.innerHeight - 350);
    const sc = Math.min(maxW / (ed.w * TILE), maxH / (ed.h * TILE));
    cv.style.width = Math.floor(ed.w * TILE * sc) + 'px';
    cv.style.height = Math.floor(ed.h * TILE * sc) + 'px';
  };
  const draw = () => {
    cv.width = ed.w * TILE * 2; cv.height = ed.h * TILE * 2;
    g.imageSmoothingEnabled = false;
    g.drawImage(renderTerrainLayer({ w: ed.w, h: ed.h, terrain: ed.terrain }, 0), 0, 0, cv.width, cv.height);
    for (const t of EDITOR_TEAMS) if (ed.castles[t]) editorMark(g, ed.castles[t][0] * TILE * 2, ed.castles[t][1] * TILE * 2, TILE * 2, t);
    m.querySelector('.ed-w').textContent = ed.w;
    m.querySelector('.ed-h').textContent = ed.h;
    m.querySelector('.ed-brush-name').textContent = brush.name;
    layout();
  };

  const box = m.querySelector('.ed-brushes');
  const brushBtns = EDITOR_BRUSHES.map(b => {
    const btn = el('button', 'ed-brush');
    btn.title = b.name;
    btn.appendChild(editorBrushIcon(b, 28));
    btn.onclick = () => { SND.play('click'); brush = b; mark(); m.querySelector('.ed-brush-name').textContent = b.name; };
    box.appendChild(btn);
    return btn;
  });
  const mark = () => brushBtns.forEach((btn, i) => btn.classList.toggle('on', EDITOR_BRUSHES[i] === brush));

  const paint = (x, y) => {
    if (x < 0 || y < 0 || x >= ed.w || y >= ed.h) return;
    for (const t of EDITOR_TEAMS) {
      const c = ed.castles[t];
      if (c && c[0] === x && c[1] === y) delete ed.castles[t];
    }
    if (brush.team) {
      const old = ed.castles[brush.team];
      if (old) ed.terrain[old[1]][old[0]] = 'plain';
      ed.castles[brush.team] = [x, y];
    }
    ed.terrain[y][x] = brush.terrain;
    showErr('');
    draw();
  };
  let last = null;
  const cellOf = e => {
    const r = cv.getBoundingClientRect();
    return [Math.floor((e.clientX - r.left) / r.width * ed.w), Math.floor((e.clientY - r.top) / r.height * ed.h)];
  };
  cv.addEventListener('pointerdown', e => {
    e.preventDefault(); cv.setPointerCapture(e.pointerId);
    const c = cellOf(e); last = c.join(','); paint(c[0], c[1]);
  });
  cv.addEventListener('pointermove', e => {
    if (last === null) return;
    const c = cellOf(e);
    if (c.join(',') !== last) { last = c.join(','); paint(c[0], c[1]); }
  });
  const stop = () => { last = null; };
  cv.addEventListener('pointerup', stop);
  cv.addEventListener('pointercancel', stop);

  const resize = (dw, dh) => {
    const nw = Math.min(EDITOR_MAX_W, Math.max(EDITOR_MIN_W, ed.w + dw));
    const nh = Math.min(EDITOR_MAX_H, Math.max(EDITOR_MIN_H, ed.h + dh));
    ed.terrain = Array.from({ length: nh }, (_, y) => Array.from({ length: nw }, (_, x) => (ed.terrain[y] && ed.terrain[y][x]) || 'plain'));
    ed.w = nw; ed.h = nh;
    for (const t of EDITOR_TEAMS) {
      const c = ed.castles[t];
      if (c && (c[0] >= nw || c[1] >= nh)) delete ed.castles[t];
    }
    showErr(''); draw();
  };
  m.querySelectorAll('[data-dw]').forEach(b => b.onclick = () => { SND.play('click'); resize(+b.dataset.dw, 0); });
  m.querySelectorAll('[data-dh]').forEach(b => b.onclick = () => { SND.play('click'); resize(0, +b.dataset.dh); });

  m.querySelector('[data-cancel]').onclick = () => { SND.play('click'); closeModal(); onDone(null); };
  m.querySelector('[data-save]').onclick = () => {
    SND.play('click');
    const name = title.value.trim() || 'Моя карта';
    const res = editorBuildMap(ed, name, existing ? existing.id : 'c' + Date.now().toString(36));
    if (typeof res === 'string') { showErr(res); return; }
    closeModal(); onDone({ map: res });
  };
  const del = m.querySelector('[data-del]');
  if (del) del.onclick = () => {
    SND.play('click');
    if (!confirmDel) { confirmDel = true; del.textContent = 'Точно удалить?'; return; }
    closeModal(); onDone({ deleted: existing.id });
  };

  mark(); draw();
  window.addEventListener('resize', layout, { once: false });
}
