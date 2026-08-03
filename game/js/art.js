/* ============================================================
   Horizon Rush – Grafik-Fabrik
   Alle Sprites werden zur Laufzeit gezeichnet (keine Bilddateien),
   damit die App komplett offline und in wenigen KB läuft.
   ============================================================ */
(function (HR) {
'use strict';
const U = HR.U;

function cvs(w, h) {
  const c = document.createElement('canvas');
  c.width = Math.max(1, Math.round(w)); c.height = Math.max(1, Math.round(h));
  return c;
}
function rr(g, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  g.beginPath();
  g.moveTo(x + r, y);
  g.arcTo(x + w, y, x + w, y + h, r);
  g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r);
  g.arcTo(x, y, x + w, y, r);
  g.closePath();
}
/* Sprite-Hülle: worldW = Breite in Vielfachen der Strassenbreite */
function sprite(c, worldW) { return { c: c, w: c.width, h: c.height, worldW: worldW }; }

const Art = HR.Art = {};

/* ---------------------------------------------------------- Fahrzeug (Heckansicht) */
const BODIES = {
  hatch:  { baseW: .84, roofW: .56, glassY: .40, spoiler: 0, tail: .10, height: .62 },
  coupe:  { baseW: .88, roofW: .52, glassY: .44, spoiler: 1, tail: .09, height: .58 },
  rally:  { baseW: .86, roofW: .58, glassY: .38, spoiler: 2, tail: .11, height: .64 },
  muscle: { baseW: .92, roofW: .54, glassY: .47, spoiler: 1, tail: .08, height: .54 },
  super:  { baseW: .94, roofW: .46, glassY: .52, spoiler: 2, tail: .07, height: .48 },
  proto:  { baseW: .96, roofW: .40, glassY: .56, spoiler: 3, tail: .06, height: .44 },
  van:    { baseW: .80, roofW: .74, glassY: .26, spoiler: 0, tail: .10, height: .80 },
  truck:  { baseW: .94, roofW: .90, glassY: .18, spoiler: 0, tail: .09, height: .92 }
};

/* col = Lackfarbe, lean = -1|0|1 (Lenkeinschlag), brake = Bremslicht an */
Art.car = function (col, body, lean, brake) {
  const B = BODIES[body] || BODIES.coupe;
  const W = 240, H = 140;
  const c = cvs(W, H), g = c.getContext('2d');
  const cx = W / 2;
  const ground = H * 0.94;
  const bw = W * B.baseW;                       /* Karosseriebreite unten */
  const topY = ground - H * B.height;           /* Dachhöhe */
  const roofW = W * B.roofW;
  const shift = (lean || 0) * W * 0.030;        /* oberer Teil kippt beim Lenken */
  const sq = 1 - Math.abs(lean || 0) * 0.05;

  /* Schatten */
  g.save();
  g.globalAlpha = 0.42; g.fillStyle = '#000';
  g.beginPath(); g.ellipse(cx, ground + 3, bw * 0.56, H * 0.055, 0, 0, Math.PI * 2); g.fill();
  g.restore();

  /* Räder */
  const wheelH = H * 0.20, wheelW = W * 0.115;
  g.fillStyle = '#15161c';
  rr(g, cx - bw / 2 * sq - wheelW * 0.30, ground - wheelH, wheelW, wheelH, 4); g.fill();
  rr(g, cx + bw / 2 * sq - wheelW * 0.70, ground - wheelH, wheelW, wheelH, 4); g.fill();
  g.fillStyle = '#31343f';
  g.fillRect(cx - bw / 2 * sq - wheelW * 0.30, ground - wheelH * 0.55, wheelW, 3);
  g.fillRect(cx + bw / 2 * sq - wheelW * 0.70, ground - wheelH * 0.55, wheelW, 3);

  /* Karosserie: unten breit, oben schmales Dach, mit Schulterkante */
  const shoulderY = ground - H * (B.glassY + 0.02);
  const grad = g.createLinearGradient(0, topY, 0, ground);
  grad.addColorStop(0, U.shade(col, 0.34));
  grad.addColorStop(0.42, col);
  grad.addColorStop(1, U.shade(col, -0.45));
  g.fillStyle = grad;
  g.beginPath();
  g.moveTo(cx - bw / 2 * sq, ground);
  g.lineTo(cx - bw / 2 * sq, shoulderY);
  g.quadraticCurveTo(cx - bw / 2 * sq * 0.94, topY + H * 0.02, cx - roofW / 2 * sq + shift, topY);
  g.lineTo(cx + roofW / 2 * sq + shift, topY);
  g.quadraticCurveTo(cx + bw / 2 * sq * 0.94, topY + H * 0.02, cx + bw / 2 * sq, shoulderY);
  g.lineTo(cx + bw / 2 * sq, ground);
  g.closePath(); g.fill();
  /* Schulterkante als heller Strich */
  g.strokeStyle = U.rgba('#ffffff', 0.13); g.lineWidth = 2;
  g.beginPath(); g.moveTo(cx - bw / 2 * sq + 2, shoulderY); g.lineTo(cx + bw / 2 * sq - 2, shoulderY); g.stroke();

  /* Heckscheibe */
  const gy = ground - H * B.glassY;
  const gg = g.createLinearGradient(0, topY, 0, gy);
  gg.addColorStop(0, 'rgba(150,190,230,.30)'); gg.addColorStop(1, 'rgba(8,12,24,.92)');
  g.fillStyle = gg;
  g.beginPath();
  g.moveTo(cx - roofW / 2 * sq * 0.92 + shift, topY + H * 0.022);
  g.lineTo(cx + roofW / 2 * sq * 0.92 + shift, topY + H * 0.022);
  g.lineTo(cx + roofW / 2 * sq * 1.16 + shift * 0.5, gy);
  g.lineTo(cx - roofW / 2 * sq * 1.16 + shift * 0.5, gy);
  g.closePath(); g.fill();

  /* Glanzkante oben */
  g.strokeStyle = 'rgba(255,255,255,.42)'; g.lineWidth = 2;
  g.beginPath(); g.moveTo(cx - roofW / 2 * sq + shift, topY + 1); g.lineTo(cx + roofW / 2 * sq + shift, topY + 1); g.stroke();

  /* Rückleuchten */
  const ty = ground - H * 0.30, th = H * B.tail * 1.6, tw = bw * 0.24;
  const lit = brake ? '#ff2a2a' : '#c31228';
  g.save();
  g.shadowColor = brake ? 'rgba(255,40,40,.95)' : 'rgba(220,20,50,.55)';
  g.shadowBlur = brake ? 22 : 9;
  g.fillStyle = lit;
  rr(g, cx - bw / 2 + bw * 0.06, ty, tw, th, 3); g.fill();
  rr(g, cx + bw / 2 - bw * 0.06 - tw, ty, tw, th, 3); g.fill();
  g.restore();
  if (B.spoiler >= 2) {   /* durchgehendes Leuchtband bei Sportwagen */
    g.save(); g.globalAlpha = brake ? 0.95 : 0.5; g.fillStyle = lit;
    g.shadowColor = 'rgba(255,40,60,.8)'; g.shadowBlur = brake ? 16 : 6;
    g.fillRect(cx - bw / 2 + bw * 0.06, ty + th * 0.3, bw * 0.88, th * 0.26);
    g.restore();
  }

  /* Stossfänger / Diffusor */
  g.fillStyle = 'rgba(12,14,20,.88)';
  rr(g, cx - bw / 2 * 0.96, ground - H * 0.115, bw * 0.96, H * 0.10, 4); g.fill();
  g.fillStyle = 'rgba(200,210,225,.55)';
  rr(g, cx - bw * 0.10, ground - H * 0.095, bw * 0.20, H * 0.045, 2); g.fill();   /* Kennzeichen */
  /* Auspuff */
  g.fillStyle = '#4b5060';
  g.fillRect(cx - bw * 0.30, ground - H * 0.055, bw * 0.07, H * 0.022);
  g.fillRect(cx + bw * 0.23, ground - H * 0.055, bw * 0.07, H * 0.022);

  /* Heckflügel */
  if (B.spoiler > 0) {
    const swY = topY - H * (B.spoiler === 3 ? 0.10 : B.spoiler === 2 ? 0.07 : 0.035);
    const sw = bw * (B.spoiler === 3 ? 1.02 : 0.92);
    g.fillStyle = U.shade(col, -0.55);
    g.fillRect(cx - bw * 0.30 + shift, swY, W * 0.022, topY - swY + 4);
    g.fillRect(cx + bw * 0.28 + shift, swY, W * 0.022, topY - swY + 4);
    const sg = g.createLinearGradient(0, swY, 0, swY + H * 0.045);
    sg.addColorStop(0, U.shade(col, 0.18)); sg.addColorStop(1, U.shade(col, -0.42));
    g.fillStyle = sg;
    rr(g, cx - sw / 2 + shift, swY, sw, H * 0.042, 3); g.fill();
  }
  return sprite(c, body === 'truck' ? 0.36 : body === 'van' ? 0.31 : 0.285);
};

/* ---------------------------------------------------------- Bäume & Landschaft */
Art.pine = function (rng, dark, snow) {
  const W = 150, H = 300;
  const c = cvs(W, H), g = c.getContext('2d');
  const cx = W / 2;
  g.fillStyle = '#3b2a1c';
  g.fillRect(cx - 8, H * 0.80, 16, H * 0.20);
  const layers = 5;
  for (let i = 0; i < layers; i++) {
    const t = i / (layers - 1);
    const y = U.lerp(H * 0.86, H * 0.06, t);
    const w = U.lerp(W * 0.48, W * 0.13, t);
    const hh = H * 0.20;
    g.fillStyle = U.mix(dark ? '#123322' : '#1f6b34', dark ? '#0a1f14' : '#2f8f45', t * 0.7 + rng.range(-0.1, 0.1));
    g.beginPath(); g.moveTo(cx, y - hh); g.lineTo(cx + w, y + 6); g.lineTo(cx - w, y + 6); g.closePath(); g.fill();
    if (snow) {
      g.fillStyle = 'rgba(238,246,255,.85)';
      g.beginPath(); g.moveTo(cx, y - hh); g.lineTo(cx + w * 0.55, y - hh * 0.20); g.lineTo(cx - w * 0.55, y - hh * 0.20); g.closePath(); g.fill();
    }
  }
  return sprite(c, rng.range(0.55, 0.85));
};

Art.broadleaf = function (rng, tint) {
  const W = 190, H = 240;
  const c = cvs(W, H), g = c.getContext('2d');
  const cx = W / 2;
  g.strokeStyle = '#4a3520'; g.lineWidth = 13; g.lineCap = 'round';
  g.beginPath(); g.moveTo(cx, H); g.lineTo(cx + rng.range(-8, 8), H * 0.56); g.stroke();
  const base = tint || '#2f7a3a';
  for (let i = 0; i < 9; i++) {
    const a = rng.range(0, Math.PI * 2), r = rng.range(0, W * 0.24);
    const x = cx + Math.cos(a) * r, y = H * 0.36 + Math.sin(a) * r * 0.72;
    const rad = rng.range(W * 0.16, W * 0.27);
    g.fillStyle = U.mix(base, '#8fd070', rng.range(0, 0.45));
    g.beginPath(); g.ellipse(x, y, rad, rad * 0.86, 0, 0, Math.PI * 2); g.fill();
  }
  g.globalAlpha = 0.25; g.fillStyle = '#04120a';
  g.beginPath(); g.ellipse(cx, H * 0.46, W * 0.34, H * 0.16, 0, 0, Math.PI * 2); g.fill();
  return sprite(c, rng.range(0.6, 0.95));
};

Art.palm = function (rng) {
  const W = 200, H = 300;
  const c = cvs(W, H), g = c.getContext('2d');
  const cx = W / 2, bend = rng.range(-26, 26);
  g.strokeStyle = '#6a5233'; g.lineWidth = 12; g.lineCap = 'round';
  g.beginPath(); g.moveTo(cx, H); g.quadraticCurveTo(cx + bend * 0.6, H * 0.55, cx + bend, H * 0.24); g.stroke();
  g.strokeStyle = '#7d6440'; g.lineWidth = 3;
  for (let i = 0; i < 7; i++) { const t = i / 7; const y = U.lerp(H, H * 0.28, t); g.beginPath(); g.moveTo(cx + bend * (1 - Math.pow(1 - t, 2)) - 7, y); g.lineTo(cx + bend * (1 - Math.pow(1 - t, 2)) + 7, y); g.stroke(); }
  const tx = cx + bend, ty = H * 0.24;
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * Math.PI * 2 + rng.range(-0.2, 0.2);
    const len = rng.range(W * 0.28, W * 0.44);
    g.strokeStyle = U.mix('#1d7a48', '#4fd07a', rng.range(0, 0.6));
    g.lineWidth = rng.range(7, 13);
    g.beginPath(); g.moveTo(tx, ty);
    g.quadraticCurveTo(tx + Math.cos(a) * len * 0.6, ty + Math.sin(a) * len * 0.35 - 20,
                       tx + Math.cos(a) * len, ty + Math.sin(a) * len * 0.55 + 12);
    g.stroke();
  }
  g.fillStyle = '#6b4a22';
  g.beginPath(); g.arc(tx, ty + 6, 9, 0, Math.PI * 2); g.fill();
  return sprite(c, rng.range(0.7, 1.0));
};

