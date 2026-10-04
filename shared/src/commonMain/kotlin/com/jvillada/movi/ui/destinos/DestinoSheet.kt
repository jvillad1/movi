package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.DESTINO_SIN_NOMBRE
import com.jvillada.movi.shared.model.DE_QUIEN_DEMASIADO_LARGO
import com.jvillada.movi.shared.model.MAX_DE_QUIEN
import com.jvillada.movi.shared.model.MAX_NOMBRE_DEL_DESTINO
import com.jvillada.movi.shared.model.NOMBRE_DEL_DESTINO_DEMASIADO_LARGO
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FALTA_NUMERO_O_LLAVE
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.comoLoEntiendeMovi
import com.jvillada.movi.shared.model.comoSeLeeEnLaFicha
import com.jvillada.movi.shared.model.conIdentificadores
import com.jvillada.movi.shared.model.cuentaPropiaConEseNumero
import com.jvillada.movi.shared.model.esLlaveDeQr
import com.jvillada.movi.shared.model.identificadorEscrito
import com.jvillada.movi.shared.model.mensajeDeNumeroPropio
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.otraLecturaDe
import com.jvillada.movi.shared.model.rechazoDelIdentificador
import com.jvillada.movi.shared.model.tipoOPersona
import com.jvillada.movi.shared.model.tipoParaGuardar
import com.jvillada.movi.shared.model.todosLosIdentificadores
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ConfirmacionEnLinea
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.credits.FieldBox
import com.jvillada.movi.ui.credits.SectionLabel
import kotlinx.coroutines.launch

/**
 * # Guardar o editar una persona o comercio
 *
 * **Lo indispensable y nada más** (4-oct-2026). Al crear: el **nombre** y **un solo campo «número o
 * llave»** que Movi clasifica ([identificadorEscrito]) y que se da vuelta con un toque si se
 * equivocó. Persona o comercio viene deducido y es una línea con «Cambiar», no una pregunta. La nota
 * («esposa») queda detrás de un enlace: el nombre casi siempre ya dice de quién es.
 *
 * Al editar, además: **cómo lo reconoce Movi** (agregar y quitar números o llaves — antes vivía en la
 * ficha, y la hoja de editar solo veía el primer número y la primera llave), **unir con otra
 * ficha** si es la misma persona, y **eliminar**.
 *
 * ### Lo que se dejó afuera
 *
 * - **Número y llave como dos campos.** Eran dos campos con su párrafo cada uno para un solo dato:
 *   casi siempre se tiene uno de los dos.
 * - **«Mover a otra persona»** por identificador: lo cubren «Quitar» y, para el caso de verdad (la
 *   misma persona guardada dos veces), «Unir». El server lo sigue aceptando.
 *
 * ### La guarda que se muestra ACÁ y no solo en el server
 *
 * Si el número que escribe es el de una cuenta suya, la hoja lo dice antes de mandar nada (ver
 * [cuentaPropiaConEseNumero]). Y borrar o unir **no toca un solo movimiento**: se dice.
 */
