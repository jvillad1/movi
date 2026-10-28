package com.jvillada.movi.ui.mas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.data.PreferenciaDeTema
import com.jvillada.movi.data.TemaStore
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.primeruso.DireccionDeMovi
import com.jvillada.movi.ui.primeruso.ReenvioPorCorreo
import com.jvillada.movi.ui.profile.CARGANDO

/** El título de la fila del tema, en Ajustes → Cómo se ve (vuelta 31: eran tres filas). */
const val TITULO_DEL_TEMA: String = "Tema"

/** El tag de la hoja del tema. */
const val TAG_HOJA_DEL_TEMA: String = "ajustes:hoja-del-tema"

/** La fila de Ajustes donde no se leen SMS (la web, iOS): reenviar los correos del banco a Movi. */
const val RECIBE_TUS_ALERTAS_POR_CORREO: String = "Recibe tus alertas por correo"

/** El tag de la hoja de las alertas por correo. */
const val TAG_HOJA_DE_ALERTAS_POR_CORREO: String = "ajustes:hoja-de-alertas-por-correo"

/**
 * **Elegir el tema**, en una hoja (vuelta 31). Ajustes tenía las tres opciones como tres filas —«Igual
 * que el teléfono», «Claro», «Oscuro»— y la sección pesaba más que todo lo demás. Ahora es una fila
 * «Tema» con su valor, y esta hoja con las tres. Elegir una la guarda (`TemaStore`, de este aparato) y
 * cierra la hoja.
 */
@Composable
internal fun HojaDelTema(onDismiss: () -> Unit) {
    MarcoDeHoja(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false).testTag(TAG_HOJA_DEL_TEMA)) {
            EncabezadoDeHoja(TITULO_DEL_TEMA, subtitulo = "Solo en este aparato")
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Superficie(modifier = Modifier.fillMaxWidth(), nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista) {
                PreferenciaDeTema.entries.forEachIndexed { i, opcion ->
                    if (i > 0) Separador(SangriaDeSeparador.Margen)
                    val elegida = TemaStore.preferencia == opcion
                    Fila(
                        titulo = opcion.titulo,
                        subtitulo = if (opcion == PreferenciaDeTema.SISTEMA) "Claro u oscuro, según tu teléfono" else null,
                        final = {
                            Box(modifier = Modifier.size(Icono.l), contentAlignment = Alignment.Center) {
                                if (elegida) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Elegido", tint = Movi.colores.marca, modifier = Modifier.size(Icono.m))
                                }
                            }
                        },
                        onClick = {
                            TemaStore.poner(opcion)
                            onDismiss()
                        },
                        modifier = Modifier.semantics { selected = elegida },
                    )
                }
            }
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}

/**
 * **«Recibe tus alertas por correo»**, la fila (vuelta 31). Solo donde el aparato no lee SMS (la web,
 * iOS): ahí la captura del banco es reenviar sus correos a Movi. Dice la dirección para que se vea sin
 * abrir nada, y abre [HojaDeAlertasPorCorreo] con «Copiar» y el paso a paso.
 */
@Composable
internal fun FilaDeAlertasPorCorreo(direccion: DireccionDeMovi, onAbrir: () -> Unit) {
    val laDireccion = direccion.direccion
    Fila(
        titulo = RECIBE_TUS_ALERTAS_POR_CORREO,
        subtitulo = when {
            laDireccion != null -> laDireccion
            !direccion.leida -> CARGANDO
            else -> "Todavía no está disponible"
        },
        // La dirección es larga («alertas+3f2a…@…resend.app»): en dos renglones, no cortada.
        lineasDelSubtitulo = 2,
        inicio = { IconoEnCirculo(Icons.Rounded.Email, tono = TonoDeIcono.Neutro) },
        final = if (laDireccion != null) ({ Chevron() }) else null,
        onClick = if (laDireccion != null) onAbrir else null,
    )
}

/** La hoja de las alertas por correo: la misma pieza que la guía de primer uso ([ReenvioPorCorreo]). */
@Composable
internal fun HojaDeAlertasPorCorreo(direccion: DireccionDeMovi, onDismiss: () -> Unit) {
    MarcoDeHoja(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false).testTag(TAG_HOJA_DE_ALERTAS_POR_CORREO),
        ) {
            EncabezadoDeHoja(
                RECIBE_TUS_ALERTAS_POR_CORREO,
                subtitulo = "Si tu banco te manda un correo con cada compra, reenvíalo a Movi: te queda en " +
                    "«Por revisar» para que lo confirmes.",
            )
            Spacer(Modifier.height(Medida.entreSecciones))
            ReenvioPorCorreo(direccion)
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}
