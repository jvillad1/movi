package com.jvillada.movi.server.ai

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ComoVaLaDeuda
import com.jvillada.movi.shared.model.Patrimonio
import com.jvillada.movi.shared.model.claseDeBien
import com.jvillada.movi.shared.model.deudaDelBien
import com.jvillada.movi.shared.model.esCuentaDeDeuda
import com.jvillada.movi.shared.model.estadoDePresupuesto
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.resumirDeudas
import com.jvillada.movi.shared.model.tasaMensualDeUnaEA
import com.jvillada.movi.shared.model.valorEnPesosDe
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * # Los datos exactos para ESTA pregunta
 *
 * El contexto general ya trae casi todo, pero lo trae **en renglones para leer**, y el 23-sep el
 * modelo leyó el renglón del Hipotecario 2334 y rehízo una cuenta que el renglón ya traía hecha:
 * restó cuota menos interés, se olvidó de los seguros y le dijo al dueño que la deuda bajaba
 * $185.831 al mes cuando en realidad crece unos $23.388. La aritmética que un modelo hace mal es
 * justo la que una función hace bien.
 *
 * Así que antes de llamar al modelo, el server mira **qué nombra la pregunta** —una cuenta, un
 * crédito, una tarjeta, un bien, una categoría, un presupuesto, un recurrente, «este período»,
 * «patrimonio», «intereses»— y para cada cosa arma un bloque con **todo lo que el modelo podría
 * querer calcular, ya calculado**: para un crédito, lo que queda de la cuota después de los
 * seguros, el interés del mes, cuánto baja o crece la deuda, cuántas cuotas faltan y quién paga;
 * para una categoría, lo gastado, cuánto falta o cuánto se pasó y los tres gastos más grandes.
 *
 * Tres reglas:
 *
 * 1. **Las mismas funciones que las pantallas** ([com.jvillada.movi.shared.model.planDeUnaDeuda],
 *    [com.jvillada.movi.shared.model.patrimonioDe], [estadoDePresupuesto]): la cifra del chat y
 *    la de la pantalla tienen que ser la misma, o el dueño no sabe a cuál creerle.
 * 2. **Va en el mensaje del turno, no en el sistema.** La PERSONA y el contexto van cacheados, lo
 *    estable primero; este bloque cambia con cada pregunta, así que va DESPUÉS de todo lo cacheado,
 *    pegado a la pregunta. Si fuera al sistema, cada pregunta tiraría la caché entera.
 * 3. **Si la pregunta no nombra nada, no se agrega nada.** Ni una ficha de más para «hola».
 */

/** Lo que hace falta para armar los hechos, ya leído de la base por `cargarDatosDelUsuario`. */
internal data class DatosParaLosHechos(
    val cuentas: List<Account>,
    val patrimonio: Patrimonio,
    val periodo: ContextoDelPeriodo,
    /** Categoría → límite mensual, como en la tabla `budgets`. */
    val presupuestos: List<Pair<String, Long>>,
)

/**
 * **Cuántas cosas nombradas entran como máximo.** Una pregunta que nombra más de cinco cosas no
 * necesita el detalle de todas —necesita el contexto general, que ya viaja—, y cada bloque es
 * entrada que se paga.
 */
internal const val MAXIMO_DE_COSAS_NOMBRADAS = 5

internal const val TITULO_DE_LOS_HECHOS = "DATOS EXACTOS PARA ESTA PREGUNTA"

/**
 * Palabras que, **solas**, no alcanzan para decir de qué cosa se habla. «¿Qué hipoteca pago
 * primero?» es una pregunta sobre todas las hipotecas, no sobre la única que se llame «Hipoteca
 * 1254»; y «cuenta» está en el nombre de media app. El nombre completo sí enlaza siempre.
 */
private val PALABRAS_QUE_NO_ALCANZAN = setOf(
    "cuenta", "cuentas", "tarjeta", "tarjetas", "credito", "creditos", "hipoteca", "hipotecas",
    "hipotecario", "prestamo", "prestamos", "deuda", "deudas", "ahorro", "ahorros", "corriente",
    "pension", "voluntaria", "banco", "gasto", "gastos", "pago", "pagos", "cuota", "cuotas",
    "plata", "otros", "otro", "extra", "libre", "inversion", "mensual", "master", "black", "visa",
    "nomina", "para", "este", "esta",
)

