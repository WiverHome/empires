/* ============ MAIN: сохранения, экраны, ввод, старт ============ */
'use strict';

/* ---------- Сохранения (localStorage) ---------- */
const SAVE = {
  KEY: 'empires.save.v1',
  data: { unlocked: 1, heroLevel: 0, completed: [], current: null, prefs: {} },

  load() {
    try {
      const raw = localStorage.getItem(this.KEY);
      if (raw) this.data = Object.assign(this.data, JSON.parse(raw));
    } catch (e) { /* приватный режим или битое сохранение: играем без него */ }
    this.data.prefs = this.data.prefs || {};
  },
  write() {
    try { localStorage.setItem(this.KEY, JSON.stringify(this.data)); } catch (e) { /* нет места или запрещено */ }
  },
  setPref(k, v) { this.data.prefs[k] = v; this.write(); },

  // Автосохранение в начале хода игрока и после каждого действия
  saveCurrent() {
    if (!GAME.st || GAME.st.over) return;
    this.data.current = { missionId: GAME.mission.id, state: GAME.st, fired: GAME.fired };
    this.write();
  },
  clearCurrent() { this.data.current = null; this.write(); },

  missionWon(id, st) {
    const idx = MISSIONS.findIndex(m => m.id === id);
    if (!this.data.completed.includes(id)) this.data.completed.push(id);
    this.data.unlocked = Math.max(this.data.unlocked, Math.min(MISSIONS.length, idx + 2));
    // уровень Ардана переходит в следующую миссию
    this.data.heroLevel = Math.max(this.data.heroLevel, st.teams.blue.commanderLevel || 0);
    this.write();
  },
};

/* ---------- Экраны ---------- */
function showScreen(name) {
  document.querySelectorAll('.screen').forEach(s => { s.hidden = s.id !== 'screen-' + name; });
}

function renderTitle() {
  const box = $('#title-actions');
  box.innerHTML = '';
  const cur = SAVE.data.current;
  if (cur && missionById(cur.missionId)) {
    const b = el('button', '', `Продолжить <small>${missionById(cur.missionId).title}, ход ${cur.state.turn}</small>`);
    b.onclick = () => { SND.play('click'); startMission(cur.missionId, cur); };
    box.appendChild(b);
  }
  const nb = el('button', cur ? 'secondary' : '', SAVE.data.completed.length ? 'Кампания' : 'Новая кампания');
  nb.onclick = () => { SND.play('click'); renderMissions(); };
  box.appendChild(nb);
  const rb = el('button', 'secondary', 'Как играть');
  rb.onclick = () => { SND.play('click'); showRules(); };
  box.appendChild(rb);
}

function renderMissions() {
  const list = MISSIONS.map((m, i) => {
    const open = i < SAVE.data.unlocked;
    const done = SAVE.data.completed.includes(m.id);
    return `<button class="mission ${open ? '' : 'locked'}" data-id="${m.id}" ${open ? '' : 'disabled'}>
      <span class="m-num">${i + 1}</span><span class="m-body"><b>${m.title}</b><small>${open ? m.goal : 'Закрыто'}</small></span>
      ${done ? '<span class="m-done">✓</span>' : ''}</button>`;
  }).join('');
  const m = openModal(`<h2>Кампания</h2>${SAVE.data.heroLevel ? `<p class="muted">Уровень Ардана: ★${SAVE.data.heroLevel}</p>` : ''}
    <div class="mission-list">${list}</div>
    ${SAVE.data.current ? '<p class="warn">Новая миссия заменит текущее сохранение.</p>' : ''}
    <div class="modal-actions"><button class="secondary" data-close>Назад</button></div>`, 'missions');
  m.querySelectorAll('.mission:not(.locked)').forEach(b => b.onclick = () => {
    SND.play('click');
    closeModal();
    SAVE.clearCurrent();
    startMission(b.dataset.id);
  });
  m.querySelector('[data-close]').onclick = closeModal;
}

function showRules() {
  const rows = BUY_LIST.map(t => {
    const d = UNIT_DEFS[t];
    return `<tr><td><img src="${spriteDataUrl(t, 'blue', 2)}" alt=""></td><td><b>${d.name}</b><br><small>${d.desc}</small></td>
      <td>${d.cost}</td><td>${d.atk.join('-')}</td><td>${d.def}</td><td>${d.move}</td><td>${d.range[0] === d.range[1] ? d.range[0] : d.range.join('-')}</td></tr>`;
  }).join('');
  const terr = Object.values(TERRAIN).map(t => `<tr><td>${t.name}</td><td>${isFinite(t.cost) ? t.cost : '-'}</td><td>+${t.def}</td></tr>`).join('');
  const m = openModal(`<h2>Как играть</h2>
    <ul class="rules">
      <li>Нажмите на своего воина: синим подсвечено, куда он дойдёт, красным - куда сможет ударить. Затем выберите клетку и действие.</li>
      <li>Урон зависит от атаки, защиты цели и местности под ней, а также от здоровья атакующего: раненые бьют слабее.</li>
      <li>Если цель выжила и достаёт до вас, она отвечает ударом. Наведите курсор на цель, чтобы увидеть прогноз.</li>
      <li>Мечники и командиры захватывают деревни, замки захватывает только командир. Каждое здание приносит золото в начале хода и лечит своих на ${BAL.healBuilding}.</li>
      <li>В свободном своём замке можно нанимать воинов. Нанятый воин ходит со следующего хода.</li>
      <li>За урон воины получают опыт. Уровень даёт +${BAL.levelAtk} к атаке и +${BAL.levelDef} к защите.</li>
      <li>Павшие оставляют могилы на ${BAL.graveTurns} хода. Чародей может поднять из могилы призрака.</li>
      <li>Управление: перетаскивание - двигать карту, колесо или кнопки ± - масштаб, Esc или правая кнопка - отмена, E - конец хода.</li>
    </ul>
    <h3>Воины</h3><div class="table-wrap"><table class="units"><tr><th></th><th></th><th>Цена</th><th>Атака</th><th>Защ.</th><th>Ход</th><th>Дальн.</th></tr>${rows}</table></div>
    <h3>Местность</h3><table class="terrain"><tr><th></th><th>Ход</th><th>Защита</th></tr>${terr}</table>
    <div class="modal-actions"><button data-close>Понятно</button></div>`, 'rules-modal');
  m.querySelector('[data-close]').onclick = () => { closeModal(); if (GAME.st) openGameMenu(); };
}

