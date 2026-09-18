// Fixture de test: hace las veces del bundle real (webApp/build/dist/.../composeApp.js), que el
// Dockerfile copia a server/src/main/resources/static/ (esa carpeta está en .gitignore).
//
// Existe para que CacheControlDelBundleTest pueda pedir el bundle por su nombre REAL: el nombre
// sin hash es justamente lo que hace peligroso que el navegador lo reuse sin revalidar.
globalThis.moviFixtureDelBundle = true;
