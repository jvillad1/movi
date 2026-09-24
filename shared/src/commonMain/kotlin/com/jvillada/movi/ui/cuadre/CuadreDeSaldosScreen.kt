package com.jvillada.movi.ui.cuadre

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.MAX_ACCOUNT_BALANCE_COP
import com.jvillada.movi.shared.model.groupLabel
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.altoDeMoneyFieldConRotulo
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
import com.jvillada.movi.ui.components.TAG_TITULO_DE_FILA_ESQUELETO
import com.jvillada.movi.ui.components.altoDeUnRenglon
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/**
 * # «Cuadre de saldos» — lo que dice el banco contra lo que cree Movi
 *
 * Movi deriva cada saldo de los movimientos. Eso es lo que hace que sus cifras se puedan explicar
 * renglón por renglón, y también su único punto ciego: **lo que mueve plata sin emitir un
 * movimiento se desvía para siempre**. El 21-sep, sobre los datos reales del dueño, los
 * rendimientos de Nu habían crecido $745.856 y los de la Fiducuenta $1.637 sin un solo SMS que
 * capturar; una cuota de manejo que solo figura en el extracto hace lo mismo del otro lado.
 *
 * Esta pantalla es el lugar donde eso se corrige: todas las cuentas juntas, con lo que Movi cree al
 * lado de un campo para escribir lo que dice el banco. No inventa ninguna forma nueva de tocar un
 * saldo — usa el ajuste que ya existía (ver `CuadreDeSaldosLogic` y, del lado del server,
 * `balanceAdjustmentEventFor`), que registra un **movimiento visible** en vez de sobrescribir un
 * número.
 *
 * ## Tres decisiones que la hacen segura
 *
 * - **Un campo vacío es «esta no la cuadré», nunca «el banco dice $0».** Es la falla que más caro
 *   costaría: entrar, cuadrar una cuenta y que las otras cuatro se vayan a cero.
 * - **Nada se escribe hasta confirmar**, y antes de confirmar se lee en palabras lo que va a
 *   quedar anotado, con las dos cifras que se están comparando.
 * - **Un ajuste no es plata que se movió**, así que no entra en «Gastos» ni en «Ingresos» del
 *   período: su categoría es la reservada «Ajuste de saldo» y `isCashFlow` la excluye entera, en
 *   cualquier tipo de cuenta. Sí mueve el saldo, que es justamente lo que se vino a arreglar.
 */
