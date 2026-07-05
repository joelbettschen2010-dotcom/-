/** @type {import('next').NextConfig} */
// Statischer Export: die App wird als reine HTML/JS/CSS-Dateien gebaut und
// vom ESP32 (LittleFS, Ordner /www) ausgeliefert. Kein Server noetig.
const nextConfig = {
  output: 'export',
  trailingSlash: true,          // ESP32-Webserver mag /eq/index.html
  images: { unoptimized: true },
};
export default nextConfig;