/** Las palabras de un texto, sin tildes ni mayúsculas ni signos: «¿Hipotecario 2334?» → [hipotecario, 2334]. */
private fun palabrasDe(texto: String): List<String> =
    normalizarParaBuscar(texto).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

/**
 * **Qué nombres de [nombres] nombra la pregunta**, en el orden en que aparecen en ella.
 *
 * Un nombre queda enlazado si la pregunta lo trae **entero** («Hipotecario 2334», «Mercado
 * extra»), o si trae una palabra que **solo ese nombre tiene** y que alcanza para distinguirlo: un
 * número de tres o más dígitos («el 2334»), o una palabra de cuatro letras o más que no esté en
 * [PALABRAS_QUE_NO_ALCANZAN] («Skandia», «Almendros»). Todo sin tildes ni mayúsculas.
 *
 * Lo que NO hace, a propósito: adivinar («la hipoteca grande»). Un enlace equivocado le pone al
 * modelo los datos de otra cosa como si fueran «los exactos»; uno que falta solo lo deja con el
 * contexto general, que ya trae todo.
 */
internal fun queNombraLaPregunta(pregunta: String, nombres: Collection<String>): List<String> {
    val enLaPregunta = palabrasDe(pregunta)
    if (enLaPregunta.isEmpty()) return emptyList()
    val frase = " " + enLaPregunta.joinToString(" ") + " "
    val palabrasPorNombre = nombres.distinct().associateWith(::palabrasDe).filterValues { it.isNotEmpty() }
    val cuantosLaTienen = palabrasPorNombre.values.flatMap { it.distinct() }.groupingBy { it }.eachCount()

    val enteros = palabrasPorNombre.mapNotNull { (nombre, palabras) ->
        val donde = frase.indexOf(" " + palabras.joinToString(" ") + " ")
        if (donde >= 0) nombre to donde else null
    }
    // «Mercado» adentro de «Mercado extra»: si los dos se nombran enteros en el mismo lugar, se
    // habla del largo.
    val sinLosDeAdentro = enteros.filterNot { (nombre, donde) ->
        val suyas = palabrasPorNombre.getValue(nombre).joinToString(" ")
        enteros.any { (otro, dondeOtro) ->
            otro != nombre && palabrasPorNombre.getValue(otro).joinToString(" ").let { largo ->
                largo.length > suyas.length && " $largo ".contains(" $suyas ") &&
                    donde >= dondeOtro && donde < dondeOtro + largo.length + 1
            }
        }
    }
    val yaEstan = sinLosDeAdentro.map { it.first }.toSet()
    val porUnaPalabra = palabrasPorNombre.filterKeys { it !in yaEstan }.mapNotNull { (nombre, palabras) ->
        palabras.firstNotNullOfOrNull { palabra ->
            val distingue = (palabra.all { it.isDigit() } && palabra.length >= 3) ||
                (palabra.length >= 4 && palabra.any { it.isLetter() } && palabra !in PALABRAS_QUE_NO_ALCANZAN)
            val soloEste = cuantosLaTienen[palabra] == 1
            val donde = enLaPregunta.indexOf(palabra)
            if (distingue && soloEste && donde >= 0) nombre to frase.indexOf(" $palabra ") else null
        }
    }
    return (sinLosDeAdentro + porUnaPalabra).sortedBy { it.second }.map { it.first }
}

/**
 * **El bloque de hechos para [pregunta]**, o `null` si no nombra nada que Movi sepa calcular.
 *
 * Es texto para el modelo, no para el dueño —él nunca lo ve—, así que habla del dueño en tercera
 * persona como el resto de los datos. Las cifras van con el formato de la app («$2.404.495»,
 * «15,24 %») para que el modelo las copie tal cual en vez de reformatearlas a mano.
 */
