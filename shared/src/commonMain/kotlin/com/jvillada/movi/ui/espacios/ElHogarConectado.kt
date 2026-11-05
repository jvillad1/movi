package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.platform.ComoSalioElMensaje
import com.jvillada.movi.platform.hojaDeCompartir
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.InvitacionPendiente
import com.jvillada.movi.shared.model.InvitacionVista
import com.jvillada.movi.shared.model.LARGO_DEL_CODIGO
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.LoQueSeDejaDeVer
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.codigoLimpio
import com.jvillada.movi.shared.model.pareceUnCodigo
import com.jvillada.movi.shared.model.sePuedeInvitar
import com.jvillada.movi.shared.model.traeCaracteresQueNoExisten
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.Espacio as EspacioDeDiseno
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.analisis.fechaCorta
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.desplazableConElTeclado
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.ParDeBotones
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/*
 * # El hogar conectado, en pantalla (vuelta 131)
 *
 * - En un grupo de dos sin conectar, la fila «Invita a Caro a Movi» abre la hoja de invitar: qué verá
 *   ella, «Mandar la invitación» (el código por la hoja de compartir) y «Copiar el código». Con una
 *   pendiente, «Invitación enviada · vence el 12 nov» con «Mandar de nuevo» y «Cancelar».
 * - En Grupos, «Tengo un código de invitación» abre la hoja del código: se escribe, se ve a qué grupo
 *   invita y quién, y «Unirme» abre el grupo nuevo.
 * - En el grupo conectado: «Conectado con Caro» bajo el nombre, «Anotó Caro» en lo que anotó ella (sin
 *   quitar ni deshacer), el aviso en «Pagó otro» y «Desconectar de Caro» en «Editar grupo».
 *
 * Las frases son funciones puras, para probarlas sin montar la pantalla. Tuteo, sin voseo.
 */

const val TAG_INVITAR: String = "hogar:invitar"
const val TAG_INVITACION_PENDIENTE: String = "hogar:invitacion-pendiente"
const val TAG_HOJA_DE_INVITAR: String = "hogar:hoja-de-invitar"
const val TAG_TENGO_UN_CODIGO: String = "hogar:tengo-un-codigo"
const val TAG_CAMPO_DEL_CODIGO: String = "hogar:campo-del-codigo"
const val TAG_CONECTADO_CON: String = "hogar:conectado-con"
const val TAG_ANOTO_EL_OTRO: String = "hogar:anoto-el-otro"
const val TAG_DESCONECTAR: String = "hogar:desconectar"
const val TAG_LO_ANOTA_DESDE_SU_MOVI: String = "hogar:lo-anota-desde-su-movi"
const val TAG_DE_ANTES_DE_CONECTARSE: String = "hogar:de-antes-de-conectarse"
const val TAG_YA_LO_MARCO: String = "hogar:ya-lo-marco"
const val TAG_SALDAR_LO_DE_ANTES: String = "hogar:saldar-lo-de-antes"
const val TAG_MENU_DEL_SALDADO_CRUZADO: String = "hogar:menu-del-saldado-cruzado"
const val DESVINCULAR_TU_TRANSFERENCIA: String = "Desvincular tu transferencia"
const val TAG_GRUPOS_DE_DOS: String = "hogar:grupos-de-dos"

/** La fila de un grupo de dos sin conectar: «Invita a Caro a Movi». */
fun tituloDeInvitar(nombre: String): String = "Invita a $nombre a Movi"

/** Debajo de [tituloDeInvitar]. */
const val SUBTITULO_DE_INVITAR: String = "Así anota lo que paga y los dos ven lo mismo"

/** Qué verá la otra persona, en dos renglones (la hoja de invitar y la de unirse). */
val LO_QUE_VERAN_LOS_DOS: List<String> = listOf(
    "Los dos verán los gastos que cada uno reparta en este grupo y lo saldado.",
    "Nada de tus cuentas, saldos ni otros movimientos.",
)

/** Con más de dos personas, la fila no ofrece nada. */
const val POR_AHORA_GRUPOS_DE_DOS: String = "Por ahora se conectan los grupos de dos"

/** La fila de Grupos para unirse con un código. */
const val TENGO_UN_CODIGO: String = "Tengo un código de invitación"

