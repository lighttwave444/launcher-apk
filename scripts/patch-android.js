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
for (const f of ['AppsPlugin.java', 'MainActivity.java', 'PointerService.java']) {
  const src = fs.readFileSync(path.join(root, 'native', f), 'utf8')
    .replace(/^package\s+[\w.]+;/m, 'package ' + appId + ';');
  fs.writeFileSync(path.join(javaDir, f), src);
}

// 1b) config do servico de acessibilidade
const xmlDir = path.join(root, 'android/app/src/main/res/xml');
fs.mkdirSync(xmlDir, { recursive: true });
const valDir = path.join(root, 'android/app/src/main/res/values');
fs.mkdirSync(valDir, { recursive: true });
fs.copyFileSync(path.join(root, 'native/res/pointer_strings.xml'), path.join(valDir, 'pointer_strings.xml'));
fs.copyFileSync(path.join(root, 'native/res/xml/pointer_service_config.xml'), path.join(xmlDir, 'pointer_service_config.xml'));

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
if (!m.includes('PointerService')) {
  const svc = '    <service android:name="' + appId + '.PointerService" android:label="Launcher ponteiro" android:exported="true" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">\n' +
    '      <intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter>\n' +
    '      <meta-data android:name="android.accessibilityservice" android:resource="@xml/pointer_service_config" />\n' +
    '    </service>\n';
  m = m.replace('</application>', svc + '</application>');
}
if (!m.includes('ShizukuProvider')) {
  m = m.replace('</application>', '    <provider android:name="rikka.shizuku.ShizukuProvider" android:authorities="' + appId + '.shizuku" android:multiprocess="false" android:enabled="true" android:exported="true" android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />\n</application>');
  m = m.replace('<application', '<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />\n    <application');
}
const gp = path.join(root, 'android/app/build.gradle');
let g = fs.readFileSync(gp, 'utf8');
if (!g.includes('shizuku')) {
  g = g.replace(/dependencies\s*\{/, "dependencies {\n    implementation 'dev.rikka.shizuku:api:13.1.5'\n    implementation 'dev.rikka.shizuku:provider:13.1.5'");
  fs.writeFileSync(gp, g);
}
fs.writeFileSync(manifestPath, m);
console.log('Launcher configurado: ' + appId);
