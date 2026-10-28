package com.jvillada.movi.shared.model

/**
 * # El ahorro del período: lo que el dueño elige apartar cada período antes de gastar
 *
 * Decisión del dueño (27-oct-2026): *«Descontar un ahorro que elijo: en Ajustes eliges cuánto apartar
 * cada período (un monto o un %). "Te quedan" es lo libre después de fijos y ese ahorro. Una sola
 * cifra para Hoy, Plan y Movi AI.»* El héroe decía «Te quedan $10,9M · $2,2M por día» porque contaba
 * toda la plata como gastable.
 *
 * **No mueve plata.** Tu plata, el patrimonio y los saldos no cambian: solo cambia cuánto Movi dice
 * que se puede gastar. Por eso no es un [Apartado] (que sí sale de Tu plata) sino un tramo más de
 * [PlataDelDisponible] ([PlataDelDisponible.ahorroDelPeriodo]), que [DisponibleDelPeriodo] resta.
 *
 * Se guarda por usuario (`users.ahorro_del_periodo_tipo` / `_valor`) y viaja en [UserProfile] como
 * los demás ajustes del período. El server convierte el ajuste en pesos con [montoDelAhorro] y manda
 * la cifra en `DashboardSummary.ahorroDelPeriodo`: el cliente, el widget y Movi AI restan la misma.
 */
enum class TipoDeAhorro {
    /** No aparta nada: el disponible es el de siempre. El default de toda cuenta. */
    NINGUNO,

    /** Un monto fijo en pesos cada período. */
    MONTO,

    /** Un porcentaje entero (0 a [MAX_PORCENTAJE_DE_AHORRO]) de lo que entró en el período. */
    PORCENTAJE,
}

/** El tope del porcentaje: apartar más del 90 % de lo que entra no deja nada que organizar. */
const val MAX_PORCENTAJE_DE_AHORRO: Long = 90L

/**
 * El tope del monto: un billón de pesos por período, lo que cabe en el campo de monto de la app (12
 * dígitos). Solo para que una cifra imposible no se guarde.
 */
const val MAX_MONTO_DE_AHORRO: Long = 1_000_000_000_000L

/**
 * El tipo leído de su nombre guardado. Un nombre que no se conozca (o `null`, una cuenta que nunca lo
 * eligió) se lee como [TipoDeAhorro.NINGUNO]: nadie ve un cambio sin elegirlo.
 */
fun tipoDeAhorroDe(nombre: String?): TipoDeAhorro =
    TipoDeAhorro.entries.firstOrNull { it.name == nombre } ?: TipoDeAhorro.NINGUNO

/**
 * El ajuste de un usuario: qué tipo y cuánto. [valor] son pesos con [TipoDeAhorro.MONTO] y un
 * porcentaje entero con [TipoDeAhorro.PORCENTAJE]; con [TipoDeAhorro.NINGUNO] no se lee.
 */
data class AhorroDelPeriodo(
    val tipo: TipoDeAhorro = TipoDeAhorro.NINGUNO,
    val valor: Long = 0L,
) {
    /** ¿Aparta algo? Un monto o un porcentaje en cero es lo mismo que nada. */
    val apartaAlgo: Boolean get() = tipo != TipoDeAhorro.NINGUNO && valor > 0L
}

/** El ajuste del perfil (ver [UserProfile.ahorroDelPeriodoTipo]). */
fun UserProfile.ahorroDelPeriodo(): AhorroDelPeriodo =
    AhorroDelPeriodo(tipoDeAhorroDe(ahorroDelPeriodoTipo), ahorroDelPeriodoValor)

/**
 * **Cuántos pesos aparta [ahorro] en un período en el que entraron [entro]** — la única cuenta del
 * ahorro; el server la usa para el Inicio y para Movi AI.
 *
 * - [TipoDeAhorro.MONTO]: el monto, entre lo que haya entrado o no.
 * - [TipoDeAhorro.PORCENTAJE]: **sobre lo que entró en el período**, con la regla de «Entró» (los
 *   ingresos del flujo de caja: sin traspasos, sin «Por confirmar», sin anulados, en pesos) — no
 *   sobre el saldo ni sobre lo que tenías al empezar. «Ahorro el 20 %» es el 20 % de lo que me
 *   pagaron. Si todavía no entró nada, da 0: el sueldo que falta no se ahorra antes de llegar, y en
 *   cuanto llega el ahorro sube con él. Se redondea hacia abajo al peso.
 *
 * Nunca negativo, y el porcentaje nunca pasa de [MAX_PORCENTAJE_DE_AHORRO].
 */
fun montoDelAhorro(ahorro: AhorroDelPeriodo, entro: Long): Long = when (ahorro.tipo) {
    TipoDeAhorro.NINGUNO -> 0L
    TipoDeAhorro.MONTO -> ahorro.valor.coerceIn(0L, MAX_MONTO_DE_AHORRO)
    TipoDeAhorro.PORCENTAJE -> entro.coerceAtLeast(0L) * ahorro.valor.coerceIn(0L, MAX_PORCENTAJE_DE_AHORRO) / 100L
}

/**
 * ¿Es válido guardar este ajuste? `null` si sí; si no, el porqué en una frase para el dueño. La
 * comparten el server (que es la autoridad) y la hoja de Ajustes (que apaga «Guardar» y lo dice).
 */
fun problemaDelAhorro(tipo: TipoDeAhorro, valor: Long?): String? = when (tipo) {
    TipoDeAhorro.NINGUNO -> null
    TipoDeAhorro.MONTO -> when {
        valor == null || valor <= 0L -> "Escribe cuánto quieres apartar"
        valor > MAX_MONTO_DE_AHORRO -> "Ese monto es demasiado grande"
        else -> null
    }
    TipoDeAhorro.PORCENTAJE -> when {
        valor == null || valor <= 0L -> "Escribe qué porcentaje quieres apartar"
        valor > MAX_PORCENTAJE_DE_AHORRO -> "El porcentaje va de 1 a $MAX_PORCENTAJE_DE_AHORRO"
        else -> null
    }
}
