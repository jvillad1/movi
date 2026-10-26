package com.jvillada.movi.ui.buscar

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.claveDeComercio
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.ui.mas.TITULO_DE_AJUSTES
import com.jvillada.movi.ui.sms.tituloDeCapturaDelBanco
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
        override val titulo get() = evento.description.ifBlank { evento.merchant ?: evento.category }
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
        override val clave get() = "com:${claveDeComercio(ejemplo) ?: nombre}"
        override val titulo get() = nombre
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
    PantallaBuscable("Inicio", "Pestaña", Screen.Dashboard, listOf("hoy", "te quedan")),
    PantallaBuscable("Movimientos", "Pestaña", Screen.Transactions(), listOf("gastos", "ingresos")),
    PantallaBuscable("Por revisar", "Movimientos", Screen.PorRevisar, listOf("pendientes", "confirmar", "avisos del banco")),
    PantallaBuscable("Plan", "Pestaña", Screen.Plan(), listOf("pagos del mes", "disponible")),
    PantallaBuscable("Presupuestos", "Plan", Screen.Budgets),
    PantallaBuscable("Tus períodos", "Plan", Screen.Periodos, listOf("periodos", "como me fue")),
    PantallaBuscable("Patrimonio", "Pestaña", Screen.Accounts, listOf("cuentas", "tu plata")),
    PantallaBuscable("Créditos", "Patrimonio", Screen.Credits, listOf("deudas", "tarjetas", "prestamos")),
    PantallaBuscable("Cómo salir de tus deudas", "Créditos", Screen.SalidaDeDeudas, listOf("abono", "avalancha", "bola de nieve")),
    PantallaBuscable("Cuadre de saldos", "Patrimonio", Screen.CuadreDeSaldos, listOf("cuadrar", "ajustar saldo")),
    // Ola R4: su casa es Espacios (y Ajustes); Movimientos ya no tiene su fila.
    PantallaBuscable(PERSONAS_Y_COMERCIOS, "Espacios y Ajustes", Screen.Destinos(), listOf("terceros", "cuentas de otros", "hogar")),
    PantallaBuscable(TITULO_DE_AJUSTES, "El avatar", Screen.Mas),
    PantallaBuscable("Perfil", TITULO_DE_AJUSTES, Screen.Profile, listOf("huella", "ocultar los saldos", "tema", "como se ve", "avisos por correo", "corte del periodo")),
    PantallaBuscable("Categorías", TITULO_DE_AJUSTES, Screen.Categorias),
    PantallaBuscable("Documentos", TITULO_DE_AJUSTES, Screen.Documentos, listOf("extractos", "comprobantes", "papeles")),
    PantallaBuscable("Compartir", TITULO_DE_AJUSTES, Screen.Compartir, listOf("enlace")),
    PantallaBuscable("Movi AI", TITULO_DE_AJUSTES, Screen.AIChat(), listOf("asistente", "preguntar")),
    PantallaBuscable("Lo que Movi sabe de ti", TITULO_DE_AJUSTES, Screen.LoQueMoviSabe, listOf("memoria")),
    PantallaBuscable("Editar inicio", TITULO_DE_AJUSTES, Screen.EditarInicio, listOf("hoy", "secciones", "orden", "ocultar")),
    PantallaBuscable(tituloDeCapturaDelBanco, TITULO_DE_AJUSTES, Screen.CapturaDelBanco, listOf("captura del banco", "mensajes del banco", "sms", "notificaciones", "banco mudo", "correo")),
    PantallaBuscable("Borrar mi cuenta", "Perfil", Screen.BorrarLaCuenta, listOf("eliminar cuenta")),
    PantallaBuscable("Primeros pasos", TITULO_DE_AJUSTES, Screen.PrimerosPasos),
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

    val coinciden = datos.eventos
        .filter { (monto != null && it.amount == monto) || matchesQuery(it, consulta) }
        .filter { pasaLosFiltros(it, filtros, periodo) }
        .sortedByDescending { it.timestamp }
        // Un traspaso son dos patas con el mismo `transferId`: se lista una.
        .distinctBy { it.transferId ?: it.id }

    val movimientos = coinciden.take(TOPE_DE_MOVIMIENTOS_EN_LA_BUSQUEDA).map { ResultadoDeBusqueda.Movimiento(it) }

    // Los comercios salen del nombre, no del monto: «27500» no es el nombre de ningún comercio.
    val comercios = if (monto != null) emptyList() else coinciden
        .filter { !isTransferLeg(it) && (contiene(it.merchant, q) || contiene(it.description, q)) }
        .groupBy { claveDeComercio(it) }
        .filterKeys { it != null }
        .values
        .filter { it.size >= 2 }
        .map { delComercio ->
            val ejemplo = delComercio.first()
            val enElPeriodo = delComercio.filter { periodo != null && it.timestamp in periodo }
            ResultadoDeBusqueda.Comercio(
                nombre = (ejemplo.merchant?.takeIf { it.isNotBlank() } ?: ejemplo.description).trim(),
                ejemplo = ejemplo,
                enElPeriodo = enElPeriodo.size,
                totalDelPeriodo = enElPeriodo.filter { it.currency == ejemplo.currency }.sumOf { com.jvillada.movi.shared.model.montoContado(it) },
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
