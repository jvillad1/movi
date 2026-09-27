package com.jvillada.movi.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.ui.LocalRefreshTick
import kotlinx.datetime.Clock

/**
 * **Una lectura que arranca con lo último que se vio** y se actualiza sola.
 *
 * Las reglas de siempre siguen valiendo, y este objeto es el que las hace fáciles de cumplir:
 *
 * - [valor] `null` = **no llegó** (ni de esta visita ni recordado): la pantalla pinta su esqueleto
 *   o, si [fallo], su error de siempre. Una lista vacía es «llegó vacía», no «no llegó».
 * - [actualizando] con [valor] a la vista = se está pintando algo que esta visita todavía no
 *   confirmó: la pantalla lo dice con «Actualizando…», el mismo texto del Inicio.
 * - [fallo] con [valor] a la vista = lo que se ve es **lo último que vimos**, no lo actual, y la
 *   pantalla tiene que decirlo con un «Reintentar» (ver `NoSePudoActualizar`). Nunca se hace
 *   pasar por actual.
 *
 * Lo crea [rememberLectura]; las pruebas lo manejan directo con [alEmpezar], [alContestar] y
 * [alFallar].
 */
@Stable
class Lectura<T : Any> internal constructor(recordado: T?) {

    /** Lo último que se sabe: de esta visita, o recordado de una anterior. `null` = no llegó. */
    var valor: T? by mutableStateOf(recordado)
        private set

    /**
     * Hay una lectura en vuelo. Arranca prendido: la primera lectura sale en el mismo cuadro en que
     * la pantalla aparece, y hasta que conteste no se puede afirmar ni vacío ni error.
     */
    var actualizando: Boolean by mutableStateOf(true)
        private set

    /**
     * Por qué falló la última lectura, o `null` si no falló (o todavía no terminó). Es una
     * instancia nueva por cada falla, así que sirve de llave para un `LaunchedEffect` que avise.
     */
    var error: Throwable? by mutableStateOf(null)
        private set

    /** Terminó al menos una lectura en esta visita, bien o mal. */
    var terminada: Boolean by mutableStateOf(false)
        private set

    /** La lectura más reciente de esta visita contestó bien. */
    private var contesto: Boolean by mutableStateOf(false)

    val fallo: Boolean get() = error != null

    /**
     * Lo que se ve no lo confirmó la lectura más reciente: todavía viaja, o falló. Con esto
     * prendido la pantalla no puede presentar [valor] como actual.
     */
    val mostrandoLoUltimo: Boolean get() = valor != null && !contesto

    /**
     * Falló con algo a la vista: lo que se ve es lo último que vimos y la pantalla tiene que
     * decirlo (ver `NoSePudoActualizar`). Sin nada a la vista, el error de siempre.
     */
    val falloConAlgoALaVista: Boolean get() = fallo && valor != null

    fun alEmpezar() {
        actualizando = true
        contesto = false
        error = null
    }

    /**
     * **Solo se reemplaza si llegó algo distinto.** Si la lectura nueva es igual —la igualdad de
     * las `data class` del modelo, campo por campo— se queda la instancia que ya estaba, así que
     * nada de lo que depende de ella se vuelve a calcular ni la lista se recompone: volver a una
     * pantalla sin novedades no mueve un píxel.
     */
    fun alContestar(nuevo: T) {
        if (nuevo != valor) valor = nuevo
        contesto = true
        error = null
        actualizando = false
        terminada = true
    }

    /** Falló: [valor] se queda —es lo último que vimos— y la pantalla decide cómo decirlo. */
    fun alFallar(causa: Throwable) {
        error = causa
        actualizando = false
        terminada = true
    }

    /**
     * Lo que devolvió una escritura propia desde esta pantalla (guardar el arranque de un período,
     * renombrar algo): es más nuevo que cualquier lectura, así que se muestra ya. No entra al
     * cache: la escritura acaba de vaciarlo, y lo llena la próxima lectura.
     */
    fun anotar(nuevo: T) {
        if (nuevo != valor) valor = nuevo
        contesto = true
        error = null
    }
}

