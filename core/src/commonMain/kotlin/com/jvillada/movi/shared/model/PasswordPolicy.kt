package com.jvillada.movi.shared.model

/**
 * Política de contraseñas de movi — ÚNICA fuente de verdad.
 *
 * Vive en `:core` a propósito: `:server` la usa para la validación autoritativa (registro y
 * cambio de contraseña) y `:shared` la usa para la validación de UI. Antes el mínimo estaba
 * escrito a mano en dos lugares (`AuthRoutes.kt` y `RegisterScreen.kt`) más un comentario, y
 * cambiar uno no cambiaba el otro. Esa clase de invariante duplicada ya nos mordió varias
 * veces en este repo; acá queda estructural: hay un solo número y un solo mensaje.
 *
 * **No agregar reglas de composición.** NIST SP 800-63B §3.1.1.2 desaconseja explícitamente
 * exigir símbolos, dígitos o mayúsculas: en la práctica empujan a la gente hacia patrones
 * predecibles (`Password1!`) y BAJAN la fuerza real de las contraseñas. La palanca que sí
 * importa es la longitud. Por eso acá solo se mide longitud.
 */
object PasswordPolicy {

    /**
     * **12 caracteres.**
     *
     * NIST SP 800-63B fija 8 como piso absoluto para contraseñas elegidas por la persona;
     * 12 es el piso que recomiendan las guías modernas (CISA, OWASP ASVS L2) cuando lo que
     * se protege es material sensible. movi guarda las finanzas COMPLETAS de una persona
     * —cuentas, saldos, créditos, movimientos importados del banco— detrás de un registro
     * público, así que el objetivo correcto es el nivel "sensible", no el mínimo legal.
     *
     * Aritmética: con BCrypt cost 12 (~4 hashes/s/core en hardware de ataque razonable) una
     * contraseña de 12 caracteres aleatorios en minúsculas son ~56 bits, muy fuera de alcance
     * offline. Con 6 caracteres eran ~28 bits: crackeable en horas incluso con BCrypt.
     *
     * Subirlo más (14, 16) no es gratis: empuja a la gente a reusar contraseñas o anotarlas.
     * 12 es el punto donde el ataque offline deja de ser el eslabón débil.
     */
    const val MIN_LENGTH = 12

    /**
     * **72.** No es una restricción de política sino el límite real de BCrypt, que ignora
     * todo byte a partir del 72. Rechazar con un error explícito es preferible a aceptar 200
     * caracteres y autenticar en silencio solo con los primeros 72 — eso sería mentirle a la
     * persona sobre la fuerza de su contraseña.
     *
     * Cumple de sobra la recomendación de NIST de aceptar al menos 64. Nota: el límite de
     * BCrypt es en BYTES; acá se cuenta en caracteres, así que para contraseñas con acentos o
     * emoji el corte real llega un poco antes. Es conservador en la dirección correcta.
     */
    const val MAX_LENGTH = 72

    /** `null` si la contraseña cumple la política. */
    fun problemWith(password: String): PasswordProblem? = when {
        password.length < MIN_LENGTH -> PasswordProblem.TOO_SHORT
        password.length > MAX_LENGTH -> PasswordProblem.TOO_LONG
        else -> null
    }

    fun isValid(password: String): Boolean = problemWith(password) == null

    /**
     * Mensaje en español para mostrarle a la persona. Compartido para que la UI y el servidor
     * digan exactamente lo mismo y el número salga siempre de [MIN_LENGTH] / [MAX_LENGTH].
     */
    fun messageFor(problem: PasswordProblem): String = when (problem) {
        PasswordProblem.TOO_SHORT -> "La contraseña debe tener al menos $MIN_LENGTH caracteres"
        PasswordProblem.TOO_LONG  -> "La contraseña no puede superar los $MAX_LENGTH caracteres"
    }
}

enum class PasswordProblem { TOO_SHORT, TOO_LONG }
