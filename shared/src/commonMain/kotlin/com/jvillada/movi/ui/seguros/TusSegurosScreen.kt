package com.jvillada.movi.ui.seguros

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WorkOff
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.SeguroDetectado
import com.jvillada.movi.shared.model.SegurosQuePagas
import com.jvillada.movi.shared.model.TipoDeSeguro
import com.jvillada.movi.shared.model.tituloDelTipoDeSeguro
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.desplazableConElTeclado
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.Vacio
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.recurrentes.fechaCortaDeFactura
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import kotlinx.coroutines.launch

/*
 * # Vuelta 132 · «Tus seguros»: lo que ya pagas en seguros, sin vender nada
 *
 * La fase 0 del negocio de seguros: Movi muestra los seguros que la persona **ya** paga —el de la cuota
 * de cada crédito y los cargos de seguros en tarjetas y cuentas— con lo que ya lee, y pregunta si le
 * interesa que la ayude. Nada acá vende, cotiza ni recomienda: no hay precios de seguros ajenos ni
 * aseguradoras como oferta, y la línea del final lo dice.
 *
 * De arriba abajo: la cifra «Pagas $X al mes en seguros» con el año y de dónde sale; la lista agrupada
 * por tipo ([tituloDelTipoDeSeguro]); los no concluyentes en «¿Son seguros?» con «Sí» y «No»; la
 * tarjeta «¿Quieres que Movi te ayude con tus seguros?» con «Me interesa» **Secundario** (aquí no hay
 * acción principal: ningún amarillo). Tocar una fila abre su hoja: los movimientos que la prueban (cada
 * uno abre su hoja de siempre) o «Va dentro de la cuota de tu crédito» con la ficha, y «No es un seguro».
 *
 * La lectura es `GET /api/seguros?pantalla=1`; en el teléfono se guarda la última buena (`remote_cache`).
 * Las tres escrituras piden red y, sin ella, lo dicen ([toUserMessage]).
 */

const val TITULO_TUS_SEGUROS: String = "Tus seguros"
const val SIN_DATOS_DE_SEGUROS: String = "Mira cuáles pagas"
const val DE_DONDE_SALEN_LOS_SEGUROS: String =
    "Sale de tus movimientos de los últimos 12 meses y de las cuotas de tus créditos."
const val VACIO_DE_SEGUROS: String = "Movi todavía no ve seguros en tus movimientos"
const val COMO_LOS_VERIA: String =
    "Los verá cuando subas el extracto de tu tarjeta o de tu crédito, o cuando conectes la captura del banco: " +
        "el seguro de la cuota y los cobros de pólizas aparecen solos."
const val TITULO_SON_SEGUROS: String = "¿Son seguros?"
const val PREGUNTA_DE_AYUDA: String = "¿Quieres que Movi te ayude con tus seguros?"
const val TEXTO_DE_AYUDA: String =
    "Pronto Movi podrá decirte si alguno sobra o si te falta alguno. No vendemos nada sin que lo pidas."
const val ME_INTERESA: String = "Me interesa"
const val GRACIAS_TE_AVISAREMOS: String = "Gracias, te avisaremos"
const val MOVI_NO_VENDE_SEGUROS: String = "Movi no vende seguros ni te asesora para comprarlos."
const val NO_ES_UN_SEGURO: String = "No es un seguro"
const val VA_DENTRO_DE_LA_CUOTA: String = "Va dentro de la cuota de tu crédito"
const val VER_EL_CREDITO: String = "Ver el crédito"
const val NO_PUDIMOS_ABRIR_EL_MOVIMIENTO: String = "No pudimos abrir ese movimiento. Inténtalo de nuevo."

const val TAG_PANTALLA_DE_SEGUROS: String = "seguros:pantalla"
const val TAG_FILA_DE_SEGURO: String = "seguros:fila"
const val TAG_POR_CONFIRMAR: String = "seguros:por-confirmar"
const val TAG_ME_INTERESA: String = "seguros:me-interesa"
const val TAG_NO_ES_UN_SEGURO: String = "seguros:no-es"
const val TAG_SI_ES: String = "seguros:si-es"
const val TAG_NO_ES: String = "seguros:no"
const val TAG_CADA_MES: String = "seguros:cada-mes"
const val TAG_CADA_ANO: String = "seguros:cada-ano"

