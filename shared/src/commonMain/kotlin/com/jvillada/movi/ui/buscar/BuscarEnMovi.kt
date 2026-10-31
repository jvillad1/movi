package com.jvillada.movi.ui.buscar

import com.jvillada.movi.shared.model.enFormaDeTitulo
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.laPataQueCuenta
import com.jvillada.movi.shared.model.PresentacionDelMovimiento
import com.jvillada.movi.shared.model.comoSePresenta
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.montoContado
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.conTuParteDeOtros
import com.jvillada.movi.shared.model.esTuParteDeOtro
import com.jvillada.movi.shared.model.tuParteComoEventos
import com.jvillada.movi.ui.transactions.textosDeTuParteDeOtro
import com.jvillada.movi.ui.transactions.quienPagoTuParte
import com.jvillada.movi.ui.transactions.TU_PARTE
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.claveDelComercio
import com.jvillada.movi.shared.model.marcaDe
import com.jvillada.movi.shared.model.vecesEnElComercio
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.ui.mas.TITULO_DE_TU
import com.jvillada.movi.ui.espacios.TITULO_DE_GRUPOS
import com.jvillada.movi.ui.plan.TITULO_DE_PAGOS
import com.jvillada.movi.ui.transactions.TITULO_DE_ACTIVIDAD
import com.jvillada.movi.ui.sms.tituloDeCapturaDelBanco
import com.jvillada.movi.ui.transactions.textosDeLaFila
import com.jvillada.movi.ui.transactions.isTransferLeg
import com.jvillada.movi.ui.transactions.matchesQuery

/*
 * # Ola 5 · Buscar en toda la app, sin dibujo
 *
 * Una lupa en el encabezado de Hoy abre «Buscar»: escribir «rappi», «27500», «colegio» o «captura»
 * encuentra movimientos, comercios, personas, categorías, cuentas y pantallas, agrupados por tipo.
 * Todo sale **de lo que el cliente ya tiene** —la historia completa de `getEventsByDay` (todos los
 * períodos, con su caché), las cuentas, los terceros guardados y las categorías—, así que no hace
 * falta un endpoint nuevo: la búsqueda nunca va al server.
 *
 * Los movimientos se comparan con [matchesQuery], la MISMA función de la búsqueda de Movimientos
 * (sin tildes ni mayúsculas, contra la descripción, el comercio y la categoría); acá además un número
 * busca el monto exacto.
 */

/** Gastos, ingresos o los dos: el primer chip. */
enum class FiltroDeTipo(val rotulo: String) { TODO("Todo"), GASTOS("Gastos"), INGRESOS("Ingresos") }

/**
 * Los chips de «Buscar». [soloEstePeriodo] filtra movimientos y comercios al período en curso;
 * [cuentaId] a una cuenta. Las categorías, personas, cuentas y pantallas no tienen período ni cuenta:
 * los chips no las tocan.
 */
data class FiltrosDeBusqueda(
    val tipo: FiltroDeTipo = FiltroDeTipo.TODO,
    val soloEstePeriodo: Boolean = false,
    val cuentaId: String? = null,
)

/** Lo que la búsqueda tiene a mano. */
data class DatosDeBusqueda(
    val eventos: List<FinancialEvent> = emptyList(),
    val cuentas: List<Account> = emptyList(),
    val destinos: List<DestinoConocido> = emptyList(),
    val categorias: List<String> = emptyList(),
)

/** El período en curso, en epoch ms: `[desde, hasta)`. */
data class RangoDelPeriodo(val desde: Long, val hasta: Long) {
    operator fun contains(momento: Long): Boolean = momento >= desde && momento < hasta
}

/** Los grupos, en el orden en que se pintan. */
enum class TipoDeResultado(val titulo: String) {
    MOVIMIENTO("Movimientos"),
    COMERCIO("Comercios"),
    PERSONA(PERSONAS_Y_COMERCIOS),
    CATEGORIA("Categorías"),
    CUENTA("Cuentas"),
    PANTALLA("Pantallas y ajustes"),
}