/** «12 nov», en la zona de la app. */
internal fun fechaCortaDe(epoch: Long, zona: TimeZone = AppTimeZone.zone): String = fechaCorta(fechaDeEpoch(epoch, zona))

/**
 * El mensaje que se manda con el código: «Te invito a «Casa con Caro» en Movi. Abre Movi → Tú → Grupos →
 * «Tengo un código de invitación» y escribe K7P3Q9. Vence el 12 nov.». El camino es el de la app: la fila
 * de Tú se llama [TITULO_DE_GRUPOS].
 */
fun mensajeDeLaInvitacion(grupo: String, codigo: String, venceEn: Long, zona: TimeZone = AppTimeZone.zone): String =
    "Te invito a «$grupo» en Movi. Abre Movi → Tú → $TITULO_DE_GRUPOS → «$TENGO_UN_CODIGO» y escribe $codigo. " +
        "Vence el ${fechaCortaDe(venceEn, zona)}."

/** «Invitación enviada · vence el 12 nov». */
fun invitacionEnviada(p: InvitacionPendiente, zona: TimeZone = AppTimeZone.zone): String =
    "Invitación enviada · vence el ${fechaCortaDe(p.venceEn, zona)}"

/** «Juan te invita a «Casa con Caro»». */
fun teInvita(v: InvitacionVista): String = "${v.quienInvita} te invita a «${v.nombreDelGrupo}»"

/** La marca bajo el nombre del grupo: «Conectado con Caro». */
fun conectadoCon(espacio: Espacio): String? = espacio.conexion?.let { c ->
    val enEsteGrupo = "Conectado con ${c.nombre.ifBlank { nombreEnElEspacio(espacio, c.miembroId) }}"
    // Revisión: quién está del otro lado, con el nombre de su perfil de Movi.
    if (c.nombreEnMovi.isBlank()) enEsteGrupo else "$enEsteGrupo · ${c.nombreEnMovi} en Movi"
}

/** «Anotó Caro», o `null` si lo anotó el dueño. */
fun anotoElOtro(espacio: Espacio, anotadoPor: String?): String? =
    anotadoPor?.let { "Anotó ${nombreEnElEspacio(espacio, it)}" }

/**
 * Revisión de la vuelta 131 · en un grupo conectado, «Pagó otro» ya no se ofrece con la otra persona: ella
 * anota lo que paga desde su Movi. Esto es lo que se dice en su lugar.
 */
fun loAnotaDesdeSuMovi(espacio: Espacio): String? = espacio.conexion?.let { c ->
    "${nombreEnElEspacio(espacio, c.miembroId)} anota lo que paga desde su Movi"
}

/**
 * Revisión 2 · **lo de antes de conectarse, aparte**: «Antes de conectarse: Caro te debía $30.000» (o «le
 * debías $30.000 a Caro»), solo en el lado que tiene esa historia. El saldo de la tarjeta es el de lo que ven
 * los dos.
 */
fun lineaDeAntesDeConectarse(detalle: DetalleDelEspacio): String? {
    val c = detalle.espacio.conexion ?: return null
    val neto = detalle.deAntesDeConectarse
    if (neto == 0L) return null
    val otro = nombreEnElEspacio(detalle.espacio, c.miembroId)
    return if (neto > 0L) "Antes de conectarse: $otro te debía ${formatCOP(neto)}"
    else "Antes de conectarse: le debías ${formatCOP(-neto)} a $otro"
}

/** El saldado que deja en cero lo de antes de conectarse (solo de este lado: `deAntes`). */
fun saldadoDeLoDeAntes(detalle: DetalleDelEspacio, id: String, ahora: Long): LiquidacionDelEspacio? {
    val c = detalle.espacio.conexion ?: return null
    val neto = detalle.deAntesDeConectarse
    if (neto == 0L) return null
    return if (neto > 0L) LiquidacionDelEspacio(id = id, de = c.miembroId, a = MIEMBRO_YO, monto = neto, fecha = ahora, deAntes = true)
    else LiquidacionDelEspacio(id = id, de = MIEMBRO_YO, a = c.miembroId, monto = -neto, fecha = ahora, deAntes = true)
}

/**
 * Revisión 2 (N-7) · el aviso cuando la otra persona ya marcó el saldado: «Caro ya lo marcó como saldado.
 * Cuando llegue tu transferencia, Movi te la propone para vincularla.»
 */
