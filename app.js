/* =====================================================================
   Velo Navi Schweiz  –  app.js
   Routing: BRouter (OSM) · Karten: swisstopo · Geocoding: geo.admin.ch
   Eigenes physikbasiertes Zeitmodell (Steigung + Velo-Typ + Kalibrierung)
   ===================================================================== */
'use strict';

/* ---------------------------------------------------------------------
   0)  KONSTANTEN
   ------------------------------------------------------------------- */
const BROUTER = 'https://brouter.de/brouter';
const GEOCODE = 'https://api3.geo.admin.ch/rest/services/api/SearchServer';

// Velo-Typ-Profile fürs Physikmodell
const BIKES = {
  velo:    { label:'Velo',      mass:85, CdA:0.40, motorW:0,   cutoff:Infinity, maxKmh:62, emoji:'🚲' },
  ebike25: { label:'E-Bike 25', mass:91, CdA:0.46, motorW:250, cutoff:25/3.6,   maxKmh:55, emoji:'⚡' },
  ebike45: { label:'E-Bike 45', mass:93, CdA:0.46, motorW:480, cutoff:45/3.6,   maxKmh:58, emoji:'⚡⚡' },
};

// BRouter-Profile + Anzeige
const STYLES = {
  'trekking':  { label:'Trekking / Stadt', maxKmh:50, color:'#4a9eff' },
  'road-bike': { label:'Rennvelo',          maxKmh:65, color:'#e63946' },
  'gravel':    { label:'Gravel',            maxKmh:45, color:'#c9a36a' },
  'mtb':       { label:'MTB Trail',         maxKmh:38, color:'#f4a261' },
};
const ROUTE_COLORS = ['#e63946','#2a9d8f','#4a9eff','#f4a261','#9b59b6','#43b581'];

const G = 9.81, RHO = 1.2, ETA = 0.97;   // Erdbeschl., Luftdichte, Antriebs-Wirkungsgrad

/* ---------------------------------------------------------------------
   1)  STATE
   ------------------------------------------------------------------- */
const state = {
  start:null, end:null,          // {lat,lon,label}
  bike:'velo',
  style:'trekking',
  multiStyle:false,
  power:130,
  routes:[],                     // berechnete Routen
  activeRoute:null,
  layers:[],                     // Leaflet-Polylines
  calib: loadCalib(),            // Tempo-Kalibrierung pro Velo-Typ
};

function loadCalib(){
  try{ return Object.assign({velo:1,ebike25:1,ebike45:1}, JSON.parse(localStorage.getItem('velonavi_calib')||'{}')); }
  catch(e){ return {velo:1,ebike25:1,ebike45:1}; }
}
function saveCalib(){ localStorage.setItem('velonavi_calib', JSON.stringify(state.calib)); updateCalibInfo(); }

/* ---------------------------------------------------------------------
   2)  KARTE
   ------------------------------------------------------------------- */
const map = L.map('map',{ zoomControl:true, center:[46.8,8.23], zoom:8 });

const swisstopo = L.tileLayer(
  'https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.pixelkarte-farbe/default/current/3857/{z}/{x}/{y}.jpeg',
  { attribution:'© swisstopo', maxZoom:18 });
const swissimage = L.tileLayer(
  'https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.swissimage/default/current/3857/{z}/{x}/{y}.jpeg',
  { attribution:'© swisstopo', maxZoom:18 });
const osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
  { attribution:'© OpenStreetMap', maxZoom:19 });

swisstopo.addTo(map);
L.control.layers(
  { 'swisstopo Karte':swisstopo, 'Satellit (swissimage)':swissimage, 'OpenStreetMap':osm },
  null, {position:'topright'}
).addTo(map);

let startMarker=null, endMarker=null;
function makeIcon(emoji){ return L.divIcon({className:'',html:`<div class="pin">${emoji}</div>`,iconSize:[30,30],iconAnchor:[15,28]}); }

// Punkt auf Karte setzen: 1 Sekunde gedrückt halten (Long-Press) statt einfachem Klick,
// damit beim Verschieben/Auswählen nicht versehentlich ein Standort gesetzt wird.
function placeFromMap(latlng){
  const p = { lat:+latlng.lat.toFixed(6), lon:+latlng.lng.toFixed(6), label:`Kartenpunkt (${latlng.lat.toFixed(4)}, ${latlng.lng.toFixed(4)})` };
  if(!state.start || (state.start && state.end)){ setPoint('start',p,true); clearPoint('end'); }
  else { setPoint('end',p,true); }
  // kleines Feedback
  setStatus(state.end?'Ziel gesetzt.':'Start gesetzt – jetzt Ziel wählen.','');
}

// Long-Press-Erkennung (Maus + Touch), ~700 ms, mit Bewegungs-Abbruch
let lpTimer=null, lpStart=null, lpRing=null;
function lpClear(){ if(lpTimer){clearTimeout(lpTimer);lpTimer=null;} if(lpRing){lpRing.remove();lpRing=null;} }
map.on('mousedown', e=>{
  if(typeof nav!=='undefined' && nav.active) return;     // im Navi-Modus keine Punkte setzen
  if(e.originalEvent && e.originalEvent.button!==0) return;
  // iOS-Auswahl-/Kopiermenü beim Halten unterdrücken
  if(e.originalEvent && e.originalEvent.preventDefault) e.originalEvent.preventDefault();
  lpStart=e.latlng; lpRing=makeLpRing(e.containerPoint);
  lpTimer=setTimeout(()=>{ lpClear(); placeFromMap(lpStart); navigator.vibrate&&navigator.vibrate(15); }, 450);
});
map.on('mouseup', lpClear);
map.on('movestart dragstart zoomstart', lpClear);
map.on('mousemove', e=>{
  if(lpStart && lpStart.distanceTo(e.latlng)>12) lpClear(); // bei Bewegung abbrechen (Karte verschieben)
});
function makeLpRing(pt){
  const el=document.createElement('div'); el.className='lp-ring';
  el.style.left=pt.x+'px'; el.style.top=pt.y+'px';
  document.getElementById('map').appendChild(el); return el;
}

function setPoint(which,p,fromMap){
  state[which]=p;
  const input = document.getElementById(which==='start'?'startInput':'endInput');
  if(input && fromMap) input.value = p.label;
  const icon = makeIcon(which==='start'?'🟢':'🔴');
  if(which==='start'){
    if(startMarker) startMarker.setLatLng([p.lat,p.lon]); else { startMarker=L.marker([p.lat,p.lon],{icon,draggable:true}).addTo(map); bindDrag(startMarker,'start'); }
  } else {
    if(endMarker) endMarker.setLatLng([p.lat,p.lon]); else { endMarker=L.marker([p.lat,p.lon],{icon,draggable:true}).addTo(map); bindDrag(endMarker,'end'); }
  }
}
function bindDrag(marker,which){
  marker.on('dragend',()=>{ const ll=marker.getLatLng(); state[which]={lat:+ll.lat.toFixed(6),lon:+ll.lng.toFixed(6),label:`Kartenpunkt (${ll.lat.toFixed(4)}, ${ll.lng.toFixed(4)})`};
    document.getElementById(which==='start'?'startInput':'endInput').value=state[which].label; });
}
function clearPoint(which){
  state[which]=null;
  if(which==='start'&&startMarker){map.removeLayer(startMarker);startMarker=null;}
  if(which==='end'&&endMarker){map.removeLayer(endMarker);endMarker=null;}
}

