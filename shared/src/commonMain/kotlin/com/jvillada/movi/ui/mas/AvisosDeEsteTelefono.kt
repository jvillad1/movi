package com.jvillada.movi.ui.mas

import androidx.compose.runtime.Composable

/**
 * # Los avisos de este teléfono, en Tú → Avisos (vuelta 73)
 *
 * Los avisos vivían en dos lugares: Tú → Avisos (las notificaciones del navegador, cuántos días antes
 * avisar y el resumen por correo) y Captura del banco → «Avisos de Movi» (el permiso de notificaciones
 * de Android y los interruptores de lo que suena en el teléfono). La revisión `r4` lo encontró: para
 * apagar el resumen de la mañana había que saber que vivía en la pantalla de los SMS.
 *
 * Ahora **Tú → Avisos es la casa de todos los interruptores**. Esto pinta, dentro de esa misma lista,
 * las filas de lo que suena en el teléfono Android —el permiso, movimientos, pagos por vencer, banco
 * mudo, débitos automáticos, cierre del período, resumen de la mañana y resumen semanal de los
 * grupos—, cada una guardada **donde se guardaba** (`PreferenciasDeAvisos`, en el teléfono): no se
 * migró ninguna preferencia. En la web y en iOS esos avisos no existen (los hace WorkManager), así que
 * no se pinta nada: un interruptor que no apaga nada sería mentir.
 *
 * Empieza con un `Separador`: va después de las filas de siempre de la sección.
 */
@Composable
expect fun AvisosDeEsteTelefono()
