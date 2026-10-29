package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.LocalSaldosOcultos
import com.jvillada.movi.ui.components.ROTULO_MOSTRAR_LOS_SALDOS
import com.jvillada.movi.ui.components.ROTULO_OCULTAR_LOS_SALDOS
import com.jvillada.movi.ui.components.TAG_OJO_DE_LOS_SALDOS
import com.jvillada.movi.ui.components.rememberVibrador
import androidx.compose.ui.draw.clip
import com.jvillada.movi.ui.components.ds.anilloDeFoco

/** El encabezado del Inicio, sobre la banda amarilla (vuelta 39). */
const val TAG_ENCABEZADO_DEL_INICIO: String = "inicio-encabezado"

/** El avatar del encabezado del Inicio, que abre «Tú». */
const val TAG_AVATAR_DEL_INICIO: String = "inicio-avatar"

/** El punto de la campana del Inicio (solo con algo vencido o urgente). */
const val TAG_PUNTO_DE_LA_CAMPANA: String = "inicio-punto-de-la-campana"

/** Lo que dice el lector de pantalla de la campana. */
const val ROTULO_DE_LA_CAMPANA: String = "Notificaciones"

/**
 * **El encabezado del Inicio de la opción 3** (vuelta 39): va sobre la banda amarilla, del alto de
 * cualquier encabezado ([Medida.altoDeEncabezado]).
 *
 * - El avatar blanco con las iniciales ([com.jvillada.movi.ui.components.inicialesDelUsuario]) abre «Tú».
 * - «Hola, Juan» en `saludo` y, debajo, el período (o «Actualizando…» mientras se recarga con cifras ya
 *   pintadas) en `apoyoMedio` y `sobreMarcaMedio`.
 * - Dos botones redondos en `marcaSuave`, como en la maqueta: **ocultar los saldos** (el ojo que antes
 *   iba junto al rótulo del héroe) y **la campana**, con su punto solo si hay algo vencido o urgente
 *   (vuelta 28, [campanaConPunto]). Buscar sigue en Actividad.
 *
 * El fondo amarillo lo pone quien lo monta (`DashboardScreen`), así arriba no queda una franja crema.
 */
@Composable
fun EncabezadoDelInicio(
    saludo: String,
    subtitulo: String?,
    iniciales: String,
    conPunto: Boolean,
    onAvatar: () -> Unit,
    onCampana: () -> Unit,
    modifier: Modifier = Modifier,
    descripcionDelSubtitulo: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Medida.altoDeEncabezado)
            .testTag(TAG_ENCABEZADO_DEL_INICIO)
            .padding(horizontal = Medida.margenDePantalla),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Box(
            modifier = Modifier
                .size(Medida.circuloDeLaBanda)
                // Vuelta 55 (revisión): con Tab, el avatar mostraba el cuadrado gris tenue del foco sobre
                // el amarillo, sin anillo. Ahora el anillo de tinta (entra en los 56 del encabezado: el
                // círculo mide 44 y el anillo sale 4) y el resaltado redondo, recortado al círculo.
                .anilloDeFoco(CircleShape)
                .clip(CircleShape)
                .background(Movi.colores.tarjeta, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Abrir Tú", onClick = onAvatar)
                .semantics { contentDescription = "Tú" }
                .testTag(TAG_AVATAR_DEL_INICIO),
            contentAlignment = Alignment.Center,
        ) {
            Text(iniciales, style = Movi.textos.cuerpoFuerte, color = Movi.colores.texto, maxLines = 1)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = saludo,
                style = Movi.textos.saludo,
                color = Movi.colores.sobreMarca,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitulo != null) {
                Text(
                    text = subtitulo,
                    style = Movi.textos.apoyoMedio,
                    color = Movi.colores.sobreMarcaMedio,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Vuelta 54: el rango entero del período, para el lector de pantalla.
                    modifier = if (descripcionDelSubtitulo != null) {
                        Modifier.semantics { contentDescription = descripcionDelSubtitulo }
                    } else Modifier,
                )
            }
        }
        OjoDeLaBanda()
        Box {
            BotonDeLaBanda(Icons.Rounded.Notifications, ROTULO_DE_LA_CAMPANA, onCampana)
            if (conPunto) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = Espacio.xs, end = Espacio.xs)
                        .size(Espacio.s)
                        .border(Medida.trazoDeInsignia, Movi.colores.marcaSuave, CircleShape)
                        .background(Movi.colores.sobreMarca, CircleShape)
                        .testTag(TAG_PUNTO_DE_LA_CAMPANA),
                )
            }
        }
    }
}

/** Un botón redondo del encabezado del Inicio: el ícono en tinta sobre un círculo `marcaSuave` de 44. */
@Composable
private fun BotonDeLaBanda(
    icono: ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(Medida.circuloDeLaBanda)
            .anilloDeFoco(CircleShape)
            .clip(CircleShape)
            .background(Movi.colores.marcaSuave, CircleShape)
            .clickable(role = Role.Button, onClickLabel = descripcion, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = descripcion, tint = Movi.colores.sobreMarca, modifier = Modifier.size(Icono.l))
    }
}

/**
 * **Ocultar los saldos**, en la banda: lo mismo que el ojo de siempre ([SaldosOcultosStore], la
 * vibración de alternar, el mismo rótulo y el mismo tag), con la forma de los botones del encabezado.
 */
@Composable
private fun OjoDeLaBanda() {
    val ocultos = LocalSaldosOcultos.current
    val vibrador = rememberVibrador()
    BotonDeLaBanda(
        icono = if (ocultos) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
        descripcion = if (ocultos) ROTULO_MOSTRAR_LOS_SALDOS else ROTULO_OCULTAR_LOS_SALDOS,
        onClick = {
            SaldosOcultosStore.alternar()
            vibrador.alternar(prendido = SaldosOcultosStore.ocultos)
        },
        modifier = Modifier.testTag(TAG_OJO_DE_LOS_SALDOS),
    )
}
