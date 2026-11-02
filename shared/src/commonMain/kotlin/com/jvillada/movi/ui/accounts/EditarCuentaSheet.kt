package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EditarCuentaRequest
import com.jvillada.movi.shared.model.UNA_DEUDA_NO_CAMBIA_DE_TIPO
import com.jvillada.movi.shared.model.avisoAlRenombrar
import com.jvillada.movi.shared.model.esCuentaDeDeuda
import com.jvillada.movi.shared.model.problemaDelCambioDeTipo
import com.jvillada.movi.shared.model.problemaDelNombreDeCuenta
import com.jvillada.movi.shared.model.seEditaElSaldo
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.saldoEnSuMoneda
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.cuadre.diferenciaDelCuadre
import com.jvillada.movi.ui.cuadre.hayAjusteQueAnotar
import com.jvillada.movi.ui.cuadre.montoDelAjuste
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** El título de la hoja y de la fila del detalle que la abre (vuelta 98). */
const val EDITAR_LA_CUENTA: String = "Editar la cuenta"

/** El `testTag` de la fila «Editar la cuenta» del detalle. */
const val TAG_EDITAR_LA_CUENTA: String = "cuenta:editar"

/** El `testTag` de la hoja. */
const val TAG_HOJA_EDITAR_LA_CUENTA: String = "hoja:editar-la-cuenta"

/**
 * **El saldo con que se llena «Saldo de hoy»**: el que muestra la pantalla, en la moneda de la cuenta
 * ([saldoEnSuMoneda], el mismo de Cuadre de saldos). En negativo (un efectivo imposible) va vacío: el
 * campo no escribe negativos, y el server tampoco los acepta como objetivo de un cuadre. Sin «Saldo de
 * hoy» ([seEditaElSaldo]), `null`.
 */
fun saldoParaEditar(cuenta: Account): Long? =
    if (!seEditaElSaldo(cuenta)) null else saldoEnSuMoneda(cuenta).first.takeIf { it >= 0L }

/**
 * **La línea de «Saldo de hoy»**: «Movi anota un ajuste de +$X para que cuadre» cuando lo escrito no es
 * lo que Movi tiene; `null` si es igual o si el campo está vacío (vacío es «no lo toques», nunca $0,
 * como en Cuadre de saldos). Con las mismas funciones que esa pantalla ([diferenciaDelCuadre],
 * [hayAjusteQueAnotar], [montoDelAjuste]): es el mismo ajuste, dicho igual. Pura.
 */
fun lineaDelAjusteAlEditar(cuenta: Account, saldoEscrito: Long?): String? {
    if (!seEditaElSaldo(cuenta)) return null
    val (enMovi, moneda) = saldoEnSuMoneda(cuenta)
    val diferencia = diferenciaDelCuadre(enMovi, saldoEscrito)
    if (!hayAjusteQueAnotar(diferencia)) return null
    return "Movi anota un ajuste de ${montoDelAjuste(diferencia!!, moneda)} para que cuadre."
}

/** Lo que dice el campo vacío de una cuenta en negativo: la cifra de hoy, para que no se lea como $0. */
private fun ayudaDelSaldoVacio(cuenta: Account): String? {
    if (!seEditaElSaldo(cuenta)) return null
    val (enMovi, moneda) = saldoEnSuMoneda(cuenta)
    return if (enMovi < 0L) "Hoy Movi dice ${formatMoney(enMovi, moneda)}: escribe lo que tienes." else null
}

/**
 * **Lo que se le pide al server**, o `null` si no cambió nada. Solo viaja lo que cambió: el tipo si es
 * otro, el saldo si hay un ajuste que anotar ([lineaDelAjusteAlEditar]). Pura.
 */
fun pedidoDeEdicion(cuenta: Account, nombre: String, tipo: AccountType, saldoEscrito: Long?): EditarCuentaRequest? {
    val limpio = nombre.trim()
    val otroTipo = tipo.takeIf { it != cuenta.type }
    val saldo = saldoEscrito.takeIf { lineaDelAjusteAlEditar(cuenta, saldoEscrito) != null }
    if (limpio == cuenta.name && otroTipo == null && saldo == null) return null
    return EditarCuentaRequest(name = limpio, type = otroTipo, saldoDeHoy = saldo)
}

