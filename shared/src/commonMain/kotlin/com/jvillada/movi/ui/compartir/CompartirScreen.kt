package com.jvillada.movi.ui.compartir

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.platform.hojaDeCompartirDeLaPlataforma
import com.jvillada.movi.shared.model.EnlaceCompartido
import com.jvillada.movi.shared.model.NuevoEnlaceCompartido
import com.jvillada.movi.shared.model.VIGENCIAS_DE_ENLACE
import com.jvillada.movi.shared.model.VIGENCIA_POR_DEFECTO
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * # «Compartir» — un enlace de solo lectura para un tercero
 *
 * El dueño lo pidió para poder mostrarle su situación a Caro o a un asesor **sin darle su
 * contraseña ni mandar capturas**. Esta pantalla es el lado del dueño: crear el enlace, mandarlo y
 * revocarlo. La página que abre el tercero la sirve el server (`PaginaCompartida.kt`).
 *
 * ## Lo que tiene que quedar claro antes del botón
 *
 * El orden de la pantalla es el orden de las preguntas que uno se hace antes de compartir algo de
 * plata: **qué va a ver** (y qué no), **por cuánto tiempo**, y **cómo lo corto**. Recién después, el
 * botón. Los textos viven en `CompartirLogic.kt` y se prueban allá.
 *
 * ## El enlace se manda al crearlo, o nunca
 *
 * El server guarda solo una huella del token (ver `EnlaceCompartido` en `:core`), así que la lista de
 * abajo **no puede** volver a mostrar un enlace. Por eso, al crear, la pantalla deja el enlace a la
 * vista con los botones para mandarlo, y lo dice en voz alta ([SE_COMPARTE_AHORA]). Es incómodo
 * a propósito: la alternativa es guardar en la base una llave que abre la plata del dueño.
 *
 * ## Solo en línea
 *
 * Sin señal no hay nada que crear ni revocar de verdad. La lista dice que no pudo leer y ofrece
 * reintentar, como «Cuentas de otros».
 */