/** Un resultado. [destino] es a dónde lleva tocarlo; `null` en un movimiento, que abre su hoja ahí mismo. */
sealed class ResultadoDeBusqueda {
    abstract val tipo: TipoDeResultado
    abstract val clave: String
    abstract val titulo: String
    abstract val destino: Screen?

    data class Movimiento(val evento: FinancialEvent) : ResultadoDeBusqueda() {
        override val tipo get() = TipoDeResultado.MOVIMIENTO
        override val clave get() = "mov:${evento.id}"
        // El mismo título que la fila de Movimientos ([textosDeLaFila]): en forma de título (vuelta 32) y,
        // si la descripción es la categoría, el comercio (vuelta 30). Antes Buscar decía «Mercado» donde
        // Movimientos decía «Éxito Suba».
        override val titulo get() = when {
            // Vuelta 78: tu parte de lo que pagó otro se llama como en Actividad («Mercado del sábado»).
            esTuParteDeOtro(evento) -> textosDeTuParteDeOtro(evento, espacio = null).titulo
            evento.description.isBlank() -> enFormaDeTitulo(evento.merchant ?: evento.category)
            else -> textosDeLaFila(evento, nombreDeLaCuenta = null).titulo
        }
        override val destino: Screen? get() = null
    }

    /** Un comercio: sus movimientos agrupados, con cuántos y cuánto **en el período en curso**. */
    data class Comercio(
        val nombre: String,
        val ejemplo: FinancialEvent,
        val enElPeriodo: Int,
        val totalDelPeriodo: Long,
        val moneda: String,
    ) : ResultadoDeBusqueda() {
        override val tipo get() = TipoDeResultado.COMERCIO
        override val clave get() = "com:${claveDelComercio(ejemplo) ?: nombre}"
        override val titulo get() = enFormaDeTitulo(nombre)
        override val destino: Screen get() = Screen.Transactions(consultaInicial = nombre)
    }

    data class Persona(val destinoConocido: DestinoConocido) : ResultadoDeBusqueda() {
        override val tipo get() = TipoDeResultado.PERSONA
        override val clave get() = "ter:${destinoConocido.id}"
        override val titulo get() = destinoConocido.nombre
        override val destino: Screen get() = Screen.Destinos(abrir = destinoConocido.id.takeIf { it.isNotBlank() })
    }

    data class Categoria(val nombre: String) : ResultadoDeBusqueda() {
        override val tipo get() = TipoDeResultado.CATEGORIA
        override val clave get() = "cat:$nombre"
        override val titulo get() = nombre
        override val destino: Screen get() = Screen.Categorias
    }

    data class Cuenta(val cuenta: Account) : ResultadoDeBusqueda() {
        override val tipo get() = TipoDeResultado.CUENTA
        override val clave get() = "cta:${cuenta.id}"
        override val titulo get() = cuenta.name
        override val destino: Screen get() = Screen.AccountDetail(cuenta.id, cuenta.type.group)
    }

    data class Pantalla(val nombre: String, val donde: String, override val destino: Screen) : ResultadoDeBusqueda() {
        override val tipo get() = TipoDeResultado.PANTALLA
        override val clave get() = "pan:$nombre"
        override val titulo get() = nombre
    }
}

/** Un grupo de resultados del mismo tipo. */
data class GrupoDeResultados(val tipo: TipoDeResultado, val resultados: List<ResultadoDeBusqueda>)

/**
 * **Las pantallas y ajustes que se pueden buscar por nombre**: el nombre que se ve, dónde vive (para
 * la línea de abajo) y otras palabras con que alguien la buscaría («huella» lleva a Perfil).
 */
internal data class PantallaBuscable(val nombre: String, val donde: String, val destino: Screen, val tambien: List<String> = emptyList())

