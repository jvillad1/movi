# Pruebas y CI: medición de noviembre de 2026 (vuelta 122)

Pedido del dueño (2-nov-2026): que correr las pruebas cueste menos, en GitHub y en la Mac de 16 GB.
Todo lo de abajo está medido; donde algo es una estimación, lo dice.

## Antes

### GitHub Actions (`pruebas.yml`, un solo trabajo con `--rerun-tasks`)

Las 12 últimas corridas verdes antes del cambio (2 y 3 de noviembre, 6 de PR y 6 de push a master):

| | PR | Push a master |
|---|---|---|
| Trabajo completo | 422–515 s (media **8,1 min**) | 359–553 s (media 7,5 min) |
| Paso de Gradle | 381–476 s | 302–477 s |
| Del push al resultado (con la cola) | 7,1–10,1 min | 6,0–9,3 min |

Por cada cambio mergeado: **~15,6 minutos de máquina** (PR + otra vez la batería entera en master)
y ~8 minutos de espera. Lo de fuera de Gradle era poco: el JDK ~9 s, setup-gradle ~15 s, restaurar
los `android-all` ~5 s.

Dentro de Gradle (corrida 41596610979, PR de la vuelta 118, por las marcas de tiempo del log):

| Tramo | Tiempo |
|---|---|
| Configuración (sin caché de configuración: siempre fresca en CI) | ~22 s |
| Compilación (todo, en serie de dependencias: core → shared → pruebas de shared) | ~5 min |
| `:core:jvmTest` | empieza a 3 min |
| `:server:test` | ~1 min 50 s |
| `:shared:testDebugUnitTest` (Robolectric, una JVM) | ~2 min 12 s, y es lo último |

`--rerun-tasks` ignoraba la caché: 99 de 99 tareas ejecutadas en cada corrida.

### La Mac (una sola corrida, con el candado y en un worktree limpio)

`./gradlew --rerun-tasks --profile :core:jvmTest :server:test :shared:testDebugUnitTest
:shared:compileKotlinWasmJs` (el comando de `CLAUDE.md` de entonces, sin `:androidApp`), 3-nov 02:59,
con otra sesión con su servidor abierto: **15 min 20 s**. Configuración: ~2 s (arranque 1,5 s,
proyectos 0,7 s). Tiempo de tareas sumado (en paralelo): 32 min 45 s.

| Módulo | Compilación | Pruebas |
|---|---|---|
| `:shared` | `compileDebugKotlinAndroid` 9 min 52 s · `compileKotlinWasmJs` 2 min 12 s · `compileDebugUnitTestKotlinAndroid` 1 min 16 s | `testDebugUnitTest` **3 min 38 s** |
| `:server` | `compileTestKotlin` 7 min 44 s · `compileKotlin` 2 min 11 s | `test` **2 min 36 s** |
| `:core` | `compileTestKotlinJvm` 1 min 23 s · `compileKotlinJvm` 22 s · `compileDebugKotlinAndroid` 22 s · `compileKotlinWasmJs` 12 s | `jvmTest` **42 s** |

**~25 de los 33 minutos de tareas son compilación**; las pruebas son ~7. (Los tiempos de compilación
se inflan porque corren a la vez y comparten un demonio de Kotlin de 2 GB.)

Memoria: en esta Mac `~/.gradle/gradle.properties` **gana** sobre el del proyecto
(`org.gradle.jvmargs=-Xmx3g`, `kotlin.daemon.jvmargs=-Xmx2g`, `org.gradle.workers.max=4`,
`org.gradle.caching=true`). El `-Xmx8192m` del proyecto (por los links de iOS) **no aplica aquí**; la
corrida entera pasó con 3 + 2 GB, y al terminar el demonio de Gradle ocupaba 1,7 GB y el de Kotlin
0,6 GB. Las JVM de prueba usan el heap por defecto de Gradle (512 MB) y una a la vez. No hay nada que
recortar en memoria: la Mac ya corre con lo mínimo que la batería aguanta.

### Pruebas que corrían (por módulo)

Contadas de los XML de resultados de esa corrida de la Mac (commit `734aab9a5`):

| Módulo | Clases | Pruebas |
|---|---|---|
| `:core:jvmTest` | 176 | 1.569 |
| `:server:test` | 253 | 2.682 |
| `:shared:testDebugUnitTest` | 632 | 4.658 |

## Qué cambió

