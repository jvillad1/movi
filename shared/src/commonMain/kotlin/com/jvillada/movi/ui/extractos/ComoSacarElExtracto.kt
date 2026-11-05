package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.GuiaDelExtracto
import com.jvillada.movi.shared.model.LecturaDelExtracto
import com.jvillada.movi.shared.model.Marca
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.SUBIR_EL_EXTRACTO_DE_LA_GUIA
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.bancosParaElegir
import com.jvillada.movi.shared.model.guiaDelExtracto
import com.jvillada.movi.shared.model.guiaGenerica
import com.jvillada.movi.shared.model.guiasDelBanco
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.notaDeLaFuente
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.queSubirDe
import com.jvillada.movi.shared.model.rangoIdealDelExtracto
import com.jvillada.movi.shared.model.rotuloDelProducto
import com.jvillada.movi.shared.model.textoDeLaLectura
import com.jvillada.movi.shared.model.tituloDeLaGuiaDelExtracto
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.CirculoDeMarca
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.TamanoDeAvatar
import com.jvillada.movi.ui.components.desplazableConElTeclado
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.CampoDeBusqueda
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.LocalAtajosDeAgregar
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/** La hoja de la guía. */
const val TAG_HOJA_DE_COMO_SACAR_EL_EXTRACTO: String = "hoja-de-como-sacar-el-extracto"

/** La fila que la abre, en el detalle de una cuenta y en la revisión de un extracto. */
const val TAG_FILA_DE_COMO_SACAR_EL_EXTRACTO: String = "fila-de-como-sacar-el-extracto"

/** El renglón del rango ideal, dentro de la hoja. */
const val TAG_RANGO_IDEAL_DEL_EXTRACTO: String = "rango-ideal-del-extracto"

/** La pastilla «Movi lo lee al instante» / «Con IA · puede tardar un poco». */
const val TAG_LECTURA_DE_LA_GUIA: String = "guia-del-extracto-lectura"

/** El selector de producto (solo si el banco tiene guías distintas por producto). */
const val TAG_PRODUCTOS_DE_LA_GUIA: String = "guia-del-extracto-productos"

/** La lista de bancos, cuando no se sabe de cuál es el extracto. */
const val TAG_LISTA_DE_BANCOS_DE_LA_GUIA: String = "guia-del-extracto-bancos"

/** La búsqueda de la lista de bancos. */
const val TAG_BUSCAR_BANCO_DE_LA_GUIA: String = "guia-del-extracto-buscar-banco"

/** «Mi banco no está»: la guía genérica. */
const val TAG_MI_BANCO_NO_ESTA: String = "guia-del-extracto-mi-banco-no-esta"

/** «Otro banco»: vuelve a la lista desde una guía. */
const val TAG_OTRO_BANCO_DE_LA_GUIA: String = "guia-del-extracto-otro-banco"

/** El botón amarillo «Subir el extracto». */
const val TAG_SUBIR_DESDE_LA_GUIA: String = "guia-del-extracto-subir"

/** Un enlace que abre la guía (la hoja de subida, la propuesta, el error de lectura). */
const val TAG_ENLACE_A_LA_GUIA: String = "enlace-a-la-guia-del-extracto"

const val ELIGE_TU_BANCO: String = "Elige tu banco y te decimos qué archivo bajar."
const val BUSCA_TU_BANCO: String = "Busca tu banco"
const val MI_BANCO_NO_ESTA: String = "Mi banco no está"
const val OTRO_BANCO_EN_LA_GUIA: String = "Otro banco"

/**
 * **Desde cuándo bajar los movimientos** (vuelta 107): el inicio del período del dueño (su «corte», el
 * 23 de octubre si su período arrancó ese día) o el corte de la tarjeta, el más viejo de los dos, para
 * que el extracto cubra lo que Movi no pudo ver desde ahí. Sin ninguno de los dos, `null`. Pura.
 */
fun desdeDelExtracto(ajustes: PeriodSettings?, corteDeLaTarjeta: LocalDate?, ahoraMillis: Long): LocalDate? {
    val delPeriodo = ajustes?.let { inicioDelPeriodo(periodoActual(ahoraMillis, it), it) }
    return listOfNotNull(delPeriodo, corteDeLaTarjeta).minOrNull()
}

/**
 * **Los bancos de la lista** (vuelta 130): todos los del catálogo de marcas que pueden tener un extracto
 * —el mismo «Ver todos los bancos» de crear una cuenta, con la AMEX—, filtrados por [consulta] sin
 * tildes ni mayúsculas. Un banco sin guía propia abre la genérica con su nombre. Pura.
 */