const val PREGUNTA_CADA_CUANTO: String = "¿Cada cuánto lo pagas?"
const val CADA_MES: String = "Cada mes"
const val CADA_ANO: String = "Cada año"
const val ES_EL_MISMO: String = "Es el mismo"
const val ES_OTRO: String = "Es otro"

/** «¿Es el mismo seguro de tu crédito Hipoteca casa?»: un cargo que se parece al de la cuota. */
fun preguntaDelDuplicado(credito: String): String = "¿Es el mismo seguro de tu crédito $credito?"

/** «y $2.400.000 en pagos de una vez»: lo que no se sabe cada cuánto se paga, fuera del total al mes. */
fun lineaDeLosPagosDeUnaVez(s: SegurosQuePagas): String? =
    s.enPagosDeUnaVez.takeIf { it > 0L }?.let { "y ${formatCOP(it)} en pagos de una vez" }

/** «Además, US$12 al mes en otra moneda, fuera de este total.»: nunca se convierte en silencio. */
fun lineaDeOtrasMonedas(s: SegurosQuePagas): String? =
    s.otrasMonedas.filterValues { it > 0L }.takeIf { it.isNotEmpty() }?.let { m ->
        "Además, " + m.entries.joinToString(" y ") { (moneda, monto) -> formatMoney(monto, moneda) } +
            " al mes en otra moneda, fuera de este total."
    }

/** Lo que dice la fila «Tus seguros» de Tú: «$121.100 al mes», o qué hay adentro sin datos. */
fun subtituloDeLaFilaDeSeguros(s: SegurosQuePagas?): String =
    if (s == null || s.alMes <= 0L) SIN_DATOS_DE_SEGUROS else "${formatCOP(s.alMes)} al mes"

/** «unos $1.453.200 al año», y si hay anuales, que se repartieron. */
fun lineaDelAnoDeLosSeguros(s: SegurosQuePagas): String =
    "unos ${formatCOP(s.alAno)} al año" + if (s.conAnuales) " · los anuales, repartidos en 12 meses" else ""

/** La línea de abajo del nombre: la cuenta y el último cobro, o qué pasó. */
fun lineaDeLaFilaDelSeguro(s: SeguroDetectado): String {
    val cuando = s.ultimoCobro?.let(::fechaCortaDeFactura)
    val partes = buildList {
        if (s.dentroDeLaCuota) add("Va dentro de la cuota") else if (s.cuentaNombre.isNotBlank()) add(s.cuentaNombre)
        when {
            // Un solo cobro sin saber cada cuánto: lo que pasó, sin inventar que se repite ni que se acabó.
            s.pagadoUnaVez -> add("Pagado una vez · ${formatMoney(s.montoDelCiclo ?: 0L, s.moneda)}" + (cuando?.let { " el $it" } ?: ""))
            !s.sigueCobrandose && cuando != null -> add("Dejó de cobrarse · último: $cuando")
            cuando != null -> add("Último cobro: $cuando")
        }
    }
    return partes.joinToString(" · ")
}

/** Debajo de la cifra de la fila: «al mes», «al año», «cada 6 meses» o «una vez». */
fun periodoDeLaFilaDelSeguro(s: SeguroDetectado): String = if (s.pagadoUnaVez) "una vez" else when (s.mesesEntreCobros) {
    1 -> "al mes"
    12 -> "al año"
    else -> "cada ${s.mesesEntreCobros} meses"
}

/** La cifra de la fila: lo del mes, o el cobro entero de un seguro que no es mensual. */
fun montoDeLaFilaDelSeguro(s: SeguroDetectado): Long =
    if (s.pagadoUnaVez || s.mesesEntreCobros > 1) s.montoDelCiclo ?: s.alMes else s.alMes

