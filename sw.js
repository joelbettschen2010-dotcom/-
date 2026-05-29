/* Service Worker – Velo Navi Schweiz
   App-Shell offline verfügbar machen. Karten/Routing brauchen weiterhin Internet. */
const CACHE = 'velonavi-v3';
const SHELL = [
  './', './index.html', './style.css', './app.js', './manifest.webmanifest',
  './icon-192.png', './icon-512.png', './apple-touch-icon.png',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  // Karten-Kacheln, Routing, Geocoding: immer aus dem Netz (nicht cachen)
  if (/wmts\.geo\.admin\.ch|brouter\.de|api3\.geo\.admin\.ch|tile\.openstreetmap/.test(url.host + url.pathname)) {
    return; // Browser-Standard (Netzwerk)
  }
  // App-Shell: Cache-first mit Netz-Fallback
  e.respondWith(
    caches.match(e.request).then(hit => hit || fetch(e.request).then(resp => {
      if (e.request.method === 'GET' && resp.ok && url.origin === location.origin) {
        const copy = resp.clone();
        caches.open(CACHE).then(c => c.put(e.request, copy));
      }
      return resp;
    }).catch(() => hit))
  );
});