internal val PANTALLAS_BUSCABLES: List<PantallaBuscable> = listOf(
    // Vuelta 38: las cuatro pestañas de la opción 3. Los nombres viejos («movimientos», «plan»,
    // «ajustes», «espacios») siguen encontrando su pantalla.
    PantallaBuscable("Inicio", "Pestaña", Screen.Dashboard, listOf("hoy", "te quedan")),
    PantallaBuscable(TITULO_DE_ACTIVIDAD, "Pestaña", Screen.Transactions(), listOf("movimientos", "gastos", "ingresos")),
    PantallaBuscable("Por revisar", TITULO_DE_ACTIVIDAD, Screen.PorRevisar, listOf("pendientes", "confirmar", "avisos del banco")),
    PantallaBuscable(TITULO_DE_PAGOS, "Pestaña", Screen.Plan(), listOf("plan", "pagos del mes", "disponible")),
    PantallaBuscable("Presupuestos", TITULO_DE_PAGOS, Screen.Budgets),
    PantallaBuscable("Tus períodos", TITULO_DE_PAGOS, Screen.Periodos, listOf("periodos", "como me fue")),
    PantallaBuscable(TITULO_DE_TU, "Pestaña", Screen.Tu, listOf("ajustes", "perfil", "huella", "ocultar los saldos", "tema", "como se ve", "notificaciones", "avisos por correo", "corte del periodo", "contrasena", "cerrar sesion", "descargar tus datos")),
    PantallaBuscable("Patrimonio", TITULO_DE_TU, Screen.Accounts, listOf("cuentas", "tu plata")),
    PantallaBuscable("Créditos", "Patrimonio", Screen.Credits, listOf("deudas", "tarjetas", "prestamos")),
    PantallaBuscable("Cómo salir de tus deudas", "Créditos", Screen.SalidaDeDeudas, listOf("abono", "avalancha", "bola de nieve")),
    PantallaBuscable("Cuadre de saldos", TITULO_DE_TU, Screen.CuadreDeSaldos, listOf("cuadrar", "ajustar saldo")),
    // Vuelta 42: «Grupos» (fue «Espacios» y «Personas»): los dos nombres viejos la siguen encontrando.
    PantallaBuscable(TITULO_DE_GRUPOS, TITULO_DE_TU, Screen.Espacios, listOf("espacios", "personas", "compartidos", "hogar", "repartir", "casa")),
    // Ola R4: su casa es Grupos (antes Espacios y Personas).
    PantallaBuscable(PERSONAS_Y_COMERCIOS, TITULO_DE_GRUPOS, Screen.Destinos(), listOf("terceros", "cuentas de otros", "hogar")),
    PantallaBuscable("Categorías", TITULO_DE_TU, Screen.Categorias),
    PantallaBuscable("Documentos", TITULO_DE_TU, Screen.Documentos, listOf("extractos", "comprobantes", "papeles")),
    PantallaBuscable("Compartir", TITULO_DE_TU, Screen.Compartir, listOf("enlace")),
    PantallaBuscable("Movi AI", TITULO_DE_TU, Screen.AIChat(), listOf("asistente", "preguntar")),
    PantallaBuscable("Lo que Movi sabe de ti", TITULO_DE_TU, Screen.LoQueMoviSabe, listOf("memoria")),
    PantallaBuscable("Editar inicio", TITULO_DE_TU, Screen.EditarInicio, listOf("hoy", "secciones", "orden", "ocultar")),
    PantallaBuscable(tituloDeCapturaDelBanco, TITULO_DE_TU, Screen.CapturaDelBanco, listOf("captura del banco", "mensajes del banco", "sms", "notificaciones", "banco mudo", "correo")),
    PantallaBuscable("Lo anulado", TITULO_DE_TU, Screen.Anulados(), listOf("anulados", "restaurar", "deshacer")),
    PantallaBuscable("Borrar mi cuenta", TITULO_DE_TU, Screen.BorrarLaCuenta, listOf("eliminar cuenta")),
    PantallaBuscable("Primeros pasos", TITULO_DE_TU, Screen.PrimerosPasos),
)

