package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.MAX_PROPOSITO_APARTADO
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.mesDeTexto
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.problemaDelGastoDelApartado
import com.jvillada.movi.shared.model.valorEnPesosDe
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ConfirmacionEnLinea
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.SelectorDeCategoria
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.Casilla
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.FilaDeCategorias
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TagsDeLaFilaDeCategorias
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VistaPrevia
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.apartadoEfectivoDe
import com.jvillada.movi.ui.quickadd.categoriasParaLasPastillasDeAgregar
import com.jvillada.movi.ui.quickadd.pastillasDeLaCategoria
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Lo que se dice cuando apartar (o quitar) no llegó al server por falta de red. */
const val APARTAR_SIN_CONEXION: String = "Para apartar plata necesitas conexión"

/**
 * **Lo que queda disponible en una cuenta**: su saldo menos lo apartado que de verdad pesa
 * ([apartadoEfectivoDe]), nunca por debajo de cero. Es el renglón [DISPONIBLE_EN_LA_CUENTA] del
 * detalle de la cuenta, y lo que «Tus cuentas» del Inicio deja ver debajo del saldo (vuelta 64).
 */
fun libreDeLaCuenta(account: Account, apartados: List<Apartado>): Long =
    (valorEnPesosDe(account) - apartadoEfectivoDe(account, apartados)).coerceAtLeast(0L)

/**
 * El renglón de lo que queda sin apartar en el detalle de la cuenta (vuelta 64). Se llamaba «Libre», y
 * en la misma pantalla «Para qué es esta plata · Libre» quería decir otra cosa (que la cuenta no tiene
 * condición, ver [PLATA_LIBRE]).
 */
const val DISPONIBLE_EN_LA_CUENTA: String = "Disponible en la cuenta"

/**
 * El error de guardar o quitar un apartado, dicho para esta hoja. Se decide por el TIPO, no por el
 * texto: un [ApiException] es que el server contestó (su motivo, o el de siempre para su código);
 * cualquier otra cosa es que el pedido no llegó —sin red, el server caído, un tiempo agotado—, y los
 * apartados no se guardan en el aparato (ver el diseño), así que se dice que hace falta conexión.
 */
fun mensajeAlApartar(e: Throwable): String =
    if (e is ApiException) e.toUserMessage() else APARTAR_SIN_CONEXION

/** Los `testTag` de la hoja de apartar. */
const val TAG_HOJA_DE_APARTAR: String = "apartado:hoja"
const val TAG_TODAS_EN_APARTAR: String = "apartado:todas-las-categorias"
const val TAG_GASTO_DEL_APARTADO: String = "apartado:es-para-un-gasto"
const val TAG_MES_DEL_GASTO: String = "apartado:mes-del-gasto"
const val TAG_COSTO_DEL_GASTO: String = "apartado:costo-del-gasto"

/**
 * El título de la casilla del gasto de un mes puntual: «Es para un gasto que llega en marzo», o sin el
 * mes mientras no se elija.
 */
fun tituloDeLaCasillaDelGasto(mes: PeriodoFinanciero?, hoy: kotlinx.datetime.LocalDate): String =
    if (mes == null) "Es para un gasto de un mes puntual"
    else "Es para un gasto que llega en ${nombreDelMesDelGasto(mes, hoy)}"

/**
 * **Apartar plata** de [account]: crear un apartado, o editar / quitar [existente].
 *
 * El id de uno nuevo se elige **una vez, al abrir la hoja**, y viaja en cada intento: el POST es
 * idempotente por id, así que reintentar tras un corte no deja dos apartados iguales.
 *
 * Vuelta 64:
 * - **Avisa antes de guardar**: debajo del monto, cómo queda «Te quedan» del Inicio con este apartado
 *   ([teQuedanConElApartado]) y, si quedaría negativo, por qué —con el ahorro del período, que se resta
 *   aparte (ver `ElApartadoQueAvisa.kt`)—.
 * - **Habla el sistema**: [EncabezadoDeHoja], [PieDeHoja] con una sola acción (sin «Cancelar»: la X
 *   y el velo cierran) y la categoría con [FilaDeCategorias] —las frecuentes y «Todas ›»—, no la
 *   cuadrícula en línea que la vuelta 42 ya había sacado de Revisar aviso.
 * - **Un gasto de un mes puntual** (el SOAT, el predial): su costo y su mes, y la hoja sugiere cuánto
 *   apartar cada mes ([sugerenciaDelGasto]). Es solo una sugerencia: no es un fijo ni mueve plata.
 *
 * «Quitar» va arriba, junto al título (como en la hoja de un tercero), y pregunta adentro de la hoja;
 * un error —sin red o un rechazo del server— se dice abajo sin perder lo escrito.
 */
