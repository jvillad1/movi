package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/*
 * # «¿Salda Casa con Andrés?» antes de confirmar el aviso (vuelta 85)
 *
 * La revisión 9 lo vio con el ingreso de Andrés: Movi lo reconocía y proponía «¿salda Casa con
 * Andrés?», pero **solo después** de confirmarlo, en otra tarjeta. Al confirmar, el dueño no sabía que
 * esa plata era la que Andrés le debía. Esto hace la misma pregunta con el aviso todavía pendiente, para
 * que la tarjeta lo diga.
 *
 * **No es una regla nueva**: es [transferenciasParaSaldar] —la misma que propone vincular un movimiento
 * ya anotado— aplicada a un movimiento que todavía no existe, armado con lo que Movi leyó del aviso. Y
 * **nada se salda solo**: la tarjeta lo dice; saldar sigue siendo «Sí, saldar» sobre el movimiento ya
 * confirmado, como siempre.
 */

/**
 * El grupo que el aviso saldaría, si se confirma.
 *
 * @property quien la otra persona («Andrés Rojas»).
 * @property debe lo que se deben hoy entre los dos en ese grupo (el reembolso de [saldoDelEspacio]).
 * @property entra `true` si la otra persona le paga al dueño (un ingreso); `false` si el dueño le paga.
 */
@Serializable
data class GrupoQueSaldaElAviso(
    val espacioId: String,
    val nombreDelEspacio: String,
    val quien: String,
    val debe: Long,
    val entra: Boolean = true,
)

/** Un grupo con su saldo de hoy: lo que [grupoQueSaldaElAviso] necesita de cada uno. */
data class GrupoConSuSaldo(val espacio: Espacio, val saldo: SaldoDelEspacio)

/**
 * **¿Este aviso saldaría un grupo?** El primero de [grupos] en el que [transferenciasParaSaldar]
 * propondría el movimiento que el aviso va a crear (mismo tipo, monto, texto del banco y momento), o
 * `null`. Solo en pesos, como la regla de siempre.
 */
fun grupoQueSaldaElAviso(
    leido: ParsedSms,
    textoDelAviso: String,
    momento: Long,
    grupos: List<GrupoConSuSaldo>,
    destinos: List<DestinoConocido>,
): GrupoQueSaldaElAviso? {
    if (grupos.isEmpty() || destinos.isEmpty()) return null
    if (leido.type != TransactionType.INCOME && leido.type != TransactionType.EXPENSE) return null
    val comoMovimiento = FinancialEvent(
        id = "aviso-por-confirmar",
        accountId = "",
        type = leido.type,
        amount = leido.amount.roundToLong(),
        currency = leido.currency,
        category = leido.category,
        description = leido.merchant,
        merchant = leido.merchant,
        timestamp = momento,
        rawPayload = textoDelAviso,
    )
    for (grupo in grupos) {
        val propuesta = transferenciasParaSaldar(grupo.saldo, listOf(comoMovimiento), destinos, ahora = momento)
            .firstOrNull() ?: continue
        val entra = propuesta.a == MIEMBRO_YO
        val otro = if (entra) propuesta.de else propuesta.a
        val debe = grupo.saldo.reembolsos.firstOrNull { it.de == propuesta.de && it.a == propuesta.a }?.monto ?: continue
        return GrupoQueSaldaElAviso(
            espacioId = grupo.espacio.id,
            nombreDelEspacio = grupo.espacio.nombre,
            quien = grupo.espacio.miembros.firstOrNull { it.id == otro }?.nombre?.takeIf { it.isNotBlank() }
                ?: destinos.firstOrNull { it.id == otro }?.nombre.orEmpty(),
            debe = debe,
            entra = entra,
        )
    }
    return null
}
