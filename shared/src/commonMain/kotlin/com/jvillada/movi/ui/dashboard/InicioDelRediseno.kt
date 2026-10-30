package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.candidatosParecidos

import com.jvillada.movi.ui.accounts.tipoSiElNombreNoLoDice
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.CambioDeCategoriaDeUnRecurrente
import com.jvillada.movi.shared.model.fraseDelCierre
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.shared.model.rangoCortoDelPeriodo
import com.jvillada.movi.shared.model.rangoDelPeriodo
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ANCHO_DE_LECTURA
import com.jvillada.movi.ui.components.WindowWidthClass
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import com.jvillada.movi.ui.quickadd.pantallaDelAtajo
import com.jvillada.movi.ui.recurrentes.fechaDeLaFila
import com.jvillada.movi.ui.recurrentes.hojaParaAnotar
import com.jvillada.movi.shared.model.Pendiente
import com.jvillada.movi.shared.model.cifraDelPago
import com.jvillada.movi.shared.model.colaDePendientes
import com.jvillada.movi.shared.model.pagosEnOrdenDeUrgencia
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.ui.transactions.CHIP_TODO
import com.jvillada.movi.ui.transactions.MovementRow
import com.jvillada.movi.ui.transactions.collapseTransfers
import com.jvillada.movi.ui.transactions.diasVisibles
import com.jvillada.movi.ui.porrevisar.ConteoPorRevisar
import com.jvillada.movi.ui.porrevisar.desgloseDePorRevisar
import com.jvillada.movi.ui.porrevisar.textoDePorRevisar

/**
 * # El Inicio del rediseño (ola R1, generación 9)
 *
 * Lo que deciden el encabezado, «Movi propone hoy» y el carrusel de cuentas, puro y probado. Las
 * pantallas (`SeccionesDelInicio.kt`) solo eligen el componente de `ds` y el ícono: si una decisión
 * sobre la plata se escribe allá, está en el lugar equivocado.
 *
 * **Ninguna cifra se calcula acá.** Cada propuesta toma su monto tal como llega (el de un pago del
 * checklist, el de un débito, la diferencia que ya trae un cuadre): nada se suma a mano.
 */

// ── El encabezado ────────────────────────────────────────────────────────────

/** «Hola, Juan» con el primer nombre del dueño, o «Hola» si no se sabe. */
fun saludoDelInicio(nombre: String?): String {
    val primero = nombre?.trim()?.split(' ')?.firstOrNull { it.isNotBlank() }
    return if (primero.isNullOrBlank()) "Hola" else "Hola, $primero"
}

/**
 * El período, debajo del saludo; o `null` mientras el perfil no contesta.
 *
 * **Vuelta 54: con un corte a mitad de mes, el rango corto** —«23 oct – 24 nov» ([rangoCortoDelPeriodo])—.
 * Era el nombre, «Noviembre 2026», y el 28 de octubre eso confundía: el período se llama por el mes en
 * que termina y nada lo explicaba. El rango largo («Del 23 de octubre al 24 de noviembre») se cortaba a
 * 360 dp (vuelta de diseño 11); el corto entra, y dice lo mismo que la línea «Del … al …» de Pagos. Con
 * el mes de calendario no hay nada que aclarar: sigue el nombre, «Octubre 2026». El rango entero va en
 * la descripción para el lector de pantalla ([descripcionDelPeriodoDelEncabezado]).
 */
fun periodoDelEncabezado(data: DashboardData): String? = data.periodoActual?.let { periodo ->
    if (data.ajustesDePeriodo.esMesDeCalendario) tituloDelPeriodo(periodo) else rangoCortoDelPeriodo(periodo, data.ajustesDePeriodo)
}

/** El comienzo de la línea del encabezado cuando las cifras a la vista son de antes de una carga caída. */
const val SIN_CONEXION: String = "Sin conexión"

/**
 * Vuelta 68 · **«Sin conexión · cifras de las 9:06»**: lo que dice el encabezado del Inicio cuando la
 * última carga falló y lo que se ve son las cifras guardadas ([cifrasDe]: cuándo se leyeron). Antes
 * el Inicio mostraba la cifra guardada como si fuera de ahora, y el único error era un aviso de abajo
 * que se iba solo (la revisión `r3-err-inicio.png`). «de las 9:06» si son de hoy, «de ayer a las
 * 21:05», «del 3 de octubre a las 9:40». `null` si no se sabe de cuándo son.
 */
fun lineaSinConexion(cifrasDe: Long?, ahora: Long): String? {
    val cuando = cifrasDe?.takeIf { it > 0L }?.let { deCuandoEs(it, ahora) } ?: return null
    val de = when {
        cuando.startsWith("a las ") -> "de " + cuando.removePrefix("a ")
        cuando.startsWith("el ") -> "del " + cuando.removePrefix("el ")
        else -> "de $cuando"
    }
    return "$SIN_CONEXION · cifras $de"
}

/**
 * Lo que va debajo del saludo: «Actualizando…» mientras se recarga con cifras a la vista; la línea
 * [lineaSinConexion] si la carga falló y se ven las guardadas; si no, el período.
 */
fun subtituloDelEncabezadoDelInicio(
    data: DashboardData,
    actualizandoConDatos: Boolean,
    falloLaCarga: Boolean,
    cifrasDe: Long?,
    ahora: Long,
): String? = when {
    actualizandoConDatos -> "Actualizando…"
    falloLaCarga && data.puedeAfirmarVacio -> lineaSinConexion(cifrasDe, ahora) ?: periodoDelEncabezado(data)
    else -> periodoDelEncabezado(data)
}