@Composable
fun CompartirScreen(onNavigate: (Screen) -> Unit) {
    val alcance = rememberCoroutineScope()
    val hoja = hojaDeCompartirDeLaPlataforma()

    var enlaces by remember { mutableStateOf<List<EnlaceCompartido>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var leidos by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) }

    var dias by remember { mutableStateOf(VIGENCIA_POR_DEFECTO) }
    var creando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // El recién creado: la única vez que la app tiene la URL con el token.
    var recienCreado by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var copiado by remember { mutableStateOf(false) }

    var porRevocar by remember { mutableStateOf<EnlaceCompartido?>(null) }
    var revocando by remember { mutableStateOf(false) }

    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(loadKey, refreshTick) {
        cargando = true
        runCatching { Repositories.compartir.listar() }
            .onSuccess { enlaces = it; leidos = true }
        cargando = false
    }
    val noSeLeyo = !cargando && !leidos
    val ahora = Clock.System.now().toEpochMilliseconds()

    fun crear() {
        if (creando) return
        creando = true
        error = null
        alcance.launch {
            runCatching { Repositories.compartir.crear(NuevoEnlaceCompartido(dias = dias)) }
                .onSuccess { creado ->
                    recienCreado = creado.ruta to dias
                    copiado = false
                    enlaces = listOf(creado.enlace) + enlaces.filterNot { it.id == creado.enlace.id }
                }
                .onFailure { error = it.toUserMessage() }
            creando = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
            MinScreenHeader(
                title = "Compartir",
                leading = HeaderLeading.Back(fallback = Screen.Mas),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Movi.espacios.amplio),
            ) {
                item {
                    QueVaAVer()
                    Spacer(Modifier.height(Movi.espacios.amplio))
                }

                item {
                    val creado = recienCreado
                    if (creado == null) {
                        CrearEnlace(
                            dias = dias,
                            onDias = { dias = it },
                            creando = creando,
                            error = error,
                            onCrear = ::crear,
                        )
                    } else {
                        EnlaceListo(
                            url = creado.first,
                            diasDelEnlace = creado.second,
                            abreLaHojaDelSistema = hoja.abreLaHojaDelSistema,
                            copiado = copiado,
                            onCompartir = { hoja.compartir(mensajeParaCompartir(creado.first, creado.second)) },
                            onCopiar = { hoja.copiar(creado.first); copiado = true },
                            onOtro = { recienCreado = null; copiado = false },
                        )
                    }
                    Spacer(Modifier.height(Movi.espacios.seccion))
                }

                item {
                    MinSectionHeader(
                        title = "Enlaces activos",
                        count = if (enlaces.isNotEmpty() && !noSeLeyo) enlaces.size else null,
                    )
                    when {
                        noSeLeyo -> NoSePudoLeer(
                            "No pudimos cargar tus enlaces",
                            onReintentar = { loadKey++ },
                        )
                        // Reemplaza al texto suelto de siempre. «Crear un enlace»
                        // llama a la MISMA `crear()` que ofrece la sección de arriba — no duplica
                        // la lógica de creación, solo le da una segunda puerta a quien llegó
                        // hasta acá sin haber leído la explicación.
                        enlaces.isEmpty() && !cargando -> VacioQueEnsena(
                            titulo = "No tienes enlaces activos",
                            detalle = "Los que crees aparecerán aquí hasta que venzan o los revoques.",
                            accion = "Crear un enlace",
                            onAccion = ::crear,
                            modifier = Modifier.padding(horizontal = Movi.espacios.corto),
                        )
                    }
                }

                if (!noSeLeyo) items(enlaces, key = { it.id }) { enlace ->
                    FilaDeEnlace(enlace, ahora, onRevocar = { porRevocar = enlace })
                    Spacer(Modifier.height(Movi.espacios.corto))
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        porRevocar?.let { enlace ->
            ConfirmarEnHoja(
                pregunta = PREGUNTA_REVOCAR,
                detalle = DETALLE_REVOCAR,
                textoConfirmar = "Revocar",
                ocupado = revocando,
                onConfirmar = {
                    revocando = true
                    alcance.launch {
                        runCatching { Repositories.compartir.revocar(enlace.id) }
                            .onSuccess { enlaces = enlaces.filterNot { it.id == enlace.id } }
                            .onFailure { error = it.toUserMessage() }
                        revocando = false
                        porRevocar = null
                        loadKey++
                    }
                },
                onCancelar = { porRevocar = null },
            )
        }
    }
}

@Composable
private fun QueVaAVer() {
    MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated, padding = PaddingValues(18.dp)) {
        Text(QUE_ES_COMPARTIR, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        Spacer(Modifier.height(Movi.espacios.amplio))
        Text("LO QUE VA A VER", style = Movi.textos.rotulo, color = Movi.colores.textoApagado)
        Spacer(Modifier.height(Movi.espacios.corto))
        LO_QUE_VA_A_VER.forEach { renglon ->
            Row(modifier = Modifier.padding(vertical = Movi.espacios.minimo)) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Movi.colores.entra,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp),
                )
                Spacer(Modifier.width(Movi.espacios.corto))
                Text(renglon, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
        Spacer(Modifier.height(Movi.espacios.medio))
        Text(LO_QUE_NO_VA_A_VER, style = Movi.textos.apoyo, color = Movi.colores.texto)
    }
}

@Composable
private fun CrearEnlace(
    dias: Int,
    onDias: (Int) -> Unit,
    creando: Boolean,
    error: String?,
    onCrear: () -> Unit,
) {
    Text(
        "¿POR CUÁNTO TIEMPO?",
        style = Movi.textos.rotulo,
        color = Movi.colores.textoApagado,
        modifier = Modifier.padding(horizontal = Movi.espacios.corto),
    )
    Spacer(Modifier.height(Movi.espacios.corto))
    Row(horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto)) {
        VIGENCIAS_DE_ENLACE.forEach { opcion ->
            val elegida = opcion == dias
            Text(
                rotuloDeVigencia(opcion),
                style = Movi.textos.cuerpo,
                color = if (elegida) Movi.colores.sobreMarca else Movi.colores.textoMedio,
                modifier = Modifier
                    .clip(RoundedCornerShape(Movi.formas.pleno))
                    .background(if (elegida) Movi.colores.marca else Movi.colores.tarjeta)
                    .border(1.dp, if (elegida) Movi.colores.marca else Movi.colores.borde, RoundedCornerShape(Movi.formas.pleno))
                    .clickable(enabled = !creando) { onDias(opcion) }
                    .padding(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.corto),
            )
        }
    }
    Spacer(Modifier.height(Movi.espacios.amplio))
    BotonPrincipal(
        texto = if (creando) "Creando…" else "Crear enlace",
        icono = Icons.Rounded.Share,
        habilitado = !creando,
        onClick = onCrear,
    )
    error?.let {
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.padding(horizontal = Movi.espacios.corto))
    }
}

