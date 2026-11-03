package com.jvillada.movi.ui.primeruso

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.sePreguntaSiYaPagasteElDeEstePeriodo
import com.jvillada.movi.ui.accounts.EstadoDelFormularioDeCuenta
import com.jvillada.movi.ui.accounts.crearCuentaConSaldo
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.recurrentes.ajustesParaPreguntarSiYaPagaste
import kotlinx.datetime.LocalDate

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
 *
 * **Vuelta 124 · «¿Ya pagaste el de este período?»**, como la hoja de un pago fijo nuevo (vuelta 121): si
 * el vencimiento de este período del día elegido ya pasó o es hoy ([sePreguntaSiYaPagasteElDeEstePeriodo],
 * la misma regla pura), el formulario lo pregunta con «Sí» por defecto, y el `POST` lleva
 * [RecurringRule.yaPagoElDeEstePeriodo] (el server lo revalida). [ajustes] son los del período que el
 * dueño acaba de elegir en la guía, o los de su perfil ([ajustesParaLaPreguntaDeLaGuia]); sin ellos no se
 * pregunta, como la hoja sin el perfil leído. La respuesta vive en el borrador: sobrevive a «Atrás» y a
 * cerrar y abrir la fila, como el monto y el día.
 */
@Stable
class BorradorDeRecurrente(
    val nombreFijo: String?,
    val categoria: String,
    private val cuenta: () -> String?,
    private val ajustes: () -> PeriodSettings? = { null },
    private val hoy: () -> LocalDate = { hoyEnAppZone() },
    private val alCrear: (RecurringRule) -> Unit,
) : Borrador {
    var nombre by mutableStateOf(nombreFijo ?: "")
    var monto by mutableStateOf<Long?>(null)
    private var diaElegido by mutableStateOf("")
    /** El día; al cambiarlo, una respuesta a una pregunta que dejó de verse se olvida ([olvidarSiYaNoSePregunta]). */
    var dia: String
        get() = diaElegido
        set(valor) {
            diaElegido = valor
            olvidarSiYaNoSePregunta()
        }
    /** Vuelta 124 · la respuesta a «¿Ya pagaste el de este período?»; «Sí» por defecto. Solo cuenta si [preguntaSiYaPago]. */
    var yaPago by mutableStateOf(true)

    /**
     * Revisión de la 124: **una pregunta que deja de verse vuelve a «Sí»**. Con «No» en el día 1, pasar al 20
     * (por venir: sin pregunta) y volver al 1 la muestra otra vez como una pregunta nueva, con «Sí». Se llama al
     * cambiar el día y al cambiar el período elegido en la guía.
     */
    fun olvidarSiYaNoSePregunta() {
        if (!preguntaSiYaPago) yaPago = true
    }
    var guardando by mutableStateOf(false)
        private set
    override var error: String? by mutableStateOf(null)
        private set

    val diaValido: Int? get() = dia.toIntOrNull()?.takeIf { it in 1..31 }

    /** ¿El formulario pregunta «¿Ya pagaste el de este período?»? La regla de la hoja, con el día elegido. */
    val preguntaSiYaPago: Boolean
        get() {
            val elDia = diaValido ?: return false
            val losAjustes = ajustes() ?: return false
            return sePreguntaSiYaPagasteElDeEstePeriodo(TransactionType.EXPENSE, desdeUnMovimiento = false, elDia, hoy(), losAjustes)
        }

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
            // Vuelta 124: la respuesta, solo si la pregunta se ve; si no, sin el campo (como la hoja).
            yaPagoElDeEstePeriodo = if (preguntaSiYaPago) yaPago else null,
        )
        val ok = runCatching { Repositories.wallets.createRecurringRule(regla) }
            .onSuccess { alCrear(it) }
            .onFailure { error = it.toUserMessage() }
            .isSuccess
        guardando = false
        return ok
    }
}

/**
 * Vuelta 124 · **los ajustes del período con que la guía pregunta «¿Ya pagaste el de este período?»**: los
 * que el dueño acaba de guardar en «Tu período» ([guardados], el corte y la regla del fin de semana, o la
 * quincena), con las excepciones a mano de su perfil; si no guardó ninguno en esta guía, los de su perfil
 * (que es lo que el server usa para revalidar).
 *
 * Vuelta 125: sin ninguno de los dos, el mismo criterio de la hoja y de la tarjeta de Movi AI
 * ([ajustesParaPreguntarSiYaPagaste]): si la lectura del perfil ya terminó ([seLeyoElPerfil]) y falló, el mes
 * de calendario —el corte con que nace toda cuenta—; `null` (no se pregunta) solo mientras se lee. Pura.
 */
fun ajustesParaLaPreguntaDeLaGuia(guardados: PeriodSettings?, perfil: UserProfile?, seLeyoElPerfil: Boolean): PeriodSettings? {
    if (guardados == null) return ajustesParaPreguntarSiYaPagaste(perfil, seLeyoElPerfil)
    val delPerfil = perfil?.ajustesDelPeriodo()
    return if (delPerfil == null) guardados else guardados.copy(iniciosPropios = delPerfil.iniciosPropios)
}

/** «el arriendo», «la suscripción»; un pago escrito a mano, «el pago». Pura. */
fun articuloDelPago(nombreFijo: String?): String = when (nombreFijo) {
    null -> "el pago"
    "Una suscripción" -> "la suscripción"
    else -> "el " + nombreFijo.lowercase()
}