/** Lo que dice el lector de pantalla del período del encabezado: «Tu período: del 23 de octubre al 24 de noviembre». */
fun descripcionDelPeriodoDelEncabezado(data: DashboardData): String? = data.periodoActual?.let { periodo ->
    "Tu período: " + rangoDelPeriodo(periodo, data.ajustesDePeriodo).replaceFirstChar { it.lowercase() }
}

/**
 * **El héroe pide la primera cuenta**: las cuentas ya contestaron y no hay ninguna (`null` es que
 * todavía no se sabe, y ahí va el esqueleto). Con esto el héroe es el vacío que enseña con «Crear mi
 * primera cuenta», y es el ÚNICO llamado a crearla en el Inicio (vuelta de diseño 11): «Primeros pasos»
 * va debajo del héroe y sin ese paso, en vez de arriba repitiendo el mismo botón.
 */
fun elHeroePideLaCuenta(data: DashboardData): Boolean = data.accounts?.isEmpty() == true

// ── La cuadrícula de acciones ────────────────────────────────────────────────

/**
 * Las acciones de la cuadrícula del Inicio (camino 3), en su orden.
 *
 * **Ocho, en dos filas de cuatro** (vuelta 39, la opción 3: el dueño eligió las 8). La vuelta 26 las
 * había dejado en cuatro y mandado Traspaso, Pagar tarjeta, Repartir y Comprobante al menú del «+»;
 * la vuelta 38 sacó el «+» del teléfono y puso «Más formas de anotar» de provisional. Ahora vuelven a
 * la cuadrícula, y el «+» con su menú queda solo en el rail de pantalla ancha.
 *
 * [rotulo] es lo que se lee debajo del círculo, en **dos renglones de alto fijo** («Anotar / gasto»):
 * a 360 dp ninguno parte una palabra (ver `SinPartirPalabras`). [descripcion] es la frase entera que
 * dice el lector de pantalla. Los pinta el cliente: la definición SDUI solo trae la sección
 * `ACCIONES`, así que un APK viejo sigue con las suyas y el server no cambia.
 */
enum class AccionDelInicio(val rotulo: String, val descripcion: String, val destino: DestinoDeLaAccion) {
    ANOTAR_GASTO("Anotar gasto", "Anotar un gasto", DestinoDeLaAccion.Atajo(AtajoDeAgregar.GASTO)),
    ANOTAR_INGRESO("Anotar ingreso", "Anotar un ingreso", DestinoDeLaAccion.Atajo(AtajoDeAgregar.INGRESO)),
    TRASPASAR("Traspasar", "Anotar un traspaso entre tus cuentas", DestinoDeLaAccion.Atajo(AtajoDeAgregar.TRASPASO)),
    PAGAR_TARJETA(com.jvillada.movi.ui.quickadd.PAGAR_TARJETA, "Anotar el pago de una tarjeta", DestinoDeLaAccion.Atajo(AtajoDeAgregar.PAGO_DE_TARJETA)),
    REPARTIR("Repartir", "Repartir un gasto con alguien", DestinoDeLaAccion.Atajo(AtajoDeAgregar.REPARTIR)),
    SUBIR_COMPROBANTE("Subir comprobante", "Subir un comprobante para que Movi lo lea", DestinoDeLaAccion.Atajo(AtajoDeAgregar.SUBIR_COMPROBANTE)),
    REVISAR_AVISOS("Revisar avisos", "Revisar los avisos", DestinoDeLaAccion.Pantalla(Screen.PorRevisar)),
    PREGUNTAR_A_MOVI("Preguntar a Movi", "Preguntarle a Movi", DestinoDeLaAccion.Pantalla(Screen.AIChat())),
}

/**
 * **A dónde lleva una acción: a lo que ya existe.** Las seis de anotar son los atajos del menú del «+»
 * ([AtajoDeAgregar]): la cáscara sabe hacerlos (`hacerElAtajo`: la hoja de «Agregar» en su pestaña, las
 * hojas de «Repartir», el selector del comprobante), así que la cuadrícula abre exactamente lo mismo.
 * Revisar y preguntar son pantallas.
 */
sealed interface DestinoDeLaAccion {
    data class Atajo(val atajo: AtajoDeAgregar) : DestinoDeLaAccion
    data class Pantalla(val pantalla: Screen) : DestinoDeLaAccion
}

/** Las acciones que se ven, en su orden. */
fun accionesDelInicio(): List<AccionDelInicio> = AccionDelInicio.entries

/**
 * La pantalla a la que lleva una acción, cuando es una pantalla: las de anotar en la hoja de «Agregar»
 * (`pantallaDelAtajo`), revisar y preguntar. `null` para «Repartir» y «Subir comprobante», que no son
 * una pantalla sino lo que hace la cáscara con su atajo.
 */
fun destinoDeLaAccion(accion: AccionDelInicio): Screen? = when (val destino = accion.destino) {
    is DestinoDeLaAccion.Pantalla -> destino.pantalla
    is DestinoDeLaAccion.Atajo -> pantallaDelAtajo(destino.atajo)
}

