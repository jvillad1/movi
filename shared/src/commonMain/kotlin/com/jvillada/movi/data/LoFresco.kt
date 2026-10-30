package com.jvillada.movi.data

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
}

/** Ver [LoFresco.olvidar]. */
fun olvidarLoFresco() = LoFresco.olvidar()
