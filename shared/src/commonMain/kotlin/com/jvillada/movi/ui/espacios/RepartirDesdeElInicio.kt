package com.jvillada.movi.ui.espacios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * # «Repartir» desde el Inicio (vuelta de diseño 7, cambiado en la 44)
 *
 * Vuelta 7: la acción «Repartir» abría **la hoja «Pagó otro»** del espacio («¿Cuánto pagó Caro?»), que
 * supone que pagó el otro, con el selector de categoría viejo. La revisión de «un día del dueño» (vuelta
 * 44): casi siempre el que anota es el que pagó. Ahora abre **Agregar en modo personas** —«Repartir este
 * gasto · ¿Quién pagó? Yo | Caro»—, el flujo bueno que ya existía desde el ícono de personas de Agregar
 * ([HojaRepartirEsteGasto]), con el monto en blanco y «Yo» marcado. Si pagó Caro, se elige ahí mismo.
 *
 * - Con **algún** espacio, Agregar en ese modo ([pantallaParaRepartir]); con varios, la hoja pregunta
 *   «¿En qué grupo?».
 * - **Sin ninguno**, la creación del espacio ([HojaNuevoEspacio]); creado, Agregar en ese modo.
 * - Si no se pudieron leer los espacios, la pantalla de los grupos, como antes.
 */
sealed interface DestinoDeRepartir {
    /** Abrir Agregar en modo personas. */
    data object Agregar : DestinoDeRepartir

    /** No hay ninguno: crearlo. */
    data object CrearEspacio : DestinoDeRepartir
}

/** A dónde lleva «Repartir» con [espacios]. Pura. */
fun destinoDeRepartir(espacios: List<Espacio>): DestinoDeRepartir =
    if (espacios.isEmpty()) DestinoDeRepartir.CrearEspacio else DestinoDeRepartir.Agregar

/** Agregar abierto en «Repartir este gasto», en la pestaña Gasto (vuelta 44). */
fun pantallaParaRepartir(): Screen = Screen.QuickAdd(presetPestana = PestanaDeAgregar.GASTO, presetRepartir = true)

/** La pantalla del espacio [espacioId] con la hoja «Pagó otro» abierta (lo que abría «Repartir» hasta la vuelta 43). */
fun pantallaParaAnotarEn(espacioId: String): Screen = Screen.Espacio(espacioId, pagoOtro = true)

/** El título de la hoja que elige el espacio. */
const val TITULO_ELEGIR_ESPACIO: String = "¿En qué grupo?"

/**
 * Lo que «Repartir» tiene abierto: nada o la hoja de crear el espacio. Vive en la cáscara (`App.kt`),
 * junto a los atajos del «+».
 */
@Stable
class EstadoDeRepartir internal constructor(
    private val alcance: CoroutineScope,
    private val onNavigate: (Screen) -> Unit,
) {
    internal var creando by mutableStateOf(false)
    private var leyendo = false

    /** Tocar «Repartir»: lee los espacios y abre lo que corresponda. */
    fun tocar() {
        if (leyendo) return
        leyendo = true
        alcance.launch {
            val espacios = try {
                Repositories.wallets.getEspacios()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            } finally {
                leyendo = false
            }
            if (espacios == null) {
                onNavigate(Screen.Espacios)
                return@launch
            }
            when (destinoDeRepartir(espacios)) {
                DestinoDeRepartir.Agregar -> onNavigate(pantallaParaRepartir())
                DestinoDeRepartir.CrearEspacio -> creando = true
            }
        }
    }

    internal fun ir(pantalla: Screen) {
        creando = false
        onNavigate(pantalla)
    }
}

@Composable
fun rememberEstadoDeRepartir(onNavigate: (Screen) -> Unit): EstadoDeRepartir {
    val alcance = rememberCoroutineScope()
    return remember(alcance) { EstadoDeRepartir(alcance, onNavigate) }
}

/** Las hojas que puede abrir «Repartir»: las pinta quien tiene el [estado]. */
@Composable
fun HojasDeRepartir(estado: EstadoDeRepartir) {
    if (estado.creando) {
        HojaNuevoEspacio(
            onDismiss = { estado.creando = false },
            onCreado = { estado.ir(pantallaParaRepartir()) },
            onIrATerceros = { estado.ir(Screen.Destinos()) },
        )
    }
}

/**
 * Con quién se comparte [espacio], para el subtítulo: «Con Caro», «Con Caro y Ana». `null` si el
 * nombre del espacio ya los dice todos («Casa con Caro»): el subtítulo no repite el título.
 */
internal fun conQuienDe(espacio: Espacio): String? {
    val otros = espacio.miembros.filter { it.id != MIEMBRO_YO }.map { it.nombre }.filter { it.isNotBlank() }
    if (otros.isEmpty() || otros.all { espacio.nombre.contains(it, ignoreCase = true) }) return null
    val lista = if (otros.size == 1) otros.single() else otros.dropLast(1).joinToString(", ") + " y " + otros.last()
    return "Con $lista"
}