fun yaLoMarcoComoSaldado(espacio: Espacio): String =
    "${espacio.conexion?.let { nombreEnElEspacio(espacio, it.miembroId) } ?: "La otra persona"} ya lo marcó como saldado. " +
        "Cuando llegue tu transferencia, Movi te la propone para vincularla."

/** Por qué el código todavía no sirve, para el pie de la hoja. */
fun razonDelCodigo(codigo: String): String? = when {
    traeCaracteresQueNoExisten(codigo) -> "Los códigos de Movi no usan 0, O, 1, I ni L: revisa esa letra"
    !pareceUnCodigo(codigo) -> "El código tiene $LARGO_DEL_CODIGO letras y números"
    else -> null
}

/** El saldo del dueño dicho corto, para «de $X a $Y»: «$70.000 a tu favor», «$20.000 a favor de Caro», «$0». */
fun saldoCorto(neto: Long, otro: String): String = when {
    neto > 0L -> "${formatCOP(neto)} a tu favor"
    neto < 0L -> "${formatCOP(-neto)} a favor de $otro"
    else -> formatCOP(0L)
}

/**
 * La pregunta de «Desconectar»: cuántos gastos (y saldados) de la otra persona dejarán de verse y cómo
 * queda el saldo, de $X a $Y. Nada se borra.
 */
fun confirmacionDeDesconectar(espacio: Espacio, r: LoQueSeDejaDeVer): Pair<String, String> {
    val otro = espacio.conexion?.let { nombreEnElEspacio(espacio, it.miembroId) } ?: "la otra persona"
    val gastos = if (r.gastos == 1) "1 gasto" else "${r.gastos} gastos"
    val saldados = when (r.saldados) {
        0 -> ""
        1 -> " y 1 saldado"
        else -> " y ${r.saldados} saldados"
    }
    val loQueSeVa = if (r.gastos == 0 && r.saldados == 0) {
        "No hay gastos de $otro en este grupo."
    } else {
        "Dejarás de ver $gastos$saldados que anotó $otro."
    }
    val saldo = if (r.netoAntes == r.netoDespues) {
        "El saldo no cambia."
    } else {
        "El saldo pasa de ${saldoCorto(r.netoAntes, otro)} a ${saldoCorto(r.netoDespues, otro)}."
    }
    return "¿Desconectar de $otro?" to "$loQueSeVa $saldo Nada se borra: cada uno sigue con lo que anotó."
}

/**
 * **La fila de invitar**, en el detalle de un grupo sin conectar. Con más de dos personas dice
 * [POR_AHORA_GRUPOS_DE_DOS] y no ofrece nada; conectado, no se pinta.
 */
