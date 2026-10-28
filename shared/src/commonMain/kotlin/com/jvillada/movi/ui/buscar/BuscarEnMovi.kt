package com.jvillada.movi.ui.buscar

import com.jvillada.movi.shared.model.enFormaDeTitulo
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.claveDeComercio
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.ui.mas.TITULO_DE_TU
import com.jvillada.movi.ui.espacios.TITULO_DE_PERSONAS
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
        override val titulo get() =
            if (evento.description.isBlank()) enFormaDeTitulo(evento.merchant ?: evento.category)
            else textosDeLaFila(evento, nombreDeLaCuenta = null).titulo
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
    PantallaBuscable(TITULO_DE_TU, "Pestaña", Screen.Tu, listOf("ajustes", "perfil", "huella", "ocultar los saldos", "tema", "como se ve", "avisos por correo", "corte del periodo", "contrasena", "cerrar sesion", "descargar tus datos")),
    PantallaBuscable("Patrimonio", TITULO_DE_TU, Screen.Accounts, listOf("cuentas", "tu plata")),
    PantallaBuscable("Créditos", "Patrimonio", Screen.Credits, listOf("deudas", "tarjetas", "prestamos")),
    PantallaBuscable("Cómo salir de tus deudas", "Créditos", Screen.SalidaDeDeudas, listOf("abono", "avalancha", "bola de nieve")),
    PantallaBuscable("Cuadre de saldos", TITULO_DE_TU, Screen.CuadreDeSaldos, listOf("cuadrar", "ajustar saldo")),
    PantallaBuscable(TITULO_DE_PERSONAS, TITULO_DE_TU, Screen.Espacios, listOf("grupos", "espacios", "hogar", "repartir", "casa")),
    // Ola R4: su casa es Personas (antes Espacios).
    PantallaBuscable(PERSONAS_Y_COMERCIOS, TITULO_DE_PERSONAS, Screen.Destinos(), listOf("terceros", "cuentas de otros", "hogar")),
    PantallaBuscable("Categorías", TITULO_DE_TU, Screen.Categorias),
    PantallaBuscable("Documentos", TITULO_DE_TU, Screen.Documentos, listOf("extractos", "comprobantes", "papeles")),
    PantallaBuscable("Compartir", TITULO_DE_TU, Screen.Compartir, listOf("enlace")),
    PantallaBuscable("Movi AI", TITULO_DE_TU, Screen.AIChat(), listOf("asistente", "preguntar")),
    PantallaBuscable("Lo que Movi sabe de ti", TITULO_DE_TU, Screen.LoQueMoviSabe, listOf("memoria")),
    PantallaBuscable("Editar inicio", TITULO_DE_TU, Screen.EditarInicio, listOf("hoy", "secciones", "orden", "ocultar")),
    PantallaBuscable(tituloDeCapturaDelBanco, TITULO_DE_TU, Screen.CapturaDelBanco, listOf("captura del banco", "mensajes del banco", "sms", "notificaciones", "banco mudo", "correo")),
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