Art.cactus = function (rng) {
  const W = 130, H = 220;
  const c = cvs(W, H), g = c.getContext('2d');
  const cx = W / 2;
  const col = U.mix('#2f7a4a', '#5aa05e', rng.range(0, 0.5));
  g.fillStyle = col;
  rr(g, cx - 17, H * 0.20, 34, H * 0.80, 17); g.fill();
  if (rng.chance(0.75)) { rr(g, cx - 52, H * 0.42, 26, H * 0.30, 13); g.fill(); rr(g, cx - 52, H * 0.42, 60, 24, 12); g.fill(); }
  if (rng.chance(0.6)) { rr(g, cx + 27, H * 0.34, 25, H * 0.34, 12); g.fill(); rr(g, cx - 6, H * 0.56, 58, 23, 11); g.fill(); }
  g.strokeStyle = 'rgba(0,0,0,.20)'; g.lineWidth = 2;
  for (let i = 0; i < 3; i++) { g.beginPath(); g.moveTo(cx - 10 + i * 10, H * 0.24); g.lineTo(cx - 10 + i * 10, H * 0.97); g.stroke(); }
  return sprite(c, rng.range(0.32, 0.5));
};

Art.rock = function (rng, col) {
  const W = 180, H = 130;
  const c = cvs(W, H), g = c.getContext('2d');
  const base = col || '#6d6a63';
  g.fillStyle = base;
  g.beginPath();
  const n = 8; g.moveTo(6, H);
  for (let i = 0; i <= n; i++) {
    const t = i / n;
    g.lineTo(6 + t * (W - 12), H - Math.sin(t * Math.PI) * H * rng.range(0.62, 0.98) * (0.7 + 0.3 * Math.sin(t * 9)));
  }
  g.lineTo(W - 6, H); g.closePath(); g.fill();
  g.fillStyle = U.shade(base, 0.22);
  g.beginPath(); g.moveTo(W * 0.2, H); g.lineTo(W * 0.42, H * 0.22); g.lineTo(W * 0.55, H); g.closePath(); g.fill();
  return sprite(c, rng.range(0.35, 0.62));
};