/* ---------------------------------------------------------------------
   3)  GEOCODING (Ortssuche Schweiz)
   ------------------------------------------------------------------- */
// Geokodierung für freien Text (gibt beste Treffer zurück)
async function geocode(q){
  const url=`${GEOCODE}?searchText=${encodeURIComponent(q)}&type=locations&sr=4326&limit=8&origins=zipcode,gg25,address`;
  const r=await fetch(url); const d=await r.json();
  return (d.results||[]).map(x=>({
    label:String(x.attrs.label).replace(/<\/?[^>]+>/g,''),
    lat:x.attrs.lat, lon:x.attrs.lon, detail:x.attrs.detail||'' }));
}

function setupSearch(inputId, suggestId, which){
  const input=document.getElementById(inputId), box=document.getElementById(suggestId);
  let timer=null, items=[], sel=-1, chosenLabel=null;

  input.addEventListener('input',()=>{
    clearTimeout(timer); chosenLabel=null; const q=input.value.trim();
    if(q.length<2){ box.classList.remove('show'); return; }
    timer=setTimeout(async()=>{
      try{ items=await geocode(q); renderSuggest(); }
      catch(e){ box.classList.remove('show'); }
    },220);
  });
  input.addEventListener('keydown',e=>{
    if(e.key==='Enter'){
      e.preventDefault();
      if(box.classList.contains('show') && sel>=0){ choose(items[sel]); }
      else { commitText(); }                       // Enter ohne Auswahl → Text übernehmen
      input.blur(); return;
    }
    if(!box.classList.contains('show'))return;
    if(e.key==='ArrowDown'){sel=Math.min(sel+1,items.length-1);hi();e.preventDefault();}
    else if(e.key==='ArrowUp'){sel=Math.max(sel-1,0);hi();e.preventDefault();}
    else if(e.key==='Escape'){box.classList.remove('show');}
  });
  // Wichtigster Fix: Beim Verlassen des Feldes Text automatisch übernehmen,
  // auch wenn kein Vorschlag angetippt wurde.
  input.addEventListener('blur',()=>{ setTimeout(()=>{ if(!box.matches(':hover')) commitText(); }, 180); });

  document.addEventListener('click',e=>{ if(!box.contains(e.target)&&e.target!==input) box.classList.remove('show'); });

  function renderSuggest(){
    if(!items.length){box.classList.remove('show');return;}
    sel=-1;
    box.innerHTML=items.map((it,i)=>`<li data-i="${i}">${it.label}</li>`).join('');
    box.querySelectorAll('li').forEach(li=>li.onclick=()=>choose(items[+li.dataset.i]));
    box.classList.add('show');
  }
  function hi(){ box.querySelectorAll('li').forEach((li,i)=>li.classList.toggle('sel',i===sel)); }
  function choose(it){
    chosenLabel=it.label; input.value=it.label;
    setPoint(which,{lat:it.lat,lon:it.lon,label:it.label},false);
    box.classList.remove('show');
    map.setView([it.lat,it.lon],13);
  }
  // Übernimmt den getippten Text: nutzt den ersten Geocoding-Treffer
  async function commitText(){
    box.classList.remove('show');
    const q=input.value.trim();
    if(q.length<2) return;
    if(chosenLabel===input.value) return;          // schon gesetzt
    // bereits geladene Treffer nutzen, sonst frisch holen
    let list=items;
    if(!list.length || (list[0]&&list[0].label.toLowerCase().indexOf(q.toLowerCase().slice(0,3))<0)){
      try{ list=await geocode(q); }catch(e){ list=[]; }
    }
    if(list.length){ const it=list[0]; chosenLabel=it.label; input.value=it.label;
      setPoint(which,{lat:it.lat,lon:it.lon,label:it.label},false);
      if(!(state.start&&state.end)) map.setView([it.lat,it.lon],13);
      setStatus(which==='start'?'Start gesetzt.':'Ziel gesetzt.','');
    } else {
      setStatus(`Kein Ort für „${q}" gefunden – bitte genauer eingeben.`,'err');
    }
  }
  // nach aussen, damit Tausch/Enter zuverlässig committen kann
  input._commit = commitText;
}
setupSearch('startInput','startSuggest','start');
setupSearch('endInput','endSuggest','end');

/* ---------------------------------------------------------------------
   4)  STEUERELEMENTE
   ------------------------------------------------------------------- */
function bindSeg(containerId, key, cb){
  document.querySelectorAll(`#${containerId} button`).forEach(b=>{
    b.onclick=()=>{
      document.querySelectorAll(`#${containerId} button`).forEach(x=>x.classList.remove('active'));
      b.classList.add('active'); state[key]=b.dataset.val; if(cb)cb();
    };
  });
}
bindSeg('bikeType','bike',()=>{ updateCalibInfo(); if(state.routes.length) recomputeAll(); });
bindSeg('rideStyle','style');

document.getElementById('multiStyle').onchange=e=>state.multiStyle=e.target.checked;
const powerEl=document.getElementById('power');
powerEl.value=localStorage.getItem('velonavi_power')||130; state.power=+powerEl.value;
document.getElementById('powerVal').textContent=state.power;
powerEl.oninput=()=>{ state.power=+powerEl.value; document.getElementById('powerVal').textContent=state.power;
  localStorage.setItem('velonavi_power',state.power); };
powerEl.onchange=()=>{ if(state.routes.length) recomputeAll(); };

document.getElementById('swapBtn').onclick=()=>{
  const s=state.start,e=state.end;
  document.getElementById('startInput').value=e?e.label:'';
  document.getElementById('endInput').value=s?s.label:'';
  clearPoint('start');clearPoint('end');
  if(e)setPoint('start',e,false); if(s)setPoint('end',s,false);
};
document.getElementById('clearBtn').onclick=()=>{
  clearPoint('start');clearPoint('end');
  document.getElementById('startInput').value='';document.getElementById('endInput').value='';
  clearRoutes(); document.getElementById('results').innerHTML='';
  document.getElementById('elevation-panel').classList.add('hidden');
};
document.getElementById('routeBtn').onclick=computeRoutes;

function updateCalibInfo(){
  const f=state.calib[state.bike];
  const el=document.getElementById('calibInfo');
  if(Math.abs(f-1)<0.02){ el.textContent='Tempo-Kalibrierung: Standard (gib nach Fahrten Feedback)'; }
  else { const pct=Math.round((f-1)*100); el.textContent=`Dein Tempo (${BIKES[state.bike].label}): ${pct>0?'+':''}${pct}% ggü. Standard`; }
}
updateCalibInfo();

/* ---------------------------------------------------------------------
   5)  PHYSIK – Geschwindigkeit pro Segment
   ------------------------------------------------------------------- */
// Rollwiderstandskoeffizient je nach Untergrund-Kategorie
const CRR = { road:0.005, cycleway:0.006, gravel:0.012, forest:0.018, trail:0.028 };

