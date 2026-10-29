package com.jvillada.movi.ui.primeruso

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.accounts.EstadoDelFormularioDeCuenta
import com.jvillada.movi.ui.accounts.crearCuentaConSaldo
import com.jvillada.movi.ui.components.toUserMessage

/*
 * # Lo que se lleva escrito en un paso de la guía (revisión de la vuelta 54)
 *
 * Con el arriendo escrito («$1.200.000, el 5»), «Guardar» iba adentro del formulario con borde y
 * «Siguiente» estaba apagado: la revisión tocó «Saltar este paso» creyendo que avanzaba, y el arriendo
 * se perdió. Ahora cada formulario de un paso es un [Borrador] y el pie de la guía lo mira:
 *
 * - con uno escrito y completo, el principal es **«Guardar y seguir»**: guarda todo lo escrito con la
 *   creación de siempre y avanza;
 * - con uno escrito e incompleto, el pie dice qué falta ([razonDelPie]);
 * - «Saltar este paso» (y «Ahora no») con algo escrito pregunta antes ([preguntaDeDescartar]).
 */

/** Un formulario de un paso de la guía, visto desde el pie. */
interface Borrador {
    /** ¿Tiene algo escrito que se perdería al saltar? */
    val escrito: Boolean

    /** Lo que falta para guardarlo («Falta el monto»); `null` si está completo. */
    val queFalta: String?

    /** Cómo se nombra en una frase: «el arriendo», «la cuenta», «la tarjeta». */
    val articulo: String

    /** Lo guarda con la creación de siempre. `false` si falló (el error queda en [error]). */
    suspend fun guardar(): Boolean

    val error: String?
}

/** El principal del pie con algo escrito. */
const val GUARDAR_Y_SEGUIR: String = "Guardar y seguir"

/** «Falta el monto del arriendo»: lo que dice el pie cuando un borrador escrito no se puede guardar. Pura. */
fun razonDelPie(borradores: List<Borrador>): String? =
    borradores.firstOrNull { it.escrito && it.queFalta != null }?.let { b ->
        "${b.queFalta} de ${b.articulo}".replace(" de el ", " del ")
    }

/** «¿Descartar el arriendo que escribiste?»; `null` si no hay nada escrito. Pura. */
fun preguntaDeDescartar(borradores: List<Borrador>): String? {
    val escritos = borradores.filter { it.escrito }.map { it.articulo }.distinct()
    if (escritos.isEmpty()) return null
    val que = if (escritos.size == 1) escritos.single() else escritos.dropLast(1).joinToString(", ") + " y " + escritos.last()
    return "¿Descartar $que que escribiste?"
}

/** La cuenta del formulario del paso de cuentas, creada con [crearCuentaConSaldo] como la hoja «Nueva cuenta». */
@Stable
class BorradorDeCuenta(val formulario: EstadoDelFormularioDeCuenta, private val alCrear: (Account) -> Unit) : Borrador {
    var creando by mutableStateOf(false)
        private set
    override var error: String? by mutableStateOf(null)
        private set
    private var yaCreada: Account? = null

    override val escrito: Boolean
        get() = formulario.banco != null || formulario.nombre.isNotBlank() || formulario.saldo != null
    override val queFalta: String? get() = if (formulario.sePuedeCrear) null else "Falta el nombre"
    override val articulo: String = "la cuenta"

    override suspend fun guardar(): Boolean {
        if (!formulario.sePuedeCrear || creando) return false
        creando = true
        error = null
        val cuenta = formulario.cuentaNueva()
        val ok = runCatching { crearCuentaConSaldo(cuenta, yaCreada = yaCreada) { yaCreada = it } }
            .onSuccess { creada ->
                alCrear(creada.copy(balance = cuenta.balance))
                formulario.limpiar()
                yaCreada = null
            }
            .onFailure {
                error = if (yaCreada != null) {
                    "La cuenta quedó creada, pero no se pudo guardar el saldo. Toca «$AGREGAR_CUENTA» otra vez."
                } else {
                    it.toUserMessage()
                }
            }
            .isSuccess
        creando = false
        return ok
    }
}

