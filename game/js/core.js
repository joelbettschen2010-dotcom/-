/* ============================================================
   Horizon Rush – Kern
   Mathe-Helfer, Zufallsgenerator mit Saat, Speicherstand,
   Fahrzeugdaten, Tuning und Motorsound.
   ============================================================ */
window.HR = window.HR || {};
(function (HR) {
'use strict';

/* ---------------------------------------------------------- Mathe */
const U = HR.U = {
  clamp: (v, a, b) => v < a ? a : (v > b ? b : v),
  lerp: (a, b, t) => a + (b - a) * t,
  easeIn:    (a, b, p) => a + (b - a) * Math.pow(p, 2),
  easeOut:   (a, b, p) => a + (b - a) * (1 - Math.pow(1 - p, 2)),
  easeInOut: (a, b, p) => a + (b - a) * ((-Math.cos(p * Math.PI) / 2) + 0.5),
  /* zyklisch weiterzählen (Position auf der Rundstrecke) */
  increase(start, inc, max) {
    let r = start + inc;
    while (r >= max) r -= max;
    while (r < 0) r += max;
    return r;
  },
  pctRemaining: (n, total) => (n % total) / total,
  /* 1:23.45 */
  fmtTime(s) {
    if (!isFinite(s) || s < 0) s = 0;
    const m = Math.floor(s / 60), r = s - m * 60;
    return m + ':' + (r < 10 ? '0' : '') + r.toFixed(2);
  },
  fmtClock(s) {
    if (!isFinite(s) || s < 0) s = 0;
    const m = Math.floor(s / 60), r = Math.floor(s - m * 60);
    return m + ':' + (r < 10 ? '0' : '') + r;
  },
  fmtNum(n) { return Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, "'"); },
  /* Farbmischung für Himmel/Nebel */
  mix(c1, c2, t) {
    const a = U.hex2rgb(c1), b = U.hex2rgb(c2);
    return 'rgb(' + Math.round(U.lerp(a[0], b[0], t)) + ',' +
                    Math.round(U.lerp(a[1], b[1], t)) + ',' +
                    Math.round(U.lerp(a[2], b[2], t)) + ')';
  },
  hex2rgb(h) {
    if (h[0] !== '#') { const m = h.match(/\d+/g); return [+m[0], +m[1], +m[2]]; }
    if (h.length === 4) h = '#' + h[1] + h[1] + h[2] + h[2] + h[3] + h[3];
    return [parseInt(h.substr(1, 2), 16), parseInt(h.substr(3, 2), 16), parseInt(h.substr(5, 2), 16)];
  },
  shade(hex, f) {          /* f>0 heller, f<0 dunkler */
    const c = U.hex2rgb(hex);
    const g = v => Math.round(U.clamp(f > 0 ? v + (255 - v) * f : v * (1 + f), 0, 255));
    return 'rgb(' + g(c[0]) + ',' + g(c[1]) + ',' + g(c[2]) + ')';
  },
  rgba(hex, a) { const c = U.hex2rgb(hex); return 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',' + a + ')'; }
};

/* ---------------------------------------------------------- Zufall mit Saat
   Gleiche Saat = exakt gleiche Strecke. Das macht Tagesrouten und
   das Wiederholen von Events möglich, ohne irgendetwas zu speichern. */
function hashStr(s) {
  let h = 2166136261 >>> 0;
  s = String(s);
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return h >>> 0;
}
function mulberry32(a) {
  return function () {
    a |= 0; a = a + 0x6D2B79F5 | 0;
    let t = Math.imul(a ^ a >>> 15, 1 | a);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}
HR.hashStr = hashStr;
HR.RNG = function (seed) {
  const r = mulberry32(typeof seed === 'string' ? hashStr(seed) : (seed >>> 0));
  const api = {
    next: r,
    range: (a, b) => a + r() * (b - a),
    int: (a, b) => Math.floor(a + r() * (b - a + 1)),
    pick: arr => arr[Math.floor(r() * arr.length)],
    chance: p => r() < p,
    sign: () => r() < 0.5 ? -1 : 1,
    /* [{w:3,v:'x'},…] */
    weighted(list) {
      let t = 0; for (const it of list) t += it.w;
      let x = r() * t;
      for (const it of list) { x -= it.w; if (x <= 0) return it.v; }
      return list[list.length - 1].v;
    },
    shuffle(arr) {
      for (let i = arr.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); const t = arr[i]; arr[i] = arr[j]; arr[j] = t; }
      return arr;
    }
  };
  return api;
};

/* ---------------------------------------------------------- Fahrzeuge */
const CARS = HR.CARS = [
  { id: 'v88',   name: 'Vulpes 88',   brand: 'Vulpes',  cls: 'D', price: 0,      top: 196, acc: 0.40, grip: 0.52, nos: 0.45, body: 'hatch',  col: '#ef4b3c' },
  { id: 'lynx',  name: 'Lynx RS',     brand: 'Auria',   cls: 'C', price: 13000,  top: 224, acc: 0.50, grip: 0.58, nos: 0.50, body: 'coupe',  col: '#2f9dff' },
  { id: 'delta', name: 'Delta Nord',  brand: 'Nordval', cls: 'C', price: 19000,  top: 231, acc: 0.54, grip: 0.70, nos: 0.55, body: 'rally',  col: '#f2ede0' },
  { id: 'mira',  name: 'Mira Turbo',  brand: 'Sanzo',   cls: 'B', price: 34000,  top: 256, acc: 0.62, grip: 0.61, nos: 0.64, body: 'coupe',  col: '#ffd21e' },
  { id: 'tarn',  name: 'Tarn GTX',    brand: 'Brakken', cls: 'B', price: 47000,  top: 264, acc: 0.65, grip: 0.72, nos: 0.58, body: 'muscle', col: '#1fbf75' },
  { id: 'sol',   name: 'Solano V10',  brand: 'Ferrara', cls: 'A', price: 79000,  top: 294, acc: 0.74, grip: 0.75, nos: 0.68, body: 'super',  col: '#ff2e63' },
  { id: 'komet', name: 'Komet Neun',  brand: 'Ostwind', cls: 'A', price: 98000,  top: 303, acc: 0.78, grip: 0.71, nos: 0.73, body: 'super',  col: '#7b5cff' },
  { id: 'gale',  name: 'Gale Type-R', brand: 'Auria',   cls: 'S', price: 150000, top: 317, acc: 0.84, grip: 0.81, nos: 0.78, body: 'super',  col: '#22e6c8' },
  { id: 'vortx', name: 'Vortex GT1',  brand: 'Brakken', cls: 'S', price: 212000, top: 331, acc: 0.88, grip: 0.86, nos: 0.83, body: 'proto',  col: '#ff8a1f' },
  { id: 'aurum', name: 'Aurum X',     brand: 'Ferrara', cls: 'X', price: 330000, top: 352, acc: 0.94, grip: 0.90, nos: 0.90, body: 'proto',  col: '#ffd700' }
];
HR.carById = id => CARS.find(c => c.id === id) || CARS[0];

const UPGRADES = HR.UPGRADES = [
  { id: 'motor',    name: 'Motor',    desc: 'Höchstgeschwindigkeit + Antritt', icon: '⚙️' },
  { id: 'getriebe', name: 'Getriebe', desc: 'Beschleunigung aus Kurven',       icon: '🔧' },
  { id: 'reifen',   name: 'Reifen',   desc: 'Grip, weniger Schieben',          icon: '🛞' },
  { id: 'turbo',    name: 'Lachgas',  desc: 'NOS-Schub und Tankgrösse',        icon: '💨' }
];
const CLS_FACTOR = { D: 1, C: 1.5, B: 2.4, A: 4, S: 6.5, X: 10 };
HR.upgradeCost = (car, lvl) => Math.round((1400 + lvl * 2100) * (CLS_FACTOR[car.cls] || 1));
HR.MAX_UP = 5;

/* Tatsächliche Werte inkl. Tuning */
HR.effStats = function (carId) {
  const base = HR.carById(carId);
  const u = (HR.Save.data.cars[carId] && HR.Save.data.cars[carId].up) || { motor: 0, getriebe: 0, reifen: 0, turbo: 0 };
  return {
    top:  base.top  * (1 + 0.030 * u.motor),
    acc:  Math.min(0.99, base.acc  * (1 + 0.050 * u.getriebe + 0.022 * u.motor)),
    grip: Math.min(0.99, base.grip * (1 + 0.058 * u.reifen)),
    nos:  base.nos  * (1 + 0.10 * u.turbo),
    pi:   0
  };
};
/* Leistungsindex 100…999 – für Klassenvergleich und Gegnerstärke */
HR.perfIndex = function (carId) {
  const s = HR.effStats(carId);
  return Math.round(60 + (s.top - 180) * 1.55 + s.acc * 210 + s.grip * 150 + s.nos * 60);
};

/* ---------------------------------------------------------- Speicherstand */
const SAVE_KEY = 'horizonRush.save.v1';
const Save = HR.Save = {
  data: null,
  /* Manche Umgebungen (privater Modus, eingebettete Rahmen, lokale Dateien)
     sperren den Speicher. Dann laeuft das Spiel weiter, warnt aber und
     bietet die Sicherung per Code an. */
  available: (function () {
    try { localStorage.setItem('hr.t', '1'); localStorage.removeItem('hr.t'); return true; }
    catch (e) { return false; }
  })(),
  fresh() {
    return {
      seed: (Math.random() * 1e9) | 0,
      created: Date.now(),
      credits: 6000,
      xp: 0,
      level: 1,
      skill: 46,                       /* adaptive Gegnerstärke 0…100 */
      car: 'v88',
      cars: { v88: { up: { motor: 0, getriebe: 0, reifen: 0, turbo: 0 }, col: null } },
      events: {},                      /* id -> {stars, best} */
      best: {},                        /* modus/strecke -> Bestwert */
      daily: {},                       /* datum -> score */
      stats: { races: 0, wins: 0, km: 0, drift: 0, nearMiss: 0, topSpeed: 0, playtime: 0 },
      settings: { sound: true, steer: 'pad', autoGas: true, quality: 'auto', assist: true, rubber: true, aiBoost: 0 },
      seenIntro: false
    };
  },
  load() {
    let d = null;
    try { d = JSON.parse(localStorage.getItem(SAVE_KEY) || 'null'); } catch (e) { d = null; }
    const f = this.fresh();
    if (!d || typeof d !== 'object') { this.data = f; this.save(); return this.data; }
    /* fehlende Felder aus dem Standard ergänzen (Vorwärtskompatibilität) */
    const merge = (dst, src) => {
      for (const k in src) {
        if (dst[k] === undefined || dst[k] === null) dst[k] = src[k];
        else if (typeof src[k] === 'object' && !Array.isArray(src[k]) && typeof dst[k] === 'object') merge(dst[k], src[k]);
      }
      return dst;
    };
    this.data = merge(d, f);
    if (!this.data.cars[this.data.car]) this.data.car = 'v88';
    return this.data;
  },
  save() {
    try { localStorage.setItem(SAVE_KEY, JSON.stringify(this.data)); this.available = true; }
    catch (e) { this.available = false; }
  },
  reset() { this.data = this.fresh(); this.save(); },

  /* ---- Sicherung als Text-Code, geraeteunabhaengig ---- */
  exportCode() {
    const json = JSON.stringify(this.data);
    return 'HR1.' + btoa(unescape(encodeURIComponent(json)));
  },
  importCode(code) {
    code = String(code || '').trim().replace(/\s+/g, '');
    if (code.indexOf('HR1.') !== 0) return 'Das ist kein Horizon-Rush-Code (er beginnt mit HR1.).';
    let obj;
    try { obj = JSON.parse(decodeURIComponent(escape(atob(code.slice(4))))); }
    catch (e) { return 'Der Code ist unvollständig oder beschädigt.'; }
    if (!obj || typeof obj !== 'object' || !obj.cars || typeof obj.credits !== 'number')
      return 'Der Code enthält keinen gültigen Spielstand.';
    this.data = obj;
    /* fehlende Felder aus dem Standard ergaenzen */
    const f = this.fresh();
    const merge = (dst, src) => {
      for (const k in src) {
        if (dst[k] === undefined || dst[k] === null) dst[k] = src[k];
        else if (typeof src[k] === 'object' && !Array.isArray(src[k]) && typeof dst[k] === 'object') merge(dst[k], src[k]);
      }
    };
    merge(this.data, f);
    if (!this.data.cars[this.data.car]) this.data.car = 'v88';
    this.save();
    return null;
  },

  addCredits(n) { this.data.credits = Math.max(0, Math.round(this.data.credits + n)); this.save(); },
  /* gibt zurück, wie viele Stufen aufgestiegen wurde */
  addXP(n) {
    const d = this.data;
    d.xp += Math.round(n);
    let up = 0;
    while (d.xp >= HR.xpForLevel(d.level)) { d.xp -= HR.xpForLevel(d.level); d.level++; up++; }
    this.save();
    return up;
  },
  owns(id) { return !!this.data.cars[id]; },
  buyCar(id) {
    const c = HR.carById(id);
    if (this.owns(id) || this.data.credits < c.price) return false;
    this.data.credits -= c.price;
    this.data.cars[id] = { up: { motor: 0, getriebe: 0, reifen: 0, turbo: 0 }, col: null };
    this.data.car = id;
    this.save();
    return true;
  }
};
HR.xpForLevel = lvl => Math.round(420 + lvl * 260 + lvl * lvl * 26);

/* ---------------------------------------------------------- Ton
   Reiner Synthesizer – keine Audiodateien, damit alles offline läuft.
   iOS erlaubt Ton erst nach einer Berührung: init() aus einem Tap heraus. */
const Audio2 = HR.Audio = {
  ctx: null, ready: false, muted: false,
  init() {
    if (this.ctx) { if (this.ctx.state === 'suspended') this.ctx.resume(); return; }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    try {
      const ctx = this.ctx = new AC();
      const master = this.master = ctx.createGain();
      master.gain.value = 0.55; master.connect(ctx.destination);

      /* Motor: zwei Oszillatoren durch ein Tiefpassfilter */
      const eg = this.eg = ctx.createGain(); eg.gain.value = 0;
      const ef = this.ef = ctx.createBiquadFilter(); ef.type = 'lowpass'; ef.frequency.value = 760; ef.Q.value = 5;
      eg.connect(ef); ef.connect(master);
      const o1 = ctx.createOscillator(); o1.type = 'sawtooth'; o1.frequency.value = 55;
      const o2 = ctx.createOscillator(); o2.type = 'square';   o2.frequency.value = 27.5;
      const g2 = ctx.createGain(); g2.gain.value = 0.45;
      o1.connect(eg); o2.connect(g2); g2.connect(eg);
      o1.start(); o2.start(); this.o1 = o1; this.o2 = o2;

      /* Fahrtwind / Reifen: gefiltertes Rauschen */
      const buf = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate);
      const dat = buf.getChannelData(0);
      for (let i = 0; i < dat.length; i++) dat[i] = Math.random() * 2 - 1;
      const src = ctx.createBufferSource(); src.buffer = buf; src.loop = true;
      const nf = this.nf = ctx.createBiquadFilter(); nf.type = 'bandpass'; nf.frequency.value = 900; nf.Q.value = 0.6;
      const ng = this.ng = ctx.createGain(); ng.gain.value = 0;
      src.connect(nf); nf.connect(ng); ng.connect(master); src.start();

      this.ready = true;
    } catch (e) { this.ready = false; }
  },
  setMuted(m) {
    this.muted = m;
    if (this.master) this.master.gain.value = m ? 0 : 0.55;
  },
  /* rpm 0…1, load 0…1 (Gas), extra: NOS */
  engine(rpm, load, on, nos) {
    if (!this.ready || this.muted) return;
    const t = this.ctx.currentTime;
    const f = 46 + rpm * 210 + (nos ? 26 : 0);
    this.o1.frequency.setTargetAtTime(f, t, 0.05);
    this.o2.frequency.setTargetAtTime(f * 0.5, t, 0.05);
    this.ef.frequency.setTargetAtTime(430 + rpm * 1500 + load * 500, t, 0.08);
    this.eg.gain.setTargetAtTime(on ? (0.055 + load * 0.10 + rpm * 0.05) : 0, t, 0.10);
  },
  wind(v, screech) {
    if (!this.ready || this.muted) return;
    const t = this.ctx.currentTime;
    this.nf.frequency.setTargetAtTime(screech ? 2400 : (500 + v * 1500), t, 0.08);
    this.nf.Q.setTargetAtTime(screech ? 6 : 0.6, t, 0.08);
    this.ng.gain.setTargetAtTime(v * 0.05 + (screech ? 0.075 : 0), t, 0.09);
  },
  stopEngine() { if (this.ready) { this.eg.gain.setTargetAtTime(0, this.ctx.currentTime, 0.1); this.ng.gain.setTargetAtTime(0, this.ctx.currentTime, 0.1); } },
  /* kurzer Ton */
  blip(freq, dur, type, vol) {
    if (!this.ready || this.muted) return;
    const ctx = this.ctx, t = ctx.currentTime;
    const o = ctx.createOscillator(), g = ctx.createGain();
    o.type = type || 'triangle'; o.frequency.setValueAtTime(freq, t);
    g.gain.setValueAtTime(0, t);
    g.gain.linearRampToValueAtTime(vol == null ? 0.18 : vol, t + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, t + (dur || 0.14));
    o.connect(g); g.connect(this.master); o.start(t); o.stop(t + (dur || 0.14) + 0.03);
  },
  ui() { this.blip(660, 0.08, 'triangle', 0.10); },
  ok() { this.blip(880, 0.10, 'triangle', 0.13); setTimeout(() => this.blip(1320, 0.12, 'triangle', 0.11), 80); },
  countdown(last) { this.blip(last ? 1100 : 620, last ? 0.42 : 0.16, 'square', 0.14); },
  checkpoint() { this.blip(1046, 0.09, 'triangle', 0.14); setTimeout(() => this.blip(1568, 0.16, 'triangle', 0.12), 70); },
  crash() {
    if (!this.ready || this.muted) return;
    const ctx = this.ctx, t = ctx.currentTime;
    const buf = ctx.createBuffer(1, ctx.sampleRate * 0.3, ctx.sampleRate), d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / d.length, 2.2);
    const s = ctx.createBufferSource(); s.buffer = buf;
    const f = ctx.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = 900;
    const g = ctx.createGain(); g.gain.value = 0.32;
    s.connect(f); f.connect(g); g.connect(this.master); s.start(t);
  },
  nosBurst() {
    if (!this.ready || this.muted) return;
    const ctx = this.ctx, t = ctx.currentTime;
    const buf = ctx.createBuffer(1, ctx.sampleRate * 0.5, ctx.sampleRate), d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / d.length);
    const s = ctx.createBufferSource(); s.buffer = buf;
    const f = ctx.createBiquadFilter(); f.type = 'highpass'; f.frequency.setValueAtTime(400, t);
    f.frequency.exponentialRampToValueAtTime(4000, t + 0.45);
    const g = ctx.createGain(); g.gain.value = 0.16;
    s.connect(f); f.connect(g); g.connect(this.master); s.start(t);
  }
};

/* ---------------------------------------------------------- Vibration (Android; iOS ignoriert es) */
HR.buzz = function (ms) { try { if (navigator.vibrate) navigator.vibrate(ms); } catch (e) { } };

Save.load();

/* Zusätzlich sichern, sobald die App in den Hintergrund geht oder geschlossen
   wird – iOS beendet Safari-Tabs gerne ohne Vorwarnung. */
document.addEventListener('visibilitychange', () => { if (document.hidden) Save.save(); });
window.addEventListener('pagehide', () => Save.save());
window.addEventListener('blur', () => Save.save());

})(window.HR);
