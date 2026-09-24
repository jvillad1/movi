package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.ui.Screen
import kotlinx.datetime.Clock

/**
 * **El tablero entero, montado solo, para sus pruebas**: su lista, su aviso de error con
 * «Reintentar» y sus hojas, con las mismas piezas que monta Plan ([rememberTableroMontadoSolo],
 * [tableroDeRecurrentes], [HojasDelTableroDeRecurrentes]).
 *
 * Vivía en el código de la app, pero desde que Plan lo monta adentro de su propia lista nadie más
 * lo usaba. Lo que Plan agrega alrededor —el corte leído del perfil, los vencimientos leídos con
 * cualquier segmento, el esqueleto hasta que llegan— lo prueba `PlanScreenTest`; estas pruebas
 * miran lo que el tablero hace con sus filas, que es igual por los dos caminos.
 */
@Composable
fun TableroDeRecurrentesDePrueba(
    ajustesDelPeriodo: PeriodSettings,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val solo = rememberTableroMontadoSolo()
    val periodoDeHoy = remember(ajustesDelPeriodo) {
        periodoActual(Clock.System.now().toEpochMilliseconds(), ajustesDelPeriodo)
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp),
        ) {
            tableroDeRecurrentes(
                estado = solo.estado,
                periodoVisible = periodoDeHoy,
                periodoDeHoy = periodoDeHoy,
                ajustesDelPeriodo = ajustesDelPeriodo,
                accountNames = solo.accountNames,
                onNavigate = onNavigate,
            )
        }
        SnackbarHost(
            hostState = solo.aviso,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
        HojasDelTableroDeRecurrentes(solo.estado)
    }
}
