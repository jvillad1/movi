package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val name: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val name: String,
    val email: String,
)

/** Pedido de recuperación. La respuesta es idéntica exista o no el correo (anti-enumeración). */
@Serializable
data class PasswordResetRequest(
    val email: String,
)

/** Canje del token que llegó por correo por una contraseña nueva. */
@Serializable
data class PasswordResetConfirmRequest(
    val token: String,
    val newPassword: String,
)

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    // passwordHash is server-only — never sent to client
)
