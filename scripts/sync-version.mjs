// package.json -> www/js/version.js + android versionName/versionCode
import fs from 'node:fs';
const { version } = JSON.parse(fs.readFileSync('package.json', 'utf8'));
const [a, b, c] = version.split('.').map(Number);
const code = a * 10000 + b * 100 + c;
fs.writeFileSync('www/js/version.js', `export const VERSION = '${version}';\n`);
const g = 'android/app/build.gradle';
if (fs.existsSync(g)) {
  const s = fs.readFileSync(g, 'utf8')
    .replace(/versionCode \d+/, `versionCode ${code}`)
    .replace(/versionName "[^"]*"/, `versionName "${version}"`);
  fs.writeFileSync(g, s);
}
console.log(`version ${version} (code ${code})`);
