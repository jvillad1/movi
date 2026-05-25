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

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    // passwordHash is server-only — never sent to client
)