Art.bush = function (rng, col) {
  const W = 120, H = 80;
  const c = cvs(W, H), g = c.getContext('2d');
  for (let i = 0; i < 5; i++) {
    g.fillStyle = U.mix(col || '#2c6b34', '#79b45c', rng.range(0, 0.5));
    g.beginPath(); g.ellipse(rng.range(20, W - 20), rng.range(H * 0.45, H * 0.85), rng.range(18, 30), rng.range(14, 22), 0, 0, Math.PI * 2); g.fill();
  }
  return sprite(c, rng.range(0.22, 0.36));
};

/* Werbetafel im Festival-Look */
const BILLBOARDS = [
  { t: 'HORIZON', s: 'RUSH', c: '#ff6a1f' }, { t: 'NOS', s: 'BOOST', c: '#22e6c8' },
  { t: 'FESTIVAL', s: 'ROUTE', c: '#7b5cff' }, { t: 'VOLLGAS', s: '', c: '#ffcc33' },
  { t: 'DRIFT', s: 'ZONE', c: '#ff2e63' }, { t: 'ETAPPE', s: 'START', c: '#39d98a' }
];
Art.billboard = function (rng) {
  const b = rng.pick(BILLBOARDS);
  const W = 260, H = 220;
  const c = cvs(W, H), g = c.getContext('2d');
  g.fillStyle = '#3a3f52'; g.fillRect(W * 0.44, H * 0.52, 12, H * 0.48); g.fillRect(W * 0.53, H * 0.52, 12, H * 0.48);
  const bg = g.createLinearGradient(0, 0, 0, H * 0.58);
  bg.addColorStop(0, '#131726'); bg.addColorStop(1, '#0b0e18');
  g.fillStyle = bg; rr(g, 4, 6, W - 8, H * 0.55, 10); g.fill();
  g.strokeStyle = b.c; g.lineWidth = 5; rr(g, 4, 6, W - 8, H * 0.55, 10); g.stroke();
  g.fillStyle = b.c; g.textAlign = 'center';
  g.font = '900 48px -apple-system,system-ui,sans-serif';
  g.fillText(b.t, W / 2, H * 0.30);
  if (b.s) { g.fillStyle = '#fff'; g.font = '900 30px -apple-system,system-ui,sans-serif'; g.fillText(b.s, W / 2, H * 0.46); }
  return sprite(c, 0.62);
};