/**
 * **Lo que espera en «Por revisar», fuente por fuente**, con lo que el Inicio ya cargó: los avisos (uno
 * por pago) y los movimientos por confirmar del resumen, los candidatos a pago de tarjeta y los débitos
 * automáticos. El mismo [ConteoPorRevisar] que arma Movimientos sobre la bandeja (vuelta de diseño 22):
 * la insignia, «Movi propone hoy», la campana y «N por revisar» dicen el mismo número.
 */
fun conteoPorRevisarDelInicio(data: DashboardData): ConteoPorRevisar = ConteoPorRevisar(
    avisos = data.pendingSms,
    movimientos = data.movimientosPorConfirmar,
    pagosDeTarjeta = data.cardCandidates,
    debitos = data.debitosPorConfirmar.orEmpty().size,
    avisosAntiguos = data.avisosAntiguos,
    // Vuelta 67: las transferencias que saldan un grupo también esperan en Por revisar.
    saldar = data.propuestasDeSaldar.orEmpty().size,
)

/**
 * El número de la insignia de «Revisar avisos»: **todo** lo que espera en Por revisar
 * ([conteoPorRevisarDelInicio]), el mismo «N por revisar» de Movimientos. Cero no dibuja insignia.
 */
fun pendientesPorRevisar(data: DashboardData): Int = conteoPorRevisarDelInicio(data).total

/**
 * La descripción de la acción «Avisos» para el lector de pantalla, con de qué es su número: «Revisar
 * los avisos (2 avisos · 1 movimiento)». La cuadrícula le agrega la insignia («…, 3»): el número solo
 * no dice de qué es.
 */
fun descripcionDeLaInsignia(data: DashboardData): String {
    val desglose = desgloseDePorRevisar(conteoPorRevisarDelInicio(data)) ?: return AccionDelInicio.REVISAR_AVISOS.descripcion
    return "${AccionDelInicio.REVISAR_AVISOS.descripcion} ($desglose)"
}

// ── La cola de lo pendiente ──────────────────────────────────────────────────

/**
 * **La cola de lo pendiente del Inicio** (vuelta de diseño 22, `colaDePendientes` en `:core`): los pagos
 * vencidos y de la semana del checklist del período, los débitos por confirmar y lo que entró solo, en
 * el orden de la cola. La leen la campana ([notificationRows]) y «Movi propone hoy»
 * ([propuestasDelInicio]); el widget «Falta por pagar» y Plan ordenan sus pagos con la misma regla.
 * Vacía mientras no se sabe en qué período estamos (el checklist no se puede armar).
 */
fun colaDelInicio(data: DashboardData): List<Pendiente> = colaDePendientes(
    checklist = checklistDelPeriodoDe(data),
    debitos = data.debitosPorConfirmar.orEmpty(),
    porRevisar = conteoPorRevisarDelInicio(data).loQueEntroSolo,
)

/**
 * **El pago que «Movi propone hoy» muestra**: el primero de la cola que vence a más tardar mañana
 * (vencido, hoy o mañana), o `null`.
 */
fun pagoPropuestoHoy(data: DashboardData): PagoDelPeriodo? = pagosParaProponer(data).firstOrNull()

private fun pagosParaProponer(data: DashboardData): List<PagoDelPeriodo> =
    colaDelInicio(data).filterIsInstance<Pendiente.Pago>().map { it.pago }.filter { it.diasParaVencer <= 1 }

/**
 * Lo que el widget «Falta por pagar» lista: los pagos pendientes **en el orden de la cola** —lo más
 * urgente primero: lo vencido, y dentro de cada grupo el monto más grande—, hasta [PAGOS_EN_EL_WIDGET];
 * y cuántos quedan sin fila, para decir «y N más». Las filas más ese número dan lo que dice «Faltan N».
 *
 * **Vuelta de diseño 28: ya no se salta el pago de «Movi propone hoy».** La vuelta 22 lo sacaba para no
 * repetirlo, y con eso el Arriendo de $1,8M vencido hace 22 días quedaba en «y 1 más» mientras se veían
 * Carro $9.000 e Internet $3.000. Un resumen de lo que falta no puede esconder lo que más falta.
 */
data class FilasDelWidgetDeFalta(
    val filas: List<PagoDelPeriodo>,
    val masSinFila: Int,
    /**
     * Vuelta 68: el pago que no tiene fila porque ya está en «Próximo pago», arriba en el mismo Inicio;
     * `null` si no se saltó ninguno. Con él, filas + [masSinFila] + 1 es lo que dice «Faltan N».
     */
    val elDeArriba: PagoDelPeriodo? = null,
)

/** Cuántos pagos lista el widget de «Falta por pagar». */
const val PAGOS_EN_EL_WIDGET: Int = 2

/**
 * @param sinElProximoPago **vuelta 68**: el Inicio ya muestra «Próximo pago», que es el primero de esta
 *   misma cola ([proximoPagoDelInicio]): el widget no lo repite como primer renglón (la revisión `r3`:
 *   «Carro $9.000» dos veces, una debajo de la otra). Sigue contando en la cifra y en «Falta 1 de 5».
 */
fun filasDelWidgetDeFalta(data: DashboardData, sinElProximoPago: Boolean = false): FilasDelWidgetDeFalta {
    val todos = pagosEnOrdenDeUrgencia(checklistDelPeriodoDe(data))
    val elDeArriba = if (sinElProximoPago) todos.firstOrNull() else null
    val pendientes = if (elDeArriba != null) todos.drop(1) else todos
    val filas = pendientes.take(PAGOS_EN_EL_WIDGET)
    return FilasDelWidgetDeFalta(filas = filas, masSinFila = pendientes.size - filas.size, elDeArriba = elDeArriba)
}