// Löst die Leistungsgleichung   P = v*(Crr*m*g*cosθ + m*g*sinθ + 0.5*ρ*CdA*v²)/η  nach v
function solveSpeed(power, grade, mass, CdA, crr){
  const theta=Math.atan(grade), c=Math.cos(theta), s=Math.sin(theta);
  const fRoll=crr*mass*G*c, fGrav=mass*G*s;          // s<0 bergab → hilft
  // f(v) = (0.5ρ CdA v³ + (fRoll+fGrav) v)/η - P   ;  monoton steigend in v
  const f=v=>(0.5*RHO*CdA*v*v*v + (fRoll+fGrav)*v)/ETA - power;
  let lo=0.1, hi=40;                                  // m/s
  if(f(lo)>0) return lo;                              // selbst Minimaltempo braucht zu viel → schiebt
  for(let i=0;i<60;i++){ const mid=(lo+hi)/2; if(f(mid)>0)hi=mid; else lo=mid; }
  return (lo+hi)/2;
}

// Geschwindigkeit (m/s) für ein Segment unter Berücksichtigung Velo-Typ + Motor + Kalibrierung
function segmentSpeed(grade, crr){
  const b=BIKES[state.bike];
  const Prider=state.power;
  let v;
  if(b.motorW>0){
    const vm=solveSpeed(Prider+b.motorW, grade, b.mass, b.CdA, crr);   // mit Motor
    if(vm<=b.cutoff){ v=vm; }
    else { const vn=solveSpeed(Prider, grade, b.mass, b.CdA, crr); v=Math.max(vn, b.cutoff); }
  } else {
    v=solveSpeed(Prider, grade, b.mass, b.CdA, crr);
  }
  // Sicherheits-/Realismus-Limits
  const styleMax=(STYLES[state.style]?.maxKmh||50)/3.6;
  v=Math.min(v, b.maxKmh/3.6, styleMax);
  v=Math.max(v, 0.9);                                  // ~3.2 km/h schieben am steilsten
  v*=state.calib[state.bike];                          // persönliche Kalibrierung
  return v;
}

// Haversine (m)
function hav(a,b){
  const R=6371000, dLat=(b[1]-a[1])*Math.PI/180, dLon=(b[0]-a[0])*Math.PI/180;
  const la1=a[1]*Math.PI/180, la2=b[1]*Math.PI/180;
  const x=Math.sin(dLat/2)**2 + Math.cos(la1)*Math.cos(la2)*Math.sin(dLon/2)**2;
  return 2*R*Math.asin(Math.sqrt(x));
}

/* ---------------------------------------------------------------------
   6)  ROUTE → Kennzahlen (Zeit, Höhe, Beschreibung)
   ------------------------------------------------------------------- */
function classifyWay(tags){
  const t=(tags||'').toLowerCase();
  if(/highway=path/.test(t) && (/sac_scale|mtb|trail_visibility|surface=ground|surface=dirt|surface=rock/.test(t) || /tracktype=grade[45]/.test(t))) return 'trail';
  if(/highway=track/.test(t)){
    if(/surface=(gravel|fine_gravel|compacted|pebblestone)/.test(t)||/tracktype=grade[12]/.test(t)) return 'gravel';
    return 'forest';
  }
  if(/highway=path|highway=footway|highway=bridleway/.test(t)){
    if(/surface=(asphalt|paved|concrete)/.test(t)) return 'cycleway';
    return 'forest';
  }
  if(/highway=cycleway/.test(t)) return 'cycleway';
  if(/surface=(gravel|fine_gravel|compacted|unpaved|pebblestone)/.test(t)) return 'gravel';
  return 'road';
}
const CAT_LABEL={road:'Strasse',cycleway:'Veloweg',gravel:'Schotter/Feldweg',forest:'Wald-/Naturweg',trail:'Trail/Singletrack'};
const CAT_TAGCLASS={road:'road',cycleway:'road',gravel:'gravel',forest:'forest',trail:'trail'};

// Verarbeitet eine BRouter-GeoJSON-Feature → Routenobjekt mit Zeit/Höhe/Beschreibung
function analyzeRoute(feature, name, color){
  const coords=feature.geometry.coordinates;                 // [lon,lat,ele]
  const props=feature.properties;
  const dist=+props['track-length']||0;                      // m
  const ascend=+props['filtered ascend']||0;                 // m

  // --- Untergrund-Klassifikation aus messages ---
  const cat={road:0,cycleway:0,gravel:0,forest:0,trail:0};
  const msgs=props.messages;
  if(msgs && msgs.length>1){
    const head=msgs[0];
    const iDist=head.indexOf('Distance'), iTags=head.indexOf('WayTags');
    let cumulative=null;
    if(iDist>=0){
      const vals=msgs.slice(1).map(m=>+m[iDist]);
      cumulative = vals.every((v,i)=>i===0||v>=vals[i-1]) && vals[vals.length-1]>dist*0.8;
    }
    let prev=0;
    for(let i=1;i<msgs.length;i++){
      let d=+msgs[i][iDist]; if(cumulative){const cur=d; d=cur-prev; prev=cur;}
      if(!(d>0))continue;
      cat[classifyWay(msgs[i][iTags])]+=d;
    }
  }
  const catTotal=Object.values(cat).reduce((a,b)=>a+b,0)||1;

  // --- Zeit & Höhe aus Geometrie (feinkörnig) ---
  let time=0, descend=0, maxGrade=0, totalH=0;
  // pro Segment Untergrund grob aus dominanter Kategorie ableiten:
  const domCat=Object.entries(cat).sort((a,b)=>b[1]-a[1])[0][0];
  const baseCrr=CRR[domCat]||CRR.road;
  for(let i=1;i<coords.length;i++){
    const a=coords[i-1], b=coords[i];
    const d=hav(a,b); if(d<0.5)continue;
    const dh=(b[2]||0)-(a[2]||0);
    const grade=Math.max(-0.30,Math.min(0.30,dh/d));
    if(grade>0)totalH+=dh; if(dh<0)descend-=dh;
    if(grade>maxGrade)maxGrade=grade;
    time += d/segmentSpeed(grade, baseCrr);
  }
  time*=1.04;  // kleiner Aufschlag für Halte/Kreuzungen

  // --- Beschreibung bauen ---
  const parts=Object.entries(cat).filter(([k,v])=>v/catTotal>0.08)
      .sort((a,b)=>b[1]-a[1])
      .map(([k,v])=>`${Math.round(v/catTotal*100)}% ${CAT_LABEL[k]}`);
  const flat = ascend/(dist/1000); // Höhenmeter pro km
  let terrain = flat<6?'flach':flat<14?'hügelig':flat<25?'bergig':'sehr bergig';
  const desc = `Überwiegend ${parts.slice(0,3).join(', ')||'gemischt'}. Profil: ${terrain} (${Math.round(ascend)} hm ↑, ${Math.round(descend)} hm ↓, max. ${Math.round(maxGrade*100)}% Steigung).`;

  const tags=Object.entries(cat).filter(([k,v])=>v/catTotal>0.12).sort((a,b)=>b[1]-a[1])
      .map(([k])=>({label:CAT_LABEL[k],cls:CAT_TAGCLASS[k]}));

  return { name, color, coords, dist, ascend, descend, maxGrade, time, desc, tags, brouterTime:+props['total-time']||0 };
}

