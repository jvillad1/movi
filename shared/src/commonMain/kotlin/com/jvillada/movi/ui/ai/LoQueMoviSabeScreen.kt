package com.jvillada.movi.ui.ai

import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Vacio
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.ui.components.ds.AccionDeEncabezado
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.LARGO_MAXIMO_DE_UN_RECUERDO
import com.jvillada.movi.shared.model.OrigenDelRecuerdo
import com.jvillada.movi.shared.model.RecuerdoDelAsistente
import com.jvillada.movi.shared.model.ResumenDeLoQueMoviSabe
import com.jvillada.movi.shared.model.fraseDeLoQueMoviSabe
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.BotonReintentar
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.components.ds.Campo

/**
 * # «Lo que Movi sabe de ti» (Ola 3 · 2)
 *
 * Lo que el dueño le contó a Movi AI y confirmó guardar. Esto entra al contexto de cada
 * conversación, así que tiene que poder verse entero, corregirse y borrarse sin pedirle nada a
 * nadie: una frase equivocada acá es un asistente que se equivoca siempre en lo mismo.
 *
 * Lo que Movi recuerda nace casi siempre de una conversación, con una propuesta que el dueño
 * confirma. **Vuelta 61: también se escribe a mano** («Agregar», arriba; con la memoria vacía, «Escribir un
 * recuerdo» del vacío —vuelta 78: una sola de las dos a la vez—), con el mismo
 * `POST /api/asistente/memoria` de siempre y el origen `A_MANO` («Lo escribiste tú»): sin crédito en
 * Anthropic el chat no propone nada, y la memoria no se podía llenar de ninguna forma.
 */

internal const val TITULO_LO_QUE_MOVI_SABE = "Lo que Movi sabe de ti"

internal const val QUE_ES_LO_QUE_MOVI_SABE =
    "Lo que le contaste a Movi AI y le pediste recordar. Lo usa para entenderte en cada conversación. " +
        "Puedes corregirlo o borrarlo cuando quieras."

internal const val NADA_TODAVIA = "Todavía no sé nada de ti"

internal const val COMO_SE_LLENA =
    "Cuando le cuentes a Movi AI algo que valga la pena recordar —quién es quién, qué es cada pago—, " +
        "te va a proponer guardarlo aquí. Nada se guarda sin que lo confirmes. También puedes escribirlo tú."

internal const val AGREGAR = "Agregar"
internal const val AGREGAR_UN_RECUERDO = "Escribir un recuerdo"
internal const val ROTULO_DEL_RECUERDO_NUEVO = "Lo que Movi debe saber de ti"
internal const val EJEMPLO_DE_RECUERDO = "Caro es mi esposa; el colegio de mi hija se paga el 25"

/** El editor de un recuerdo nuevo, para encontrarlo en una prueba. */
internal const val TAG_RECUERDO_NUEVO = "ai-recuerdo-nuevo"

internal const val EDITAR = "Editar"
internal const val BORRAR = "Borrar"
internal const val GUARDAR = "Guardar"
internal const val CANCELAR = "Cancelar"
internal const val SI_BORRAR = "Sí, olvidarlo"
internal const val PREGUNTA_DE_BORRAR = "¿Movi lo olvida? No se puede deshacer."

private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** «Lo confirmaste en el chat · 2 oct. 2026», o «Lo corregiste · …» si se editó. */
internal fun deDondeSalio(r: RecuerdoDelAsistente): String {
    val cuando = r.editadoEn ?: r.creadoEn
    val fecha = fechaDeEpoch(cuando)
    val fechaTexto = "${fecha.dayOfMonth} ${MESES_CORTOS[fecha.monthNumber - 1]}. ${fecha.year}"
    val que = when {
        r.editadoEn != null -> "Lo corregiste"
        r.origen == OrigenDelRecuerdo.A_MANO -> "Lo escribiste tú"
        else -> "Lo confirmaste en el chat"
    }
    return "$que · $fechaTexto"
}