/* Warnschild für Kurven */
Art.sign = function (dir) {
  const W = 120, H = 190;
  const c = cvs(W, H), g = c.getContext('2d');
  g.fillStyle = '#5b6070'; g.fillRect(W / 2 - 5, H * 0.5, 10, H * 0.5);
  g.fillStyle = '#0d1020'; rr(g, 8, 8, W - 16, H * 0.5, 8); g.fill();
  g.strokeStyle = '#ffcc33'; g.lineWidth = 5; rr(g, 8, 8, W - 16, H * 0.5, 8); g.stroke();
  g.fillStyle = '#ffcc33';
  for (let i = 0; i < 2; i++) {
    const x = W / 2 + (i - 0.5) * 30 * (dir < 0 ? -1 : 1);
    g.beginPath();
    if (dir < 0) { g.moveTo(x + 14, H * 0.16); g.lineTo(x - 8, H * 0.33); g.lineTo(x + 14, H * 0.50); g.lineTo(x + 20, H * 0.42); g.lineTo(x + 4, H * 0.33); g.lineTo(x + 20, H * 0.24); }
    else { g.moveTo(x - 14, H * 0.16); g.lineTo(x + 8, H * 0.33); g.lineTo(x - 14, H * 0.50); g.lineTo(x - 20, H * 0.42); g.lineTo(x - 4, H * 0.33); g.lineTo(x - 20, H * 0.24); }
    g.closePath(); g.fill();
  }
  return sprite(c, 0.30);
};

