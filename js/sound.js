/* ============ SOUND: синтезированные эффекты (WebAudio, без файлов) ============ */
'use strict';

const SND = {
  ctx: null,
  muted: false,
  volume: 0.35,

  ensure() {
    if (this.ctx) return this.ctx;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    this.ctx = new AC();
    return this.ctx;
  },

  tone(freq, dur, { type = 'square', vol = 1, slide = 0, delay = 0 } = {}) {
    const c = this.ctx, t = c.currentTime + delay;
    const o = c.createOscillator(), g = c.createGain();
    o.type = type;
    o.frequency.setValueAtTime(freq, t);
    if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(30, freq + slide), t + dur);
    g.gain.setValueAtTime(this.volume * vol * 0.3, t);
    g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    o.connect(g).connect(c.destination);
    o.start(t);
    o.stop(t + dur + 0.02);
  },

  noise(dur, { vol = 1, freq = 1200, delay = 0 } = {}) {
    const c = this.ctx, t = c.currentTime + delay;
    const buf = c.createBuffer(1, Math.floor(c.sampleRate * dur), c.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / d.length);
    const s = c.createBufferSource(), f = c.createBiquadFilter(), g = c.createGain();
    f.type = 'bandpass'; f.frequency.value = freq;
    g.gain.value = this.volume * vol * 0.6;
    s.buffer = buf;
    s.connect(f).connect(g).connect(c.destination);
    s.start(t);
  },

  play(name) {
    if (this.muted || !this.ensure()) return;
    if (this.ctx.state === 'suspended') this.ctx.resume();
    switch (name) {
      case 'click': this.tone(660, 0.05, { vol: 0.4 }); break;
      case 'select': this.tone(520, 0.06, { vol: 0.5 }); this.tone(780, 0.06, { vol: 0.5, delay: 0.05 }); break;
      case 'move': this.tone(300, 0.05, { type: 'triangle', vol: 0.5 }); break;
      case 'swing': this.noise(0.12, { freq: 2500, vol: 0.6 }); break;
      case 'hit': this.noise(0.15, { freq: 500, vol: 1 }); this.tone(110, 0.15, { type: 'sine', slide: -60 }); break;
      case 'death': this.tone(220, 0.4, { type: 'sawtooth', slide: -170, vol: 0.6 }); break;
      case 'level': [523, 659, 784, 1047].forEach((f, i) => this.tone(f, 0.12, { vol: 0.5, delay: i * 0.07 })); break;
      case 'capture': this.tone(392, 0.12, { vol: 0.5 }); this.tone(587, 0.2, { vol: 0.5, delay: 0.1 }); break;
      case 'buy': this.tone(988, 0.08, { vol: 0.4 }); this.tone(1319, 0.15, { vol: 0.4, delay: 0.07 }); break;
      case 'raise': this.tone(180, 0.6, { type: 'triangle', slide: 200, vol: 0.6 }); this.tone(185, 0.6, { type: 'triangle', slide: 210, vol: 0.4 }); break;
      case 'turn': this.tone(440, 0.1, { type: 'triangle', vol: 0.6 }); this.tone(660, 0.18, { type: 'triangle', vol: 0.6, delay: 0.1 }); break;
      case 'win': [523, 659, 784, 1047, 784, 1047].forEach((f, i) => this.tone(f, 0.18, { type: 'triangle', vol: 0.6, delay: i * 0.12 })); break;
      case 'lose': [392, 349, 311, 262].forEach((f, i) => this.tone(f, 0.3, { type: 'triangle', vol: 0.6, delay: i * 0.2 })); break;
    }
  },
};
