package com.jvillada.movi.server.compartir

import com.jvillada.movi.server.time.AppClock
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * # La página que ve el tercero
 *
 * HTML y CSS servidos por el server, **sin wasm y sin login**: tiene que abrir al instante en el
 * teléfono de Caro o del asesor, que no tienen Movi ni por qué tenerlo. Pesa unos pocos KB y no
 * pide nada a ningún otro dominio.
 *
 * ## Dos piezas: la cáscara y el contenido
 *
 * - [cascara] es la misma para todo el mundo y no sabe de quién es nada. La sirve `GET /compartido`.
 *   Trae un script de doce líneas que lee el token del **fragmento** de la URL (`#…`) y lo manda por
 *   `POST` — por qué el fragmento y no la ruta está en `EnlaceCompartidoRoutes.kt`.
 * - [contenido] es el resumen de UNA persona, ya pintado. Lo contesta el `POST`, y la cáscara lo
 *   inserta tal cual. Se pinta en el server y no en JavaScript para que la regla de qué se muestra
 *   —y sobre todo qué NO, como un número de cuenta completo— viva en Kotlin, al lado de su prueba.
 *
 * ## Coherente con Movi, sin depender de la app
 *
 * Los colores son **los mismos valores** de `Tokens.kt` (`COLORES_OSCUROS` y `COLORES_CLAROS`), y
 * el tema lo decide el teléfono de quien mira (`prefers-color-scheme`). No se pueden importar —
 * `Tokens.kt` es Compose y esto es un `String`—, así que `PaginaCompartidaTest` lee `Tokens.kt` y
 * falla si algún valor de acá se desalinea. La tipografía es la de la app (Space Grotesk y Martian
 * Mono para la cifra protagonista), servida desde el mismo bundle que el server ya publica; si en
 * algún armado no está, cae a la pila del sistema sin romper nada.
 *
 * ## Todo texto que viene de la base se escapa
 *
 * Nombres de cuenta, categorías, bancos y el nombre del dueño los escribió una persona. Pasan por
 * [esc] sin excepción: un «<» en el nombre de una categoría no puede convertirse en HTML en el
 * teléfono de otro.
 */
internal object PaginaCompartida {

    private val MESES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    /** Cuántas categorías se nombran antes de juntar el resto en «Otras». */
    private const val CATEGORIAS_A_LA_VISTA = 6

    // ── La cáscara ───────────────────────────────────────────────────────────

    /**
     * El script de la cáscara. Va en una constante aparte porque la política de contenido
     * ([politicaDeContenido]) lo autoriza **por su hash**: cualquier otro script que alguien
     * consiguiera meter en la página no correría.
     *
     * Sin token (alguien abrió `/compartido` a secas) muestra lo mismo que un enlace vencido, sin
     * ir al server. `credentials: 'omit'`: la página no tiene sesión y no la necesita.
     */
    private val SCRIPT = """
(function () {
  var main = document.getElementById('contenido');
  var token = (location.hash || '').slice(1);
  function pintar(html) { main.innerHTML = html; main.setAttribute('aria-busy', 'false'); }
  if (!token) { pintar(document.getElementById('no-disponible').innerHTML); return; }
  fetch('/compartido', {
    method: 'POST', body: token, cache: 'no-store', credentials: 'omit', referrerPolicy: 'no-referrer',
    headers: { 'Content-Type': 'text/plain; charset=utf-8' }
  }).then(function (r) { return r.text(); })
    .then(pintar)
    .catch(function () { pintar(document.getElementById('sin-conexion').innerHTML); });
})();
""".trim()

