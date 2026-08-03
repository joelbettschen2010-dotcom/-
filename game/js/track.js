/* ============================================================
   Horizon Rush – Streckengenerator (der "Algorithmus")
   Aus einer Saat (Text) entsteht deterministisch eine komplette
   Strecke: Kurven, Kuppen, Landschaftszonen, Bewuchs, Schilder.
   Gleiche Saat -> exakt gleiche Strecke, ohne dass etwas
   gespeichert werden muss.
   ============================================================ */
(function (HR) {
'use strict';
const U = HR.U, Art = HR.Art;

/* Weltmasse */
const SEG = HR.SEG = {
  LEN: 200,        /* Länge eines Segments in Welteinheiten */
  RUMBLE: 3,       /* Segmente pro Randstreifen-Block       */
  ROAD_W: 2200,    /* halbe Fahrbahnbreite                  */
  LANES: 3
};

/* ---------------------------------------------------------- Landschaftszonen */
const BIOMES = HR.BIOMES = {
  kueste: {
    id: 'kueste', name: 'Küste', night: false,
    sky: ['#1b5fb0', '#4ea3dd', '#a9dcf2'], sun: '#fff3c4', sunY: 0.62, sunR: 0.10,
    mount: ['#2d6f92', '#1b4a67'], snow: false, rough: 0.54,
    grass: ['#2f8a52', '#28794a'], road: ['#4b4f5e', '#454959'],
    rumble: ['#eef1f6', '#c22f3f'], lane: '#eef1f6', fog: '#a9dcf2',
    cloud: 'rgba(255,255,255,.85)', density: 0.55, kinds: ['palm', 'bush', 'rock', 'billboard'],
    rain: 0.05
  },
  wueste: {
    id: 'wueste', name: 'Wüste', night: false,
    sky: ['#3b2170', '#c84a3a', '#ffb066'], sun: '#fff0a8', sunY: 0.80, sunR: 0.15,
    mount: ['#7a4436', '#4a2620'], snow: false, rough: 0.60,
    grass: ['#c39a5e', '#b48d52'], road: ['#55524f', '#4e4b48'],
    rumble: ['#f3e6c8', '#b8452f'], lane: '#f6ecd2', fog: '#ffb98a',
    cloud: 'rgba(255,200,150,.55)', density: 0.34, kinds: ['cactus', 'rock', 'billboard', 'bush'],
    rain: 0.0
  },
  alpen: {
    id: 'alpen', name: 'Alpen', night: false,
    sky: ['#0e3f78', '#3f86c6', '#cfe6f5'], sun: '#ffffff', sunY: 0.40, sunR: 0.07,
    mount: ['#8fb6d4', '#3d5f80'], snow: true, rough: 0.66,
    grass: ['#dfe9f2', '#cddbe8'], road: ['#3f434f', '#3a3e4a'],
    rumble: ['#ffffff', '#d03a48'], lane: '#ffffff', fog: '#e2eef8',
    cloud: 'rgba(255,255,255,.9)', density: 0.72, kinds: ['pineSnow', 'rock', 'sign'],
    rain: 0.35, snowfall: true
  },
  wald: {
    id: 'wald', name: 'Wald', night: false,
    sky: ['#123a52', '#3d7f92', '#bfe0d8'], sun: '#ffe9b0', sunY: 0.68, sunR: 0.08,
    mount: ['#2b5645', '#173628'], snow: false, rough: 0.5,
    grass: ['#276b3c', '#1f5c33'], road: ['#474b57', '#424653'],
    rumble: ['#e6ebe8', '#b8323f'], lane: '#e6ebe8', fog: '#cfe6dd',
    cloud: 'rgba(230,245,240,.75)', density: 0.9, kinds: ['pine', 'leaf', 'bush'],
    rain: 0.4
  },
  stadt: {
    id: 'stadt', name: 'Nachtstadt', night: true,
    sky: ['#05060f', '#141033', '#3a1f5c'], sun: '#dfe7ff', sunY: 0.22, sunR: 0.05,
    mount: ['#1a1533', '#0a0818'], snow: false, rough: 0.7,
    grass: ['#191d2c', '#151928'], road: ['#2b2f3c', '#262a36'],
    rumble: ['#f2f4ff', '#7b5cff'], lane: '#f2f4ff', fog: '#241a44',
    cloud: 'rgba(120,90,200,.35)', density: 0.85, kinds: ['building', 'lamp', 'billboard'],
    rain: 0.35
  },
  canyon: {
    id: 'canyon', name: 'Canyon', night: false,
    sky: ['#5a1f5e', '#d4523f', '#ffa25c'], sun: '#ffd98a', sunY: 0.72, sunR: 0.13,
    mount: ['#8c3f2e', '#4d1f18'], snow: false, rough: 0.72,
    grass: ['#a35a3a', '#8f4d32'], road: ['#4f4a48', '#494442'],
    rumble: ['#f7e3c6', '#a33528'], lane: '#f7e3c6', fog: '#ff9d6a',
    cloud: 'rgba(255,180,140,.5)', density: 0.42, kinds: ['rock', 'cactus', 'sign'],
    rain: 0.0
  }
};
const BIOME_LIST = Object.keys(BIOMES);
HR.BIOME_LIST = BIOME_LIST;

/* Sprite-Vorrat je Zone – wird einmal erzeugt und wiederverwendet,
   sonst entstehen tausende Canvas-Objekte. */
function pool(b) {
  if (b._pool) return b._pool;
  const rng = HR.RNG('pool-' + b.id);
  const p = b._pool = {};
  const mk = (key, n, fn) => { p[key] = []; for (let i = 0; i < n; i++) p[key].push(fn(rng)); };
  if (b.kinds.indexOf('palm') >= 0) mk('palm', 6, r => Art.palm(r));
  if (b.kinds.indexOf('pine') >= 0) mk('pine', 6, r => Art.pine(r, true, false));
  if (b.kinds.indexOf('pineSnow') >= 0) mk('pineSnow', 6, r => Art.pine(r, false, true));
  if (b.kinds.indexOf('leaf') >= 0) mk('leaf', 6, r => Art.broadleaf(r, '#2a7038'));
  if (b.kinds.indexOf('cactus') >= 0) mk('cactus', 5, r => Art.cactus(r));
  if (b.kinds.indexOf('rock') >= 0) mk('rock', 5, r => Art.rock(r, b.id === 'canyon' ? '#8a4a33' : (b.id === 'alpen' ? '#8d97a6' : '#6d6a63')));
  if (b.kinds.indexOf('bush') >= 0) mk('bush', 5, r => Art.bush(r, b.id === 'wueste' ? '#8a8a4a' : '#2c6b34'));
  if (b.kinds.indexOf('building') >= 0) mk('building', 7, r => Art.building(r, b.night));
  if (b.kinds.indexOf('lamp') >= 0) p.lamp = [Art.lamp(b.night)];
  if (b.kinds.indexOf('billboard') >= 0) mk('billboard', 5, r => Art.billboard(r));
  p.signL = [Art.sign(-1)]; p.signR = [Art.sign(1)];
  return p;
}
HR.biomePool = pool;

/* Hintergrundebenen je Zone (ebenfalls zwischengespeichert) */
HR.biomeBg = function (b) {
  if (b._bg) return b._bg;
  const w = 1024;
  b._bg = {
    far: Art.mountains('m1-' + b.id, w, 240, b.mount[0], b.mount[1], b.snow, b.rough * 0.7),
    near: Art.mountains('m2-' + b.id, w, 300, U.shade(b.mount[1], -0.18), U.shade(b.mount[1], -0.45), b.snow, b.rough),
    cloud: Art.clouds('c-' + b.id, w, 200, b.cloud, b.night ? 0.5 : 0.75)
  };
  return b._bg;
};

/* ---------------------------------------------------------- Generator */
const L = { S: 24, M: 48, L: 96 };
const C = { E: 2.0, M: 4.0, H: 6.2, X: 8.5 };
const HILL = { L: 20, M: 42, H: 68 };

/* cfg = {segments, difficulty 0..1, biomes:[ids], traffic 0..1, name} */
HR.buildTrack = function (seedStr, cfg) {
  cfg = cfg || {};
  const rng = HR.RNG(seedStr);
  const target = cfg.segments || 1400;
  const diff = U.clamp(cfg.difficulty == null ? 0.4 : cfg.difficulty, 0, 1);

  /* Zonenabfolge */
  let ids = (cfg.biomes && cfg.biomes.length) ? cfg.biomes.slice() : null;
  if (!ids) { ids = rng.shuffle(BIOME_LIST.slice()).slice(0, rng.int(2, 3)); }
  const zones = ids.map(id => BIOMES[id] || BIOMES.kueste);

  const segs = [];
  let zoneIdx = 0;
  const lastY = () => segs.length === 0 ? 0 : segs[segs.length - 1].p2.world.y;

  function addSegment(curve, y) {
    const n = segs.length;
    segs.push({
      index: n,
      p1: { world: { x: 0, y: lastY(), z: n * SEG.LEN }, camera: {}, screen: {} },
      p2: { world: { x: 0, y: y, z: (n + 1) * SEG.LEN }, camera: {}, screen: {} },
      curve: curve,
      sprites: [], cars: [],
      alt: Math.floor(n / SEG.RUMBLE) % 2 === 1,
      b: zones[Math.min(zoneIdx, zones.length - 1)],
      fog: 1, clip: 0
    });
  }
  function addRoad(enter, hold, leave, curve, y) {
    const startY = lastY(), endY = startY + (y || 0) * SEG.LEN;
    const total = enter + hold + leave;
    for (let n = 0; n < enter; n++) addSegment(U.easeIn(0, curve, n / enter), U.easeInOut(startY, endY, n / total));
    for (let n = 0; n < hold; n++) addSegment(curve, U.easeInOut(startY, endY, (enter + n) / total));
    for (let n = 0; n < leave; n++) addSegment(U.easeInOut(curve, 0, n / leave), U.easeInOut(startY, endY, (enter + hold + n) / total));
  }

  /* Bausteine */
  function straight(len) { addRoad(len, len, len, 0, 0); }
  function curveP(len, cv, hl) { addRoad(len, len, len, cv, hl); }
  function sCurve(len, cv) { addRoad(len, len, len, -cv, rng.range(-10, 10)); addRoad(len, len, len, cv, rng.range(-10, 10)); }
  function hills(len, h) {
    addRoad(len, len, len, 0, h); addRoad(len, len, len, 0, -h);
  }
  function bumps(n) {
    for (let i = 0; i < n; i++) addRoad(6, 6, 6, 0, rng.range(-6, 6));
  }

  /* Startgerade – dort steht der Zielbogen und die Startaufstellung */
  straight(L.M);

  const zoneLen = Math.floor((target - 220) / zones.length);
  while (segs.length < target - 130) {
    zoneIdx = U.clamp(Math.floor((segs.length - 60) / zoneLen), 0, zones.length - 1);
    const hard = diff;
    const kind = rng.weighted([
      { w: 3.2 - hard * 2.0, v: 'straight' },
      { w: 2.6, v: 'easy' },
      { w: 1.6 + hard * 1.4, v: 'med' },
      { w: 0.4 + hard * 2.4, v: 'hard' },
      { w: 1.0 + hard * 1.0, v: 's' },
      { w: 1.5, v: 'hill' },
      { w: 0.9 + hard * 1.1, v: 'curveHill' },
      { w: 0.5 + hard * 0.8, v: 'bumps' }
    ]);
    const len = rng.pick([L.S, L.M, L.M, L.L]);
    const dir = rng.sign();
    switch (kind) {
      case 'straight':  straight(len); break;
      case 'easy':      curveP(len, dir * C.E, 0); break;
      case 'med':       curveP(len, dir * C.M, rng.range(-12, 12)); break;
      case 'hard':      curveP(rng.pick([L.S, L.M]), dir * (diff > 0.7 ? C.X : C.H), rng.range(-14, 14)); break;
      case 's':         sCurve(L.S, dir * (C.E + hard * 3)); break;
      case 'hill':      hills(len, rng.pick([HILL.L, HILL.M, HILL.H])); break;
      case 'curveHill': curveP(len, dir * (C.E + hard * 3), rng.pick([HILL.L, HILL.M])); break;
      case 'bumps':     bumps(rng.int(3, 6)); break;
    }
  }

  /* Auslauf: Höhe sauber auf 0 zurück, damit der Rundenschluss nahtlos ist */
  zoneIdx = zones.length - 1;
  const back = -lastY() / SEG.LEN;
  addRoad(40, 40, 40, 0, back);
  straight(L.S);
  /* Segmentzahl auf ein Vielfaches der Randstreifen bringen */
  while (segs.length % (SEG.RUMBLE * 2) !== 0) addSegment(0, lastY());

  const total = segs.length;
  const trackLength = total * SEG.LEN;

  /* ------------------------------------------------ Bewuchs & Schilder */
  const startClear = 30;
  for (let n = startClear; n < total - 10; n++) {
    const s = segs[n], b = s.b, p = pool(b);
    /* Rand-Objekte */
    if (n % 2 === 0 && rng.chance(b.density * 0.55)) {
      const kind = rng.pick(b.kinds.filter(k => k !== 'sign'));
      const arr = p[kind === 'billboard' ? 'billboard' : kind];
      if (arr && arr.length) {
        const side = rng.chance(0.5) ? -1 : 1;
        const far = kind === 'building' ? rng.range(2.6, 5.5) : rng.range(1.15, 3.4);
        s.sprites.push({ s: rng.pick(arr), offset: side * far });
      }
    }
    /* dichte Waldkante */
    if (b.id === 'wald' && n % 4 === 0) {
      for (const side of [-1, 1]) {
        if (rng.chance(0.8)) s.sprites.push({ s: rng.pick(p.pine || p.leaf), offset: side * rng.range(1.1, 2.2) });
      }
    }
    /* Strassenlaternen in der Stadt, regelmässig */
    if (b.id === 'stadt' && n % 14 === 0 && p.lamp) {
      s.sprites.push({ s: p.lamp[0], offset: (n % 28 === 0 ? -1 : 1) * 1.25 });
    }
    /* Warnschild vor scharfen Kurven */
    const ahead = segs[Math.min(total - 1, n + 16)];
    if (Math.abs(ahead.curve) > 4.4 && Math.abs(s.curve) < 1.2 && n % 6 === 0 && rng.chance(0.55)) {
      const d = ahead.curve > 0 ? 1 : -1;
      s.sprites.push({ s: (d > 0 ? p.signR : p.signL)[0], offset: -d * 1.35 });
    }
  }
  /* Zielbogen */
  const gantry = Art.gantry('ZIEL');
  segs[Math.max(0, total - 24)].sprites.push({ s: gantry, offset: 0, center: true, arch: true });

  /* ------------------------------------------------ Minikarte */
  const mini = (function () {
    let head = 0, x = 0, y = 0;
    const pts = [];
    for (let i = 0; i < total; i++) {
      head += segs[i].curve * 0.0062;
      x += Math.sin(head); y += Math.cos(head);
      if (i % 6 === 0) pts.push([x, y, i]);
    }
    let minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
    for (const p of pts) { if (p[0] < minX) minX = p[0]; if (p[0] > maxX) maxX = p[0]; if (p[1] < minY) minY = p[1]; if (p[1] > maxY) maxY = p[1]; }
    const w = Math.max(1e-6, maxX - minX), h = Math.max(1e-6, maxY - minY), sc = 1 / Math.max(w, h);
    return pts.map(p => [(p[0] - minX) * sc + (1 - w * sc) / 2, (p[1] - minY) * sc + (1 - h * sc) / 2, p[2]]);
  })();

  /* ------------------------------------------------ Wetter & Tageszeit */
  const lead = zones[0];
  const weather = rng.chance(lead.rain) ? (lead.snowfall ? 'snow' : 'rain') : 'clear';

  return {
    seed: seedStr,
    segments: segs,
    total: total,
    trackLength: trackLength,
    zones: zones,
    mini: mini,
    weather: weather,
    name: cfg.name || (zones.map(z => z.name).join(' · ')),
    findSegment(z) { return segs[Math.floor(z / SEG.LEN) % total]; }
  };
};

})(window.HR);
