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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.DescartarSugerido
import com.jvillada.movi.shared.model.MotivoDeDescarte
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.comoSeDice
import com.jvillada.movi.shared.model.destinoConEseIdentificador
import com.jvillada.movi.shared.model.identificadoresDelDestino
import com.jvillada.movi.shared.model.tipoProbable
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.destinoParaGuardar
import com.jvillada.movi.shared.model.enTituloCaso
import com.jvillada.movi.shared.model.laLlaveEsUnNombre
import com.jvillada.movi.shared.model.nombreSugeridoParaElDestino
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.ofreceGuardarElDestino
import com.jvillada.movi.shared.model.rechazoDelDestino
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.credits.FieldBox
import com.jvillada.movi.ui.LocalNavigate
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.shared.model.MAX_NOMBRE_DEL_DESTINO
import com.jvillada.movi.shared.model.clave
import com.jvillada.movi.shared.model.conIdentificadores
import com.jvillada.movi.shared.model.elDestinoConoce
import com.jvillada.movi.shared.model.normalizado
import com.jvillada.movi.shared.model.normalizarLlave
import com.jvillada.movi.shared.model.nombresCrudosDelBancoEn
import com.jvillada.movi.shared.model.todosLosIdentificadores
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.launch

/** El tag de la fila «¿De quién es la cuenta ·1234?», se muestre donde se muestre. */
const val TAG_GUARDAR_EL_DESTINO: String = "guardar-el-destino"


/**
 * **El enlace que despliega la nota** (`deQuien`: esposa, papá), en la hoja de guardar o editar: el
 * nombre casi siempre ya dice de quién es, así que la relación es un extra que se pide solo si él
 * quiere.
 */
const val AGREGAR_UNA_NOTA: String = "Agregar una nota (ej. esposa)"

/** El enlace que suma el dato a alguien que ya está guardado (4-oct-2026). */
const val YA_LO_TENGO_GUARDADO: String = "Ya lo tengo guardado"

/** El enlace que dice que la cuenta es del dueño: la fila no vuelve a preguntar. */
const val ES_MIA: String = "Es mía"

/** El tag de la lista de terceros que se abre con [ES_DE_UN_TERCERO_QUE_YA_TENGO]. */
const val TAG_TERCEROS_QUE_YA_TENGO: String = "terceros-que-ya-tengo"

/**
 * **La pregunta de la fila**, según lo que el banco haya dicho: «¿De quién es la cuenta ·0756?»,
 * «¿De quién es la llave 0087?», o —cuando lo que hay es el nombre de quien mandó la plata, que ya
 * contesta de quién es— «¿Guardar a Carolina Restrepo Salazar?».
 */
internal fun preguntaDelDestino(identificador: IdentificadorDelDestino): String =
    if (identificador.tipo == TipoDeIdentificador.LLAVE && laLlaveEsUnNombre(identificador.valor)) {
        "¿Guardar a ${identificador.comoSeDice}?"
    } else {
        "¿De quién es ${identificador.comoSeDice}?"
    }

/**
 * **El nombre con que la fila llega prellenada**: el que mandó el banco en Título Caso; si el banco
 * no mandó un nombre de verdad pero el identificador ES un nombre (Nu, «Te llegó dinero de …»), ese;
 * y si no, vacío — un número no es el nombre de nadie y lo escribe él.
 */
internal fun nombreParaLaFila(identificador: IdentificadorDelDestino, nombreDelBanco: String?): String =
    nombreSugeridoParaElDestino(nombreDelBanco).ifEmpty {
        if (identificador.tipo == TipoDeIdentificador.LLAVE && laLlaveEsUnNombre(identificador.valor)) {
            enTituloCaso(identificador.valor)
        } else {
            ""
        }
    }

/**
 * **Los destinos guardados, para decidir si la fila hace falta** — leídos solo cuando [hacenFalta]
 * (hay un identificador en pantalla). `null` mientras no contestó o si la lectura falló: sin saber
 * qué hay guardado **no se ofrece nada**, porque ofrecer guardar una cuenta que ya está guardada es
 * peor que no ofrecerlo (el server lo rechazaría con un 409 que el dueño no entendería).
 */
