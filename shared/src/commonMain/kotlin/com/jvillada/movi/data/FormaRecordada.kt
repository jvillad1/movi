package com.jvillada.movi.data

import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * # La forma de la última carga, para que el esqueleto la copie
 *
 * ## El defecto que cierra
 *
 * Medido en el teléfono del dueño (Ola B, pulido): los esqueletos de Créditos, Categorías y Cuentas
 * copian la forma de **una** cartera cualquiera, no la suya. Créditos reservaba una tarjeta de
 * resumen sin avisos, y la del dueño trae el ámbar («2 créditos no se terminan…») **y** el rojo
 * («En 1 crédito la cuota… no alcanza…»): al llegar los datos, la lista de préstamos bajaba
 * ~130 dp. Categorías no reservaba la tarjeta «Movi encontró 12 cosas para ordenar» (~50 dp de
 * empujón), y Cuentas dibujaba un grupo de 4 filas donde hay «DINERO · 5» e «INVERSIÓN · 2».
 *
 * Esto recuerda, por usuario y en el aparato, **los números que deciden la forma** de cada una de
 * esas pantallas la última vez que su lectura salió bien. Al montarse, el esqueleto los lee y
 * reserva exactamente eso. Sin nada recordado (la primera vez en este aparato), el esqueleto de
 * siempre.
 *
 * Ola B, tarea 2 (whole-branch review): Movimientos se sumó con un caso más chico —no cuántas
 * filas, sino si la línea del rango del período iba o no— pero la misma idea: sin ese dato, la
 * pantalla reserva de más para quien nunca la necesitó.
 *
 * ## Solo números (y un sí/no): nada de plata ni de nombres
 *
 * Cantidades de filas, de renglones, de tarjetas, o si algo se mostró o no — nunca un saldo, un
 * monto ni el nombre de una cuenta. Es lo que el esqueleto necesita, y lo que no se necesita no se
 * guarda: esto vive en el almacenamiento del aparato (`localStorage` en la web) sin cifrar, igual
 * que el tema.
 *
 * ## Por usuario, y borrada al cerrar sesión
 *
 * Misma regla que [com.jvillada.movi.ui.dashboard.InstantaneaDelInicio]: la clave lleva el
 * [SessionManager.userId], sin usuario no se lee ni se escribe nada, y `SessionManager.clear()` la
 * borra con el id que se va, **antes** de soltarlo.
 *
 * ## Lo que no puede pasar
 *
 * Lo mismo que en `TemaStore` y en la instantánea del Inicio: `Settings()` explota **al
 * construirse** en una JVM sin contexto o en un navegador con el almacenamiento bloqueado, así que
 * va `by lazy` y todo acceso adentro de un `runCatching`. Una forma que no deserializa —otra
 * versión de la app, o el almacenamiento corrupto— o que trae un número imposible (negativo,
 * enorme) vale `null`: el esqueleto de siempre, sin error.
 *
 * La lectura y la escritura entran por parámetro para probar la lógica en la JVM pelada; la app usa
 * [delAparato].
 */
