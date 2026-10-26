package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.montoContado
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.tipoOPersona
import com.jvillada.movi.theme.Espacio as EspacioDeDiseno
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.CategoryField
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.ds.AvatarDePersona
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.formatCOP
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Tags para las pruebas. */
const val TAG_REPARTIR: String = "espacios:repartir"
const val TAG_GUARDAR_PAGO_OTRO: String = "espacios:guardar-pago-otro"
const val TAG_CONFIRMAR_SALDADO: String = "espacios:confirmar-saldado"

/** El título de una hoja, igual en las tres. */
@Composable
private fun TituloDeLaHoja(texto: String, detalle: String?) {
    Text(texto, style = Movi.textos.titulo, color = Movi.colores.texto)
    detalle?.let {
        Spacer(Modifier.height(EspacioDeDiseno.xxs))
        Text(it, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
    }
    Spacer(Modifier.height(Medida.dentroDeSeccion))
}

@Composable
private fun ErrorDeLaHoja(error: String?) {
    error?.let {
        Spacer(Modifier.height(EspacioDeDiseno.xs))
        Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
    }
}

/**
 * **Crear un espacio**: se elige con quién de los terceros guardados (las personas, no los
 * comercios). Sin ninguna persona guardada, lleva a «Personas y comercios» a crearla.
 */
@Composable
fun HojaNuevoEspacio(onDismiss: () -> Unit, onCreado: (Espacio) -> Unit, onIrATerceros: () -> Unit) {
    val coroutine = rememberCoroutineScope()
    var personas by remember { mutableStateOf<List<DestinoConocido>?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val id = remember { newId("esp") }

    LaunchedEffect(Unit) {
        personas = runCatching { Repositories.wallets.getDestinos() }.getOrDefault(emptyList())
            .filter { it.tipoOPersona() == TipoDeTercero.PERSONA }
    }

    fun crear(con: DestinoConocido) {
        if (trabajando) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                val espacio = Repositories.wallets.guardarEspacio(
                    Espacio(
                        id = id,
                        nombre = "Casa con ${con.nombre}".take(com.jvillada.movi.shared.model.MAX_NOMBRE_DE_ESPACIO),
                        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), MiembroDelEspacio(con.id, con.nombre)),
                    ),
                )
                onCreado(espacio)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
                trabajando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !trabajando) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            TituloDeLaHoja("¿Con quién compartes?", "Mitad y mitad. Solo tu parte cuenta en tus gastos.")
            val lista = personas
            when {
                lista == null -> Unit
                lista.isEmpty() -> {
                    Text(
                        "Primero guarda a esa persona en «Personas y comercios».",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
                    )
                    Spacer(Modifier.height(Medida.dentroDeSeccion))
                    Boton("Ir a Personas y comercios", onClick = onIrATerceros, anchoCompleto = true)
                }
                else -> Superficie(nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista) {
                    lista.forEachIndexed { i, d ->
                        if (i > 0) Separador()
                        Fila(
                            titulo = d.nombre,
                            subtitulo = "Casa con ${d.nombre}",
                            inicio = { AvatarDePersona(d.nombre) },
                            onClick = { crear(d) },
                        )
                    }
                }
            }
            ErrorDeLaHoja(error)
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}

/**
 * **«Marcar como saldado»**: el pago que deja al espacio a mano. Si Movi encontró transferencias que
 * pueden ser ese pago, se elige cuál (o ninguna): la elegida deja de contar como ingreso o gasto. El
 * id se elige al abrir y viaja en cada intento, así que tocar dos veces es saldar una.
 */