/** Cuántos movimientos sueltos se listan, como mucho: los más recientes. El resto, en Movimientos. */
const val TOPE_DE_MOVIMIENTOS_EN_LA_BUSQUEDA: Int = 30

/** Cuántos de cada uno de los otros grupos. */
const val TOPE_POR_GRUPO_EN_LA_BUSQUEDA: Int = 8

/**
 * El número de [consulta] si es un monto —«27500», «$27.500», «27,500»—, o `null` si trae letras o
 * no trae dígitos.
 */
fun montoDeLaConsulta(consulta: String): Long? {
    val limpia = consulta.trim().removePrefix("$").trim()
    if (limpia.isEmpty() || limpia.any { !it.isDigit() && it != '.' && it != ',' && it != ' ' }) return null
    return limpia.filter { it.isDigit() }.takeIf { it.isNotEmpty() && it.length <= 15 }?.toLongOrNull()
}

private fun contiene(texto: String?, q: String): Boolean = texto != null && normalizarParaBuscar(texto).contains(q)

/** ¿[evento] pasa los chips? */
private fun pasaLosFiltros(evento: FinancialEvent, filtros: FiltrosDeBusqueda, periodo: RangoDelPeriodo?): Boolean {
    when (filtros.tipo) {
        FiltroDeTipo.TODO -> Unit
        FiltroDeTipo.GASTOS -> if (evento.type != TransactionType.EXPENSE) return false
        FiltroDeTipo.INGRESOS -> if (evento.type != TransactionType.INCOME) return false
    }
    if (filtros.soloEstePeriodo && periodo != null && evento.timestamp !in periodo) return false
    if (filtros.cuentaId != null && evento.accountId != filtros.cuentaId) return false
    return true
}

/**
 * **Una fila por par**, en el orden de [eventos]: de las dos patas de un `transferId` se queda **la que
 * cuenta** ([laPataQueCuenta]: la de la cuenta de dinero en una cuota —un gasto, «−$980.000»— o en un
 * desembolso) y, si ninguna cuenta (traspaso, pago de tarjeta), la primera. Antes se quedaba la primera
 * siempre, y una cuota podía salir por la pata del crédito: sin signo y por el abono a capital, no por
 * lo que salió de tu cuenta. Va en el lugar de la primera pata. Pura.
 */
fun unaPataPorPar(eventos: List<FinancialEvent>, tipos: Map<String, AccountType> = emptyMap()): List<FinancialEvent> {
    val porPar = eventos.filter { it.transferId != null }.groupBy { it.transferId }
    val vistos = mutableSetOf<String>()
    return eventos.mapNotNull { evento ->
        val par = evento.transferId ?: return@mapNotNull evento
        if (!vistos.add(par)) return@mapNotNull null
        val patas = porPar[par].orEmpty()
        val salida = patas.firstOrNull { it.type == TransactionType.EXPENSE }
        val entrada = patas.firstOrNull { it.type == TransactionType.INCOME }
        if (salida != null && entrada != null) laPataQueCuenta(salida, entrada, tipos) ?: evento else evento
    }
}

/**
 * **El nombre de un comercio de Buscar** (vuelta 67): **su marca** si la tiene («Éxito», que junta
 * Éxito Suba y Éxito Laureles; antes se llamaba «Éxito Suba» y sumaba también Laureles). Sin marca, el
 * texto que más se repite en el grupo; a igual cuenta, el del más reciente (el primero). Pura.
 */
fun nombreDelComercio(delComercio: List<FinancialEvent>): String {
    val ejemplo = delComercio.first()
    marcaDe(ejemplo)?.let { return it.nombre }
    return delComercio.map { (it.merchant?.takeIf { m -> m.isNotBlank() } ?: it.description).trim() }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ejemplo.description.trim()
}