1. **Las pruebas siempre corren; la compilación sale de la caché** (`build.gradle.kts` de la raíz):
   toda tarea `Test` tiene `outputs.upToDateWhen { false }` y `doNotCacheIf { true }`. Ya no hace
   falta `--rerun-tasks`, que además recompilaba todo.
2. **CI en paralelo** (`pruebas.yml`): una matriz de 5 trabajos —`core`, `server`, `shared (1/2)`,
   `shared (2/2)` y `web y app`— con `--build-cache` y 2 JVM de prueba por trabajo, y «Las cuatro
   suites» como trabajo final que espera a todos (el nombre del check obligatorio no cambió).
   `shared` se parte en dos con `-Pmovi.trozoDePruebas=i/2` (cada clase cae en un trozo por el hash de
   su nombre). Cada trabajo cuenta las pruebas que corrió y falla con cero.
3. **El push a master ya no repite las pruebas**: corre «Humo de master» (compila todo y prepara las
   tres suites sin correr ninguna prueba, `-Pmovi.soloPrepararLasPruebas=true`, más la guarda del
   despliegue), que además es lo que escribe la caché que leen los PR. La matriz y el humo comparten
   la llave de caché de trabajo `pruebas` (`GRADLE_BUILD_ACTION_CACHE_KEY_JOB`), así los PR restauran
   la del humo —con todas sus dependencias— y no la de `paquete.yml`, que corre justo después de cada
   merge y no trae nada de Android. Por qué es seguro saltar las pruebas: ver el encabezado de
   `pruebas.yml`.
4. **`scripts/pruebas-dirigidas.sh`** para la Mac: solo las clases que tocaste más las guardas de
   `quality/`, con el candado.

## Después

### GitHub Actions

| Corrida | Caché de build | Del primero al último trabajo | Minutos de máquina |
|---|---|---|---|
| 41602254970 (`ci/v122-medir`) | fría | **5,3 min** | 20,8 |
| 41603165008 (`ci/v122-rojo`, solo lectura, como un PR) | fría | 6,4 min | 22,6 |
| 41603090071 (`ci/v122-medir`, cambio solo en `:shared`) | tibia | **4,0 min** sin el trabajo de prueba «x4» | 15,0 sin «x4» |
| 41604956936 (`ci/v122-final`, el árbol final de la rama, como un PR) | fría | 6,6 min | 21,3 |
| 41606223146 (`ci/v122-humo`, con la caché que dejó el humo, cambio solo en `:shared`) | la del humo | **5,0 min** | 15,1 |

Por trabajo (segundos del trabajo entero; entre paréntesis, el paso de Gradle):

| Trabajo | Fría (41602254970) | Tibia (41603090071) |
|---|---|---|
| core | 164 (112) | 90 (40) |
| server | 281 (232) | 152 (103) |
| shared (1/2) | 310 (246) | 221 (164) |
| shared (2/2) | 279 (214) | 238 (177) |
| web y app | 208 (153) | 196 (141) |
| Las cuatro suites | 4 | 3 |

(Esas dos corridas guardaban la caché al final, ~20 s de «Post setup-gradle» por trabajo que un PR
real no paga: en un PR la caché es de solo lectura.)

- **Fría** es el peor caso: el primer PR después de mergear esto, o uno que toca `:core` (6 de cada
  10 de los últimos 25 merges). **Tibia**: un PR que no toca `:core` ni `:server` saca de la caché
  `:core`, `:server` y sus pruebas compiladas.
- El cuello de botella son los trozos de `shared`: compilar `:core` para Android (~1 min, o de la
  caché), `:shared` (~1 min) y sus pruebas (~35 s) antes de correr ~55 s de pruebas por trozo.
- **Probado y descartado: un solo trabajo de `shared` con 4 JVM de prueba.** Las pruebas tardaron
  3 min 3 s contra ~55 s de cada trozo con 2: Robolectric no escala con más JVM en una máquina de
  4 núcleos. Partir en dos máquinas sí.
- **El humo de master** (corrida 41605424744 en `ci/v122-humo`, partiendo de la caché vieja de
  master): 338 s el trabajo, 4 min 45 s de Gradle (99 tareas, 11 de la caché). Nadie lo espera. La
  corrida siguiente de la matriz en esa rama (41606223146) restauró **en sus cinco trabajos** la
  entrada que escribió ese humo (`Cache hit for restore-key: gradle-home-v1|Linux-X64|pruebas[…]-616aa67…`).