/* ---------- Ввод на карте: мышь, тач, клавиатура ---------- */
function wireInput() {
  const c = VIEW.canvas;
  let down = null, dragging = false;
  // координаты в физических пикселях холста
  const pos = e => { const r = c.getBoundingClientRect(); return { x: (e.clientX - r.left) * VIEW.dpr, y: (e.clientY - r.top) * VIEW.dpr }; };
  const pointers = new Map();
  let pinch = null;

  c.addEventListener('pointerdown', e => {
    c.setPointerCapture(e.pointerId);
    pointers.set(e.pointerId, pos(e));
    if (pointers.size === 2) {
      const [a, b] = [...pointers.values()];
      pinch = { d: Math.hypot(a.x - b.x, a.y - b.y), ts: VIEW.ts };
      down = null;
      return;
    }
    if (e.button === 2) return;
    down = { ...pos(e), camX: VIEW.camX, camY: VIEW.camY };
    dragging = false;
  });
  c.addEventListener('pointermove', e => {
    const p = pos(e);
    if (pointers.has(e.pointerId)) pointers.set(e.pointerId, p);
    if (pinch && pointers.size === 2) {
      const [a, b] = [...pointers.values()];
      setTileSize(pinch.ts * Math.hypot(a.x - b.x, a.y - b.y) / pinch.d);
      return;
    }
    if (down) {
      const dx = p.x - down.x, dy = p.y - down.y;
      if (!dragging && Math.hypot(dx, dy) > 10 * VIEW.dpr) { dragging = true; hidePopup(); }
      if (dragging) { VIEW.camX = down.camX - dx; VIEW.camY = down.camY - dy; clampCamera(); return; }
    }
    if (e.pointerType === 'mouse') onTileHover(screenToTile(p.x, p.y));
  });
  c.addEventListener('pointerup', e => {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) pinch = null;
    if (!down) return;
    const p = pos(e);
    const wasDrag = dragging;
    down = null; dragging = false;
    if (wasDrag) {
      if (GAME.mode === 'action' && selUnit()) openActionMenu(selUnit());
      return;
    }
    const t = screenToTile(p.x, p.y);
    if (!t) return;
    if (e.pointerType !== 'mouse') {
      // на таче: первое касание по клетке хода показывает путь, второе - подтверждает
      onTileHover(t);
    }
    onTileClick(t.x, t.y);
  });
  c.addEventListener('pointercancel', e => { pointers.delete(e.pointerId); down = null; pinch = null; });
  c.addEventListener('pointerleave', () => { VIEW.hover = null; });
  c.addEventListener('contextmenu', e => { e.preventDefault(); onCancel(); });
  c.addEventListener('wheel', e => {
    e.preventDefault();
    zoomStep(e.deltaY < 0 ? 1 : -1);
    if (GAME.mode === 'action' && selUnit()) openActionMenu(selUnit());
  }, { passive: false });

  window.addEventListener('keydown', e => {
    if (!GAME.st || GAME.mode === 'dialog') return;
    if (!$('#modal-root').hidden) { if (e.key === 'Escape') closeModal(); return; }
    const step = VIEW.ts;
    if (e.key === 'Escape') onCancel();
    else if (e.key === 'e' || e.key === 'E' || e.key === 'у' || e.key === 'У') { if (!$('#btn-end').disabled) endTurnClicked(); }
    else if (e.key === 'ArrowLeft' || e.key === 'a') { VIEW.camX -= step; clampCamera(); }
    else if (e.key === 'ArrowRight' || e.key === 'd') { VIEW.camX += step; clampCamera(); }
    else if (e.key === 'ArrowUp' || e.key === 'w') { VIEW.camY -= step; clampCamera(); }
    else if (e.key === 'ArrowDown' || e.key === 's') { VIEW.camY += step; clampCamera(); }
    else if (e.key === '+' || e.key === '=') zoomStep(1);
    else if (e.key === '-') zoomStep(-1);
  });
}

function endTurnClicked() {
  if (!isHumanTurn() || !['idle', 'selected'].includes(GAME.mode)) return;
  SND.play('click');
  endTurnFlow();
}

/* ---------- Старт ---------- */
async function boot() {
  SAVE.load();
  SND.muted = !!SAVE.data.prefs.muted;
  try {
    await loadAssets();
  } catch (e) {
    $('#title-actions').innerHTML = `<p class="warn">${e.message}. Запустите игру через локальный сервер (см. README).</p>`;
    return;
  }
  initView($('#map'));
  wireInput();
  $('#btn-end').onclick = endTurnClicked;
  $('#btn-menu').onclick = () => { SND.play('click'); openGameMenu(); };
  $('#btn-zoom-in').onclick = () => zoomStep(1);
  $('#btn-zoom-out').onclick = () => zoomStep(-1);
  $('#modal-root').addEventListener('click', e => { if (e.target.id === 'modal-root' && !GAME.st?.over) closeModal(); });
  renderTitle();
  showScreen('title');
}

boot();