/* Hochhaus mit beleuchteten Fenstern */
Art.building = function (rng, night) {
  const W = 220, H = rng.int(240, 460);
  const c = cvs(W, H), g = c.getContext('2d');
  const bw = rng.range(W * 0.55, W * 0.92), x0 = (W - bw) / 2;
  const bg = g.createLinearGradient(x0, 0, x0 + bw, 0);
  bg.addColorStop(0, night ? '#141a2e' : '#59617e');
  bg.addColorStop(0.5, night ? '#1c2440' : '#767f9c');
  bg.addColorStop(1, night ? '#0e1322' : '#454c66');
  g.fillStyle = bg; g.fillRect(x0, H * 0.05, bw, H * 0.95);
  const cols = Math.max(3, Math.floor(bw / 26)), rows = Math.max(4, Math.floor(H * 0.9 / 30));
  for (let i = 0; i < cols; i++) for (let j = 0; j < rows; j++) {
    const lit = rng.chance(night ? 0.42 : 0.10);
    g.fillStyle = lit ? U.mix('#ffd98a', '#9fe4ff', rng.next()) : (night ? 'rgba(255,255,255,.05)' : 'rgba(20,26,44,.35)');
    g.fillRect(x0 + 10 + i * (bw - 20) / cols, H * 0.10 + j * (H * 0.86) / rows, (bw - 20) / cols - 7, (H * 0.86) / rows - 10);
  }
  if (night && rng.chance(0.5)) { g.fillStyle = '#ff3b5c'; g.beginPath(); g.arc(W / 2, H * 0.045, 4, 0, Math.PI * 2); g.fill(); }
  return sprite(c, rng.range(1.0, 1.8));
};

Art.lamp = function (night) {
  const W = 120, H = 320;
  const c = cvs(W, H), g = c.getContext('2d');
  g.strokeStyle = '#4a5064'; g.lineWidth = 8; g.lineCap = 'round';
  g.beginPath(); g.moveTo(W * 0.78, H); g.lineTo(W * 0.78, H * 0.12);
  g.quadraticCurveTo(W * 0.78, H * 0.04, W * 0.40, H * 0.05); g.stroke();
  if (night) {
    g.save(); g.globalAlpha = 0.55;
    const gr = g.createRadialGradient(W * 0.40, H * 0.08, 2, W * 0.40, H * 0.08, 70);
    gr.addColorStop(0, 'rgba(255,220,150,.95)'); gr.addColorStop(1, 'rgba(255,200,120,0)');
    g.fillStyle = gr; g.beginPath(); g.arc(W * 0.40, H * 0.08, 70, 0, Math.PI * 2); g.fill(); g.restore();
  }
  g.fillStyle = night ? '#ffe6b0' : '#8d93a6';
  rr(g, W * 0.30, H * 0.04, 30, 9, 4); g.fill();
  return sprite(c, 0.34);
};