@Composable
internal fun FilaDeInvitar(espacio: Espacio) {
    if (espacio.conexion != null) return
    val otros = espacio.miembros.filter { it.id != MIEMBRO_YO }
    if (!sePuedeInvitar(espacio)) {
        if (otros.size > 1) {
            Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_GRUPOS_DE_DOS), relleno = RellenoDeSuperficie.Lista) {
                Fila(
                    titulo = POR_AHORA_GRUPOS_DE_DOS,
                    inicio = { IconoEnCirculo(Icons.Rounded.Link, tono = TonoDeIcono.Neutro) },
                    lineasDelTitulo = 2,
                )
            }
        }
        return
    }
    val nombre = otros.single().nombre.ifBlank { nombreEnElEspacio(espacio, otros.single().id) }
    val coroutine = rememberCoroutineScope()
    var recarga by remember(espacio.id) { mutableStateOf(0) }
    var pendiente by remember(espacio.id) { mutableStateOf<InvitacionPendiente?>(null) }
    var invitando by remember(espacio.id) { mutableStateOf(false) }
    var trabajando by remember(espacio.id) { mutableStateOf(false) }
    var error by remember(espacio.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(espacio.id, recarga) {
        pendiente = runCatching { Repositories.wallets.getInvitacionPendiente(espacio.id) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
    }
    val actual = pendiente
    Column {
        if (actual == null) {
            Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                Fila(
                    titulo = tituloDeInvitar(nombre),
                    subtitulo = SUBTITULO_DE_INVITAR,
                    lineasDelSubtitulo = 2,
                    inicio = { IconoEnCirculo(Icons.Rounded.PersonAdd, tono = TonoDeIcono.Info) },
                    onClick = { invitando = true },
                    onClickLabel = "Invitar",
                    modifier = Modifier.testTag(TAG_INVITAR),
                )
            }
        } else {
            Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_INVITACION_PENDIENTE)) {
                Fila(
                    titulo = invitacionEnviada(actual),
                    lineasDelTitulo = 2,
                    inicio = { IconoEnCirculo(Icons.Rounded.Schedule, tono = TonoDeIcono.Neutro) },
                )
                Spacer(Modifier.height(EspacioDeDiseno.xs))
                // Un solo amarillo por vista: el del saldo. Estos dos van sin relleno de marca.
                ParDeBotones(
                    principal = {
                        Boton(
                            "Mandar de nuevo",
                            onClick = { invitando = true },
                            variante = VarianteDeBoton.Secundario,
                            tamano = TamanoDeBoton.S,
                            habilitado = !trabajando,
                        )
                    },
                    secundario = {
                        Boton(
                            "Cancelar",
                            onClick = {
                                if (!trabajando) {
                                    trabajando = true
                                    error = null
                                    coroutine.launch {
                                        try {
                                            Repositories.wallets.cancelarInvitacion(espacio.id)
                                            pendiente = null
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Throwable) {
                                            error = mensajeDelEspacio(e)
                                        } finally {
                                            trabajando = false
                                        }
                                    }
                                }
                            },
                            variante = VarianteDeBoton.Fantasma,
                            tamano = TamanoDeBoton.S,
                            cargando = trabajando,
                        )
                    },
                )
            }
        }
        error?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
    }
    if (invitando) {
        HojaDeInvitar(
            espacio = espacio,
            nombre = nombre,
            onDismiss = {
                invitando = false
                recarga++
            },
        )
    }
}

/**
 * **La hoja de invitar**: qué verá la otra persona, «Mandar la invitación» (genera el código y lo manda
 * por la hoja de compartir) y «Copiar el código». El código se genera una vez por hoja: mandar y copiar
 * usan el mismo; abrirla de nuevo («Mandar de nuevo») genera otro, que reemplaza al anterior.
 */