/** «y 2 más»: lo pendiente que el widget no lista (ver [FilasDelWidgetDeFalta.masSinFila]). */
fun yNMas(n: Int): String? = if (n > 0) "y $n más" else null

// ── Movi propone hoy ─────────────────────────────────────────────────────────

/** Cuántas propuestas pinta el Inicio como mucho. El resto está en «Ver todo» (Por revisar). */
const val TOPE_DE_PROPUESTAS: Int = 4

/** De qué es una propuesta. Una tarjeta por tipo: varias cosas del mismo tipo se dicen en una. */
enum class TipoDePropuesta { PAGO, DEBITO, CUADRE, ANOMALIA, CIERRE }

/** Lo que hace tocar un botón de una propuesta. */
sealed interface AccionDeLaPropuesta {
    /** Ir a la pantalla donde se resuelve (o abrir «Agregar» ya prellenado). */
    data class Ir(val pantalla: Screen) : AccionDeLaPropuesta

    /** «Está bien»: no volver a avisar este aviso de lo que se sale de lo normal. */
    data class DescartarAnomalia(val huella: String) : AccionDeLaPropuesta

    /** «Ya lo vi»: la tarjeta del cierre no vuelve en este período. */
    data class DescartarCierre(val periodo: String) : AccionDeLaPropuesta

    /**
     * Vuelta 60: «Sí, cambiar»: el movimiento vuelve a la categoría del período anterior y, si la regla
     * tiene la otra, también la regla. Lo hace el dueño con el toque; Movi nunca lo cambia solo.
     */
    data class CambiarLaCategoria(val cambio: CambioDeCategoriaDeUnRecurrente) : AccionDeLaPropuesta
}

/**
 * **«Sí, cambiar»** (vuelta 60): el movimiento vuelve a la categoría que tuvo el período anterior con el
 * cambio de categoría de siempre, y la regla —si la propuesta la nombra y todavía tiene la otra— con la
 * edición de siempre. Nada más: no hay escritura nueva en el server.
 */
internal suspend fun aceptarElCambioDeCategoria(
    repo: com.jvillada.movi.shared.repository.WalletRepository,
    cambio: CambioDeCategoriaDeUnRecurrente,
) {
    repo.updateEventCategory(cambio.eventoId, cambio.categoriaAnterior)
    val reglaId = cambio.reglaId ?: return
    val regla = repo.getRecurringRules().firstOrNull { it.id == reglaId } ?: return
    if (regla.category != cambio.categoriaAnterior) {
        repo.updateRecurringRule(reglaId, regla.copy(category = cambio.categoriaAnterior))
    }
}

/** El botón que acepta un cambio de categoría (vuelta 60). */
const val SI_CAMBIAR: String = "Sí, cambiar"

/** El que lo rechaza: el aviso no vuelve para ese movimiento. */
const val NO_CAMBIAR: String = "No"

/** Una tarjeta de «Movi propone hoy»: lo que pasa, su cifra si la tiene y la decisión que pide. */
data class PropuestaDelInicio(
    val tipo: TipoDePropuesta,
    val titulo: String,
    val accion: String,
    val alTocar: AccionDeLaPropuesta,
    val cifra: Long? = null,
    val moneda: String = "COP",
    val cuerpo: String? = null,
    val accionSecundaria: String? = null,
    val alTocarSecundaria: AccionDeLaPropuesta? = null,
    /** Vencido, vence hoy o ya se cobró: el ícono va en aviso, no en la marca. */
    val urgente: Boolean = false,
    /** Para ordenar: menor primero. */
    internal val rango: Int = 0,
)

/**
 * **Todo lo que pide una decisión hoy, una tarjeta por tipo, de lo más urgente a lo menos.** Junta lo
 * que estaba repartido entre «Para revisar», las alertas del Inicio, la campana y la tarjeta del cierre
 * (ola R1). El orden:
 *
 * 1. un pago vencido o que vence hoy;
 * 2. lo que el banco cobró solo y falta confirmar;
 * 3. un pago que vence mañana;
 * 5. un saldo que no cuadra con el que dijo el banco;
 * 6. algo que se sale de lo normal;
 * 7. el cierre del período que terminó.
 *
 * Sin tope: el tope ([TOPE_DE_PROPUESTAS]) lo pone la pantalla, que además dice cuántas hay.
 *
 * **Lo que entró solo no es una propuesta** (vuelta de diseño 28). «3 por revisar» era una tarjeta más
 * del feed, y Hoy decía lo mismo tres veces: el punto de la campana, la insignia «3» de la acción
 * «Avisos» y esa tarjeta. Las propuestas son decisiones con contenido —un pago, un cobro, un saldo—;
 * un contador no lo es. Lo que espera en «Por revisar» queda en la insignia de «Avisos» (siempre en la
 * cuadrícula) y en la campana.
 */
fun propuestasDelInicio(data: DashboardData, conElPago: Boolean = true): List<PropuestaDelInicio> = buildList {
    // Vuelta 39: con «Próximo pago» en la definición, el pago ya está arriba, con su «Anotar pago»; acá
    // solo lo que no es un pago (`conElPago = false`, lo decide `visibleSections`).
    if (conElPago) propuestaDePago(data)?.let(::add)
    propuestaDeDebitos(data)?.let(::add)
    propuestaDeCuadre(data)?.let(::add)
    propuestaDeAnomalia(data)?.let(::add)
    propuestaDeCierre(data)?.let(::add)
}.sortedBy { it.rango }

