package com.jvillada.movi.ui.primeruso

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.CapturaDelPrimerUso
import com.jvillada.movi.shared.model.EstadoDelPrimerPaso
import com.jvillada.movi.shared.model.LoQueYaHay
import com.jvillada.movi.shared.model.PrimerPaso
import com.jvillada.movi.shared.model.capturaDelPrimerUsoEn
import com.jvillada.movi.shared.model.debeAbrirseLaGuiaDePrimerUso
import com.jvillada.movi.shared.model.primerosPasos
import com.jvillada.movi.shared.model.tituloDelPrimerPaso
import com.jvillada.movi.shared.repository.WalletRepository

/**
 * # «Primer uso»: la guía por pasos de una cuenta nueva
 *
 * Hasta acá crear una cuenta era un formulario de tres campos y, después, una tarjeta «Primeros
 * pasos» en el Inicio. La primera persona que la va a usar sin nadie al lado (el papá del dueño)
 * necesita que Movi lo lleve de la mano: qué día empieza su mes, qué cuentas tiene, cómo se entera
 * Movi sola de lo que pasa y qué paga cada mes. Esta guía es eso, a pantalla completa y con cada
 * paso saltable. Lo que se salte queda en «Primeros pasos», que sigue existiendo.
 *
 * Cada paso guarda con lo de siempre —`updateUserProfile` como Perfil, `crearCuentaConSaldo` como la
 * hoja de crear cuenta, `createCard` como la hoja de tarjetas, `createRecurringRule` como la de
 * recurrentes—: la guía no tiene escrituras propias, salvo marcarse terminada.
 */
enum class PasoDePrimerUso(val primerPaso: PrimerPaso?) {
    BIENVENIDA(null),
    PERIODO(PrimerPaso.PERIODO),
    CUENTAS(PrimerPaso.CUENTAS),
    CAPTURA(PrimerPaso.CAPTURA),
    RECURRENTES(PrimerPaso.FIJOS),
    LISTO(null),
    ;

    /** El título que se lee arriba del paso: el de la lista única para los pasos de en medio. */
    fun titulo(captura: CapturaDelPrimerUso): String =
        primerPaso?.let { tituloDelPrimerPaso(it, captura) } ?: when (this) {
            BIENVENIDA -> TITULO_DE_LA_BIENVENIDA
            else -> "Listo"
        }

    /**
     * ¿Lleva el botón de texto «Saltar este paso»? **Todos los de en medio** (vuelta 54: también las
     * cuentas, que antes solo se saltaban con «Ahora no» y dejaban «Siguiente» apagado sin otra salida
     * a la vista). La bienvenida y el final no tienen nada que saltar.
     */
    val seSaltaConUnBoton: Boolean get() = primerPaso != null
}

/** El paso de la guía que pinta un paso de la lista única. */
fun pasoDeLaGuia(paso: PrimerPaso): PasoDePrimerUso = PasoDePrimerUso.entries.first { it.primerPaso == paso }

/**
 * **Los pasos de la guía en esta plataforma**: la bienvenida, los de la lista única ([primerosPasos],
 * en su orden; sin captura posible, sin ese paso) y el final. «Paso N de M» y la barra salen de acá.
 */
fun pasosDeLaGuia(captura: CapturaDelPrimerUso): List<PasoDePrimerUso> {
    val delMedio = primerosPasos(LoQueYaHay(false, 0, false, 0), captura).map { pasoDeLaGuia(it.paso) }
    return listOf(PasoDePrimerUso.BIENVENIDA) + delMedio + PasoDePrimerUso.LISTO
}

/** El número de [paso] en [pasos] (desde 1). */
fun numeroDelPaso(paso: PasoDePrimerUso, pasos: List<PasoDePrimerUso>): Int = pasos.indexOf(paso) + 1

/** La fracción de la barra de progreso: el primer paso ya cuenta, el último la llena. */
fun progresoDelPaso(paso: PasoDePrimerUso, pasos: List<PasoDePrimerUso>): Float =
    numeroDelPaso(paso, pasos).toFloat() / pasos.size

fun pasoAnterior(paso: PasoDePrimerUso, pasos: List<PasoDePrimerUso>): PasoDePrimerUso? =
    pasos.getOrNull(pasos.indexOf(paso) - 1)

fun pasoSiguiente(paso: PasoDePrimerUso, pasos: List<PasoDePrimerUso>): PasoDePrimerUso? =
    pasos.indexOf(paso).takeIf { it >= 0 }?.let { pasos.getOrNull(it + 1) }

/**
 * **Cómo se entera Movi sola, según dónde corre**: la de la lista única (`:core`). Android lee los SMS y
 * las notificaciones del banco; la web y iOS, un extracto que se sube.
 */
typealias ModoDeCaptura = CapturaDelPrimerUso

/** Pura, para poder probar la decisión sin depender de en qué plataforma corre la prueba. */
fun modoDeCapturaDe(esAndroid: Boolean): ModoDeCaptura = capturaDelPrimerUsoEn(esAndroid)

/**
 * El estado de la guía **en esta sesión** (no en el server: eso es `UserProfile.primerUsoEn`).
 *
 * - [revisada]: ya se decidió si se abre, y no se vuelve a preguntar hasta la próxima sesión. Sin
 *   esto, «Ahora no» sin conexión (el server no se enteró) la volvería a abrir al llegar al Inicio.
 * - [enCurso]: la guía está abierta o a punto de abrirse. La tarjeta «Primeros pasos» del Inicio no
 *   compite con ella.
 *
 * Lo limpia `SessionManager.clear()`: es de quien se va.
 */
object GuiaDePrimerUso {
    var revisada: Boolean = false
        private set