/**
 * **La lectura de [clave] para esta pantalla**, empezando por lo último que se vio.
 *
 * Al primer cuadro [Lectura.valor] ya trae lo que [CacheDeLecturas] recuerde para esta sesión (o
 * `null`); en el mismo cuadro sale la lectura de verdad con [leer]. Si contesta, se muestra —solo
 * si trae algo distinto, ver [Lectura.alContestar]— y se recuerda para la próxima visita. Si
 * falla, lo que había se queda y la pantalla se entera por [Lectura.fallo].
 *
 * Vuelve a leer cuando cambia [reintento] (el «Reintentar» de la pantalla, o que algo se guardó
 * desde ella) y cuando cambia `LocalRefreshTick` (se guardó algo desde la hoja de Agregar).
 *
 * [periodoVigente] es para las claves que [dependen del período][ClaveDeLectura.dependeDelPeriodo]:
 * lo recordado de otro período no se muestra, y lo que se lee se guarda con él. **Mientras sea
 * `null` —el período todavía no se sabe— esas claves no muestran nada ni leen nada**: la
 * [Lectura] se queda sin valor y [Lectura.actualizando], o sea el esqueleto. Mostrar algo sin saber
 * de qué período es sería arriesgarse a pintar el mes pasado como si fuera este. Cuando el período
 * llega (el perfil leído o recordado) la lectura empieza, y si después cambia empieza de nuevo: lo
 * que había era de otro período. Para las demás claves [periodoVigente] no se usa.
 *
 * Si mientras la lectura viajaba hubo una escritura (ver [CacheDeLecturas.generacion]), lo que trae
 * pudo haberse leído antes de ella: no se muestra como confirmado —pisaría, por ejemplo, lo que
 * [Lectura.anotar] acaba de poner— y se lee otra vez. Tras [REINTENTOS_POR_ESCRITURAS] vueltas
 * así se muestra lo último que trajo, sin guardarlo.
 *
 * [activa] en `false` no lee (la pantalla está compuesta pero no se muestra, como Presupuestos
 * con «Pagos del mes» elegido en Plan); lee apenas pasa a `true`.
 *
 * La lectura usa [intentar] y no `runCatching`: una navegación que cancela la lectura no es una
 * falla, y no puede dejar un aviso rojo que nadie tuvo.
 */
@Composable
fun <T : Any> rememberLectura(
    clave: ClaveDeLectura<T>,
    reintento: Int,
    periodoVigente: String? = null,
    activa: Boolean = true,
    leer: suspend () -> T,
): Lectura<T> {
    val lectura = remember(clave, periodoVigente) {
        Lectura(CacheDeLecturas.ultima(clave, ahoraEnMs(), periodoVigente))
    }
    val leerAhora by rememberUpdatedState(leer)
    val tick = LocalRefreshTick.current
    LaunchedEffect(lectura, reintento, tick, activa) {
        if (!activa) return@LaunchedEffect
        // Sin período no se sabe qué mostrar: ni se lee (ver el KDoc).
        if (clave.dependeDelPeriodo && periodoVigente == null) return@LaunchedEffect
        lectura.alEmpezar()
        var vueltas = 0
        while (true) {
            // Se anotan AL SALIR: si mientras viaja se cierra la sesión o se escribe algo, lo que
            // vuelva no se recuerda (ver [CacheDeLecturas.guardar]).
            val usuario = SessionManager.userId
            val generacion = CacheDeLecturas.generacion
            val resultado = intentar { leerAhora() }
            val cruzoUnaEscritura = CacheDeLecturas.generacion != generacion
            if (resultado.isSuccess && cruzoUnaEscritura && ++vueltas < REINTENTOS_POR_ESCRITURAS) continue
            resultado
                .onSuccess { nuevo ->
                    lectura.alContestar(nuevo)
                    // Lo que quedó a la vista (la instancia de antes, si era igual): una sola copia
                    // de la historia en memoria, no dos.
                    CacheDeLecturas.guardar(clave, lectura.valor ?: nuevo, usuario, ahoraEnMs(), periodoVigente, generacion)
                }
                .onFailure { lectura.alFallar(it) }
            break
        }
    }
    return lectura
}

/** Cuántas veces se vuelve a leer una lectura que se cruzó con escrituras antes de rendirse. */
internal const val REINTENTOS_POR_ESCRITURAS: Int = 3

internal fun ahoraEnMs(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * **El período vigente según el perfil a la vista**, como id («2026-09»), para pasárselo a
 * [rememberLectura] en las claves que dependen del período.
 *
 * `null` mientras el perfil no se sabe: ni leído, ni recordado, ni falló. Si la lectura del perfil
 * falló sin nada recordado se usa el mes de calendario, que es lo que esas pantallas ya hacían sin
 * perfil; lo que se guarde así lleva ese id, y si el período de verdad es otro no se va a mostrar.
 */
fun periodoVigenteSegun(perfil: Lectura<UserProfile>, ahora: Long = ahoraEnMs()): String? {
    val ajustes = perfil.valor?.ajustesDelPeriodo()
        ?: if (perfil.terminada) PeriodSettings() else return null
    return periodoActual(ahora, ajustes).prefijo
}