@Composable
internal fun rememberDestinosParaGuardar(hacenFalta: Boolean): DestinosParaGuardar {
    var destinos by remember { mutableStateOf<List<DestinoConocido>?>(null) }
    var descartados by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(hacenFalta) {
        if (hacenFalta && destinos == null) {
            runCatching { Repositories.wallets.getDestinos() }.onSuccess { destinos = it }
            // Lo que el dueño ya dijo que es suyo o que ignore, y lo que Movi sabe que es suyo (4-oct).
            // Si falla, se pregunta igual: es mejor preguntar de más que callar algo nuevo.
            runCatching { Repositories.wallets.getDestinosDescartados() }.onSuccess { descartados = it.claves.toSet() }
        }
    }
    return DestinosParaGuardar(destinos, descartados) { guardado ->
        destinos = destinos.orEmpty().filterNot { it.id == guardado.id } + guardado
    }
}

/** Lo que devuelve [rememberDestinosParaGuardar]: la lista y cómo sumarle el recién guardado. */
internal class DestinosParaGuardar(
    val guardados: List<DestinoConocido>?,
    val descartados: Set<String> = emptySet(),
    val alGuardar: (DestinoConocido) -> Unit,
) {
    /** El tercero guardado que ya se reconoce por [identificador], si hay uno solo. */
    fun deQuienEs(identificador: IdentificadorDelDestino?): DestinoConocido? =
        identificador?.let { id -> guardados?.let { destinoConEseIdentificador(id, it) } }

    /**
     * ¿Se ofrece la fila para [identificador]? Ver `ofreceGuardarElDestino` en `:core`.
     *
     * **Sin las cuentas del dueño tampoco se ofrece**: la lista vacía es «todavía no llegaron», y
     * sin ella no se puede descartar que el número sea el de una cuenta SUYA — un traspaso entre
     * sus cuentas nombra la de destino igual que una transferencia a otra persona.
     */
    fun ofrece(identificador: IdentificadorDelDestino?, cuentas: List<Account>): Boolean =
        guardados != null && cuentas.isNotEmpty() && ofreceGuardarElDestino(identificador, guardados, cuentas, descartados)
}

/**
 * # «¿Guardar a Marta Prueba Ruiz?» — guardar desde donde aparece, en uno o dos toques
 *
 * La fila que aparece donde el banco nombra una cuenta o una llave que nadie tiene guardada: en
 * «Reconciliar movimiento», en las tarjetas de «Por revisar» y en el detalle de un movimiento.
 * Quien la muestra ya decidió que hace falta ([DestinosParaGuardar.ofrece]).
 *
 * ## Uno o dos toques (4-oct-2026)
 *
 * - **Con el nombre** (el banco lo dijo: «… a MARTA PRUEBA RUIZ», «Te llegó dinero de …»): la fila
 *   cerrada ya es la pregunta y el botón — «¿Guardar a «Marta Prueba Ruiz»? · Guardar». **Un toque.**
 *   Si el nombre es el de alguien guardado, la pregunta es «¿Es de «Caro»? · Sumar»: se le agrega
 *   el dato a esa ficha en vez de crear otra.
 * - **Sin nombre** (un número suelto): «¿De quién es la cuenta ·0756? · Ponerle nombre» abre un solo
 *   campo con su «Guardar». **Dos toques** y el nombre.
 * - **«Otro…»** abre lo mismo para cambiar el nombre, elegir alguien ya guardado («Ya lo tengo
 *   guardado», dos toques) o decir «Es mía».
 *
 * ## Lo que se sacó
 *
 * El selector Persona/Comercio y «Agregar una nota» estaban en la fila abierta: dos decisiones más
 * para guardar un nombre. El tipo lo deduce el server de los avisos (ver `tipoInferido` en `:core`)
 * y la nota se pone después, en la ficha, si alguna vez hace falta.
 *
 * Además del dato que pregunta, se guarda **el nombre con que lo nombra el banco** en ese mismo
 * aviso («a la llave @x … a MARTA PRUEBA RUIZ»), como hacen los sugeridos: así el aviso siguiente lo
 * reconoce aunque llegue por otro lado.
 *
 * [textoDelAviso] es el texto del banco, si quien muestra la fila lo tiene.
 */