internal fun hechosParaLaPregunta(pregunta: String, datos: DatosParaLosHechos): String? {
    val p = datos.periodo
    val creditoPorCuenta = p.creditos.associateBy { it.cuenta }
    val cuentaPorNombre = datos.cuentas.associateBy { it.name }
    val categorias = (p.gastoPorCategoria.keys + datos.presupuestos.map { it.first } + p.recurrentes.map { it.categoria })
        .filter { it.isNotBlank() }.toSet()
    val recurrentePorNombre = p.recurrentes.associateBy { it.nombre }
    val suscripcionPorNombre = p.suscripciones.associateBy { it.nombre }

    // Una cosa, un bloque: un crédito es también una cuenta LOAN, y se cuenta como crédito.
    val nombres = (creditoPorCuenta.keys + cuentaPorNombre.keys + categorias + recurrentePorNombre.keys + suscripcionPorNombre.keys)
    val nombradas = queNombraLaPregunta(pregunta, nombres).take(MAXIMO_DE_COSAS_NOMBRADAS)

    val bloques = mutableListOf<String>()
    nombradas.forEach { nombre ->
        val credito = creditoPorCuenta[nombre]
        val cuenta = cuentaPorNombre[nombre]
        when {
            credito != null -> bloques += hechosDelCredito(credito)
            cuenta != null -> bloques += hechosDeLaCuenta(cuenta, datos)
            nombre in categorias -> bloques += hechosDeLaCategoria(nombre, datos)
            nombre in recurrentePorNombre -> bloques += hechosDelRecurrente(recurrentePorNombre.getValue(nombre))
            nombre in suscripcionPorNombre -> bloques += hechosDeLaSuscripcion(suscripcionPorNombre.getValue(nombre))
        }
    }

    val limpia = " " + palabrasDe(pregunta).joinToString(" ") + " "
    fun hablaDe(vararg trozos: String) = trozos.any { it in limpia }
    if (hablaDe(" periodo ", " este mes ", " me alcanza ", " me queda ")) bloques += hechosDelPeriodo(p)
    if (hablaDe(" patrimonio ", " cuanto tengo ", " mi plata ", " tu plata ")) bloques += hechosDelPatrimonio(datos.patrimonio)
    if (hablaDe(" interes ", " intereses ")) hechosDeLosIntereses(p.creditos)?.let { bloques += it }
    if (hablaDe(" presupuesto ", " presupuestos ") && nombradas.none { it in categorias }) {
        hechosDeLosPresupuestos(datos)?.let { bloques += it }
    }

    if (bloques.isEmpty()) return null
    return buildString {
        appendLine(
            "$TITULO_DE_LOS_HECHOS (calculados por Movi con las mismas cuentas que sus pantallas; " +
                "usa estas cifras tal cual y no las recalcules):",
        )
        bloques.forEach { appendLine(); append(it.trimEnd()); appendLine() }
    }.trimEnd()
}

// ── Un bloque por cosa ───────────────────────────────────────────────────────

internal fun hechosDelCredito(c: CreditoParaContexto): String = buildString {
    appendLine("Crédito «${c.cuenta}» (${c.banco}):")
    c.saldo?.let { appendLine("- Debe hoy: ${pesos(it)}") } ?: appendLine("- Saldo de hoy: no se sabe (falta registrar el desembolso o está en otra moneda)")
    if (c.sinIntereses) {
        appendLine("- NO cobra intereses")
    } else {
        appendLine("- Tasa: ${porcentaje(c.tasaEa, 2)} EA (${porcentaje(tasaMensualDeUnaEA(c.tasaEa) * 100, 2)} mensual)")
    }
    appendLine("- Cuota: ${pesos(c.cuota)} el día ${c.dia}; plazo pactado ${c.plazoMeses} meses")
    val seguro = c.seguroMensual?.takeIf { it > 0L } ?: 0L
    val otros = c.otrosCargosMensuales?.takeIf { it > 0L } ?: 0L
    // «Seguros» en plural y sin apellido: el campo suma todos (vida, incendio, terremoto). Ver
    // `renglonDelCredito`, donde «seguro de vida» ya le hizo decir al modelo algo falso.
    if (seguro > 0L) appendLine("- Seguros dentro de la cuota (todos los que cobra el crédito): ${pesos(seguro)} al mes (no bajan la deuda)")
    if (otros > 0L) appendLine("- Otros cargos dentro de la cuota: ${pesos(otros)} al mes (no bajan la deuda)")
    if (seguro + otros > 0L) {
        appendLine("- De la cuota, después de ${pesos(seguro + otros)} de cargos, le quedan ${pesos(c.cuota - seguro - otros)} para interés y capital")
    }
    appendLine("- Quién paga la cuota: " + quienPaga(c))

    val plan = c.plan ?: return@buildString
    if (!c.sinIntereses) {
        val fraccion = plan.fraccionDeInteres?.let { " (el ${porcentaje(it * 100, 1)} de la cuota)" }.orEmpty()
        appendLine("- Interés de este mes: ${pesos(plan.interes)}$fraccion")
    }
    when (plan.comoVa) {
        ComoVaLaDeuda.AMORTIZA -> {
            appendLine("- Qué hace la cuota con la deuda: la BAJA ${pesos(plan.capital)} este mes")
            plan.mesesHastaLaUltimaCuota?.let { appendLine("- A este ritmo le quedan $it cuotas") }
            plan.interesPorPagar?.let { appendLine("- Intereses que faltan por pagar hasta la última cuota: ${pesos(it)}") }
        }
        ComoVaLaDeuda.SOLO_INTERESES ->
            appendLine("- Qué hace la cuota con la deuda: casi nada; lo que queda de la cuota apenas cubre el interés (la deuda cambia ${pesos(abs(plan.capital))} al mes)")
        ComoVaLaDeuda.LA_DEUDA_CRECE -> {
            appendLine("- Qué hace la cuota con la deuda: la deuda CRECE unos ${pesos(-plan.capital)} al mes aunque pague, porque lo que queda de la cuota no alcanza para el interés")
            appendLine("- A este ritmo NO se termina de pagar")
        }
        ComoVaLaDeuda.SIN_TASA -> appendLine("- Sin tasa registrada: no se puede separar interés de capital")
        ComoVaLaDeuda.SIN_CUOTA -> appendLine("- Sin cuota registrada: no se puede proyectar")
        ComoVaLaDeuda.SIN_DEUDA -> appendLine("- No hay deuda registrada hoy")
    }
}