    var enCurso by mutableStateOf(false)
        private set

    /** Se va a abrir (o ya se abrió): no se pregunta de nuevo en esta sesión. */
    fun abrir() {
        revisada = true
        enCurso = true
    }

    /** Terminada o descartada. */
    fun cerrar() {
        revisada = true
        enCurso = false
    }

    /** Se revisó y no hacía falta. */
    internal fun noHaceFalta() {
        revisada = true
    }

    fun olvidar() {
        revisada = false
        enCurso = false
    }
}

/**
 * **¿Hay que abrir la guía ahora?** Lo pregunta App.kt al llegar al Inicio con sesión, una vez por
 * sesión. Lee el perfil y las cuentas y decide con [debeAbrirseLaGuiaDePrimerUso] (:core).
 *
 * Si alguna de las dos lecturas falla no se decide nada y se vuelve a preguntar la próxima vez que
 * se llegue al Inicio: tapar el Inicio de alguien que sí tenía cuentas por una lectura caída sería
 * peor que abrir la guía un rato después.
 */
suspend fun revisarLaGuiaDePrimerUso(repo: WalletRepository): Boolean {
    if (GuiaDePrimerUso.revisada) return false
    val perfil = runCatching { repo.getUserProfile() }.getOrNull() ?: return false
    val cuentas = runCatching { repo.getAccounts() }.getOrNull() ?: return false
    return if (debeAbrirseLaGuiaDePrimerUso(perfil, cuentas)) {
        GuiaDePrimerUso.abrir()
        true
    } else {
        GuiaDePrimerUso.noHaceFalta()
        false
    }
}

/**
 * **Un atajo de «Lo que pagas cada mes»**: el nombre que se escribe y la categoría de siempre en la
 * que cae (de las predefinidas de :core).
 */
data class AtajoDeRecurrente(val nombre: String, val categoria: String)

val ATAJOS_DE_RECURRENTES: List<AtajoDeRecurrente> = listOf(
    AtajoDeRecurrente("Arriendo", "Vivienda"),
    AtajoDeRecurrente("Internet", "Servicios"),
    AtajoDeRecurrente("Celular", "Servicios"),
    AtajoDeRecurrente("Una suscripción", "Entretenimiento"),
)

// Los bancos de un toque y los tipos de cuenta («Ahorros · Corriente · Efectivo · Inversión») viven
// desde la vuelta de diseño 16 en `ui/accounts/FormularioDeCuenta.kt`: la guía y «Nueva cuenta»
// pintan el mismo bloque.

/**
 * Lo que quedó al terminar, para el paso «Listo»: el detalle de cada renglón. Si un paso quedó hecho o
 * pendiente **no se decide acá**: sale de la lista única ([primerosPasos]), la misma de la fila del
 * Inicio. [periodo] es el texto de «Inicio del mes» si ya está elegido (`null` = mes de calendario).
 */
data class ResumenDelPrimerUso(
    val periodo: String?,
    val cuentas: Int,
    val tarjetas: Int,
    val recurrentes: Int,
    val captura: EstadoDeLaCaptura,
    val modo: CapturaDelPrimerUso,
)

/**
 * Cómo quedó la captura. [ACTIVA]: el permiso del teléfono. [EXTRACTO_SUBIDO]: se subió uno en la guía.
 * [CON_MOVIMIENTOS]: ya hay movimientos (en la web y en iOS, el paso está hecho). [DIRECCION_COPIADA]: se
 * copió la dirección de reenvío, que no prueba que el reenvío ande.
 */
enum class EstadoDeLaCaptura { ACTIVA, EXTRACTO_SUBIDO, CON_MOVIMIENTOS, DIRECCION_COPIADA, PENDIENTE }

/** Un renglón del resumen: qué es, cómo quedó, y si quedó hecho. */
data class LineaDelResumen(val titulo: String, val detalle: String, val hecho: Boolean)

/** Las líneas del paso «Listo»: una por paso de la lista única, en su orden. Pura. */
fun lineasDelResumen(lista: List<EstadoDelPrimerPaso>, r: ResumenDelPrimerUso): List<LineaDelResumen> = lista.map { estado ->
    val detalle = when (estado.paso) {
        PrimerPaso.PERIODO -> if (estado.hecho) r.periodo ?: "Elegido" else "Sin elegir: mes de calendario"
        PrimerPaso.CUENTAS -> when (r.cuentas) {
            0 -> "Ninguna todavía"
            1 -> "1 cuenta"
            else -> "${r.cuentas} cuentas"
        } + when (r.tarjetas) {
            0 -> ""
            1 -> " y 1 tarjeta"
            else -> " y ${r.tarjetas} tarjetas"
        }
        PrimerPaso.CAPTURA -> when (r.captura) {
            EstadoDeLaCaptura.ACTIVA -> "Activo en este teléfono"
            EstadoDeLaCaptura.EXTRACTO_SUBIDO -> "Movi está leyendo tu extracto"
            EstadoDeLaCaptura.CON_MOVIMIENTOS -> "Ya tienes movimientos en Movi"
            EstadoDeLaCaptura.DIRECCION_COPIADA -> "Dirección copiada: falta el reenvío en Gmail"
            EstadoDeLaCaptura.PENDIENTE ->
                if (r.modo == CapturaDelPrimerUso.PERMISOS_DEL_TELEFONO) "Sin activar todavía" else "Sin subir todavía"
        }
        PrimerPaso.FIJOS -> when (r.recurrentes) {
            0 -> "Ninguno todavía"
            1 -> "1 pago"
            else -> "${r.recurrentes} pagos"
        }
    }
    LineaDelResumen(tituloDelPrimerPaso(estado.paso, r.modo), detalle, estado.hecho)
}