/** Las propuestas que se ven: las primeras [TOPE_DE_PROPUESTAS]. */
fun propuestasALaVista(propuestas: List<PropuestaDelInicio>): List<PropuestaDelInicio> =
    propuestas.take(TOPE_DE_PROPUESTAS)

private fun propuestaDePago(data: DashboardData): PropuestaDelInicio? {
    // Vuelta de diseño 22: de la cola, en su orden (lo vencido primero, el monto más grande primero).
    val porVencer = pagosParaProponer(data)
    val pago = porVencer.firstOrNull() ?: return null
    val cuando = when {
        pago.diasParaVencer < -1 -> "venció hace ${-pago.diasParaVencer} días"
        pago.diasParaVencer == -1 -> "venció ayer"
        pago.diasParaVencer == 0 -> "vence hoy"
        else -> "vence mañana"
    }
    val otros = porVencer.size - 1
    // Una tarjeta no tiene cuota: su «monto» es el saldo. Va el mínimo si se sabe; si no, sin cifra.
    val cifra = cifraDelPago(pago)
    // Vuelta 51: solo los candidatos que se parecen al monto cuentan como «Movi encontró…».
    val conCandidatos = pago.candidatosParecidos.isNotEmpty()
    val (accion, destino) = accionDelPago(pago)
    return PropuestaDelInicio(
        tipo = TipoDePropuesta.PAGO,
        titulo = "${pago.nombre} $cuando",
        cifra = cifra,
        moneda = pago.moneda,
        cuerpo = when {
            conCandidatos -> "Movi encontró movimientos que podrían ser este pago"
            otros == 1 -> "y 1 pago más por vencer"
            otros > 1 -> "y $otros pagos más por vencer"
            pago.montoEsSaldo && cifra != null -> "pago mínimo"
            else -> null
        },
        accion = accion,
        alTocar = AccionDeLaPropuesta.Ir(destino),
        accionSecundaria = if (conCandidatos) null else "Ver pagos",
        alTocarSecundaria = if (conCandidatos) null else AccionDeLaPropuesta.Ir(Screen.Plan(SEGMENTO_PAGOS)),
        urgente = pago.diasParaVencer <= 0,
        rango = if (pago.diasParaVencer <= 0) 0 else 3,
    )
}

private fun propuestaDeDebitos(data: DashboardData): PropuestaDelInicio? {
    val debitos = data.debitosPorConfirmar.orEmpty()
    val titulo = textoDeDebitosEnHoy(debitos) ?: return null
    val uno = debitos.singleOrNull()
    return PropuestaDelInicio(
        tipo = TipoDePropuesta.DEBITO,
        titulo = titulo,
        cifra = uno?.monto,
        moneda = uno?.moneda ?: "COP",
        cuerpo = uno?.let { "desde ${it.cuentaNombre}" },
        accion = "Confirmar",
        alTocar = AccionDeLaPropuesta.Ir(Screen.PorRevisar),
        urgente = true,
        rango = 1,
    )
}

/**
 * **Lo que hace el botón de un pago pendiente**: «Anotar pago» abre «Agregar» con lo que el recurrente
 * ya sabe ([hojaParaAnotar]). Con candidatos, Movi ya encontró movimientos que podrían ser este pago:
 * anotar otro lo duplicaría, así que es «Revisar» y lleva a Pagos, donde se elige «Fue este». Lo usan
 * «Movi propone hoy» y «Próximo pago»: el mismo botón hace lo mismo en los dos.
 */
fun accionDelPago(pago: PagoDelPeriodo): Pair<String, Screen> =
    // Vuelta 51: un candidato que se aleja más de la mitad del monto (el tanqueo de $260.000 para la
    // factura de $9.000) no hace «Revisar»: el principal sigue siendo «Anotar pago», y en Pagos el
    // candidato queda como enlace ([candidatosParecidos]).
    if (pago.candidatosParecidos.isNotEmpty()) "Revisar" to Screen.Plan(SEGMENTO_PAGOS) else "Anotar pago" to hojaParaAnotar(pago)

// ── Próximo pago ─────────────────────────────────────────────────────────────

/**
 * **El próximo pago del Inicio** (vuelta 39): el pago pendiente más urgente del período —lo vencido
 * primero, y dentro de cada grupo el de más monto; después lo que vence antes— con la regla de
 * siempre ([pagosEnOrdenDeUrgencia], la del widget «Falta por pagar» y de la cola). [masPagos] son los
 * demás pendientes, que «y N más» lleva a Pagos.
 */
data class ProximoPagoDelInicio(
    val pago: PagoDelPeriodo,
    /** «vence el 22 de octubre», «venció hace 3 días»: la fecha de la fila de Pagos (`fechaDeLaFila`). */
    val cuando: String,
    val masPagos: Int,
    val accion: String,
    val alTocar: Screen,
) {
    /**
     * Lo que se lee arriba de la tarjeta: «Próximo pago · vence el 22 de octubre». Un pago vencido no es
     * «próximo» (la revisión de la vuelta 39): «Pago vencido · hace 23 días», «Pago vencido · ayer».
     */
    val titulo: String get() = when {
        pago.diasParaVencer < -1 -> "Pago vencido · hace ${-pago.diasParaVencer} días"
        pago.diasParaVencer == -1 -> "Pago vencido · ayer"
        else -> "Próximo pago · $cuando"
    }

    /** Vencido o vence hoy: el título va en el color de aviso. */
    val urgente: Boolean get() = pago.diasParaVencer <= 0
}