@Composable
fun ApartadoSheet(
    account: Account,
    existente: Apartado?,
    onDismiss: () -> Unit,
    onGuardado: (Apartado) -> Unit,
    onQuitado: (String) -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val hoy = remember { epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()) }
    val id = remember { existente?.id ?: newId("apt") }
    var proposito by remember { mutableStateOf(existente?.proposito.orEmpty()) }
    var monto by remember { mutableStateOf(existente?.monto) }
    var categoria by remember { mutableStateOf(existente?.categoria.orEmpty()) }
    var esParaUnGasto by remember { mutableStateOf(existente?.objetivo != null) }
    var mesDelGasto by remember { mutableStateOf(mesDeTexto(existente?.mesDelGasto)) }
    var objetivo by remember { mutableStateOf(existente?.objetivo) }
    var trabajando by remember { mutableStateOf(false) }
    var pidiendoQuitar by remember { mutableStateOf(false) }
    var hojaDeCategorias by remember { mutableStateOf(false) }
    var pedidosDeFoco by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    // Lo que la vista previa necesita: se lee una vez al abrir. Sin eso, la hoja guarda igual.
    var datos by remember { mutableStateOf<DashboardData?>(null) }
    LaunchedEffect(Unit) { datos = datosParaLaVistaPrevia(Clock.System.now().toEpochMilliseconds()) }

    val propositoLimpio = proposito.trim()
    val montoValido = (monto ?: 0L) > 0L
    val problemaDelGasto = if (esParaUnGasto) problemaDelGastoDelApartado(objetivo, mesDelGasto?.prefijo, hoy) else null
    val puedeGuardar = propositoLimpio.isNotEmpty() && montoValido && problemaDelGasto == null && !trabajando

    fun propuesto(): Apartado = Apartado(
        id = id,
        accountId = account.id,
        proposito = propositoLimpio,
        monto = monto ?: 0L,
        categoria = categoria.trim().ifEmpty { null },
        // Uno nuevo cuenta gastos desde que se crea (el server pone la hora); uno editado conserva
        // su arranque.
        desde = existente?.desde ?: 0L,
        // Siempre viajan (también en null): «quitarlo» desmarcando la casilla llega como tal.
        objetivo = if (esParaUnGasto) objetivo else null,
        mesDelGasto = if (esParaUnGasto) mesDelGasto?.prefijo else null,
    )

    fun guardar() {
        if (!puedeGuardar) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                onGuardado(Repositories.wallets.guardarApartado(propuesto()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeAlApartar(e)
                trabajando = false
            }
        }
    }

    fun quitar() {
        val actual = existente ?: return
        if (trabajando) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                Repositories.wallets.quitarApartado(actual.id)
                onQuitado(actual.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeAlApartar(e)
                trabajando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !trabajando) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
                .testTag(TAG_HOJA_DE_APARTAR),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Espacio.xxs),
                verticalAlignment = Alignment.Top,
            ) {
                EncabezadoDeHoja(
                    if (existente == null) "Apartar plata" else "Apartado",
                    subtitulo = "La plata sigue en ${account.name}, pero deja de contar en «Tu plata» y en " +
                        "«Te quedan». No se mueve nada en tu banco.",
                    modifier = Modifier.weight(1f),
                )
                if (existente != null && !pidiendoQuitar) {
                    Spacer(Modifier.width(Espacio.xs))
                    Boton(
                        "Quitar",
                        onClick = { pidiendoQuitar = true; error = null },
                        variante = VarianteDeBoton.Peligro,
                        tamano = TamanoDeBoton.S,
                        habilitado = !trabajando,
                    )
                }
            }

            if (pidiendoQuitar && existente != null) {
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                ConfirmacionEnLinea(
                    pregunta = "¿Quitar «${existente.proposito}»?",
                    detalle = "La plata vuelve a «Tu plata» y a «Te quedan». Tus movimientos no se tocan.",
                    textoConfirmar = "Quitar",
                    ocupado = trabajando,
                    onConfirmar = { quitar() },
                    onCancelar = { pidiendoQuitar = false; error = null },
                )
                error?.let {
                    Spacer(Modifier.height(Espacio.xs))
                    Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
                }
            }

            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Campo(
                proposito, { proposito = it.take(MAX_PROPOSITO_APARTADO) },
                rotulo = "¿Para qué?",
                guia = "Colegio nov-dic",
                teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                habilitado = !trabajando,
            )

            Spacer(Modifier.height(Medida.dentroDeSeccion))
            CampoDeMonto(monto, { monto = it }, rotulo = "Monto", habilitado = !trabajando)

            // **Lo que va a pasar si guardas**: «Te quedan pasa de $1.450.950 a −$549.050».
            val vista = datos?.takeIf { montoValido }?.let { d -> teQuedanConElApartado(d, propuesto(), hoy) }
            if (vista != null) {
                Spacer(Modifier.height(Espacio.xs))
                VistaPrevia(fraseDeLaVistaPrevia(vista), detalle = advertenciaDeLaVistaPrevia(vista))
            }

            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Superficie(
                modifier = Modifier.fillMaxWidth(),
                nivel = NivelDeSuperficie.Capa2,
                relleno = RellenoDeSuperficie.Lista,
            ) {
                FilaDeCategorias(
                    categorias = pastillasDeLaCategoria(
                        categoriasParaLasPastillasDeAgregar(
                            categoriaActual = categoria,
                            tipo = TransactionType.EXPENSE,
                            usadas = UsedCategoriesCache.used,
                            prefs = UsedCategoriesCache.prefs,
                            usos = UsedCategoriesCache.usosRecientes,
                        ),
                        categoria,
                    ),
                    elegida = categoria,
                    // Tocar la elegida otra vez la suelta: la categoría es opcional.
                    onElegir = { categoria = if (it == categoria) "" else it },
                    onTodas = { pedidosDeFoco = 0; hojaDeCategorias = true },
                    onNueva = { pedidosDeFoco += 1; hojaDeCategorias = true },
                    tags = TagsDeLaFilaDeCategorias(todas = TAG_TODAS_EN_APARTAR),
                )
            }
            Spacer(Modifier.height(Espacio.xs))
            Text(
                text = if (categoria.isBlank()) {
                    "Opcional. Si eliges una, cada gasto de esa categoría desde ${account.name} lo va bajando solo."
                } else {
                    "Se descuenta con los gastos de $categoria desde ${account.name}."
                },
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
            if (categoria.isNotBlank()) {
                Boton(
                    "Sin categoría",
                    onClick = { categoria = "" },
                    variante = VarianteDeBoton.Fantasma,
                    tamano = TamanoDeBoton.S,
                    habilitado = !trabajando,
                )
            }

            Spacer(Modifier.height(Medida.dentroDeSeccion))
            ElGastoDelApartado(
                marcado = esParaUnGasto,
                onMarcar = { esParaUnGasto = it },
                mes = mesDelGasto,
                onMes = { mesDelGasto = it },
                objetivo = objetivo,
                onObjetivo = { objetivo = it },
                apartado = conSuRestante(propuesto(), existente).restante,
                hoy = hoy,
                habilitado = !trabajando,
            )

            Spacer(Modifier.height(Medida.entreSecciones))
            PieDeHoja(
                texto = "Guardar",
                onClick = { guardar() },
                habilitado = puedeGuardar,
                razon = when {
                    trabajando -> null
                    propositoLimpio.isEmpty() && montoValido -> "Falta para qué es"
                    problemaDelGasto != null && propositoLimpio.isNotEmpty() && montoValido -> problemaDelGasto
                    else -> null
                },
                cargando = trabajando && !pidiendoQuitar,
                error = error.takeIf { !pidiendoQuitar },
            )
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }

    if (hojaDeCategorias) {
        MarcoDeHoja(onDismiss = { hojaDeCategorias = false }) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                EncabezadoDeHoja("Categoría")
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                SelectorDeCategoria(
                    elegida = categoria,
                    onElegir = {
                        categoria = it
                        hojaDeCategorias = false
                    },
                    tipo = TransactionType.EXPENSE,
                    usadas = UsedCategoriesCache.used,
                    prefs = UsedCategoriesCache.prefs,
                    usos = UsedCategoriesCache.usosRecientes,
                    pedidosDeFoco = pedidosDeFoco,
                )
                Spacer(Modifier.height(Medida.margenDePantalla))
            }
        }
    }
}