/**
 * **El subtítulo de un movimiento en Buscar** (vuelta 67): el de su fila en Actividad
 * ([textosDeLaFila]) con la fecha, porque acá no hay días: «Mercado · 27 de octubre · Nu». La categoría
 * no se repite si ya es el título. Pura.
 */
fun subtituloDeUnMovimientoEnBuscar(
    evento: FinancialEvent,
    nombreDeLaCuenta: String?,
    /**
     * **Un solo formato** (vuelta 87): la fecha corta («28 oct», «Hoy», «Ayer»; el año solo si no es el
     * actual). Antes la fila decía «28 oct» con la cuenta y «27 de octubre» sin ella.
     */
    fecha: String,
): String {
    val titulo = textosDeLaFila(evento, nombreDeLaCuenta).titulo
    val categoria = evento.category.takeIf { it.isNotBlank() && normalizarParaBuscar(it) != normalizarParaBuscar(titulo) }
    // Revisión de la 83: la cuenta solo va cuando no es la habitual, y entonces **primero** y con la fecha
    // corta («Nu · Mercado · 21 oct»): al final era lo que se cortaba con dos renglones a la derecha.
    val cuenta = nombreDeLaCuenta?.takeIf { it.isNotBlank() }
    return if (cuenta != null) listOfNotNull(cuenta, categoria, fecha).joinToString(" · ")
    else listOfNotNull(categoria, fecha).joinToString(" · ")
}

/**
 * **Los movimientos en los que busca Buscar** (vuelta 78): la historia y **tu parte de lo que pagó otra
 * persona en un grupo** ([tuParteComoEventos], de `/api/espacios/tu-parte`), cada una en su día con
 * [conTuParteDeOtros] —la misma de Actividad—. Así «mercado» suma lo mismo que la categoría en En qué se
 * va, el widget y Movi AI, que la cuentan el día del gasto. Pura.
 */
fun eventosParaBuscar(dias: List<EventDay>, tuParteDeOtros: List<FinancialEvent>): List<FinancialEvent> =
    conTuParteDeOtros(dias, tuParteDeOtros).flatMap { it.items }

/**
 * **El subtítulo de tu parte de un gasto que pagó otro** (vuelta 78): la categoría si no es el título,
 * y la fecha. Quién pagó va debajo de la cifra ([debajoDeTuParteEnBuscar]). Pura.
 */
fun subtituloDeTuParteEnBuscar(evento: FinancialEvent, fecha: String): String {
    val titulo = textosDeTuParteDeOtro(evento, espacio = null).titulo
    val categoria = evento.category.takeIf { it.isNotBlank() && normalizarParaBuscar(it) != normalizarParaBuscar(titulo) }
    return listOfNotNull(categoria, fecha.takeIf { it.isNotBlank() }).joinToString(" · ")
}

/**
 * **Debajo de tu parte de un gasto que pagó otro, en Buscar** (revisión de la 78): «de $240.000 · pagó
 * Caro» —de cuánto fue el gasto del grupo ([FinancialEvent.montoDelGrupo]) y quién lo pagó—, como «de
 * $88.600 · con Caro» en un gasto tuyo repartido. Sin el monto del grupo (un server viejo), «Tu parte ·
 * pagó Caro». Pura.
 */
fun debajoDeTuParteEnBuscar(evento: FinancialEvent, espacio: Espacio?): String {
    val quien = quienPagoTuParte(evento, espacio).replaceFirstChar { it.lowercase() }
    val deCuanto = evento.montoDelGrupo?.let { "de ${formatCOP(it)}" } ?: TU_PARTE
    return "$deCuanto · $quien"
}