/** Quién paga, en palabras: la misma decisión que el renglón del contexto ([renglonDelCredito]). */
private fun quienPaga(c: CreditoParaContexto): String = when {
    c.porNomina -> "la descuenta la nómina antes de que llegue el sueldo; NO sale de su cuenta"
    c.loPagaCuentaPropia != null ->
        "se paga con plata de su propia cuenta «${c.loPagaCuentaPropia}» (no es un seguro ni un tercero); " +
            "no sale de su plata del día a día, pero es plata suya"
    !c.loPaga.isNullOrBlank() -> "la paga ${c.loPaga}; NO sale de su cuenta (los datos no dicen qué es ${c.loPaga})"
    else -> "sale de su bolsillo"
}

private fun hechosDeLaCuenta(cuenta: Account, datos: DatosParaLosHechos): String = buildString {
    val bien = cuenta.bien
    if (bien != null) {
        appendLine("Bien «${cuenta.name}» (${claseDeBien(bien.clase).nombre}):")
        append("- Vale ${pesos(bien.valor)}")
        bien.valorAl?.let { append(" según el avalúo del $it") }
        appendLine("; es un bien, NO plata disponible")
        deudaDelBien(cuenta, datos.cuentas)?.let { d ->
            appendLine("- Lo financia «${d.deuda.name}», que debe ${pesos(d.debes)}: lo suyo de verdad son ${pesos(d.tuyo)}")
        }
        return@buildString
    }
    val esTarjeta = cuenta.type == AccountType.CREDIT_CARD
    appendLine("${if (esTarjeta) "Tarjeta" else "Cuenta"} «${cuenta.name}» (${cuenta.type}):")
    val valor = valorEnPesosDe(cuenta)
    if (esCuentaDeDeuda(cuenta.type)) appendLine("- Debe hoy: ${pesos(valor)}")
    else appendLine("- Saldo hoy: ${pesos(valor)}")
    cuenta.balancesByCurrency.filter { (moneda, saldo) -> moneda != "COP" && saldo != 0L }.forEach { (moneda, saldo) ->
        appendLine("- Además tiene $moneda ${numero(saldo)} (incluidos arriba a la TRM de hoy)")
    }
    cuenta.condicionadaA?.let { appendLine("- NO es plata disponible: solo se puede usar para $it") }
    val cuotasQuePaga = datos.periodo.creditos.filter { it.loPagaCuentaPropia == cuenta.name }
    if (cuotasQuePaga.isNotEmpty()) {
        appendLine("- De esta cuenta sale la cuota de: " + cuotasQuePaga.joinToString("; ") { "«${it.cuenta}» (${pesos(it.cuota)} al mes)" })
    }
    val gastos = datos.periodo.gastos.filter { it.cuenta == cuenta.name }
    if (gastos.isNotEmpty()) {
        appendLine("- Gastado desde ${if (esTarjeta) "esta tarjeta" else "esta cuenta"} en este período (${datos.periodo.rango}): ${pesos(gastos.sumOf { it.monto })}")
        appendLine("- Los gastos más grandes: " + losMasGrandes(gastos, conCuenta = false))
    }
}

