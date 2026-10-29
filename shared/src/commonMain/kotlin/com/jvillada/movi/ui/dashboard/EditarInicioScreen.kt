package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.ScreenDefCache
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.PreferenciasDelInicio
import com.jvillada.movi.shared.model.SECCIONES_CONFIGURABLES
import com.jvillada.movi.shared.model.SECCIONES_OPCIONALES
import com.jvillada.movi.shared.model.limpias
import com.jvillada.movi.shared.model.ordenarConPreferencias
import com.jvillada.movi.shared.model.renderableSections
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ANCHO_DE_UNA_COLUMNA
import com.jvillada.movi.ui.components.BotonReintentar
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * # «Editar inicio» (Ola 3 · Hoy en una mirada, 22-oct-2026)
 *
 * El dueño elige qué secciones ve en Hoy y en qué orden: un interruptor por sección para mostrarla o
 * esconderla, y flechas ↑ ↓ para moverla (las flechas son la forma accesible, y la que sirve con
 * mouse; no hay arrastre). «Volver al orden de Movi» deshace todo.
 *
 * - **El héroe («Te quedan») no se mueve ni se esconde**: va siempre primero, y la pantalla lo dice
 *   arriba de la lista. Tampoco las tarjetas que aparecen cuando hacen falta (Primeros pasos, Cerró tu
 *   período, Avisos): no viajan en la definición.
 * - **«Para revisar» escondida no esconde lo urgente**: Hoy deja una línea, «2 cosas para revisar ›»
 *   (ver `ParaRevisarCompactoSection`).
 * - **Se guarda en el server, por usuario** (`PUT /api/inicio/preferencias`), y cada cambio se guarda
 *   solo: en el teléfono, la web y el iPhone se ve el mismo Hoy. El server aplica la preferencia al
 *   servir la definición, así que después de guardar se pide de nuevo y queda en [ScreenDefCache] y en
 *   la instantánea del aparato: al volver, Hoy ya tiene el orden nuevo, sin saltar.
 *
 * Se llega desde el botón del final de Hoy y desde Ajustes → Perfil → «Cómo se ve».
 */

internal const val TITULO_EDITAR_INICIO = "Editar inicio"
internal const val VOLVER_AL_ORDEN_DE_MOVI = "Volver al orden de Movi"
internal const val EL_HEROE_VA_PRIMERO = "«Te quedan», las acciones y «Movi propone hoy» van siempre arriba."
internal const val PARA_REVISAR_ESCONDIDA =
    "Escondida: si hay algo urgente, Inicio te lo dice en una sola línea."

/** El tag de la fila de una sección, por su tipo. */
internal fun tagDeLaFilaDelEditor(tipo: String): String = "editar-inicio-$tipo"

/** «Mostrar «Disponible» en Hoy»: lo que dice el interruptor de cada fila al lector de pantalla. */
internal fun rotuloDeMostrar(nombre: String): String = "Mostrar «$nombre» en Inicio"
internal fun rotuloDeSubir(nombre: String): String = "Subir «$nombre»"
internal fun rotuloDeBajar(nombre: String): String = "Bajar «$nombre»"

/** Una fila de «Editar inicio»: el tipo de la sección, cómo se llama y si se ve en Hoy. */
data class SeccionDelEditor(val tipo: String, val nombre: String, val visible: Boolean)

/**
 * **Lo que lista «Editar inicio»**, en el orden que se va a ver en Hoy. Pura.
 *
 * La base es `ordenDeMovi` —las secciones configurables que trae la definición de Hoy—, o todas las
 * configurables si el server no la mandó. El orden elegido se aplica con la MISMA regla con la que el
 * server arma Hoy ([ordenarConPreferencias]), así la lista no puede decir un orden y Hoy otro.
 */
fun seccionesDelEditor(preferencias: PreferenciasDelInicio): List<SeccionDelEditor> {
    val limpias = preferencias.limpias()
    val deMovi = deLaDefinicion(preferencias)
    return ordenarConPreferencias(baseDelEditor(preferencias), { it }, limpias.orden).map { tipo ->
        // Ola R1: una opcional que el Inicio de Movi no trae (Disponible, Tu patrimonio) se ve solo si
        // el dueño la prendió; lo demás, salvo que la haya escondido.
        val visible = if (tipo in deMovi) tipo !in limpias.ocultas else tipo in limpias.visibles
        SeccionDelEditor(tipo, SECCIONES_CONFIGURABLES[tipo] ?: tipo, visible)
    }
}

/** Las configurables que trae el Inicio de Movi: `ordenDeMovi`, o las que no son opcionales. */
private fun deLaDefinicion(preferencias: PreferenciasDelInicio): List<String> =
    preferencias.ordenDeMovi.ifEmpty { SECCIONES_CONFIGURABLES.keys.filter { it !in SECCIONES_OPCIONALES } }