/* ---------------------------------------------------------------------
   7)  ROUTING-ABFRAGE
   ------------------------------------------------------------------- */
async function brouterFetch(profile, altIdx){
  const ll=`${state.start.lon},${state.start.lat}|${state.end.lon},${state.end.lat}`;
  const url=`${BROUTER}?lonlats=${ll}&profile=${profile}&alternativeidx=${altIdx}&format=geojson`;
  const r=await fetch(url);
  if(!r.ok) throw new Error('no route');
  const txt=await r.text();
  if(txt.trim().startsWith('{')===false) throw new Error('no route');
  const d=JSON.parse(txt);
  if(!d.features||!d.features.length) throw new Error('empty');
  return d.features[0];
}

async function computeRoutes(){
  // Falls noch Text in den Feldern steht, der nicht als Punkt übernommen wurde: jetzt nachholen
  const si=document.getElementById('startInput'), ei=document.getElementById('endInput');
  if(!state.start && si.value.trim().length>=2 && si._commit){ setStatus('Start wird gesucht …','work'); await si._commit(); }
  if(!state.end && ei.value.trim().length>=2 && ei._commit){ setStatus('Ziel wird gesucht …','work'); await ei._commit(); }
  if(!state.start||!state.end){ setStatus('Bitte Start und Ziel wählen (Ort eingeben oder Karte 1 Sek. gedrückt halten).','err'); return; }
  const btn=document.getElementById('routeBtn'); btn.disabled=true;
  setStatus('Routen werden berechnet …','work');
  clearRoutes(); state.routes=[]; document.getElementById('results').innerHTML='';

  // Welche Profile/Alternativen?
  const jobs=[];
  const main=state.style;
  jobs.push({profile:main,alt:0,name:STYLES[main].label});
  jobs.push({profile:main,alt:1,name:STYLES[main].label+' · Variante'});
  jobs.push({profile:main,alt:2,name:STYLES[main].label+' · Variante 2'});
  if(state.multiStyle){
    Object.keys(STYLES).filter(p=>p!==main).forEach(p=>jobs.push({profile:p,alt:0,name:STYLES[p].label}));
  }

  const out=[]; let ci=0;
  for(const job of jobs){
    try{
      const feat=await brouterFetch(job.profile,job.alt);
      const color=ROUTE_COLORS[ci%ROUTE_COLORS.length];
      const route=analyzeRoute(feat,job.name,color);
      // Duplikate (sehr ähnliche Länge) überspringen
      if(out.some(o=>Math.abs(o.dist-route.dist)<route.dist*0.02 && Math.abs(o.ascend-route.ascend)<15)) continue;
      route.profile=job.profile; out.push(route); ci++;
      setStatus(`${out.length} Route(n) gefunden …`,'work');
    }catch(e){ /* keine (weitere) Alternative – ignorieren */ }
    await new Promise(r=>setTimeout(r,120)); // sanft zum Server
  }

  if(!out.length){ setStatus('Keine Route gefunden. Liegen Start/Ziel auf befahrbarem Weg?','err'); btn.disabled=false; return; }
  out.sort((a,b)=>a.time-b.time);
  state.routes=out;
  renderRoutes();
  drawRoutes();
  selectRoute(0);
  setStatus(`${out.length} Route(n) – sortiert nach Fahrzeit.`,'');
  btn.disabled=false;
}

// Bei Änderung von Velo-Typ/Power: nur Zeit neu rechnen (keine neue Abfrage)
function recomputeAll(){
  state.routes.forEach(r=>{
    const feat={geometry:{coordinates:r.coords},properties:{'track-length':r.dist,'filtered ascend':r.ascend,messages:null}};
    // Zeit & Höhe neu (Untergrund-Crr aus bestehenden Tags grob)
    const domCat = r.tags[0]?.label||'';
    const crr = CRR[Object.keys(CAT_LABEL).find(k=>CAT_LABEL[k]===domCat)]||CRR.road;
    let time=0;
    for(let i=1;i<r.coords.length;i++){
      const a=r.coords[i-1],b=r.coords[i]; const d=hav(a,b); if(d<0.5)continue;
      const grade=Math.max(-0.30,Math.min(0.30,((b[2]||0)-(a[2]||0))/d));
      time+=d/segmentSpeed(grade,crr);
    }
    r.time=time*1.04;
  });
  state.routes.sort((a,b)=>a.time-b.time);
  renderRoutes(); drawRoutes();
  const idx=state.activeRoute?state.routes.indexOf(state.activeRoute):0;
  selectRoute(idx>=0?idx:0);
}

/* ---------------------------------------------------------------------
   8)  DARSTELLUNG ROUTEN
   ------------------------------------------------------------------- */
function fmtTime(s){ const m=Math.round(s/60); if(m<60)return m+' min'; return Math.floor(m/60)+' h '+(m%60)+' min'; }
function fmtKm(m){ return (m/1000).toFixed(m<10000?1:0)+' km'; }
function setStatus(t,cls){ const el=document.getElementById('status'); el.textContent=t; el.className='status '+(cls||''); }

function renderRoutes(){
  const box=document.getElementById('results');
  box.innerHTML=state.routes.map((r,i)=>`
    <div class="route-card${r===state.activeRoute?' active':''}" data-i="${i}">
      <div class="rc-bar" style="background:${r.color}"></div>
      <div class="rc-head"><span class="rc-name">${r.name}</span><span class="rc-time">${fmtTime(r.time)}</span></div>
      <div class="rc-stats"><span><b>${fmtKm(r.dist)}</b></span><span>↑ <b>${Math.round(r.ascend)} hm</b></span><span>↓ <b>${Math.round(r.descend)} hm</b></span><span>⌀ <b>${(r.dist/1000/(r.time/3600)).toFixed(1)} km/h</b></span></div>
      <div class="rc-desc">${r.desc}</div>
      <div class="rc-tags">${r.tags.map(t=>`<span class="tag ${t.cls}">${t.label}</span>`).join('')}</div>
      <div class="rc-actions">
        <button data-act="nav" data-i="${i}" class="nav-start-btn">🧭 Navigation</button>
        <button data-act="elev" data-i="${i}">📈 Höhe</button>
        <button data-act="gpx" data-i="${i}">⬇ GPX</button>
        <button data-act="done" data-i="${i}" class="done-btn">🏁 Gefahren</button>
      </div>
    </div>`).join('');

  box.querySelectorAll('.route-card').forEach(c=>{
    c.addEventListener('click',e=>{ if(e.target.closest('button'))return; selectRoute(+c.dataset.i); });
  });
  box.querySelectorAll('button[data-act]').forEach(b=>{
    b.onclick=e=>{ e.stopPropagation(); const i=+b.dataset.i;
      if(b.dataset.act==='nav'){ selectRoute(i); startNavigation(state.routes[i]); }
      if(b.dataset.act==='elev'){ selectRoute(i); showElevation(state.routes[i]); }
      if(b.dataset.act==='gpx') downloadGPX(state.routes[i]);
      if(b.dataset.act==='done') openFeedback(state.routes[i]);
    };
  });
}

