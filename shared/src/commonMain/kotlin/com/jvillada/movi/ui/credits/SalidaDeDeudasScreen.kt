package com.jvillada.movi.ui.credits

import com.jvillada.movi.theme.Espacio
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.DeudaParaSalir
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.TipoDeDeuda
import com.jvillada.movi.shared.model.deudasParaSalir
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.planDeSalida
import com.jvillada.movi.shared.model.planDelCredito
import com.jvillada.movi.shared.model.interesDelMes
import com.jvillada.movi.shared.model.deudasSinSaldoDe
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ANCHO_DE_LECTURA
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.recurrentes.BajoLaFila
import com.jvillada.movi.ui.recurrentes.NumeroEnCirculo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import kotlinx.datetime.Clock

const val TAG_SALIDA_DE_DEUDAS = "salida-de-deudas"

/**
 * # «Cómo salir de tus deudas» (Ola 4)
 *
 * Desde Patrimonio → Deudas (Créditos). Por cada deuda: saldo, tasa, cuota e interés del mes; el
 * orden para un abono extra (avalancha, o bola de nieve como alternativa); y el simulador «Si abonas
 * $X al mes extra», que dice cuándo sales de cada una y cuánto interés te ahorras.
 *
 * Las deudas son las de Créditos —las mismas dos lecturas, `getCredits` y `getCards`— y la cuenta es
 * [planDeSalida] en `:core`, la misma que usa Movi AI. Lo que no entra se dice con su porqué: una
 * tarjeta sin tasa ofrece cargarla ahí mismo; lo que paga la nómina o un tercero va aparte.
 */
@Composable
fun SalidaDeDeudasScreen(onNavigate: (Screen) -> Unit) {
    var recarga by remember { mutableStateOf(0) }
    val creditos = rememberLectura(ClaveDeLectura.Creditos, reintento = recarga) { Repositories.wallets.getCredits() }
    val tarjetas = rememberLectura(ClaveDeLectura.Tarjetas, reintento = recarga) { Repositories.wallets.getCards() }
    val perfil = rememberLectura(ClaveDeLectura.Perfil, reintento = recarga) { Repositories.wallets.getUserProfile() }
    var estrategia by rememberSaveable { mutableStateOf(0) }
    var abono by rememberSaveable { mutableStateOf<Long?>(null) }
    var editandoTarjeta by remember { mutableStateOf<CardSummary?>(null) }

    val ajustes = perfil.valor?.ajustesDelPeriodo() ?: PeriodSettings()
    val periodoActual = remember(ajustes) { periodoDe(Clock.System.now().toEpochMilliseconds(), ajustes) }
    val listas = creditos.valor?.let { c -> tarjetas.valor?.let { t -> c to t } }
    val deudas = remember(listas) { listas?.let { (c, t) -> deudasParaSalir(c, t) } }
    val plan = remember(deudas, abono, estrategia) {
        deudas?.let { planDeSalida(it, abono ?: 0L, estrategiaDelIndice(estrategia)) }
    }
    // Vuelta 59: el interés del mes de Créditos (créditos y tarjetas, `interesDelMes` de :core) y la
    // primera cuota sin pagar de cada deuda, de donde se cuentan sus fechas.
    val hoy = hoyEnAppZone()
    val interes = remember(listas) {
        listas?.let { (c, t) -> interesDelMes(c.mapNotNull { planDelCredito(it, hoy) }, t) }
    }
    val primeras = remember(listas) { listas?.let { (c, t) -> primerasCuotasDe(c, t, hoy) }.orEmpty() }
    // Vuelta 59: las deudas sin saldo no entran al plan (no hay saldo que amortizar) y se dicen aparte.
    val sinSaldo = remember(listas) {
        listas?.let { (c, t) -> deudasSinSaldoDe(c.map { it.account } + t.map { it.account }) }.orEmpty()
    }
    val noSeLeyo = listas == null && (creditos.terminada || tarjetas.terminada) && !creditos.actualizando && !tarjetas.actualizando

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MinScreenHeader(title = TITULO_SALIDA_DE_DEUDAS, leading = HeaderLeading.Back(fallback = Screen.Credits))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Medida.botonFlotante + Espacio.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ANCHO_DE_LECTURA)
                        .fillMaxWidth()
                        .padding(horizontal = Medida.margenDePantalla)
                        .testTag(TAG_SALIDA_DE_DEUDAS),
                ) {
                    when {
                        noSeLeyo -> NoSePudoLeer("No pudimos leer tus deudas", onReintentar = { recarga++ })
                        plan == null -> {
                            Spacer(Modifier.height(Movi.espacios.medio))
                            repeat(3) {
                                LineaEsqueleto(fraccionDelAncho = 0.9f, estilo = Movi.textos.cuerpo)
                                Spacer(Modifier.height(Movi.espacios.medio))
                            }
                        }
                        else -> ContenidoDelPlan(
                            plan = plan,
                            interes = interes,
                            periodoActual = periodoActual,
                            primeras = primeras,
                            sinSaldo = sinSaldo,
                            onCuadrar = { id -> onNavigate(Screen.CuadrarLaCuenta(id)) },
                            estrategia = estrategia,
                            onEstrategia = { estrategia = it },
                            abono = abono,
                            onAbono = { abono = it },
                            onCargar = { deuda ->
                                if (deuda.tipo == TipoDeDeuda.TARJETA) {
                                    editandoTarjeta = tarjetas.valor?.firstOrNull { it.account.id == deuda.id }
                                } else {
                                    onNavigate(Screen.Credits)
                                }
                            },
                        )
                    }
                }
            }
        }
        editandoTarjeta?.let { tarjeta ->
            CardTermsSheet(
                editing = tarjeta,
                onDismiss = { editandoTarjeta = null },
                onSaved = {
                    editandoTarjeta = null
                    recarga++
                },
            )
        }
    }
}