/** Lo que lista el editor: las del Inicio de Movi y, al final, las opcionales que no trae. */
private fun baseDelEditor(preferencias: PreferenciasDelInicio): List<String> {
    val deMovi = deLaDefinicion(preferencias)
    return deMovi + SECCIONES_OPCIONALES.filter { it !in deMovi }
}

/**
 * [tipo] un lugar más arriba ([hacia] = −1) o más abajo (+1). Sin efecto en los bordes. Si el orden
 * que queda es el de Movi, la preferencia deja de nombrarlo: volver a mano al orden de siempre es lo
 * mismo que «Volver al orden de Movi».
 */
fun PreferenciasDelInicio.moviendo(tipo: String, hacia: Int): PreferenciasDelInicio {
    val lista = seccionesDelEditor(this).map { it.tipo }.toMutableList()
    val desde = lista.indexOf(tipo)
    val hasta = desde + hacia
    if (desde < 0 || hasta !in lista.indices) return this
    lista.removeAt(desde)
    lista.add(hasta, tipo)
    return copy(orden = if (lista == baseDelEditor(this)) emptyList() else lista)
}

/**
 * [tipo] visible o escondida en el Inicio. Una opcional que el Inicio de Movi no trae se prende y se
 * apaga en [PreferenciasDelInicio.visibles]; las demás se esconden en [PreferenciasDelInicio.ocultas].
 */
fun PreferenciasDelInicio.mostrando(tipo: String, visible: Boolean): PreferenciasDelInicio =
    if (tipo in SECCIONES_OPCIONALES && tipo !in deLaDefinicion(this)) {
        copy(visibles = if (visible) (visibles + tipo).distinct() else visibles - tipo)
    } else {
        copy(ocultas = if (visible) ocultas - tipo else (ocultas + tipo).distinct())
    }

/** «Volver al orden de Movi»: sin orden propio ni ocultas. */
fun PreferenciasDelInicio.deMovi(): PreferenciasDelInicio = PreferenciasDelInicio(ordenDeMovi = ordenDeMovi)

@Composable
fun EditarInicioScreen(onNavigate: (Screen) -> Unit) {
    var preferencias by remember { mutableStateOf<PreferenciasDelInicio?>(null) }
    var fallo by remember { mutableStateOf(false) }
    var intento by remember { mutableStateOf(0) }
    var noSeGuardo by remember { mutableStateOf(false) }
    // Lo último que pidió el dueño. Un solo guardado a la vez, siempre con lo más nuevo: un `StateFlow`
    // se queda con el último valor mientras el anterior se guarda, así dos toques rápidos no mandan
    // dos PUT que puedan llegar al revés.
    val porGuardar = remember { MutableStateFlow<PreferenciasDelInicio?>(null) }

    LaunchedEffect(intento) {
        fallo = false
        runCatching { Repositories.wallets.getPreferenciasDelInicio() }
            .onSuccess { preferencias = it }
            .onFailure { fallo = true }
    }

    LaunchedEffect(Unit) {
        porGuardar.filterNotNull().collect { pedida ->
            runCatching { Repositories.wallets.guardarPreferenciasDelInicio(pedida.limpias()) }
                .onSuccess {
                    noSeGuardo = false
                    // El server arma Hoy con la preferencia: se pide la definición nueva y queda donde
                    // Hoy la busca al montarse (la memoria y el aparato). Si falla, Hoy la pide solo.
                    val usuario = SessionManager.userId
                    runCatching { Repositories.wallets.getScreen("dashboard", null) }.getOrNull()
                        ?.takeIf { renderableSections(it).isNotEmpty() }
                        ?.let { def ->
                            ScreenDefCache.dashboard = def
                            if (SessionManager.userId == usuario) InstantaneaDelInicio.delAparato.guardarDefinicion(usuario, def)
                        }
                }
                .onFailure { noSeGuardo = true }
        }
    }

    fun cambiar(nueva: PreferenciasDelInicio) {
        preferencias = nueva
        porGuardar.value = nueva
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(
            title = TITULO_EDITAR_INICIO,
            leading = HeaderLeading.Back(fallback = Screen.Dashboard),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.medio),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = ANCHO_DE_UNA_COLUMNA)) {
                Text(
                    "Elige qué ves en Inicio y en qué orden. Se guarda solo, y vale en todos tus aparatos.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                Spacer(Modifier.height(Movi.espacios.medio))
                val actual = preferencias
                when {
                    actual == null && fallo -> NoSePudoLeer("No pude cargar cómo tienes Inicio.", onReintentar = { intento++ })
                    actual == null -> Superficie(relleno = RellenoDeSuperficie.Lista) {
                        repeat(4) { EsqueletoDeFila(conInicio = false) }
                    }
                    else -> EditorDelInicio(
                        preferencias = actual,
                        noSeGuardo = noSeGuardo,
                        onCambiar = ::cambiar,
                    )
                }
            }
        }
    }
}

