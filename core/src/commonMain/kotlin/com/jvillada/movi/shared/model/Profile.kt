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
