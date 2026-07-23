# PWA Instalable Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hacer instalable la web de movi (home screen móvil + ventana standalone desktop) con manifest, iconos y meta tags iOS — sin service worker.

**Architecture:** Assets estáticos puros en `webApp/src/wasmJsMain/resources/` que fluyen por el pipeline existente (resources → `wasmJsBrowserDistribution` → static del Docker). Cero Kotlin, cero cambios de server.

**Tech Stack:** manifest.json + PNG + HTML. `sips` (macOS) para el upscale del ícono 512.

**Spec:** `docs/superpowers/specs/2026-07-23-pwa-installable-design.md`

## Global Constraints

- Branch `feat/pwa-installable` en `/Users/carolinarestrepo/Developer/movi`. JBR 21 ya es JAVA_HOME.
- SIN service worker, SIN offline (locked). `manifest.json` (NO `.webmanifest`) para no tocar el server.
- Colores exactos: `background_color` y `theme_color` = `#121212`. `display: "standalone"`, `start_url: "/"`, `scope: "/"`, `lang: "es"`.
- Iconos: `movi-192.png` = copia EXACTA de `androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (192×192 verificado); `movi-512.png` = `sips -z 512 512` sobre esa copia. Ambos COMMITEADOS.
- NO correr `./gradlew build` completo (OOM iOS release pre-existente); el target es `:webApp:wasmJsBrowserDistribution`.

---

### Task 1: Manifest + iconos + tags iOS + verificación

**Files:**
- Create: `webApp/src/wasmJsMain/resources/manifest.json`
- Create: `webApp/src/wasmJsMain/resources/icons/movi-192.png` (binario)
- Create: `webApp/src/wasmJsMain/resources/icons/movi-512.png` (binario)
- Modify: `webApp/src/wasmJsMain/resources/index.html` (solo el `<head>`, tras `<title>Movi</title>`)

**Interfaces:** ninguna — assets finales.

- [ ] **Step 1: Generar los iconos**

```bash
mkdir -p webApp/src/wasmJsMain/resources/icons
cp androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png webApp/src/wasmJsMain/resources/icons/movi-192.png
cp webApp/src/wasmJsMain/resources/icons/movi-192.png webApp/src/wasmJsMain/resources/icons/movi-512.png
sips -z 512 512 webApp/src/wasmJsMain/resources/icons/movi-512.png
file webApp/src/wasmJsMain/resources/icons/movi-192.png webApp/src/wasmJsMain/resources/icons/movi-512.png
```

Expected: `192 x 192` y `512 x 512`, ambos PNG.

- [ ] **Step 2: Crear manifest.json**

`webApp/src/wasmJsMain/resources/manifest.json` — EXACTAMENTE:

```json
{
  "name": "Movi",
  "short_name": "Movi",
  "description": "Finanzas personales y familiares",
  "lang": "es",
  "start_url": "/",
  "scope": "/",
  "display": "standalone",
  "background_color": "#121212",
  "theme_color": "#121212",
  "icons": [
    { "src": "icons/movi-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any maskable" },
    { "src": "icons/movi-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any maskable" }
  ]
}
```

Validar: `python3 -c "import json;json.load(open('webApp/src/wasmJsMain/resources/manifest.json'));print('OK')"` → OK.

- [ ] **Step 3: Tags en index.html**

En `webApp/src/wasmJsMain/resources/index.html`, inmediatamente después de la línea `<title>Movi</title>`, insertar:

```html
    <link rel="manifest" href="manifest.json">
    <meta name="theme-color" content="#121212">
    <link rel="apple-touch-icon" href="icons/movi-192.png">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <meta name="apple-mobile-web-app-title" content="Movi">
```

NADA más del archivo cambia (el overlay de login y los scripts quedan intactos).

- [ ] **Step 4: Verificar el dist**

```bash
./gradlew :webApp:wasmJsBrowserDistribution
ls webApp/build/dist/wasmJs/productionExecutable/manifest.json webApp/build/dist/wasmJs/productionExecutable/icons/
grep -c 'rel="manifest"' webApp/build/dist/wasmJs/productionExecutable/index.html
```

Expected: manifest.json e icons/{movi-192.png,movi-512.png} presentes; grep = 1.

- [ ] **Step 5: Verificar servido (HTTP)**

```bash
cd webApp/build/dist/wasmJs/productionExecutable && python3 -m http.server 8090 &
sleep 2
curl -s -o /dev/null -w "manifest %{http_code} %{content_type}\n" localhost:8090/manifest.json
curl -s -o /dev/null -w "icon512 %{http_code} %{content_type}\n" localhost:8090/icons/movi-512.png
curl -s localhost:8090/manifest.json | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['display'],d['theme_color'],len(d['icons']))"
kill %1
```

Expected: `manifest 200 application/json`, `icon512 200 image/png`, `standalone #121212 2`.

- [ ] **Step 6: Commit y push**

```bash
git add webApp/src/wasmJsMain/resources
git commit -m "feat(web): PWA instalable — manifest, iconos y meta tags iOS (sin service worker)"
git push -u origin feat/pwa-installable
```
