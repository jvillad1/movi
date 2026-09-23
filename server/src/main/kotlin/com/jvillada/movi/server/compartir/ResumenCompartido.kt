package com.jvillada.movi.server.compartir

import com.jvillada.movi.server.ai.contextoDelPeriodoDe
import com.jvillada.movi.server.balance.accountCopValue
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.netWorth
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.esCuentaDeDeuda
import com.jvillada.movi.shared.model.esDeTuPlata
import com.jvillada.movi.shared.model.normalizarCondicion
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll

/**
 * # Lo que ve un tercero con el alcance «resumen»
 *
 * Las cifras que el dueño ve en su Inicio, ni una más: la plata disponible, lo condicionado, las
 * deudas, el patrimonio, cómo va el período y en qué se fue. **Nada de movimientos uno por uno**:
 * un resumen para conversar con Caro o con un asesor no necesita saber en qué restaurante comió el
 * martes, y lo que no se muestra no se puede filtrar.
 *
 * ## Mismas reglas que el resto de Movi, no una tercera versión
 *
 * La página de un tercero que diga otra cifra que la app del dueño es peor que no tener página:
 * la conversación termina en «¿y por qué a mí me sale distinto?». Por eso nada se recalcula acá:
 *
 * - **Plata disponible** es [esDeTuPlata] de `:core` —el mismo predicado del hero del Inicio y de la
 *   tarjeta «Disponible»—, sumado con [accountCopValue] (dólares a la TRM, igual que el cliente).
 * - **Patrimonio** es [netWorth], la regla del server que ya usa `finance-summary`.
 * - **El período** sale de [contextoDelPeriodoDe] —lo que ya lee Movi AI—, que a su vez aplica los
 *   mismos filtros que el Inicio: anulados afuera, «Por confirmar» afuera, pago de tarjeta y
 *   movimientos de deuda fuera del flujo, el período del usuario y no el mes de calendario.
 */
internal data class ResumenCompartido(
    /** El nombre del dueño, tal como lo escribió en su perfil. */
    val quien: String,
    /** Cuándo se armó esta foto: la página la dice («Datos al …»). Epoch ms. */
    val generadoEn: Long,
    /** Hasta cuándo vale el enlace. Epoch ms. */
    val venceEn: Long,
    val tuPlata: Long,
    val condicionado: Long,
    /** Para qué es lo condicionado, si hay una sola condición; `null` con varias o ninguna. */
    val condicionadoA: String?,
    /**
     * **Punto de extensión: los bienes** (la casa, el vehículo).
     *
     * Hoy Movi no los modela, y por eso el patrimonio del dueño da −$2.074M: cuenta $2.191M de deudas
     * —las hipotecas de una casa con avalúo de $1.411M— y cero bienes. Otra rama los está sumando al
     * modelo. Cuando llegue, lo único que hay que hacer es llenar este campo en
     * [resumenCompartidoDe] con la MISMA regla que use el Inicio; la página ya lo pinta como un
     * renglón más de «lo que tiene» y ya lo suma al patrimonio (ver [patrimonio]).
     *
     * `null` = el modelo no sabe de bienes, que NO es lo mismo que «tiene cero bienes»: con `null` la
     * página no dibuja un «Bienes $0» que afirmaría algo falso.
     */
    val bienes: Long?,
    val deudas: Long,
    /** Lo que tiene (plata + condicionado + bienes) menos lo que debe. */
    val patrimonio: Long,
    /** «25 de agosto al 24 de septiembre». */
    val rangoDelPeriodo: String,
    val ingresos: Long,
    val gastoPorCategoria: Map<String, Long>,
    /** Solo las deudas con saldo, la más grande primero. */
    val deudasConSaldo: List<DeudaCompartida>,
) {
    val gastos: Long get() = gastoPorCategoria.values.sum()
    val flujo: Long get() = ingresos - gastos
    /** Lo que tiene, sumado: la barra del patrimonio lo necesita junto. */
    val loQueTiene: Long get() = tuPlata + condicionado + (bienes ?: 0L)
}

/**
 * Una deuda como la ve un tercero. **El nombre ya viene sin números de cuenta completos** — ver
 * [sinNumerosCompletos], que es lo único que decide eso.
 */
internal data class DeudaCompartida(
    val nombre: String,
    val esTarjeta: Boolean,
    val banco: String?,
    /** En pesos, positivo = se debe. */
    val saldo: Long,
    /** La cuota del crédito, o el pago mínimo de la tarjeta si el dueño lo anotó. */
    val cuota: Long?,
    /** % efectivo anual. `null` en las tarjetas: Movi no guarda su tasa. */
    val tasaEa: Double?,
    val sinIntereses: Boolean,
)