/** «Tarjeta de crédito · …»: el tipo de una deuda, que no cambia, con el porqué. */
private fun tipoFijoDe(cuenta: Account): String? {
    if (!esCuentaDeDeuda(cuenta.type)) return null
    val tipo = if (cuenta.type == AccountType.LOAN) "Préstamo" else "Tarjeta de crédito"
    return "$tipo. $UNA_DEUDA_NO_CAMBIA_DE_TIPO"
}

/**
 * # «Editar la cuenta» (vuelta 98)
 *
 * El mismo [FormularioDeCuenta] de crear, **lleno con lo de hoy** ([EstadoDelFormularioDeCuenta.cargar]):
 * banco, tipo, nombre y saldo. Lo abre la fila «Editar la cuenta» del detalle, a la vista —no un menú
 * «Más»—, porque el papá del dueño buscó cómo cambiarle el nombre y el saldo a una cuenta y no lo halló.
 *
 * - **El saldo no es un campo crudo.** Escribir otra cifra en «Saldo de hoy» dice en una línea qué va a
 *   pasar —«Movi anota un ajuste de +$X para que cuadre»— y al guardar el server escribe el MISMO ajuste
 *   que «Cuadrar con el banco». Nunca se reescribe un movimiento ni el saldo inicial.
 * - **El tipo** solo entre los de plata (Ahorros, Corriente, Efectivo, Inversión). Una deuda no ofrece
 *   las píldoras: dice su tipo y por qué no cambia. Tampoco «Saldo de hoy»: su saldo se cuadra en su
 *   ficha («Ajustar saldo»).
 * - **El nombre** con la regla de crear; si el nuevo pierde el número o el «Skandia» con que Movi la
 *   reconoce en avisos y extractos, lo dice debajo ([avisoAlRenombrar]).
 * - **Un bien no pasa por acá**: se edita en su hoja de siempre ([BienSheet]).
 *
 * Una sola llamada (`PUT /api/accounts/{id}`, [com.jvillada.movi.shared.repository.WalletRepository.editarCuenta]):
 * o queda todo o nada. Sin red, sobre una cuenta que el server ya tiene, no se guarda y se dice.
 */
@Composable
fun EditarCuentaSheet(
    cuenta: Account,
    onDismiss: () -> Unit,
    onGuardada: (Account) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val formulario = remember(cuenta.id) {
        EstadoDelFormularioDeCuenta().apply { cargar(cuenta, saldoParaEditar(cuenta)) }
    }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val problemaDelNombre = problemaDelNombreDeCuenta(formulario.nombre)
    val problemaDelTipo = problemaDelCambioDeTipo(cuenta, formulario.tipo)
    val pedido = pedidoDeEdicion(cuenta, formulario.nombre, formulario.tipo, formulario.saldo)
    val razon = when {
        problemaDelNombre != null -> problemaDelNombre
        problemaDelTipo != null -> problemaDelTipo
        pedido == null -> "No has cambiado nada"
        else -> null
    }

    fun guardar() {
        val p = pedido ?: return
        if (guardando || razon != null) return
        guardando = true
        error = null
        coroutine.launch {
            try {
                onGuardada(Repositories.wallets.editarCuenta(cuenta.id, p).account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable: en la web un fetch caído llega como `kotlin.Error`.
                error = e.toUserMessage()
                guardando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !guardando) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
                .testTag(TAG_HOJA_EDITAR_LA_CUENTA),
        ) {
            EncabezadoDeHoja(EDITAR_LA_CUENTA)
            Spacer(Modifier.height(Medida.entreSecciones))
            val moneda = saldoEnSuMoneda(cuenta).second
            FormularioDeCuenta(
                formulario,
                habilitado = !guardando,
                tipoFijo = tipoFijoDe(cuenta),
                conSaldo = seEditaElSaldo(cuenta),
                ayudaDelNombre = avisoAlRenombrar(cuenta.name, formulario.nombre),
                ayudaDelSaldo = lineaDelAjusteAlEditar(cuenta, formulario.saldo)
                    ?: ayudaDelSaldoVacio(cuenta).takeIf { formulario.saldo == null },
                monedaDelSaldo = if (moneda == "COP") "$" else moneda,
            )
            Spacer(Modifier.height(Espacio.l))
            PieDeHoja(
                texto = "Guardar",
                onClick = { guardar() },
                habilitado = razon == null,
                razon = if (guardando) null else razon,
                cargando = guardando,
                error = error,
                // Sin «Cancelar»: la × y el arrastre ya cancelan (sistema, `PieDeHoja`).
            )
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}
