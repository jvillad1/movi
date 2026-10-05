package com.jvillada.movi.server.ai

import com.anthropic.core.JsonValue
import com.anthropic.core.http.Headers
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.UnexpectedStatusCodeException

/**
 * Las fallas que lanza el SDK de Anthropic, armadas a mano: así se prueba qué hace Movi con cada una
 * **sin llamar a la API**. Los cuerpos son los que contesta la API de verdad.
 */
internal object FallasDeLaApiDePrueba {
    private val sinCabeceras: Headers = Headers.builder().build()

    private fun cuerpo(tipo: String, mensaje: String): JsonValue =
        JsonValue.from(mapOf("type" to "error", "error" to mapOf("type" to tipo, "message" to mensaje)))

    /** El 400 del 2026-10-04: un `invalid_request_error` que solo el mensaje distingue. */
    fun sinCredito(): BadRequestException = BadRequestException.builder()
        .headers(sinCabeceras)
        .body(cuerpo("invalid_request_error", "Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing to upgrade or purchase credits."))
        .build()

    /** Un 400 cualquiera: un pedido mal armado. Ese sí es un error de Movi. */
    fun pedidoMalArmado(): BadRequestException = BadRequestException.builder()
        .headers(sinCabeceras)
        .body(cuerpo("invalid_request_error", "messages: roles must alternate"))
        .build()

    fun facturacion(): UnexpectedStatusCodeException = UnexpectedStatusCodeException.builder()
        .statusCode(402).headers(sinCabeceras).body(cuerpo("billing_error", "Billing problem")).build()

    fun claveRechazada(): UnauthorizedException = UnauthorizedException.builder()
        .headers(sinCabeceras).body(cuerpo("authentication_error", "invalid x-api-key")).build()

    fun sinPermiso(): PermissionDeniedException = PermissionDeniedException.builder()
        .headers(sinCabeceras).body(cuerpo("permission_error", "not allowed")).build()

    fun limiteDeUso(): RateLimitException = RateLimitException.builder()
        .headers(sinCabeceras).body(cuerpo("rate_limit_error", "rate limited")).build()

    fun saturada(): InternalServerException = InternalServerException.builder()
        .statusCode(529).headers(sinCabeceras).body(cuerpo("overloaded_error", "Overloaded")).build()

    fun errorInterno(): InternalServerException = InternalServerException.builder()
        .statusCode(500).headers(sinCabeceras).body(cuerpo("api_error", "Internal error")).build()

    fun modeloQueNoExiste(): NotFoundException = NotFoundException.builder()
        .headers(sinCabeceras).body(cuerpo("not_found_error", "model: claude-x")).build()
}
