// Проверка карт миссий: размеры, юниты и здания на допустимых клетках.
// node tools/check-maps.js
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');
const ctx = vm.createContext({ console, Math });
for (const f of ['data.js', 'rules.js', 'ai.js', 'campaign.js'])
  vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'js', f), 'utf8'), ctx, { filename: f });

const MAX_W = 11, MAX_H = 15;   // вертикальный экран телефона без прокрутки
let bad = 0;
vm.runInContext('this.__M = MISSIONS; this.__create = createState; this.__T = TERRAIN; this.__U = UNIT_DEFS;', ctx);
for (const m of ctx.__M) {
  const errs = [];
  let st;
  try { st = ctx.__create(m, { seed: 1 }); } catch (e) { console.log(m.id, 'ERROR', e.message); bad++; continue; }
  if (st.w > MAX_W || st.h > MAX_H) errs.push(`size ${st.w}x${st.h} > ${MAX_W}x${MAX_H}`);
  const seen = new Set();
  for (const u of m.units) {
    const t = st.terrain[u.y] && st.terrain[u.y][u.x];
    if (!t) { errs.push(`unit out of map ${u.x},${u.y}`); continue; }
    if (!isFinite(ctx.__T[t].cost) && !ctx.__U[u.type].flying) errs.push(`${u.type} on ${t} ${u.x},${u.y}`);
    const k = u.x + ',' + u.y;
    if (seen.has(k)) errs.push('two units at ' + k);
    seen.add(k);
  }
  for (const b of m.buildings || []) if (!ctx.__T[st.terrain[b.y][b.x]].building) errs.push(`building owner on ${st.terrain[b.y][b.x]} ${b.x},${b.y}`);
  for (const g of m.graves || []) if (st.terrain[g.y][g.x] === 'water') errs.push(`grave in water ${g.x},${g.y}`);
  if (errs.length) bad++;
  console.log(m.id, `${st.w}x${st.h}`, errs.length ? errs.join('; ') : 'ok');
}
process.exit(bad ? 1 : 0);