fun bancosDeLaGuia(consulta: String): List<Marca> = bancosParaElegir(consulta, paraTarjetas = true)

/**
 * # «Cómo bajar tu extracto» (vuelta 107; banco por banco desde la vuelta 130)
 *
 * Con [inicial], la guía de ese banco y producto; sin ella, primero la lista de bancos con buscador (el
 * patrón de «Ver todos los bancos») y «Mi banco no está». La guía dice, en este orden: el banco, la
 * pastilla de cómo lo lee Movi («Movi lo lee al instante» / «Con IA · puede tardar un poco»), dónde se
 * baja, los pasos numerados (aquí sí son un orden), el rango ideal si el banco deja elegir fechas, qué
 * archivo subir, la clave, si llega por correo, la segunda forma (el extracto mensual de Bancolombia) y
 * «Si no lo encuentras». Si el banco tiene guías distintas por producto, píldoras para elegirlo.
 *
 * **Un solo amarillo**: «Subir el extracto» ([onSubir]; sin él, el atajo «Subir comprobante» de la
 * cáscara; fuera de la app, sin botón). Todo lo demás es texto o fantasma. No escribe nada.
 *
 * El rango sale del perfil (el inicio del período) y de [corteDeLaTarjeta]; mientras el perfil no llega el
 * renglón dice el rango sin la fecha, con el mismo alto: nada salta.
 */
@Composable
fun HojaDeComoSacarElExtracto(
    inicial: GuiaDelExtracto?,
    onDismiss: () -> Unit,
    corteDeLaTarjeta: LocalDate? = null,
    onSubir: (() -> Unit)? = null,
) {
    var guia by remember(inicial) { mutableStateOf(inicial) }
    val atajos = LocalAtajosDeAgregar.current
    val subir: (() -> Unit)? = onSubir ?: atajos?.let { a -> { a(AtajoDeAgregar.SUBIR_COMPROBANTE) } }
    MarcoDeHoja(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .desplazableConElTeclado()
                .weight(1f, fill = false)
                .testTag(TAG_HOJA_DE_COMO_SACAR_EL_EXTRACTO),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            when (val g = guia) {
                null -> ListaDeBancosDeLaGuia(onElegir = { guia = it })
                else -> CuerpoDeLaGuia(g, corteDeLaTarjeta, onCambiar = { guia = it }, onOtroBanco = { guia = null })
            }
            Spacer(Modifier.height(Espacio.xs))
        }
        // Un crédito que Movi todavía no importa no ofrece subir nada (revisión de la vuelta 130).
        val conQueSubir = guia?.let { com.jvillada.movi.shared.model.seOfreceSubir(it) } == true
        if (conQueSubir && subir != null) {
            Spacer(Modifier.height(Espacio.s))
            Boton(
                SUBIR_EL_EXTRACTO_DE_LA_GUIA,
                onClick = { onDismiss(); subir() },
                anchoCompleto = true,
                modifier = Modifier.testTag(TAG_SUBIR_DESDE_LA_GUIA),
            )
        }
        Spacer(Modifier.height(Medida.entreSecciones))
    }
}