function clearRoutes(){ state.layers.forEach(l=>map.removeLayer(l)); state.layers=[]; }
function drawRoutes(){
  clearRoutes();
  state.routes.forEach((r,i)=>{
    const latlngs=r.coords.map(c=>[c[1],c[0]]);
    const pl=L.polyline(latlngs,{color:r.color,weight:r===state.activeRoute?6:4,opacity:r===state.activeRoute?1:0.55});
    pl.on('click',()=>selectRoute(i));
    pl.addTo(map); state.layers.push(pl);
  });
  if(state.routes.length){
    const all=state.routes.flatMap(r=>r.coords.map(c=>[c[1],c[0]]));
    map.fitBounds(L.latLngBounds(all).pad(0.15));
  }
}
function selectRoute(i){
  state.activeRoute=state.routes[i];
  drawRoutes(); renderRoutes();
  // aktive Linie nach vorne
  state.layers[i] && state.layers[i].bringToFront && state.layers[i].bringToFront();
}

/* ---------------------------------------------------------------------
   9)  HÖHENPROFIL (Canvas)
   ------------------------------------------------------------------- */
function showElevation(r){
  const panel=document.getElementById('elevation-panel');
  panel.classList.remove('hidden');
  document.getElementById('ep-title').textContent=`Höhenprofil – ${r.name} (${fmtKm(r.dist)}, ↑${Math.round(r.ascend)} hm)`;
  const cv=document.getElementById('elevation-canvas');
  const dpr=window.devicePixelRatio||1;
  const w=cv.clientWidth, h=cv.clientHeight;
  cv.width=w*dpr; cv.height=h*dpr;
  const ctx=cv.getContext('2d'); ctx.scale(dpr,dpr); ctx.clearRect(0,0,w,h);

  // Daten: kumulative Distanz + Höhe
  const pts=[]; let cum=0;
  for(let i=0;i<r.coords.length;i++){ if(i>0)cum+=hav(r.coords[i-1],r.coords[i]); pts.push([cum,r.coords[i][2]||0]); }
  const eles=pts.map(p=>p[1]), minE=Math.min(...eles), maxE=Math.max(...eles), range=Math.max(maxE-minE,20);
  const padL=42,padB=18,padT=8,padR=8;
  const X=d=>padL+(d/cum)*(w-padL-padR);
  const Y=e=>h-padB-((e-minE)/range)*(h-padB-padT);

  // Fläche
  ctx.beginPath(); ctx.moveTo(X(0),Y(eles[0]));
  pts.forEach(p=>ctx.lineTo(X(p[0]),Y(p[1])));
  ctx.lineTo(X(cum),h-padB); ctx.lineTo(X(0),h-padB); ctx.closePath();
  const grad=ctx.createLinearGradient(0,padT,0,h-padB);
  grad.addColorStop(0,r.color+'cc'); grad.addColorStop(1,r.color+'10');
  ctx.fillStyle=grad; ctx.fill();
  // Linie
  ctx.beginPath(); ctx.moveTo(X(0),Y(eles[0]));
  pts.forEach(p=>ctx.lineTo(X(p[0]),Y(p[1])));
  ctx.strokeStyle=r.color; ctx.lineWidth=2; ctx.stroke();
  // Achsen
  ctx.fillStyle='#94a6b5'; ctx.font='10px sans-serif'; ctx.textBaseline='middle';
  [minE,(minE+maxE)/2,maxE].forEach(e=>{ const y=Y(e); ctx.fillText(Math.round(e)+'m',4,y);
    ctx.strokeStyle='rgba(148,166,181,.12)';ctx.beginPath();ctx.moveTo(padL,y);ctx.lineTo(w-padR,y);ctx.stroke(); });
  ctx.textBaseline='alphabetic';
  for(let k=0;k<=4;k++){ const d=cum*k/4; ctx.fillText((d/1000).toFixed(1)+'km',X(d)-12,h-5); }
}
document.getElementById('ep-close').onclick=()=>document.getElementById('elevation-panel').classList.add('hidden');

/* ---------------------------------------------------------------------
   10)  GPX-EXPORT
   ------------------------------------------------------------------- */
function downloadGPX(r){
  const pts=r.coords.map(c=>`<trkpt lat="${c[1]}" lon="${c[0]}"><ele>${c[2]||0}</ele></trkpt>`).join('');
  const gpx=`<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Velo Navi Schweiz" xmlns="http://www.topografix.com/GPX/1/1">
<trk><name>${r.name}</name><trkseg>${pts}</trkseg></trk></gpx>`;
  const blob=new Blob([gpx],{type:'application/gpx+xml'});
  const a=document.createElement('a'); a.href=URL.createObjectURL(blob);
  a.download=`velonavi_${r.name.replace(/\W+/g,'_')}.gpx`; a.click(); URL.revokeObjectURL(a.href);
}

/* ---------------------------------------------------------------------
   11)  FEEDBACK → KALIBRIERUNG
   ------------------------------------------------------------------- */
let fbRoute=null;
function openFeedback(r){
  fbRoute=r;
  document.getElementById('fbRoute').textContent=`${r.name} · ${fmtKm(r.dist)} · angezeigt: ${fmtTime(r.time)} (${BIKES[state.bike].label})`;
  document.getElementById('feedback-modal').classList.remove('hidden');
}
document.getElementById('fbCancel').onclick=()=>document.getElementById('feedback-modal').classList.add('hidden');
document.querySelectorAll('.fb-grid button').forEach(b=>{
  b.onclick=()=>{
    // "schneller" = Fahrer war schneller als angezeigt → Speed-Faktor hoch
    const adj={muchslower:-0.10,slower:-0.05,ok:0,faster:0.05,muchfaster:0.10}[b.dataset.fb];
    let f=state.calib[state.bike]*(1+adj);
    f=Math.max(0.5,Math.min(2.0,f));
    state.calib[state.bike]=f; saveCalib();
    document.getElementById('feedback-modal').classList.add('hidden');
    if(state.routes.length) recomputeAll();
    setStatus(`Danke! Tempo für ${BIKES[state.bike].label} angepasst (${(f>=1?'+':'')}${Math.round((f-1)*100)}%).`,'');
  };
});

/* ---------------------------------------------------------------------
   12)  „MEIN STANDORT" als Start
   ------------------------------------------------------------------- */