/** La lista con sus interruptores y flechas, y «Volver al orden de Movi». Sin red: la de arriba guarda. */
@Composable
internal fun EditorDelInicio(
    preferencias: PreferenciasDelInicio,
    noSeGuardo: Boolean,
    onCambiar: (PreferenciasDelInicio) -> Unit,
) {
    val secciones = seccionesDelEditor(preferencias)
    Seccion(titulo = "Secciones de Inicio")
    Text(EL_HEROE_VA_PRIMERO, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
    Spacer(Modifier.height(Espacio.xs))
    Superficie(relleno = RellenoDeSuperficie.Lista) {
        secciones.forEachIndexed { i, seccion ->
            FilaDelEditor(
                seccion = seccion,
                puedeSubir = i > 0,
                puedeBajar = i < secciones.lastIndex,
                onSubir = { onCambiar(preferencias.moviendo(seccion.tipo, -1)) },
                onBajar = { onCambiar(preferencias.moviendo(seccion.tipo, +1)) },
                onMostrar = { onCambiar(preferencias.mostrando(seccion.tipo, it)) },
            )
            if (i < secciones.lastIndex) Separador(SangriaDeSeparador.Margen)
        }
    }
    if (noSeGuardo) {
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(
            "No se pudo guardar. Revisa tu conexión y vuelve a intentarlo.",
            style = Movi.textos.apoyo,
            color = Movi.colores.sale,
        )
    }
    // Vuelta 41: con el Inicio ya en el orden de Movi no hay nada que deshacer y el botón no se pinta
    // (apagado se veía en el marrón del texto apagado, como roto). Con algo cambiado, un botón de
    // texto en el azul del enlace.
    if (seVeVolverAlOrdenDeMovi(preferencias)) {
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Boton(
            VOLVER_AL_ORDEN_DE_MOVI,
            onClick = { onCambiar(preferencias.deMovi()) },
            variante = VarianteDeBoton.Fantasma,
        )
    }
}

/** ¿Se ofrece «Volver al orden de Movi»? Solo si el Inicio no está ya en el orden de Movi. Pura. */
fun seVeVolverAlOrdenDeMovi(preferencias: PreferenciasDelInicio): Boolean = !preferencias.limpias().esLaDeMovi

@Composable
private fun FilaDelEditor(
    seccion: SeccionDelEditor,
    puedeSubir: Boolean,
    puedeBajar: Boolean,
    onSubir: () -> Unit,
    onBajar: () -> Unit,
    onMostrar: (Boolean) -> Unit,
) {
    // Ola R4: una [Fila] del sistema; las flechas y el interruptor van en su final.
    Fila(
        titulo = seccion.nombre,
        subtitulo = if (!seccion.visible && seccion.tipo == "ALERTS") PARA_REVISAR_ESCONDIDA else null,
        final = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Flecha(Icons.Rounded.KeyboardArrowUp, rotuloDeSubir(seccion.nombre), puedeSubir, onSubir)
                Flecha(Icons.Rounded.KeyboardArrowDown, rotuloDeBajar(seccion.nombre), puedeBajar, onBajar)
                Switch(
                    checked = seccion.visible,
                    onCheckedChange = onMostrar,
                    modifier = Modifier.semantics { contentDescription = rotuloDeMostrar(seccion.nombre) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Movi.colores.tarjeta,
                        // Encendido en tinta, el «seleccionado» de toda la app (vuelta 29).
                        checkedTrackColor = Movi.colores.texto,
                        uncheckedThumbColor = Movi.colores.textoApagado,
                        uncheckedTrackColor = Movi.colores.tarjeta,
                        uncheckedBorderColor = Movi.colores.borde,
                    ),
                )
            }
        },
        modifier = Modifier.testTag(tagDeLaFilaDelEditor(seccion.tipo)),
    )
}

/** Una flecha de 44 dp de blanco para el dedo; apagada en el borde de la lista. */
@Composable
private fun Flecha(
    icono: ImageVector,
    rotulo: String,
    habilitada: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Medida.objetivoTactil)
            .clip(CircleShape)
            .clickable(enabled = habilitada, role = Role.Button, onClickLabel = rotulo, onClick = onClick)
            .semantics { contentDescription = rotulo },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = if (habilitada) Movi.colores.texto else Movi.colores.hilo,
            modifier = Modifier.size(Icono.l),
        )
    }
}

/**
 * **«Editar inicio», al final de Hoy.** Un renglón discreto con el ícono de ajustes: está siempre, al
 * final, para no competir con lo que Hoy dice.
 */
@Composable
internal fun BotonEditarInicio(onClick: () -> Unit) {
    // Vuelta de diseño 20: el `Boton` de texto del sistema (`Fantasma` `S`), centrado, con el ícono de
    // ajustes. Era un renglón armado a mano con `clickable` y el texto en gris.
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = Espacio.xs), contentAlignment = Alignment.Center) {
        Boton(
            TITULO_EDITAR_INICIO,
            onClick = onClick,
            variante = VarianteDeBoton.Fantasma,
            tamano = TamanoDeBoton.S,
            icono = Icons.Rounded.Tune,
        )
    }
}
