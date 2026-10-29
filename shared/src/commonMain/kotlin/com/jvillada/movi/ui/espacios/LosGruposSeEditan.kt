package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
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
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MAX_NOMBRE_DE_ESPACIO
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.PropuestaDeSaldar
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.huellaDeLaPropuestaDeSaldar
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.tipoOPersona
import com.jvillada.movi.theme.Espacio as EspacioDeDiseno
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.AvatarDePersona
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.destinos.DestinoSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * # Vuelta 66 · los grupos se crean, se nombran, se borran, y llevan su historial
 *
 * - **Nuevo grupo** ([HojaNuevoEspacio]): el nombre y las personas (una o varias), con «Agregar
 *   persona» ahí mismo (la hoja de siempre de «Personas y comercios»). Si ya hay un grupo con ese
 *   nombre y esas personas, lo avisa ([avisoDeGrupoRepetido]). El reparto sigue siendo por partes
 *   iguales: el modelo ya admite porcentajes, pero elegirlos no entra en esta vuelta.
 * - **Editar el grupo** ([HojaEditarGrupo]): renombrar (el upsert de siempre) y borrar, con su pregunta
 *   antes ([confirmacionDeBorrarElGrupo]): dice qué pasa con los gastos y el saldo.
 * - **Pagos entre ustedes** ([PagosEntreUstedes]): cada saldado, con su transferencia o «Esperando la
 *   transferencia», y «Deshacer».
 * - **La propuesta de saldar** ([TarjetaDeSaldar]): la misma en el grupo, en Por revisar y en la campana.
 */

/** Tags para las pruebas. */
const val TAG_NOMBRE_DEL_GRUPO: String = "grupos:nombre"
const val TAG_AGREGAR_PERSONA: String = "grupos:agregar-persona"
const val TAG_AVISO_DE_GRUPO_REPETIDO: String = "grupos:repetido"
const val TAG_CREAR_GRUPO: String = "grupos:crear"
const val TAG_EDITAR_GRUPO: String = "grupos:editar"
const val TAG_BORRAR_GRUPO: String = "grupos:borrar"
const val TAG_PAGO_ENTRE_USTEDES: String = "grupos:pago"
const val TAG_DESHACER_PAGO: String = "grupos:deshacer-pago"
const val TAG_PROPUESTA_DE_SALDAR: String = "grupos:propuesta-de-saldar"

/** El título de la hoja de un grupo nuevo. */
const val TITULO_NUEVO_GRUPO: String = "Nuevo grupo"

/**
 * **Crear un grupo**: el nombre y con quién. Las personas son las de «Personas y comercios» (no los
 * comercios); «Agregar persona» abre su hoja encima y, al guardar, la deja elegida. Movi sugiere el
 * nombre («Casa con Caro») mientras el dueño no escriba uno.
 */
