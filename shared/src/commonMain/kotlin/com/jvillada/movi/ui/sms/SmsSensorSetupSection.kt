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