@Composable
fun CuadreDeSaldosScreen(onNavigate: (Screen) -> Unit) {
    var cuentas by remember { mutableStateOf<List<Account>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    // Ver [NoSePudoLeer]: no se afirma «no tienes cuentas» sin una lectura que haya contestado.
    var leidas by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resultado by remember { mutableStateOf<String?>(null) }

    // Lo que el dueño va escribiendo, por cuenta. `null` (o ausente) = campo vacío = no la cuadró.
    val escrito = remember { mutableStateMapOf<String, Long?>() }
    val coroutine = rememberCoroutineScope()

    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(loadKey, refreshTick) {
        cargando = true
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { cuentas = cuentasParaCuadrar(it); leidas = true }
            .onFailure { e -> error = e.toUserMessage() }
        cargando = false
    }

    val hoy = hoyEnAppZone()
    // Un solo reloj para toda la pantalla: si cada fila leyera el suyo, dos filas podrían quedar
    // de distinto lado del umbral en la misma composición.
    val ahora = remember(loadKey) { Clock.System.now().toEpochMilliseconds() }
    val noSeLeyo = !cargando && !leidas

    // Lo que queda por anotar AHORA: cuentas con una cifra escrita que no coincide con la de Movi.
    val pendientes = cuentas.filter { cuenta ->
        val (enMovi, _) = saldoQueCreeMovi(cuenta)
        hayAjusteQueAnotar(diferenciaDelCuadre(enMovi, escrito[cuenta.id]))
    }
    val fueraDeRango = escrito.values.any { it != null && it > MAX_ACCOUNT_BALANCE_COP }
    val sePuedeAnotar = pendientes.isNotEmpty() && !fueraDeRango && !guardando

    fun anotar() {
        // La guarda contra el doble toque (mismo patrón que la hoja de ajuste de un crédito): sin
        // ella, dos toques seguidos mandan dos veces y el segundo se calcula contra un saldo que
        // el primero ya movió.
        if (!sePuedeAnotar) return
        guardando = true
        error = null
        resultado = null
        coroutine.launch {
            var anotados = 0
            var fallados = 0
            // De a una y en orden. Un POST que falla no corta los que siguen: cuadrar cuatro
            // cuentas y perderlas todas porque la tercera no tenía señal sería peor que el
            // problema que esta pantalla vino a resolver.
            pendientes.forEach { cuenta ->
                val objetivo = escrito[cuenta.id] ?: return@forEach
                runCatching { Repositories.wallets.adjustAccountBalance(cuenta.id, objetivo) }
                    .onSuccess { anotados++; escrito.remove(cuenta.id) }
                    .onFailure { e -> fallados++; error = e.toUserMessage() }
            }
            guardando = false
            resultado = when {
                anotados == 0 -> null
                fallados > 0 -> "Se anotaron $anotados y ${if (fallados == 1) "una quedó" else "$fallados quedaron"} sin anotar."
                anotados == 1 -> "Listo: se anotó el ajuste."
                else -> "Listo: se anotaron $anotados ajustes."
            }
            // Recargar deja a la vista el saldo nuevo — y el «Cuadrada hoy» de cada fila.
            loadKey++
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MinScreenHeader(
                title = "Cuadre de saldos",
                leading = HeaderLeading.Back(fallback = Screen.Accounts),
                subtitle = if (cuentas.isEmpty()) null else "Compara con lo que dice tu banco hoy",
            )

            if (noSeLeyo) {
                Spacer(Modifier.height(14.dp))
                NoSePudoLeer(
                    "No pudimos cargar tus cuentas",
                    onReintentar = { loadKey++ },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (!noSeLeyo) LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            ) {
                item {
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(18.dp),
                    ) {
                        Text(QUE_ES_EL_CUADRE, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (cuentas.isEmpty() && !cargando) {
                        Text(
                            "Todavía no tienes cuentas de Dinero ni de Inversión.",
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.textoMedio,
                        )
                    }
                }
                // Ola B, tarea 9: antes de la primera lectura buena, la pantalla quedaba en
                // blanco debajo de la explicación — ni una fila, ni una rueda. `!leidas` y no
                // solo `cargando`: una recarga con las cuentas ya pintadas (tocar «Reintentar»,
                // volver de anotar un ajuste) sigue con `items(cuentas)` de siempre.
                if (cargando && !leidas) {
                    cuadreEsqueleto()
                } else {
                    items(cuentas, key = { it.id }) { cuenta ->
                        FilaDeCuadre(
                            cuenta = cuenta,
                            hoy = hoy,
                            ahora = ahora,
                            escrito = escrito[cuenta.id],
                            habilitado = !guardando,
                            onEscribir = { escrito[cuenta.id] = it },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item {
                    resultado?.let {
                        Text(it, style = Movi.textos.apoyo, color = Movi.colores.entra)
                        Spacer(Modifier.height(8.dp))
                    }
                    error?.let {
                        Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (!noSeLeyo && cuentas.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sePuedeAnotar) Movi.colores.texto else Movi.colores.textoApagado)
                        .clickable(enabled = sePuedeAnotar) { anotar() }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            guardando -> "Anotando…"
                            pendientes.size > 1 -> "Anotar ${pendientes.size} ajustes"
                            else -> "Anotar el ajuste"
                        },
                        color = Movi.colores.fondo,
                        style = Movi.textos.cuerpo,
                    )
                }
            }
        }
    }
}

/**
 * Lo que la pantalla explica arriba de todo. Vive afuera del `@Composable` para poder leerse de
 * corrido: tiene que dejar claras las dos cosas que no son obvias —que el ajuste queda como un
 * movimiento y que no cuenta como gasto— antes de que alguien escriba una cifra.
 */
internal const val QUE_ES_EL_CUADRE: String =
    "Escribe el saldo que muestra tu banco y Movi anota la diferencia como un ajuste. Queda como " +
        "un movimiento visible en la cuenta, no como un número editado a mano, y no cuenta como " +
        "gasto ni como ingreso del período. La cuenta que dejes en blanco no se toca."

/** Una cuenta: lo que Movi cree, desde cuándo nadie la mira, el campo y lo que se va a anotar. */
@Composable
private fun FilaDeCuadre(
    cuenta: Account,
    hoy: LocalDate,
    ahora: Long,
    escrito: Long?,
    habilitado: Boolean,
    onEscribir: (Long?) -> Unit,
) {
    val (enMovi, moneda) = saldoQueCreeMovi(cuenta)
    val diferencia = diferenciaDelCuadre(enMovi, escrito)
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                Text(
                    cuenta.name,
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                )
                Text(
                    textoDelUltimoCuadre(cuenta, hoy),
                    style = Movi.textos.apoyo,
                    // En ámbar cuando lleva demasiado sin cuadrarse: es el mismo dato que dispara
                    // el aviso del Inicio, dicho acá en el único lugar donde se puede resolver.
                    color = if (estaSinCuadrar(cuenta, ahora)) Movi.colores.aviso else Movi.colores.textoMedio,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Movi dice", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Text(
                    formatMoney(enMovi, moneda),
                    style = Movi.textos.monto,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        MoneyField(
            value = escrito,
            onValueChange = { if (habilitado) onEscribir(it) },
            label = "LO QUE DICE EL BANCO",
            placeholder = "Sin cuadrar",
            // El símbolo de la moneda de la cuenta: en Colombia «$20» se lee veinte pesos, así que
            // una cuenta en dólares con el símbolo del peso invitaría a escribir otra cifra.
            prefix = if (moneda == "COP") "$" else "US$",
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                escrito != null && escrito > MAX_ACCOUNT_BALANCE_COP -> "Saldo fuera de rango — revisa el monto."
                else -> textoDelCuadre(enMovi, escrito, moneda)
            },
            style = Movi.textos.apoyo,
            color = if (hayAjusteQueAnotar(diferencia)) Movi.colores.texto else Movi.colores.textoMedio,
        )
        Text(
            cuenta.type.groupLabel,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Cuántas filas pinta [cuadreEsqueleto] mientras las cuentas no llegaron ni una vez. */
private const val FILAS_DE_CUADRE_ESQUELETO = 3

/**
 * **Cuadre de saldos mientras carga, con la forma de [FilaDeCuadre]** (Ola B, tarea 9). Antes de
 * esta tarea la pantalla quedaba en blanco debajo de la explicación de arriba —ni una rueda, ni
 * una fila— hasta que las cuentas contestaban. Copia el mismo `MinCard` de 18 dp de relleno, el
 * título y subtítulo a la izquierda, «Movi dice» + su cifra a la derecha, el campo «LO QUE DICE EL
 * BANCO» y las dos líneas de apoyo de abajo.
 */
private fun LazyListScope.cuadreEsqueleto() {
    items(FILAS_DE_CUADRE_ESQUELETO) {
        FilaDeCuadreEsqueleto()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FilaDeCuadreEsqueleto() {
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_LISTA_ESQUELETO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                LineaEsqueleto(
                    fraccionDelAncho = 0.7f,
                    estilo = Movi.textos.titulo,
                    modifier = Modifier.testTag(TAG_TITULO_DE_FILA_ESQUELETO),
                )
                Spacer(Modifier.height(4.dp))
                LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.apoyo)
            }
            Column(horizontalAlignment = Alignment.End) {
                BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.apoyo), ancho = 64.dp)
                Spacer(Modifier.height(4.dp))
                BloqueEsqueleto(alto = altoDeUnRenglon(Movi.textos.monto), ancho = 96.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
        // El campo «LO QUE DICE EL BANCO»: un bloque a todo el ancho, sin abrir el teclado que un
        // `MoneyField` de verdad ofrecería. Fix round 1: el alto sale de `altoDeMoneyFieldConRotulo`
        // —los mismos tokens que arma el campo real, no un número puesto a ojo— para que no se
        // desalinee en silencio si `Movi.textos.apoyo`/`.monto` cambian.
        BloqueEsqueleto(alto = altoDeMoneyFieldConRotulo())
        Spacer(Modifier.height(8.dp))
        LineaEsqueleto(fraccionDelAncho = 0.85f, estilo = Movi.textos.apoyo)
        Spacer(Modifier.height(6.dp))
        LineaEsqueleto(fraccionDelAncho = 0.3f, estilo = Movi.textos.apoyo)
    }
}
