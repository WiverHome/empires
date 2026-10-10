// Проверка карт миссий: размеры, юниты и здания на допустимых клетках.
// node tools/check-maps.js
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');
const ctx = vm.createContext({ console, Math });
for (const f of ['data.js', 'rules.js', 'ai.js', 'campaign.js', 'skirmish.js'])
  vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'js', f), 'utf8'), ctx, { filename: f });

const MAX_W = 11, MAX_H = 15;   // вертикальный экран телефона без прокрутки
let bad = 0;
vm.runInContext('this.__M = MISSIONS.concat(SKIRMISH_MAPS.map(m => skirmishMission(skirmishId(m.id, 1000, "a".repeat(m.slots.length))))); this.__create = createState; this.__T = TERRAIN; this.__U = UNIT_DEFS;', ctx);
for (const m of ctx.__M) {
  const errs = [];
  let st;
  try { st = ctx.__create(m, { seed: 1 }); } catch (e) { console.log(m.id, 'ERROR', e.message); bad++; continue; }
  if (st.w > MAX_W || st.h > MAX_H) errs.push(`size ${st.w}x${st.h} > ${MAX_W}x${MAX_H}`);
  const seen = new Set();
  for (const u of m.units) {
    const t = st.terrain[u.y] && st.terrain[u.y][u.x];
    if (!t) { errs.push(`unit out of map ${u.x},${u.y}`); continue; }
    const ud = ctx.__U[u.type];
    if (!ud) { errs.push(`unknown unit ${u.type}`); continue; }
    if (!isFinite(ctx.__T[t].cost) && !ud.flying && !(ud.swim && ctx.__T[t].water)) errs.push(`${u.type} on ${t} ${u.x},${u.y}`);
    const k = u.x + ',' + u.y;
    if (seen.has(k)) errs.push('two units at ' + k);
    seen.add(k);
  }
  for (const b of m.buildings || []) if (!ctx.__T[st.terrain[b.y][b.x]].building) errs.push(`building owner on ${st.terrain[b.y][b.x]} ${b.x},${b.y}`);
  for (const g of m.graves || []) if (st.terrain[g.y][g.x] === 'water') errs.push(`grave in water ${g.x},${g.y}`);
  for (const k of m.marks || []) if (!st.terrain[k.y] || !isFinite(ctx.__T[st.terrain[k.y][k.x]].cost)) errs.push(`mark on bad tile ${k.x},${k.y}`);
  // схватка: замки всех сторон связаны пешим путём (мечник)
  if (m.skirmish) {
    const reach = new Set([m.buildings[0].x + ',' + m.buildings[0].y]), open = [[m.buildings[0].x, m.buildings[0].y]];
    while (open.length) {
      const [x, y] = open.pop();
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = x + dx, ny = y + dy, k = nx + ',' + ny;
        if (nx < 0 || ny < 0 || nx >= st.w || ny >= st.h || reach.has(k) || !isFinite(ctx.__T[st.terrain[ny][nx]].cost)) continue;
        reach.add(k); open.push([nx, ny]);
      }
    }
    for (const b of m.buildings) if (!reach.has(b.x + ',' + b.y)) errs.push(`castle ${b.x},${b.y} unreachable on foot`);
  }
  // кампания: норма ходов для звёзд
  if (!m.skirmish && !(m.par > 0)) errs.push('no par');
  if (errs.length) bad++;
  console.log(m.id, `${st.w}x${st.h}`, errs.length ? errs.join('; ') : 'ok');
}
process.exit(bad ? 1 : 0);