@Composable
private fun EnlaceListo(
    url: String,
    diasDelEnlace: Int,
    abreLaHojaDelSistema: Boolean,
    copiado: Boolean,
    onCompartir: () -> Unit,
    onCopiar: () -> Unit,
    onOtro: () -> Unit,
) {
    MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.High, padding = PaddingValues(18.dp)) {
        Text("Tu enlace está listo", style = Movi.textos.titulo, color = Movi.colores.texto)
        Spacer(Modifier.height(Movi.espacios.minimo))
        Text("Vale ${rotuloDeVigencia(diasDelEnlace)}.", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Movi.espacios.medio))
        // Seleccionable: si copiar falla por lo que sea, igual se puede marcar y copiar a mano.
        SelectionContainer {
            Text(
                url,
                style = Movi.textos.monto,
                color = Movi.colores.texto,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Movi.formas.normal))
                    .background(Movi.colores.fondo)
                    .padding(Movi.espacios.medio),
            )
        }
        Spacer(Modifier.height(Movi.espacios.medio))
        if (abreLaHojaDelSistema) {
            BotonPrincipal(texto = "Compartir", icono = Icons.Rounded.Share, habilitado = true, onClick = onCompartir)
            Spacer(Modifier.height(Movi.espacios.corto))
            BotonSecundario(texto = if (copiado) "Copiado" else "Copiar enlace", onClick = onCopiar)
        } else {
            BotonPrincipal(
                texto = if (copiado) "Copiado" else "Copiar enlace",
                icono = Icons.Rounded.ContentCopy,
                habilitado = true,
                onClick = onCopiar,
            )
        }
        Spacer(Modifier.height(Movi.espacios.medio))
        Text(SE_COMPARTE_AHORA, style = Movi.textos.apoyo, color = Movi.colores.aviso)
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(
            "Crear otro enlace",
            style = Movi.textos.cuerpo,
            color = Movi.colores.marca,
            modifier = Modifier
                .clip(RoundedCornerShape(Movi.formas.normal))
                .clickable(onClick = onOtro)
                .padding(vertical = Movi.espacios.corto),
        )
    }
}

@Composable
private fun FilaDeEnlace(enlace: EnlaceCompartido, ahora: Long, onRevocar: () -> Unit) {
    MinCard(modifier = Modifier.fillMaxWidth(), variant = MinCardVariant.Elevated, padding = PaddingValues(Movi.espacios.amplio)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Resumen · ${textoDeVencimiento(enlace.venceEn, ahora)}", style = Movi.textos.cuerpo, color = Movi.colores.texto)
                Spacer(Modifier.height(2.dp))
                Text(
                    textoDeVistas(enlace.ultimaVista, enlace.vistas, ahora),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
            }
            Text(
                "Revocar",
                style = Movi.textos.cuerpo,
                color = Movi.colores.sale,
                modifier = Modifier
                    .clip(RoundedCornerShape(Movi.formas.normal))
                    .clickable(onClick = onRevocar)
                    .padding(horizontal = Movi.espacios.medio, vertical = Movi.espacios.corto),
            )
        }
    }
}

@Composable
private fun BotonPrincipal(texto: String, icono: ImageVector, habilitado: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Movi.formas.amplia))
            .background(Movi.colores.marca)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = Movi.colores.sobreMarca, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Movi.espacios.corto))
        Text(texto, style = Movi.textos.cuerpo, color = Movi.colores.sobreMarca)
    }
}

@Composable
private fun BotonSecundario(texto: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Movi.formas.amplia))
            .border(1.dp, Movi.colores.borde, RoundedCornerShape(Movi.formas.amplia))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = Movi.colores.marca, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Movi.espacios.corto))
        Text(texto, style = Movi.textos.cuerpo, color = Movi.colores.marca)
    }
}