/** Arma el resumen del usuario [uid] **ahora**. Una pasada por la base, más la TRM (cacheada por día). */
internal suspend fun resumenCompartidoDe(uid: String, venceEn: Long): ResumenCompartido {
    val tasa = FxRateService.usdToCop()
    val ahora = AppClock.now().toInstant().toEpochMilli()

    data class Cuenta(val id: String, val nombre: String, val tipo: AccountType, val condicionadaA: String?)
    data class TerminosDeCredito(val banco: String, val cuota: Long, val tasa: Double, val sinIntereses: Boolean)

    val (quien, cuentas, creditos, tarjetas) = dbQuery {
        val nombre = Users.select(Users.name).where { Users.id eq uid }.firstOrNull()?.get(Users.name).orEmpty()
        val cuentas = Accounts.selectAll().where { Accounts.userId eq uid }.mapNotNull { fila ->
            // Un tipo que este server no conoce se salta en vez de tumbar la página entera.
            val tipo = runCatching { AccountType.valueOf(fila[Accounts.type]) }.getOrNull() ?: return@mapNotNull null
            Cuenta(fila[Accounts.id], fila[Accounts.name], tipo, normalizarCondicion(fila[Accounts.conditionedTo]))
        }
        val creditos = Credits.selectAll().where { Credits.userId eq uid }.associate { fila ->
            fila[Credits.accountId] to TerminosDeCredito(
                banco = fila[Credits.bank],
                cuota = fila[Credits.installment],
                tasa = fila[Credits.rateEa],
                sinIntereses = fila[Credits.sinIntereses] ?: false,
            )
        }
        val tarjetas = Cards.selectAll().where { Cards.userId eq uid }.associate { fila ->
            fila[Cards.accountId] to (fila[Cards.bank] to fila[Cards.pagoMinimo])
        }
        Cuatro(nombre, cuentas, creditos, tarjetas)
    }

    val eventosPorCuenta = loadNonVoidedEvents(uid).groupBy { it.accountId }
    val valor = cuentas.associate { it.id to accountCopValue(it.tipo, eventosPorCuenta[it.id].orEmpty(), tasa) }

    val tuPlata = cuentas.filter { esDeTuPlata(it.tipo, it.condicionadaA) }.sumOf { valor.getValue(it.id) }
    val condicionadas = cuentas.filter { !esCuentaDeDeuda(it.tipo) && it.condicionadaA != null }
    val condicionado = condicionadas.sumOf { valor.getValue(it.id) }
    val deudas = cuentas.filter { esCuentaDeDeuda(it.tipo) }.sumOf { valor.getValue(it.id) }
    // Ver el KDoc de [ResumenCompartido.bienes]: hoy el modelo no los tiene.
    val bienes: Long? = null
    val patrimonio = netWorth(cuentas.map { it.id to it.tipo }, eventosPorCuenta, tasa) + (bienes ?: 0L)

    val periodo = contextoDelPeriodoDe(uid)

    val deudasConSaldo = cuentas
        .filter { esCuentaDeDeuda(it.tipo) }
        .map { it to valor.getValue(it.id) }
        .filter { (_, saldo) -> saldo > 0L }
        .sortedByDescending { (_, saldo) -> saldo }
        .map { (cuenta, saldo) ->
            val credito = creditos[cuenta.id]
            val tarjeta = tarjetas[cuenta.id]
            DeudaCompartida(
                nombre = sinNumerosCompletos(cuenta.nombre),
                esTarjeta = cuenta.tipo == AccountType.CREDIT_CARD,
                banco = (credito?.banco ?: tarjeta?.first)?.takeIf { it.isNotBlank() }?.let(::sinNumerosCompletos),
                saldo = saldo,
                cuota = credito?.cuota?.takeIf { it > 0L } ?: tarjeta?.second?.takeIf { it > 0L },
                tasaEa = credito?.tasa?.takeIf { it > 0.0 },
                sinIntereses = credito?.sinIntereses ?: false,
            )
        }

    return ResumenCompartido(
        quien = quien,
        generadoEn = ahora,
        venceEn = venceEn,
        tuPlata = tuPlata,
        condicionado = condicionado,
        condicionadoA = condicionadas.mapNotNull { it.condicionadaA }.distinct().singleOrNull(),
        bienes = bienes,
        deudas = deudas,
        patrimonio = patrimonio,
        rangoDelPeriodo = periodo.rango,
        ingresos = periodo.ingresos,
        gastoPorCategoria = periodo.gastoPorCategoria,
        deudasConSaldo = deudasConSaldo,
    )
}

/** Cuatro cosas leídas en la misma transacción. Un `Pair` de `Pair`s no se lee. */
private data class Cuatro<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/**
 * **Ningún número de cuenta completo sale de Movi hacia un tercero.**
 *
 * Los nombres de cuenta los escribe el dueño, y los escribe como le dice el banco: «Hipoteca 1254»,
 * «Vehículo 8761» —ya la cola—, pero nada impide «Ahorros 0012345678» o «Tarjeta 4513 2200 1234
 * 5678». Esto recorta **toda** secuencia de cinco dígitos o más (contando espacios, puntos o guiones
 * entre ellos) a sus últimos cuatro: «Ahorros ••5678». Cuatro dígitos es lo que el propio banco
 * imprime en un extracto; es lo que sirve para que Caro sepa de cuál se habla y no alcanza para
 * nada más.
 *
 * Se aplica en el server, antes de pintar, y no confía en que el dueño haya escrito bien: la
 * prueba de la página lo fija con un nombre que trae el número entero.
 */
internal fun sinNumerosCompletos(texto: String): String =
    SECUENCIA_DE_DIGITOS.replace(texto) { m ->
        val digitos = m.value.filter(Char::isDigit)
        if (digitos.length >= 5) "••" + digitos.takeLast(4) else m.value
    }

private val SECUENCIA_DE_DIGITOS = Regex("""\d(?:[\d .\-]*\d)?""")