/* Start-/Zielbogen über der Fahrbahn (je Beschriftung nur einmal zeichnen) */
const gantryCache = {};
Art.gantry = function (label) {
  label = label || 'ZIEL';
  if (gantryCache[label]) return gantryCache[label];
  const W = 900, H = 330;
  const c = cvs(W, H), g = c.getContext('2d');
  g.fillStyle = '#1b2033';
  g.fillRect(20, H * 0.28, 46, H * 0.72); g.fillRect(W - 66, H * 0.28, 46, H * 0.72);
  const bg = g.createLinearGradient(0, 0, W, 0);
  bg.addColorStop(0, '#ff6a1f'); bg.addColorStop(0.5, '#7b5cff'); bg.addColorStop(1, '#22e6c8');
  g.fillStyle = bg; rr(g, 10, H * 0.06, W - 20, H * 0.24, 10); g.fill();
  g.fillStyle = 'rgba(0,0,0,.35)'; rr(g, 22, H * 0.09, W - 44, H * 0.18, 7); g.fill();
  g.fillStyle = '#fff'; g.textAlign = 'center';
  g.font = '900 ' + (label.length > 6 ? 78 : 96) + 'px -apple-system,system-ui,sans-serif';
  g.fillText(label, W / 2, H * 0.245);
  /* Zielflagge */
  const s = 22;
  for (let x = 0; x < W; x += s) for (let y = 0; y < 2; y++) {
    g.fillStyle = ((x / s + y) % 2) ? '#fff' : '#111';
    g.fillRect(x, H * 0.30 + y * s * 0.6, s, s * 0.6);
  }
  return (gantryCache[label] = sprite(c, 2.9));
};

/* ---------------------------------------------------------- Hintergrund */
/* nahtlos kachelbare Bergkette (Endpunkte identisch) */
Art.mountains = function (seed, w, h, colTop, colBot, snow, rough) {
  const rng = HR.RNG(seed);
  const c = cvs(w, h), g = c.getContext('2d');
  const n = 128; const pts = new Array(n + 1);
  pts[0] = pts[n] = h * 0.62;
  (function disp(a, b, d) {
    if (b - a < 2) return;
    const m = (a + b) >> 1;
    pts[m] = (pts[a] + pts[b]) / 2 + rng.range(-d, d);
    disp(a, m, d * rough); disp(m, b, d * rough);
  })(0, n, h * 0.36);
  const gr = g.createLinearGradient(0, 0, 0, h);
  gr.addColorStop(0, colTop); gr.addColorStop(1, colBot);
  g.fillStyle = gr;
  g.beginPath(); g.moveTo(0, h);
  for (let i = 0; i <= n; i++) g.lineTo(i * w / n, U.clamp(pts[i], h * 0.05, h * 0.95));
  g.lineTo(w, h); g.closePath(); g.fill();
  if (snow) {
    g.fillStyle = 'rgba(240,248,255,.85)';
    for (let i = 0; i < n; i++) {
      const y = U.clamp(pts[i], h * 0.05, h * 0.95);
      if (y < h * 0.36) {
        g.beginPath(); g.moveTo(i * w / n, y);
        g.lineTo((i + 1) * w / n, U.clamp(pts[i + 1], h * 0.05, h * 0.95));
        g.lineTo((i + 1) * w / n, y + h * 0.10); g.lineTo(i * w / n, y + h * 0.10);
        g.closePath(); g.fill();
      }
    }
  }
  return c;
};

Art.clouds = function (seed, w, h, col, alpha) {
  const rng = HR.RNG(seed);
  const c = cvs(w, h), g = c.getContext('2d');
  g.globalAlpha = alpha == null ? 0.6 : alpha;
  for (let i = 0; i < 16; i++) {
    const x = rng.range(w * 0.06, w * 0.94), y = rng.range(h * 0.15, h * 0.8), r = rng.range(h * 0.16, h * 0.42);
    const gr = g.createRadialGradient(x, y, 1, x, y, r);
    gr.addColorStop(0, col); gr.addColorStop(1, U.rgba('#ffffff', 0));
    g.fillStyle = gr;
    g.beginPath(); g.ellipse(x, y, r * 1.8, r * 0.62, 0, 0, Math.PI * 2); g.fill();
  }
  return c;
};

})(window.HR);