@Composable
fun HojaNuevoEspacio(onDismiss: () -> Unit, onCreado: (Espacio) -> Unit, onIrATerceros: () -> Unit) {
    val coroutine = rememberCoroutineScope()
    var personas by remember { mutableStateOf<List<DestinoConocido>?>(null) }
    var existentes by remember { mutableStateOf<List<Espacio>>(emptyList()) }
    var cuentas by remember { mutableStateOf<List<Account>>(emptyList()) }
    var elegidas by remember { mutableStateOf<List<String>>(emptyList()) }
    var nombre by remember { mutableStateOf("") }
    var nombreEscrito by remember { mutableStateOf(false) }
    var agregando by remember { mutableStateOf(false) }
    var lecturas by remember { mutableStateOf(0) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val id = remember { newId("esp") }

    LaunchedEffect(lecturas) {
        val antes = personas?.map { it.id }?.toSet()
        val leidas = runCatching { Repositories.wallets.getDestinos() }.getOrDefault(emptyList())
            .filter { it.tipoOPersona() == TipoDeTercero.PERSONA }
        // La que se acaba de agregar con «Agregar persona» queda elegida.
        if (antes != null) elegidas = elegidas + leidas.map { it.id }.filter { it !in antes }
        personas = leidas
        existentes = runCatching { Repositories.wallets.getEspacios() }.getOrDefault(emptyList())
        if (cuentas.isEmpty()) cuentas = runCatching { Repositories.wallets.getAccounts() }.getOrDefault(emptyList())
    }
    val conQuien = personas.orEmpty().filter { it.id in elegidas }
    val sugerido = nombreSugeridoDelGrupo(conQuien)
    val nombreFinal = (if (nombreEscrito) nombre else sugerido).trim()
    val repetido = avisoDeGrupoRepetido(nombreFinal, conQuien, existentes)
    val puede = conQuien.isNotEmpty() && nombreFinal.isNotEmpty() && !trabajando

    fun crear() {
        if (!puede) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                val espacio = Repositories.wallets.guardarEspacio(
                    Espacio(
                        id = id,
                        nombre = nombreFinal.take(MAX_NOMBRE_DE_ESPACIO),
                        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO)) + conQuien.map { MiembroDelEspacio(it.id, it.nombre) },
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
            EncabezadoDeHoja(TITULO_NUEVO_GRUPO, subtitulo = "Partes iguales. Solo tu parte cuenta en tus gastos.")
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Campo(
                valor = if (nombreEscrito) nombre else sugerido,
                alCambiar = {
                    nombre = it.take(MAX_NOMBRE_DE_ESPACIO)
                    nombreEscrito = true
                },
                rotulo = "Nombre",
                ejemplo = "Casa con Caro",
                tag = TAG_NOMBRE_DEL_GRUPO,
            )
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Text("¿Con quién compartes?", style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Spacer(Modifier.height(EspacioDeDiseno.xs))
            val lista = personas
            Superficie(nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista) {
                lista.orEmpty().forEachIndexed { i, d ->
                    if (i > 0) Separador()
                    val elegida = d.id in elegidas
                    Fila(
                        titulo = d.nombre,
                        inicio = { AvatarDePersona(d.nombre) },
                        final = if (elegida) {
                            { Icon(Icons.Rounded.Check, contentDescription = "Elegida", tint = Movi.colores.texto) }
                        } else null,
                        onClick = { elegidas = if (elegida) elegidas - d.id else elegidas + d.id },
                        onClickLabel = if (elegida) "Quitar a ${d.nombre}" else "Compartir con ${d.nombre}",
                    )
                }
                if (lista != null) {
                    if (lista.isNotEmpty()) Separador()
                    Fila(
                        titulo = "Agregar persona",
                        subtitulo = if (lista.isEmpty()) "Con su número o su llave, para reconocer sus transferencias" else null,
                        inicio = { com.jvillada.movi.ui.components.ds.IconoEnCirculo(Icons.Rounded.PersonAdd) },
                        onClick = { agregando = true },
                        modifier = Modifier.testTag(TAG_AGREGAR_PERSONA),
                    )
                }
            }
            repetido?.let {
                Spacer(Modifier.height(EspacioDeDiseno.xs))
                Text(it, style = Movi.textos.apoyo, color = Movi.colores.aviso, modifier = Modifier.testTag(TAG_AVISO_DE_GRUPO_REPETIDO))
            }
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            PieDeHoja(
                if (repetido != null) "Crear otro igual" else "Crear grupo",
                onClick = ::crear,
                habilitado = puede,
                razon = when {
                    conQuien.isEmpty() -> "Elige con quién compartes"
                    nombreFinal.isEmpty() -> "Ponle un nombre"
                    else -> null
                },
                cargando = trabajando,
                error = error,
                modifier = Modifier.testTag(TAG_CREAR_GRUPO),
            )
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
    if (agregando) {
        DestinoSheet(
            cuentas = cuentas,
            existente = null,
            onDismiss = { agregando = false },
            onGuardado = {
                agregando = false
                lecturas++
            },
        )
    }
}

/**
 * **Editar un grupo**: el nombre, y «Borrar grupo» con su pregunta antes. [onBorrado] se llama cuando
 * el server lo borró (la pantalla de ese grupo se cierra).
 */
@Composable
fun HojaEditarGrupo(detalle: DetalleDelEspacio, onDismiss: () -> Unit, onGuardado: () -> Unit, onBorrado: () -> Unit) {
    val espacio = detalle.espacio
    val coroutine = rememberCoroutineScope()
    var nombre by remember(espacio.id) { mutableStateOf(espacio.nombre) }
    var trabajando by remember { mutableStateOf(false) }
    var preguntando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val limpio = nombre.trim()

    fun guardar() {
        if (trabajando || limpio.isEmpty()) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                // El upsert de siempre, por id: el mismo grupo con otro nombre.
                Repositories.wallets.guardarEspacio(espacio.copy(nombre = limpio.take(MAX_NOMBRE_DE_ESPACIO)))
                onGuardado()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
                trabajando = false
            }
        }
    }

    fun borrar() {
        if (trabajando) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                Repositories.wallets.borrarEspacio(espacio.id)
                preguntando = false
                onBorrado()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
                preguntando = false
                trabajando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !trabajando) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            EncabezadoDeHoja("Editar grupo", subtitulo = quienesComparten(espacio))
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Campo(
                valor = nombre,
                alCambiar = { nombre = it.take(MAX_NOMBRE_DE_ESPACIO) },
                rotulo = "Nombre",
                tag = TAG_NOMBRE_DEL_GRUPO,
            )
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            PieDeHoja(
                "Guardar",
                onClick = ::guardar,
                habilitado = limpio.isNotEmpty() && limpio != espacio.nombre,
                razon = when {
                    limpio.isEmpty() -> "Ponle un nombre"
                    limpio == espacio.nombre -> "Escribe el nombre nuevo"
                    else -> null
                },
                cargando = trabajando && !preguntando,
                error = error,
                secundaria = "Borrar grupo",
                onSecundaria = { preguntando = true },
                secundariaHabilitada = !trabajando,
            )
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
    if (preguntando) {
        val (pregunta, cuerpo) = confirmacionDeBorrarElGrupo(detalle)
        ConfirmarEnHoja(
            pregunta = pregunta,
            detalle = cuerpo,
            textoConfirmar = botonDeBorrarElGrupo(detalle),
            ocupado = trabajando,
            onConfirmar = ::borrar,
            onCancelar = { if (!trabajando) preguntando = false },
        )
    }
}

/**
 * **La propuesta de vincular una transferencia que salda** (la misma en el grupo, en Por revisar y en
 * la campana). «Sí, saldar» es la vinculación de siempre —con el id del saldado que espera, si lo hay
 * ([liquidacionDe])—; «No» la descarta para siempre ([huellaDeLaPropuestaDeSaldar]).
 *
 * @param conElGrupo fuera del grupo, dice de qué grupo es.
 */
@Composable
fun TarjetaDeSaldar(
    p: PropuestaDeSaldar,
    espacioId: String,
    quien: String,
    nombreDelGrupo: String,
    conElGrupo: Boolean,
    onHecho: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutine = rememberCoroutineScope()
    var trabajando by remember(p.eventoId) { mutableStateOf(false) }
    var error by remember(p.eventoId) { mutableStateOf<String?>(null) }
    fun hacer(bloque: suspend () -> Unit) {
        if (trabajando) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                bloque()
                onHecho()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
            } finally {
                trabajando = false
            }
        }
    }
    Column(modifier = modifier.testTag(TAG_PROPUESTA_DE_SALDAR)) {
        TarjetaDePropuesta(
            icono = Icons.Rounded.SwapHoriz,
            titulo = preguntaDeLaPropuesta(p, quien, nombreDelGrupo),
            cifra = p.monto,
            cuerpo = cuerpoDeLaPropuesta(p, conElGrupo),
            accion = "Sí, saldar",
            onAccion = { hacer { Repositories.wallets.liquidarEspacio(espacioId, liquidacionDe(p)) } },
            accionSecundaria = "No",
            onAccionSecundaria = { hacer { Repositories.wallets.descartarAnomalia(huellaDeLaPropuestaDeSaldar(p.eventoId)) } },
        )
        error?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
    }
}

