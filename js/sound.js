/* ============ SOUND: готовые эффекты и музыка (assets/audio, CC0) ============ */
'use strict';

// Имя события -> варианты файлов (берётся случайный).
const SFX_FILES = {
  click: ['metalClick'],
  select: ['cloth1'],
  move: ['footstep00', 'footstep02', 'footstep04', 'footstep06'],
  swing: ['drawKnife1', 'drawKnife2'],
  hit: ['chop', 'knifeSlice'],
  death: ['dropLeather'],
  level: ['level'],
  capture: ['doorOpen_1'],
  buy: ['handleCoins'],
  raise: ['creak1'],
  destroy: ['doorClose_4'],
  turn: ['bookFlip1'],
  win: ['win'],
  lose: ['lose'],
};

const MUSIC_FILES = { menu: 'assets/audio/music/menu.mp3', battle: 'assets/audio/music/battle.ogg' };

const SND = {
  ctx: null,
  muted: false,       // эффекты
  musicMuted: false,  // музыка
  volume: 0.6,
  musicVolume: 0.35,
  buffers: {},
  loading: null,
  track: null,        // какая музыка должна играть
  audio: null,        // <audio> текущей музыки

  ensure() {
    if (this.ctx) return this.ctx;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    this.ctx = new AC();
    this.load();
    return this.ctx;
  },

  load() {
    if (this.loading) return this.loading;
    const names = [...new Set(Object.values(SFX_FILES).flat())];
    this.loading = Promise.all(names.map(n =>
      fetch(`assets/audio/sfx/${n}.ogg`)
        .then(r => r.arrayBuffer())
        .then(b => new Promise((ok, fail) => this.ctx.decodeAudioData(b, ok, fail)))
        .then(buf => { this.buffers[n] = buf; })
        .catch(() => {})));
    return this.loading;
  },

  play(name) {
    if (this.muted || !this.ensure()) return;
    if (this.ctx.state === 'suspended') this.ctx.resume();
    const list = SFX_FILES[name];
    if (!list) return;
    const buf = this.buffers[list[Math.floor(Math.random() * list.length)]];
    if (!buf) return;
    const s = this.ctx.createBufferSource(), g = this.ctx.createGain();
    g.gain.value = this.volume;
    s.buffer = buf;
    s.connect(g).connect(this.ctx.destination);
    s.start();
  },

  // Музыка: 'menu' | 'battle' | null. До первого касания браузер не даст играть - тогда стартуем в unlock().
  music(track) {
    this.track = track;
    if (this.audio && this.audio.dataset.track === track && !this.musicMuted) {
      if (this.audio.paused) this.audio.play().catch(() => {});
      return;
    }
    if (this.audio) { this.audio.pause(); this.audio = null; }
    if (!track || this.musicMuted) return;
    const a = new Audio(MUSIC_FILES[track]);
    a.loop = true;
    a.volume = this.musicVolume;
    a.dataset.track = track;
    this.audio = a;
    a.play().catch(() => {});
  },

  setMusicMuted(v) {
    this.musicMuted = v;
    if (v) { if (this.audio) this.audio.pause(); }
    else this.music(this.track);
  },

  // Вызывается на первом касании: открывает WebAudio и запускает отложенную музыку.
  unlock() {
    this.ensure();
    if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume();
    if (this.audio && this.audio.paused && !this.musicMuted) this.audio.play().catch(() => {});
  },
};