class FormaRecordada(
    private val leer: (clave: String) -> String?,
    private val escribir: (clave: String, valor: String?) -> Unit,
) {
    fun creditos(userId: String?): FormaDeCreditos? =
        leerForma(userId, PANTALLA_CREDITOS, FormaDeCreditos.serializer())?.takeIf { it.esValida() }

    fun guardarCreditos(userId: String?, forma: FormaDeCreditos) =
        guardarForma(userId, PANTALLA_CREDITOS, forma, FormaDeCreditos.serializer())

    fun categorias(userId: String?): FormaDeCategorias? =
        leerForma(userId, PANTALLA_CATEGORIAS, FormaDeCategorias.serializer())?.takeIf { it.esValida() }

    fun guardarCategorias(userId: String?, forma: FormaDeCategorias) =
        guardarForma(userId, PANTALLA_CATEGORIAS, forma, FormaDeCategorias.serializer())

    fun cuentas(userId: String?): FormaDeCuentas? =
        leerForma(userId, PANTALLA_CUENTAS, FormaDeCuentas.serializer())?.takeIf { it.esValida() }

    fun guardarCuentas(userId: String?, forma: FormaDeCuentas) =
        guardarForma(userId, PANTALLA_CUENTAS, forma, FormaDeCuentas.serializer())

    /** Cuántas filas tenía «Tus períodos» la última carga que salió bien. */
    fun periodos(userId: String?): FormaDePeriodos? =
        leerForma(userId, PANTALLA_PERIODOS, FormaDePeriodos.serializer())?.takeIf { it.esValida() }

    fun guardarPeriodos(userId: String?, forma: FormaDePeriodos) =
        guardarForma(userId, PANTALLA_PERIODOS, forma, FormaDePeriodos.serializer())

    /**
     * Ola B, tarea 2 (whole-branch review, final fix wave): si la última carga que salió bien
     * mostraba la línea del rango del período debajo del mes, o no —con corte 1 (mes de
     * calendario) nunca hay línea que mostrar—. `null` (nada recordado, la primera vez en este
     * aparato) es «reservarla», el comportamiento de siempre; solo con un `false` explícito el
     * esqueleto deja de reservarla.
     */
    fun movimientos(userId: String?): FormaDeMovimientos? =
        leerForma(userId, PANTALLA_MOVIMIENTOS, FormaDeMovimientos.serializer())

    fun guardarMovimientos(userId: String?, forma: FormaDeMovimientos) =
        guardarForma(userId, PANTALLA_MOVIMIENTOS, forma, FormaDeMovimientos.serializer())

    /**
     * Anota si el período en curso lleva línea de rango, con la misma regla que decide si se pinta
     * ([rangoLegibleDe]: con corte 1 no hay nada que aclarar). Ola C: la línea es un dato del
     * CORTE del dueño, no de una pantalla, y la muestran Movimientos y Plan; las dos la anotan
     * con esto —y solo tras un perfil que contestó bien— y las dos la leen de [movimientos] para
     * reservarla (o no) antes de que el perfil conteste.
     *
     * **Reemplaza la [FormaDeMovimientos] entera**, no solo este campo: hoy es el único que tiene.
     * Si mañana se le agrega otro, esto tiene que leer la guardada y copiarla con el campo nuevo,
     * o cada visita a Plan borraría lo que Movimientos recordó.
     */
    fun recordarLineaDePeriodo(userId: String?, periodoDeHoy: PeriodoFinanciero, ajustes: PeriodSettings) =
        guardarMovimientos(userId, FormaDeMovimientos(lineaDePeriodo = rangoLegibleDe(periodoDeHoy, ajustes) != null))

    /** Al cerrar sesión: todas las formas de [userId]. */
    fun borrar(userId: String?) {
        val id = userId?.takeIf { it.isNotBlank() } ?: return
        PANTALLAS.forEach { escribir(clave(it, id), null) }
    }

    private fun <T> leerForma(userId: String?, pantalla: String, serializer: KSerializer<T>): T? {
        val id = userId?.takeIf { it.isNotBlank() } ?: return null
        val crudo = leer(clave(pantalla, id))
        if (crudo.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(serializer, crudo) }.getOrNull()
    }

    private fun <T> guardarForma(userId: String?, pantalla: String, forma: T, serializer: KSerializer<T>) {
        val id = userId?.takeIf { it.isNotBlank() } ?: return
        runCatching { json.encodeToString(serializer, forma) }.getOrNull()?.let { escribir(clave(pantalla, id), it) }
    }

    companion object {
        private val real = FormaRecordada(
            leer = { clave -> runCatching { formaSettings.getStringOrNull(clave) }.getOrNull() },
            escribir = { clave, valor ->
                runCatching {
                    if (valor == null) formaSettings.remove(clave) else formaSettings[clave] = valor
                }
            },
        )

        /**
         * La costura de las pruebas de pantalla, como `InstantaneaDelInicio.sustitutoDePrueba`: en
         * Robolectric `Settings()` no se construye, así que sin esto ninguna prueba podría montar
         * una pantalla con una forma recordada. `AppDePrueba` la vuelve a `null` antes de cada
         * método, **después** del logout que la vacía.
         */
        internal var sustitutoDePrueba: FormaRecordada? = null

        /** La que usa la app. Ver [sustitutoDePrueba]. */
        val delAparato: FormaRecordada get() = sustitutoDePrueba ?: real

        private const val PANTALLA_CREDITOS = "creditos"
        private const val PANTALLA_CATEGORIAS = "categorias"
        private const val PANTALLA_CUENTAS = "cuentas"
        private const val PANTALLA_MOVIMIENTOS = "movimientos"
        private const val PANTALLA_PERIODOS = "periodos"
        private val PANTALLAS =
            listOf(PANTALLA_CREDITOS, PANTALLA_CATEGORIAS, PANTALLA_CUENTAS, PANTALLA_MOVIMIENTOS, PANTALLA_PERIODOS)

        private fun clave(pantalla: String, userId: String) = "forma_${pantalla}_$userId"
    }
}