private fun hechosDeLaCategoria(categoria: String, datos: DatosParaLosHechos): String = buildString {
    val p = datos.periodo
    val gastado = p.gastoPorCategoria[categoria] ?: 0L
    appendLine("Categoría «$categoria», en este período (${p.rango}):")
    appendLine("- Gastado: ${pesos(gastado)}" + if (gastado == 0L) " (sin gastos en esta categoría este período)" else "")
    datos.presupuestos.firstOrNull { it.first == categoria }?.second?.let { limite ->
        appendLine("- " + estadoDelPresupuesto(gastado, limite))
    }
    val gastos = p.gastos.filter { it.categoria == categoria }
    if (gastos.isNotEmpty()) appendLine("- Los gastos más grandes: " + losMasGrandes(gastos, conCuenta = true))
    p.recurrentes.filter { it.categoria == categoria && !it.esIngreso }.forEach { r ->
        appendLine("- Recurrente de esta categoría: ${renglonDelRecurrente(r)}")
    }
}

private fun estadoDelPresupuesto(gastado: Long, limite: Long): String {
    val va = if (limite > 0L) " (va en el ${porcentaje(gastado * 100.0 / limite, 0)} del límite)" else ""
    return if (estadoDePresupuesto(gastado, limite).estaSuperado) {
        "Presupuesto: límite ${pesos(limite)}; SE PASÓ por ${pesos(gastado - limite)}$va"
    } else {
        "Presupuesto: límite ${pesos(limite)}; le quedan ${pesos(limite - gastado)}$va"
    }
}

private fun hechosDelRecurrente(r: RecurrenteParaContexto): String =
    "Recurrente «${r.nombre}» (${r.categoria}): ${renglonDelRecurrente(r)}"

private fun renglonDelRecurrente(r: RecurrenteParaContexto): String {
    val que = if (r.esIngreso) "entra" else "sale"
    val estado = if (r.yaOcurrioEnElPeriodo) "YA ocurrió en este período" else "TODAVÍA no ocurrió en este período"
    return "${r.nombre} ${pesos(r.monto)}, $que el día ${r.dia} — $estado"
}

private fun hechosDeLaSuscripcion(s: SuscripcionParaContexto): String = buildString {
    append("Suscripción «${s.nombre}»: ${pesos(s.montoMensualCop)} al mes, el día ${s.dia}")
    when {
        s.esAnual -> append(" (el cobro real es ${s.moneda} ${numero(s.montoNativo)} una vez al año; al año son ${pesos(s.montoMensualCop * 12)})")
        s.moneda != "COP" -> append(" (el cobro real es ${s.moneda} ${numero(s.montoNativo)} al mes, a la TRM de hoy); al año son ${pesos(s.montoMensualCop * 12)}")
        else -> append("; al año son ${pesos(s.montoMensualCop * 12)}")
    }
}

private fun hechosDelPeriodo(p: ContextoDelPeriodo): String = buildString {
    val gastos = p.gastoPorCategoria.values.sum()
    val flujo = p.ingresos - gastos
    appendLine("Este período (${p.rango}; quedan ${p.diasQueQuedan} días):")
    appendLine("- Ingresos: ${pesos(p.ingresos)}; gastos: ${pesos(gastos)}; flujo (ingresos − gastos): ${pesos(flujo)}")
    val pendientes = p.recurrentes.filter { !it.esIngreso && !it.yaOcurrioEnElPeriodo }.sumOf { it.monto }
    if (pendientes > 0L) {
        appendLine("- Gastos recurrentes que todavía no ocurrieron: ${pesos(pendientes)}")
        appendLine("- Si salen todos, el flujo del período quedaría en ${pesos(flujo - pendientes)}")
    }
}

private fun hechosDelPatrimonio(pat: Patrimonio): String = buildString {
    appendLine("Patrimonio (la misma cuenta que el Inicio):")
    appendLine("- Tu plata (disponible): ${pesos(pat.tuPlata)}")
    if (pat.condicionado != 0L) appendLine("- Plata con destino (suya, NO disponible): ${pesos(pat.condicionado)}")
    if (pat.bienes != 0L) appendLine("- Bienes (no son plata): ${pesos(pat.bienes)}")
    appendLine("- Deudas: ${pesos(pat.deudas)}")
    appendLine("- Patrimonio neto: ${pesos(pat.neto)}")
}

