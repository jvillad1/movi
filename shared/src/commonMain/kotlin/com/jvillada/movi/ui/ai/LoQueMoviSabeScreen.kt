package com.jvillada.movi.ui.ai

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
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
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.BotonReintentar
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.coroutines.launch

/**
 * # «Lo que Movi sabe de ti» (Ola 3 · 2)
 *
 * Lo que el dueño le contó a Movi AI y confirmó guardar. Esto entra al contexto de cada
 * conversación, así que tiene que poder verse entero, corregirse y borrarse sin pedirle nada a
 * nadie: una frase equivocada acá es un asistente que se equivoca siempre en lo mismo.
 *
 * Nada se agrega desde acá: lo que Movi recuerda nace de una conversación, con una propuesta que el
 * dueño confirma. Acá se mira y se arregla.
 */

internal const val TITULO_LO_QUE_MOVI_SABE = "Lo que Movi sabe de ti"

internal const val QUE_ES_LO_QUE_MOVI_SABE =
    "Lo que le contaste a Movi AI y le pediste recordar. Lo usa para entenderte en cada conversación. " +
        "Puedes corregirlo o borrarlo cuando quieras."

internal const val NADA_TODAVIA = "Todavía no sé nada de ti"

internal const val COMO_SE_LLENA =
    "Cuando le cuentes a Movi AI algo que valga la pena recordar —quién es quién, qué es cada pago—, " +
        "te va a proponer guardarlo aquí. Nada se guarda sin que lo confirmes."

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

    LaunchedEffect(intento) {
        cargando = true
        fallo = false
        runCatching { Repositories.wallets.getMemoriaDelAsistente() }
            .onSuccess { recuerdos.clear(); recuerdos.addAll(it) }
            .onFailure { fallo = true }
        cargando = false
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(
            title = TITULO_LO_QUE_MOVI_SABE,
            leading = HeaderLeading.Back(fallback = Screen.Mas),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.medio),
            verticalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Text(QUE_ES_LO_QUE_MOVI_SABE, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            aviso?.let { texto ->
                item { Text(texto, style = Movi.textos.apoyo, color = Movi.colores.sale) }
            }
            when {
                cargando && recuerdos.isEmpty() -> item {
                    Text("Cargando…", style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                }
                fallo && recuerdos.isEmpty() -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(Movi.espacios.corto)) {
                        Text("No pude cargar lo que Movi sabe de ti.", style = Movi.textos.cuerpo, color = Movi.colores.texto)
                        BotonReintentar(onReintentar = { intento++ })
                    }
                }
                recuerdos.isEmpty() -> item {
                    VacioQueEnsena(titulo = NADA_TODAVIA, detalle = COMO_SE_LLENA, icono = Icons.Rounded.Psychology)
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

@Composable
private fun FilaDeRecuerdo(
    recuerdo: RecuerdoDelAsistente,
    onGuardar: (String, () -> Unit) -> Unit,
    onBorrar: () -> Unit,
) {
    var editando by remember(recuerdo.id) { mutableStateOf(false) }
    var preguntandoSiBorrar by remember(recuerdo.id) { mutableStateOf(false) }
    var texto by remember(recuerdo.id, recuerdo.texto) { mutableStateOf(recuerdo.texto) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Movi.formas.amplia))
            .background(Movi.colores.tarjeta)
            .padding(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.medio),
        verticalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
    ) {
        if (editando) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Movi.formas.normal))
                    .border(1.dp, Movi.colores.borde, RoundedCornerShape(Movi.formas.normal))
                    .padding(10.dp),
            ) {
                BasicTextField(
                    value = texto,
                    onValueChange = { if (it.length <= LARGO_MAXIMO_DE_UN_RECUERDO) texto = it },
                    textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                    cursorBrush = SolidColor(Movi.colores.texto),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto)) {
                    Accion(SI_BORRAR, peligro = true) { preguntandoSiBorrar = false; onBorrar() }
                    Accion(CANCELAR) { preguntandoSiBorrar = false }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto)) {
                    Accion(EDITAR) { editando = true }
                    Accion(BORRAR) { preguntandoSiBorrar = true }
                }
            }
        }
    }
}

@Composable
private fun Accion(
    texto: String,
    principal: Boolean = false,
    peligro: Boolean = false,
    habilitada: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        texto,
        style = Movi.textos.apoyo,
        fontWeight = FontWeight.Medium,
        color = when {
            !habilitada -> Movi.colores.textoApagado
            principal -> Movi.colores.sobreMarca
            peligro -> Movi.colores.sale
            else -> Movi.colores.marca
        },
        modifier = Modifier
            .clip(RoundedCornerShape(Movi.formas.normal))
            .then(if (principal && habilitada) Modifier.background(Movi.colores.marca) else Modifier)
            .clickable(enabled = habilitada, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