@Composable
fun LoQueMoviSabeScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    val recuerdos = remember { mutableStateListOf<RecuerdoDelAsistente>() }
    var cargando by remember { mutableStateOf(true) }
    var fallo by remember { mutableStateOf(false) }
    var intento by remember { mutableStateOf(0) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var agregando by remember { mutableStateOf(false) }

    fun guardarNuevo(texto: String, listo: () -> Unit) {
        coroutine.launch {
            runCatching { Repositories.wallets.guardarRecuerdo(texto, OrigenDelRecuerdo.A_MANO) }
                .onSuccess { nuevo ->
                    recuerdos.removeAll { it.id == nuevo.id }
                    recuerdos.add(0, nuevo)
                    aviso = null
                    agregando = false
                    listo()
                }
                .onFailure { aviso = motivoDeLaFalla(it); listo() }
        }
    }

    // Vuelta 95: el resumen de arriba. Es aparte de los recuerdos: si no llega, la pantalla sigue igual.
    var resumen by remember { mutableStateOf<ResumenDeLoQueMoviSabe?>(null) }
    LaunchedEffect(intento) {
        runCatching { Repositories.wallets.getResumenDeLoQueMoviSabe() }.onSuccess { resumen = it }
    }

    LaunchedEffect(intento) {
        cargando = true
        fallo = false
        runCatching { Repositories.wallets.getMemoriaDelAsistente() }
            .onSuccess { recuerdos.clear(); recuerdos.addAll(it) }
            .onFailure { fallo = true }
        cargando = false
    }

    // Vuelta 78 · **una sola acción para agregar**. Con la memoria vacía, el vacío trae «Escribir un
    // recuerdo» y el encabezado traía además «Agregar», que hacía lo mismo: dos botones, una acción. Con
    // el vacío a la vista va la suya; con recuerdos (o cargando, o caída la lectura), la del encabezado.
    val vacioConSuAccion = !cargando && !fallo && recuerdos.isEmpty()
    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(
            title = TITULO_LO_QUE_MOVI_SABE,
            leading = HeaderLeading.Back(fallback = Screen.Tu),
            // Vuelta 61: escribir un recuerdo a mano (sin la IA, era la única forma que faltaba).
            accion = if (vacioConSuAccion) null
            else AccionDeEncabezado(AGREGAR, Icons.Rounded.Add, onClick = { agregando = true; aviso = null }, habilitado = !agregando),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = Medida.margenDePantalla, vertical = Medida.dentroDeSeccion),
            verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Text(QUE_ES_LO_QUE_MOVI_SABE, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            // Los recuerdos se cuentan de la lista de abajo, que se corrige y se borra en esta misma
            // pantalla: el número no se queda viejo.
            resumen?.let { r ->
                item(key = "resumen") {
                    ResumenDeUnVistazo(
                        if (cargando && recuerdos.isEmpty()) r else r.copy(recuerdos = recuerdos.size),
                        onNavigate,
                    )
                }
            }
            aviso?.let { texto ->
                item { Text(texto, style = Movi.textos.apoyo, color = Movi.colores.sale) }
            }
            if (agregando) {
                item(key = "recuerdo-nuevo") {
                    RecuerdoNuevo(onGuardar = ::guardarNuevo, onCancelar = { agregando = false })
                }
            }
            when {
                cargando && recuerdos.isEmpty() -> item {
                    Column { repeat(3) { EsqueletoDeFila(conInicio = false, conFinal = false) } }
                }
                fallo && recuerdos.isEmpty() -> item {
                    NoSePudoLeer("No pude cargar lo que Movi sabe de ti.", onReintentar = { intento++ })
                }
                recuerdos.isEmpty() -> if (!agregando) item {
                    Vacio(
                        titulo = NADA_TODAVIA,
                        detalle = COMO_SE_LLENA,
                        icono = Icons.Rounded.Psychology,
                        accion = AGREGAR_UN_RECUERDO,
                        onAccion = { agregando = true; aviso = null },
                    )
                }
                else -> items(recuerdos, key = { it.id }) { recuerdo ->
                    FilaDeRecuerdo(
                        recuerdo = recuerdo,
                        onGuardar = { nuevo, listo ->
                            coroutine.launch {
                                runCatching { Repositories.wallets.editarRecuerdo(recuerdo.id, nuevo) }
                                    .onSuccess { editado ->
                                        val i = recuerdos.indexOfFirst { it.id == recuerdo.id }
                                        if (i >= 0) recuerdos[i] = editado
                                        aviso = null
                                        listo()
                                    }
                                    .onFailure { aviso = motivoDeLaFalla(it) }
                            }
                        },
                        onBorrar = {
                            coroutine.launch {
                                runCatching { Repositories.wallets.borrarRecuerdo(recuerdo.id) }
                                    .onSuccess { recuerdos.removeAll { it.id == recuerdo.id }; aviso = null }
                                    .onFailure { aviso = motivoDeLaFalla(it) }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** El tag del resumen de arriba, para las pruebas. */
internal const val TAG_RESUMEN_DE_LO_QUE_MOVI_SABE = "ai-resumen-de-lo-que-movi-sabe"

internal const val VER_MOVIMIENTOS = "Ver movimientos"
internal const val VER_CUENTAS = "Ver cuentas"

/**
 * **Lo que Movi sabe de ti, de un vistazo** (vuelta 95): una frase con los cuatro conteos y, para los
 * que ya tienen pantalla —los movimientos y las cuentas—, el botón que la abre. Las reglas aprendidas no
 * tienen pantalla propia (se ven en cómo Movi propone categorías) y los recuerdos están debajo.
 */
@Composable
private fun ResumenDeUnVistazo(r: ResumenDeLoQueMoviSabe, onNavigate: (Screen) -> Unit) {
    Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_RESUMEN_DE_LO_QUE_MOVI_SABE)) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(fraseDeLoQueMoviSabe(r), style = Movi.textos.cuerpo, color = Movi.colores.texto)
            if (r.movimientos > 0 || r.cuentas > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    if (r.movimientos > 0) Accion(VER_MOVIMIENTOS) { onNavigate(Screen.Transactions()) }
                    if (r.cuentas > 0) Accion(VER_CUENTAS) { onNavigate(Screen.Accounts) }
                }
            }
        }
    }
}

/**
 * **Un recuerdo nuevo, escrito a mano** (vuelta 61): el mismo editor que corregir uno —el [Campo] de
 * hasta seis renglones con el tope de [LARGO_MAXIMO_DE_UN_RECUERDO]— con «Guardar» y «Cancelar».
 */
@Composable
private fun RecuerdoNuevo(onGuardar: (String, () -> Unit) -> Unit, onCancelar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_RECUERDO_NUEVO)) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Campo(
                texto,
                { if (it.length <= LARGO_MAXIMO_DE_UN_RECUERDO) texto = it },
                rotulo = ROTULO_DEL_RECUERDO_NUEVO,
                ejemplo = EJEMPLO_DE_RECUERDO,
                renglones = 6,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Accion(GUARDAR, principal = true, habilitada = texto.isNotBlank() && !guardando) {
                    guardando = true
                    onGuardar(texto.trim()) { guardando = false }
                }
                Accion(CANCELAR, onClick = onCancelar)
            }
        }
    }
}

