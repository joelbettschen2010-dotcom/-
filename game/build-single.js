#!/usr/bin/env node
/* Baut aus den Spieldateien eine einzige, in sich geschlossene HTML-Datei.
   Alles ist eingebettet – Stil, Skripte und das App-Icon als data-URI.
   Damit laeuft das Spiel auch ohne Server und ohne Internet, einfach durch
   Doppelklick bzw. Antippen der Datei.

   Aufruf:  node build-single.js  [Zieldatei]
*/
const fs = require('fs');
const path = require('path');

const SRC = __dirname;
const OUT = process.argv[2] || path.join(SRC, 'Horizon-Rush.html');
const read = f => fs.readFileSync(path.join(SRC, f), 'utf8');
const b64 = f => fs.readFileSync(path.join(SRC, f)).toString('base64');

/* Rumpf aus index.html herausloesen, eigene Skript-Verweise entfernen */
const html = read('index.html');
let body = html.split('<body>')[1].split('</body>')[0];
body = body.replace(/<script[\s\S]*?<\/script>/g, '').trim();

const css = read('style.css');
const js = ['js/core.js', 'js/art.js', 'js/track.js', 'js/race.js', 'js/ui.js'].map(read).join('\n');
const icon = 'data:image/png;base64,' + b64('icon-180.png');

const out = `<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
<title>Horizon Rush</title>
<meta name="theme-color" content="#0a0b12">
<meta name="description" content="Horizon Rush – prozedural erzeugtes Arcade-Rennspiel. Einzeldatei, laeuft offline.">
<link rel="apple-touch-icon" href="${icon}">
<link rel="icon" href="${icon}">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<meta name="apple-mobile-web-app-title" content="Horizon Rush">
<style>
${css}
</style>
</head>
<body>
${body}
<script>
${js}
</script>
</body>
</html>
`;
fs.writeFileSync(OUT, out);
console.log('geschrieben:', OUT, Math.round(out.length / 1024) + ' KB');