@Composable
fun DestinoSheet(
    cuentas: List<Account>,
    existente: DestinoConocido?,
    onDismiss: () -> Unit,
    onGuardado: () -> Unit,
    /** Las otras fichas guardadas: con cuál se puede unir esta. */
    otros: List<DestinoConocido> = emptyList(),
) {
    val alcance = rememberCoroutineScope()
    val editando = existente != null
    var actual by remember { mutableStateOf(existente) }
    var nombre by remember { mutableStateOf(existente?.nombre ?: "") }
    var deQuien by remember { mutableStateOf(existente?.deQuien ?: "") }
    // La nota se despliega sola cuando ya había una: esconder un dato guardado sería perderlo de vista.
    var conNota by remember { mutableStateOf(!existente?.deQuien.isNullOrBlank()) }
    var escrito by remember { mutableStateOf("") }
    var leidoAlReves by remember { mutableStateOf(false) }
    var tipoElegido by remember { mutableStateOf<TipoDeTercero?>(null) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pidiendoBorrar by remember { mutableStateOf(false) }
    var eligiendoConQuienUnir by remember { mutableStateOf(false) }
    var unirCon by remember { mutableStateOf<DestinoConocido?>(null) }

    // Lo que escribió en «Número o llave», como lo entiende Movi (o dado vuelta, si lo pidió).
    val leido = identificadorEscrito(escrito)
    val identificador = if (leidoAlReves) leido?.let(::otraLecturaDe) ?: leido else leido
    val propia = remember(identificador, cuentas) {
        identificador?.takeIf { it.tipo == TipoDeIdentificador.NUMERO }?.let { cuentaPropiaConEseNumero(it.valor, cuentas) }
    }
    val tipoDeducido = actual?.tipoOPersona()
        ?: if (identificador != null && esLlaveDeQr(identificador)) TipoDeTercero.COMERCIO else TipoDeTercero.PERSONA
    val tipo = tipoElegido ?: tipoDeducido

    val loQueFalta = rechazoDelNombre(nombre, deQuien) ?: if (editando) null else {
        when (identificador) {
            null -> FALTA_NUMERO_O_LLAVE
            else -> rechazoDelIdentificador(identificador) ?: propia?.let { mensajeDeNumeroPropio(it.name) }
        }
    }
    val sePuedeGuardar = loQueFalta == null && !guardando

    fun guardar() {
        if (!sePuedeGuardar) return
        guardando = true
        error = null
        alcance.launch {
            val resultado = runCatching {
                val base = actual
                if (base != null) {
                    Repositories.wallets.updateDestino(
                        base.id,
                        base.copy(
                            nombre = nombre.trim(),
                            deQuien = deQuien.trim().ifBlank { null },
                            tipo = tipoParaGuardar(base, tipo),
                        ),
                    )
                } else {
                    Repositories.wallets.createDestino(
                        DestinoConocido(
                            nombre = nombre.trim(),
                            numero = "",
                            deQuien = deQuien.trim().ifBlank { null },
                            // Solo si lo eligió: lo deducido lo vuelve a deducir el server al leer.
                            tipo = tipoElegido,
                        ).conIdentificadores(listOf(identificador!!)),
                    )
                }
            }
            guardando = false
            resultado.onSuccess { onGuardado() }.onFailure { error = it.toUserMessage() }
        }
    }

    fun hacer(bloque: suspend () -> Unit) {
        if (guardando) return
        guardando = true
        error = null
        alcance.launch {
            runCatching { bloque() }.onFailure { error = it.toUserMessage() }
            guardando = false
        }
    }

    MarcoDeHoja(
        onDismiss = onDismiss,
        dismissEnabled = !guardando,
        onEscape = {
            when {
                pidiendoBorrar -> pidiendoBorrar = false
                unirCon != null -> unirCon = null
                !guardando -> onDismiss()
            }
        },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
                .testTag(TAG_HOJA_DEL_TERCERO),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (editando) "Editar «${existente!!.nombre}»" else NUEVA_PERSONA_O_COMERCIO,
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                    modifier = Modifier.weight(1f),
                )
                if (editando) {
                    Text(
                        text = if (guardando) "…" else "Eliminar",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.sale,
                        modifier = Modifier.clickable(enabled = !guardando) { pidiendoBorrar = true },
                    )
                }
            }
            if (pidiendoBorrar && existente != null) {
                ConfirmacionEnLinea(
                    pregunta = "¿Eliminar «${existente.nombre}»?",
                    detalle = "Se olvidan el nombre y cómo lo reconoce Movi. Tus movimientos no se tocan: " +
                        "siguen ahí, con el nombre que tengan. No se puede deshacer.",
                    textoConfirmar = "Eliminar",
                    ocupado = guardando,
                    onConfirmar = { hacer { Repositories.wallets.deleteDestino(existente.id); onGuardado() } },
                    onCancelar = { pidiendoBorrar = false },
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }

            SectionLabel("NOMBRE")
            Spacer(Modifier.height(8.dp))
            FieldBox("Ej: Ana, Cancha El Gol", nombre, { nombre = it })

            if (!editando) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("NÚMERO O LLAVE")
                Spacer(Modifier.height(8.dp))
                // Se acepta pegado como lo mandó el banco («*00012345678», con guiones o espacios):
                // Movi se queda con lo que identifica y dice qué entendió.
                FieldBox("Ej: *00012345678, 3001234567 o @usuario", escrito, { escrito = it; leidoAlReves = false })
                if (identificador != null) {
                    Spacer(Modifier.height(6.dp))
                    LoQueEntendio(identificador, puedeDarseVuelta = leido?.let(::otraLecturaDe) != null) { leidoAlReves = !leidoAlReves }
                }
            }

            Spacer(Modifier.height(14.dp))
            LineaDelTipo(tipo, enabled = !guardando) { tipoElegido = it }

            Spacer(Modifier.height(10.dp))
            // La nota (`deQuien`) casi nunca hace falta: el nombre ya dice de quién es.
            if (conNota) {
                Spacer(Modifier.height(6.dp))
                SectionLabel("NOTA (OPCIONAL)")
                Spacer(Modifier.height(8.dp))
                FieldBox("Ej: esposa, papá", deQuien, { deQuien = it })
            } else {
                Enlace(AGREGAR_UNA_NOTA, { conNota = true }, enabled = !guardando)
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
            }

            Spacer(Modifier.height(18.dp))
            BotonDeGuardar(
                texto = when {
                    guardando -> "Guardando…"
                    editando -> "Guardar cambios"
                    else -> "Guardar"
                },
                habilitado = sePuedeGuardar,
                onClick = { guardar() },
            )
            // Lo PRIMERO que falta, no un botón gris sin explicación.
            if (!sePuedeGuardar && !guardando && loQueFalta != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = loQueFalta,
                    style = Movi.textos.apoyo,
                    color = if (propia != null) Movi.colores.sale else Movi.colores.textoMedio,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            val base = actual
            if (editando && base != null) {
                Spacer(Modifier.height(22.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
                ComoLoReconoceMovi(
                    destino = base,
                    ocupado = guardando,
                    onQuitar = { id -> hacer { actual = Repositories.wallets.quitarIdentificador(base.id, id) } },
                    onAgregar = { id -> hacer { actual = Repositories.wallets.agregarIdentificador(base.id, AgregarIdentificador(id)) } },
                    cuentas = cuentas,
                )

                if (otros.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Hairline()
                    Spacer(Modifier.height(16.dp))
                    val elegido = unirCon
                    when {
                        elegido != null -> ConfirmacionEnLinea(
                            pregunta = "¿Unir «${base.nombre}» con «${elegido.nombre}»?",
                            detalle = "Sus números y llaves pasan a «${elegido.nombre}» y esta ficha se borra. " +
                                "Tus movimientos no se tocan.",
                            textoConfirmar = "Unir",
                            ocupado = guardando,
                            onConfirmar = { hacer { Repositories.wallets.unirDestinos(base.id, elegido.id); onGuardado() } },
                            onCancelar = { unirCon = null },
                        )
                        !eligiendoConQuienUnir -> Enlace(ES_LA_MISMA_QUE_OTRA, { eligiendoConQuienUnir = true }, enabled = !guardando)
                        else -> {
                            Text("¿Con cuál la unes?", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                            Spacer(Modifier.height(4.dp))
                            ListaDeTerceros(otros, enabled = !guardando, modifier = Modifier.testTag(TAG_UNIR_CON)) { unirCon = it }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

/** El nombre y la nota: lo que se valida siempre, al crear y al editar. La misma regla que el server. */
internal fun rechazoDelNombre(nombre: String, deQuien: String): String? = when {
    nombre.trim().isEmpty() -> DESTINO_SIN_NOMBRE
    nombre.trim().length > MAX_NOMBRE_DEL_DESTINO -> NOMBRE_DEL_DESTINO_DEMASIADO_LARGO
    deQuien.trim().length > MAX_DE_QUIEN -> DE_QUIEN_DEMASIADO_LARGO
    else -> null
}

/** El tag de la hoja de guardar o editar. */
const val TAG_HOJA_DEL_TERCERO: String = "hoja-del-tercero"

/** El tag de la lista de «¿Con cuál la unes?». */
const val TAG_UNIR_CON: String = "unir-con"

/** El título de la hoja al crear. */
const val NUEVA_PERSONA_O_COMERCIO: String = "Nueva persona o comercio"

/** El enlace que abre «unir con otra ficha». */
const val ES_LA_MISMA_QUE_OTRA: String = "Es la misma que otra ficha: unirlas"

/**
 * **Lo que entendió Movi** de lo que se escribió, y el toque que lo da vuelta: «Número de cuenta
 * ·0756 · Es una llave» o «Llave 3001112222 · Es un número de cuenta».
 */
@Composable
internal fun LoQueEntendio(identificador: IdentificadorDelDestino, puedeDarseVuelta: Boolean, onDarVuelta: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(comoLoEntiendeMovi(identificador), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        if (puedeDarseVuelta) {
            Enlace(
                if (identificador.tipo == TipoDeIdentificador.NUMERO) "Es una llave" else "Es un número de cuenta",
                onDarVuelta,
            )
        }
    }
}

/**
 * **Persona o comercio, sin preguntar**: una línea con lo que Movi dedujo y «Cambiar». Un toque lo
 * da vuelta. Ver `tipoInferido` en `:core`.
 */
@Composable
internal fun LineaDelTipo(tipo: TipoDeTercero, enabled: Boolean, onCambiar: (TipoDeTercero) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag(TAG_LINEA_DEL_TIPO),
    ) {
        Text(
            if (tipo == TipoDeTercero.COMERCIO) "Es un comercio" else "Es una persona",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        Enlace(
            if (tipo == TipoDeTercero.COMERCIO) "Es una persona" else "Es un comercio",
            {
                onCambiar(if (tipo == TipoDeTercero.COMERCIO) TipoDeTercero.PERSONA else TipoDeTercero.COMERCIO)
            },
            enabled = enabled,
        )
    }
}

/** El tag de la línea de persona o comercio. */
const val TAG_LINEA_DEL_TIPO: String = "linea-del-tipo"

/**
 * **Cómo lo reconoce Movi**, en la hoja de editar: cada número o llave con «Quitar» (no el último:
 * sin ninguno no reconoce nada), y un solo campo para agregar otro.
 */
@Composable
private fun ComoLoReconoceMovi(
    destino: DestinoConocido,
    ocupado: Boolean,
    cuentas: List<Account>,
    onQuitar: (IdentificadorDelDestino) -> Unit,
    onAgregar: (IdentificadorDelDestino) -> Unit,
) {
    val ids = destino.todosLosIdentificadores()
    var agregando by remember(destino.id) { mutableStateOf(false) }
    var escrito by remember(destino.id) { mutableStateOf("") }
    var alReves by remember(destino.id) { mutableStateOf(false) }
    val leido = identificadorEscrito(escrito)
    val nuevo = if (alReves) leido?.let(::otraLecturaDe) ?: leido else leido
    val propia = nuevo?.takeIf { it.tipo == TipoDeIdentificador.NUMERO }?.let { cuentaPropiaConEseNumero(it.valor, cuentas) }
    val rechazo = nuevo?.let(::rechazoDelIdentificador) ?: propia?.let { mensajeDeNumeroPropio(it.name) }

    SectionLabel("CÓMO LO RECONOCE MOVI")
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag(TAG_COMO_LO_RECONOCE)) {
        ids.forEach { id ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(comoSeLeeEnLaFicha(id), style = Movi.textos.cuerpo, color = Movi.colores.texto)
                    Text(queEsElIdentificador(id), style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                }
                if (ids.size > 1) Enlace("Quitar", { onQuitar(id) }, enabled = !ocupado)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    if (!agregando) {
        Enlace(AGREGAR_UN_IDENTIFICADOR, { agregando = true }, enabled = !ocupado)
    } else {
        FieldBox("Número o llave", escrito, { escrito = it; alReves = false })
        if (nuevo != null) {
            Spacer(Modifier.height(6.dp))
            LoQueEntendio(nuevo, puedeDarseVuelta = leido?.let(::otraLecturaDe) != null) { alReves = !alReves }
        }
        if (rechazo != null) {
            Spacer(Modifier.height(4.dp))
            Text(rechazo, style = Movi.textos.apoyo, color = if (propia != null) Movi.colores.sale else Movi.colores.textoMedio)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pastilla(
                "Agregar",
                { nuevo?.let { onAgregar(it); agregando = false; escrito = "" } },
                principal = true,
                enabled = !ocupado && nuevo != null && rechazo == null,
            )
            Pastilla("Cancelar", { agregando = false; escrito = "" }, enabled = !ocupado)
        }
    }
}

/** El tag de «Cómo lo reconoce Movi». */
const val TAG_COMO_LO_RECONOCE: String = "como-lo-reconoce"

/** El enlace que abre el campo de un identificador más. */
const val AGREGAR_UN_IDENTIFICADOR: String = "Agregar un número o una llave"

/** «Número de cuenta», «Llave», «Como lo nombra el banco». */
internal fun queEsElIdentificador(id: IdentificadorDelDestino): String = when {
    id.tipo == TipoDeIdentificador.NUMERO -> "Número de cuenta"
    com.jvillada.movi.shared.model.laLlaveEsUnNombre(id.valor) -> "Como lo nombra el banco"
    else -> "Llave"
}

/** Las fichas guardadas para elegir una (unir, o «Ya lo tengo guardado»). */
@Composable
internal fun ListaDeTerceros(
    terceros: List<DestinoConocido>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onElegir: (DestinoConocido) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        terceros.sortedBy { normalizarParaBuscar(it.nombre) }.forEach { tercero ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled, role = Role.Button) { onElegir(tercero) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tercero.nombre, style = Movi.textos.cuerpo, color = Movi.colores.texto, modifier = Modifier.weight(1f))
                Text(
                    tercero.todosLosIdentificadores().take(2).joinToString(" · ") { comoSeLeeEnLaFicha(it) },
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoApagado,
                )
            }
        }
    }
}

/** El botón ancho de guardar de las hojas de esta pantalla. */
@Composable
internal fun BotonDeGuardar(texto: String, habilitado: Boolean, onClick: () -> Unit, alto: Int = 54) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(alto.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (habilitado) Movi.colores.marca.copy(alpha = 0.16f) else Movi.colores.tarjeta)
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texto,
            style = Movi.textos.titulo,
            fontWeight = FontWeight.Medium,
            color = if (habilitado) Movi.colores.marca else Movi.colores.textoApagado,
        )
    }
}