/** La tarjeta de crédito del paso de cuentas: banco, cupo y día de pago, con `createCard` de siempre. */
@Stable
class BorradorDeTarjeta(private val alCrear: (String) -> Unit) : Borrador {
    var abierta by mutableStateOf(false)
    var banco by mutableStateOf<String?>(null)
    var cupo by mutableStateOf<Long?>(null)
    var diaDePago by mutableStateOf("")
    var creando by mutableStateOf(false)
        private set
    override var error: String? by mutableStateOf(null)
        private set

    val diaValido: Int? get() = diaDePago.toIntOrNull()?.takeIf { it in 1..31 }

    override val escrito: Boolean get() = abierta && (banco != null || cupo != null || diaDePago.isNotBlank())
    override val queFalta: String?
        get() = when {
            banco == null -> "Falta el banco"
            diaValido == null -> "Falta el día de pago"
            else -> null
        }
    override val articulo: String = "la tarjeta"

    override suspend fun guardar(): Boolean {
        val elBanco = banco ?: return false
        val elDia = diaValido ?: return false
        if (creando) return false
        creando = true
        error = null
        val nombre = "Tarjeta $elBanco"
        val ok = runCatching {
            Repositories.wallets.createCard(
                CreateCardRequest(
                    name = nombre,
                    terms = CardTerms(accountId = "", bank = elBanco, creditLimit = cupo, paymentDay = elDia),
                ),
            )
        }.onSuccess {
            alCrear(nombre)
            banco = null; cupo = null; diaDePago = ""; abierta = false
        }.onFailure { error = it.toUserMessage() }.isSuccess
        creando = false
        return ok
    }
}

/**
 * Un pago de cada mes: el de un atajo ([nombreFijo], «Arriendo») o «Agregar otro» (`null`, con nombre a
 * mano). Se guarda con `createRecurringRule` de siempre.
 */
@Stable
class BorradorDeRecurrente(
    val nombreFijo: String?,
    val categoria: String,
    private val cuenta: () -> String?,
    private val alCrear: (RecurringRule) -> Unit,
) : Borrador {
    var nombre by mutableStateOf(nombreFijo ?: "")
    var monto by mutableStateOf<Long?>(null)
    var dia by mutableStateOf("")
    var guardando by mutableStateOf(false)
        private set
    override var error: String? by mutableStateOf(null)
        private set

    val diaValido: Int? get() = dia.toIntOrNull()?.takeIf { it in 1..31 }

    override val escrito: Boolean
        get() = monto != null || dia.isNotBlank() || (nombreFijo == null && nombre.isNotBlank())
    override val queFalta: String?
        get() = when {
            nombre.isBlank() -> "Falta el nombre"
            (monto ?: 0L) <= 0L -> "Falta el monto"
            diaValido == null -> "Falta el día"
            else -> null
        }
    override val articulo: String get() = articuloDelPago(nombreFijo)

    override suspend fun guardar(): Boolean {
        if (queFalta != null || guardando) return false
        val elMonto = monto ?: return false
        val elDia = diaValido ?: return false
        guardando = true
        error = null
        val regla = RecurringRule(
            id = "",
            name = nombre.trim(),
            category = categoria,
            amount = elMonto,
            dayOfMonth = elDia,
            type = TransactionType.EXPENSE,
            accountId = cuenta(),
        )
        val ok = runCatching { Repositories.wallets.createRecurringRule(regla) }
            .onSuccess { alCrear(it) }
            .onFailure { error = it.toUserMessage() }
            .isSuccess
        guardando = false
        return ok
    }
}

/** «el arriendo», «la suscripción»; un pago escrito a mano, «el pago». Pura. */
fun articuloDelPago(nombreFijo: String?): String = when (nombreFijo) {
    null -> "el pago"
    "Una suscripción" -> "la suscripción"
    else -> "el " + nombreFijo.lowercase()
}
