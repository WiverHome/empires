/* ============ ACHIEVEMENTS: статистика партии, звёзды миссий, достижения (без DOM) ============ */
'use strict';

// Статистика стороны в партии: лежит в st.stats, поэтому переживает сохранение и отмену хода
function teamStats(st, team) {
  st.stats = st.stats || {};
  return st.stats[team] = st.stats[team] || { kills: 0, lost: 0, raised: 0, destroyed: 0, captured: 0 };
}

// Учесть события действия. Убийцу берём из удара перед смертью.
function trackEvents(st, events) {
  let lastFrom = null;
  for (const e of events) {
    if (e.type === 'hit') { const a = unitById(st, e.from); lastFrom = a ? a.team : st.active; }
    else if (e.type === 'death') {
      if (UNIT_DEFS[e.unitType].noAttack) continue;
      teamStats(st, e.team).lost++;
      const killer = lastFrom && lastFrom !== e.team ? lastFrom : st.active;
      if (killer !== e.team) teamStats(st, killer).kills++;
    }
    else if (e.type === 'raise') teamStats(st, st.active).raised++;
    else if (e.type === 'destroy') teamStats(st, st.active).destroyed++;
    else if (e.type === 'capture') teamStats(st, st.active).captured++;
  }
}

// Звёзды за победу в миссии кампании: победа, не дольше par ходов, не больше maxLost павших
const STARS_MAX_LOST = 3;
function missionStars(mission, st) {
  const s = teamStats(st, 'blue');
  let n = 1;
  if (mission.par && st.turn <= mission.par) n++;
  if (s.lost <= (mission.maxLost ?? STARS_MAX_LOST)) n++;
  return n;
}
function starsHint(mission) {
  return `★ победа · ★ не дольше ${mission.par} ходов · ★ не больше ${mission.maxLost ?? STARS_MAX_LOST} павших`;
}

// ctx: { phase: 'events' | 'end', st, mission, events, res, humans, save }
// save - SAVE.data (stars, achievements, prefs). check возвращает true, если достижение заработано.
const humanWon = c => c.phase === 'end' && c.res === 'win' && (!c.mission.skirmish || c.humans.includes(c.st.winner));
const vsAi = c => c.st.order.some(t => c.st.teams[t].ai);
const ACHIEVEMENTS = [
  { id: 'first_win', name: 'Первая победа', desc: 'Выиграйте любую партию.', check: humanWon },
  { id: 'chapter1', name: 'Конец Морвена', desc: 'Пройдите первую главу кампании.', check: c => humanWon(c) && c.mission.id === 'm4' },
  { id: 'chapter2', name: 'Тишина на болотах', desc: 'Пройдите вторую главу кампании.', check: c => humanWon(c) && c.mission.id === 'm8' },
  { id: 'chapter3', name: 'Хроники завершены', desc: 'Пройдите третью главу кампании.', check: c => humanWon(c) && c.mission.id === 'm12' },
  { id: 'three_stars', name: 'Безупречно', desc: 'Получите три звезды в миссии.', check: c => humanWon(c) && !c.mission.skirmish && missionStars(c.mission, c.st) === 3 },
  { id: 'all_stars', name: 'Летописец', desc: 'Соберите все звёзды кампании.',
    check: c => typeof MISSIONS !== 'undefined' && MISSIONS.every(m => (c.save.stars || {})[m.id] === 3) },
  { id: 'skirmish_win', name: 'Полководец', desc: 'Победите ИИ в схватке.', check: c => humanWon(c) && c.mission.skirmish && vsAi(c) },
  { id: 'free_for_all', name: 'Царь горы', desc: 'Победите в схватке трёх и более сторон.', check: c => humanWon(c) && c.mission.skirmish && c.st.order.length >= 3 },
  { id: 'fog_win', name: 'Вслепую', desc: 'Победите в схватке с туманом войны.', check: c => humanWon(c) && c.mission.skirmish && c.st.fog && vsAi(c) },
  { id: 'hard_win', name: 'Железная воля', desc: 'Победите ИИ на сложной сложности.', check: c => humanWon(c) && vsAi(c) && c.st.difficulty === 'hard' },
  { id: 'blitz', name: 'Молниеносно', desc: 'Победите в схватке с ИИ за 12 ходов или быстрее.', check: c => humanWon(c) && c.mission.skirmish && vsAi(c) && c.st.turn <= 12 },
  { id: 'no_losses', name: 'Ни шагу назад', desc: 'Победите, не потеряв ни одного воина.', check: c => humanWon(c) && c.humans.every(t => teamStats(c.st, t).lost === 0) },
  { id: 'hotseat', name: 'Друзья-соперники', desc: 'Доиграйте партию вдвоём на одном телефоне.', check: c => c.phase === 'end' && c.humans.length >= 2 },
  { id: 'veteran', name: 'Ветеран', desc: 'Поднимите воина до 5 уровня.',
    check: c => c.events.some(e => e.type === 'levelup' && e.level >= 5 && c.humans.includes(e.team)) },
  { id: 'necromancer', name: 'Тёмное искусство', desc: 'Поднимите призрака из могилы.', check: c => c.humans.includes(c.st.active) && c.events.some(e => e.type === 'raise') },
  { id: 'siege', name: 'Осадный мастер', desc: 'Разрушьте деревню катапультой.', check: c => c.humans.includes(c.st.active) && c.events.some(e => e.type === 'destroy') },
  { id: 'full_army', name: 'Великая армия', desc: `Соберите отряд из ${BAL.unitCap} воинов.`, check: c => c.humans.some(t => teamUnits(c.st, t).length >= BAL.unitCap) },
  { id: 'map_maker', name: 'Картограф', desc: 'Создайте свою карту в редакторе.', check: c => c.phase === 'map' },
];

// Новые достижения (id), которых ещё нет в save.achievements
function checkAchievements(ctx) {
  const have = ctx.save.achievements || {};
  if (ctx.phase === 'map') return have.map_maker ? [] : ['map_maker'];
  ctx.events = ctx.events || [];
  return ACHIEVEMENTS.filter(a => !have[a.id] && a.check(ctx)).map(a => a.id);
}