/**
 * **Todos los movimientos que coinciden** con [consulta] y los chips, del más reciente al más viejo y con
 * una fila por par ([unaPataPorPar]). Sin el tope de [TOPE_DE_MOVIMIENTOS_EN_LA_BUSQUEDA]: la lista de
 * Buscar muestra los más recientes, pero los totales ([totalesDeLaBusqueda]) suman **todos**.
 */
fun movimientosQueCoinciden(
    consulta: String,
    datos: DatosDeBusqueda,
    filtros: FiltrosDeBusqueda = FiltrosDeBusqueda(),
    periodo: RangoDelPeriodo? = null,
): List<FinancialEvent> {
    if (normalizarParaBuscar(consulta).isEmpty()) return emptyList()
    val monto = montoDeLaConsulta(consulta)
    return datos.eventos
        .filter { (monto != null && it.amount == monto) || matchesQuery(it, consulta) }
        .filter { pasaLosFiltros(it, filtros, periodo) }
        .sortedByDescending { it.timestamp }
        // Un traspaso son dos patas con el mismo `transferId`: se lista una — la que cuenta (vuelta 50).
        .let { unaPataPorPar(it, datos.cuentas.associate { c -> c.id to c.type }) }
}

/** Lo que suman los movimientos de un tipo en una búsqueda: en total y en el período en curso. */
data class SumaDeLaBusqueda(val total: Long, val delPeriodo: Long)

/**
 * **Los totales de Buscar** (vuelta 87): «uber» daba «Movimientos · 30» sin decir cuánto sumaban.
 *
 * - **Solo lo que cuenta como gasto o ingreso** (`comoSePresenta`, la regla de «Gastos» de Actividad y
 *   de En qué se va): sin traspasos, pagos de tarjeta ni ajustes, y sin lo que espera en «Por revisar»
 *   (`esperaEnPorConfirmar`: se cuenta en [porConfirmar] y se dice). Es lo que el dueño ya llama «lo que gasté».
 * - **Tu parte**: un gasto repartido suma `montoContado` (tu parte), igual que En qué se va; tu parte de lo
 *   que pagó otro ya viene como un movimiento de [DatosDeBusqueda.eventos].
 * - **Solo pesos**: lo que está en otra moneda no se mezcla; se cuenta en [enOtraMoneda] y se dice.
 * - **Gastos e ingresos van aparte** (no se restan: «Gastaste» y «Te entró» son dos cosas): cada uno
 *   con su suma, y solo aparece el que tiene algo.
 * - **Todos los que coinciden**, no solo los [TOPE_DE_MOVIMIENTOS_EN_LA_BUSQUEDA] de la lista.
 *
 * [cuantos] son todos los movimientos que coinciden (listados o no).
 */
data class TotalesDeLaBusqueda(
    val cuantos: Int,
    val gastos: SumaDeLaBusqueda?,
    val ingresos: SumaDeLaBusqueda?,
    val enOtraMoneda: Int,
    /** Los que esperan en «Por revisar» (`esperaEnPorConfirmar`): no cuentan todavía, y se dice. */
    val porConfirmar: Int = 0,
)

