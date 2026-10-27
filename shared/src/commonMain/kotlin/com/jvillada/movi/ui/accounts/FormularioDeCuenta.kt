package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.BANCOS_CONOCIDOS
import com.jvillada.movi.shared.model.Marca
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.CirculoDeMarca
import com.jvillada.movi.ui.components.TamanoDeAvatar
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RotuloDeCampo

/** El tag del bloque de crear una cuenta: el mismo en la guía de primer uso y en «Nueva cuenta». */
const val TAG_FORMULARIO_DE_CUENTA: String = "formulario-de-cuenta"

/** El tag de la píldora «Bien», la que abre la hoja de bienes en vez de elegir un tipo. */
const val TAG_TIPO_BIEN: String = "formulario-de-cuenta-bien"

/**
 * Los bancos que se ofrecen de un toque, por su id de `MarcasConocidas` y en el orden en que se ven.
 * Los más comunes en Colombia; cualquier otro se escribe a mano en el nombre.
 */
val BANCOS_DE_UN_TOQUE: List<String> = listOf(
    "bancolombia", "nu", "davivienda", "nequi", "banco_de_bogota", "bbva", "daviplata",
)

/**
 * **Los tipos de cuenta, en palabras que entiende cualquiera** (vuelta de diseño 3 en la guía; desde
 * la vuelta 16, también en «Nueva cuenta»). Cada rótulo va a un [AccountType] que ya existe —no se
 * inventa ninguno—: ahorros, corriente y efectivo son los tres de **Dinero** (`AccountType.group`), y
 * se suman igual en todas las cuentas de Movi; lo que cambia es que el efectivo no recibe extractos.
 * Antes «Nueva cuenta» decía «Dinero / Inversión», los dos grupos de Patrimonio, que quien empieza no
 * sabe leer.
 */
val TIPOS_DE_CUENTA: List<Pair<String, AccountType>> = listOf(
    "Ahorros" to AccountType.SAVINGS,
    "Corriente" to AccountType.CHECKING,
    "Efectivo" to AccountType.CASH,
    "Inversión" to AccountType.INVESTMENT,
)

/** El rótulo de la píldora que abre la hoja de un bien (la casa, el carro). */
const val ROTULO_DEL_TIPO_BIEN: String = "Bien"

/** «Ahorros», «Corriente»…: el rótulo de [TIPOS_DE_CUENTA] de un tipo, o `null` si no se ofrece. */
fun rotuloDelTipoDeCuenta(tipo: AccountType): String? = TIPOS_DE_CUENTA.firstOrNull { it.second == tipo }?.first

/**
 * **El nombre que Movi propone** al elegir un banco: el banco y el tipo, «Bancolombia Ahorros». Sin
 * banco no propone nada. Pura.
 */
fun nombrePropuestoDeLaCuenta(banco: String?, tipo: AccountType): String? {
    banco ?: return null
    val rotulo = rotuloDelTipoDeCuenta(tipo) ?: return banco
    return "$banco $rotulo"
}

/**
 * **Lo que se lleva escrito del bloque de crear una cuenta**: banco, tipo, nombre y saldo. Lo
 * recuerda quien lo pinta (la guía lo limpia al agregar; la hoja se cierra).
 *
 * El nombre se propone solo ([nombrePropuestoDeLaCuenta]) **mientras esté vacío o siga siendo la
 * propuesta**: si el dueño escribió el suyo, elegir otro banco o tipo no se lo pisa.
 */
@Stable
class EstadoDelFormularioDeCuenta {
    var banco: String? by mutableStateOf(null)
        private set
    var tipo: AccountType by mutableStateOf(AccountType.SAVINGS)
        private set
    var nombre: String by mutableStateOf("")
    var saldo: Long? by mutableStateOf(null)

    private fun proponerSiNoLoEscribio(antes: String?) {
        if (nombre.isBlank() || nombre == antes) nombrePropuestoDeLaCuenta(banco, tipo)?.let { nombre = it }
    }

    fun elegirBanco(nuevo: String) {
        val antes = nombrePropuestoDeLaCuenta(banco, tipo)
        banco = nuevo
        proponerSiNoLoEscribio(antes)
    }

    fun elegirTipo(nuevo: AccountType) {
        val antes = nombrePropuestoDeLaCuenta(banco, tipo)
        tipo = nuevo
        proponerSiNoLoEscribio(antes)
    }

    /**
     * El nombre que se le pasa a la hoja de un bien: el que escribió el dueño, no el que Movi
     * propuso para una cuenta («Bancolombia Ahorros» no es el nombre de una casa).
     */
    fun nombreParaUnBien(): String =
        nombre.trim().takeIf { it != nombrePropuestoDeLaCuenta(banco, tipo) }.orEmpty()

