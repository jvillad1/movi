package com.jvillada.movi.ui.primeruso

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.debeAbrirseLaGuiaDePrimerUso
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
enum class PasoDePrimerUso(val titulo: String) {
    BIENVENIDA("Te damos la bienvenida a Movi"),
    PERIODO("Tu período"),
    CUENTAS("Tus cuentas"),
    CAPTURA("Que Movi se entere solo"),
    RECURRENTES("Lo que pagas cada mes"),
    LISTO("Listo"),
    ;

    val numero: Int get() = ordinal + 1

    /**
     * ¿Lleva el botón de texto «Saltar este paso»? Los pasos opcionales (el período, la captura, los
     * pagos de cada mes). La bienvenida y el final no tienen nada que saltar, y las cuentas se saltan
     * con «Ahora no»: sin ninguna cuenta Movi no tiene dónde anotar nada.
     */
    val seSaltaConUnBoton: Boolean get() = this == PERIODO || this == CAPTURA || this == RECURRENTES

    /** La fracción de la barra de progreso: el primer paso ya cuenta, el último la llena. */
    val progreso: Float get() = numero.toFloat() / entries.size

    val anterior: PasoDePrimerUso? get() = entries.getOrNull(ordinal - 1)
    val siguiente: PasoDePrimerUso? get() = entries.getOrNull(ordinal + 1)
}

/**
 * **Cómo se entera Movi sola, según dónde corre.** Android lee los SMS y las notificaciones del banco
 * (la sección de Captura del banco); la web y iOS no pueden, y ahí lo que funciona es reenviar los
 * correos del banco a la dirección de Movi.
 */
enum class ModoDeCaptura { PERMISOS_DEL_TELEFONO, REENVIO_DE_CORREO }

/** Pura, para poder probar la decisión sin depender de en qué plataforma corre la prueba. */
fun modoDeCapturaDe(esAndroid: Boolean): ModoDeCaptura =
    if (esAndroid) ModoDeCaptura.PERMISOS_DEL_TELEFONO else ModoDeCaptura.REENVIO_DE_CORREO

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

/**
 * Los bancos que la guía ofrece de un toque, por su id de `MarcasConocidas` y en el orden en que se
 * ven. Los más comunes en Colombia; cualquier otro se escribe a mano.
 */
val BANCOS_DE_LA_GUIA: List<String> = listOf(
    "bancolombia", "nu", "davivienda", "nequi", "banco_de_bogota", "bbva", "daviplata",
)

/**
 * **Los tipos de cuenta que ofrece la guía**, con rótulos que entiende alguien nuevo (vuelta de
 * diseño 3). Antes eran «Dinero / Inversión», los dos grupos de Patrimonio → «Nueva cuenta», que
 * quien empieza no sabe leer. Cada rótulo va a un [AccountType] que ya existe —no se inventa
 * ninguno—: ahorros, corriente y efectivo son los tres de **Dinero** (`AccountType.group`), y se
 * suman igual en todas las cuentas de Movi; lo que cambia es que el efectivo no recibe extractos.
 */
val TIPOS_DE_CUENTA_DE_LA_GUIA: List<Pair<String, AccountType>> = listOf(
    "Ahorros" to AccountType.SAVINGS,
    "Corriente" to AccountType.CHECKING,
    "Efectivo" to AccountType.CASH,
    "Inversión" to AccountType.INVESTMENT,
)

/**
 * Lo que quedó al terminar, para el paso «Listo». [periodo] es el texto de «Inicio del mes» si se
 * guardó en la guía (`null` = no se tocó); [captura] dice si Movi ya se puede enterar sola.
 */
data class ResumenDelPrimerUso(
    val periodo: String?,
    val cuentas: Int,
    val tarjetas: Int,
    val recurrentes: Int,
    val captura: EstadoDeLaCaptura,
)

enum class EstadoDeLaCaptura { ACTIVA, DIRECCION_COPIADA, PENDIENTE }

/** Un renglón del resumen: qué es, cómo quedó, y si quedó hecho. */
data class LineaDelResumen(val titulo: String, val detalle: String, val hecho: Boolean)

/** Las líneas del paso «Listo», en el orden de los pasos. Pura. */
fun lineasDelResumen(r: ResumenDelPrimerUso): List<LineaDelResumen> = listOf(
    LineaDelResumen("Tu período", r.periodo ?: "Sin cambiar: mes de calendario", r.periodo != null),
    LineaDelResumen(
        "Tus cuentas",
        when (r.cuentas) {
            0 -> "Ninguna todavía"
            1 -> "1 cuenta"
            else -> "${r.cuentas} cuentas"
        } + when (r.tarjetas) {
            0 -> ""
            1 -> " y 1 tarjeta"
            else -> " y ${r.tarjetas} tarjetas"
        },
        r.cuentas > 0,
    ),
    LineaDelResumen(
        "Que Movi se entere solo",
        when (r.captura) {
            EstadoDeLaCaptura.ACTIVA -> "Activo en este teléfono"
            EstadoDeLaCaptura.DIRECCION_COPIADA -> "Dirección copiada: falta el reenvío en Gmail"
            EstadoDeLaCaptura.PENDIENTE -> "Pendiente"
        },
        r.captura == EstadoDeLaCaptura.ACTIVA,
    ),
    LineaDelResumen(
        "Lo que pagas cada mes",
        when (r.recurrentes) {
            0 -> "Ninguno todavía"
            1 -> "1 pago"
            else -> "${r.recurrentes} pagos"
        },
        r.recurrentes > 0,
    ),
)
