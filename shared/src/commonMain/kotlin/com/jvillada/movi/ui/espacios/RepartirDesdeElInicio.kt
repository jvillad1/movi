package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.theme.Espacio as EspacioDeDiseno
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.AvatarDePersona
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * # «Repartir» desde el Inicio (vuelta de diseño 7)
 *
 * Antes la acción «Repartir» de la cuadrícula solo cambiaba a la pestaña Espacios, y desde ahí había
 * que buscar cómo anotar el gasto. Ahora abre directo **la hoja «Pagó otro»** ([HojaPagoOtro], la de
 * «Anotar lo que pagó Caro»): es la única que anota un gasto compartido desde cero —repartir un gasto
 * tuyo se hace desde el detalle del movimiento, que ya existe—. Las mismas hojas y los mismos
 * endpoints: nada nuevo de plata.
 *
 * - Con **un** espacio, sobre ese: la pantalla del espacio con la hoja abierta encima (lo mismo que
 *   pide «Pagó otro» en Agregar, `Screen.Espacio(id, pagoOtro = true)`).
 * - Con **varios**, primero se elige el espacio ([HojaElegirEspacio]).
 * - **Sin ninguno**, la creación del espacio ([HojaNuevoEspacio]); creado, se abre su hoja.
 * - Si no se pudieron leer los espacios, la pestaña Espacios, como antes.
 */
sealed interface DestinoDeRepartir {
    /** Abrir la hoja «Pagó otro» sobre este espacio. */
    data class AnotarEn(val espacioId: String) : DestinoDeRepartir

    /** Elegir primero en cuál. */
    data class ElegirEspacio(val espacios: List<Espacio>) : DestinoDeRepartir

    /** No hay ninguno: crearlo. */
    data object CrearEspacio : DestinoDeRepartir
}

/** A dónde lleva «Repartir» con [espacios]. Pura. */
fun destinoDeRepartir(espacios: List<Espacio>): DestinoDeRepartir = when (espacios.size) {
    0 -> DestinoDeRepartir.CrearEspacio
    1 -> DestinoDeRepartir.AnotarEn(espacios.single().id)
    else -> DestinoDeRepartir.ElegirEspacio(espacios)
}

/** La pantalla del espacio [espacioId] con la hoja «Pagó otro» abierta. */
fun pantallaParaAnotarEn(espacioId: String): Screen = Screen.Espacio(espacioId, pagoOtro = true)

/** Tags para las pruebas. */
const val TAG_HOJA_ELEGIR_ESPACIO: String = "espacios:elegir-para-repartir"
const val TAG_FILA_ELEGIR_ESPACIO: String = "espacios:fila-elegir-para-repartir"

/** El título de la hoja que elige el espacio. */
const val TITULO_ELEGIR_ESPACIO: String = "¿En qué espacio?"

/**
 * Lo que «Repartir» tiene abierto: nada, la hoja de elegir el espacio o la de crearlo. Vive en el
 * Inicio, junto a la cuadrícula.
 */
@Stable
class EstadoDeRepartir internal constructor(
    private val alcance: CoroutineScope,
    private val onNavigate: (Screen) -> Unit,
) {
    internal var eligiendo by mutableStateOf<List<Espacio>?>(null)
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
            when (val destino = destinoDeRepartir(espacios)) {
                is DestinoDeRepartir.AnotarEn -> onNavigate(pantallaParaAnotarEn(destino.espacioId))
                is DestinoDeRepartir.ElegirEspacio -> eligiendo = destino.espacios
                DestinoDeRepartir.CrearEspacio -> creando = true
            }
        }
    }

    internal fun ir(pantalla: Screen) {
        eligiendo = null
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
    estado.eligiendo?.let { espacios ->
        HojaElegirEspacio(
            espacios = espacios,
            onDismiss = { estado.eligiendo = null },
            onElegido = { estado.ir(pantallaParaAnotarEn(it.id)) },
        )
    }
    if (estado.creando) {
        HojaNuevoEspacio(
            onDismiss = { estado.creando = false },
            onCreado = { estado.ir(pantallaParaAnotarEn(it.id)) },
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

/** **¿En qué espacio?**: una [Fila] por espacio; tocarla abre su hoja «Pagó otro». */
@Composable
internal fun HojaElegirEspacio(espacios: List<Espacio>, onDismiss: () -> Unit, onElegido: (Espacio) -> Unit) {
    MarcoDeHoja(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
                .testTag(TAG_HOJA_ELEGIR_ESPACIO),
        ) {
            Text(TITULO_ELEGIR_ESPACIO, style = Movi.textos.titulo, color = Movi.colores.texto)
            Spacer(Modifier.height(EspacioDeDiseno.xxs))
            Text(
                "Anota lo que pagó otra persona. Tu parte cuenta en tus gastos.",
                style = Movi.textos.cuerpo,
                color = Movi.colores.textoMedio,
            )
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Superficie(nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista) {
                espacios.forEachIndexed { i, espacio ->
                    if (i > 0) Separador()
                    val otro = espacio.miembros.firstOrNull { it.id != MIEMBRO_YO }?.nombre?.takeIf { it.isNotBlank() }
                    Fila(
                        titulo = espacio.nombre,
                        subtitulo = conQuienDe(espacio),
                        inicio = { AvatarDePersona(otro ?: espacio.nombre) },
                        final = { Chevron() },
                        onClick = { onElegido(espacio) },
                        modifier = Modifier.testTag(TAG_FILA_ELEGIR_ESPACIO),
                    )
                }
            }
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}
