package com.jvillada.movi.ui.compartir

import com.jvillada.movi.ui.components.ds.Boton
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
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import androidx.compose.material.icons.rounded.Link
import androidx.compose.ui.text.font.FontWeight
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
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
import com.jvillada.movi.data.intentar
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
        // `intentar` y no `runCatching`: una lectura cancelada por el refresco no puede apagar
        // `cargando` encima de la lectura que la reemplaza (ver `data/Intentar.kt`).
        intentar { Repositories.compartir.listar() }
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
                contentPadding = PaddingValues(
                    start = Medida.margenDePantalla,
                    end = Medida.margenDePantalla,
                    top = Medida.dentroDeSeccion,
                    bottom = Medida.botonFlotante + Medida.entreSecciones,
                ),
            ) {
                item {
                    QueVaAVer()
                    Spacer(Modifier.height(Medida.entreSecciones))
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
                    Spacer(Modifier.height(Medida.entreSecciones))
                }

                item {
                    Seccion(
                        titulo = "Enlaces activos",
                        conteo = if (enlaces.isNotEmpty() && !noSeLeyo) enlaces.size else null,
                    )
                    when {
                        noSeLeyo -> NoSePudoLeer(
                            "No pudimos cargar tus enlaces",
                            onReintentar = { loadKey++ },
                        )
                        // Reemplaza al texto suelto de siempre, sin botón propio — el botón
                        // «Crear enlace» de la sección de arriba ya está a la vista en esta misma
                        // pantalla, y repetirlo acá abajo le mostraba a quien recién llega dos
                        // botones idénticos al mismo tiempo.
                        enlaces.isEmpty() && !cargando -> Vacio(
                            titulo = "No tienes enlaces activos",
                            detalle = "Los que crees aparecerán aquí hasta que venzan o los revoques.",
                        )
                    }
                }

                if (!noSeLeyo && enlaces.isNotEmpty()) item {
                    Superficie(relleno = RellenoDeSuperficie.Lista) {
                        enlaces.forEachIndexed { i, enlace ->
                            if (i > 0) Separador(SangriaDeSeparador.Margen)
                            FilaDeEnlace(enlace, ahora, onRevocar = { porRevocar = enlace })
                        }
                    }
                }
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
    Superficie(modifier = Modifier.fillMaxWidth()) {
        Text(QUE_ES_COMPARTIR, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Text("Lo que va a ver", style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Espacio.xs))
        LO_QUE_VA_A_VER.forEach { renglon ->
            Row(modifier = Modifier.padding(vertical = Espacio.xxs), horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Movi.colores.entra,
                    modifier = Modifier.size(Icono.s),
                )
                Text(renglon, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
        Spacer(Modifier.height(Espacio.s))
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
    Seccion(titulo = "¿Por cuánto tiempo?")
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        VIGENCIAS_DE_ENLACE.forEach { opcion ->
            PildoraDeFiltro(
                rotuloDeVigencia(opcion),
                seleccionada = opcion == dias,
                onClick = { if (!creando) onDias(opcion) },
            )
        }
    }
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    Boton(
        texto = "Crear enlace",
        onClick = onCrear,
        icono = Icons.Rounded.Share,
        cargando = creando,
        anchoCompleto = true,
    )
    error?.let {
        Spacer(Modifier.height(Espacio.xs))
        Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
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
    Superficie(modifier = Modifier.fillMaxWidth()) {
        Text("Tu enlace está listo", style = Movi.textos.titulo, color = Movi.colores.texto)
        Spacer(Modifier.height(Espacio.xxs))
        Text("Vale ${rotuloDeVigencia(diasDelEnlace)}.", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Espacio.s))
        // Seleccionable: si copiar falla por lo que sea, igual se puede marcar y copiar a mano.
        SelectionContainer {
            Superficie(modifier = Modifier.fillMaxWidth(), nivel = NivelDeSuperficie.Capa2) {
                // Vuelta de diseño 18: una URL no es plata, así que no va en la letra de las cifras
                // (`monto`). El sistema no tiene un estilo monoespaciado para enlaces y no le hace
                // falta: la superficie de capa 2 y la selección ya lo apartan del texto.
                Text(url, style = Movi.textos.cuerpo, color = Movi.colores.texto)
            }
        }
        Spacer(Modifier.height(Espacio.s))
        if (abreLaHojaDelSistema) {
            Boton("Compartir", onClick = onCompartir, icono = Icons.Rounded.Share, anchoCompleto = true)
            Spacer(Modifier.height(Espacio.xs))
            Boton(
                if (copiado) "Copiado" else "Copiar enlace",
                onClick = onCopiar,
                icono = Icons.Rounded.ContentCopy,
                variante = VarianteDeBoton.Secundario,
                anchoCompleto = true,
            )
        } else {
            Boton(if (copiado) "Copiado" else "Copiar enlace", onClick = onCopiar, icono = Icons.Rounded.ContentCopy, anchoCompleto = true)
        }
        Spacer(Modifier.height(Espacio.s))
        Text(SE_COMPARTE_AHORA, style = Movi.textos.apoyo, color = Movi.colores.aviso)
        Spacer(Modifier.height(Espacio.xs))
        Boton("Crear otro enlace", onClick = onOtro, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S)
    }
}

@Composable
private fun FilaDeEnlace(enlace: EnlaceCompartido, ahora: Long, onRevocar: () -> Unit) {
    Fila(
        titulo = "Resumen · ${textoDeVencimiento(enlace.venceEn, ahora)}",
        subtitulo = textoDeVistas(enlace.ultimaVista, enlace.vistas, ahora),
        inicio = { IconoEnCirculo(Icons.Rounded.Link, tono = TonoDeIcono.Info) },
        final = { Boton("Revocar", onClick = onRevocar, variante = VarianteDeBoton.Peligro, tamano = TamanoDeBoton.S) },
    )
}
