// ИИ против ИИ на миссиях кампании, без браузера.
// node tools/sim.js [--runs 20] [--mission m2] [--turns 40]
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');

const LOGIC = ['data.js', 'rules.js', 'ai.js', 'campaign.js', 'skirmish.js'];
function loadLogic() {
  const ctx = vm.createContext({ console, Math });
  for (const f of LOGIC) vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'js', f), 'utf8'), ctx, { filename: f });
  // const/let верхнего уровня не попадают в контекст: достаём через выражение
  return vm.runInContext('({ MISSIONS: MISSIONS.concat(SKIRMISH_MAPS.map(m => skirmishMission(skirmishId(m.id, 1000, "a".repeat(m.slots.length))))), createState, beginTurn, aiNextAction, aiApply, evaluateOutcome, spawnNear, teamUnits })', ctx);
}

const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const runs = +arg('runs', 20), maxTurns = +arg('turns', 40), only = arg('mission', null);

const G = loadLogic();
for (const m of G.MISSIONS) {
  if (only && m.id !== only) continue;
  const res = { win: 0, lose: 0, timeout: 0 }, turns = [];
  for (let r = 0; r < runs; r++) {
    const st = G.createState(m, { seed: r + 1 });
    st.teams.blue.ai = true;
    const fired = new Set();
    const runTriggers = ctx => (m.triggers || []).forEach((t, i) => {
      if (fired.has(i) || !t.when(st, ctx)) return;
      fired.add(i);
      for (const s of t.spawn || []) G.spawnNear(st, s.type, s.team, s.x, s.y);
    });
    let events = G.beginTurn(st), out = null, steps = 0;
    runTriggers({ phase: 'turn' });
    while (!out && st.turn <= maxTurns) {
      if (++steps > 20000) throw new Error('stuck in ' + m.id);
      const a = G.aiNextAction(st, m);
      const turnBefore = st.active;
      events = G.aiApply(st, a);
      runTriggers({ phase: 'events', events });
      if (a.kind === 'end' && st.active !== turnBefore) runTriggers({ phase: 'turn' });
      out = G.evaluateOutcome(st, m);
    }
    res[out || 'timeout']++;
    turns.push(st.turn);
  }
  const avg = (turns.reduce((a, b) => a + b, 0) / turns.length).toFixed(1);
  console.log(`${m.id} ${m.title}: win ${res.win}, lose ${res.lose}, timeout ${res.timeout}, avg turns ${avg}`);
}
