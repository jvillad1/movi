package com.jvillada.movi.ui.credits

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.DeudaParaSalir
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.TipoDeDeuda
import com.jvillada.movi.shared.model.deudasParaSalir
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.planDeSalida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.recurrentes.ActionChip
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

    val ajustes = PeriodSettings(
        cutoffDay = (perfil.valor?.periodCutoffDay ?: 1).coerceIn(1, 31),
        iniciosPropios = perfil.valor?.periodStarts ?: emptyMap(),
    )
    val periodoActual = remember(ajustes) { periodoDe(Clock.System.now().toEpochMilliseconds(), ajustes) }
    val listas = creditos.valor?.let { c -> tarjetas.valor?.let { t -> c to t } }
    val deudas = remember(listas) { listas?.let { (c, t) -> deudasParaSalir(c, t) } }
    val plan = remember(deudas, abono, estrategia) {
        deudas?.let { planDeSalida(it, abono ?: 0L, estrategiaDelIndice(estrategia)) }
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
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .padding(horizontal = Movi.espacios.amplio)
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
                            periodoActual = periodoActual,
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
    periodoActual: com.jvillada.movi.shared.model.PeriodoFinanciero,
    estrategia: Int,
    onEstrategia: (Int) -> Unit,
    abono: Long?,
    onAbono: (Long?) -> Unit,
    onCargar: (DeudaParaSalir) -> Unit,
) {
    Spacer(Modifier.height(Movi.espacios.corto))
    if (plan.enElCalculo.isNotEmpty()) {
        Text(
            "Tus deudas te cobran ${formatCOP(interesDelMesPropio(plan))} de interés este mes",
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
        )
        Text(
            "Solo las que salen de tu bolsillo y tienen tasa.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(Movi.espacios.amplio))
    }

    // ── El simulador ──
    MinSectionHeader(title = "Si abonas al mes extra")
    MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated, padding = PaddingValues(Movi.espacios.amplio)) {
        MoneyField(abono, onAbono, placeholder = "Abono extra al mes, por ejemplo \$500.000")
        Spacer(Modifier.height(Movi.espacios.medio))
        resumenDelSimulador(plan, periodoActual).forEachIndexed { i, linea ->
            Text(
                linea,
                style = if (i == 0 && plan.abonoMensual > 0L && plan.enElCalculo.isNotEmpty()) Movi.textos.titulo else Movi.textos.apoyo,
                fontWeight = if (i == 0) FontWeight.Medium else FontWeight.Normal,
                color = if (i == 0) Movi.colores.texto else Movi.colores.textoMedio,
            )
            Spacer(Modifier.height(Movi.espacios.minimo))
        }
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(SUPUESTOS_DEL_PLAN_DE_SALIDA, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
    }

    // ── El orden ──
    if (plan.enElCalculo.isNotEmpty()) {
        Spacer(Modifier.height(Movi.espacios.seccion))
        MinSectionHeader(title = "En qué orden abonar")
        SelectorSegmentado(labels = ROTULOS_DE_LAS_ESTRATEGIAS, selected = estrategia, onSelect = onEstrategia)
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(explicacionDe(plan.estrategia), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Movi.espacios.medio))
        MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated, padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
            plan.enElCalculo.forEachIndexed { i, salida ->
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        "${salida.orden}. ${salida.deuda.nombre}",
                        style = Movi.textos.cuerpo,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.texto,
                    )
                    Text(textoDeLaDeuda(salida.deuda), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                    Text(textoDeLaSalida(salida, plan.abonoMensual, periodoActual), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
                if (i < plan.enElCalculo.lastIndex) Hairline()
            }
        }
    }

    // ── Lo que no entra ──
    if (plan.faltanDatos.isNotEmpty()) {
        Spacer(Modifier.height(Movi.espacios.seccion))
        MinSectionHeader(title = "Faltan datos", count = plan.faltanDatos.size)
        MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated, padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
            plan.faltanDatos.forEachIndexed { i, deuda ->
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(deuda.nombre, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                    Text(textoDeLaDeuda(deuda), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                    Text(motivoDeFuera(deuda), style = Movi.textos.apoyo, color = Movi.colores.aviso)
                    if (deuda.porQueNoEntra != com.jvillada.movi.shared.model.PorQueNoEntraAlPlan.EN_OTRA_MONEDA) {
                        Spacer(Modifier.height(Movi.espacios.corto))
                        ActionChip(
                            label = if (deuda.tipo == TipoDeDeuda.TARJETA) "Cargar en la tarjeta" else "Ir a Créditos",
                            primary = false,
                        ) { onCargar(deuda) }
                    }
                }
                if (i < plan.faltanDatos.lastIndex) Hairline()
            }
        }
    }

    // ── Lo ajeno ──
    if (plan.ajenas.isNotEmpty()) {
        Spacer(Modifier.height(Movi.espacios.seccion))
        MinSectionHeader(title = "No salen de tu bolsillo", count = plan.ajenas.size)
        Text(
            "Su cuota la paga otro, así que no compiten por tu abono.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.padding(start = Movi.espacios.corto, bottom = Movi.espacios.corto),
        )
        MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated, padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
            plan.ajenas.forEachIndexed { i, deuda ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(deuda.nombre, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                        Text(quienPagaEnTexto(deuda), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                    }
                    Text(formatCOP(deuda.saldo), style = Movi.textos.monto, color = Movi.colores.textoMedio)
                }
                if (i < plan.ajenas.lastIndex) Hairline()
            }
        }
    }

    Spacer(Modifier.height(Movi.espacios.seccion))
    Text(PIE_DEL_PLAN_DE_SALIDA, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
}
