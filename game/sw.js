/* Service Worker – Horizon Rush
   Das ganze Spiel liegt im Zwischenspeicher; nach dem ersten Laden läuft es
   vollständig offline. */

/* Wichtig: Auf github.io teilen sich alle Projekte dieses Kontos denselben
   Ursprung. Deshalb darf beim Aufräumen NUR der eigene Vorrat angefasst
   werden – sonst löschen sich die Apps gegenseitig ihren Offline-Bestand. */
const PREFIX = 'horizon-rush-';
const CACHE = PREFIX + 'v2';

const SHELL = [
  './', './index.html', './style.css', './manifest.webmanifest',
  './js/core.js', './js/art.js', './js/track.js', './js/race.js', './js/ui.js',
  './icon-192.png', './icon-512.png', './icon-180.png'
];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE)
      /* 'reload' umgeht den HTTP-Zwischenspeicher, sonst landet beim
         Aktualisieren wieder die alte Fassung im Vorrat. */
      .then(c => Promise.all(SHELL.map(u => c.add(new Request(u, { cache: 'reload' })).catch(() => null))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(k => k.indexOf(PREFIX) === 0 && k !== CACHE).map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);
  if (url.origin !== location.origin) return;
  e.respondWith(
    caches.match(e.request).then(hit => hit || fetch(e.request).then(resp => {
      if (resp.ok) { const copy = resp.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); }
      return resp;
    }).catch(() => caches.match('./index.html')))
  );
});

/* Die Seite darf nachfragen, ob der Offline-Vorrat vollständig ist. */
self.addEventListener('message', e => {
  if (e.data === 'hr-offline-status') {
    caches.open(CACHE)
      .then(c => c.keys())
      .then(keys => e.source && e.source.postMessage({ type: 'hr-offline-status', dateien: keys.length, soll: SHELL.length }))
      .catch(() => { });
  }
});