@Composable
internal fun HojaDeInvitar(espacio: Espacio, nombre: String, onDismiss: () -> Unit) {
    val coroutine = rememberCoroutineScope()
    val hoja = hojaDeCompartir()
    var codigo by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }

    fun conElCodigo(hacer: (String, Long) -> Unit) {
        if (trabajando) return
        val ya = codigo
        if (ya != null) {
            hacer(ya.first, ya.second)
            return
        }
        trabajando = true
        error = null
        coroutine.launch {
            try {
                val creada = Repositories.wallets.crearInvitacion(espacio.id)
                codigo = creada.codigo to creada.venceEn
                hacer(creada.codigo, creada.venceEn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
            } finally {
                trabajando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !trabajando) {
        Column(modifier = Modifier.desplazableConElTeclado().weight(1f, fill = false).testTag(TAG_HOJA_DE_INVITAR)) {
            EncabezadoDeHoja(tituloDeInvitar(nombre), subtitulo = SUBTITULO_DE_INVITAR)
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            LoQueVeranLosDos()
            aviso?.let {
                Spacer(Modifier.height(EspacioDeDiseno.xs))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            PieDeHoja(
                "Mandar la invitación",
                onClick = {
                    conElCodigo { c, vence ->
                        val como = hoja.mandarMensaje(mensajeDeLaInvitacion(espacio.nombre, c, vence))
                        if (como == ComoSalioElMensaje.COPIADO) aviso = "Mensaje copiado: pégalo en un chat con $nombre." else onDismiss()
                    }
                },
                cargando = trabajando,
                error = error,
                secundaria = "Copiar el código",
                onSecundaria = {
                    conElCodigo { c, _ ->
                        hoja.copiar(c)
                        aviso = "Código copiado: $c"
                    }
                },
                secundariaHabilitada = !trabajando,
            )
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}

/** Los dos renglones de qué verán los dos. */
@Composable
private fun LoQueVeranLosDos() {
    Column {
        LO_QUE_VERAN_LOS_DOS.forEach { renglon ->
            Text(renglon, style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Spacer(Modifier.height(EspacioDeDiseno.xs))
        }
    }
}

/** **«Tengo un código de invitación»**, en Grupos (también sin grupos). [onUnido] recibe el grupo nuevo. */
@Composable
internal fun FilaTengoUnCodigo(onUnido: (Espacio) -> Unit) {
    var abierta by remember { mutableStateOf(false) }
    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
        Fila(
            titulo = TENGO_UN_CODIGO,
            subtitulo = "Únete al grupo que alguien te compartió",
            lineasDelSubtitulo = 2,
            inicio = { IconoEnCirculo(Icons.Rounded.Key, tono = TonoDeIcono.Neutro) },
            onClick = { abierta = true },
            modifier = Modifier.testTag(TAG_TENGO_UN_CODIGO),
        )
    }
    if (abierta) {
        HojaTengoUnCodigo(
            onDismiss = { abierta = false },
            onUnido = {
                abierta = false
                onUnido(it)
            },
        )
    }
}

/**
 * La hoja del código: se escribe (minúsculas, espacios y guiones se limpian), «Ver la invitación» dice a
 * qué grupo invita y quién, y «Unirme» se une. «Ahora no» cierra sin unirse.
 */
@Composable
internal fun HojaTengoUnCodigo(onDismiss: () -> Unit, onUnido: (Espacio) -> Unit) {
    val coroutine = rememberCoroutineScope()
    var codigo by remember { mutableStateOf("") }
    var vista by remember { mutableStateOf<InvitacionVista?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun hacer(bloque: suspend () -> Unit) {
        if (trabajando) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                bloque()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
            } finally {
                trabajando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !trabajando) {
        Column(modifier = Modifier.desplazableConElTeclado().weight(1f, fill = false)) {
            val v = vista
            if (v == null) {
                EncabezadoDeHoja(TENGO_UN_CODIGO, subtitulo = "Escribe el código que te mandaron.")
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Campo(
                    valor = codigo,
                    alCambiar = { codigo = codigoLimpio(it).take(LARGO_DEL_CODIGO) },
                    rotulo = "Código",
                    guia = "K7P3Q9",
                    teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    habilitado = !trabajando,
                    tag = TAG_CAMPO_DEL_CODIGO,
                )
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                PieDeHoja(
                    "Ver la invitación",
                    onClick = { hacer { vista = Repositories.wallets.getInvitacion(codigo) } },
                    habilitado = pareceUnCodigo(codigo) && !trabajando,
                    razon = razonDelCodigo(codigo),
                    cargando = trabajando,
                    error = error,
                )
            } else {
                EncabezadoDeHoja(teInvita(v))
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                LoQueVeranLosDos()
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                PieDeHoja(
                    "Unirme",
                    onClick = { hacer { onUnido(Repositories.wallets.aceptarInvitacion(codigo)) } },
                    cargando = trabajando,
                    error = error,
                    secundaria = "Ahora no",
                    onSecundaria = onDismiss,
                    secundariaHabilitada = !trabajando,
                )
            }
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}

/** La marca bajo el nombre del grupo, si está conectado. */
@Composable
internal fun MarcaDeConectado(espacio: Espacio) {
    val texto = conectadoCon(espacio) ?: return
    Text(texto, style = Movi.textos.apoyo, color = Movi.colores.enlace, modifier = Modifier.testTag(TAG_CONECTADO_CON))
}

/** ¿[g] lo anotó la otra persona de un grupo conectado? (No se quita ni se edita desde acá.) */
internal fun loAnotoElOtro(g: GastoDelEspacio): Boolean = g.anotadoPor != null

/** ¿[l] lo anotó la otra persona? (No se deshace desde acá.) */
internal fun loAnotoElOtro(l: LiquidacionDelEspacio): Boolean = l.anotadoPor != null

/** El «Desconectar de Caro» de «Editar grupo»: un botón sin amarillo, que pregunta antes. */
@Composable
internal fun BotonDeDesconectar(detalle: DetalleDelEspacio, onPreguntar: () -> Unit, habilitado: Boolean) {
    val c = detalle.espacio.conexion ?: return
    Boton(
        "Desconectar de ${nombreEnElEspacio(detalle.espacio, c.miembroId)}",
        onClick = onPreguntar,
        variante = VarianteDeBoton.Fantasma,
        habilitado = habilitado,
        anchoCompleto = true,
        modifier = Modifier.testTag(TAG_DESCONECTAR),
    )
}
