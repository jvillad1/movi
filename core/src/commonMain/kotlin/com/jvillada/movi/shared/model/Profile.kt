package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * F42 · F46 — perfil editable. Antes `User` (ver `Auth.kt`) era lo único que existía y no había
 * ningún endpoint para leerlo ni cambiarlo fuera del login/registro; `GET /api/users/me` es la
 * primera vez que el cliente puede pedirlo de vuelta.
 *
 * `avatarColor` nunca es `null` en la respuesta del servidor: una cuenta sin color elegido
 * todavía cae a [AvatarPalette.DEFAULT] server-side (ver `UserRoutes.kt`) — el cliente no
 * necesita saber que la columna es nullable en la base.
 */
@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val name: String,
    val avatarColor: String,
    /**
     * Día en que arranca el período financiero del usuario (ver `PeriodSettings`).
     *
     * Nunca es `null` en la respuesta: una cuenta que no lo eligió cae a **1** —el mes de
     * calendario— del lado del servidor, así que el cliente no necesita saber que la columna es
     * nullable, igual que con `avatarColor`.
     */
    val periodCutoffDay: Int = 1,
    /**
     * **Los períodos que arrancaron otro día**: `"2026-09"` → la fecha ISO en que empezó de verdad.
     * Ver `PeriodSettings.iniciosPropios`, que es donde está el porqué completo.
     *
     * Vacío casi siempre, y vacío significa «todos salen del día de corte». Viaja en el perfil y no
     * en una ruta propia porque es parte de la misma pregunta que [periodCutoffDay] contesta —
     * cuándo empieza tu mes— y separarlos dejaría a un cliente capaz de leer una mitad sin la otra.
     */
    val periodStarts: Map<String, String> = emptyMap(),
    /**
     * Días de anticipación del aviso de vencimiento. Nunca `null` en la respuesta: cae al default
     * del lado del server, igual que [avatarColor] y [periodCutoffDay].
     */
    val reminderLeadDays: Int = DEFAULT_REMINDER_LEAD_DAYS,
    /**
     * El dueño silenció el aviso del Inicio sobre la captura de SMS (ver [CapturaDeSms]). Nunca
     * `null` en la respuesta: una cuenta que no lo tocó cae a `false` del lado del server.
     */
    val smsAlertMuted: Boolean = false,
)

/**
 * `PUT /api/users/me`. Ambos campos opcionales — mandar solo `name` no toca el color, y
 * viceversa. `name` recortado y no vacío, máx 100 caracteres; `avatarColor` tiene que ser uno
 * de [AvatarPalette.COLORS] — el servidor es la autoridad, esto es solo lo que se manda.
 */
@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val avatarColor: String? = null,
    /** Día de corte del período, 1..31. `null` = no tocar. */
    val periodCutoffDay: Int? = null,
    /**
     * Los períodos con arranque propio, **el mapa completo**: lo que venga reemplaza lo que había.
     * `null` = no tocar.
     *
     * Se manda entero y no de a una entrada porque quitar un arranque propio —«este mes sí empezó
     * cuando siempre»— es tan necesario como ponerlo, y con un formato incremental habría que
     * inventar cómo se dice «borrá esta clave». Un mapa vacío es exactamente eso: ninguno.
     */
    val periodStarts: Map<String, String>? = null,
    /** Días de aviso, 0..30. `null` = no tocar. */
    val reminderLeadDays: Int? = null,
    /**
     * Silenciar (o volver a mostrar) el aviso del Inicio sobre la captura de SMS. `null` = no
     * tocar.
     *
     * Se manda desde «Mensajes del banco», que es donde el hecho está a la vista, y no desde
     * Perfil: quien decide callar el recordatorio tiene que estar viendo lo que se calla.
     */
    val smsAlertMuted: Boolean? = null,
)

/**
 * `PUT /api/users/me/password`. [current] se verifica contra el hash guardado; [new] pasa por
 * [PasswordPolicy] en el servidor ANTES de hashearse — la validación del cliente es cortesía.
 */
@Serializable
data class ChangePasswordRequest(
    val current: String,
    val new: String,
)