/** Ver [TotalesDeLaBusqueda]. `null` sin consulta o sin ningún movimiento. Pura. */
fun totalesDeLaBusqueda(
    consulta: String,
    datos: DatosDeBusqueda,
    filtros: FiltrosDeBusqueda = FiltrosDeBusqueda(),
    periodo: RangoDelPeriodo? = null,
): TotalesDeLaBusqueda? {
    val coinciden = movimientosQueCoinciden(consulta, datos, filtros, periodo)
    if (coinciden.isEmpty()) return null
    val tipos = datos.cuentas.associate { it.id to it.type }
    var gastoTotal = 0L; var gastoPeriodo = 0L; var hayGastos = false
    var ingresoTotal = 0L; var ingresoPeriodo = 0L; var hayIngresos = false
    var otraMoneda = 0
    var porConfirmar = 0
    coinciden.forEach { e ->
        val presentacion = comoSePresenta(e, tipos[e.accountId])
        if (presentacion == PresentacionDelMovimiento.NEUTRO) return@forEach
        if (esperaEnPorConfirmar(e.reconciliationStatus)) { porConfirmar++; return@forEach }
        if (e.currency != "COP") { otraMoneda++; return@forEach }
        val monto = montoContado(e)
        val enElPeriodo = periodo != null && e.timestamp in periodo
        if (presentacion == PresentacionDelMovimiento.GASTO) {
            hayGastos = true; gastoTotal += monto; if (enElPeriodo) gastoPeriodo += monto
        } else {
            hayIngresos = true; ingresoTotal += monto; if (enElPeriodo) ingresoPeriodo += monto
        }
    }
    return TotalesDeLaBusqueda(
        cuantos = coinciden.size,
        gastos = if (hayGastos) SumaDeLaBusqueda(gastoTotal, gastoPeriodo) else null,
        ingresos = if (hayIngresos) SumaDeLaBusqueda(ingresoTotal, ingresoPeriodo) else null,
        enOtraMoneda = otraMoneda,
        porConfirmar = porConfirmar,
    )
}

/**
 * **Las líneas de los totales** de [totales], bajo «Movimientos · 30»: «Gastaste $412.300 en total · $98.000
 * este período» y, si la búsqueda trae ingresos, «Te entró $1.500.000 en total · $0 este período». Con el
 * chip «Este período» prendido ([soloEstePeriodo]) todo es del período y se dice una sola vez («Gastaste
 * $98.000 este período»). Si hay más movimientos que los listados, una línea lo dice. Vacía sin nada que sumar.
 * Pura.
 */
fun lineasDeLosTotales(
    totales: TotalesDeLaBusqueda?,
    soloEstePeriodo: Boolean = false,
    listados: Int = TOPE_DE_MOVIMIENTOS_EN_LA_BUSQUEDA,
): List<String> {
    if (totales == null) return emptyList()
    fun linea(verbo: String, suma: SumaDeLaBusqueda): String =
        if (soloEstePeriodo) "$verbo ${formatCOP(suma.total)} este período"
        else "$verbo ${formatCOP(suma.total)} en total · ${formatCOP(suma.delPeriodo)} este período"
    return buildList {
        if (totales.cuantos > listados) add("Los $listados más recientes de ${totales.cuantos}")
        totales.gastos?.let { add(linea("Gastaste", it)) }
        totales.ingresos?.let { add(linea("Te entró", it)) }
        if (totales.porConfirmar > 0) {
            add(if (totales.porConfirmar == 1) "Sin contar 1 por confirmar" else "Sin contar ${totales.porConfirmar} por confirmar")
        }
        if (totales.enOtraMoneda > 0) {
            add(if (totales.enOtraMoneda == 1) "Sin contar 1 en otra moneda" else "Sin contar ${totales.enOtraMoneda} en otra moneda")
        }
    }
}

/**
 * **Buscar [consulta] en lo que hay a mano.** Vacío con la consulta en blanco. Los grupos sin
 * resultados no vienen.
 *
 * - **Movimientos**: [matchesQuery] (descripción, comercio, categoría, sin tildes ni mayúsculas) o,
 *   si la consulta es un número, el monto exacto. Con los chips; los más recientes primero; un
 *   traspaso aparece una vez (no sus dos patas).
 * - **Comercios**: los movimientos de arriba cuyo comercio o descripción coincide, agrupados por
 *   comercio, con cuántos y cuánto en el período en curso ([periodo]). Tocarlo abre Movimientos
 *   con esa búsqueda.
 * - **Personas y comercios** guardados (nombre, número o llave), **categorías**, **cuentas** y
 *   **pantallas** por nombre.
 */