@Composable
fun HojaSaldar(detalle: DetalleDelEspacio, onDismiss: () -> Unit, onSaldado: () -> Unit) {
    val espacio = detalle.espacio
    val frase = fraseDelSaldo(espacio, detalle.saldo)
    val reembolso = frase.reembolso ?: return
    val coroutine = rememberCoroutineScope()
    val id = remember { newId("liq") }
    var vinculada by remember { mutableStateOf<String?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val propuesta = detalle.propuestas.firstOrNull { it.eventoId == vinculada }
    val monto = propuesta?.monto ?: reembolso.monto
    val titulo = if (reembolso.a == MIEMBRO_YO) {
        "${nombreEnElEspacio(espacio, reembolso.de)} te paga"
    } else {
        "Le pagas a ${nombreEnElEspacio(espacio, reembolso.a)}"
    }

    fun saldar() {
        if (trabajando) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                Repositories.wallets.liquidarEspacio(
                    espacio.id,
                    LiquidacionDelEspacio(
                        id = id,
                        de = reembolso.de,
                        a = reembolso.a,
                        monto = monto,
                        fecha = propuesta?.fecha ?: kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                        eventoId = propuesta?.eventoId,
                    ),
                )
                onSaldado()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
                trabajando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !trabajando) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            TituloDeLaHoja(titulo, "No es ingreso ni gasto: solo salda lo de ${espacio.nombre}.")
            Cifra(monto, VarianteDeCifra.Grande)
            if (detalle.propuestas.isNotEmpty()) {
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Text("¿Con cuál transferencia?", style = Movi.textos.cuerpo, color = Movi.colores.texto)
                Spacer(Modifier.height(EspacioDeDiseno.xs))
                Superficie(nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista) {
                    detalle.propuestas.forEachIndexed { i, p ->
                        if (i > 0) Separador()
                        Fila(
                            titulo = p.descripcion,
                            subtitulo = formatCOP(p.monto),
                            final = if (vinculada == p.eventoId) {
                                { Icon(Icons.Rounded.Check, contentDescription = "Elegida", tint = Movi.colores.marca) }
                            } else {
                                null
                            },
                            onClick = { vinculada = if (vinculada == p.eventoId) null else p.eventoId },
                        )
                    }
                }
                Spacer(Modifier.height(EspacioDeDiseno.xxs))
                Text(
                    if (vinculada == null) "Sin vincular: se anota el pago completo." else "La transferencia deja de contar como ingreso.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
            }
            ErrorDeLaHoja(error)
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Boton(
                "Marcar como saldado",
                onClick = ::saldar,
                cargando = trabajando,
                anchoCompleto = true,
                modifier = Modifier.testTag(TAG_CONFIRMAR_SALDADO),
            )
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}

/**
 * **«Pagó otro»**: un gasto del espacio que pagó otro miembro («Caro pagó el mercado»). No toca tus
 * cuentas; tu parte sí es gasto tuyo, el día del gasto. El id se elige al abrir.
 */
@Composable
fun HojaPagoOtro(espacio: Espacio, onDismiss: () -> Unit, onGuardado: () -> Unit) {
    val coroutine = rememberCoroutineScope()
    val id = remember { newId("gde") }
    val otros = espacio.miembros.filter { it.id != MIEMBRO_YO }
    var quien by remember { mutableStateOf(otros.firstOrNull()?.id) }
    var monto by remember { mutableStateOf<Long?>(null) }
    var categoria by remember { mutableStateOf("") }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pagador = quien
    val tuParte = if (pagador != null && (monto ?: 0L) > 0L) partesDelGasto(monto ?: 0L, espacio, pagador)[MIEMBRO_YO] ?: 0L else 0L
    val puede = pagador != null && (monto ?: 0L) > 0L && categoria.isNotBlank() && !trabajando

    fun guardar() {
        if (!puede || pagador == null) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                Repositories.wallets.anotarGastoDeOtro(
                    espacio.id,
                    GastoDelEspacio(
                        id = id,
                        espacioId = espacio.id,
                        pagadoPor = pagador,
                        monto = monto ?: 0L,
                        categoria = categoria.trim(),
                        descripcion = categoria.trim(),
                        fecha = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                    ),
                )
                onGuardado()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
                trabajando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !trabajando) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            TituloDeLaHoja("Pagó otro", "No toca tus cuentas. Tu parte cuenta en tus gastos de hoy.")
            if (otros.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs)) {
                    otros.forEach { m -> PildoraDeFiltro(m.nombre, seleccionada = quien == m.id, onClick = { quien = m.id }) }
                }
                Spacer(Modifier.height(Medida.dentroDeSeccion))
            }
            MoneyField(value = monto, onValueChange = { monto = it }, label = "¿Cuánto pagó ${otros.firstOrNull { it.id == quien }?.nombre ?: "otra persona"}?")
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            CategoryField(
                value = categoria,
                onValueChange = { categoria = it },
                type = TransactionType.EXPENSE,
                usedCategories = UsedCategoriesCache.used,
                prefs = UsedCategoriesCache.prefs,
                usos = UsedCategoriesCache.usosRecientes,
                label = "Categoría",
                placeholder = "Mercado",
            )
            if (tuParte > 0L) {
                Spacer(Modifier.height(EspacioDeDiseno.xs))
                Text("Tu parte ${formatCOP(tuParte)}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            ErrorDeLaHoja(error)
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Boton(
                "Guardar",
                onClick = ::guardar,
                habilitado = puede,
                cargando = trabajando,
                anchoCompleto = true,
                modifier = Modifier.testTag(TAG_GUARDAR_PAGO_OTRO),
            )
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}

/**
 * **«Repartir», en el detalle de un movimiento.** Un gasto propio se asigna a un espacio: desde ahí
 * cuenta solo tu parte. Si ya está repartido, dice tu parte y deja quitarlo. Solo aparece si el
 * dueño tiene algún espacio (o el movimiento ya está en uno) y el movimiento se puede repartir.
 *
 * @param onCambiado el movimiento con su parte ajena nueva (la pantalla de atrás relee igual).
 */
@Composable
internal fun FilaDeRepartir(
    evento: FinancialEvent,
    onCambiado: (FinancialEvent) -> Unit,
) {
    if (!sePuedeRepartir(evento) && evento.espacioId == null) return
    val coroutine = rememberCoroutineScope()
    var espacios by remember(evento.id) { mutableStateOf<List<Espacio>>(emptyList()) }
    var trabajando by remember(evento.id) { mutableStateOf(false) }
    var error by remember(evento.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(evento.id) {
        espacios = runCatching { Repositories.wallets.getEspacios() }.getOrDefault(emptyList())
    }
    val actual = espacios.firstOrNull { it.id == evento.espacioId }
    if (espacios.isEmpty() && evento.espacioId == null) return

    fun repartir(espacio: Espacio) {
        if (trabajando) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                val gasto = Repositories.wallets.repartirMovimiento(espacio.id, evento.id)
                onCambiado(evento.copy(parteAjena = gasto.monto - (gasto.partes[MIEMBRO_YO] ?: gasto.monto), espacioId = espacio.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
                trabajando = false
            }
        }
    }

    fun quitar(espacioId: String) {
        if (trabajando) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                Repositories.wallets.quitarDelEspacio(espacioId, evento.id)
                onCambiado(evento.copy(parteAjena = 0L, espacioId = null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
                trabajando = false
            }
        }
    }

    Superficie(nivel = NivelDeSuperficie.Capa2, modifier = Modifier.fillMaxWidth().testTag(TAG_REPARTIR)) {
        val repartidoEn = evento.espacioId
        if (repartidoEn != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.s)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Repartido en ${actual?.nombre ?: "un espacio"}", style = Movi.textos.cuerpo, color = Movi.colores.texto)
                    Text("Tu parte ${formatCOP(montoContado(evento))}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
                Boton(
                    "Quitar",
                    onClick = { quitar(repartidoEn) },
                    variante = VarianteDeBoton.Fantasma,
                    tamano = TamanoDeBoton.S,
                    cargando = trabajando,
                )
            }
        } else {
            Text("¿Es un gasto compartido?", style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Text("Repártelo: en tus gastos cuenta solo tu parte.", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            Spacer(Modifier.height(EspacioDeDiseno.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs)) {
                espacios.forEach { e ->
                    Boton(
                        if (espacios.size == 1) "Repartir" else e.nombre,
                        onClick = { repartir(e) },
                        tamano = TamanoDeBoton.S,
                        cargando = trabajando && espacios.size == 1,
                    )
                }
            }
        }
        ErrorDeLaHoja(error)
    }
}