const useLocBtn=document.getElementById('useLocBtn');
useLocBtn.onclick=()=>{
  if(!navigator.geolocation){ setStatus('GPS wird von diesem Browser nicht unterstützt.','err'); return; }
  useLocBtn.textContent='📡 Standort wird ermittelt …'; useLocBtn.disabled=true;
  navigator.geolocation.getCurrentPosition(async pos=>{
    const {latitude:lat,longitude:lon}=pos.coords;
    let label='Mein Standort';
    try{
      const r=await fetch(`${GEOCODE}?searchText=${lat},${lon}&type=locations&sr=4326&limit=1&origins=address`);
      const d=await r.json(); if(d.results&&d.results[0]) label=String(d.results[0].attrs.label).replace(/<\/?[^>]+>/g,'');
    }catch(e){}
    setPoint('start',{lat:+lat.toFixed(6),lon:+lon.toFixed(6),label},false);
    document.getElementById('startInput').value=label;
    map.setView([lat,lon],14);
    useLocBtn.textContent='📡 Mein Standort als Start'; useLocBtn.disabled=false; useLocBtn.classList.add('active');
  }, err=>{
    setStatus('Standort nicht verfügbar: '+err.message,'err');
    useLocBtn.textContent='📡 Mein Standort als Start'; useLocBtn.disabled=false;
  }, {enableHighAccuracy:true, timeout:10000, maximumAge:5000});
};

/* ---------------------------------------------------------------------
   13)  LIVE-NAVIGATION
   ------------------------------------------------------------------- */
const nav = {
  active:false, route:null,
  cumDist:[], cumTime:[], total:0, totalTime:0,
  maneuvers:[],            // {dist, latlng:[lat,lon], type, angle, text}
  watchId:null, wakeLock:null,
  posMarker:null, traveledLine:null, aheadLine:null,
  muted:false, follow:true,
  lastPos:null, heading:0, speed:0,
  offrouteCount:0, rerouting:false, lastReroute:0,
  spoken:{}, lastAnnouncedIdx:-1,
};

// Bearing zwischen zwei [lon,lat,..]-Punkten (Grad, 0=Nord)
function bearing(a,b){
  const la1=a[1]*Math.PI/180, la2=b[1]*Math.PI/180, dLon=(b[0]-a[0])*Math.PI/180;
  const y=Math.sin(dLon)*Math.cos(la2);
  const x=Math.cos(la1)*Math.sin(la2)-Math.sin(la1)*Math.cos(la2)*Math.cos(dLon);
  return (Math.atan2(y,x)*180/Math.PI+360)%360;
}
function angleDiff(a,b){ let d=((b-a+540)%360)-180; return d; } // [-180,180], +rechts

// Manöver aus der Geometrie ableiten (mit Look-ahead, um Mikro-Knoten zu glätten)
function buildManeuvers(coords, cum){
  const LOOK=22; // Meter Vor-/Nachlauf für stabile Peilung
  const man=[];
  function ptBefore(i){ let j=i; while(j>0 && cum[i]-cum[j]<LOOK) j--; return coords[j]; }
  function ptAfter(i){ let j=i; while(j<coords.length-1 && cum[j]-cum[i]<LOOK) j++; return coords[j]; }
  for(let i=1;i<coords.length-1;i++){
    const bIn=bearing(ptBefore(i),coords[i]);
    const bOut=bearing(coords[i],ptAfter(i));
    const turn=angleDiff(bIn,bOut);
    const a=Math.abs(turn);
    if(a<32) continue;                       // nur echte Abbiegungen
    // dicht beieinander liegende Manöver zusammenfassen
    if(man.length && cum[i]-man[man.length-1].dist < 25) continue;
    man.push({ dist:cum[i], latlng:[coords[i][1],coords[i][0]], angle:turn, type:turnType(turn), text:turnText(turn) });
  }
  // Zielmanöver
  man.push({ dist:cum[cum.length-1], latlng:[coords[coords.length-1][1],coords[coords.length-1][0]], angle:0, type:'dest', text:'Ziel erreicht' });
  return man;
}
function turnType(t){
  const a=Math.abs(t);
  if(a>=150) return t>0?'uturn-r':'uturn-l';
  if(t>0)  return a>=110?'sharp-r':a>=60?'right':'slight-r';
  else     return a>=110?'sharp-l':a>=60?'left':'slight-l';
}
const TURN_ARROW={ 'slight-l':'↖','left':'⬅','sharp-l':'↰','slight-r':'↗','right':'➡','sharp-r':'↱',
  'uturn-l':'↶','uturn-r':'↷','dest':'🏁','straight':'↑' };
const TURN_WORD={ 'slight-l':'leicht links','left':'links abbiegen','sharp-l':'scharf links',
  'slight-r':'leicht rechts','right':'rechts abbiegen','sharp-r':'scharf rechts',
  'uturn-l':'wenden','uturn-r':'wenden','dest':'Ziel erreicht','straight':'geradeaus' };
function turnText(t){ return TURN_WORD[turnType(t)]; }

// Punkt → nächstgelegener Punkt auf Strecke; liefert {dist(m vom Start), off(m abseits)}
function projectOnRoute(lat,lon){
  const coords=nav.route.coords;
  let best={off:Infinity,dist:0};
  for(let i=1;i<coords.length;i++){
    const a=coords[i-1], b=coords[i];
    const r=projSeg(lon,lat,a[0],a[1],b[0],b[1]);
    if(r.off<best.off){ best={off:r.off, dist:nav.cumDist[i-1]+r.along}; }
  }
  return best;
}
// Projektion Punkt auf Segment in ~Meter (lokale Ebene)
function projSeg(plon,plat,alon,alat,blon,blat){
  const mLat=111320, mLon=111320*Math.cos(plat*Math.PI/180);
  const ax=alon*mLon, ay=alat*mLat, bx=blon*mLon, by=blat*mLat, px=plon*mLon, py=plat*mLat;
  const dx=bx-ax, dy=by-ay, len2=dx*dx+dy*dy||1e-9;
  let t=((px-ax)*dx+(py-ay)*dy)/len2; t=Math.max(0,Math.min(1,t));
  const cx=ax+t*dx, cy=ay+t*dy;
  const off=Math.hypot(px-cx,py-cy);
  const along=Math.hypot(cx-ax,cy-ay);
  return {off, along};
}

function fmtClock(date){ return date.toLocaleTimeString('de-CH',{hour:'2-digit',minute:'2-digit'}); }
function speak(text){
  if(nav.muted || !('speechSynthesis' in window)) return;
  try{ const u=new SpeechSynthesisUtterance(text); u.lang='de-DE'; u.rate=1.05; speechSynthesis.cancel(); speechSynthesis.speak(u); }catch(e){}
}
function navToast(msg,warn,ms){
  const t=document.getElementById('nav-toast'); t.textContent=msg; t.className=warn?'warn':'';
  if(ms!==0){ clearTimeout(nav._toastT); nav._toastT=setTimeout(()=>t.classList.add('hidden'),ms||3500); }
}