/** La lista de bancos con su buscador, y «Mi banco no está». */
@Composable
private fun ListaDeBancosDeLaGuia(onElegir: (GuiaDelExtracto) -> Unit) {
    var consulta by remember { mutableStateOf("") }
    EncabezadoDeHoja(com.jvillada.movi.shared.model.COMO_BAJAR_TUS_EXTRACTOS, subtitulo = ELIGE_TU_BANCO)
    CampoDeBusqueda(
        consulta,
        { consulta = it },
        guia = BUSCA_TU_BANCO,
        nivel = NivelDeSuperficie.Capa2,
        tag = TAG_BUSCAR_BANCO_DE_LA_GUIA,
    )
    Column(Modifier.fillMaxWidth().testTag(TAG_LISTA_DE_BANCOS_DE_LA_GUIA)) {
        bancosDeLaGuia(consulta).forEach { marca ->
            Fila(
                titulo = marca.nombre,
                // Vuelta 79: los avatares de banco van neutros (un solo amarillo por vista).
                inicio = { CirculoDeMarca(marca, tamano = TamanoDeAvatar.Fila, comoBanco = true) },
                onClick = { onElegir(guiaDelExtracto(marca.id, null, marca.nombre)) },
            )
        }
        Fila(
            titulo = MI_BANCO_NO_ESTA,
            inicio = { IconoEnCirculo(Icons.Rounded.HelpOutline, tono = TonoDeIcono.Neutro) },
            onClick = { onElegir(guiaGenerica(null, null, null)) },
            modifier = Modifier.testTag(TAG_MI_BANCO_NO_ESTA),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CuerpoDeLaGuia(
    guia: GuiaDelExtracto,
    corteDeLaTarjeta: LocalDate?,
    onCambiar: (GuiaDelExtracto) -> Unit,
    onOtroBanco: () -> Unit,
) {
    var ajustes by remember { mutableStateOf<PeriodSettings?>(null) }
    if (guia.conRango) {
        LaunchedEffect(Unit) {
            ajustes = try {
                Repositories.wallets.getUserProfile().ajustesDelPeriodo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Sin perfil, el rango del período por defecto (corte el 1): mejor una fecha aproximada
                // que ninguna. Throwable: en la web un fetch caído llega como `kotlin.Error`.
                PeriodSettings()
            }
        }
    }
    EncabezadoDeHoja(tituloDeLaGuiaDelExtracto(guia))
    Pildora(
        textoDeLaLectura(guia.lectura),
        tono = when (guia.lectura) {
            LecturaDelExtracto.AL_INSTANTE, LecturaDelExtracto.SOLO_EL_SALDO -> TonoDePildora.Exito
            LecturaDelExtracto.NO_SE_IMPORTA -> TonoDePildora.Aviso
            LecturaDelExtracto.CON_IA, LecturaDelExtracto.SOLO_ALGUNOS_CREDITOS -> TonoDePildora.Neutra
        },
        modifier = Modifier.testTag(TAG_LECTURA_DE_LA_GUIA),
    )
    // El producto, si el banco tiene guías distintas (la cuenta y la tarjeta de Bancolombia).
    val delBanco = guiasDelBanco(guia.bancoId)
    if (delBanco.size > 1) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag(TAG_PRODUCTOS_DE_LA_GUIA),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            delBanco.forEach { otra ->
                PildoraDeFiltro(rotuloDelProducto(otra), seleccionada = otra == guia, onClick = { onCambiar(otra) })
            }
        }
    }
    Texto(guia.donde, apagado = true)
    guia.aviso?.let { Texto(it, color = Movi.colores.aviso) }
    Pasos(guia.pasos)
    if (guia.conRango) {
        val desde = desdeDelExtracto(ajustes, corteDeLaTarjeta, Clock.System.now().toEpochMilliseconds())
        Text(
            if (desde != null) rangoIdealDelExtracto(desde, hoyEnAppZone()) else "Rango ideal: desde tu último corte hasta hoy.",
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
            modifier = Modifier.testTag(TAG_RANGO_IDEAL_DEL_EXTRACTO),
        )
    }
    guia.limite?.let { Texto(it, apagado = true) }
    Apartado(QUE_ARCHIVO_SUBIR, queSubirDe(guia))
    guia.clave?.let { Apartado(LA_CLAVE, it) }
    guia.porCorreo?.let { Apartado(POR_CORREO, it) }
    guia.porSms?.let { Apartado(POR_SMS, it) }
    guia.otraForma?.let { otra ->
        Titulito(otra.titulo)
        Pasos(otra.pasos)
        Texto(otra.queSubir)
        otra.clave?.let { Texto(it) }
        otra.limite?.let { Texto(it, apagado = true) }
    }
    Apartado(SI_NO_LO_ENCUENTRAS, guia.siNoLoEncuentras)
    notaDeLaFuente(guia)?.let { Texto(it, apagado = true) }
    Boton(
        OTRO_BANCO_EN_LA_GUIA,
        onClick = onOtroBanco,
        variante = VarianteDeBoton.Fantasma,
        tamano = TamanoDeBoton.S,
        modifier = Modifier.testTag(TAG_OTRO_BANCO_DE_LA_GUIA),
    )
}

const val QUE_ARCHIVO_SUBIR: String = "Qué archivo subir"
const val LA_CLAVE: String = "La clave"
const val POR_CORREO: String = "Por correo"
const val POR_SMS: String = "Por SMS"
const val SI_NO_LO_ENCUENTRAS: String = "Si no lo encuentras"

@Composable
private fun Titulito(texto: String) {
    Text(texto, style = Movi.textos.cuerpoFuerte, color = Movi.colores.texto)
}

@Composable
private fun Texto(texto: String, apagado: Boolean = false, color: androidx.compose.ui.graphics.Color? = null) {
    Text(
        texto,
        style = if (apagado) Movi.textos.apoyo else Movi.textos.cuerpo,
        color = color ?: if (apagado) Movi.colores.textoMedio else Movi.colores.texto,
    )
}

@Composable
private fun Apartado(titulo: String, texto: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
        Titulito(titulo)
        Texto(texto)
    }
}