/** [ProximoPagoDelInicio], o `null` sin pagos pendientes en el período (la sección no se pinta). */
fun proximoPagoDelInicio(data: DashboardData): ProximoPagoDelInicio? {
    val pendientes = pagosEnOrdenDeUrgencia(checklistDelPeriodoDe(data))
    val pago = pendientes.firstOrNull() ?: return null
    val (accion, destino) = accionDelPago(pago)
    return ProximoPagoDelInicio(
        pago = pago,
        cuando = fechaDeLaFila(pago),
        masPagos = pendientes.size - 1,
        accion = accion,
        alTocar = destino,
    )
}

/**
 * **Cómo se dice lo que entró solo** (vuelta de diseño 22), sin los débitos —que tienen su propia
 * tarjeta—: de una sola fuente, con su nombre («Llegaron 2 avisos del banco», «1 movimiento entró
 * solo»); de varias, el número de «Por revisar» y su desglose debajo ([cuerpoDeLoQueEntroSolo]).
 * `null` sin nada. Lo usa la campana.
 */
fun tituloDeLoQueEntroSolo(conteo: ConteoPorRevisar): String? {
    val n = conteo.loQueEntroSolo
    if (n <= 0) return null
    return when (n) {
        conteo.avisos -> if (n == 1) "Llegó 1 aviso del banco" else "Llegaron $n avisos del banco"
        conteo.movimientos -> if (n == 1) "1 movimiento entró solo" else "$n movimientos entraron solos"
        conteo.pagosDeTarjeta -> if (n == 1) "1 pago de tarjeta por confirmar" else "$n pagos de tarjeta por confirmar"
        else -> textoDePorRevisar(n)
    }
}

/** Debajo de [tituloDeLoQueEntroSolo]: «por confirmar» de una fuente, o el desglose de varias. */
fun cuerpoDeLoQueEntroSolo(conteo: ConteoPorRevisar): String? = when (conteo.loQueEntroSolo) {
    0 -> null
    conteo.avisos, conteo.movimientos -> "por confirmar"
    conteo.pagosDeTarjeta -> "Movi los encontró en tus movimientos"
    // Vuelta 67 (revisión): sin las propuestas de saldar, que en la campana tienen su propio renglón
    // (con ellas el desglose sumaba más que el título).
    else -> desgloseDePorRevisar(conteo.copy(saldar = 0), conDebitos = false)
}

private fun propuestaDeCuadre(data: DashboardData): PropuestaDelInicio? {
    val cuadres = data.cuadresPorAviso.orEmpty()
    val cuadre = cuadres.firstOrNull() ?: return null
    val otros = cuadres.size - 1
    return PropuestaDelInicio(
        tipo = TipoDePropuesta.CUADRE,
        titulo = "El saldo de ${cuadre.cuentaNombre} no cuadra con el banco",
        cifra = cuadre.diferencia,
        moneda = cuadre.moneda,
        cuerpo = when {
            otros == 1 -> "de diferencia · y 1 cuenta más"
            otros > 1 -> "de diferencia · y $otros cuentas más"
            else -> "de diferencia, según el último aviso"
        },
        accion = "Revisar",
        alTocar = AccionDeLaPropuesta.Ir(Screen.CuadreDeSaldos),
        rango = 4,
    )
}

private fun propuestaDeAnomalia(data: DashboardData): PropuestaDelInicio? {
    val anomalias = anomaliasVisiblesDe(data)
    // Vuelta 60: un recurrente que cambió de categoría va primero: es lo único que se resuelve con un
    // toque, y mientras siga, otras pantallas cuentan mal (Presupuestos, «En qué se va»).
    val cambio = anomalias.firstOrNull { it.cambioDeCategoria != null }
    if (cambio != null) {
        val otras = anomalias.size - 1
        return PropuestaDelInicio(
            tipo = TipoDePropuesta.ANOMALIA,
            titulo = cambio.titulo,
            cuerpo = cuerpoDeLaAnomalia(cambio.detalle, otras),
            accion = SI_CAMBIAR,
            alTocar = AccionDeLaPropuesta.CambiarLaCategoria(cambio.cambioDeCategoria!!),
            accionSecundaria = NO_CAMBIAR,
            alTocarSecundaria = AccionDeLaPropuesta.DescartarAnomalia(cambio.huella),
            rango = 5,
        )
    }
    val anomalia = anomalias.firstOrNull() ?: return null
    val otras = anomalias.size - 1
    return PropuestaDelInicio(
        tipo = TipoDePropuesta.ANOMALIA,
        titulo = anomalia.titulo,
        cuerpo = cuerpoDeLaAnomalia(anomalia.detalle, otras),
        accion = "Ver",
        alTocar = AccionDeLaPropuesta.Ir(destinoDeLaAnomalia(anomalia)),
        accionSecundaria = "Está bien",
        alTocarSecundaria = AccionDeLaPropuesta.DescartarAnomalia(anomalia.huella),
        rango = 5,
    )
}