- La variación entre corridas es grande (los trozos de `shared` fríos fueron de 214 a 356 s de
  Gradle en máquinas iguales): las cifras son rangos, no promedios de muchas corridas.

Por cambio mergeado: el PR pasa de 7–10 a **4–7 minutos de espera** (4–5 con la caché del humo,
5–7 fría); las máquinas, de ~15,6 minutos (PR + master) a 15–23 en el PR + ~5–6 del humo. Se gastan más minutos de máquina (el repo es público:
los runners estándar no se cobran) a cambio de la mitad de la espera, que es lo que frena las vueltas.

### Pruebas que corren (por módulo), iguales

Mismo árbol (`1da8f5650` = `734aab9a5` + esta vuelta, sin pruebas nuevas), contadas por el paso
«Cuántas pruebas corrieron» de la corrida 41602254970:

| Módulo | Antes (Mac, `--rerun-tasks`) | Después (CI en trozos) |
|---|---|---|
| `:core:jvmTest` | 176 clases · 1.569 | 176 clases · 1.569 |
| `:server:test` | 253 clases · 2.682 | 253 clases · 2.682 |
| `:shared:testDebugUnitTest` | 632 clases · 4.658 | 311 + 321 clases · 2.437 + 2.221 = **4.658** |

La corrida 41603090071 corrió además `shared` entero en un trabajo aparte: 632 clases · 4.658,
igual a la suma de los dos trozos.

### Una prueba rota pone el check en rojo

Rama `ci/v122-rojo` (borrada): una prueba nueva en `shared/src/commonTest` con `assertEquals(2, 1 + 2)`.
Corrida 41603165008: `shared (1/2)` en rojo («2438 tests completed, 1 failed»; el hash la mandaba a
ese trozo, como se calculó antes de empujar), los otros cuatro en verde, y **«Las cuatro suites» en
rojo** («Algún trabajo de la matriz no pasó (failure)»).

### La Mac

| | Antes | Después |
|---|---|---|
| Batería completa | 15 min 20 s (con `--rerun-tasks`) | no se corre: la corre GitHub |
| Pruebas dirigidas en un worktree **nuevo** (`UnSoloAmarilloPorVistaTest` + `CompresionTest` + las 23 guardas de `quality/`) | — (estimado: había que compilar `:shared`, ~10 min en el perfil de arriba) | **38 s**: 31 tareas de la caché, todas las compilaciones `FROM-CACHE`, las tres tareas de prueba ejecutadas |

La caché de build compartida entre worktrees ya estaba prendida en esta Mac (`~/.gradle`), pero el
comando de siempre la anulaba con `--rerun-tasks`. Es segura con lo que hay en el repo:

- SQLDelight, los recursos de Compose y las tareas de AGP son cacheables y salieron de la caché en el
  worktree nuevo; la configuración de pruebas de Robolectric (`test_config.properties`) usa rutas
  **relativas** (`build/intermediates/…`), así que la de otro worktree sirve. No hay KSP.
- Las tareas propias (`GenerarRotulosDeUso`, `VerificaElDex`, `VerificaElManifiestoSinSms`) no son
  `@CacheableTask`: siempre corren.
- Ninguna prueba sale `FROM-CACHE` ni `UP-TO-DATE`: lo impide el build (punto 1 de «Qué cambió»).
  Comprobado: `PeriodoDeLaFechaTest` pedida dos veces seguidas con el script corrió las dos (3 s cada
  una, `:core:jvmTest` ejecutada, sin `UP-TO-DATE`).

## Lo que no se hizo, y por qué

- **Bajar el `-Xmx8192m` del demonio de Kotlin**: en la Mac no aplica (gana `~/.gradle`) y en CI es
  un máximo, no una reserva, en máquinas de 16 GB, y ninguna corrida falló por memoria. Los links de
  iOS sí lo necesitan.
- **Más trozos de `shared`**: cada trozo paga entera la compilación (~2,5 min) por ~55 s de pruebas;
  un tercero ahorraría ~20 s a cambio de otra máquina completa.
- **`org.gradle.caching=true` en el `gradle.properties` del proyecto**: también lo leería
  `paquete.yml`, que arma el JAR de producción, y esta vuelta no toca el despliegue. CI lo pide con
  `--build-cache`; la Mac ya lo tiene en `~/.gradle/gradle.properties`.