/** Los pasos, numerados: aquí la numeración sí es un orden. */
@Composable
private fun Pasos(pasos: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        pasos.forEachIndexed { i, paso ->
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                Text("${i + 1}.", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                Text(paso, style = Movi.textos.cuerpo, color = Movi.colores.texto)
            }
        }
    }
}

/**
 * La fila que abre la guía: «Sube el extracto de este mes · Cómo bajarlo de Bancolombia» en el detalle
 * de una cuenta, o el título que quiera quien la usa ([titulo]).
 */
@Composable
fun FilaDeComoSacarElExtracto(
    guia: GuiaDelExtracto,
    titulo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Fila(
        titulo = titulo,
        subtitulo = "Cómo bajarlo de ${guia.banco}",
        inicio = { IconoEnCirculo(Icons.Rounded.Download, tono = TonoDeIcono.Neutro) },
        onClick = onClick,
        modifier = modifier.testTag(TAG_FILA_DE_COMO_SACAR_EL_EXTRACTO),
    )
}

/**
 * **El enlace que abre la guía** (vuelta 130): «¿Cómo bajo mi extracto?» en la hoja de subida, «Cómo bajar
 * el archivo que Movi lee mejor» bajo un extracto que no se pudo leer. Un botón fantasma: nunca le quita
 * el amarillo a la acción de la vista.
 */
@Composable
fun EnlaceALaGuiaDelExtracto(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Boton(
        texto,
        onClick = onClick,
        variante = VarianteDeBoton.Fantasma,
        tamano = TamanoDeBoton.S,
        modifier = modifier.testTag(TAG_ENLACE_A_LA_GUIA),
    )
}

/**
 * **La guía pedida**, para una pantalla que la abre: con [inicial] abre ese banco y producto; con `null`,
 * la lista de bancos. (Un `GuiaDelExtracto?` solo no distingue «cerrada» de «abierta en la lista».)
 */
class GuiaPedida(val inicial: GuiaDelExtracto?)

/**
 * Vuelta 130 · **la guía que abre «¿Cómo bajo mi extracto?» en la hoja de subida**: la de la cuenta
 * propuesta o elegida, si su nombre dice el banco («Nu Ahorros» → Nu · cuenta; una tarjeta Nu → Nu ·
 * tarjeta). Sin cuenta, o con una que no dice el banco, `null`: la hoja abre la lista de bancos. Pura.
 */
fun guiaParaLaHojaDeSubida(
    cuentaId: String?,
    cuentas: List<com.jvillada.movi.shared.model.Account>,
    /**
     * Revisión de la vuelta 130: el banco de cada tarjeta por su cuenta (`CardTerms.bank`): «Master Black
     * 3684» no dice que es de Bancolombia; sus condiciones, sí.
     */
    bancosDeLasTarjetas: Map<String, String> = emptyMap(),
): GuiaDelExtracto? =
    cuentaId?.let { id -> cuentas.firstOrNull { it.id == id } }
        ?.let { com.jvillada.movi.shared.model.guiaDelExtractoDeLaCuenta(it, bancosDeLasTarjetas[it.id]) }

/**
 * Vuelta 130 · **la guía de un extracto que no se pudo leer**: la de su cuenta, si se sabe; si no, la que
 * nombra el archivo ([com.jvillada.movi.shared.model.guiaDelExtractoDelArchivo]); si tampoco, `null` (la
 * lista). Pura.
 */
fun guiaDelPapelQueNoSeLeyo(
    nombre: String,
    cuenta: com.jvillada.movi.shared.model.Account? = null,
    bancoDeLaTarjeta: String? = null,
): GuiaDelExtracto? =
    cuenta?.let { com.jvillada.movi.shared.model.guiaDelExtractoDeLaCuenta(it, bancoDeLaTarjeta) }
        ?: com.jvillada.movi.shared.model.guiaDelExtractoDelArchivo(nombre)