@Composable
internal fun FilaGuardarElDestino(
    identificador: IdentificadorDelDestino,
    nombreSugerido: String,
    destinos: List<DestinoConocido>,
    onGuardado: (DestinoConocido) -> Unit,
    modifier: Modifier = Modifier,
    textoDelAviso: String? = null,
) {
    val alcance = rememberCoroutineScope()
    // El nombre que dijo el banco en el aviso, si quien llama no lo pasó.
    val delAviso = remember(textoDelAviso) { textoDelAviso?.let { nombresCrudosDelBancoEn(it).firstOrNull() } }
    val propuesto = nombreSugerido.ifBlank { delAviso?.let(::enTituloCaso).orEmpty() }.take(MAX_NOMBRE_DEL_DESTINO)
    var abierta by remember(identificador) { mutableStateOf(false) }
    var nombre by remember(identificador, propuesto) { mutableStateOf(propuesto) }
    var eligiendoTercero by remember(identificador) { mutableStateOf(false) }
    var esMia by remember(identificador) { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember(identificador) { mutableStateOf<String?>(null) }

    // Los otros datos del mismo aviso que lo reconocen (el nombre del banco junto a una llave), si
    // no son ya de alguien.
    val otros = remember(identificador, delAviso, destinos) {
        listOfNotNull(delAviso?.let { IdentificadorDelDestino(TipoDeIdentificador.LLAVE, normalizarLlave(it)) })
            .filter { it.clave != identificador.normalizado().clave && destinos.none { d -> elDestinoConoce(d, it) } }
    }
    val aGuardar = destinoParaGuardar(identificador, nombre, null, destinos).let { d ->
        if (d.id.isEmpty()) d.conIdentificadores(d.todosLosIdentificadores() + otros) else d
    }
    val loQueFalta = rechazoDelDestino(aGuardar.nombre, aGuardar.numero, aGuardar.deQuien, aGuardar.llave)
    val sePuedeGuardar = loQueFalta == null && !guardando
    // «Caro» ya existe y esto se le suma: se dice, para que no parezca que se crea otra.
    val seSumaA = aGuardar.takeIf { it.id.isNotEmpty() }

    fun guardar() {
        if (!sePuedeGuardar) return
        guardando = true
        error = null
        alcance.launch {
            val resultado = runCatching {
                if (seSumaA != null) Repositories.wallets.agregarIdentificador(seSumaA.id, AgregarIdentificador(identificador))
                else Repositories.wallets.createDestino(aGuardar)
            }
            guardando = false
            resultado.onSuccess { onGuardado(it) }.onFailure { error = it.toUserMessage(); abierta = true }
        }
    }

    fun sumarA(tercero: DestinoConocido) {
        if (guardando) return
        guardando = true
        error = null
        alcance.launch {
            val resultado = runCatching {
                Repositories.wallets.agregarIdentificador(tercero.id, AgregarIdentificador(identificador))
            }
            guardando = false
            resultado.onSuccess { onGuardado(it) }.onFailure { error = it.toUserMessage() }
        }
    }

    fun marcarComoMia() {
        if (guardando) return
        guardando = true
        error = null
        alcance.launch {
            val resultado = runCatching {
                Repositories.wallets.descartarSugerido(DescartarSugerido(identificador, MotivoDeDescarte.ES_MIA))
            }
            guardando = false
            resultado.onSuccess { esMia = true }.onFailure { error = it.toUserMessage() }
        }
    }

    if (esMia) {
        Text(
            "Listo: ${identificador.comoSeDice} es tuya. Movi no vuelve a preguntar.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = modifier.fillMaxWidth().testTag(TAG_GUARDAR_EL_DESTINO),
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth().testTag(TAG_GUARDAR_EL_DESTINO)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                preguntaDeLaFila(identificador, propuesto, seSumaA.takeIf { !abierta }),
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                modifier = Modifier.weight(1f),
            )
            when {
                abierta -> Pastilla("Cerrar", { abierta = false; eligiendoTercero = false }, enabled = !guardando)
                propuesto.isNotBlank() -> {
                    Pastilla(
                        if (guardando) "Guardando…" else if (seSumaA != null) "Sumar" else GUARDAR,
                        { guardar() },
                        principal = true,
                        enabled = sePuedeGuardar,
                        modifier = Modifier.testTag(TAG_GUARDAR_DE_UN_TOQUE),
                    )
                    Pastilla(OTRO, { abierta = true }, enabled = !guardando)
                }
                else -> Pastilla(PONERLE_NOMBRE, { abierta = true }, principal = true, enabled = !guardando)
            }
        }
        if (error != null && !abierta) {
            Spacer(Modifier.height(6.dp))
            Text(error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        if (!abierta) return@Column

        Spacer(Modifier.height(10.dp))
        FieldBox("Nombre. Ej: Ana", nombre, { nombre = it })
        Spacer(Modifier.height(8.dp))
        if (seSumaA != null) {
            Text(
                "Se agrega a «${seSumaA.nombre}», que ya tienes guardada.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (error != null) {
            Text(error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
            Spacer(Modifier.height(6.dp))
        }
        BotonDeGuardar(
            texto = if (guardando) "Guardando…" else GUARDAR,
            habilitado = sePuedeGuardar,
            onClick = { guardar() },
            alto = 46,
        )
        // Lo primero que falta — no un botón gris sin explicación.
        if (loQueFalta != null && !guardando) {
            Spacer(Modifier.height(6.dp))
            Text(loQueFalta, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (destinos.isNotEmpty()) {
                Enlace(YA_LO_TENGO_GUARDADO, { eligiendoTercero = !eligiendoTercero }, enabled = !guardando)
            }
            Enlace(ES_MIA, { marcarComoMia() }, enabled = !guardando)
        }
        if (eligiendoTercero) {
            Spacer(Modifier.height(6.dp))
            ListaDeTerceros(destinos, enabled = !guardando, modifier = Modifier.testTag(TAG_TERCEROS_QUE_YA_TENGO)) { sumarA(it) }
        }
    }
}

/** La pastilla que guarda de un toque. */
const val TAG_GUARDAR_DE_UN_TOQUE: String = "guardar-de-un-toque"

/** «Guardar», en la fila y en su campo. */
const val GUARDAR: String = "Guardar"

/** Abre la fila para cambiar el nombre, elegir alguien guardado o decir «Es mía». */
const val OTRO: String = "Otro…"

/** Abre el campo del nombre cuando el banco no lo dijo. */
const val PONERLE_NOMBRE: String = "Ponerle nombre"

/**
 * La pregunta de la fila: «¿Guardar a «Marta Prueba Ruiz»?» con el nombre, «¿Es de «Caro»?» si ese
 * nombre ya está guardado, o «¿De quién es la cuenta ·0756?» sin nombre.
 */
internal fun preguntaDeLaFila(
    identificador: IdentificadorDelDestino,
    nombre: String,
    seSumaA: DestinoConocido?,
): String = when {
    seSumaA != null -> "¿Es de «${seSumaA.nombre}»? (${identificador.comoSeDice})"
    nombre.isNotBlank() -> "¿Guardar a «$nombre»?"
    else -> preguntaDelDestino(identificador)
}

/** El tag de la fila «Es de Caro · Ver su ficha» del detalle de un movimiento. */
const val TAG_FILA_DEL_TERCERO: String = "fila-del-tercero"

/** El enlace que abre la ficha del tercero desde un movimiento. */
const val VER_SU_FICHA: String = "Ver su ficha"

/**
 * **Quién abre la ficha de un tercero desde adentro de un movimiento.** «Personas y comercios» la abre
 * en el lugar (el movimiento se abrió desde su ficha); en cualquier otra pantalla es `null` y «Ver su
 * ficha» navega a «Personas y comercios» con la ficha abierta.
 */
val LocalAbrirFichaDelTercero = staticCompositionLocalOf<((DestinoConocido) -> Unit)?> { null }

/**
 * **«Es de «Caro», en Personas y comercios · Ver su ficha»**: en el detalle de un movimiento que ya
 * es de alguien guardado. [recienGuardado] = se acaba de guardar desde esta misma hoja.
 *
 * Hasta el 4-oct-2026 la ficha se abría como una hoja ADENTRO de la hoja del movimiento, y en el
 * teléfono —donde la hoja se dibuja en su lugar y no en el anfitrión— salía vacía. Ahora se abre en
 * «Personas y comercios» ([Screen.Destinos] con `abrir`), donde además están «Editar» y la lista.
 */
@Composable
internal fun FilaDelTercero(
    tercero: DestinoConocido,
    recienGuardado: Boolean,
    modifier: Modifier = Modifier,
) {
    val navegar = LocalNavigate.current
    val abrirEnElLugar = LocalAbrirFichaDelTercero.current
    Row(
        modifier = modifier.fillMaxWidth().testTag(TAG_FILA_DEL_TERCERO),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (recienGuardado) "«${tercero.nombre}» quedó en $PERSONAS_Y_COMERCIOS. Este movimiento ya cuenta ahí."
            else "Es de «${tercero.nombre}», en $PERSONAS_Y_COMERCIOS.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.weight(1f),
        )
        Pastilla(
            VER_SU_FICHA,
            { abrirEnElLugar?.invoke(tercero) ?: navegar(Screen.Destinos(abrir = tercero.id)) },
            principal = true,
        )
    }
}