/**
 * **El cuerpo de la tarjeta de una anomalía**: su detalle y, si hay más, «y N avisos más» después de
 * « · ». El detalle viene como frase, con su punto final («… (el promedio de los 3 anteriores).»), y
 * pegado a « · » quedaba «…anteriores). · y 1 aviso más»: el punto final se saca antes de unir
 * (vuelta de diseño 28). Solo, el detalle queda tal cual.
 */
internal fun cuerpoDeLaAnomalia(detalle: String, otras: Int): String {
    val mas = when {
        otras == 1 -> "y 1 aviso más"
        otras > 1 -> "y $otras avisos más"
        else -> return detalle
    }
    val sinPunto = detalle.trimEnd().removeSuffix(".").trimEnd()
    return if (sinPunto.isEmpty()) mas.replaceFirstChar { it.uppercase() } else "$sinPunto · $mas"
}

/**
 * Dónde se mira un aviso de lo que se sale de lo normal: una categoría que subió, en «En qué se va»;
 * un cobro (duplicado, nuevo, una suscripción que subió), en Movimientos con su nombre buscado; una
 * deuda, en Créditos.
 */
internal fun destinoDeLaAnomalia(anomalia: com.jvillada.movi.shared.model.Anomalia): Screen = when (anomalia.tipo) {
    TipoDeAnomalia.CATEGORIA_POR_ENCIMA -> Screen.EnQueSeVa()
    TipoDeAnomalia.DEUDA_QUE_CRECE, TipoDeAnomalia.INTERES_LEJOS_DEL_ESTIMADO -> Screen.Credits
    else -> anomalia.evidencia.firstOrNull()?.nombre?.takeIf { it.isNotBlank() }
        ?.let { Screen.Transactions(consultaInicial = it) }
        ?: Screen.Transactions()
}

private fun propuestaDeCierre(data: DashboardData): PropuestaDelInicio? {
    val cierre = cierreParaHoy(data) ?: return null
    return PropuestaDelInicio(
        tipo = TipoDePropuesta.CIERRE,
        titulo = "Resumen de ${cierre.nombre.replaceFirstChar { it.lowercase() }}",
        // La frase del resumen y el renglón que más sirve (lo que quedó del disponible); el resto está
        // en el detalle del período.
        cuerpo = (listOf(fraseDelCierre(cierre)) + renglonesDeLaTarjetaDeCierre(cierre).take(1)).joinToString(". "),
        accion = "Ver el período",
        alTocar = AccionDeLaPropuesta.Ir(Screen.DetalleDePeriodo(cierre.periodo)),
        accionSecundaria = "Ya lo vi",
        alTocarSecundaria = AccionDeLaPropuesta.DescartarCierre(cierre.periodo),
        rango = 6,
    )
}

// ── Últimos movimientos ──────────────────────────────────────────────────────

/** Cuántas filas lleva «Últimos movimientos» (vuelta 39, la maqueta de la opción 3). */
const val FILAS_DE_ULTIMOS_MOVIMIENTOS: Int = 3

/**
 * **Los movimientos de las primeras [FILAS_DE_ULTIMOS_MOVIMIENTOS] filas de Actividad**, sobre la misma
 * historia (`getEventsByDay`, el más reciente primero) y con la misma regla de lo que se lista en
 * «Todo» ([diasVisibles]: sin la apertura de una cuenta). Un traspaso es una fila con sus dos patas
 * ([collapseTransfers]), así que se guardan las dos. Lo que se guarda va a la instantánea del Inicio.
 */
fun ultimosMovimientosDe(dias: List<EventDay>, cuantas: Int = FILAS_DE_ULTIMOS_MOVIMIENTOS): List<FinancialEvent> {
    val filas = diasVisibles(dias, CHIP_TODO, query = "")
        .asSequence()
        .flatMap { collapseTransfers(it.items) }
        .take(cuantas)
        .toList()
    return filas.flatMap { fila ->
        when (fila) {
            is MovementRow.Single -> listOf(fila.event)
            is MovementRow.Transfer -> listOf(fila.out, fila.into)
            is MovementRow.Ajustes -> fila.events
        }
    }
}

/** Las filas de «Últimos movimientos», en su orden: las de Actividad ([collapseTransfers]). */
fun filasDeUltimosMovimientos(eventos: List<FinancialEvent>): List<MovementRow> =
    collapseTransfers(eventos).take(FILAS_DE_ULTIMOS_MOVIMIENTOS)

// ── Tus cuentas ──────────────────────────────────────────────────────────────

/** Una tarjeta del carrusel «Tus cuentas»: la cuenta, cómo se llama su tipo y qué cifra se muestra. */
data class CuentaDelCarrusel(
    val cuenta: Account,
    /**
     * «Ahorros», «Corriente», «Efectivo», «Inversión», «Tarjeta», o `null` si el nombre ya lo dice
     * («Bancolombia Ahorros»): la regla de Patrimonio, [tipoSiElNombreNoLoDice] (vuelta de diseño 16).
     */
    val tipo: String?,
    /** «Saldo», o «Debes» en una tarjeta. */
    val rotuloDelSaldo: String,
    /** El saldo tal como lo trae la cuenta; en una tarjeta, la deuda en positivo. */
    val saldo: Long,
    val moneda: String,
    /**
     * Vuelta 64: lo apartado de esta cuenta que de verdad pesa ([apartadoEfectivoDe]), o 0. La tarjeta
     * lo dice debajo del saldo («$2.000.000 apartado»): el saldo sigue siendo el del banco, y lo
     * apartado es la diferencia con «Disponible en la cuenta» del detalle.
     */
    val apartado: Long = 0L,
) {
    /** Lo que abre tocarla: el detalle de la cuenta, en el grupo donde vive. */
    val destino: Screen get() = Screen.AccountDetail(cuenta.id, cuenta.type.group)
}