fun buscarEnMovi(
    consulta: String,
    datos: DatosDeBusqueda,
    filtros: FiltrosDeBusqueda = FiltrosDeBusqueda(),
    periodo: RangoDelPeriodo? = null,
): List<GrupoDeResultados> {
    val q = normalizarParaBuscar(consulta)
    if (q.isEmpty()) return emptyList()
    val monto = montoDeLaConsulta(consulta)

    val coinciden = movimientosQueCoinciden(consulta, datos, filtros, periodo)

    val movimientos = coinciden.take(TOPE_DE_MOVIMIENTOS_EN_LA_BUSQUEDA).map { ResultadoDeBusqueda.Movimiento(it) }

    // Los comercios salen del nombre, no del monto: «27500» no es el nombre de ningún comercio.
    val comercios = if (monto != null) emptyList() else coinciden
        .filter { !isTransferLeg(it) && (contiene(it.merchant, q) || contiene(it.description, q)) }
        // Vuelta 66: la clave y la cuenta de `:core` ([claveDelComercio], [vecesEnElComercio]), las
        // mismas del detalle de un movimiento: los dos dicen el mismo «N veces» y el mismo total.
        .groupBy { claveDelComercio(it) }
        .entries
        .mapNotNull { (clave, delComercio) ->
            if (clave == null) return@mapNotNull null
            val ejemplo = delComercio.first()
            if (vecesEnElComercio(delComercio, clave).veces < 2) return@mapNotNull null
            val enElPeriodo = vecesEnElComercio(
                delComercio.filter { periodo != null && it.timestamp in periodo && it.currency == ejemplo.currency },
                clave,
            )
            ResultadoDeBusqueda.Comercio(
                nombre = nombreDelComercio(delComercio),
                ejemplo = ejemplo,
                enElPeriodo = enElPeriodo.veces,
                totalDelPeriodo = enElPeriodo.total,
                moneda = ejemplo.currency,
            )
        }
        .sortedWith(compareByDescending<ResultadoDeBusqueda.Comercio> { it.totalDelPeriodo }.thenBy { it.nombre })
        .take(TOPE_POR_GRUPO_EN_LA_BUSQUEDA)

    val personas = datos.destinos
        .filter { d ->
            contiene(d.nombre, q) || contiene(d.deQuien, q) ||
                // El número de su cuenta («0756») también: un número puede ser un monto o una cuenta.
                d.numero.contains(q) || d.llave?.let { contiene(it, q) } == true ||
                d.identificadores.any { contiene(it.valor, q) }
        }
        .sortedBy { normalizarParaBuscar(it.nombre) }
        .take(TOPE_POR_GRUPO_EN_LA_BUSQUEDA)
        .map { ResultadoDeBusqueda.Persona(it) }

    val categorias = datos.categorias
        .filter { it.isNotBlank() && contiene(it, q) }
        .distinctBy { normalizarParaBuscar(it) }
        .sortedBy { normalizarParaBuscar(it) }
        .take(TOPE_POR_GRUPO_EN_LA_BUSQUEDA)
        .map { ResultadoDeBusqueda.Categoria(it) }

    val cuentas = datos.cuentas
        .filter { contiene(it.name, q) }
        .take(TOPE_POR_GRUPO_EN_LA_BUSQUEDA)
        .map { ResultadoDeBusqueda.Cuenta(it) }

    val pantallas = if (monto != null) emptyList() else PANTALLAS_BUSCABLES
        .filter { p -> contiene(p.nombre, q) || p.tambien.any { contiene(it, q) } }
        .distinctBy { it.nombre }
        .take(TOPE_POR_GRUPO_EN_LA_BUSQUEDA)
        .map { ResultadoDeBusqueda.Pantalla(it.nombre, it.donde, it.destino) }

    return listOf<List<ResultadoDeBusqueda>>(movimientos, comercios, personas, categorias, cuentas, pantallas)
        .filter { it.isNotEmpty() }
        .map { GrupoDeResultados(it.first().tipo, it) }
}
