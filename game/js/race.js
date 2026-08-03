/* ============================================================
   Horizon Rush – Rennen
   Pseudo-3D-Renderer (projizierte Strassensegmente), Fahrphysik,
   Gegner-KI mit adaptiver Stärke, Steuerung und HUD.
   ============================================================ */
(function (HR) {
'use strict';
const U = HR.U, SEG = HR.SEG, Art = HR.Art;

/* Kamera */
const FOV = 100;
const CAM_H = 1150;
const CAM_DEPTH = 1 / Math.tan((FOV / 2) * Math.PI / 180);
const PLAYER_Z = CAM_H * CAM_DEPTH;
const FOG_DENSITY = 5;
const STEP = 1 / 60;
/* Umrechnung: Tempo in km/h * 40 = Welteinheiten pro Sekunde.
   Also entspricht 1 km genau 40 * 3600 = 144000 Welteinheiten. */
const UNITS_PER_KM = 144000;
HR.UNITS_PER_KM = UNITS_PER_KM;

/* Senkrechte Bildmitte (Horizont) als Anteil der Bildhöhe.
   Im Hochformat wird sie nach oben gezogen, sonst steht das eigene Auto
   hinter den Bedienflächen. */
let CY = 0.5;

const RIVAL_NAMES = ['Nova', 'Kruse', 'Bianchi', 'Okonkwo', 'Sato', 'Vasquez', 'Lindqvist', 'Moreau',
  'Duarte', 'Ferrari', 'Hoffmann', 'Petrov', 'Rossi', 'Abara', 'Keller', 'Nakamura', 'Silva', 'Wolf'];
const RIVAL_COLS = ['#2f9dff', '#39d98a', '#ffcc33', '#ff3b5c', '#7b5cff', '#22e6c8', '#ff8a1f', '#e9edf5', '#ff2e63', '#9bff5c'];
const TRAFFIC_COLS = ['#8b93a8', '#c7ccd8', '#5c6478', '#7a8296', '#a3aab8', '#4a5164'];

const Race = HR.Race = {
  state: 'idle',
  cfg: null, track: null, result: null,
  onFinish: null,

  /* ---------------------------------------------------------- Aufbau */
  init(canvas, mini) {
    this.cv = canvas; this.g = canvas.getContext('2d', { alpha: false });
    this.miniCv = mini; this.miniG = mini ? mini.getContext('2d') : null;
    this.drawDist = 190;
    this.frameAvg = 16;
    this.dom = {
      hud: document.getElementById('hud'),
      controls: document.getElementById('controls'),
      kmh: document.getElementById('kmh'),
      rev: document.getElementById('revFill'),
      nos: document.getElementById('nosFill'),
      chipPos: document.getElementById('chipPos'),
      chipLap: document.getElementById('chipLap'),
      chipTime: document.getElementById('chipTime'),
      chipExtra: document.getElementById('chipExtra'),
      big: document.getElementById('bigMsg'),
      pops: document.getElementById('popups'),
      steerPad: document.getElementById('steerPad'),
      knob: document.getElementById('steerKnob'),
      btnNos: document.getElementById('btnNos'),
      btnBrake: document.getElementById('btnBrake'),
      btnGas: document.getElementById('btnGas')
    };
    this.resize();
    window.addEventListener('resize', () => this.resize());
    window.addEventListener('orientationchange', () => setTimeout(() => this.resize(), 250));
    this.bindInput();
  },

  resize() {
    const q = HR.Save.data.settings.quality;
    let maxDpr = q === 'high' ? 2 : q === 'low' ? 1 : 1.5;
    if (q === 'auto') maxDpr = 1.5;
    const dpr = Math.min(window.devicePixelRatio || 1, maxDpr);
    const w = window.innerWidth, h = window.innerHeight;
    this.W = Math.round(w * dpr); this.H = Math.round(h * dpr);
    this.cv.width = this.W; this.cv.height = this.H;
    this.cv.style.width = w + 'px'; this.cv.style.height = h + 'px';
    this.res = this.H / 480;
    CY = U.clamp(0.5 - (this.H / this.W - 0.8) * 0.16, 0.30, 0.5);
  },

  /* ---------------------------------------------------------- Steuerung */
  bindInput() {
    const I = this.input = { steer: 0, gas: false, brake: false, nos: false, tilt: 0, useTilt: false };
    const d = this.dom;

    /* Lenkfläche: analog, Position des Fingers = Lenkeinschlag */
    let padId = null;
    const padSet = (e) => {
      const r = d.steerPad.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width;          /* 0…1 */
      I.steer = U.clamp((x - 0.5) * 2.35, -1, 1);
      d.knob.style.transform = 'translateX(' + (I.steer * r.width * 0.34) + 'px)';
    };
    d.steerPad.addEventListener('pointerdown', e => { padId = e.pointerId; d.steerPad.setPointerCapture(e.pointerId); padSet(e); e.preventDefault(); });
    d.steerPad.addEventListener('pointermove', e => { if (e.pointerId === padId) { padSet(e); e.preventDefault(); } });
    const padEnd = e => { if (e.pointerId === padId) { padId = null; I.steer = 0; d.knob.style.transform = 'translateX(0px)'; } };
    d.steerPad.addEventListener('pointerup', padEnd);
    d.steerPad.addEventListener('pointercancel', padEnd);

    const hold = (el, on, off) => {
      el.addEventListener('pointerdown', e => { e.preventDefault(); el.classList.add('on'); on(); el.setPointerCapture(e.pointerId); });
      const end = e => { el.classList.remove('on'); off(); };
      el.addEventListener('pointerup', end); el.addEventListener('pointercancel', end); el.addEventListener('pointerleave', end);
    };
    hold(d.btnBrake, () => I.brake = true, () => I.brake = false);
    hold(d.btnNos, () => I.nos = true, () => I.nos = false);
    hold(d.btnGas, () => I.gas = true, () => I.gas = false);

    /* Tastatur (Desktop-Test) */
    const key = (e, v) => {
      switch (e.key) {
        case 'ArrowLeft': case 'a': I.kl = v; break;
        case 'ArrowRight': case 'd': I.kr = v; break;
        case 'ArrowUp': case 'w': I.gas = v; break;
        case 'ArrowDown': case 's': I.brake = v; break;
        case ' ': I.nos = v; break;
        default: return;
      }
      e.preventDefault();
    };
    window.addEventListener('keydown', e => key(e, true));
    window.addEventListener('keyup', e => key(e, false));

    /* Neigungssteuerung */
    window.addEventListener('deviceorientation', e => {
      if (!I.useTilt) return;
      const port = Math.abs(window.orientation || 0) !== 90;
      let g = port ? (e.gamma || 0) : -(e.beta || 0) * (window.orientation === -90 ? -1 : 1);
      I.tilt = U.clamp(g / 26, -1, 1);
    });
  },
  enableTilt(on) {
    this.input.useTilt = on;
    if (on && window.DeviceOrientationEvent && typeof DeviceOrientationEvent.requestPermission === 'function') {
      DeviceOrientationEvent.requestPermission().catch(() => { });
    }
  },

  /* ---------------------------------------------------------- Rennen starten */
  start(cfg) {
    this.cfg = cfg;
    const S = HR.Save.data;
    const stats = HR.effStats(cfg.carId || S.car);
    const car = HR.carById(cfg.carId || S.car);
    const col = (S.cars[car.id] && S.cars[car.id].col) || car.col;

    this.stats = stats;
    this.maxSpeed = stats.top * 40;                       /* Welteinheiten pro Sekunde */
    this.player = {
      sprites: {
        s: Art.car(col, car.body, 0, false), l: Art.car(col, car.body, -1, false), r: Art.car(col, car.body, 1, false),
        sb: Art.car(col, car.body, 0, true), lb: Art.car(col, car.body, -1, true), rb: Art.car(col, car.body, 1, true)
      }
    };

    this.stage = 0;
    this.buildStage(cfg.seed);

    this.position = 0; this.playerX = 0; this.speed = 0; this.steerSmooth = 0;
    this.lap = 0; this.time = 0; this.raceTime = 0; this.comboT = 0;
    this.nos = 0.4; this.nosOn = false; this.slip = 0; this.shake = 0;
    this.combo = 0; this.score = 0; this.driftTime = 0; this.nearMiss = 0;
    this.distance = 0; this.topSpeed = 0; this.airTime = 0;
    this.timeLeft = cfg.timeLimit || 0;
    this.nextCp = 0;
    this.trapTotal = 0; this.trapCount = 0;
    this.smoke = []; this.rainDrops = null;
    this.bgOff = [0, 0, 0];
    this.pitch = 0;
    this.state = 'countdown'; this.cdT = 3.6;
    this.result = null; this.finishedRivals = 0;
    this.lastY = 0;

    this.dom.hud.hidden = false; this.dom.controls.hidden = false;
    this.dom.btnGas.hidden = !!S.settings.autoGas;
    this.dom.chipPos.hidden = !(cfg.rivalCount > 0);
    this.dom.chipLap.hidden = !(cfg.laps > 1) || cfg.mode === 'marathon';
    this.dom.chipExtra.hidden = !(cfg.mode === 'marathon' || cfg.mode === 'trap');
    this.input.useTilt = S.settings.steer === 'tilt';
    this.dom.steerPad.style.opacity = this.input.useTilt ? 0.35 : 1;

    HR.Audio.init();
    HR.Audio.setMuted(!S.settings.sound);

    this.lastT = performance.now(); this.acc = 0;
    if (!this._raf) this.loop();
  },

  /* eine Etappe (bei Marathon wird laufend nachgeneriert) */
  buildStage(seed) {
    const cfg = this.cfg;
    const diff = U.clamp((cfg.difficulty || 0.4) + this.stage * 0.05, 0, 1);
    const biomes = cfg.mode === 'marathon' && this.stage > 0 ? null : cfg.biomes;
    this.track = HR.buildTrack(seed + '#' + this.stage, {
      segments: cfg.segments || 1400,
      difficulty: diff,
      biomes: biomes,
      name: cfg.name
    });
    this.weather = this.track.weather;
    this.markTrack();
    this.spawnCars();
  },

  /* Bögen über Blitzern und Checkpoints, damit man sie kommen sieht */
  markTrack() {
    const cfg = this.cfg, T = this.track;
    const add = (z, label) => {
      const s = T.findSegment(U.increase(0, z, T.trackLength));
      s.sprites.push({ s: Art.gantry(label), offset: 0, center: true });
    };
    if (cfg.mode === 'trap') {
      const n = cfg.traps || 4, q = T.trackLength / n;
      for (let i = 0; i < n; i++) add((i + 0.5) * q, 'BLITZER');
    } else if (cfg.mode === 'marathon') {
      const q = T.trackLength / 4;
      for (let i = 0; i < 3; i++) add((i + 1) * q, 'CHECK');
    }
  },

  /* Gegner und Verkehr setzen */
  spawnCars() {
    const cfg = this.cfg, T = this.track, S = HR.Save.data;
    const rng = HR.RNG(T.seed + '-cars');
    this.cars = [];

    /* Adaptive Gegnerstärke: Können des Spielers + Eventschwierigkeit */
    const skill = U.clamp(S.skill / 100, 0, 1);
    const evd = cfg.difficulty == null ? 0.4 : cfg.difficulty;
    const strength = U.clamp(0.40 + skill * 0.52 + evd * 0.20, 0.35, 1.06);
    this.aiStrength = strength;

    const n = cfg.rivalCount || 0;
    const names = rng.shuffle(RIVAL_NAMES.slice()).slice(0, n);
    for (let i = 0; i < n; i++) {
      /* Startaufstellung: zwei Kolonnen vor dem Spieler, er startet zuhinterst */
      const grid = (i % 2 === 0 ? -0.42 : 0.42);
      const rowZ = 620 + Math.floor(i / 2) * 460;
      const col = RIVAL_COLS[i % RIVAL_COLS.length];
      const body = rng.pick(['coupe', 'super', 'muscle', 'rally', 'proto']);
      const s = strength * (1 + (i - n / 2) * 0.016) * rng.range(0.985, 1.015);
      this.cars.push({
        rival: true, name: names[i], col: col,
        sprite: Art.car(col, body, 0, false),
        z: rowZ,
        offset: grid,
        wanted: grid,
        speed: 0, base: this.maxSpeed * U.clamp(s, 0.3, 1.05),
        skill: U.clamp(s, 0.2, 1), lap: 0, dist: 0, mistake: 0, done: false, finishT: 0,
        percent: 0
      });
    }

    /* Verkehr */
    const tn = Math.round((cfg.traffic == null ? 0.5 : cfg.traffic) * 26);
    for (let i = 0; i < tn; i++) {
      const col = rng.pick(TRAFFIC_COLS);
      const body = rng.weighted([{ w: 4, v: 'hatch' }, { w: 3, v: 'coupe' }, { w: 2, v: 'van' }, { w: 1.2, v: 'truck' }]);
      this.cars.push({
        rival: false, col: col, sprite: Art.car(col, body, 0, false),
        z: rng.range(T.trackLength * 0.06, T.trackLength * 0.98),
        offset: rng.range(-0.78, 0.78), wanted: 0,
        speed: this.maxSpeed * rng.range(0.20, 0.42), base: 0, lap: 0, dist: 0, percent: 0,
        big: body === 'truck' || body === 'van'
      });
    }
    for (const c of this.cars) { c.seg = T.findSegment(c.z); c.seg.cars.push(c); }
  },

  stop() {
    this.state = 'idle';
    this.dom.hud.hidden = true; this.dom.controls.hidden = true;
    this.dom.big.textContent = '';
    this.dom.pops.innerHTML = '';
    HR.Audio.stopEngine();
    if (this._raf) { cancelAnimationFrame(this._raf); this._raf = null; }
  },
  pause() { if (this.state === 'racing' || this.state === 'countdown') { this.prevState = this.state; this.state = 'paused'; HR.Audio.stopEngine(); } },
  resume() { if (this.state === 'paused') { this.state = this.prevState || 'racing'; this.lastT = performance.now(); } },

  /* ---------------------------------------------------------- Hauptschleife */
  loop() {
    if (this.state === 'idle') { this._raf = null; return; }
    this._raf = requestAnimationFrame(() => this.loop());
    const now = performance.now();
    let dt = (now - this.lastT) / 1000; this.lastT = now;
    if (dt > 0.25) dt = 0.25;
    this.frameAvg = this.frameAvg * 0.94 + (dt * 1000) * 0.06;

    if (this.state === 'idle') return;
    if (this.state === 'paused') { this.render(); return; }

    this.acc += dt;
    let guard = 0;
    while (this.acc >= STEP && guard++ < 6) { this.update(STEP); this.acc -= STEP; }
    this.render();
    this.updateHud(dt);

    /* Zeichenweite an die Leistung anpassen */
    if (HR.Save.data.settings.quality === 'auto') {
      if (this.frameAvg > 21 && this.drawDist > 95) this.drawDist -= 2;
      else if (this.frameAvg < 14.5 && this.drawDist < 230) this.drawDist += 1;
    } else {
      this.drawDist = HR.Save.data.settings.quality === 'high' ? 230 : HR.Save.data.settings.quality === 'low' ? 110 : 180;
    }
  },

  /* ---------------------------------------------------------- Physik */
  update(dt) {
    const T = this.track, cfg = this.cfg, S = HR.Save.data;

    if (this.state === 'countdown') {
      this.cdT -= dt;
      const n = Math.ceil(this.cdT - 0.6);
      if (n !== this._cdShown) {
        this._cdShown = n;
        if (n > 0 && n <= 3) { this.big(String(n)); HR.Audio.countdown(false); }
      }
      if (this.cdT <= 0.6 && !this._go) { this._go = true; this.big('LOS!'); HR.Audio.countdown(true); }
      if (this.cdT <= 0) { this.state = 'racing'; this.big(''); }
      /* Motor im Leerlauf */
      HR.Audio.engine(0.18 + Math.random() * 0.04, 0.2, true, false);
      this.updateCars(dt, true);
      return;
    }
    if (this.state !== 'racing') return;

    this.time += dt; this.raceTime += dt;

    const playerSeg = T.findSegment(this.position + PLAYER_Z);
    const speedPct = this.speed / this.maxSpeed;
    const assist = S.settings.assist ? 0.78 : 1;

    /* --- Lenken --- */
    let steer = this.input.useTilt ? this.input.tilt : this.input.steer;
    if (this.input.kl) steer = -1; if (this.input.kr) steer = 1;
    this.steerSmooth = U.lerp(this.steerSmooth || 0, steer, 1 - Math.pow(0.0006, dt));
    const dx = dt * 2.1 * Math.max(0.22, speedPct);
    this.playerX += this.steerSmooth * dx * (0.72 + this.stats.grip * 0.42);

    /* Fliehkraft in Kurven */
    const centri = 0.34 * (1.30 - this.stats.grip * 0.55) * assist;
    this.playerX -= dx * speedPct * playerSeg.curve * centri;

    /* Driftwert – daraus Rauch, Ton und Stilpunkte */
    const want = Math.abs(playerSeg.curve) * speedPct * 0.115;
    const slipRaw = U.clamp(want - this.stats.grip * 0.42 + Math.abs(this.steerSmooth) * speedPct * 0.16 - 0.10, 0, 1);
    this.slip = U.lerp(this.slip, slipRaw, 0.16);

    /* --- Gas / Bremse --- */
    const autoGas = S.settings.autoGas;
    const braking = !!this.input.brake;
    const gas = autoGas ? !braking : !!this.input.gas;
    const nosReady = this.nos > 0.02 && this.input.nos;
    this.nosOn = nosReady;

    const boost = nosReady ? 1 + 0.20 * this.stats.nos : 1;
    const vmax = this.maxSpeed * boost;
    if (braking) {
      this.speed -= this.maxSpeed * 1.15 * dt;
    } else if (gas) {
      const p = U.clamp(this.speed / vmax, 0, 1);
      const a = (this.maxSpeed / (2.35 + 4.6 * (1 - this.stats.acc))) * (1 - Math.pow(p, 2.4)) * (nosReady ? 2.0 : 1);
      this.speed += a * dt;
    } else {
      this.speed -= this.maxSpeed * 0.16 * dt;
    }

    /* Nebenstrecke */
    const offroad = Math.abs(this.playerX) > 1;
    if (offroad) {
      const lim = this.maxSpeed * (S.settings.assist ? 0.44 : 0.34);
      if (this.speed > lim) this.speed -= this.maxSpeed * 1.5 * dt;
      this.shake = Math.max(this.shake, 0.35 * speedPct);
      if (Math.abs(this.playerX) > 2.4) {          /* Leitplanke */
        this.playerX = 2.4 * Math.sign(this.playerX);
        this.speed *= 0.965;
      }
    }
    this.speed = U.clamp(this.speed, 0, vmax);
    this.playerX = U.clamp(this.playerX, -2.6, 2.6);

    /* NOS-Haushalt */
    if (nosReady) {
      this.nos = Math.max(0, this.nos - dt * 0.30);
      if (!this._nosSnd) { HR.Audio.nosBurst(); this._nosSnd = true; }
    } else { this._nosSnd = false; }
    this.nos = Math.min(1, this.nos + dt * 0.014 + this.slip * dt * 0.055);
    if (this.slip > 0.32) { this.driftTime += dt; this.addScore(38 * dt * speedPct, null); }

    /* Kombo verfällt, wenn längere Zeit nichts passiert */
    this.comboT -= dt;
    if (this.comboT <= 0 && this.combo > 0) this.combo = 0;

    /* --- Vorwärts --- */
    const prevPos = this.position;
    this.position = U.increase(this.position, dt * this.speed, T.trackLength);
    this.distance += dt * this.speed;
    if (this.speed / 40 > this.topSpeed) this.topSpeed = this.speed / 40;

    /* Rundenwechsel / neue Etappe */
    if (this.position < prevPos) this.onLap();

    /* Kollisionen */
    this.checkCollisions(playerSeg, dt);

    /* Kamera-Nicken aus der Steigung */
    const slope = (playerSeg.p2.world.y - playerSeg.p1.world.y) / SEG.LEN;
    this.pitch = U.lerp(this.pitch, slope, 0.08);
    this.shake = Math.max(0, this.shake - dt * 1.6);

    this.updateCars(dt, false);
    this.updateRank();

    /* Modus-Logik */
    if (cfg.mode === 'marathon') {
      this.timeLeft -= dt;
      const q = T.trackLength / 4;
      const cpZ = (this.nextCp + 1) * q;
      if (this.position >= cpZ && this.nextCp < 3) {
        this.nextCp++;
        const add = Math.max(9, 22 - this.stage * 0.8);
        this.timeLeft += add;
        HR.Audio.checkpoint();
        this.pop('+' + Math.round(add) + ' SEK', '#22e6c8');
        this.addScore(250, 'CHECKPOINT');
      }
      if (this.timeLeft <= 0) { this.finish(false); return; }
    }
    if (cfg.mode === 'trap') {
      const n = cfg.traps || 4;
      const q = T.trackLength / n;
      if (this.trapCount < n && this.position >= (this.trapCount + 0.5) * q) {
        this.trapCount++;
        const kmh = Math.round(this.speed / 40);
        this.trapTotal += kmh;
        HR.Audio.checkpoint();
        this.pop('BLITZER ' + kmh + ' km/h', '#ffcc33');
        this.addScore(kmh * 10, null);
      }
    }

    /* Ton */
    const rpmBase = (this.speed / this.maxSpeed);
    const gear = Math.min(6, 1 + Math.floor(rpmBase * 6));
    const rpm = U.clamp((rpmBase * 6) % 1 * 0.75 + 0.25, 0, 1);
    HR.Audio.engine(rpm, gas ? 1 : 0.25, true, this.nosOn);
    HR.Audio.wind(speedPct, this.slip > 0.42);
    this.gear = gear;

    /* Rauchpartikel */
    if (this.slip > 0.30 && Math.random() < 0.6) this.spawnSmoke();
    for (let i = this.smoke.length - 1; i >= 0; i--) {
      const p = this.smoke[i];
      p.life -= dt; p.x += p.vx * dt; p.y += p.vy * dt; p.r += dt * 90;
      if (p.life <= 0) this.smoke.splice(i, 1);
    }
  },

  onLap() {
    const cfg = this.cfg;
    if (cfg.mode === 'marathon') {
      this.stage++;
      this.nextCp = 0;
      this.buildStage(cfg.seed);
      this.pop('ETAPPE ' + (this.stage + 1) + ' · ' + this.track.zones[0].name, '#ff6a1f');
      this.big('ETAPPE ' + (this.stage + 1)); setTimeout(() => this.big(''), 1400);
      return;
    }
    this.lap++;
    if (this.lap >= (cfg.laps || 1)) { this.finish(true); return; }
    HR.Audio.checkpoint();
    this.pop('RUNDE ' + (this.lap + 1) + '/' + cfg.laps, '#ffffff');
  },

  spawnSmoke() {
    const W = this.W, H = this.H;
    const sx = W / 2 + (Math.random() - 0.5) * W * 0.22;
    this.smoke.push({
      x: sx, y: H * 0.90 + Math.random() * H * 0.03, r: 8 * this.res,
      vx: (Math.random() - 0.5) * 90, vy: -20 - Math.random() * 40,
      life: 0.55 + Math.random() * 0.35, max: 0.9
    });
    if (this.smoke.length > 60) this.smoke.shift();
  },

  /* --------------------------------------------------- Gegner & Verkehr */
  updateCars(dt, frozen) {
    const T = this.track, len = T.trackLength;
    const playerDist = this.lap * len + this.position;
    const rubber = HR.Save.data.settings.rubber;

    for (const c of this.cars) {
      const seg = T.findSegment(c.z);

      if (c.rival) {
        if (frozen) { c.speed = 0; }
        else {
          /* Wunschtempo: Basistempo, in Kurven langsamer */
          const cv = Math.abs(seg.curve);
          const corner = 1 - Math.min(0.46, cv * 0.055 * (1.3 - c.skill * 0.55));
          let want = c.base * corner;
          if (c.mistake > 0) { want *= 0.84; c.mistake -= dt; }
          else if (Math.random() < 0.0016 * (1.15 - c.skill)) c.mistake = 0.5 + Math.random() * 1.1;
          /* Gummiband: hält das Feld spannend, aber gedeckelt */
          if (rubber) {
            const gap = playerDist - (c.lap * len + c.z);
            want *= 1 + U.clamp(gap / 52000, -1, 1) * (gap > 0 ? 0.085 : 0.055);
          }
          c.speed = U.lerp(c.speed, want, 1 - Math.pow(0.15, dt));
          /* Ideallinie + Ausweichen */
          let target = -Math.sign(seg.curve) * Math.min(0.55, cv * 0.09) + (c.line || 0);
          for (const o of this.cars) {
            if (o === c) continue;
            const d = o.z - c.z;
            if (d > 0 && d < 2400 && Math.abs(o.offset - c.offset) < 0.42) target += (c.offset > o.offset ? 0.5 : -0.5);
          }
          const pd = (this.position + PLAYER_Z) - c.z;
          if (pd < 0 && pd > -2200 && Math.abs(this.playerX - c.offset) < 0.4) target += (c.offset > this.playerX ? 0.42 : -0.42);
          c.wanted = U.clamp(target, -0.88, 0.88);
          c.offset = U.lerp(c.offset, c.wanted, 1 - Math.pow(0.25, dt));
        }
      } else {
        /* Verkehr: gleichmässig, weicht leicht aus */
        if (!frozen) {
          const pd = (this.position + PLAYER_Z) - c.z;
          if (pd < 0 && pd > -1800 && Math.abs(this.playerX - c.offset) < 0.32) {
            c.offset += (c.offset > this.playerX ? 1 : -1) * dt * 0.55;
            c.offset = U.clamp(c.offset, -0.85, 0.85);
          }
        }
      }

      if (!frozen) {
        const before = c.z;
        c.z = U.increase(c.z, dt * c.speed, len);
        if (c.z < before) c.lap++;
      }
      c.percent = U.pctRemaining(c.z, SEG.LEN);
      c.dist = c.lap * len + c.z;
      const ns = T.findSegment(c.z);
      if (ns !== c.seg) {
        if (c.seg) { const i = c.seg.cars.indexOf(c); if (i >= 0) c.seg.cars.splice(i, 1); }
        ns.cars.push(c); c.seg = ns;
      }
    }
  },

  updateRank() {
    const len = this.track.trackLength;
    const my = this.lap * len + this.position;
    let ahead = 0, total = 0;
    for (const c of this.cars) { if (!c.rival) continue; total++; if (c.dist > my) ahead++; }
    this.place = ahead + 1; this.fieldSize = total + 1;
  },

  checkCollisions(playerSeg, dt) {
    const carW = 0.34 * 2;          /* Wagenbreite in Fahrbahn-Einheiten (grob) */
    const segs = [playerSeg, this.track.segments[(playerSeg.index + 1) % this.track.total]];
    for (const seg of segs) {
      for (const c of seg.cars) {
        const dz = c.z - (this.position + PLAYER_Z);
        if (dz > 900 || dz < -500) continue;
        const dxo = Math.abs(this.playerX - c.offset);
        if (dxo < carW * 0.55 && this.speed > c.speed) {
          if (dz > -260) {
            /* Aufprall */
            this.speed = Math.max(c.speed * 0.72, this.maxSpeed * 0.18);
            this.playerX += (this.playerX > c.offset ? 1 : -1) * 0.25;
            this.shake = 1; this.combo = 0;
            HR.Audio.crash(); HR.buzz(45);
            this.pop('RUMMS!', '#ff3b5c');
            c.speed *= 0.9;
          }
        } else if (dxo < carW * 1.15 && dz > -420 && dz < 240 && this.speed > c.speed * 1.12 && !c._nm) {
          /* Beinahe-Berührung */
          c._nm = true;
          this.combo++;
          this.comboT = 3.5;
          this.nearMiss++;
          this.nos = Math.min(1, this.nos + 0.10);
          this.addScore(60 * Math.min(6, this.combo), 'FAST BERÜHRT ×' + this.combo);
          setTimeout(() => { c._nm = false; }, 1400);
        }
      }
    }
    /* Landschaft am Fahrbahnrand */
    if (Math.abs(this.playerX) > 1.05 && this.speed > this.maxSpeed * 0.25) {
      for (const sp of playerSeg.sprites) {
        if (sp.center) continue;
        const w = sp.s.worldW;
        const a = sp.offset, b = sp.offset + (sp.offset > 0 ? w : -w);
        const lo = Math.min(a, b), hi = Math.max(a, b);
        if (this.playerX + 0.2 > lo && this.playerX - 0.2 < hi) {
          this.speed = this.maxSpeed * 0.14;
          this.shake = 1.2; this.combo = 0;
          HR.Audio.crash(); HR.buzz(70);
          this.pop('AUA!', '#ff3b5c');
          this.playerX = U.clamp(this.playerX, -0.95, 0.95);
          break;
        }
      }
    }
  },

  addScore(n, label) {
    this.score += n;
    if (label) this.pop('+' + Math.round(n) + ' ' + label, '#ffcc33');
  },

  /* ---------------------------------------------------------- Ende */
  finish(completed) {
    if (this.state === 'finished') return;
    this.state = 'finished';
    this.dom.controls.hidden = true;
    HR.Audio.stopEngine();
    const cfg = this.cfg, len = this.track.trackLength;
    const my = this.lap * len + this.position;

    /* Endstand: verbleibende Distanz der Gegner in Zeit umrechnen */
    const stand = [{ me: true, name: 'DU', col: '#ff6a1f', t: this.raceTime, dist: my }];
    for (const c of this.cars) {
      if (!c.rival) continue;
      const behind = my - c.dist;
      stand.push({ me: false, name: c.name, col: c.col, dist: c.dist, t: this.raceTime + behind / Math.max(1, c.speed || c.base) });
    }
    stand.sort((a, b) => a.t - b.t);
    const place = stand.findIndex(s => s.me) + 1;

    const res = this.result = {
      mode: cfg.mode, eventId: cfg.eventId, name: cfg.name,
      completed: completed,
      place: place, of: stand.length,
      time: this.raceTime,
      km: this.distance / UNITS_PER_KM,
      distance: this.distance,
      score: Math.round(this.score + this.distance / 900 + this.driftTime * 55 + this.nearMiss * 40),
      stage: this.stage,
      trapTotal: this.trapTotal,
      topSpeed: Math.round(this.topSpeed),
      drift: Math.round(this.driftTime),
      nearMiss: this.nearMiss,
      standings: stand
    };
    this.big(cfg.mode === 'marathon' ? 'ZEIT ABGELAUFEN' : 'ZIEL');
    setTimeout(() => { this.big(''); if (this.onFinish) this.onFinish(res); }, 1300);
  },

  big(t) { this.dom.big.textContent = t; if (t) { this.dom.big.classList.remove('pulse'); void this.dom.big.offsetWidth; this.dom.big.classList.add('pulse'); } },
  pop(text, col) {
    const d = document.createElement('div');
    d.className = 'pop'; d.textContent = text; d.style.color = col || '#fff';
    this.dom.pops.appendChild(d);
    setTimeout(() => d.remove(), 1100);
    if (this.dom.pops.childElementCount > 6) this.dom.pops.firstChild.remove();
  },

  /* ---------------------------------------------------------- HUD */
  updateHud(dt) {
    this._hudT = (this._hudT || 0) + dt;
    const d = this.dom;
    d.rev.style.width = (U.clamp(((this.speed / this.maxSpeed) * 6) % 1 * 0.7 + 0.3, 0, 1) * 100) + '%';
    d.nos.style.width = (this.nos * 100) + '%';
    if (this._hudT < 0.06) return;
    this._hudT = 0;
    d.kmh.textContent = Math.round(this.speed / 40);
    if (!d.chipPos.hidden) d.chipPos.querySelector('.v').innerHTML = '<b>' + (this.place || 1) + '</b>/' + (this.fieldSize || 1);
    if (!d.chipLap.hidden) d.chipLap.querySelector('.v').innerHTML = '<b>' + Math.min(this.lap + 1, this.cfg.laps) + '</b>/' + this.cfg.laps;
    if (this.cfg.mode === 'marathon') {
      d.chipTime.querySelector('.k').textContent = 'Restzeit';
      d.chipTime.querySelector('.v').textContent = U.fmtClock(Math.max(0, this.timeLeft));
      d.chipTime.style.color = this.timeLeft < 8 ? '#ff3b5c' : '';
      d.chipExtra.querySelector('.k').textContent = 'Distanz';
      d.chipExtra.querySelector('.v').textContent = (this.distance / UNITS_PER_KM).toFixed(2) + ' km';
    } else {
      d.chipTime.querySelector('.k').textContent = 'Zeit';
      d.chipTime.style.color = '';
      d.chipTime.querySelector('.v').textContent = U.fmtTime(this.raceTime);
      if (this.cfg.mode === 'trap') {
        d.chipExtra.querySelector('.k').textContent = 'Blitzer';
        d.chipExtra.querySelector('.v').textContent = this.trapTotal + ' km/h';
      }
    }
    this.drawMini();
  },

  drawMini() {
    const g = this.miniG; if (!g) return;
    const c = this.miniCv, w = c.width, h = c.height;
    g.clearRect(0, 0, w, h);
    const pts = this.track.mini, pad = 10;
    const X = p => pad + p[0] * (w - pad * 2), Y = p => pad + p[1] * (h - pad * 2);
    g.lineWidth = 5; g.strokeStyle = 'rgba(0,0,0,.55)'; g.lineJoin = 'round';
    g.beginPath(); g.moveTo(X(pts[0]), Y(pts[0]));
    for (let i = 1; i < pts.length; i++) g.lineTo(X(pts[i]), Y(pts[i]));
    g.stroke();
    g.lineWidth = 2.4; g.strokeStyle = 'rgba(255,255,255,.62)'; g.stroke();

    const idx = i => Math.min(pts.length - 1, Math.floor((i / this.track.total) * pts.length));
    for (const car of this.cars) {
      if (!car.rival) continue;
      const p = pts[idx(car.seg ? car.seg.index : 0)];
      g.fillStyle = car.col; g.beginPath(); g.arc(X(p), Y(p), 3, 0, Math.PI * 2); g.fill();
    }
    const ps = this.track.findSegment(this.position + PLAYER_Z);
    const p = pts[idx(ps.index)];
    g.fillStyle = '#ff6a1f'; g.strokeStyle = '#fff'; g.lineWidth = 2;
    g.beginPath(); g.arc(X(p), Y(p), 5, 0, Math.PI * 2); g.fill(); g.stroke();
  },

  /* ---------------------------------------------------------- Rendern */
  render() {
    const g = this.g, W = this.W, H = this.H, T = this.track;
    const segs = T.segments, total = T.total;

    const baseSeg = T.findSegment(this.position);
    const basePct = U.pctRemaining(this.position, SEG.LEN);
    const playerSeg = T.findSegment(this.position + PLAYER_Z);
    const playerPct = U.pctRemaining(this.position + PLAYER_Z, SEG.LEN);
    const playerY = U.lerp(playerSeg.p1.world.y, playerSeg.p2.world.y, playerPct);
    const speedPct = this.speed / this.maxSpeed;

    /* Bildschirmzittern */
    let shx = 0, shy = 0;
    if (this.shake > 0.01) {
      shx = (Math.random() - 0.5) * this.shake * 22 * this.res;
      shy = (Math.random() - 0.5) * this.shake * 16 * this.res;
    }
    g.setTransform(1, 0, 0, 1, shx, shy);

    const horizon = H * CY - this.pitch * H * 0.42;
    this.bgOff[0] = U.increase(this.bgOff[0], -baseSeg.curve * speedPct * 0.00042, 1);
    this.bgOff[1] = U.increase(this.bgOff[1], -baseSeg.curve * speedPct * 0.0011, 1);
    this.bgOff[2] = U.increase(this.bgOff[2], -baseSeg.curve * speedPct * 0.0020, 1);

    this.renderBackground(g, W, H, horizon, baseSeg.b, playerY);

    /* --- Strassensegmente --- */
    let maxy = H, x = 0, dxx = -(baseSeg.curve * basePct);
    const dd = this.drawDist;
    const camY = playerY + CAM_H;
    for (let n = 0; n < dd; n++) {
      const s = segs[(baseSeg.index + n) % total];
      s.looped = s.index < baseSeg.index;
      const t = n / dd;
      s.fog = Math.exp(-FOG_DENSITY * t * t);
      s.clip = maxy;
      const camZ = this.position - (s.looped ? T.trackLength : 0);
      project(s.p1, (this.playerX * SEG.ROAD_W) - x, camY, camZ, W, H);
      project(s.p2, (this.playerX * SEG.ROAD_W) - x - dxx, camY, camZ, W, H);
      x += dxx; dxx += s.curve;

      if (s.p1.camera.z <= CAM_DEPTH || s.p2.screen.y >= s.p1.screen.y || s.p2.screen.y >= maxy) continue;
      renderSegment(g, W, s);
      maxy = s.p2.screen.y;
    }

    /* Dunst in der Ferne – ein einziger Verlauf statt einer Füllung je Segment
       (deutlich schneller und weicher als die klassische Segment-Methode) */
    const fogTop = Math.max(0, maxy);
    const fogBot = Math.min(H, fogTop + (H - fogTop) * 0.62);
    if (fogBot > fogTop + 2) {
      const fg = g.createLinearGradient(0, fogTop, 0, fogBot);
      fg.addColorStop(0, U.rgba(baseSeg.b.fog, 0.92));
      fg.addColorStop(0.45, U.rgba(baseSeg.b.fog, 0.42));
      fg.addColorStop(1, U.rgba(baseSeg.b.fog, 0));
      g.fillStyle = fg; g.fillRect(0, fogTop, W, fogBot - fogTop);
    }

    /* --- Sprites & Fahrzeuge von hinten nach vorn --- */
    for (let n = dd - 1; n > 0; n--) {
      const s = segs[(baseSeg.index + n) % total];
      for (const c of s.cars) {
        const sc = U.lerp(s.p1.screen.scale, s.p2.screen.scale, c.percent);
        const sx = U.lerp(s.p1.screen.x, s.p2.screen.x, c.percent) + sc * c.offset * SEG.ROAD_W * W / 2;
        const sy = U.lerp(s.p1.screen.y, s.p2.screen.y, c.percent);
        drawSprite(g, W, c.sprite, sc, sx, sy, -0.5, -1, s.clip, s.fog);
      }
      for (const sp of s.sprites) {
        const sc = s.p1.screen.scale;
        const sx = s.p1.screen.x + sc * sp.offset * SEG.ROAD_W * W / 2;
        const sy = s.p1.screen.y;
        drawSprite(g, W, sp.s, sc, sx, sy, sp.center ? -0.5 : (sp.offset < 0 ? -1 : 0), -1, s.clip, s.fog);
      }
      if (s === playerSeg) this.renderPlayer(g, W, H, speedPct, playerSeg, playerPct);
    }

    this.renderSmoke(g);
    this.renderWeather(g, W, H);
    this.renderFx(g, W, H, speedPct);
    g.setTransform(1, 0, 0, 1, 0, 0);
  },

  renderBackground(g, W, H, horizon, b, playerY) {
    /* Himmel */
    const sky = g.createLinearGradient(0, 0, 0, Math.max(1, horizon));
    sky.addColorStop(0, b.sky[0]); sky.addColorStop(0.62, b.sky[1]); sky.addColorStop(1, b.sky[2]);
    g.fillStyle = sky; g.fillRect(0, 0, W, Math.max(0, horizon));
    const gnd = g.createLinearGradient(0, horizon, 0, H);
    gnd.addColorStop(0, b.fog); gnd.addColorStop(1, U.shade(b.grass[1], -0.25));
    g.fillStyle = gnd; g.fillRect(0, Math.max(0, horizon), W, H - Math.max(0, horizon));

    /* Sonne / Mond – wandert mit den Kurven mit */
    const ph = ((this.bgOff[1] % 1) + 1) % 1;
    const sx = W * (1.5 - 2 * ph);
    const sy = horizon - horizon * b.sunY * 0.9;
    const sr = Math.min(W, H) * b.sunR;
    g.save();
    const gr = g.createRadialGradient(sx, sy, sr * 0.2, sx, sy, sr * 3.4);
    gr.addColorStop(0, U.rgba(b.sun, 0.55)); gr.addColorStop(1, U.rgba(b.sun, 0));
    g.fillStyle = gr; g.beginPath(); g.arc(sx, sy, sr * 3.4, 0, Math.PI * 2); g.fill();
    g.fillStyle = b.sun; g.beginPath(); g.arc(sx, sy, sr, 0, Math.PI * 2); g.fill();
    if (!b.night) {   /* Retro-Streifen in der Sonne */
      g.globalCompositeOperation = 'destination-out';
      for (let i = 0; i < 5; i++) g.fillRect(sx - sr, sy + sr * (0.15 + i * 0.20), sr * 2, sr * (0.05 + i * 0.022));
      g.globalCompositeOperation = 'source-over';
    }
    g.restore();

    /* Luftperspektive: weiter entfernte Ebenen blasser */
    const bg = HR.biomeBg(b);
    tile(g, bg.cloud, this.bgOff[0], W, horizon - horizon * 0.62, W * 1.6, horizon * 0.34, 0.9);
    tile(g, bg.far, this.bgOff[1], W, horizon - horizon * 0.28, W * 1.35, horizon * 0.40, 0.55);
    tile(g, bg.near, this.bgOff[2], W, horizon - horizon * 0.10, W * 1.15, horizon * 0.32, 0.82);
  },

  renderPlayer(g, W, H, speedPct, seg, pct) {
    const p = this.player.sprites;
    const braking = this.input.brake;
    const st = this.steerSmooth || 0;
    let sp;
    if (st < -0.22) sp = braking ? p.lb : p.l;
    else if (st > 0.22) sp = braking ? p.rb : p.r;
    else sp = braking ? p.sb : p.s;

    const scale = CAM_DEPTH / PLAYER_Z;
    const camY = U.lerp(seg.p1.camera.y == null ? 0 : seg.p1.camera.y, seg.p2.camera.y == null ? 0 : seg.p2.camera.y, pct);
    const bounce = (1.1 * Math.random() * speedPct * this.res) * (Math.random() < 0.5 ? -1 : 1);
    const destY = H * CY - scale * camY * H / 2 + bounce;
    const lean = st * 0.02 * W;
    drawSprite(g, W, sp, scale, W / 2 + lean, destY, -0.5, -1, 0, 1);
  },

  renderSmoke(g) {
    for (const p of this.smoke) {
      const a = U.clamp(p.life / p.max, 0, 1) * 0.35;
      g.fillStyle = 'rgba(225,228,235,' + a + ')';
      g.beginPath(); g.arc(p.x, p.y, p.r * this.res, 0, Math.PI * 2); g.fill();
    }
  },

  renderWeather(g, W, H) {
    if (this.weather === 'clear') return;
    if (!this.rainDrops) {
      this.rainDrops = [];
      for (let i = 0; i < 130; i++) this.rainDrops.push({ x: Math.random(), y: Math.random(), s: 0.4 + Math.random() * 0.8 });
    }
    const snow = this.weather === 'snow';
    const sp = (0.55 + this.speed / this.maxSpeed * 1.9);
    g.save();
    g.strokeStyle = snow ? 'rgba(255,255,255,.75)' : 'rgba(200,222,255,.34)';
    g.fillStyle = 'rgba(255,255,255,.85)';
    g.lineWidth = 1.3 * this.res;
    for (const d of this.rainDrops) {
      d.y += (snow ? 0.006 : 0.034) * d.s * sp;
      d.x += snow ? 0.0016 * Math.sin(d.y * 12) : -0.0012 * sp;
      if (d.y > 1) { d.y = -0.02; d.x = Math.random(); }
      if (d.x < 0) d.x += 1;
      const px = d.x * W, py = d.y * H;
      if (snow) { g.beginPath(); g.arc(px, py, 1.7 * d.s * this.res, 0, Math.PI * 2); g.fill(); }
      else { g.beginPath(); g.moveTo(px, py); g.lineTo(px + 2 * this.res, py + 11 * d.s * this.res * (0.6 + sp * 0.25)); g.stroke(); }
    }
    g.restore();
  },

  renderFx(g, W, H, speedPct) {
    /* Tempo-Striche bei NOS */
    if (this.nosOn) {
      g.save();
      g.strokeStyle = 'rgba(140,230,255,.5)'; g.lineWidth = 2 * this.res;
      for (let i = 0; i < 22; i++) {
        const a = Math.random() * Math.PI * 2, r0 = Math.min(W, H) * (0.22 + Math.random() * 0.2), r1 = r0 + Math.min(W, H) * 0.22;
        g.beginPath();
        g.moveTo(W / 2 + Math.cos(a) * r0, H * 0.55 + Math.sin(a) * r0);
        g.lineTo(W / 2 + Math.cos(a) * r1, H * 0.55 + Math.sin(a) * r1);
        g.stroke();
      }
      g.restore();
    }
    /* Vignette – nimmt mit Tempo zu */
    const v = 0.24 + speedPct * 0.30;
    const vg = g.createRadialGradient(W / 2, H * 0.55, Math.min(W, H) * 0.28, W / 2, H * 0.55, Math.max(W, H) * 0.78);
    vg.addColorStop(0, 'rgba(0,0,0,0)'); vg.addColorStop(1, 'rgba(0,0,0,' + v.toFixed(2) + ')');
    g.fillStyle = vg; g.fillRect(0, 0, W, H);
  }
};

/* ---------------------------------------------------------- Projektion */
function project(p, camX, camY, camZ, W, H) {
  p.camera.x = (p.world.x || 0) - camX;
  p.camera.y = (p.world.y || 0) - camY;
  p.camera.z = (p.world.z || 0) - camZ;
  p.screen.scale = CAM_DEPTH / p.camera.z;
  p.screen.x = Math.round((W / 2) + (p.screen.scale * p.camera.x * W / 2));
  p.screen.y = Math.round((H * CY) - (p.screen.scale * p.camera.y * H / 2));
  p.screen.w = Math.round(p.screen.scale * SEG.ROAD_W * W / 2);
}

function poly(g, x1, y1, x2, y2, x3, y3, x4, y4, col) {
  g.fillStyle = col;
  g.beginPath(); g.moveTo(x1, y1); g.lineTo(x2, y2); g.lineTo(x3, y3); g.lineTo(x4, y4); g.closePath(); g.fill();
}

function renderSegment(g, W, s) {
  const p1 = s.p1.screen, p2 = s.p2.screen, b = s.b, alt = s.alt;
  const x1 = p1.x, y1 = p1.y, w1 = p1.w, x2 = p2.x, y2 = p2.y, w2 = p2.w;
  const r1 = w1 / Math.max(6, 2 * SEG.LANES), r2 = w2 / Math.max(6, 2 * SEG.LANES);
  const l1 = w1 / Math.max(32, 8 * SEG.LANES), l2 = w2 / Math.max(32, 8 * SEG.LANES);

  g.fillStyle = alt ? b.grass[0] : b.grass[1];
  g.fillRect(0, y2, W, y1 - y2);

  const rc = alt ? b.rumble[0] : b.rumble[1];
  poly(g, x1 - w1 - r1, y1, x1 - w1, y1, x2 - w2, y2, x2 - w2 - r2, y2, rc);
  poly(g, x1 + w1 + r1, y1, x1 + w1, y1, x2 + w2, y2, x2 + w2 + r2, y2, rc);
  poly(g, x1 - w1, y1, x1 + w1, y1, x2 + w2, y2, x2 - w2, y2, alt ? b.road[0] : b.road[1]);

  /* Mittellinien nur zeichnen, solange sie überhaupt sichtbar breit sind */
  if (alt && w1 > W * 0.055) {
    const lw1 = w1 * 2 / SEG.LANES, lw2 = w2 * 2 / SEG.LANES;
    let lx1 = x1 - w1 + lw1, lx2 = x2 - w2 + lw2;
    for (let i = 1; i < SEG.LANES; i++) {
      poly(g, lx1 - l1, y1, lx1 + l1, y1, lx2 + l2, y2, lx2 - l2, y2, b.lane);
      lx1 += lw1; lx2 += lw2;
    }
  }
}

function drawSprite(g, W, sp, scale, destX, destY, offX, offY, clipY, alpha) {
  if (scale <= 0) return;
  const destW = sp.worldW * SEG.ROAD_W * scale * W / 2;
  const destH = destW * (sp.h / sp.w);
  if (destW < 1 || destH < 1) return;
  const dx = destX + destW * (offX || 0);
  const dy = destY + destH * (offY || 0);
  const clipH = clipY ? Math.max(0, dy + destH - clipY) : 0;
  if (clipH >= destH) return;
  const a = alpha == null ? 1 : alpha;
  if (a < 0.02) return;
  if (a < 1) { g.save(); g.globalAlpha = a; }
  g.drawImage(sp.c, 0, 0, sp.w, sp.h - sp.h * clipH / destH, dx, dy, destW, destH - destH * clipH / destH);
  if (a < 1) g.restore();
}

/* Hintergrundebene waagrecht kacheln */
function tile(g, img, off, W, bottom, dw, dh, alpha) {
  const x0 = -((off % 1) * dw);
  const y = bottom - dh;
  if (alpha != null && alpha < 1) { g.save(); g.globalAlpha = alpha; }
  for (let x = x0 - dw; x < W + dw; x += dw) g.drawImage(img, x, y, dw, dh);
  if (alpha != null && alpha < 1) g.restore();
}

})(window.HR);