@Composable
private fun ContenidoDelPlan(
    plan: com.jvillada.movi.shared.model.PlanDeSalida,
    interes: com.jvillada.movi.shared.model.InteresDelMes?,
    periodoActual: com.jvillada.movi.shared.model.PeriodoFinanciero,
    primeras: Map<String, com.jvillada.movi.shared.model.PeriodoFinanciero>,
    sinSaldo: List<com.jvillada.movi.shared.model.Account>,
    onCuadrar: (String) -> Unit,
    estrategia: Int,
    onEstrategia: (Int) -> Unit,
    abono: Long?,
    onAbono: (Long?) -> Unit,
    onCargar: (DeudaParaSalir) -> Unit,
) {
    Spacer(Modifier.height(Movi.espacios.corto))
    if (interes != null && interes.propio > 0L) {
        Text(
            "Tus deudas te cobran ${formatCOP(interes.propio)} de interés este mes",
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
        )
        Text(
            desgloseDelInteresDelMes(interes),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(Movi.espacios.amplio))
    }

    // ── El simulador ──
    Seccion(titulo = "Si abonas al mes extra")
    Superficie(modifier = Modifier.fillMaxWidth()) {
        // Vuelta 42: «Ej: 500.000» (el campo ya pone el «$» delante). Era «Abono extra al mes, por
        // ejemplo $500.000», que no cabía y se cortaba en «…por ejemplo $500....».
        CampoDeMonto(
            valor = abono,
            alCambiar = onAbono,
            ejemplo = EJEMPLO_DEL_ABONO_EXTRA,
            modifier = Modifier.testTag(TAG_ABONO_EXTRA),
        )
        Spacer(Modifier.height(Movi.espacios.medio))
        resumenDelSimulador(plan, periodoActual, primeras).forEachIndexed { i, linea ->
            Text(
                linea,
                style = when {
                    i == 0 && plan.abonoMensual > 0L && plan.enElCalculo.isNotEmpty() -> Movi.textos.tituloMedio
                    i == 0 -> Movi.textos.apoyoMedio
                    else -> Movi.textos.apoyo
                },
                color = if (i == 0) Movi.colores.texto else Movi.colores.textoMedio,
            )
            Spacer(Modifier.height(Movi.espacios.minimo))
        }
        // Vuelta 42: debajo del campo, una sola frase de apoyo. Los supuestos (un párrafo gris
        // denso debajo de otro negro) pasaron a la nota del final.
    }

    // ── El orden ──
    if (plan.enElCalculo.isNotEmpty()) {
        Spacer(Modifier.height(Medida.entreSecciones))
        Seccion(titulo = "En qué orden abonar")
        SelectorSegmentado(labels = ROTULOS_DE_LAS_ESTRATEGIAS, selected = estrategia, onSelect = onEstrategia)
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(explicacionDe(plan.estrategia), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Movi.espacios.medio))
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            plan.enElCalculo.forEachIndexed { i, salida ->
                Fila(
                    titulo = salida.deuda.nombre,
                    subtitulo = textoDeLaDeuda(salida.deuda),
                    inicio = { NumeroEnCirculo(salida.orden) },
                )
                BajoLaFila {
                    Text(textoDeLaSalida(salida, plan.abonoMensual, primeras[salida.deuda.id] ?: periodoActual), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
                if (i < plan.enElCalculo.lastIndex) Separador(SangriaDeSeparador.ConInicio)
            }
        }
    }

    // ── Lo que no entra ──
    if (plan.faltanDatos.isNotEmpty()) {
        Spacer(Modifier.height(Medida.entreSecciones))
        Seccion(titulo = "Faltan datos", conteo = plan.faltanDatos.size)
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            plan.faltanDatos.forEachIndexed { i, deuda ->
                Fila(
                    titulo = deuda.nombre,
                    subtitulo = textoDeLaDeuda(deuda),
                    inicio = { IconoEnCirculo(Icons.Rounded.ErrorOutline, tono = TonoDeIcono.Aviso) },
                )
                BajoLaFila {
                    Text(motivoDeFuera(deuda), style = Movi.textos.apoyo, color = Movi.colores.aviso)
                    if (deuda.porQueNoEntra != com.jvillada.movi.shared.model.PorQueNoEntraAlPlan.EN_OTRA_MONEDA) {
                        Boton(
                            texto = if (deuda.tipo == TipoDeDeuda.TARJETA) "Cargar en la tarjeta" else "Ir a Créditos",
                            onClick = { onCargar(deuda) },
                            variante = VarianteDeBoton.Secundario,
                            tamano = TamanoDeBoton.S,
                        )
                    }
                }
                if (i < plan.faltanDatos.lastIndex) Separador(SangriaDeSeparador.ConInicio)
            }
        }
    }

    // ── Sin saldo en Movi (vuelta 59) ──
    // No entran al cálculo, y antes desaparecían de la pantalla sin decir por qué.
    if (sinSaldo.isNotEmpty()) {
        Spacer(Modifier.height(Medida.entreSecciones))
        Seccion(titulo = SIN_SALDO_EN_MOVI, conteo = sinSaldo.size)
        Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_SIN_SALDO_EN_LA_SALIDA), relleno = RellenoDeSuperficie.Lista) {
            sinSaldo.forEachIndexed { i, cuenta ->
                Fila(
                    titulo = textoDeLaDeudaSinSaldoEnLaSalida(cuenta.name),
                    inicio = { IconoEnCirculo(Icons.Rounded.ErrorOutline, tono = TonoDeIcono.Aviso) },
                    final = {
                        Boton(
                            texto = CUADRAR_LAS_DEUDAS_SIN_SALDO,
                            onClick = { onCuadrar(cuenta.id) },
                            variante = VarianteDeBoton.Fantasma,
                            tamano = TamanoDeBoton.S,
                        )
                    },
                )
                if (i < sinSaldo.lastIndex) Separador(SangriaDeSeparador.ConInicio)
            }
        }
    }

    // ── Lo ajeno ──
    if (plan.ajenas.isNotEmpty()) {
        Spacer(Modifier.height(Medida.entreSecciones))
        Seccion(titulo = "No salen de tu bolsillo", conteo = plan.ajenas.size)
        Text(
            "Su cuota la paga otro, así que no compiten por tu abono.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.padding(bottom = Medida.dentroDeSeccion),
        )
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            plan.ajenas.forEachIndexed { i, deuda ->
                Fila(
                    titulo = deuda.nombre,
                    subtitulo = quienPagaEnTexto(deuda),
                    final = { Cifra(deuda.saldo, tono = TonoDeCifra.Apagado) },
                )
                if (i < plan.ajenas.lastIndex) Separador(SangriaDeSeparador.Margen)
            }
        }
    }

    Spacer(Modifier.height(Medida.entreSecciones))
    // La nota de abajo: los supuestos del cálculo y que no es asesoría (vuelta 42: los supuestos
    // iban dentro del simulador).
    Text(
        notaDelPlanDeSalida(),
        style = Movi.textos.apoyo,
        color = Movi.colores.textoApagado,
        modifier = Modifier.testTag(TAG_NOTA_DEL_PLAN_DE_SALIDA),
    )
}