/**
 * Lo que decide el alto de Créditos por encima de la lista: la tarjeta de «Deuda total» y cuántos
 * préstamos vienen debajo.
 *
 * @property gruposDelResumen cuántas filas tiene cada grupo del resumen de lo que cuesta la deuda
 *   (intereses del mes, los que faltan, la última cuota), en orden y solo los que aparecen. Vacía
 *   si el resumen no se dibuja (ningún préstamo con plan).
 * @property renglonesDelAvisoAmbar cuántos renglones ocupó el aviso de «no se terminan a este
 *   ritmo»; 0 si no estaba. Renglones y no un sí/no porque el texto trae una cifra y parte distinto
 *   según su largo y el ancho de la pantalla — el esqueleto reserva el alto de verdad.
 * @property renglonesDelAvisoRojo lo mismo para el de la deuda que crece sola.
 * @property prestamos cuántas tarjetas de préstamo había.
 */
@Serializable
data class FormaDeCreditos(
    val gruposDelResumen: List<Int> = emptyList(),
    val renglonesDelAvisoAmbar: Int = 0,
    val renglonesDelAvisoRojo: Int = 0,
    val prestamos: Int = 0,
) {
    internal fun esValida(): Boolean =
        gruposDelResumen.size <= 3 && gruposDelResumen.all { it in 1..MAX_FILAS_POR_GRUPO_DEL_RESUMEN } &&
            renglonesDelAvisoAmbar in 0..MAX_RENGLONES && renglonesDelAvisoRojo in 0..MAX_RENGLONES &&
            prestamos in 0..MAX_ELEMENTOS
}

/**
 * Lo que decide dónde arranca la lista de Categorías.
 *
 * @property renglonesDeLaTarjetaDeOrden cuántos renglones ocupó el texto de «Movi encontró N cosas
 *   para ordenar»; 0 si la tarjeta no estaba.
 * @property filas cuántas categorías se listaban con el filtro de siempre («Todas», sin búsqueda).
 */
@Serializable
data class FormaDeCategorias(
    val renglonesDeLaTarjetaDeOrden: Int = 0,
    val filas: Int = 0,
) {
    internal fun esValida(): Boolean =
        renglonesDeLaTarjetaDeOrden in 0..MAX_RENGLONES && filas in 0..MAX_ELEMENTOS
}

/**
 * Lo que decide el alto de Cuentas: la tarjeta del patrimonio y sus grupos.
 *
 * @property renglonesDelPatrimonio cuántos renglones de desglose tenía la tarjeta del patrimonio
 *   (tu plata, lo condicionado, los bienes, las deudas: de 1 a 4).
 * @property filasPorGrupo cuántas cuentas tenía cada grupo («Dinero», «Inversión»), en orden.
 */
@Serializable
data class FormaDeCuentas(
    val renglonesDelPatrimonio: Int = 1,
    val filasPorGrupo: List<Int> = emptyList(),
) {
    internal fun esValida(): Boolean =
        renglonesDelPatrimonio in 1..4 && filasPorGrupo.size <= 4 && filasPorGrupo.all { it in 0..MAX_ELEMENTOS }
}

/**
 * Lo único que decide la forma de Movimientos que puede saltar: si debajo del nombre del mes va
 * la línea del rango («Del 25 de agosto al 24 de septiembre…») o no —con corte 1 (mes de
 * calendario) nunca hay nada que aclarar—. Ola B, tarea 2 (whole-branch review, final fix wave).
 *
 * @property lineaDePeriodo si la última carga que salió bien mostraba esa línea.
 */
@Serializable
data class FormaDeMovimientos(
    val lineaDePeriodo: Boolean = true,
)

/**
 * Lo que decide el alto de «Tus períodos» por encima de su lista.
 *
 * @property filas cuántos períodos trajo `GET /api/periodos` la última vez que contestó bien.
 */
@Serializable
data class FormaDePeriodos(val filas: Int = 0) {
    internal fun esValida(): Boolean = filas in 0..MAX_ELEMENTOS
}

/** Topes de cordura: un número por encima de esto no lo escribió esta app, y se trata como corrupto. */
private const val MAX_RENGLONES = 12
private const val MAX_ELEMENTOS = 500
private const val MAX_FILAS_POR_GRUPO_DEL_RESUMEN = 2

/**
 * `ignoreUnknownKeys`: una forma escrita por una versión POSTERIOR trae campos que esta no conoce,
 * y eso no es motivo para perderla.
 */
private val json = Json { ignoreUnknownKeys = true }

/** Top-level y `by lazy`, por lo mismo que en `LastAccountStore` (ver su KDoc). */
private val formaSettings: Settings by lazy { Settings() }