async function startNavigation(route){
  if(!navigator.geolocation){ alert('Dieses Gerät unterstützt kein GPS im Browser.'); return; }
  nav.route=route; nav.active=true;
  // Kumulative Distanz + Zeit vorbereiten
  const co=route.coords; nav.cumDist=[0]; nav.cumTime=[0];
  const domCat=route.tags[0]?.label||''; const crr=CRR[Object.keys(CAT_LABEL).find(k=>CAT_LABEL[k]===domCat)]||CRR.road;
  for(let i=1;i<co.length;i++){
    const d=hav(co[i-1],co[i]); nav.cumDist[i]=nav.cumDist[i-1]+d;
    const grade=d>0.5?Math.max(-0.30,Math.min(0.30,((co[i][2]||0)-(co[i-1][2]||0))/d)):0;
    nav.cumTime[i]=nav.cumTime[i-1]+ (d>0.5? d/segmentSpeed(grade,crr):0);
  }
  nav.total=nav.cumDist[co.length-1]; nav.totalTime=nav.cumTime[co.length-1];
  nav.maneuvers=buildManeuvers(co,nav.cumDist);
  nav.spoken={}; nav.lastAnnouncedIdx=-1; nav.offrouteCount=0; nav.follow=true;

  document.body.classList.add('navigating');
  document.getElementById('nav-overlay').classList.remove('hidden');
  map.invalidateSize();
  // Restroute-Linie (vor Position) + zurückgelegt
  if(nav.aheadLine) map.removeLayer(nav.aheadLine);
  if(nav.traveledLine) map.removeLayer(nav.traveledLine);
  const latlngs=co.map(c=>[c[1],c[0]]);
  nav.traveledLine=L.polyline([],{color:'#888',weight:7,opacity:.6}).addTo(map);
  nav.aheadLine=L.polyline(latlngs,{color:'#2a9d8f',weight:7,opacity:.95}).addTo(map);

  await requestWakeLock();
  navToast('GPS wird gesucht … Bewege dich, damit die Richtung erkannt wird.',false,5000);
  speak('Navigation gestartet.');

  nav.watchId=navigator.geolocation.watchPosition(onPos, onPosErr,
    {enableHighAccuracy:true, maximumAge:1000, timeout:15000});

  // Geräte-Kompass (optional, für Kartendrehung) – iOS braucht Permission
  enableCompass();
}

function onPosErr(err){ navToast('GPS-Problem: '+err.message,true,4000); }

function onPos(pos){
  if(!nav.active) return;
  const lat=pos.coords.latitude, lon=pos.coords.longitude;
  const acc=pos.coords.accuracy||30;
  if(pos.coords.speed!=null && pos.coords.speed>=0) nav.speed=pos.coords.speed; // m/s
  if(pos.coords.heading!=null && !isNaN(pos.coords.heading)) nav.heading=pos.coords.heading;
  else if(nav.lastPos){ const b=bearing([nav.lastPos.lon,nav.lastPos.lat],[lon,lat]); if(haversineM(nav.lastPos.lat,nav.lastPos.lon,lat,lon)>3) nav.heading=b; }
  nav.lastPos={lat,lon};

  const proj=projectOnRoute(lat,lon);
  // Off-Route?
  if(proj.off>40){ nav.offrouteCount++; } else { nav.offrouteCount=0; }
  if(nav.offrouteCount>=3 && !nav.rerouting && Date.now()-nav.lastReroute>8000){ reroute(lat,lon); return; }

  const s=proj.dist;                            // zurückgelegte Strecke entlang Route
  const remDist=Math.max(0,nav.total-s);
  const remTime=Math.max(0,nav.totalTime-timeAt(s));
  // Ankunft
  document.getElementById('nav-rem-dist').textContent = remDist>=1000?(remDist/1000).toFixed(1)+' km':Math.round(remDist)+' m';
  document.getElementById('nav-rem-time').textContent = fmtTime(remTime);
  document.getElementById('nav-eta').textContent = fmtClock(new Date(Date.now()+remTime*1000));
  document.getElementById('nav-speed').textContent = (nav.speed*3.6).toFixed(0);

  // nächstes Manöver
  let m=null, mi=-1;
  for(let i=0;i<nav.maneuvers.length;i++){ if(nav.maneuvers[i].dist>s+2){ m=nav.maneuvers[i]; mi=i; break; } }
  if(!m){ m=nav.maneuvers[nav.maneuvers.length-1]; mi=nav.maneuvers.length-1; }
  const toMan=Math.max(0,m.dist-s);
  updateManeuverUI(m,toMan,nav.maneuvers[mi+1]);
  announce(m,mi,toMan);

  // Ziel erreicht?
  if(remDist<25){ arrive(); return; }

  // Linien aktualisieren (zurückgelegt / voraus)
  updateProgressLines(s);

  // Karte folgen
  if(nav.follow){ map.setView([lat,lon], Math.max(map.getZoom(),16), {animate:true,duration:.4}); }
  updatePosMarker(lat,lon);
}

function timeAt(s){
  const cd=nav.cumDist, ct=nav.cumTime;
  if(s<=0) return 0; if(s>=nav.total) return nav.totalTime;
  let lo=0,hi=cd.length-1;
  while(lo<hi-1){ const mid=(lo+hi)>>1; if(cd[mid]<s) lo=mid; else hi=mid; }
  const f=(s-cd[lo])/((cd[hi]-cd[lo])||1);
  return ct[lo]+f*(ct[hi]-ct[lo]);
}

function updateManeuverUI(m,toMan,next){
  document.getElementById('nav-arrow').textContent = TURN_ARROW[m.type]||'↑';
  document.getElementById('nav-dist').textContent = m.type==='dest'? '' : (toMan>=1000?(toMan/1000).toFixed(1)+' km':Math.round(toMan/10)*10+' m');
  document.getElementById('nav-street').textContent = m.type==='dest'? 'Ziel' : (TURN_WORD[m.type]||'geradeaus');
  const thenEl=document.getElementById('nav-then');
  if(next && next.type!=='dest' && toMan<300){
    thenEl.classList.remove('hidden');
    document.getElementById('nav-then-arrow').textContent=TURN_ARROW[next.type]||'↑';
    document.getElementById('nav-then-street').textContent=TURN_WORD[next.type]||'';
  } else thenEl.classList.add('hidden');
}

function announce(m,mi,toMan){
  if(m.type==='dest'){ if(!nav.spoken['dest']&&toMan<120){ nav.spoken['dest']=1; speak('Du erreichst gleich dein Ziel.'); } return; }
  const key=mi; const word=TURN_WORD[m.type]||'geradeaus';
  // Schwellen: ~300 m (Vorankündigung), ~80 m, ~Jetzt
  if(toMan<=300 && toMan>120 && !nav.spoken[key+'_300']){ nav.spoken[key+'_300']=1; speak(`In ${Math.round(toMan/10)*10} Metern ${word}.`); }
  if(toMan<=80 && toMan>25 && !nav.spoken[key+'_80']){ nav.spoken[key+'_80']=1; speak(`In ${Math.round(toMan/10)*10} Metern ${word}.`); }
  if(toMan<=25 && !nav.spoken[key+'_now']){ nav.spoken[key+'_now']=1; speak(`Jetzt ${word}.`); }
}

function updateProgressLines(s){
  const co=nav.route.coords; const trav=[], ahead=[];
  for(let i=0;i<co.length;i++){
    if(nav.cumDist[i]<=s) trav.push([co[i][1],co[i][0]]);
    else ahead.push([co[i][1],co[i][0]]);
  }
  // Übergangspunkt einfügen
  if(nav.lastPos){ trav.push([nav.lastPos.lat,nav.lastPos.lon]); ahead.unshift([nav.lastPos.lat,nav.lastPos.lon]); }
  nav.traveledLine.setLatLngs(trav); nav.aheadLine.setLatLngs(ahead);
}