    /** `sha256-…` del script, para la política de contenido. */
    private val HASH_DEL_SCRIPT: String = "sha256-" + Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(SCRIPT.toByteArray(Charsets.UTF_8)),
    )

    /**
     * La `Content-Security-Policy` de las dos respuestas. Lo mínimo que la página necesita y nada
     * más: su propio script (por hash), estilos en línea (las barras llevan su ancho en un
     * `style`), las fuentes del propio server y un `fetch` al propio server. Sin imágenes, sin
     * marcos, sin formularios, y nadie la puede meter en un `<iframe>`.
     */
    val politicaDeContenido: String =
        "default-src 'none'; script-src '$HASH_DEL_SCRIPT'; style-src 'unsafe-inline'; " +
            "font-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'; " +
            "frame-ancestors 'none'"

    private const val FUENTES = "/composeResources/com.jvillada.movi.resources/font"

    /**
     * Los valores salen de `Tokens.kt` y los vigila `PaginaCompartidaTest`. Los nombres son los
     * mismos roles (`--fondo`, `--tarjeta`, `--marca`…) para que quien lea las dos cosas vea que son
     * una sola.
     */
    private val ESTILOS = """
@font-face { font-family: 'Space Grotesk'; src: url('$FUENTES/space_grotesk_regular.ttf') format('truetype'); font-weight: 400; font-display: swap; }
@font-face { font-family: 'Space Grotesk'; src: url('$FUENTES/space_grotesk_medium.ttf') format('truetype'); font-weight: 500; font-display: swap; }
@font-face { font-family: 'Space Grotesk'; src: url('$FUENTES/space_grotesk_semibold.ttf') format('truetype'); font-weight: 600; font-display: swap; }
@font-face { font-family: 'Martian Mono'; src: url('$FUENTES/martian_mono_medium.ttf') format('truetype'); font-weight: 500 600; font-display: swap; }
:root {
  --fondo: #07090C; --tarjeta: #161D25; --borde: #26303B; --hilo: #1C242D;
  --texto: #EAEEF2; --texto-medio: #B4BEC9; --texto-apagado: #95A0AD;
  --marca: #B9A0FF; --entra: #7FD4A8; --sale: #F08A7C; --neutro: #4A5763;
  --margen: 20px; --amplio: 16px; --medio: 12px; --corto: 8px; --seccion: 32px;
  --forma-amplia: 16px; --forma-normal: 11px;
  --letra: 'Space Grotesk', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  --cifras: 'Martian Mono', ui-monospace, 'SF Mono', Menlo, monospace;
  color-scheme: dark;
}
@media (prefers-color-scheme: light) {
  :root {
    --fondo: #E9ECF1; --tarjeta: #FFFFFF; --borde: #CCD3DC; --hilo: #DDE2E9;
    --texto: #0D1218; --texto-medio: #3D4752; --texto-apagado: #5C6873;
    --marca: #5636B8; --entra: #117050; --sale: #B3392A; --neutro: #98A3AE;
    color-scheme: light;
  }
}
* { box-sizing: border-box; }
html { -webkit-text-size-adjust: 100%; text-size-adjust: 100%; }
body { margin: 0; background: var(--fondo); color: var(--texto); font-family: var(--letra);
  font-size: 13.5px; line-height: 18px; font-weight: 500; -webkit-font-smoothing: antialiased; }
main { max-width: 560px; margin: 0 auto; padding: 28px var(--margen) 40px; }
h1, h2, h3, p, ul, dl, dd { margin: 0; padding: 0; }
ul { list-style: none; }
.num { font-variant-numeric: tabular-nums; font-feature-settings: 'tnum'; white-space: nowrap; }
.marca { color: var(--marca); font-size: 15.5px; font-weight: 600; letter-spacing: -0.3px; }
h1 { margin-top: 18px; font-size: 19px; line-height: 24px; font-weight: 600; letter-spacing: -0.45px; }
.meta { margin-top: 4px; color: var(--texto-medio); font-size: 11.5px; line-height: 16px; font-weight: 400; }
.rotulo { color: var(--texto-apagado); font-size: 10.5px; line-height: 14px; font-weight: 500;
  letter-spacing: 1.7px; text-transform: uppercase; }
.apoyo { color: var(--texto-medio); font-size: 11.5px; line-height: 16px; font-weight: 400; }
.hero { margin-top: var(--seccion); }
.hero .rotulo { margin-bottom: 6px; }
.cifra { font-family: var(--cifras); font-size: clamp(28px, 10.5vw, 42px); line-height: 1.1;
  font-weight: 600; letter-spacing: -0.9px; overflow-wrap: anywhere; }
.hero .apoyo { margin-top: 8px; }
.tarjeta { margin-top: var(--amplio); background: var(--tarjeta); border: 1px solid var(--hilo);
  border-radius: var(--forma-amplia); padding: 18px var(--amplio); }
.hero + .tarjeta { margin-top: 28px; }
.tarjeta > .rotulo:first-child { margin-bottom: 8px; }
.cifra-media { font-size: 26px; line-height: 32px; font-weight: 600; letter-spacing: -0.6px; overflow-wrap: anywhere; }
.barra { display: flex; gap: 3px; height: 10px; margin-top: 14px; border-radius: 999px; overflow: hidden; }
.barra span { display: block; height: 100%; border-radius: 999px; flex: 0 0 var(--p); min-width: 4px;
  transform-origin: left center; animation: crece 600ms cubic-bezier(.2,.7,.2,1) both; }
.barra .tiene { background: var(--entra); }
.barra .debe { background: var(--sale); animation-delay: 80ms; }
.leyenda { display: flex; flex-wrap: wrap; gap: 4px 14px; margin-top: 8px; }
.leyenda > span::before { content: ''; display: inline-block; width: 8px; height: 8px; border-radius: 3px;
  margin-right: 6px; vertical-align: 0; background: var(--c); }
.renglones { margin-top: 14px; border-top: 1px solid var(--hilo); }
.renglones div { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0;
  border-bottom: 1px solid var(--hilo); }
.renglones div:last-child { border-bottom: 0; padding-bottom: 0; }
.renglones dt { color: var(--texto-medio); }
.tres { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--corto); margin-top: 14px; }
.tres p + p { margin-top: 2px; font-size: 15.5px; line-height: 20px; font-weight: 600; }
.veredicto { margin-top: 14px; padding: 10px 12px; border-radius: var(--forma-normal);
  background: var(--fondo); color: var(--texto-medio); }
.subtitulo { margin-top: 22px; margin-bottom: 4px; }
.categorias li { padding: 9px 0; }
.linea { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; }
.linea .nombre { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.linea .num { font-weight: 400; }
.pista { position: relative; height: 4px; margin-top: 6px; border-radius: 999px; background: var(--hilo); }
.pista span { position: absolute; inset: 0 auto 0 0; width: var(--p); border-radius: 999px; background: var(--marca);
  transform-origin: left center; animation: crece 600ms cubic-bezier(.2,.7,.2,1) both; }
.pct { color: var(--texto-apagado); font-size: 11.5px; font-weight: 400; margin-left: 8px; }
.deudas li { padding: 12px 0; border-bottom: 1px solid var(--hilo); }
.deudas li:first-child { padding-top: 4px; }
.deudas li:last-child { border-bottom: 0; padding-bottom: 0; }
.deudas .apoyo { margin-top: 3px; }
.vacio { color: var(--texto-medio); margin-top: 6px; }
.entra { color: var(--entra); } .sale { color: var(--sale); }
footer { margin-top: var(--seccion); text-align: center; color: var(--texto-apagado); font-size: 11.5px; font-weight: 400; }
.aviso { margin-top: 30vh; text-align: center; }
.aviso h1 { margin-top: 14px; }
.aviso .apoyo { margin-top: 8px; max-width: 320px; margin-left: auto; margin-right: auto; }
.cargando { margin-top: 30vh; text-align: center; animation: late 1.4s ease-in-out infinite; }
.contenido { animation: aparece 380ms ease-out both; }
@keyframes crece { from { transform: scaleX(0); } to { transform: scaleX(1); } }
@keyframes aparece { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
@keyframes late { 50% { opacity: .45; } }
@media (prefers-reduced-motion: reduce) { *, *::before { animation: none !important; } }
""".trim()

    /** El aviso para un enlace que no sirve. Uno solo para vencido, revocado e inventado: ver la ruta. */
    val noDisponible: String = """
<div class="aviso">
  <p class="marca">Movi</p>
  <h1>Este enlace no está disponible</h1>
  <p class="apoyo">Puede que haya vencido o que quien lo compartió lo haya revocado. Si todavía lo necesitas, pídele uno nuevo.</p>
</div>
""".trim()

    private val sinConexion = """
<div class="aviso">
  <p class="marca">Movi</p>
  <h1>No se pudo cargar el resumen</h1>
  <p class="apoyo">Revisa tu conexión y vuelve a abrir el enlace.</p>
</div>
""".trim()

    /** La página que sirve `GET /compartido`. Igual para todos: no sabe de quién es nada. */
    val cascara: String = """
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="robots" content="noindex, nofollow, noarchive">
<meta name="referrer" content="no-referrer">
<meta name="color-scheme" content="dark light">
<meta name="theme-color" content="#07090C" media="(prefers-color-scheme: dark)">
<meta name="theme-color" content="#E9ECF1" media="(prefers-color-scheme: light)">
<title>Resumen compartido · Movi</title>
<style>
$ESTILOS
</style>
</head>
<body>
<main id="contenido" aria-live="polite" aria-busy="true">
  <div class="cargando"><p class="marca">Movi</p><p class="apoyo">Cargando el resumen…</p></div>
</main>
<template id="no-disponible">$noDisponible</template>
<template id="sin-conexion">$sinConexion</template>
<noscript><main><div class="aviso"><p class="marca">Movi</p><h1>Activa JavaScript para ver el resumen</h1><p class="apoyo">La página lo necesita para leer el enlace sin que viaje a ningún registro.</p></div></main></noscript>
<script>$SCRIPT</script>
</body>
</html>
""".trim()

    // ── El contenido ─────────────────────────────────────────────────────────

    /** El resumen de una persona, listo para insertar en la cáscara. */
    fun contenido(r: ResumenCompartido): String = buildString {
        append("<div class=\"contenido\">")

        // Quién, de cuándo y hasta cuándo: lo primero que alguien que recibe un enlace necesita
        // saber es si lo que está mirando es de hoy.
        val quien = r.quien.trim().ifEmpty { "alguien" }
        append("<header>")
        append("<p class=\"marca\">Movi</p>")
        append("<h1>Resumen financiero de ${esc(quien)}</h1>")
        append("<p class=\"meta\">Datos al ${fechaYHora(r.generadoEn)}</p>")
        append("<p class=\"meta\">Este enlace vale hasta el ${fechaYHora(r.venceEn)}</p>")
        append("</header>")

        // ¿Cómo está? — la plata que puede usar hoy, grande.
        append("<section class=\"hero\">")
        append("<p class=\"rotulo\">Plata disponible</p>")
        append("<p class=\"cifra num\">${dinero(r.tuPlata)}</p>")
        if (r.condicionado > 0L) {
            val paraQue = r.condicionadoA?.let { "solo para ${esc(it)}" } ?: "de uso condicionado"
            append("<p class=\"apoyo\">Además, <span class=\"num\">${dinero(r.condicionado)}</span> $paraQue: es suya, pero no la puede usar para cualquier cosa.</p>")
        }
        append("</section>")

        seccionPatrimonio(r)
        seccionPeriodo(r)
        seccionDeudas(r)

        append("<footer>Solo lectura · compartido desde Movi</footer>")
        append("</div>")
    }

    private fun StringBuilder.seccionPatrimonio(r: ResumenCompartido) {
        append("<section class=\"tarjeta\">")
        append("<h2 class=\"rotulo\">Patrimonio neto</h2>")
        append("<p class=\"cifra-media num\">${dinero(r.patrimonio)}</p>")
        append("<p class=\"apoyo\">Lo que tiene menos lo que debe.</p>")

        // La barra se entiende sin leer números: verde lo que tiene, rojo lo que debe. Solo se
        // dibuja si hay algo que comparar; dos ceros no son una proporción.
        val tiene = r.loQueTiene.coerceAtLeast(0L)
        val debe = r.deudas.coerceAtLeast(0L)
        if (tiene + debe > 0L) {
            val pTiene = porcentaje(tiene, tiene + debe)
            append("<div class=\"barra\" role=\"img\" aria-label=\"Tiene ${compacto(tiene)} y debe ${compacto(debe)}\">")
            if (tiene > 0L) append("<span class=\"tiene\" style=\"--p:${pTiene}%\"></span>")
            if (debe > 0L) append("<span class=\"debe\" style=\"--p:${100 - pTiene}%\"></span>")
            append("</div>")
            append("<p class=\"leyenda apoyo\">")
            append("<span style=\"--c:var(--entra)\">Tiene <span class=\"num\">${compacto(tiene)}</span></span>")
            append("<span style=\"--c:var(--sale)\">Debe <span class=\"num\">${compacto(debe)}</span></span>")
            append("</p>")
        }

        append("<dl class=\"renglones\">")
        renglon("Plata disponible", dinero(r.tuPlata))
        if (r.condicionado != 0L) renglon("Uso condicionado", dinero(r.condicionado))
        r.bienes?.let { renglon("Bienes", dinero(it)) }
        // Con signo menos: es lo que resta. Una tarjeta sobrepagada deja las deudas en negativo y
        // entonces el renglón sale positivo, que es lo que es — plata a favor.
        renglon("Deudas", dinero(-r.deudas))
        append("</dl>")
        append("</section>")
    }

    private fun StringBuilder.renglon(nombre: String, valor: String) {
        append("<div><dt>$nombre</dt><dd class=\"num\">$valor</dd></div>")
    }

    private fun StringBuilder.seccionPeriodo(r: ResumenCompartido) {
        append("<section class=\"tarjeta\">")
        append("<h2 class=\"rotulo\">Este período</h2>")
        append("<p class=\"apoyo\">Del ${esc(r.rangoDelPeriodo)}</p>")
        append("<div class=\"tres\">")
        append("<div><p class=\"apoyo\">Ingresos</p><p class=\"num\">${compacto(r.ingresos)}</p></div>")
        append("<div><p class=\"apoyo\">Gastos</p><p class=\"num\">${compacto(r.gastos)}</p></div>")
        val claseFlujo = if (r.flujo < 0L) "sale" else if (r.flujo > 0L) "entra" else ""
        append("<div><p class=\"apoyo\">Flujo</p><p class=\"num $claseFlujo\">${compacto(r.flujo)}</p></div>")
        append("</div>")
        append("<p class=\"veredicto\">${veredicto(r)}</p>")

        append("<h3 class=\"rotulo subtitulo\">En qué se fue</h3>")
        val categorias = categoriasALaVista(r.gastoPorCategoria)
        if (categorias.isEmpty()) {
            append("<p class=\"vacio\">Todavía no hay gastos registrados en este período.</p>")
        } else {
            val mayor = categorias.maxOf { it.second }
            append("<ul class=\"categorias\">")
            categorias.forEach { (nombre, monto) ->
                append("<li>")
                append("<div class=\"linea\"><span class=\"nombre\">${esc(nombre)}</span>")
                append("<span><span class=\"num\">${dinero(monto)}</span><span class=\"pct num\">${porcentaje(monto, r.gastos)} %</span></span></div>")
                append("<div class=\"pista\"><span style=\"--p:${porcentaje(monto, mayor)}%\"></span></div>")
                append("</li>")
            }
            append("</ul>")
        }
        append("</section>")
    }

    private fun StringBuilder.seccionDeudas(r: ResumenCompartido) {
        append("<section class=\"tarjeta\">")
        append("<h2 class=\"rotulo\">Deudas</h2>")
        if (r.deudasConSaldo.isEmpty()) {
            append("<p class=\"vacio\">Sin deudas con saldo.</p>")
        } else {
            append("<ul class=\"deudas\">")
            r.deudasConSaldo.forEach { d ->
                append("<li>")
                append("<div class=\"linea\"><span class=\"nombre\">${esc(d.nombre)}</span><span class=\"num\">${dinero(d.saldo)}</span></div>")
                append("<p class=\"apoyo\">${esc(detalleDeLaDeuda(d))}</p>")
                append("</li>")
            }
            append("</ul>")
        }
        append("</section>")
    }

    /**
     * La línea de abajo de cada deuda: banco, cuota y tasa, lo que haya. Es lo que un asesor
     * necesita para opinar — sin la tasa no se puede decir cuál conviene abonar primero.
     */
    internal fun detalleDeLaDeuda(d: DeudaCompartida): String = listOfNotNull(
        d.banco,
        if (d.esTarjeta) "Tarjeta de crédito" else null,
        d.cuota?.let { if (d.esTarjeta) "Pago mínimo ${dinero(it)}" else "Cuota ${dinero(it)} al mes" },
        when {
            d.sinIntereses -> "Sin intereses"
            // Espacio duro: «12,4 % EA» no se parte en dos renglones.
            d.tasaEa != null -> "${tasa(d.tasaEa)}\u00A0%\u00A0EA"
            else -> null
        },
    ).joinToString(" · ").ifEmpty { "Sin detalle" }

    /**
     * Una frase que contesta «¿cómo va el período?» sin que haya que restar. Sale del flujo y de
     * nada más, para no contradecir a las cifras de al lado.
     */
    internal fun veredicto(r: ResumenCompartido): String = when {
        r.ingresos == 0L && r.gastos == 0L -> "Todavía no hay ingresos ni gastos registrados en este período."
        r.flujo > 0L -> "Este período entró ${dinero(r.flujo)} más de lo que salió."
        r.flujo < 0L -> "Este período salieron ${dinero(-r.flujo)} más de los que entraron."
        else -> "Este período entró lo mismo que salió."
    }

    /** Las más grandes con nombre; el resto, juntas en «Otras» para que la lista no se alargue. */
    internal fun categoriasALaVista(gasto: Map<String, Long>): List<Pair<String, Long>> {
        val ordenadas = gasto.filterValues { it > 0L }.entries.sortedByDescending { it.value }.map { it.key to it.value }
        if (ordenadas.size <= CATEGORIAS_A_LA_VISTA + 1) return ordenadas
        val resto = ordenadas.drop(CATEGORIAS_A_LA_VISTA).sumOf { it.second }
        return ordenadas.take(CATEGORIAS_A_LA_VISTA) + ("Otras" to resto)
    }

    // ── Formatos ─────────────────────────────────────────────────────────────

    private const val MENOS = "−"

    private fun miles(n: Long): String {
        val s = abs(n).toString()
        val out = StringBuilder()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) out.append('.')
            out.append(c)
        }
        return out.toString()
    }

    /** «$558.350», «−$2.191.000.000». El signo lo pone el formato, una vez. */
    internal fun dinero(n: Long): String = (if (n < 0) MENOS else "") + "$" + miles(n)

    /**
     * La regla de `formatMoneyCompact` de la app: debajo del millón se dicen los pesos, de ahí para
     * arriba millones con un decimal y sin ceros de relleno («$22,2M», «$2.191M»). Va en las tres
     * columnas del período, donde el formato largo no entra en un teléfono.
     */
    internal fun compacto(n: Long): String {
        val a = abs(n)
        if (a < 1_000_000L) return dinero(n)
        val signo = if (n < 0) MENOS else ""
        val decimas = (a + 50_000L) / 100_000L
        val entero = decimas / 10
        val frac = decimas % 10
        return if (frac == 0L) "$signo$${miles(entero)}M" else "$signo$${miles(entero)},${frac}M"
    }

    /** Entero entre 0 y 100. Con una parte chiquita pero no nula devuelve al menos 1, para que se vea. */
    private fun porcentaje(parte: Long, total: Long): Int {
        if (total <= 0L || parte <= 0L) return 0
        val p = (parte.toDouble() * 100.0 / total.toDouble()).roundToLong().toInt()
        return p.coerceIn(1, 100)
    }

    private fun tasa(t: Double): String {
        val redondeada = (t * 100).roundToLong() / 100.0
        val texto = if (redondeada == redondeada.toLong().toDouble()) redondeada.toLong().toString() else redondeada.toString()
        return texto.replace('.', ',')
    }

    /** «23 de septiembre de 2026, 10:42 a. m.», en la zona de la app (Bogotá). */
    internal fun fechaYHora(epochMs: Long): String {
        val t = Instant.ofEpochMilli(epochMs).atZone(AppClock.zone)
        val hora12 = if (t.hour % 12 == 0) 12 else t.hour % 12
        val ampm = if (t.hour < 12) "a. m." else "p. m."
        val minutos = t.minute.toString().padStart(2, '0')
        return "${t.dayOfMonth} de ${MESES[t.monthValue - 1]} de ${t.year}, $hora12:$minutos $ampm"
    }

    /** Escape de HTML para todo texto que venga de la base. Ver el KDoc del objeto. */
    internal fun esc(s: String): String = buildString(s.length) {
        s.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
