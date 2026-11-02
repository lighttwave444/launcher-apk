// Roda depois de "npx cap add android": transforma o app em launcher (tela inicial)
// e instala o plugin que lista os apps do celular. Pode rodar varias vezes sem duplicar.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const appId = JSON.parse(fs.readFileSync(path.join(root, 'capacitor.config.json'), 'utf8')).appId;
const javaDir = path.join(root, 'android/app/src/main/java', ...appId.split('.'));
const manifestPath = path.join(root, 'android/app/src/main/AndroidManifest.xml');

if (!fs.existsSync(manifestPath)) {
  console.error('android/ nao existe. Rode "npx cap add android" antes.');
  process.exit(1);
}

// 1) Java: plugin + MainActivity (com o package certo)
fs.mkdirSync(javaDir, { recursive: true });
for (const f of ['AppsPlugin.java', 'MainActivity.java']) {
  const src = fs.readFileSync(path.join(root, 'native', f), 'utf8')
    .replace(/^package\s+[\w.]+;/m, 'package ' + appId + ';');
  fs.writeFileSync(path.join(javaDir, f), src);
}

// 2) Manifest: HOME + DEFAULT na Activity principal e permissao para enxergar os apps
let m = fs.readFileSync(manifestPath, 'utf8');
if (!m.includes('android.intent.category.HOME')) {
  const launcherCat = /(<category\s+android:name="android\.intent\.category\.LAUNCHER"\s*\/>)/;
  if (!launcherCat.test(m)) { console.error('Nao achei a categoria LAUNCHER no manifest.'); process.exit(1); }
  m = m.replace(launcherCat,
    '$1\n                <category android:name="android.intent.category.HOME" />\n' +
    '                <category android:name="android.intent.category.DEFAULT" />');
}
if (!m.includes('<queries>')) {
  m = m.replace(/<application/,
    '<queries>\n        <intent>\n            <action android:name="android.intent.action.MAIN" />\n' +
    '            <category android:name="android.intent.category.LAUNCHER" />\n        </intent>\n    </queries>\n\n    <application');
}
if (!/android:launchMode="singleTask"/.test(m)) {
  m = m.replace(/(<activity\b)/, '$1\n            android:launchMode="singleTask"');
}
fs.writeFileSync(manifestPath, m);
console.log('Launcher configurado: ' + appId);