private fun hechosDeLosIntereses(creditos: List<CreditoParaContexto>): String? {
    val conPlan = creditos.mapNotNull { c -> c.plan?.let { c to it } }.filter { (c, _) -> !c.sinIntereses }
    if (conPlan.isEmpty()) return null
    val resumen = resumirDeudas(conPlan.map { it.second })
    return buildString {
        appendLine("Intereses de este mes, crédito por crédito (del más caro al más barato):")
        conPlan.sortedByDescending { it.second.interes }.forEach { (c, plan) ->
            appendLine("- «${c.cuenta}»: ${pesos(plan.interes)} (tasa ${porcentaje(c.tasaEa, 2)} EA; ${if (c.saleDeSuBolsillo) "sale de su bolsillo" else "no sale de su cuenta"})")
        }
        appendLine("- Total: ${pesos(resumen.interesMensualPropio)} en los que salen de su bolsillo y ${pesos(resumen.interesMensualAjeno)} en los que paga la nómina, otra cuenta o un tercero")
    }
}

private fun hechosDeLosPresupuestos(datos: DatosParaLosHechos): String? {
    if (datos.presupuestos.isEmpty()) return null
    return buildString {
        appendLine("Presupuestos de este período:")
        datos.presupuestos.forEach { (categoria, limite) ->
            val gastado = datos.periodo.gastoPorCategoria[categoria] ?: 0L
            appendLine("- $categoria: gastado ${pesos(gastado)}. " + estadoDelPresupuesto(gastado, limite))
        }
    }
}

private fun losMasGrandes(gastos: List<GastoDelPeriodo>, conCuenta: Boolean): String =
    gastos.sortedByDescending { it.monto }.take(3).joinToString("; ") { g ->
        val donde = if (conCuenta) "${g.categoria}, ${g.cuenta}" else g.categoria
        "${g.fecha} · ${g.nombre} ($donde): ${pesos(g.monto)}"
    }

// ── Las cifras que se sabe que son una lectura equivocada ────────────────────

/**
 * **Restas que parecen correctas y no lo son**, para el verificador ([cifrasSinRespaldo]).
 *
 * Es la cifra exacta del 23-sep: en un crédito con seguros u otros cargos dentro de la cuota,
 * `cuota − interés` NO es lo que baja la deuda —falta restar los cargos—. Los dos números están en
 * el mismo renglón, así que para un verificador que acepta restas de dos datos esa cuenta es
 * legítima; lo que está mal es su significado, y los números solos no lo ven. Acá se declara de
 * antemano, crédito por crédito. **No viaja al modelo**: es un dato del server para revisar la
 * respuesta, y por eso no cuesta ni una ficha.
 *
 * Valor → qué es, para el log.
 */
internal fun cifrasTrampa(creditos: List<CreditoParaContexto>): Map<Long, String> = creditos.mapNotNull { c ->
    val plan = c.plan ?: return@mapNotNull null
    val cargos = plan.seguro + plan.otrosCargos
    val sinCargos = c.cuota - plan.interes
    if (cargos > 0L && plan.interes > 0L && sinCargos > 0L && sinCargos != plan.capital) {
        sinCargos to "«${c.cuenta}»: cuota − interés sin restar ${pesos(cargos)} de cargos"
    } else {
        null
    }
}.toMap()

// ── Formato ──────────────────────────────────────────────────────────────────

/** «$2.404.495», «-$23.388»: el formato de la app, para que el modelo lo copie tal cual. */
internal fun pesos(valor: Long): String = (if (valor < 0) "-$" else "$") + numero(abs(valor))

private fun numero(valor: Long): String {
    val digitos = abs(valor).toString()
    val conPuntos = digitos.reversed().chunked(3).joinToString(".").reversed()
    return if (valor < 0) "-$conPuntos" else conPuntos
}

/** «15,24 %»: con coma decimal, como lo escribe la app. */
private fun porcentaje(valor: Double, decimales: Int): String {
    val factor = Math.pow(10.0, decimales.toDouble())
    val redondeado = (valor * factor).roundToLong()
    val entero = redondeado / factor.toLong()
    val resto = abs(redondeado % factor.toLong()).toString().padStart(decimales, '0')
    return if (decimales == 0) "$entero %" else "$entero,$resto %"
}
