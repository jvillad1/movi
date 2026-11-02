package com.jvillada.movi.observabilidad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso

/** Cuánto vale una causa de cierre ([HojaDeUso.seCierra]) o un atrás del sistema: el cierre llega después de la salida animada. */
internal const val MS_PARA_LA_CAUSA_DEL_CIERRE = 1_500L

/**
 * # Las hojas en el uso (vuelta 104)
 *
 * Toda hoja de la app es un `MarcoDeHoja`, y el marco anota:
 *
 * - [EventoDeUso.HOJA_ABIERTA] un cuadro después de abrirse (así el título ya se compuso), con el id de
 *   la hoja ([ClaveDeUso.HOJA]): el de su `EncabezadoDeHoja` si su título es un rótulo de la app
 *   (`nueva-cuenta`), si no `hoja`. El toque que la abrió va aparte, como `toque:…`.
 * - [EventoDeUso.HOJA_CERRADA] al salir de la composición, con **cómo** ([ClaveDeUso.COMO]): `X`, `VELO`,
 *   `ARRASTRE` o `ESCAPE` cuando la cerró el marco; `ATRAS` si hubo un atrás del sistema justo antes
 *   ([Uso.atrasDelSistema]); y si no, `ADENTRO` (un «Guardar», un «Cancelar» de la hoja).
 */
class HojaDeUso internal constructor() {
    /** El id de la hoja: lo pone su `EncabezadoDeHoja` ([idDeUsoDe]); `null` = `hoja`. */
    var id: String? = null

    private var causa: String? = null
    private var causaEn: Long = 0L

    /** El marco la está cerrando por [como] (X, VELO, ARRASTRE, ESCAPE). */
    fun seCierra(como: String) {
        causa = como
        causaEn = Uso.reloj()
    }

    internal fun como(ahora: Long): String = when {
        causa != null && ahora - causaEn < MS_PARA_LA_CAUSA_DEL_CIERRE + 1_000L -> causa!!
        ahora - Uso.ultimoAtrasDelSistema < MS_PARA_LA_CAUSA_DEL_CIERRE -> "ATRAS"
        else -> "ADENTRO"
    }

    internal val idParaElUso: String get() = id ?: "hoja"
}

/** La hoja de la que forma parte lo que se compone (`null` fuera de una). */
val LocalHojaDeUso = staticCompositionLocalOf<HojaDeUso?> { null }

/** El registro de una hoja: anota que se abrió y, al irse, cómo se cerró. Lo llama `MarcoDeHoja`. */
@Composable
fun ObservarLaHoja(): HojaDeUso {
    val hoja = remember { HojaDeUso() }
    LaunchedEffect(hoja) {
        withFrameNanos { }
        Uso.evento(EventoDeUso.HOJA_ABIERTA, mapOf(ClaveDeUso.HOJA to hoja.idParaElUso))
    }
    DisposableEffect(hoja) {
        onDispose {
            Uso.evento(
                EventoDeUso.HOJA_CERRADA,
                mapOf(ClaveDeUso.HOJA to hoja.idParaElUso, ClaveDeUso.COMO to hoja.como(Uso.reloj())),
            )
        }
    }
    return hoja
}