/**
 * **Las cuentas del carrusel**, en el orden en que se miran: el dinero (de más a menos), las
 * inversiones y las tarjetas (de la deuda más grande a la más chica). `null` mientras las cuentas no
 * contestan: un carrusel vacío afirmaría que no hay ninguna.
 *
 * Fuera quedan los préstamos y los bienes (la casa, el carro): no son plata para mover, y su casa es
 * Patrimonio. Ninguna cifra se calcula: cada tarjeta muestra el saldo que trae su cuenta.
 */
fun cuentasDelCarrusel(accounts: List<Account>?, apartados: List<Apartado> = emptyList()): List<CuentaDelCarrusel>? {
    accounts ?: return null
    val visibles = accounts.filter { it.type != AccountType.LOAN && it.bien == null }
    val porGrupo = visibles.groupBy { it.type.group }
    return (porGrupo[AccountGroup.DINERO].orEmpty().sortedByDescending { it.balance } +
        porGrupo[AccountGroup.INVERSION].orEmpty().sortedByDescending { it.balance } +
        porGrupo[AccountGroup.DEUDA].orEmpty().sortedBy { it.balance })
        .map { cuentaDelCarrusel(it).copy(apartado = if (apartados.isEmpty()) 0L else apartadoEfectivoDe(it, apartados)) }
}

/** La línea de debajo del saldo en la tarjeta de una cuenta con algo apartado: «$2.000.000 apartado». */
fun notaDeLoApartado(c: CuentaDelCarrusel): String? =
    c.apartado.takeIf { it > 0L }?.let { "${formatCOP(it)} apartado" }

/**
 * **¿Carrusel o grilla?** (vuelta de diseño 9). Cuántas [com.jvillada.movi.ui.components.ds.TarjetaDeCuenta]
 * van por renglón en «Tus cuentas», o **0 para el carrusel**.
 *
 * En el teléfono ([WindowWidthClass.Compact]) sigue el carrusel, con la siguiente asomando. Fuera del
 * teléfono —el umbral de pantalla ancha de siempre, `claseDeAncho`— la columna del Inicio mide de 450
 * a 720 dp, y en un carrusel la segunda tarjeta quedaba cortada a la derecha: ahí van en grilla, **2
 * por renglón**, y **3** cuando la sección tiene el ancho entero de la columna de lectura
 * ([ANCHO_DE_LECTURA], el Inicio en una columna). Ningún umbral nuevo: la clase de ancho y la columna
 * de lectura son los de toda la app.
 *
 * Con una sola cuenta no hay grilla: la tarjeta va a todo el ancho, como siempre. Con menos cuentas
 * que columnas, una columna por cuenta (dos tarjetas no dejan un hueco a la derecha).
 *
 * | Ventana | Clase     | Columna del Inicio | Tarjetas por renglón |
 * |---------|-----------|--------------------|----------------------|
 * | 390     | teléfono  | 390                | carrusel             |
 * | 768     | mediana   | 688                | 2                    |
 * | 999     | mediana   | 720 (lectura)      | 3                    |
 * | 1024    | escritorio| 720 (lectura)      | 3                    |
 * | 1280    | escritorio| 532 (dos columnas) | 2                    |
 * | 1920    | escritorio| 640 (dos columnas) | 2                    |
 */
fun tarjetasPorRenglon(clase: WindowWidthClass, anchoDeLaSeccion: Dp, cuantas: Int): Int {
    if (clase == WindowWidthClass.Compact || cuantas < 2) return 0
    val columnas = if (anchoDeLaSeccion >= ANCHO_DE_LECTURA) 3 else 2
    return minOf(columnas, cuantas)
}

private fun cuentaDelCarrusel(cuenta: Account): CuentaDelCarrusel {
    // Una cuenta en otra moneda muestra su saldo en esa moneda: `balance` es solo el componente en pesos.
    val enOtraMoneda = cuenta.currency != "COP" && cuenta.balancesByCurrency.containsKey(cuenta.currency)
    val saldo = if (enOtraMoneda) cuenta.balancesByCurrency.getValue(cuenta.currency) else cuenta.balance
    val moneda = if (enOtraMoneda) cuenta.currency else "COP"
    val esDeuda = cuenta.type.group == AccountGroup.DEUDA
    return CuentaDelCarrusel(
        cuenta = cuenta,
        tipo = tipoSiElNombreNoLoDice(
            cuenta.name,
            when (cuenta.type) {
                AccountType.SAVINGS -> "Ahorros"
                AccountType.CHECKING -> "Corriente"
                AccountType.CASH -> "Efectivo"
                AccountType.INVESTMENT -> "Inversión"
                AccountType.CREDIT_CARD -> "Tarjeta"
                AccountType.LOAN -> "Préstamo"
            },
        ),
        rotuloDelSaldo = if (esDeuda) "Debes" else "Saldo",
        saldo = if (esDeuda) kotlin.math.abs(saldo) else saldo,
        moneda = moneda,
    )
}