private fun iconoDelSeguro(tipo: TipoDeSeguro): ImageVector = when (tipo) {
    TipoDeSeguro.DEL_CREDITO, TipoDeSeguro.HOGAR_DEL_CREDITO -> Icons.Rounded.AccountBalance
    TipoDeSeguro.DE_TARJETA -> Icons.Rounded.CreditCard
    TipoDeSeguro.AUTO -> Icons.Rounded.DirectionsCar
    TipoDeSeguro.SALUD -> Icons.Rounded.MedicalServices
    TipoDeSeguro.VIDA -> Icons.Rounded.Favorite
    TipoDeSeguro.EXEQUIAS -> Icons.Rounded.LocalFlorist
    TipoDeSeguro.DESEMPLEO -> Icons.Rounded.WorkOff
    TipoDeSeguro.HOGAR -> Icons.Rounded.Home
    TipoDeSeguro.OTRO -> Icons.Rounded.Shield
}

/** El ícono de la fila de Tú. */
val ICONO_DE_TUS_SEGUROS: ImageVector get() = Icons.Rounded.HealthAndSafety

@Composable
fun TusSegurosScreen(onNavigate: (Screen) -> Unit) {
    val alcance = rememberCoroutineScope()
    val refresco = LocalRefreshTick.current
    var seguros by remember { mutableStateOf<SegurosQuePagas?>(null) }
    var fallo by remember { mutableStateOf(false) }
    var reintento by remember { mutableStateOf(0) }
    var abierto by remember { mutableStateOf<SeguroDetectado?>(null) }
    var enVuelo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var gracias by remember { mutableStateOf(false) }
    var movimiento by remember { mutableStateOf<FinancialEvent?>(null) }
    var cuentasDelMovimiento by remember { mutableStateOf<List<Account>>(emptyList()) }

    LaunchedEffect(reintento, refresco) {
        intentar { Repositories.wallets.getSeguros(enLaPantalla = true) }
            .onSuccess { seguros = it; fallo = false }
            .onFailure { if (seguros == null) fallo = true }
    }

    // Cada respuesta del dueño contesta el resultado nuevo: se pinta tal cual, sin volver a leer. Sin él
    // (un doble de prueba), se relee.
    fun tomar(nuevo: SegurosQuePagas?) {
        if (nuevo != null) seguros = nuevo else reintento++
    }

    fun responder(s: SeguroDetectado, esUnSeguro: Boolean) {
        if (enVuelo != null) return
        enVuelo = s.clave
        error = null
        alcance.launch {
            intentar { Repositories.wallets.responderSiEsUnSeguro(s.clave, esUnSeguro) }
                .onSuccess { nuevo -> abierto = null; tomar(nuevo) }
                .onFailure { error = it.toUserMessage() }
            enVuelo = null
        }
    }

    fun cadaCuanto(s: SeguroDetectado, meses: Int) {
        if (enVuelo != null) return
        enVuelo = s.clave
        error = null
        alcance.launch {
            intentar { Repositories.wallets.cadaCuantoSePagaElSeguro(s.clave, meses) }
                .onSuccess(::tomar)
                .onFailure { error = it.toUserMessage() }
            enVuelo = null
        }
    }

    fun meInteresa() {
        if (enVuelo != null || gracias) return
        enVuelo = TAG_ME_INTERESA
        error = null
        alcance.launch {
            intentar { Repositories.wallets.meInteresaLaAyudaConLosSeguros() }
                .onSuccess { gracias = true }
                .onFailure { error = it.toUserMessage() }
            enVuelo = null
        }
    }

    fun abrirMovimiento(id: String) {
        abierto = null
        alcance.launch {
            val evento = intentar { Repositories.wallets.getEventsByDay() }.getOrNull()
                ?.flatMap { it.items }?.firstOrNull { it.id == id }
            if (evento == null) {
                error = NO_PUDIMOS_ABRIR_EL_MOVIMIENTO
                return@launch
            }
            cuentasDelMovimiento = intentar { Repositories.wallets.getAccounts() }.getOrDefault(emptyList())
            movimiento = evento
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo).testTag(TAG_PANTALLA_DE_SEGUROS)) {
        MinScreenHeader(title = TITULO_TUS_SEGUROS, leading = HeaderLeading.Back(Screen.Tu))
        Column(
            modifier = Modifier.fillMaxSize().desplazableConElTeclado()
                .padding(horizontal = Medida.margenDePantalla)
                .padding(top = Medida.dentroDeSeccion, bottom = Medida.entreSecciones),
            verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
        ) {
            val s = seguros
            when {
                s == null && fallo -> NoSePudoLeer("No pudimos cargar tus seguros", onReintentar = { reintento++ })
                s == null -> Superficie(relleno = RellenoDeSuperficie.Lista) { repeat(3) { EsqueletoDeFila() } }
                else -> {
                    if (s.seguros.isEmpty() && s.porConfirmar.isEmpty()) {
                        Vacio(titulo = VACIO_DE_SEGUROS, detalle = COMO_LOS_VERIA, icono = ICONO_DE_TUS_SEGUROS)
                    } else {
                        CifraDeLosSeguros(s)
                        s.seguros.groupBy { tituloDelTipoDeSeguro(it.tipo) }.forEach { (titulo, delTipo) ->
                            Column {
                                Seccion(titulo = titulo)
                                Superficie(relleno = RellenoDeSuperficie.Lista) {
                                    delTipo.forEachIndexed { i, seguro ->
                                        if (i > 0) Separador()
                                        FilaDelSeguro(seguro, onClick = { abierto = seguro })
                                        if (seguro.pagadoUnaVez) {
                                            PreguntaDeLaFila(
                                                pregunta = PREGUNTA_CADA_CUANTO,
                                                si = CADA_MES to TAG_CADA_MES,
                                                no = CADA_ANO to TAG_CADA_ANO,
                                                ocupado = enVuelo != null,
                                                onSi = { cadaCuanto(seguro, 1) },
                                                onNo = { cadaCuanto(seguro, 12) },
                                                // Las dos respuestas pesan lo mismo.
                                                noEsFantasma = false,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (s.porConfirmar.isNotEmpty()) {
                            Column(modifier = Modifier.testTag(TAG_POR_CONFIRMAR)) {
                                Seccion(titulo = TITULO_SON_SEGUROS, conteo = s.porConfirmar.size)
                                Superficie(relleno = RellenoDeSuperficie.Lista) {
                                    s.porConfirmar.forEachIndexed { i, duda ->
                                        if (i > 0) Separador()
                                        FilaPorConfirmar(
                                            duda,
                                            ocupado = enVuelo != null,
                                            onAbrir = { abierto = duda },
                                            onSi = { responder(duda, esUnSeguro = true) },
                                            onNo = { responder(duda, esUnSeguro = false) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    error?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
                    TarjetaDeAyuda(
                        yaDijo = gracias || s.meInteresa,
                        cargando = enVuelo == TAG_ME_INTERESA,
                        onMeInteresa = ::meInteresa,
                    )
                    Text(MOVI_NO_VENDE_SEGUROS, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
            }
        }
    }

    abierto?.let { seguro ->
        HojaDelSeguro(
            seguro = seguro,
            ocupado = enVuelo != null,
            onDismiss = { abierto = null },
            onVerMovimiento = ::abrirMovimiento,
            onVerCredito = { id -> abierto = null; onNavigate(Screen.AccountDetail(id, AccountGroup.DEUDA)) },
            onNoEs = { responder(seguro, esUnSeguro = false) },
        )
    }
    movimiento?.let { evento ->
        HojaDelMovimiento(
            event = evento,
            cuentas = cuentasDelMovimiento,
            onDismiss = { movimiento = null },
            onCambiado = { movimiento = null; reintento++ },
        )
    }
}

@Composable
private fun CifraDeLosSeguros(s: SegurosQuePagas) {
    Superficie {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
            Text("Pagas", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            Cifra(s.alMes, variante = VarianteDeCifra.Heroe)
            Text("al mes en seguros", style = Movi.textos.cuerpo, color = Movi.colores.texto)
            lineaDeLosPagosDeUnaVez(s)?.let { Text(it, style = Movi.textos.cuerpo, color = Movi.colores.texto) }
            Text(lineaDelAnoDeLosSeguros(s), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            lineaDeOtrasMonedas(s)?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio) }
            Spacer(Modifier.height(Espacio.xs))
            Text(DE_DONDE_SALEN_LOS_SEGUROS, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

@Composable
private fun FilaDelSeguro(s: SeguroDetectado, onClick: () -> Unit) {
    Fila(
        titulo = s.nombre,
        subtitulo = lineaDeLaFilaDelSeguro(s),
        lineasDelSubtitulo = 4,
        lineasDelTitulo = 3,
        inicio = { IconoEnCirculo(iconoDelSeguro(s.tipo), tono = TonoDeIcono.Neutro) },
        // Un pago de una vez ya dice su monto en el subtítulo: el ancho es para la frase.
        final = if (s.pagadoUnaVez) null else { { CifraConPeriodo(s) } },
        chevron = true,
        onClick = onClick,
        // Contenido dinámico: el toque se anota con la cuenta, nunca con el nombre del seguro.
        entidadDeUso = s.cuentaId,
        modifier = Modifier.testTag("$TAG_FILA_DE_SEGURO:${s.clave}"),
    )
}

@Composable
private fun CifraConPeriodo(s: SeguroDetectado) {
    Column(horizontalAlignment = Alignment.End) {
        Cifra(montoDeLaFilaDelSeguro(s), moneda = s.moneda)
        Text(periodoDeLaFilaDelSeguro(s), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    }
}

/**
 * Una pregunta de dos respuestas debajo de una fila: «¿Cada cuánto lo pagas?» (Cada mes · Cada año),
 * «¿Es el mismo seguro de tu crédito X?» (Es el mismo · Es otro) o, sin pregunta, «Sí» · «No». Nunca amarillo.
 */
@Composable
private fun PreguntaDeLaFila(
    pregunta: String?,
    si: Pair<String, String>,
    no: Pair<String, String>,
    ocupado: Boolean,
    onSi: () -> Unit,
    onNo: () -> Unit,
    noEsFantasma: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla).padding(bottom = Espacio.s),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        pregunta?.let { Text(it, style = Movi.textos.cuerpo, color = Movi.colores.texto) }
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Boton(
                si.first, onClick = onSi, variante = VarianteDeBoton.Secundario, tamano = TamanoDeBoton.S,
                habilitado = !ocupado, modifier = Modifier.testTag(si.second),
            )
            Boton(
                no.first, onClick = onNo,
                variante = if (noEsFantasma) VarianteDeBoton.Fantasma else VarianteDeBoton.Secundario,
                tamano = TamanoDeBoton.S, habilitado = !ocupado, modifier = Modifier.testTag(no.second),
            )
        }
    }
}

@Composable
private fun FilaPorConfirmar(s: SeguroDetectado, ocupado: Boolean, onAbrir: () -> Unit, onSi: () -> Unit, onNo: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().testTag("$TAG_FILA_DE_SEGURO:${s.clave}")) {
        Fila(
            titulo = s.nombre,
            subtitulo = lineaDeLaFilaDelSeguro(s),
            lineasDelSubtitulo = 4,
            lineasDelTitulo = 3,
            inicio = { IconoEnCirculo(iconoDelSeguro(s.tipo), tono = TonoDeIcono.Neutro) },
            // Un pago de una vez ya dice su monto en el subtítulo: el ancho es para la frase.
        final = if (s.pagadoUnaVez) null else { { CifraConPeriodo(s) } },
            chevron = true,
            onClick = onAbrir,
            entidadDeUso = s.cuentaId,
        )
        val credito = s.posibleDuplicadoDe
        if (credito != null) {
            // «Es el mismo» lo descarta (no suma dos veces); «Es otro» lo cuenta aparte.
            PreguntaDeLaFila(
                pregunta = preguntaDelDuplicado(credito),
                si = ES_OTRO to TAG_SI_ES,
                no = ES_EL_MISMO to TAG_NO_ES,
                ocupado = ocupado,
                onSi = onSi,
                onNo = onNo,
                noEsFantasma = false,
            )
        } else {
            PreguntaDeLaFila(pregunta = null, si = "Sí" to TAG_SI_ES, no = "No" to TAG_NO_ES, ocupado = ocupado, onSi = onSi, onNo = onNo)
        }
    }
}

@Composable
private fun TarjetaDeAyuda(yaDijo: Boolean, cargando: Boolean, onMeInteresa: () -> Unit) {
    Superficie {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(
                PREGUNTA_DE_AYUDA, style = Movi.textos.titulo, color = Movi.colores.texto,
                modifier = Modifier.semantics { heading() },
            )
            Text(TEXTO_DE_AYUDA, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
            Spacer(Modifier.height(Espacio.xxs))
            if (yaDijo) {
                Text(
                    GRACIAS_TE_AVISAREMOS, style = Movi.textos.cuerpoFuerte, color = Movi.colores.texto,
                    modifier = Modifier.testTag(TAG_ME_INTERESA),
                )
            } else {
                Boton(
                    ME_INTERESA, onClick = onMeInteresa, variante = VarianteDeBoton.Secundario,
                    cargando = cargando, modifier = Modifier.testTag(TAG_ME_INTERESA),
                )
            }
        }
    }
}

@Composable
private fun HojaDelSeguro(
    seguro: SeguroDetectado,
    ocupado: Boolean,
    onDismiss: () -> Unit,
    onVerMovimiento: (String) -> Unit,
    onVerCredito: (String) -> Unit,
    onNoEs: () -> Unit,
) {
    MarcoDeHoja(onDismiss = onDismiss) {
        EncabezadoDeHoja(titulo = seguro.nombre, subtitulo = lineaDeLaFilaDelSeguro(seguro))
        Spacer(Modifier.height(Espacio.m))
        val credito = seguro.creditoId
        if (seguro.dentroDeLaCuota && credito != null) {
            Text(VA_DENTRO_DE_LA_CUOTA, style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Spacer(Modifier.height(Espacio.xs))
            Text(
                "${formatCOP(seguro.alMes)} al mes, que el banco cobra con cada cuota.",
                style = Movi.textos.apoyo, color = Movi.colores.textoMedio,
            )
            Spacer(Modifier.height(Espacio.s))
            Boton(VER_EL_CREDITO, onClick = { onVerCredito(credito) }, variante = VarianteDeBoton.Secundario, tamano = TamanoDeBoton.S)
        } else {
            Text("Los cobros que lo prueban", style = Movi.textos.tituloMedio, color = Movi.colores.texto)
            Spacer(Modifier.height(Espacio.xs))
            Superficie(relleno = RellenoDeSuperficie.Lista) {
                seguro.cobros.forEachIndexed { i, cobro ->
                    if (i > 0) Separador()
                    Fila(
                        titulo = cobro.descripcion,
                        subtitulo = fechaCortaDeFactura(cobro.fecha) ?: cobro.fecha,
                        final = { Cifra(cobro.monto, moneda = seguro.moneda) },
                        chevron = true,
                        onClick = { onVerMovimiento(cobro.movimientoId) },
                        entidadDeUso = cobro.movimientoId,
                    )
                }
            }
        }
        Spacer(Modifier.height(Espacio.m))
        Boton(
            NO_ES_UN_SEGURO, onClick = onNoEs, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S,
            habilitado = !ocupado, modifier = Modifier.testTag(TAG_NO_ES_UN_SEGURO),
        )
        Spacer(Modifier.height(Espacio.m))
    }
}
