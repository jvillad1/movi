package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import kotlin.concurrent.Volatile

/**
 * # Olvidar lo fresco: lo que cambió por fuera de la app (vuelta 78, revisión)
 *
 * [LecturasSinRepetir] devuelve una lectura de hace menos de 30 s sin salir a la red, y una escritura
 * propia la vuelve vieja. Lo que entra **por fuera** no pasa por ahí: un aviso del banco que sube
 * `SmsSyncWorker`, el «Confirmar» de una notificación (`confirmarDesdeLaNotificacion`, con su propio
 * cliente), la subida del `SyncEngine` y el correo, que entra por el server. En la revisión el Inicio
 * decía «6 avisos» mientras Actividad decía «7».
 *
 * [olvidar] sube [vueltas] —la otra mitad de la generación con la que [LecturasCompartidas] decide si
 * algo sigue fresco— y marca la carga del Inicio como vieja ([DashboardDataCache.invalidar]). No borra lo
 * último que vio cada pantalla ([CacheDeLecturas]): eso se sigue pintando, con «Actualizando…», mientras
 * llega lo nuevo.
 *
 * Lo llaman:
 * - el gesto de tirar para actualizar, «Reintentar» y el ↻ de Por revisar: el dueño pidió lo de ahora;
 * - la vuelta del segundo plano (`AlVolverDelSegundoPlano`, en `App.kt`);
 * - en el teléfono, `SmsSyncWorker` tras una subida buena, `confirmarDesdeLaNotificacion` tras anotar y
 *   el `SyncEngine` cuando sube algo. **Viven en el mismo proceso que la pantalla** (WorkManager corre en
 *   el proceso de la app, y el `SyncEngine` es el del repositorio de la app), así que comparten este
 *   contador. Si el proceso no existía, la app arranca en frío y no hay nada fresco que olvidar.
 */
object LoFresco {
    /** Sube con cada [olvidar]. Ver [LecturasCompartidas]. */
    @Volatile
    var vueltas: Int = 0
        private set

    fun olvidar() {
        vueltas++
        DashboardDataCache.invalidar()
    }

    /**
     * **Cuántas veces se pidió recargar la pantalla a la vista** (vuelta 79): el ↻ del rail y la vuelta
     * del foco a la ventana en la web ([actualizarAMano]). Es estado de Compose: el Inicio, Actividad y
     * Pagos lo leen (`AlPedirseUnaRecarga`) y vuelven a preguntar. Solo sube.
     */
    var recargasPedidas: Int by mutableIntStateOf(0)
        private set

    /** Ver [recargasPedidas]. */
    fun pedirRecarga() {
        recargasPedidas++
    }
}

/** Ver [LoFresco.olvidar]. */
fun olvidarLoFresco() = LoFresco.olvidar()

/**
 * **Actualizar a mano** (vuelta 79): olvida lo fresco y pide que la pantalla a la vista recargue. Lo
 * llaman el ↻ del rail ancho y, en la web, la vuelta del foco a la ventana. En el teléfono está tirar
 * para actualizar.
 */
fun actualizarAMano() {
    LoFresco.olvidar()
    LoFresco.pedirRecarga()
}

/**
 * Cada cuánto, como mucho, la vuelta del foco recarga (vuelta 79): `focus` y `visibilitychange` llegan
 * juntos al volver a la pestaña, y entrar y salir de la ventana con el mouse no tiene que ser una carga
 * por vez. El ↻ no espera.
 */
const val ESPERA_ENTRE_RECARGAS_POR_FOCO_MS: Long = 5_000L

/** ¿La vuelta del foco recarga? Sí, salvo que la anterior haya sido hace menos de [ESPERA_ENTRE_RECARGAS_POR_FOCO_MS]. Pura. */
fun debeRecargarAlVolverElFoco(ultima: Long?, ahora: Long): Boolean =
    ultima == null || ahora - ultima >= ESPERA_ENTRE_RECARGAS_POR_FOCO_MS
