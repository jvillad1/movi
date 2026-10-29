package com.jvillada.movi.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.Vacio
import com.jvillada.movi.ui.dashboard.NotificationRow
import com.jvillada.movi.ui.dashboard.TipoDeNotificacion

/** El tag de cada aviso de la campana: `TAG:<índice>`. */
const val TAG_AVISO_DE_LA_CAMPANA: String = "aviso-de-la-campana"

/** Lo que dice la campana sin nada pendiente. */
const val CAMPANA_SIN_AVISOS: String = "Nada pendiente"

/**
 * **El ícono y el tono de cada aviso** (vuelta de diseño 8). Pura: el color dice el estado —lo
 * vencido y lo que se pasó en peligro, lo que vence o espera confirmación en aviso— y nunca es la
 * única pista, porque el título lo dice en palabras.
 */
fun iconoDelAviso(tipo: TipoDeNotificacion): Pair<ImageVector, TonoDeIcono> = when (tipo) {
    TipoDeNotificacion.PagoVencido -> Icons.Rounded.ErrorOutline to TonoDeIcono.Peligro
    TipoDeNotificacion.PagoPorVencer -> Icons.Rounded.Schedule to TonoDeIcono.Aviso
    TipoDeNotificacion.BancoMudo -> Icons.Rounded.NotificationsOff to TonoDeIcono.Peligro
    TipoDeNotificacion.PresupuestoSuperado -> Icons.AutoMirrored.Rounded.TrendingUp to TonoDeIcono.Peligro
    TipoDeNotificacion.PagoDeTarjetaPorConfirmar -> Icons.Rounded.CreditCard to TonoDeIcono.Aviso
    TipoDeNotificacion.MensajesDelBanco -> Icons.Rounded.Inbox to TonoDeIcono.Aviso
    // El ícono de su tarjeta en «Movi propone hoy»: lo que el banco cobró solo.
    TipoDeNotificacion.DebitoPorConfirmar -> Icons.Rounded.AccountBalance to TonoDeIcono.Aviso
    TipoDeNotificacion.CapturaSinMensajes -> Icons.Rounded.Sms to TonoDeIcono.Neutro
    // Vuelta 66: el ícono de su tarjeta en el grupo y en Por revisar.
    TipoDeNotificacion.TransferenciaParaSaldar -> Icons.Rounded.SwapHoriz to TonoDeIcono.Aviso
}

/**
 * F5: la hoja que abre la campana del Inicio. Mismo patrón que las demás hojas de la app
 * (scrim + manija con X, ver [SheetHandleWithClose]) — nada de snackbar, que era la queja
 * original («se ve feo, sobre todo la posición»).
 *
 * Es una vista puramente derivada de [rows] (ver [com.jvillada.movi.ui.dashboard.notificationRows]):
 * no hay "marcar leído" ni estado propio. Cada fila cierra el panel y navega a donde eso se
 * resuelve de verdad.
 *
 * **Vuelta de diseño 8:** cada aviso es una [Fila] —su [IconoEnCirculo] y su tono según de qué habla
 * ([iconoDelAviso]), el título en frase («Internet venció ayer»), el monto con la `Cifra` del sistema
 * cuando lo hay y un subtítulo si aporta— en una superficie de lista, lo urgente primero. Antes eran
 * renglones de texto plano con un chevron. Sin nada, un [Vacio] con una frase corta.
 */
@Composable
fun NotificationsPanel(
    rows: List<NotificationRow>,
    onDismiss: () -> Unit,
    onRowClick: (Screen) -> Unit,
) {
    MarcoDeHoja(onDismiss = onDismiss) {
        // El contenido de la hoja se desplaza.
        //
        // Estas hojas nacieron sin `verticalScroll` y funcionaban de casualidad: con el teclado
        // abierto en un teléfono chico, o con la lista un poco más larga, el contenido se salía por
        // abajo y el botón de guardar quedaba fuera de la pantalla, recortado por el `clip` de la
        // propia hoja. Sin manera de llegar a él.
        //
        // `weight(1f, fill = false)` es lo que hace que la hoja **crezca con su contenido** hasta el
        // borde de la pantalla y recién ahí desplace, en vez de ocupar siempre todo el alto. Mismo
        // patrón que las hojas de `CategorySheets.kt`, que ya lo tenían.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false),
        ) {
            Text(
                text = "Notificaciones",
                style = Movi.textos.titulo,
                color = Movi.colores.texto,
                modifier = Modifier.padding(bottom = Medida.dentroDeSeccion),
            )

            if (rows.isEmpty()) {
                // Estado vacío: anclado acá, no un snackbar flotante — la queja original de F5.
                Vacio(
                    titulo = CAMPANA_SIN_AVISOS,
                    detalle = "Aquí te avisamos de pagos que vencen y lo que espera tu confirmación.",
                    icono = Icons.Rounded.Inbox,
                )
            } else {
                Superficie(nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista) {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) Separador()
                        AvisoDeLaCampana(
                            row = row,
                            modifier = Modifier.testTag("$TAG_AVISO_DE_LA_CAMPANA:$index"),
                            onClick = { onDismiss(); onRowClick(row.target) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}

/** Un aviso de la campana: ícono en su tono, la frase, el monto si lo hay y la flecha de «esto se abre». */
@Composable
private fun AvisoDeLaCampana(row: NotificationRow, modifier: Modifier, onClick: () -> Unit) {
    val (icono, tono) = iconoDelAviso(row.tipo)
    Fila(
        titulo = row.titulo,
        subtitulo = row.subtitulo,
        inicio = { IconoEnCirculo(icono, tono = tono) },
        final = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                row.monto?.let { Cifra(it, moneda = row.moneda) }
                Chevron()
            }
        },
        onClick = onClick,
        modifier = modifier,
    )
}
