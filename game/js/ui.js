/* ============================================================
   Horizon Rush – Menüs, Karriere, Garage, Ergebnisse
   ============================================================ */
(function (HR) {
'use strict';
const U = HR.U, S = () => HR.Save.data;

/* ---------------------------------------------------------- Karriere-Aufbau */
const DISTRICTS = [
  { id: 'd1', name: 'Küstenfest',     icon: '🌴', lvl: 1,  stars: 0,  biomes: ['kueste', 'wald'] },
  { id: 'd2', name: 'Wüstenrausch',   icon: '🌵', lvl: 3,  stars: 8,  biomes: ['wueste', 'canyon'] },
  { id: 'd3', name: 'Bergetappe',     icon: '🏔️', lvl: 6,  stars: 22, biomes: ['alpen', 'wald'] },
  { id: 'd4', name: 'Nachtstadt',     icon: '🌃', lvl: 9,  stars: 38, biomes: ['stadt', 'kueste'] },
  { id: 'd5', name: 'Horizon-Finale', icon: '🏆', lvl: 13, stars: 56, biomes: null }
];
const MODE_INFO = {
  sprint:   { icon: '🏁', label: 'Sprint',     desc: 'Punkt zu Punkt' },
  circuit:  { icon: '🔄', label: 'Rundkurs',   desc: 'mehrere Runden' },
  time:     { icon: '⏱️', label: 'Zeitfahren', desc: 'gegen die Uhr' },
  trap:     { icon: '📸', label: 'Blitzerjagd', desc: 'Tempo an Messpunkten' },
  marathon: { icon: '♾️', label: 'Marathon',   desc: 'endlos, Zeit sammeln' }
};
const NAME_A = ['Sonnen', 'Küsten', 'Dünen', 'Gipfel', 'Nacht', 'Neon', 'Sturm', 'Feuer', 'Schatten',
  'Kies', 'Serpentinen', 'Horizont', 'Morgen', 'Wolken', 'Salz', 'Kobalt'];
const NAME_B = {
  sprint: ['sprint', 'etappe', 'dash'], circuit: ['rundkurs', 'schleife', 'ring'],
  time: ['zeitfahren', 'jagd'], trap: ['blitzer', 'radarlauf']
};

/* Alle Events eines Bezirks – deterministisch aus der Profil-Saat */
function eventsOf(d, di) {
  const rng = HR.RNG('ev-' + S().seed + '-' + d.id);
  const list = [];
  for (let i = 0; i < 6; i++) {
    const mode = i === 5 ? (rng.chance(0.5) ? 'circuit' : 'sprint')
      : ['sprint', 'circuit', 'trap', 'sprint', 'time'][i];
    const final = i === 5;
    const diff = U.clamp(0.16 + di * 0.17 + i * 0.028 + (final ? 0.12 : 0), 0, 1);
    const laps = mode === 'circuit' ? rng.int(2, 3) : 1;
    const segments = mode === 'circuit' ? 1500 : (mode === 'time' ? 2200 : (final ? 3400 : 2800));
    const name = rng.pick(NAME_A) + '-' + rng.pick(NAME_B[mode] || NAME_B.sprint);
    const rivals = mode === 'time' ? 0 : (final ? 7 : 5);
    const base = Math.round((1500 + di * 2600) * (1 + i * 0.16) * (final ? 1.9 : 1));
    list.push({
      id: d.id + '-' + i, idx: i, district: d.id, districtName: d.name, final: final,
      name: name.charAt(0).toUpperCase() + name.slice(1),
      mode: mode, seed: 'trk-' + S().seed + '-' + d.id + '-' + i,
      laps: laps, rivalCount: rivals, difficulty: diff, segments: segments,
      traffic: mode === 'time' ? 0.25 : 0.4 + diff * 0.35,
      biomes: d.biomes, traps: 4,
      reward: { cr: base, xp: Math.round(90 + di * 70 + i * 14 + (final ? 130 : 0)) },
      targetTime: 0
    });
  }
  return list;
}
function districtUnlocked(d) {
  return S().level >= d.lvl || totalStars() >= d.stars;
}
function totalStars() {
  let n = 0; for (const k in S().events) n += S().events[k].stars || 0; return n;
}

/* Zielzeit fürs Zeitfahren: aus Streckenlänge und Fahrzeugleistung geschätzt */
function targetTimeFor(cfg) {
  const st = HR.effStats(S().car);
  const km = (cfg.segments * HR.SEG.LEN) / HR.UNITS_PER_KM * (cfg.laps || 1);
  const avg = st.top * (0.60 - cfg.difficulty * 0.09);
  return km / avg * 3600;
}

/* ---------------------------------------------------------- Bewertung */
function starsFor(cfg, res) {
  if (cfg.mode === 'time') {
    const t = cfg.targetTime || 9999;
    return res.time <= t ? 3 : res.time <= t * 1.07 ? 2 : 1;
  }
  if (cfg.mode === 'trap') {
    const goal = Math.round(HR.effStats(S().car).top * 0.86) * (cfg.traps || 4);
    return res.trapTotal >= goal ? 3 : res.trapTotal >= goal * 0.88 ? 2 : 1;
  }
  if (cfg.mode === 'marathon') return 0;
  if (!cfg.rivalCount) return 2;
  return res.place === 1 ? 3 : res.place <= 3 ? 2 : 1;
}

/* Adaptive Gegnerstärke nachführen (Elo-artig, mit Dominanz-Zuschlag).
   Der Zuschlag sorgt dafür, dass ein klarer Kantersieg die KI deutlich
   schneller nachzieht als ein knapper – sonst braucht die Anpassung
   zu viele Rennen. */
HR.updateSkill = function (cfg, res) {
  const d = S();
  if (!cfg.rivalCount || cfg.rivalCount < 1) return 0;
  const of = Math.max(2, res.of);
  const actual = 1 - (res.place - 1) / (of - 1);       /* 1 = Sieg, 0 = Letzter */
  const before = d.skill;
  let delta = (actual - 0.5) * 12;
  if (res.place === 1 && res.standings.length > 1) {
    const gap = (res.standings[1].t - res.standings[0].t) / Math.max(1, res.time);
    delta += U.clamp(gap * 45, 0, 9);                  /* überlegen gewonnen */
  } else if (res.place === of && res.standings.length > 1) {
    const gap = (res.standings[of - 1].t - res.standings[of - 2].t) / Math.max(1, res.time);
    delta -= U.clamp(gap * 45, 0, 9);                  /* klar abgeschlagen */
  }
  d.skill = U.clamp(d.skill + delta, 5, 100);
  HR.Save.save();
  return Math.round((d.skill - before) * 10) / 10;
};

/* ---------------------------------------------------------- Oberfläche */
const UI = HR.UI = {
  root: null,
  lastCfg: null,

  boot() {
    this.root = document.getElementById('screens');
    HR.Race.init(document.getElementById('cv'), document.getElementById('mini'));
    HR.Race.onFinish = res => this.result(res);
    document.getElementById('btnPause').addEventListener('click', () => this.pause());
    /* erster Tipp irgendwo -> Ton freischalten (iOS-Regel) */
    document.addEventListener('pointerdown', () => HR.Audio.init(), { once: true });
    if (!S().seenIntro) this.intro(); else this.home();
    this.checkOrientation();
    window.addEventListener('resize', () => this.checkOrientation());
  },

  checkOrientation() {
    const el = document.getElementById('rotateHint');
    const racing = HR.Race.state === 'racing' || HR.Race.state === 'countdown';
    el.hidden = !(racing && window.innerHeight > window.innerWidth);
  },

  paint(html, cls) {
    this.root.innerHTML = '<div class="screen ' + (cls || '') + '"><div class="wrap">' + html + '</div></div>';
    this.root.style.pointerEvents = 'auto';
    const self = this;
    this.root.querySelectorAll('[data-a]').forEach(el => {
      el.addEventListener('click', function (e) {
        HR.Audio.init(); HR.Audio.ui();
        const a = this.getAttribute('data-a');
        const v = this.getAttribute('data-v');
        if (self.actions[a]) self.actions[a].call(self, v, this);
      });
    });
    this.root.scrollTop = 0;
  },
  close() { this.root.innerHTML = ''; this.root.style.pointerEvents = 'none'; },

  /* ------------------------------------------------ Start / Einführung */
  intro() {
    this.paint(
      '<div class="brand"><h1>Horizon<br>Rush</h1><p>Prozedurales Arcade-Racing</p></div>' +
      '<div class="card">' +
      '<p class="hint" style="font-size:14px;line-height:1.7">' +
      '<b>Jede Strecke wird neu berechnet.</b> Kurven, Kuppen, Landschaft und Wetter entstehen aus einer Saat – ' +
      'du fährst nie zweimal dieselbe Strasse.<br><br>' +
      '<b>Die Gegner passen sich an dich an.</b> Nach jedem Rennen wird deine Fahrstärke neu geschätzt; die KI zieht mit.<br><br>' +
      '<b>5 Minuten oder 2 Stunden.</b> Ein Sprint dauert gut eine Minute, die Karriere hat 30 Events, ' +
      'und der Marathon läuft endlos weiter, solange du Checkpoints erreichst.</p></div>' +
      '<div class="card"><div class="sect" style="margin-top:0">Steuerung</div>' +
      '<p class="hint" style="font-size:13.5px;line-height:1.7">' +
      '👈 <b>Linkes Feld</b> – Finger nach links/rechts schieben zum Lenken (analog).<br>' +
      '🛑 <b>Bremse</b> rechts. Gas gibt der Wagen automatisch.<br>' +
      '💨 <b>NOS</b> lädt sich durch knappe Überholmanöver und Drifts.</p></div>' +
      '<button class="btn primary" data-a="startIntro"><span class="ico">🏁</span><span class="txt">Los geht\'s<small>Profil wird lokal auf dem Gerät gespeichert</small></span><span class="arrow">›</span></button>'
    );
  },

  home() {
    HR.Race.stop();
    const d = S();
    const need = HR.xpForLevel(d.level);
    const car = HR.carById(d.car);
    const standalone = window.navigator.standalone || window.matchMedia('(display-mode: standalone)').matches;
    this.paint(
      '<div class="brand"><h1>Horizon<br>Rush</h1><p>Festival · Saison 1</p></div>' +
      '<div class="stats">' +
      '<div class="stat"><div class="k">Stufe</div><div class="v neon">' + d.level + '</div></div>' +
      '<div class="stat"><div class="k">Guthaben</div><div class="v gold">' + U.fmtNum(d.credits) + '</div></div>' +
      '<div class="stat"><div class="k">Sterne</div><div class="v gold">' + totalStars() + '</div></div>' +
      '<div class="stat"><div class="k">KI-Stufe</div><div class="v acc">' + Math.round(d.skill) + '</div></div>' +
      '</div>' +
      '<div class="xpbar"><i style="width:' + Math.round(d.xp / need * 100) + '%"></i></div>' +
      '<div class="spacer"></div>' +
      '<button class="btn primary" data-a="quick"><span class="ico">⚡</span><span class="txt">Schnellrennen<small>Neue Zufallsstrecke, 5 Gegner · ca. 1–2 Min</small></span><span class="arrow">›</span></button>' +
      '<button class="btn violet" data-a="career"><span class="ico">🏁</span><span class="txt">Karriere<small>30 Events in 5 Bezirken · Sterne sammeln</small></span><span class="arrow">›</span></button>' +
      '<button class="btn" data-a="marathon"><span class="ico">♾️</span><span class="txt">Marathon<small>Endlos weiterfahren, Checkpoints geben Zeit</small></span><span class="arrow">›</span></button>' +
      '<button class="btn" data-a="daily"><span class="ico">📅</span><span class="txt">Tagesroute<small>Jeden Tag dieselbe Strecke für alle · ' + dailyLabel() + '</small></span><span class="arrow">›</span></button>' +
      '<button class="btn" data-a="garage"><span class="ico">🔧</span><span class="txt">Garage<small>' + car.name + ' · LI ' + HR.perfIndex(d.car) + '</small></span><span class="arrow">›</span></button>' +
      '<button class="btn ghost small" data-a="settings"><span class="ico">⚙️</span><span class="txt">Einstellungen</span></button>' +
      (standalone ? '' :
        '<p class="hint center">📲 Tipp: In Safari auf <b>Teilen</b> → <b>Zum Home-Bildschirm</b>. ' +
        'Dann läuft Horizon Rush im Vollbild wie eine App – auch ohne Internet.</p>')
    );
  },

  /* ------------------------------------------------ Karriere */
  career() {
    let h = '<div class="topbar"><button class="back" data-a="home">‹</button><h2>Karriere</h2>' +
      '<div class="stars" style="font-size:14px"><b>★</b> ' + totalStars() + '</div></div>';
    DISTRICTS.forEach((d, di) => {
      const ok = districtUnlocked(d);
      const evs = eventsOf(d, di);
      let done = 0, st = 0;
      evs.forEach(e => { const r = S().events[e.id]; if (r) { done++; st += r.stars; } });
      h += '<button class="evt ' + (ok ? '' : 'locked') + '" data-a="' + (ok ? 'district' : 'locked') + '" data-v="' + d.id + '">' +
        '<div class="badge">' + (ok ? d.icon : '🔒') + '</div>' +
        '<div class="mid"><div class="nm">' + d.name + '</div>' +
        '<div class="sub">' + (ok ? (done + '/6 Events · ' + st + ' von 18 Sternen')
          : ('Ab Stufe ' + d.lvl + ' oder ' + d.stars + ' Sternen')) + '</div></div>' +
        '<div class="rgt"><div class="stars">' + starStr(Math.round(st / 3), 6) + '</div></div></button>';
    });
    this.paint(h);
  },

  district(id) {
    const di = DISTRICTS.findIndex(x => x.id === id);
    const d = DISTRICTS[di];
    const evs = eventsOf(d, di);
    let h = '<div class="topbar"><button class="back" data-a="career">‹</button><h2>' + d.icon + ' ' + d.name + '</h2></div>';
    evs.forEach(e => {
      const rec = S().events[e.id];
      const mi = MODE_INFO[e.mode];
      const km = ((e.segments * HR.SEG.LEN) / HR.UNITS_PER_KM * (e.laps || 1)).toFixed(1);
      h += '<button class="evt" data-a="event" data-v="' + e.id + '">' +
        '<div class="badge">' + mi.icon + '</div>' +
        '<div class="mid"><div class="nm">' + e.name + (e.final ? ' 👑' : '') + '</div>' +
        '<div class="sub">' + mi.label + ' · ' + km + ' km' + (e.laps > 1 ? ' · ' + e.laps + ' Runden' : '') +
        (e.rivalCount ? ' · ' + e.rivalCount + ' Gegner' : ' · allein') + '</div></div>' +
        '<div class="rgt"><div class="stars">' + starStr(rec ? rec.stars : 0, 3) + '</div>' +
        '<div class="pay">' + U.fmtNum(e.reward.cr) + ' ₡</div></div></button>';
    });
    h += '<p class="hint">Die Strecken entstehen aus einer festen Saat – dasselbe Event fährt sich immer gleich, ' +
      'aber jedes Event ist eine andere Strasse.</p>';
    this.paint(h);
  },

  /* ------------------------------------------------ Garage */
  garage(tab) {
    tab = tab || 'cars';
    const d = S();
    let h = '<div class="topbar"><button class="back" data-a="home">‹</button><h2>Garage</h2>' +
      '<div class="stat" style="flex:none;padding:6px 10px"><div class="k">Guthaben</div><div class="v gold">' + U.fmtNum(d.credits) + '</div></div></div>' +
      '<div class="tabs">' +
      '<button class="' + (tab === 'cars' ? 'on' : '') + '" data-a="garage" data-v="cars">Fahrzeuge</button>' +
      '<button class="' + (tab === 'tune' ? 'on' : '') + '" data-a="garage" data-v="tune">Tuning</button>' +
      '<button class="' + (tab === 'paint' ? 'on' : '') + '" data-a="garage" data-v="paint">Lack</button></div>';

    if (tab === 'cars') {
      HR.CARS.forEach(c => {
        const owned = HR.Save.owns(c.id);
        const on = d.car === c.id;
        const st = owned ? HR.effStats(c.id) : { top: c.top, acc: c.acc, grip: c.grip, nos: c.nos };
        h += '<div class="carcard ' + (on ? 'on' : '') + '">' +
          '<canvas class="cc" data-car="' + c.id + '"></canvas>' +
          '<div class="info"><div class="nm">' + c.name + '<span class="cls">' + c.cls + '</span></div>' +
          '<div class="br">' + c.brand + ' · LI ' + (owned ? HR.perfIndex(c.id) : Math.round(60 + (c.top - 180) * 1.55 + c.acc * 210 + c.grip * 150 + c.nos * 60)) + '</div>' +
          '<div class="bars">' + bar('Tempo', (st.top - 180) / 190) + bar('Antritt', st.acc) + bar('Grip', st.grip) + '</div></div>' +
          '<div style="flex:none">' +
          (owned
            ? (on ? '<button class="buy" disabled>Aktiv</button>' : '<button class="buy alt" data-a="pick" data-v="' + c.id + '">Wählen</button>')
            : '<button class="buy" data-a="buy" data-v="' + c.id + '" ' + (d.credits < c.price ? 'disabled' : '') + '>' + U.fmtNum(c.price) + ' ₡</button>') +
          '</div></div>';
      });
    } else if (tab === 'tune') {
      const c = HR.carById(d.car), up = d.cars[c.id].up, st = HR.effStats(c.id);
      h += '<div class="card"><div class="carcard" style="border:none;background:none;padding:0;margin:0">' +
        '<canvas class="cc" data-car="' + c.id + '"></canvas>' +
        '<div class="info"><div class="nm">' + c.name + '<span class="cls">' + c.cls + '</span></div>' +
        '<div class="br">Leistungsindex ' + HR.perfIndex(c.id) + '</div>' +
        '<div class="bars">' + bar('Tempo', (st.top - 180) / 190) + bar('Antritt', st.acc) + bar('Grip', st.grip) + bar('NOS', st.nos) + '</div>' +
        '</div></div></div>';
      HR.UPGRADES.forEach(u => {
        const lvl = up[u.id] || 0;
        const cost = HR.upgradeCost(c, lvl);
        h += '<div class="card" style="padding:0 14px"><div class="upg">' +
          '<div style="font-size:22px;width:30px">' + u.icon + '</div>' +
          '<div class="un">' + u.name + '<small>' + u.desc + '</small>' +
          '<div class="pips">' + pips(lvl) + '</div></div>' +
          (lvl >= HR.MAX_UP ? '<button class="buy" disabled>Max</button>'
            : '<button class="buy" data-a="upg" data-v="' + u.id + '" ' + (d.credits < cost ? 'disabled' : '') + '>' + U.fmtNum(cost) + ' ₡</button>') +
          '</div></div>';
      });
      h += '<p class="hint">Höherer Leistungsindex heisst auch stärkere Gegner – die KI rechnet deine Ausbaustufe mit ein.</p>';
    } else {
      const c = HR.carById(d.car);
      const cols = ['#ef4b3c', '#ff6a1f', '#ffcc33', '#39d98a', '#22e6c8', '#2f9dff', '#7b5cff', '#ff2e63', '#f2ede0', '#1b1e2b'];
      h += '<div class="card center"><canvas class="cc big" data-car="' + c.id + '" style="width:200px;height:126px"></canvas>' +
        '<div class="nm" style="font-weight:900;font-size:16px">' + c.name + '</div></div><div class="card">' +
        '<div class="sect" style="margin-top:0">Lackfarbe</div><div style="display:flex;flex-wrap:wrap;gap:10px">';
      cols.forEach(col => {
        h += '<button data-a="paint" data-v="' + col + '" style="width:46px;height:46px;border-radius:14px;border:2px solid ' +
          ((d.cars[c.id].col || c.col) === col ? '#fff' : 'rgba(255,255,255,.15)') + ';background:' + col + '"></button>';
      });
      h += '</div></div>';
    }
    this.paint(h);
    /* Fahrzeugbilder nachzeichnen */
    this.root.querySelectorAll('canvas.cc').forEach(cv => {
      const c = HR.carById(cv.getAttribute('data-car'));
      const col = (d.cars[c.id] && d.cars[c.id].col) || c.col;
      const sp = HR.Art.car(col, c.body, 0, false);
      cv.width = sp.w; cv.height = sp.h;
      cv.getContext('2d').drawImage(sp.c, 0, 0);
    });
  },

  /* ------------------------------------------------ Einstellungen */
  settings() {
    const s = S().settings;
    const sw = (id, on) => '<button class="sw ' + (on ? 'on' : '') + '" data-a="set" data-v="' + id + '"></button>';
    const seg = (id, val, opts) => '<div class="seg">' + opts.map(o =>
      '<button class="' + (val === o[0] ? 'on' : '') + '" data-a="set" data-v="' + id + ':' + o[0] + '">' + o[1] + '</button>').join('') + '</div>';
    this.paint(
      '<div class="topbar"><button class="back" data-a="home">‹</button><h2>Einstellungen</h2></div>' +
      '<div class="card">' +
      '<div class="row"><div class="lbl">Ton<small>Motor, Wind und Effekte</small></div>' + sw('sound', s.sound) + '</div>' +
      '<div class="row"><div class="lbl">Automatisches Gas<small>Aus: eigene Gas-Taste</small></div>' + sw('autoGas', s.autoGas) + '</div>' +
      '<div class="row"><div class="lbl">Fahrhilfe<small>Weniger Fliehkraft, mildere Wiese</small></div>' + sw('assist', s.assist) + '</div>' +
      '<div class="row"><div class="lbl">Gummiband-KI<small>Hält das Feld zusammen</small></div>' + sw('rubber', s.rubber) + '</div>' +
      '</div>' +
      '<div class="card">' +
      '<div class="row"><div class="lbl">Gegner<small>Adaptiv folgt deinen Ergebnissen (Stufe ' + Math.round(S().skill) + ')</small></div>' +
      seg('aiBoost', String(s.aiBoost || 0), [['0', 'Adaptiv'], ['14', 'Hart'], ['28', 'Brutal']]) + '</div>' +
      '<div class="row"><div class="lbl">Lenkung</div>' + seg('steer', s.steer, [['pad', 'Feld'], ['tilt', 'Neigen']]) + '</div>' +
      '<div class="row"><div class="lbl">Grafik<small>Automatisch passt sich der Bildrate an</small></div>' +
      seg('quality', s.quality, [['low', 'Sparsam'], ['auto', 'Auto'], ['high', 'Hoch']]) + '</div>' +
      '</div>' +
      '<div class="card"><div class="sect" style="margin-top:0">Statistik</div>' +
      row('Rennen', S().stats.races) + row('Siege', S().stats.wins) +
      row('Gefahrene Strecke', S().stats.km.toFixed(1) + ' km') +
      row('Höchsttempo', Math.round(S().stats.topSpeed) + ' km/h') +
      row('Knappe Überholmanöver', S().stats.nearMiss) +
      row('Adaptive KI-Stufe', Math.round(S().skill) + ' / 100') +
      '</div>' +
      '<button class="btn ghost small" data-a="reset"><span class="ico">🗑️</span><span class="txt">Profil zurücksetzen</span></button>' +
      '<p class="hint center">Horizon Rush läuft vollständig auf deinem Gerät. Keine Konten, keine Server, keine Werbung.</p>'
    );
  },

  /* ------------------------------------------------ Pause */
  pause() {
    if (HR.Race.state === 'idle' || HR.Race.state === 'finished') return;
    HR.Race.pause();
    this.paint(
      '<div style="height:12vh"></div><div class="brand"><h1 style="font-size:34px">Pause</h1></div>' +
      '<button class="btn primary" data-a="resume"><span class="ico">▶️</span><span class="txt">Weiterfahren</span></button>' +
      '<button class="btn" data-a="restart"><span class="ico">🔄</span><span class="txt">Neu starten</span></button>' +
      '<button class="btn ghost" data-a="quit"><span class="ico">✕</span><span class="txt">Rennen abbrechen</span></button>',
      'sheet');
  },

  /* ------------------------------------------------ Ergebnis */
  result(res) {
    const cfg = this.lastCfg;
    const d = S();
    const stars = starsFor(cfg, res);
    const skillDelta = HR.updateSkill(cfg, res);

    /* Belohnung */
    let cr, xp;
    const f = stars === 3 ? 1 : stars === 2 ? 0.68 : 0.42;
    if (cfg.mode === 'marathon') {
      cr = Math.round(res.score * 0.55 + res.km * 60);
      xp = Math.round(res.score * 0.05 + res.km * 8);
    } else if (cfg.reward) {
      cr = Math.round(cfg.reward.cr * f + res.score * 0.10);
      xp = Math.round(cfg.reward.xp * f + res.score * 0.02);
    } else {
      /* Schnellrennen und Tagesroute */
      cr = Math.round(1400 * f + res.score * 0.12);
      xp = Math.round(70 * (stars === 3 ? 1 : 0.6) + res.score * 0.02);
    }

    /* Bestwerte / Sterne merken */
    let isBest = false;
    if (cfg.eventId) {
      const old = d.events[cfg.eventId] || { stars: 0, best: 0 };
      if (stars > old.stars) { old.stars = stars; }
      const val = cfg.mode === 'trap' ? res.trapTotal : res.time;
      if (!old.best || (cfg.mode === 'trap' ? val > old.best : val < old.best)) { old.best = val; isBest = true; }
      d.events[cfg.eventId] = old;
      /* Wiederholung bringt weniger Geld */
      if (old.played) { cr = Math.round(cr * 0.35); xp = Math.round(xp * 0.35); }
      old.played = true;
    }
    if (cfg.mode === 'marathon') {
      if (!d.best.marathon || res.score > d.best.marathon) { d.best.marathon = res.score; isBest = true; }
    }
    if (cfg.daily) {
      const k = dailyKey();
      if (!d.daily[k] || res.score > d.daily[k]) { d.daily[k] = res.score; isBest = true; }
    }

    d.stats.races++;
    if (res.place === 1 && cfg.rivalCount) d.stats.wins++;
    d.stats.km += res.km;
    d.stats.nearMiss += res.nearMiss;
    d.stats.drift += res.drift;
    if (res.topSpeed > d.stats.topSpeed) d.stats.topSpeed = res.topSpeed;
    HR.Save.addCredits(cr);
    const lvlUp = HR.Save.addXP(xp);
    HR.Save.save();
    HR.Audio.ok();

    /* Anzeige */
    const hero = cfg.mode === 'marathon'
      ? '<div class="place">' + res.km.toFixed(1) + '<small> km</small></div><div class="ttl">Etappe ' + (res.stage + 1) + ' erreicht</div>'
      : cfg.mode === 'time'
        ? '<div class="place">' + U.fmtTime(res.time) + '</div><div class="ttl">Zeitfahren</div>'
        : cfg.mode === 'trap'
          ? '<div class="place">' + res.trapTotal + '<small> km/h</small></div><div class="ttl">Blitzer gesamt</div>'
          : '<div class="place">' + res.place + '<small>. von ' + res.of + '</small></div><div class="ttl">' + (res.place === 1 ? 'Sieg!' : 'Zielankunft') + '</div>';

    let h = '<div class="result-hero">' + hero +
      (cfg.mode === 'marathon' ? '' : '<div class="starrow">' + starStr(stars, 3) + '</div>') + '</div>';

    h += '<div class="card">' +
      rline('Belohnung', U.fmtNum(cr) + ' ₡', 'gold') +
      rline('Erfahrung', '+' + xp + ' XP', 'neon') +
      (lvlUp ? rline('Stufenaufstieg', 'Stufe ' + d.level + ' 🎉', 'gold') : '') +
      rline('Punkte', U.fmtNum(res.score)) +
      rline('Höchsttempo', res.topSpeed + ' km/h') +
      rline('Knappe Manöver', res.nearMiss) +
      (res.drift ? rline('Driftzeit', res.drift + ' s') : '') +
      (skillDelta ? rline('KI-Stufe', Math.round(d.skill) + ' (' + (skillDelta > 0 ? '+' : '') + skillDelta + ')', 'neon') : '') +
      (isBest ? rline('Neuer Bestwert', '⭐', 'gold') : '') +
      '</div>';

    if (cfg.rivalCount && res.standings.length > 1) {
      h += '<div class="sect">Endstand</div><div class="card"><table class="stand">';
      res.standings.forEach((s2, i) => {
        h += '<tr class="' + (s2.me ? 'me' : '') + '"><td class="p">' + (i + 1) + '</td>' +
          '<td><span class="dot" style="background:' + s2.col + '"></span>' + s2.name + '</td>' +
          '<td class="t">' + (i === 0 ? U.fmtTime(s2.t) : '+' + (s2.t - res.standings[0].t).toFixed(2)) + '</td></tr>';
      });
      h += '</table></div>';
    }

    h += '<button class="btn primary" data-a="again"><span class="ico">🔄</span><span class="txt">Nochmal</span></button>';
    const nxt = this.nextEvent(cfg);
    if (nxt) h += '<button class="btn violet" data-a="event" data-v="' + nxt.id + '"><span class="ico">➡️</span><span class="txt">Nächstes Event<small>' + nxt.name + '</small></span></button>';
    h += '<button class="btn ghost" data-a="home"><span class="ico">🏠</span><span class="txt">Zum Menü</span></button>';
    this.paint(h, 'sheet');
  },

  nextEvent(cfg) {
    if (!cfg.eventId) return null;
    const di = DISTRICTS.findIndex(x => x.id === cfg.district);
    if (di < 0) return null;
    const evs = eventsOf(DISTRICTS[di], di);
    if (cfg.idx + 1 < evs.length) return evs[cfg.idx + 1];
    const nd = DISTRICTS[di + 1];
    if (nd && districtUnlocked(nd)) return eventsOf(nd, di + 1)[0];
    return null;
  },

  /* ------------------------------------------------ Rennen starten */
  launch(cfg) {
    this.lastCfg = cfg;
    if (cfg.mode === 'time' && !cfg.targetTime) cfg.targetTime = targetTimeFor(cfg);
    this.close();
    cfg.carId = S().car;
    HR.Race.start(cfg);
    setTimeout(() => this.checkOrientation(), 60);
  },

  /* ------------------------------------------------ Aktionen */
  actions: {
    startIntro() { S().seenIntro = true; HR.Save.save(); this.home(); },
    home() { HR.Race.stop(); this.home(); },
    career() { this.career(); },
    district(id) { this.district(id); },
    locked() { HR.Race.state === 'idle'; },
    garage(tab) { this.garage(tab); },
    settings() { this.settings(); },
    resume() { this.close(); HR.Race.resume(); },
    restart() { this.launch(this.lastCfg); },
    quit() { HR.Race.stop(); this.home(); },
    again() { this.launch(this.lastCfg); },

    quick() {
      const skill = S().skill / 100;
      this.launch({
        mode: 'sprint', quick: true, name: 'Schnellrennen',
        seed: 'quick-' + Date.now(),
        laps: 1, rivalCount: 5, segments: 2800,
        difficulty: U.clamp(0.25 + skill * 0.55, 0, 1), traffic: 0.5, biomes: null
      });
    },
    marathon() {
      this.launch({
        mode: 'marathon', name: 'Marathon', seed: 'mara-' + Date.now(),
        laps: 9999, rivalCount: 0, segments: 1800,
        difficulty: U.clamp(0.22 + S().skill / 100 * 0.4, 0, 1),
        traffic: 0.75, timeLimit: 62, biomes: null
      });
    },
    daily() {
      const k = dailyKey();
      this.launch({
        mode: 'time', daily: true, name: 'Tagesroute ' + k,
        seed: 'daily-' + k, laps: 1, rivalCount: 0, segments: 2400,
        difficulty: 0.55, traffic: 0.4, biomes: null
      });
    },
    event(id) {
      const dId = id.split('-')[0];
      const di = DISTRICTS.findIndex(x => x.id === dId);
      const e = eventsOf(DISTRICTS[di], di).find(x => x.id === id);
      if (!e) return;
      this.launch(Object.assign({ eventId: e.id }, e));
    },

    pick(id) { S().car = id; HR.Save.save(); this.garage('cars'); },
    buy(id) { if (HR.Save.buyCar(id)) { HR.Audio.ok(); this.garage('cars'); } },
    upg(uid) {
      const d = S(), c = HR.carById(d.car), up = d.cars[c.id].up;
      const lvl = up[uid] || 0;
      if (lvl >= HR.MAX_UP) return;
      const cost = HR.upgradeCost(c, lvl);
      if (d.credits < cost) return;
      d.credits -= cost; up[uid] = lvl + 1; HR.Save.save(); HR.Audio.ok();
      this.garage('tune');
    },
    paint(col) { const d = S(); d.cars[d.car].col = col; HR.Save.save(); this.garage('paint'); },

    set(v) {
      const s = S().settings;
      if (v.indexOf(':') > 0) {
        const p = v.split(':');
        /* Zahlenwerte auch als Zahl ablegen, sonst rechnet die KI mit Text */
        s[p[0]] = /^-?\d+$/.test(p[1]) ? parseInt(p[1], 10) : p[1];
      } else s[v] = !s[v];
      HR.Save.save();
      if (s.sound) HR.Audio.setMuted(false); else HR.Audio.setMuted(true);
      HR.Race.resize();
      this.settings();
    },
    reset() {
      if (confirm('Profil wirklich löschen? Alle Fahrzeuge, Sterne und Bestwerte gehen verloren.')) {
        HR.Save.reset(); this.home();
      }
    }
  }
};

/* ---------------------------------------------------------- kleine Helfer */
function starStr(n, max) {
  let s = '';
  for (let i = 0; i < max; i++) s += i < n ? '<b>★</b>' : '★';
  return s;
}
function bar(label, v) {
  return '<div class="bar"><span>' + label + '</span><i><b style="width:' + Math.round(U.clamp(v, 0, 1) * 100) + '%"></b></i></div>';
}
function pips(n) { let s = ''; for (let i = 0; i < HR.MAX_UP; i++) s += '<i class="' + (i < n ? 'on' : '') + '"></i>'; return s; }
function row(k, v) { return '<div class="rline"><span>' + k + '</span><b>' + v + '</b></div>'; }
function rline(k, v, cls) { return '<div class="rline"><span>' + k + '</span><b class="' + (cls || '') + '">' + v + '</b></div>'; }
function dailyKey() { const d = new Date(); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); }
function dailyLabel() {
  const k = dailyKey(), b = S().daily[k];
  return b ? ('heute schon gefahren · ' + U.fmtNum(b) + ' Pkt') : 'heute noch offen';
}

let booted = false;
function boot() { if (booted) return; booted = true; UI.boot(); }
if (document.readyState === 'loading') window.addEventListener('DOMContentLoaded', boot);
else boot();

})(window.HR);
