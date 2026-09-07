package com.jvillada.movi.ui.sms

import androidx.compose.runtime.Composable

/**
 * Sección «Captura de SMS» de la pantalla Mensajes del banco — SOLO existe en Android,
 * que es donde la app puede leer SMS. Reúne lo que era la pantalla del APK sensor
 * (menos su login, redundante con el de la app): estado de los permisos RECEIVE/READ
 * con su ruta a ajustes (incluidos los ajustes restringidos de Android 15 para
 * instalaciones fuera de tienda), el aviso de hibernación con el botón para eximir a la
 * app, y la sincronización manual del historial de los últimos 30 días.
 *
 * En iOS y la web el actual no pinta nada: ahí los SMS "los lee tu teléfono", como ya
 * dice la tarjeta de arriba en [SMSInboxScreen].
 *
 * [onSynced] se invoca tras un backfill subido con éxito, para que la bandeja se refresque.
 */
@Composable
expect fun SmsSensorSetupSection(onSynced: () -> Unit)

/**
 * ¿Está concedido, AHORA y en este dispositivo, el permiso que la captura necesita? Solo
 * Android puede decir que sí (releído al volver de ajustes); iOS/web devuelven false siempre,
 * y ahí [SMSInboxScreen] ni lo consulta.
 *
 * **Un `true` no significa que la captura funcione**, y por eso la pantalla solo usa el `false`:
 * para nombrar lo que falta. El dueño estuvo semanas con el permiso concedido y sin que llegara
 * un solo mensaje —receiver muerto, app hibernada, token vencido: nada de eso se ve desde acá—
 * mientras la tarjeta le decía «AUTO-LECTURA ACTIVA». Lo único que prueba que la captura anda es
 * un mensaje que haya llegado, y eso lo dice `CapturaDeSms` (:core), no esta función.
 */
@Composable
expect fun rememberSmsCaptureReady(): Boolean