let gpsIcon=null;
function updatePosMarker(lat,lon){
  if(!nav.posMarker){
    gpsIcon=L.divIcon({className:'',html:'<div class="gps-dot"><div class="ring"></div><div class="cone"></div><div class="core"></div></div>',iconSize:[22,22],iconAnchor:[11,11]});
    nav.posMarker=L.marker([lat,lon],{icon:gpsIcon,interactive:false,zIndexOffset:1000}).addTo(map);
  } else nav.posMarker.setLatLng([lat,lon]);
  const cone=nav.posMarker.getElement()?.querySelector('.cone');
  if(cone) cone.style.transform=`translate(-50%,-100%) rotate(${nav.heading}deg)`;
}

function haversineM(la1,lo1,la2,lo2){
  const R=6371000,dLa=(la2-la1)*Math.PI/180,dLo=(lo2-lo1)*Math.PI/180;
  const x=Math.sin(dLa/2)**2+Math.cos(la1*Math.PI/180)*Math.cos(la2*Math.PI/180)*Math.sin(dLo/2)**2;
  return 2*R*Math.asin(Math.sqrt(x));
}

async function reroute(lat,lon){
  nav.rerouting=true; nav.lastReroute=Date.now();
  navToast('Neue Route wird berechnet …',true,0); speak('Route wird neu berechnet.');
  try{
    const prof=nav.route.profile||state.style;
    const dest=nav.route.coords[nav.route.coords.length-1];
    const ll=`${lon},${lat}|${dest[0]},${dest[1]}`;
    const url=`${BROUTER}?lonlats=${ll}&profile=${prof}&alternativeidx=0&format=geojson`;
    const r=await fetch(url); const d=JSON.parse(await r.text());
    const feat=d.features[0];
    const newRoute=analyzeRoute(feat, nav.route.name, nav.route.color); newRoute.profile=prof;
    // Nav-Datenstrukturen neu aufbauen
    nav.route=newRoute;
    const co=newRoute.coords; nav.cumDist=[0]; nav.cumTime=[0];
    const domCat=newRoute.tags[0]?.label||''; const crr=CRR[Object.keys(CAT_LABEL).find(k=>CAT_LABEL[k]===domCat)]||CRR.road;
    for(let i=1;i<co.length;i++){ const dd=hav(co[i-1],co[i]); nav.cumDist[i]=nav.cumDist[i-1]+dd;
      const g=dd>0.5?Math.max(-0.30,Math.min(0.30,((co[i][2]||0)-(co[i-1][2]||0))/dd)):0; nav.cumTime[i]=nav.cumTime[i-1]+(dd>0.5?dd/segmentSpeed(g,crr):0); }
    nav.total=nav.cumDist[co.length-1]; nav.totalTime=nav.cumTime[co.length-1];
    nav.maneuvers=buildManeuvers(co,nav.cumDist); nav.spoken={}; nav.offrouteCount=0;
    nav.aheadLine.setLatLngs(co.map(c=>[c[1],c[0]])); nav.traveledLine.setLatLngs([]);
    navToast('Neue Route gefunden.',false,2500);
  }catch(e){ navToast('Neuberechnung fehlgeschlagen – fahre zurück zur Route.',true,4000); }
  nav.rerouting=false;
}

function arrive(){
  speak('Du hast dein Ziel erreicht. Gute Fahrt gehabt!');
  navToast('🏁 Ziel erreicht!',false,6000);
  stopNavigation(true);
  if(nav.route) openFeedback(nav.route);
}

function stopNavigation(keepToast){
  nav.active=false;
  if(nav.watchId!=null){ navigator.geolocation.clearWatch(nav.watchId); nav.watchId=null; }
  releaseWakeLock();
  if('speechSynthesis' in window) speechSynthesis.cancel();
  document.body.classList.remove('navigating');
  document.getElementById('nav-overlay').classList.add('hidden');
  if(!keepToast) document.getElementById('nav-toast').classList.add('hidden');
  if(nav.posMarker){ map.removeLayer(nav.posMarker); nav.posMarker=null; }
  if(nav.traveledLine){ map.removeLayer(nav.traveledLine); nav.traveledLine=null; }
  if(nav.aheadLine){ map.removeLayer(nav.aheadLine); nav.aheadLine=null; }
  map.invalidateSize();
  drawRoutes();
}

// Wake Lock (Display anlassen)
async function requestWakeLock(){
  try{ if('wakeLock' in navigator){ nav.wakeLock=await navigator.wakeLock.request('screen');
    document.addEventListener('visibilitychange',reacquireWakeLock); } }catch(e){}
}
async function reacquireWakeLock(){ if(nav.active && document.visibilityState==='visible' && 'wakeLock' in navigator){ try{ nav.wakeLock=await navigator.wakeLock.request('screen'); }catch(e){} } }
function releaseWakeLock(){ try{ nav.wakeLock&&nav.wakeLock.release(); }catch(e){} nav.wakeLock=null; document.removeEventListener('visibilitychange',reacquireWakeLock); }

// Kompass (optional)
function enableCompass(){
  function handler(e){ let h=e.webkitCompassHeading; if(h==null && e.alpha!=null) h=360-e.alpha; if(h!=null && (nav.speed||0)<1.2) nav.heading=h; }
  if(typeof DeviceOrientationEvent!=='undefined' && typeof DeviceOrientationEvent.requestPermission==='function'){
    DeviceOrientationEvent.requestPermission().then(s=>{ if(s==='granted') window.addEventListener('deviceorientation',handler,true); }).catch(()=>{});
  } else if('ondeviceorientationabsolute' in window){ window.addEventListener('deviceorientationabsolute',handler,true); }
  else if(typeof DeviceOrientationEvent!=='undefined'){ window.addEventListener('deviceorientation',handler,true); }
}

// Nav-Buttons verdrahten
document.getElementById('nav-stop').onclick=()=>stopNavigation(false);
document.getElementById('nav-recenter').onclick=()=>{ nav.follow=true; if(nav.lastPos) map.setView([nav.lastPos.lat,nav.lastPos.lon],16,{animate:true}); };
document.getElementById('nav-overview').onclick=()=>{ nav.follow=false; if(nav.route){ const all=nav.route.coords.map(c=>[c[1],c[0]]); map.fitBounds(L.latLngBounds(all).pad(0.15)); } };
document.getElementById('nav-mute').onclick=function(){ nav.muted=!nav.muted; this.textContent=nav.muted?'🔇':'🔊'; this.classList.toggle('off',nav.muted); if(nav.muted&&'speechSynthesis' in window) speechSynthesis.cancel(); else speak('Ton an.'); };
map.on('dragstart',()=>{ if(nav.active) nav.follow=false; });

/* ---------------------------------------------------------------------
   14)  PWA – Service Worker
   ------------------------------------------------------------------- */
if('serviceWorker' in navigator){
  window.addEventListener('load',()=>{ navigator.serviceWorker.register('sw.js').catch(()=>{}); });
}

/* ---------------------------------------------------------------------
   15)  START
   ------------------------------------------------------------------- */
setStatus('Bereit. Wähle Start & Ziel und berechne deine Routen.','');