/**
 * «Es para un gasto que llega en marzo»: la casilla y, marcada, el mes (este y los once que siguen),
 * cuánto cuesta y cuánto apartar cada mes para llegar.
 */
@Composable
private fun ElGastoDelApartado(
    marcado: Boolean,
    onMarcar: (Boolean) -> Unit,
    mes: PeriodoFinanciero?,
    onMes: (PeriodoFinanciero) -> Unit,
    objetivo: Long?,
    onObjetivo: (Long?) -> Unit,
    apartado: Long,
    hoy: kotlinx.datetime.LocalDate,
    habilitado: Boolean,
) {
    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista, nivel = NivelDeSuperficie.Capa2) {
        Fila(
            titulo = tituloDeLaCasillaDelGasto(mes.takeIf { marcado }, hoy),
            subtitulo = "El SOAT, el predial, una matrícula, una prima",
            final = { Casilla(marcada = marcado) },
            onClick = if (habilitado) ({ onMarcar(!marcado) }) else null,
            modifier = Modifier.semantics { selected = marcado }.testTag(TAG_GASTO_DEL_APARTADO),
        )
    }
    if (!marcado) return
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    Text("¿En qué mes llega?", style = Movi.textos.cuerpo, color = Movi.colores.texto)
    Spacer(Modifier.height(Espacio.xs))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(TAG_MES_DEL_GASTO),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        mesesParaElGasto(hoy).forEach { m ->
            PildoraDeFiltro(pastillaDelMes(m, hoy), seleccionada = m == mes, onClick = { if (habilitado) onMes(m) })
        }
    }
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    CampoDeMonto(
        objetivo, onObjetivo,
        rotulo = "¿Cuánto cuesta?",
        habilitado = habilitado,
        tag = TAG_COSTO_DEL_GASTO,
    )
    sugerenciaDelGasto(objetivo, apartado, mes, hoy)?.let { (principal, detalle) ->
        Spacer(Modifier.height(Espacio.xs))
        VistaPrevia(principal, detalle = detalle)
    }
}
