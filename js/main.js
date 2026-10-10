/* ============ MAIN: сохранения, экраны, ввод, старт ============ */
'use strict';

/* ---------- Сохранения (localStorage) ---------- */
const SAVE = {
  KEY: 'empires.save.v1',
  data: { unlocked: 1, heroLevel: 0, completed: [], current: null, prefs: {}, maps: [], stars: {}, achievements: {}, replays: [] },

  load() {
    try {
      const raw = localStorage.getItem(this.KEY);
      if (raw) this.data = Object.assign(this.data, JSON.parse(raw));
    } catch (e) { /* приватный режим или битое сохранение: играем без него */ }
    this.data.prefs = this.data.prefs || {};
    this.data.maps = Array.isArray(this.data.maps) ? this.data.maps : [];
    this.data.stars = this.data.stars || {};
    this.data.achievements = this.data.achievements || {};
    this.data.replays = Array.isArray(this.data.replays) ? this.data.replays : [];
    CUSTOM_MAPS.length = 0;
    CUSTOM_MAPS.push(...this.data.maps);
    // в кампании могли появиться новые миссии: открыть следующую за последней пройденной
    for (const id of this.data.completed) {
      const idx = MISSIONS.findIndex(m => m.id === id);
      if (idx >= 0) this.data.unlocked = Math.max(this.data.unlocked, Math.min(MISSIONS.length, idx + 2));
    }
  },
  write() {
    try { localStorage.setItem(this.KEY, JSON.stringify(this.data)); } catch (e) { /* нет места или запрещено */ }
  },
  setPref(k, v) { this.data.prefs[k] = v; this.write(); },

  // Автосохранение в начале хода игрока и после каждого действия
  saveCurrent() {
    if (!GAME.st || GAME.st.over || GAME.replay) return;
    this.data.current = { missionId: GAME.mission.id, state: GAME.st, fired: GAME.fired };
    this.write();
  },
  clearCurrent() { this.data.current = null; this.write(); },

  // Последние партии для повтора (журнал действий + сид). Своя карта сохраняется в записи.
  REPLAYS_KEEP: 5,
  addReplay(mission, st, res) {
    if (!st.log || !st.rinit) return;   // партия начата до появления повторов
    const sm = mission.skirmish ? skirmishMapById(mission.id.split(':')[1]) : null;
    this.data.replays.unshift({
      missionId: mission.id, title: mission.title, res, turn: st.turn, date: Date.now(),
      sides: st.order.map(t => [t, st.teams[t].ai ? 'a' : 'h']),
      rinit: st.rinit, log: st.log, map: sm && sm.custom ? sm : undefined,
    });
    this.data.replays.length = Math.min(this.data.replays.length, this.REPLAYS_KEEP);
    this.write();
  },

  missionWon(id, st) {
    const idx = MISSIONS.findIndex(m => m.id === id);
    this.data.stars[id] = Math.max(this.data.stars[id] || 0, missionStars(MISSIONS[idx], st));
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
  SND.music(name === 'game' ? 'battle' : 'menu');
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
  const sb = el('button', 'secondary', 'Схватка');
  sb.onclick = () => { SND.play('click'); renderSkirmishSetup(); };
  box.appendChild(sb);
  const db = el('button', 'secondary', difficultyLabel());
  db.onclick = () => {
    SND.play('click');
    const keys = Object.keys(DIFFICULTY);
    SAVE.setPref('difficulty', keys[(keys.indexOf(SAVE.data.prefs.difficulty || 'normal') + 1) % keys.length]);
    db.innerHTML = difficultyLabel();
  };
  box.appendChild(db);
  const rb = el('button', 'secondary', 'Как играть');
  rb.onclick = () => { SND.play('click'); showRules(); };
  box.appendChild(rb);
  const row = el('div', 'btn-row');
  const ab = el('button', 'secondary', `Достижения <small>${Object.keys(SAVE.data.achievements).length} из ${ACHIEVEMENTS.length}</small>`);
  ab.onclick = () => { SND.play('click'); showAchievements(); };
  const pb = el('button', 'secondary', `Повторы <small>${SAVE.data.replays.length ? 'последних: ' + SAVE.data.replays.length : 'пока нет'}</small>`);
  pb.onclick = () => { SND.play('click'); showReplays(); };
  row.append(ab, pb);
  box.appendChild(row);
  const mb = el('button', 'secondary', musicLabel());
  mb.onclick = () => { toggleMusic(); mb.textContent = musicLabel(); SND.play('click'); };
  box.appendChild(mb);
}

const musicLabel = () => 'Музыка: ' + (SND.musicMuted ? 'выкл' : 'вкл');
function toggleMusic() {
  SND.setMusicMuted(!SND.musicMuted);
  SAVE.setPref('musicMuted', SND.musicMuted);
}

// Сложность действует на новые партии: доход и ошибки ИИ
const difficultyLabel = () => `Сложность: ${DIFFICULTY[SAVE.data.prefs.difficulty || 'normal'].name}`;

const CHAPTERS = ['Морвен', 'Болота', 'Север'];
const MISSIONS_PER_CHAPTER = 4;

// Кампания по главам: по умолчанию открыта глава последней доступной миссии
function renderMissions(chapter) {
  const last = Math.min(SAVE.data.unlocked, MISSIONS.length) - 1;
  const ch = chapter ?? Math.floor(last / MISSIONS_PER_CHAPTER);
  const tabs = CHAPTERS.map((name, c) => {
    const open = c * MISSIONS_PER_CHAPTER < SAVE.data.unlocked;
    return `<button class="${c === ch ? '' : 'secondary'}" data-ch="${c}" ${open ? '' : 'disabled'}>${c + 1}. ${name}</button>`;
  }).join('');
  const list = MISSIONS.slice(ch * MISSIONS_PER_CHAPTER, (ch + 1) * MISSIONS_PER_CHAPTER).map((m, j) => {
    const i = ch * MISSIONS_PER_CHAPTER + j;
    const open = i < SAVE.data.unlocked;
    const done = SAVE.data.completed.includes(m.id);
    return `<button class="mission ${open ? '' : 'locked'}" data-id="${m.id}" ${open ? '' : 'disabled'}>
      <span class="m-num">${i + 1}</span><span class="m-body"><b>${m.title}</b><small>${open ? m.goal : 'Закрыто'}</small></span>
      ${done ? `<span class="m-done">${starsText(SAVE.data.stars[m.id] || 1)}</span>` : ''}</button>`;
  }).join('');
  const m = openModal(`<h2>Кампания</h2>${SAVE.data.heroLevel ? `<p class="muted">Уровень Ардана: ★${SAVE.data.heroLevel}</p>` : ''}
    <div class="seg chapters">${tabs}</div>
    <div class="mission-list">${list}</div>
    ${SAVE.data.current ? '<p class="warn">Новая миссия заменит текущее сохранение.</p>' : ''}
    <div class="modal-actions"><button class="secondary" data-close>Назад</button></div>`, 'missions');
  m.querySelectorAll('[data-ch]').forEach(b => b.onclick = () => { SND.play('click'); renderMissions(+b.dataset.ch); });
  m.querySelectorAll('.mission:not(.locked)').forEach(b => b.onclick = () => {
    SND.play('click');
    closeModal();
    SAVE.clearCurrent();
    startMission(b.dataset.id);
  });
  m.querySelector('[data-close]').onclick = closeModal;
}

const escHtml = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const SIDE_KINDS ={ h: 'Игрок', a: 'ИИ', n: 'Нет' };

// Настройка схватки: карта, кто за какую сторону, стартовое золото. Выбор запоминается.
function renderSkirmishSetup() {
  const pref = SAVE.data.prefs.skirmish || {};
  const cfg = { map: pref.map || SKIRMISH_MAPS[0].id, gold: pref.gold || 1000, sides: pref.sides || {}, fog: !!pref.fog };
  const mapOf = () => skirmishMapById(cfg.map) || SKIRMISH_MAPS[0];
  const sidesStr = () => mapOf().slots.map((s, i) => cfg.sides[s.team] || (i === 0 ? 'h' : 'a')).join('');

  const draw = () => {
    const sm = mapOf(), sides = sidesStr();
    const active = [...sides].filter(c => c !== 'n').length;
    const ok = active >= 2 && sides.includes('h');
    const all = [...SKIRMISH_MAPS, ...CUSTOM_MAPS], mi = Math.max(0, all.findIndex(x => x.id === sm.id));
    const maps = `<button class="secondary icon" data-step="-1">◀</button>
      <div class="map-pick"><b>${escHtml(sm.title)}</b><small>${mi + 1} из ${all.length} · ${sm.custom ? 'своя · ' : ''}${escHtml(sm.desc)}</small></div>
      <button class="secondary icon" data-step="1">▶</button>`;
    const slots = sm.slots.map((s, i) => `<div class="side-row"><span class="chip" style="background:${TEAMS[s.team].color}"></span>
      <span class="side-name">${TEAMS[s.team].label}</span>
      <div class="seg">${Object.keys(SIDE_KINDS).map(k => `<button class="${sides[i] === k ? '' : 'secondary'}" data-team="${s.team}" data-kind="${k}">${SIDE_KINDS[k]}</button>`).join('')}</div></div>`).join('');
    const golds = SKIRMISH_GOLD.map(g => `<button class="${g === cfg.gold ? '' : 'secondary'}" data-gold="${g}"><span class="coin"></span>${g}</button>`).join('');
    const m = openModal(`<h2>Схватка</h2>
      <div class="map-row">${maps}</div>
      <div class="seg ed-tools"><button class="secondary" data-new-map>Новая карта</button>${sm.custom ? '<button class="secondary" data-edit-map>Изменить</button><button class="secondary" data-share-map>Поделиться</button>' : ''}<button class="secondary" data-import-map>Ввести код</button></div>
      ${slots}
      <div class="side-row"><span class="side-name">Золото</span><div class="seg">${golds}</div></div>
      <div class="side-row"><span class="side-name">Туман</span><div class="seg"><button class="${cfg.fog ? 'secondary' : ''}" data-fog="0">Нет</button><button class="${cfg.fog ? '' : 'secondary'}" data-fog="1">Есть</button></div></div>
      ${!ok ? '<p class="warn">Нужно хотя бы две стороны и один игрок.</p>' : SAVE.data.current ? '<p class="warn">Новая партия заменит текущее сохранение.</p>' : ''}
      <div class="modal-actions"><button class="secondary" data-close>Назад</button><button data-start ${ok ? '' : 'disabled'}>В бой</button></div>`, 'skirmish');
    m.querySelectorAll('[data-step]').forEach(b => b.onclick = () => {
      SND.play('click');
      cfg.map = all[(mi + +b.dataset.step + all.length) % all.length].id;
      draw();
    });
    const edit = existing => {
      SND.play('click');
      openMapEditor(existing, res => {
        if (res && res.map) {
          const i = SAVE.data.maps.findIndex(x => x.id === res.map.id);
          if (i >= 0) SAVE.data.maps[i] = res.map; else { SAVE.data.maps.push(res.map); grantAchievements({ phase: 'map' }); }
          cfg.map = res.map.id;
        } else if (res && res.deleted) {
          SAVE.data.maps = SAVE.data.maps.filter(x => x.id !== res.deleted);
          if (cfg.map === res.deleted) cfg.map = SKIRMISH_MAPS[0].id;
        }
        CUSTOM_MAPS.length = 0;
        CUSTOM_MAPS.push(...SAVE.data.maps);
        SAVE.write();
        draw();
      });
    };
    m.querySelector('[data-new-map]').onclick = () => edit(null);
    const eb = m.querySelector('[data-edit-map]');
    if (eb) eb.onclick = () => edit(sm);
    const shb = m.querySelector('[data-share-map]');
    if (shb) shb.onclick = () => { SND.play('click'); showMapCode(sm, draw); };
    m.querySelector('[data-import-map]').onclick = () => {
      SND.play('click');
      importMapCode(map => {
        if (map) {
          SAVE.data.maps.push(map);
          CUSTOM_MAPS.length = 0;
          CUSTOM_MAPS.push(...SAVE.data.maps);
          SAVE.write();
          cfg.map = map.id;
        }
        draw();
      });
    };
    m.querySelectorAll('[data-kind]').forEach(b => b.onclick = () => { SND.play('click'); cfg.sides[b.dataset.team] = b.dataset.kind; draw(); });
    m.querySelectorAll('[data-gold]').forEach(b => b.onclick = () => { SND.play('click'); cfg.gold = +b.dataset.gold; draw(); });
    m.querySelectorAll('[data-fog]').forEach(b => b.onclick = () => { SND.play('click'); cfg.fog = b.dataset.fog === '1'; draw(); });
    m.querySelector('[data-close]').onclick = closeModal;
    m.querySelector('[data-start]').onclick = () => {
      SND.play('click');
      SAVE.setPref('skirmish', cfg);
      closeModal();
      SAVE.clearCurrent();
      startMission(skirmishId(sm.id, cfg.gold, sidesStr(), cfg.fog));
    };
  };
  draw();
}

function showAchievements() {
  const have = SAVE.data.achievements;
  const list = ACHIEVEMENTS.map(a => `<div class="ach ${have[a.id] ? 'got' : ''}"><span class="ach-icon">${have[a.id] ? '🏆' : '🔒'}</span>
    <span><b>${a.name}</b><small>${a.desc}</small></span></div>`).join('');
  const m = openModal(`<h2>Достижения</h2><div class="ach-list">${list}</div>
    <div class="modal-actions"><button class="secondary" data-close>Назад</button></div>`, 'achievements');
  m.querySelector('[data-close]').onclick = () => { SND.play('click'); closeModal(); renderTitle(); };
}

function showReplays() {
  const fmt = ts => new Date(ts).toLocaleString('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  const res = r => r.res === 'win' ? '<span class="good">победа</span>' : '<span class="bad">поражение</span>';
  const list = SAVE.data.replays.map((r, i) => `<button class="mission" data-i="${i}"><span class="m-body"><b>${escHtml(r.title)}</b>
    <small>${fmt(r.date)} · ${res(r)} · ходов: ${r.turn}</small></span><span class="m-done">▶</span></button>`).join('');
  const m = openModal(`<h2>Повторы</h2>
    ${list ? `<div class="mission-list">${list}</div>` : '<p class="muted">Здесь появятся последние законченные партии.</p>'}
    ${SAVE.data.current ? '<p class="muted">Сохранённая партия не пострадает.</p>' : ''}
    <div class="modal-actions"><button class="secondary" data-close>Назад</button></div>`, 'missions');
  m.querySelectorAll('[data-i]').forEach(b => b.onclick = () => { SND.play('click'); closeModal(); startReplay(SAVE.data.replays[+b.dataset.i]); });
  m.querySelector('[data-close]').onclick = () => { SND.play('click'); closeModal(); };
}

// Код своей карты: скопировать и отправить другу
function showMapCode(sm, back) {
  const code = encodeMapCode(sm);
  const m = openModal(`<h2>${escHtml(sm.title)}</h2>
    <p class="muted">Отправьте этот код. Друг откроет «Схватка», «Ввести код» и вставит его.</p>
    <textarea class="map-code" readonly rows="5">${code}</textarea>
    <p class="muted copy-state"></p>
    <div class="modal-actions"><button class="secondary" data-back>Назад</button><button data-copy>Копировать</button></div>`);
  const ta = m.querySelector('textarea');
  ta.onfocus = () => ta.select();
  m.querySelector('[data-copy]').onclick = async () => {
    SND.play('click');
    let ok = false;
    try { await navigator.clipboard.writeText(code); ok = true; } catch (e) {
      ta.select();
      try { ok = document.execCommand('copy'); } catch (e2) { /* нет доступа к буферу */ }
    }
    m.querySelector('.copy-state').textContent = ok ? 'Скопировано.' : 'Выделите код и скопируйте вручную.';
  };
  m.querySelector('[data-back]').onclick = () => { SND.play('click'); back(); };
}

// Ввод кода карты; done(map) с новой картой или done(null) при отмене
function importMapCode(done) {
  const m = openModal(`<h2>Карта по коду</h2>
    <textarea class="map-code" rows="5" placeholder="EMP1:…"></textarea>
    <p class="warn" hidden></p>
    <div class="modal-actions"><button class="secondary" data-back>Назад</button><button data-add>Добавить</button></div>`);
  const ta = m.querySelector('textarea'), err = m.querySelector('.warn');
  m.querySelector('[data-back]').onclick = () => { SND.play('click'); done(null); };
  m.querySelector('[data-add]').onclick = () => {
    SND.play('click');
    const res = decodeMapCode(ta.value, 'c' + Date.now().toString(36));
    if (typeof res === 'string') { err.textContent = res; err.hidden = false; return; }
    done(res);
  };
  ta.focus();
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
      <li>Быстрая атака: выберите воина и нажмите на врага в рамке. Воин сам подойдёт на лучшую клетку, останется подтвердить удар.</li>
      <li>Если цель выжила и достаёт до вас, она отвечает ударом. Наведите курсор на цель, чтобы увидеть прогноз.</li>
      <li>Мечники и командиры захватывают деревни, замки захватывает только командир. Каждое здание приносит золото в начале хода и лечит своих на ${BAL.healBuilding}.</li>
      <li>В свободном своём замке можно нанимать воинов. Нанятый воин ходит со следующего хода.</li>
      <li>За урон воины получают опыт. Уровень даёт +${BAL.levelAtk} к атаке и +${BAL.levelDef} к защите.</li>
      <li>Павшие оставляют могилы на ${BAL.graveTurns} хода. Чародей может поднять из могилы призрака.</li>
      <li>Паук отравляет: −${BAL.poisonAtk} к атаке и −${BAL.poisonDef} к защите на ${BAL.poisonTurns} хода. Яд сразу снимает целительница рядом или своё здание.</li>
      <li>Шаман сковывает цель: в свой следующий ход она пройдёт на ${BAL.slowMove} клетки меньше.</li>
      <li>Огонёк даёт соседним союзникам +${BAL.wispAtk} к атаке и особенно силён против нежити.</li>
      <li>Волк быстрый и по лесу ходит как по равнине.</li>
      <li>Водяной плавает по воде и мелководью и получает там +${BAL.waterDef} к защите.</li>
      <li>Катапульта, не двигаясь, может разрушить чужую деревню. Руины отстраивает мечник или командир.</li>
      <li>В тумане войны видно только то, что рядом с вашими воинами и зданиями.</li>
      <li>Управление: перетаскивание - двигать карту, колесо или кнопки ± - масштаб, Esc или правая кнопка - отмена, E - конец хода. Кнопка ↶ или Ctrl+Z отменяет действия текущего хода (результат удара от этого не изменится).</li>
    </ul>
    <h3>Воины</h3><div class="table-wrap"><table class="units"><tr><th></th><th></th><th>Цена</th><th>Атака</th><th>Защ.</th><th>Ход</th><th>Дальн.</th></tr>${rows}</table></div>
    <h3>Местность</h3><table class="terrain"><tr><th></th><th>Ход</th><th>Защита</th></tr>${terr}</table>
    <h3>Об игре</h3>
    <p class="muted">Empires: Хроники Эльдора, версия ${GAME_VERSION}. Код - лицензия MIT. Графика, звуки и музыка - CC0 (Kenney, request, RandomMind), подробности в <a href="https://github.com/WiverHome/empires/blob/main/assets/CREDITS.md" target="_blank" rel="noopener">CREDITS.md</a>.
    Исходный код и Android-версия: <a href="https://github.com/WiverHome/empires" target="_blank" rel="noopener">github.com/WiverHome/empires</a>.</p>
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
    else if ((e.ctrlKey || e.metaKey) && (e.key === 'z' || e.key === 'я')) { e.preventDefault(); undoAction(); }
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
  SND.musicMuted = !!SAVE.data.prefs.musicMuted;
  // браузер разрешает звук только после касания
  window.addEventListener('pointerdown', () => SND.unlock(), { capture: true });
  window.addEventListener('keydown', () => SND.unlock(), { capture: true });
  try {
    await loadAssets();
  } catch (e) {
    $('#title-actions').innerHTML = `<p class="warn">${e.message}. Запустите игру через локальный сервер (см. README).</p>`;
    return;
  }
  initView($('#map'));
  wireInput();
  $('#btn-end').onclick = endTurnClicked;
  $('#btn-undo').onclick = undoAction;
  $('#btn-menu').onclick = () => { SND.play('click'); openGameMenu(); };
  $('#btn-zoom-in').onclick = () => zoomStep(1);
  $('#btn-zoom-out').onclick = () => zoomStep(-1);
  $('#modal-root').addEventListener('click', e => { if (e.target.id === 'modal-root' && !GAME.st?.over) closeModal(); });
  $('#version').textContent = 'Версия ' + GAME_VERSION;
  renderTitle();
  showScreen('title');
}

boot();
