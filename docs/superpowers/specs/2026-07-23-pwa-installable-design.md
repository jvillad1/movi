# PWA instalable — diseño

**Fecha:** 2026-07-23
**Alcance:** solo `webApp/src/wasmJsMain/resources/` (assets estáticos). Cero cambios en
`:server`, `:core`, `:shared` o Kotlin. Los archivos fluyen por el pipeline existente:
resources → `wasmJsBrowserDistribution` → `server/src/main/resources/static/` (Dockerfile).

## Problema / valor

La web de movi (única superficie en prod) no es instalable: sin manifest, sin iconos, sin
meta tags. Para uso diario, el usuario quiere movi como app en el home screen del teléfono
y como ventana standalone en desktop — sin pasar por el navegador con URL cada vez.

## Decisiones (locked)

- **Solo instalable** (elegido por el usuario): SIN service worker, SIN offline. La app es
  100% server-backed; un shell offline no muestra nada útil, y un SW mal versionado es el
  clásico bug de "bundle viejo tras deploy". Chrome moderno y Safari iOS permiten instalar
  sin SW.
- **`manifest.json`, no `.webmanifest`:** Ktor ya sirve `.json` como `application/json`
  (aceptado por todos los navegadores para manifests); `.webmanifest` exigiría registrar
  content-type en `Routing.kt` — se evita tocar el server.
- **Iconos v1 desde el launcher Android:** `movi-192.png` = copia de
  `androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (verificado 192×192 RGB);
  `movi-512.png` = upscale con `sips -z 512 512` (calidad v1 aceptable; reemplazable
  cuando exista arte nativo 512). `purpose: "any maskable"` en ambos (compromiso v1
  documentado; lo ideal futuro es un maskable con safe-zone propio).
- **Colores del manifest = shell actual:** `background_color` y `theme_color` `#121212`
  (el fondo ya hardcodeado en `index.html`).

## Diseño

### 1. `webApp/src/wasmJsMain/resources/manifest.json`

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

### 2. Iconos

`webApp/src/wasmJsMain/resources/icons/movi-192.png` y `movi-512.png`, generados una vez
(cp + `sips -z 512 512`) y COMMITEADOS como binarios (no se generan en build).

### 3. `index.html` — agregar al `<head>` (después del `<title>`)

```html
    <link rel="manifest" href="manifest.json">
    <meta name="theme-color" content="#121212">
    <link rel="apple-touch-icon" href="icons/movi-192.png">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <meta name="apple-mobile-web-app-title" content="Movi">
```

(Safari iOS ignora el manifest para "Añadir a pantalla de inicio" — usa estos tags.)

## Testing / verificación

- **Build:** `./gradlew :webApp:wasmJsBrowserDistribution` → `manifest.json`, `icons/` y
  el `index.html` con los tags llegan a `webApp/build/dist/wasmJs/productionExecutable/`.
- **Serve local:** `:server:run` (con el dist copiado a static o servido en dev) → `curl`
  a `/manifest.json` (200, JSON parseable, campos exactos) y a `/icons/movi-512.png`
  (200, PNG). `index.html` servido contiene `rel="manifest"`.
- **Manual post-deploy (usuario):** Chrome desktop muestra "Instalar Movi" en la omnibox;
  en iPhone, Compartir → Añadir a pantalla de inicio → abre standalone con ícono y nombre.

## Fuera de alcance (futuro)

Service worker / offline / cache; ícono maskable con safe-zone dedicada y arte 512
nativo; splash screens iOS (`apple-touch-startup-image`); shortcuts del manifest.
