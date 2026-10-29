package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.openingEventFor
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * **Patrimonio → «Nueva cuenta»** (y «+ Crear cuenta» desde Agregar, Hoy, Movimientos, Cuadre de
 * saldos y Primeros pasos). Desde la vuelta de diseño 16 pinta el mismo [FormularioDeCuenta] que el
 * paso «Tus cuentas» de la guía de primer uso: bancos con su marca, «Ahorros · Corriente · Efectivo ·
 * Inversión» (y «Bien» si quien llama lo ofrece), nombre propuesto solo y saldo de hoy. Antes ofrecía
 * «Dinero / Inversión / Bien» en tarjetas grandes y sin bancos: crear una cuenta se veía distinto
 * según por dónde se entrara.
 *
 * **Se desplaza** (el idioma de las hojas que sí lo hacen: el cuerpo en una `Column` con
 * `verticalScroll` + `weight(1f, fill = false)`, la manija afuera). Con los bancos el bloque es más
 * alto que las tarjetas de antes, y la hoja ya iba con margen cero en un teléfono bajo (Ola 12: unos
 * 28 dp cortados en el AVD de entonces, el borde de «Crear cuenta» contra la barra inferior).
 */
/** El título de la hoja de crear una cuenta (vuelta 61). */
const val TITULO_DE_NUEVA_CUENTA: String = "Nueva cuenta"

@Composable
fun CreateAccountSheet(
    onDismiss: () -> Unit,
    onAccountCreated: () -> Unit,
    /**
     * **Registrar un bien** (la casa, el carro) en vez de una cuenta de plata. Con esto la hoja
     * ofrece un tercer tipo, «Bien», que no crea nada acá: le pasa el nombre ya escrito a quien
     * llama, que abre [BienSheet]. Un bien no tiene saldo inicial ni movimientos —tiene un valor y
     * la fecha de ese valor—, así que meterlo en este formulario habría sido un campo de «saldo»
     * que miente sobre lo que guarda.
     *
     * `null` = no se ofrece: la hoja también se abre desde «Agregar» cuando todavía no hay
     * cuentas, y ahí lo que hace falta es de dónde sale la plata, no una casa.
     */
    onElegirBien: ((nombre: String) -> Unit)? = null,
) {
    val coroutine = rememberCoroutineScope()
    val formulario = rememberEstadoDelFormularioDeCuenta()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSave = formulario.sePuedeCrear && !saving
    // F24: el mismo patrón para todas las hojas de crear — si el botón está gris, debajo dice
    // la PRIMERA cosa que falta, no solo se apaga en silencio. Acá lo único obligatorio es el
    // nombre (el saldo puede quedar en $0).
    val missingFieldMessage = when {
        !formulario.sePuedeCrear -> "Falta el nombre"
        else -> null
    }

    // La cuenta que ya quedó creada en el server si el paso 1 (crear) tuvo éxito y el paso 2
    // (postear la apertura) falló — en la web son dos POST independientes. Sin esto, tocar
    // «Crear cuenta» de nuevo creaba una SEGUNDA cuenta (id nuevo) y la primera quedaba
    // huérfana en $0 para siempre, sin forma de borrarla desde la app. Con esto, el reintento
    // reusa la cuenta y solo repite el paso que falló. En Android/iOS postEvent nunca lanza,
    // así que este estado no se llega a usar — pero no cuesta nada tenerlo.
    var createdAccount by remember { mutableStateOf<Account?>(null) }

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            // La misma cuenta que arma la guía para la misma elección (ver
            // [EstadoDelFormularioDeCuenta.cuentaNueva]: COP, id propio, el saldo como apertura).
            val account = createdAccount ?: formulario.cuentaNueva()
            val result = runCatching {
                crearCuentaConSaldo(account, yaCreada = createdAccount) { createdAccount = it }
            }
            saving = false
            result.onSuccess { onAccountCreated() }
                .onFailure {
                    error = if (createdAccount != null)
                        "La cuenta quedó creada, pero no se pudo registrar el saldo inicial. Toca «Crear cuenta» para reintentar."
                    else it.toUserMessage()
                }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !saving) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            // Vuelta 61: con su título, como toda hoja (arrancaba en «Banco», sin decir qué se crea).
            EncabezadoDeHoja(TITULO_DE_NUEVA_CUENTA)
            Spacer(Modifier.height(Medida.entreSecciones))
            FormularioDeCuenta(formulario, onElegirBien = onElegirBien, habilitado = !saving)
            Spacer(Modifier.height(Espacio.xs))
            // F52: nota, no botón — Créditos vive en Más, no acá.
            Text(
                text = "¿Tarjetas o préstamos? Se cargan en Créditos",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )

            Spacer(Modifier.height(Espacio.l))

            // El pie de hoja del sistema: el error del guardado arriba, la razón abajo.
            PieDeHoja(
                texto = if (saving) "Creando…" else "Crear cuenta",
                onClick = { save() },
                habilitado = canSave,
                razon = if (saving) null else missingFieldMessage,
                error = error,
            )

            Spacer(Modifier.height(Espacio.s))
        }
    }
}

/**
 * **La creación de cuenta de siempre**: la de esta hoja y la del paso «Tus cuentas» de la guía de
 * primer uso, que no pueden ser dos.
 *
 * La cuenta se crea SIEMPRE en $0 — el saldo inicial que el dueño tipeó no viaja en ese POST. Es
 * esta función la que decide «esta cuenta arranca con plata», posteando el evento de apertura
 * aparte, explícito y una sola vez (ver el KDoc de `openingEventFor` en :core para el porqué: el
 * server dejó de fabricarlo — hacerlo ahí duplicaba el saldo de una cuenta creada offline cuando
 * el ingreso/gasto real anotado antes del sync se sumaba encima de la apertura que el server
 * fabricaba a partir del balance sincronizado).
 *
 * [yaCreada] es la cuenta que un intento anterior ya creó en el server y cuya apertura falló: con
 * ella el reintento no crea una SEGUNDA cuenta, solo repite la apertura. [alCrearla] avisa en
 * cuanto la cuenta existe, antes de la apertura, para que quien llama la recuerde.
 */
internal suspend fun crearCuentaConSaldo(
    cuenta: Account,
    yaCreada: Account? = null,
    alCrearla: (Account) -> Unit = {},
): Account {
    val creada = yaCreada ?: Repositories.wallets.createAccount(cuenta.copy(balance = 0L)).also(alCrearla)
    if (cuenta.balance != 0L) {
        val apertura = openingEventFor(
            cuenta.copy(id = creada.id),
            now = Clock.System.now().toEpochMilliseconds(),
        )
        if (apertura != null) Repositories.wallets.postEvent(apertura)
    }
    return creada
}
