package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.ui.accounts.tipoSiElNombreNoLoDice
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.fraseDelCierre
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ANCHO_DE_LECTURA
import com.jvillada.movi.ui.components.WindowWidthClass
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import com.jvillada.movi.ui.quickadd.pantallaDelAtajo
import com.jvillada.movi.ui.recurrentes.hojaParaAnotar
import com.jvillada.movi.shared.model.Pendiente
import com.jvillada.movi.shared.model.cifraDelPago
import com.jvillada.movi.shared.model.colaDePendientes
import com.jvillada.movi.shared.model.pagosEnOrdenDeUrgencia
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
 * El período, debajo del saludo: su nombre, «Noviembre 2026», con cualquier corte; o `null` mientras el
 * perfil no contesta.
 *
 * **Corto siempre** (vuelta de diseño 11). Con un corte a mitad de mes era el rango entero, «Del 23 de
 * octubre al 24 de noviembre», y a 360 dp el subtítulo de un renglón se cortaba en «Del 23 de octubre
 * al 24 de no…». El rango completo sigue donde se lee con lugar: la línea del período de Plan y de
 * Movimientos. Es el mismo nombre que dicen «Tus períodos» y el server ([tituloDelPeriodo]).
 */
fun periodoDelEncabezado(data: DashboardData): String? = data.periodoActual?.let(::tituloDelPeriodo)

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
 * **Cuatro, en una fila** (vuelta de diseño 26, decisión del dueño «Dejar 4»): Gasto, Ingreso,
 * Avisos (con su insignia) y Preguntar. Eran ocho en dos filas; **Traspaso, Pagar tarjeta, Repartir y
 * Comprobante** se mudaron al menú del «+» (`AtajoDeAgregar`: mantenerlo presionado en el teléfono, o
 * el clic derecho y la flecha de «Agregar» en la web y el escritorio, ver `BotonDeAgregarConAtajos`).
 * Ninguno perdió su camino: es el mismo destino de antes, desde otro gesto.
 *
 * [rotulo] es lo que se lee debajo del círculo: **una palabra o dos, en un renglón** (vuelta de diseño
 * 2). [descripcion] es la frase entera, la que dice el lector de pantalla. Los pinta el cliente: la
 * definición SDUI solo trae la sección `ACCIONES`, no los rótulos, así que un APK viejo sigue con sus
 * ocho y el server no cambia.
 */
enum class AccionDelInicio(val rotulo: String, val descripcion: String) {
    ANOTAR_GASTO("Gasto", "Anotar un gasto"),
    ANOTAR_INGRESO("Ingreso", "Anotar un ingreso"),
    REVISAR_AVISOS("Avisos", "Revisar los avisos"),
    PREGUNTAR_A_MOVI("Preguntar", "Preguntarle a Movi"),
}

/** Las acciones que se ven, en su orden. */
fun accionesDelInicio(): List<AccionDelInicio> = AccionDelInicio.entries

/**
 * A dónde lleva cada acción: **lo que ya existe**. Las de anotar son los atajos del «+» (la hoja de
 * «Agregar» en su pestaña, `pantallaDelAtajo`); revisar es Por revisar; preguntar es Movi AI.
 */
fun destinoDeLaAccion(accion: AccionDelInicio): Screen = when (accion) {
    AccionDelInicio.ANOTAR_GASTO -> pantallaDelAtajo(AtajoDeAgregar.GASTO)!!
    AccionDelInicio.ANOTAR_INGRESO -> pantallaDelAtajo(AtajoDeAgregar.INGRESO)!!
    AccionDelInicio.REVISAR_AVISOS -> Screen.PorRevisar
    AccionDelInicio.PREGUNTAR_A_MOVI -> Screen.AIChat()
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
)

/** Cuántos pagos lista el widget de «Falta por pagar». */
const val PAGOS_EN_EL_WIDGET: Int = 2

fun filasDelWidgetDeFalta(data: DashboardData): FilasDelWidgetDeFalta {
    val pendientes = pagosEnOrdenDeUrgencia(checklistDelPeriodoDe(data))
    val filas = pendientes.take(PAGOS_EN_EL_WIDGET)
    return FilasDelWidgetDeFalta(filas = filas, masSinFila = pendientes.size - filas.size)
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
}

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
fun propuestasDelInicio(data: DashboardData): List<PropuestaDelInicio> = buildList {
    propuestaDePago(data)?.let(::add)
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
    // Con candidatos Movi ya encontró movimientos que podrían ser este pago: anotar otro lo
    // duplicaría. Se lleva a Plan, donde se elige «Fue este».
    val conCandidatos = pago.candidatos.isNotEmpty()
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
        accion = if (conCandidatos) "Revisar" else "Anotar pago",
        alTocar = AccionDeLaPropuesta.Ir(if (conCandidatos) Screen.Plan(SEGMENTO_PAGOS) else hojaParaAnotar(pago)),
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
    else -> desgloseDePorRevisar(conteo, conDebitos = false)
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
fun cuentasDelCarrusel(accounts: List<Account>?): List<CuentaDelCarrusel>? {
    accounts ?: return null
    val visibles = accounts.filter { it.type != AccountType.LOAN && it.bien == null }
    val porGrupo = visibles.groupBy { it.type.group }
    return (porGrupo[AccountGroup.DINERO].orEmpty().sortedByDescending { it.balance } +
        porGrupo[AccountGroup.INVERSION].orEmpty().sortedByDescending { it.balance } +
        porGrupo[AccountGroup.DEUDA].orEmpty().sortedBy { it.balance })
        .map { cuentaDelCarrusel(it) }
}

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