    /** Lo único obligatorio es el nombre: el saldo puede quedar en $0. */
    val sePuedeCrear: Boolean get() = nombre.isNotBlank()

    /**
     * La cuenta que se crea, con la misma forma en los dos lugares: COP (el display de saldos solo
     * entiende COP), el saldo de hoy en `balance` —que `crearCuentaConSaldo` convierte en la apertura
     * de siempre— y un id propio desde el primer instante (ver `newId`).
     */
    fun cuentaNueva(): Account =
        Account(id = newId("acc"), name = nombre.trim(), type = tipo, balance = saldo ?: 0L, currency = "COP")

    fun limpiar() {
        banco = null
        tipo = AccountType.SAVINGS
        nombre = ""
        saldo = null
    }
}

@Composable
fun rememberEstadoDelFormularioDeCuenta(): EstadoDelFormularioDeCuenta = remember { EstadoDelFormularioDeCuenta() }

/** Los bancos de [BANCOS_DE_UN_TOQUE], con su marca de `MarcasConocidas`. */
private val MARCAS_DE_UN_TOQUE: List<Marca> =
    BANCOS_DE_UN_TOQUE.mapNotNull { id -> BANCOS_CONOCIDOS.firstOrNull { it.id == id } }

/**
 * **Crear una cuenta se ve igual en todos lados** (vuelta de diseño 16). Antes la guía de primer uso
 * ofrecía bancos con su marca y «Ahorros · Corriente · Efectivo · Inversión», y Patrimonio → «Nueva
 * cuenta» ofrecía «Dinero / Inversión / Bien» en tarjetas grandes, sin bancos. Ahora los dos pintan
 * este bloque:
 *
 * 1. **Banco**: píldoras con el avatar de su marca ([SelectorDeBanco]).
 * 2. **Tipo**: [TIPOS_DE_CUENTA] en píldoras; con [onElegirBien], también «Bien», que nunca queda
 *    elegido —tocarlo cambia de hoja, no de tipo— y le pasa el nombre escrito a quien llama.
 * 3. **Nombre**, propuesto solo al elegir el banco («Bancolombia Ahorros»).
 * 4. **Saldo de hoy** con [CampoDeMonto].
 *
 * No crea nada: quien lo pinta crea [EstadoDelFormularioDeCuenta.cuentaNueva] con
 * `crearCuentaConSaldo`, la creación de siempre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormularioDeCuenta(
    estado: EstadoDelFormularioDeCuenta,
    modifier: Modifier = Modifier,
    onElegirBien: ((nombre: String) -> Unit)? = null,
    habilitado: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().testTag(TAG_FORMULARIO_DE_CUENTA)) {
        RotuloDeCampo("Banco")
        SelectorDeBanco(elegido = estado.banco, onElegir = { if (habilitado) estado.elegirBanco(it) })
        Spacer(Modifier.height(Medida.dentroDeSeccion))

        RotuloDeCampo("Tipo")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            TIPOS_DE_CUENTA.forEach { (rotulo, tipo) ->
                PildoraDeFiltro(rotulo, seleccionada = estado.tipo == tipo, onClick = { if (habilitado) estado.elegirTipo(tipo) })
            }
            if (onElegirBien != null) {
                PildoraDeFiltro(
                    ROTULO_DEL_TIPO_BIEN,
                    seleccionada = false,
                    onClick = { if (habilitado) onElegirBien(estado.nombreParaUnBien()) },
                    icono = Icons.Rounded.Home,
                    modifier = Modifier.testTag(TAG_TIPO_BIEN),
                )
            }
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))

        Campo(estado.nombre, { estado.nombre = it }, rotulo = "Nombre", guia = "Ej: Bancolombia Ahorros", habilitado = habilitado)
        Spacer(Modifier.height(Medida.dentroDeSeccion))

        CampoDeMonto(estado.saldo, { estado.saldo = it }, rotulo = "Saldo de hoy", habilitado = habilitado)
    }
}

/**
 * Los bancos de un toque, cada uno con **el avatar de su marca** (vuelta de diseño 3): el mismo
 * círculo de color con la marca corta que la app pinta en las filas y las tarjetas de cuenta
 * ([CirculoDeMarca]), a [TamanoDeAvatar.Linea] dentro de la píldora. Lo usan este bloque y las
 * tarjetas de crédito de la guía.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectorDeBanco(elegido: String?, onElegir: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        MARCAS_DE_UN_TOQUE.forEach { marca ->
            PildoraDeFiltro(
                marca.nombre,
                seleccionada = marca.nombre == elegido,
                onClick = { onElegir(marca.nombre) },
                inicio = { CirculoDeMarca(marca, tamano = TamanoDeAvatar.Linea) },
            )
        }
    }
}