@Composable
private fun FilaDeRecuerdo(
    recuerdo: RecuerdoDelAsistente,
    onGuardar: (String, () -> Unit) -> Unit,
    onBorrar: () -> Unit,
) {
    var editando by remember(recuerdo.id) { mutableStateOf(false) }
    var preguntandoSiBorrar by remember(recuerdo.id) { mutableStateOf(false) }
    var texto by remember(recuerdo.id, recuerdo.texto) { mutableStateOf(recuerdo.texto) }

    Superficie(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        if (editando) {
            // Un recuerdo puede ser largo: la caja crece hasta seis renglones.
            Campo(
                texto,
                { if (it.length <= LARGO_MAXIMO_DE_UN_RECUERDO) texto = it },
                renglones = 6,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Accion(GUARDAR, principal = true, habilitada = texto.isNotBlank() && texto.trim() != recuerdo.texto) {
                    onGuardar(texto.trim()) { editando = false }
                }
                Accion(CANCELAR) { texto = recuerdo.texto; editando = false }
            }
        } else {
            Text(recuerdo.texto, style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Text(deDondeSalio(recuerdo), style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            if (preguntandoSiBorrar) {
                Text(PREGUNTA_DE_BORRAR, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    Accion(SI_BORRAR, peligro = true) { preguntandoSiBorrar = false; onBorrar() }
                    Accion(CANCELAR) { preguntandoSiBorrar = false }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    Accion(EDITAR) { editando = true }
                    Accion(BORRAR) { preguntandoSiBorrar = true }
                }
            }
        }
      }
    }
}

/** Los botones de un recuerdo: el [Boton] chico del sistema (principal, peligro o fantasma). */
@Composable
private fun Accion(
    texto: String,
    principal: Boolean = false,
    peligro: Boolean = false,
    habilitada: Boolean = true,
    onClick: () -> Unit,
) {
    Boton(
        texto,
        onClick = onClick,
        variante = when {
            principal -> VarianteDeBoton.Principal
            peligro -> VarianteDeBoton.Peligro
            else -> VarianteDeBoton.Fantasma
        },
        tamano = TamanoDeBoton.S,
        habilitado = habilitada,
    )
}