/**
 * **«Pagos entre ustedes»**: cada saldado del grupo, el más reciente primero —«Caro te pagó $168.050 ·
 * 29 oct · Transferencia de Caro»—, con «Deshacer» (borra el saldado: la deuda vuelve, y la
 * transferencia vinculada vuelve a contar como ingreso o gasto). Sin saldados, no se pinta.
 */
@Composable
fun PagosEntreUstedes(detalle: DetalleDelEspacio, ahora: Long, onCambio: () -> Unit) {
    val espacio = detalle.espacio
    val pagos = detalle.liquidaciones.filter { it.monto > 0L }.sortedByDescending { it.fecha }
    if (pagos.isEmpty()) return
    val coroutine = rememberCoroutineScope()
    var deshaciendo by remember(espacio.id) { mutableStateOf<LiquidacionDelEspacio?>(null) }
    var trabajando by remember(espacio.id) { mutableStateOf(false) }
    var error by remember(espacio.id) { mutableStateOf<String?>(null) }
    Column {
        Seccion(titulo = PAGOS_ENTRE_USTEDES, conteo = pagos.size)
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            pagos.forEachIndexed { i, l ->
                if (i > 0) Separador()
                Fila(
                    titulo = tituloDelPago(espacio, l),
                    subtitulo = detalleDelPago(l, ahora),
                    inicio = { AvatarDePersona(nombreEnElEspacio(espacio, if (l.a == MIEMBRO_YO) l.de else l.a), tono = TonoDeIcono.Info) },
                    final = {
                        Boton(
                            "Deshacer",
                            onClick = { deshaciendo = l },
                            variante = VarianteDeBoton.Fantasma,
                            tamano = TamanoDeBoton.S,
                            modifier = Modifier.testTag(TAG_DESHACER_PAGO),
                        )
                    },
                    modifier = Modifier.testTag(TAG_PAGO_ENTRE_USTEDES),
                )
            }
        }
        error?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
    }
    deshaciendo?.let { l ->
        ConfirmarEnHoja(
            pregunta = "¿Deshacer este pago?",
            detalle = "${tituloDelPago(espacio, l)}. Al deshacerlo, el saldo vuelve a quedar como antes" +
                (if (l.eventoId != null) " y la transferencia vuelve a contar como ingreso o gasto." else "."),
            textoConfirmar = "Deshacer",
            ocupado = trabajando,
            onCancelar = { if (!trabajando) deshaciendo = null },
            onConfirmar = {
                if (!trabajando) {
                    trabajando = true
                    error = null
                    coroutine.launch {
                        try {
                            Repositories.wallets.deshacerLiquidacion(espacio.id, l.id)
                            onCambio()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            error = mensajeDelEspacio(e)
                        } finally {
                            trabajando = false
                            deshaciendo = null
                        }
                    }
                }
            },
        )
    }
}

/** La marca de corte de un saldado en la lista de gastos: un renglón apagado, centrado. */
@Composable
internal fun CorteDelSaldado(l: LiquidacionDelEspacio) {
    Text(
        corteDelSaldado(l),
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
        modifier = Modifier.fillMaxWidth().padding(vertical = EspacioDeDiseno.s).testTag(TAG_CORTE_DEL_SALDADO),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** El tag de la marca de corte de un saldado en la lista de gastos. */
const val TAG_CORTE_DEL_SALDADO: String = "grupos:corte"
