package com.jvillada.movi.ui.sms

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CuadreDeLasPartes
import com.jvillada.movi.shared.model.MAXIMO_DE_PARTES_DEL_AVISO
import com.jvillada.movi.shared.model.PagoParaRepartir
import com.jvillada.movi.shared.model.ParteElegida
import com.jvillada.movi.shared.model.QueFueLaParte
import com.jvillada.movi.shared.model.conElResto
import com.jvillada.movi.shared.model.cuadreDeLasPartes
import com.jvillada.movi.shared.model.estaCompleta
import com.jvillada.movi.shared.model.esCategoriaQueNoSeElige
import com.jvillada.movi.shared.model.operacion
import com.jvillada.movi.shared.model.operacionEntre
import com.jvillada.movi.shared.model.parteDelPago
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.RotuloDeCampo
import com.jvillada.movi.ui.components.ds.PildoraDeAccion
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.quickadd.simboloDeMoneda

/*
 * # «Repartir en partes»: un aviso (o un movimiento ya anotado) que pagó varias cosas
 *
 * Ver `ElAvisoEnPartes.kt` en `:core`. Cada parte dice **qué fue** —con las mismas operaciones que el
 * aviso ya sabe confirmar: la cuota de un crédito suyo, el pago de una tarjeta suya, un traspaso a una
 * cuenta suya, un gasto con su categoría o una transferencia a la persona del aviso— y **cuánto**, con
 * `CampoDeMonto`. Debajo, la línea en vivo: «Faltan $23.333», «Te pasaste por $X» o «Cuadra con el
 * aviso». «El resto a Papá» / «El resto como gasto» convierten lo que falta en una parte más.
 *
 * El botón que confirma no vive acá: es el de la pantalla («Confirmar las N partes» en Reconciliar),
 * que se prende solo cuando [com.jvillada.movi.shared.model.sePuedenConfirmar].
 */

/** Cuántas categorías se ofrecen en un gasto de las partes (ruedan en horizontal). */
internal const val CATEGORIAS_EN_LAS_PARTES: Int = 20

/**
 * ¿[categoria] sirve para un gasto suelto? No las que escribe Movi sola (Traspaso, Pago de tarjeta,
 * Cuota de crédito, el desembolso, el avance): esas son las patas, y una parte con dos patas ya las pone.
 */
fun esCategoriaDeGastoSuelta(categoria: String): Boolean =
    // Vuelta 51: la lista única de `:core` ([esCategoriaQueNoSeElige]): las reservadas y las de dos patas.
    categoria.isNotBlank() && !esCategoriaQueNoSeElige(categoria)

/** [categoria] si sirve para un gasto suelto; si no, `null` (el dueño elige). */
fun categoriaDeGastoOAnada(categoria: String?): String? = categoria?.trim()?.takeIf { esCategoriaDeGastoSuelta(it) }

/** Una parte en pantalla, con una clave estable para que quitar una no le pase el campo a la de al lado. */
data class ParteEnPantalla(val clave: Int, val parte: ParteElegida)

/** Las partes de [elegidas] con claves nuevas, para arrancar el editor (o cambiar de propuesta). */
fun enPantalla(elegidas: List<ParteElegida>): List<ParteEnPantalla> = elegidas.mapIndexed { i, p -> ParteEnPantalla(i, p) }

private fun List<ParteEnPantalla>.siguienteClave(): Int = (maxOfOrNull { it.clave } ?: -1) + 1

/**
 * **La persona del aviso**, para la parte «a un tercero» y para «El resto a …».
 *
 * @property nombre lo que dice el movimiento, «Transferencia a Papá».
 * @property corto cómo se nombra en una píldora, «Papá».
 * @property destinoId el tercero guardado, si Movi lo conoce.
 */
data class TerceroDelAviso(val nombre: String, val corto: String, val destinoId: String? = null)

const val ESTO_PAGO_VARIAS_COSAS: String = "Esto pagó varias cosas"
const val REPARTIR_EN_PARTES: String = "Repartir en partes"
const val VOLVER_A_UN_SOLO_MOVIMIENTO: String = "Volver a un solo movimiento"
const val AGREGAR_OTRA_PARTE: String = "Agregar otra parte"
const val EL_RESTO_COMO_GASTO: String = "El resto como gasto"
const val TAG_EDITOR_DE_PARTES: String = "partes:editor"
const val TAG_CUADRE_DE_LAS_PARTES: String = "partes:cuadre"

/** «Confirmar las 3 partes» (con una sola todavía no hay reparto: «Confirmar las partes», apagado). */
fun botonDeConfirmarLasPartes(cuantas: Int): String = if (cuantas >= 2) "Confirmar las $cuantas partes" else "Confirmar las partes"

/** «El resto a Papá». */
fun elRestoA(tercero: TerceroDelAviso): String = "El resto a ${tercero.corto}"

/** El tag del campo del monto de la parte [i] (desde 0). */
fun tagDelMontoDeLaParte(i: Int): String = "partes:monto:$i"

/** «Faltan $23.333», «Te pasaste por $76.667» o «Cuadra con el aviso». */
fun textoDelCuadre(cuadre: CuadreDeLasPartes, moneda: String): String = when (cuadre) {
    CuadreDeLasPartes.Cuadra -> "Cuadra con el aviso"
    is CuadreDeLasPartes.Faltan -> "Faltan ${formatMoney(cuadre.monto, moneda)}"
    is CuadreDeLasPartes.TePasaste -> "Te pasaste por ${formatMoney(cuadre.monto, moneda)}"
}

/** Cómo se nombra cada tipo en su píldora de «Qué fue». */
fun nombreDelTipoDeParte(tipo: QueFueLaParte): String = when (tipo) {
    QueFueLaParte.CUOTA -> "Cuota"
    QueFueLaParte.PAGO_DE_TARJETA -> "Pago de tarjeta"
    QueFueLaParte.TRASPASO -> "Traspaso"
    QueFueLaParte.GASTO -> "Gasto"
    QueFueLaParte.A_UN_TERCERO -> "A un tercero"
}

/** El rótulo de la fila de «Qué fue». */
const val ROTULO_QUE_FUE: String = "Qué fue"

/** El rótulo de la fila que cada tipo necesita: la categoría de un gasto, el crédito de una cuota… */
fun rotuloDelDetalle(tipo: QueFueLaParte): String = when (tipo) {
    QueFueLaParte.GASTO -> "Categoría"
    QueFueLaParte.CUOTA -> "Crédito"
    QueFueLaParte.PAGO_DE_TARJETA -> "Tarjeta"
    QueFueLaParte.TRASPASO -> "Cuenta"
    QueFueLaParte.A_UN_TERCERO -> "Persona"
}

/** El tag de la fila de «Qué fue» de la parte [i], y el de la fila de su detalle. */
fun tagDelTipoDeLaParte(i: Int): String = "partes:tipo:$i"
fun tagDelDetalleDeLaParte(i: Int): String = "partes:detalle:$i"
fun tagDeLaPersonaDeLaParte(i: Int): String = "partes:persona:$i"

/** El tag de la razón de «Confirmar las partes» apagado. */
const val TAG_RAZON_DE_LAS_PARTES: String = "partes:razon"

/** «Transferencia a Papá» → «Papá»: la persona, para el campo de «A un tercero». */
fun personaDeLaParte(nombre: String?): String = nombre?.removePrefix(TRANSFERENCIA_A)?.trim().orEmpty()

/** «Papá» → «Transferencia a Papá», el texto del movimiento; vacío, nada. */
fun nombreDeLaTransferencia(persona: String): String? = persona.trim().takeIf { it.isNotEmpty() }?.let { "$TRANSFERENCIA_A$it" }

private const val TRANSFERENCIA_A = "Transferencia a "

/**
 * **Por qué «Confirmar las partes» está apagado**, en una línea, como la razón de `PieDeHoja`. `null`
 * si se puede confirmar. Una sola parte no es un reparto; después, lo que le falta a la primera parte
 * incompleta; y si no cuadra, cuánto falta o sobra.
 */
fun razonDeLasPartes(total: Long, partes: List<ParteElegida>, moneda: String): String? {
    if (com.jvillada.movi.shared.model.sePuedenConfirmar(total, partes)) return null
    if (partes.size < 2) return UNA_SOLA_PARTE
    partes.forEachIndexed { i, parte -> loQueLeFaltaALaParte(parte)?.let { return "Parte ${i + 1}: ${it.replaceFirstChar { c -> c.lowercase() }}" } }
    val cuadre = cuadreDeLasPartes(total, partes.map { it.monto })
    return if (cuadre == CuadreDeLasPartes.Cuadra) null else textoDelCuadre(cuadre, moneda)
}

/** La razón con una sola parte: no es un reparto. */
const val UNA_SOLA_PARTE: String = "Una sola parte no es un reparto: agrega otra o vuelve a un solo movimiento"

/** Lo que le falta a una parte, en una línea: «Elige el crédito», «Escribe el monto». `null` si está completa. */
fun loQueLeFaltaALaParte(parte: ParteElegida): String? = when {
    parte.estaCompleta -> null
    parte.destinoId.isNullOrBlank() && parte.tipo == QueFueLaParte.CUOTA -> "Elige el crédito"
    parte.destinoId.isNullOrBlank() && parte.tipo == QueFueLaParte.PAGO_DE_TARJETA -> "Elige la tarjeta"
    parte.destinoId.isNullOrBlank() && parte.tipo == QueFueLaParte.TRASPASO -> "Elige la cuenta"
    parte.nombre.isNullOrBlank() && parte.tipo == QueFueLaParte.A_UN_TERCERO -> "Escribe a quién fue"
    parte.categoria.isNullOrBlank() && (parte.tipo == QueFueLaParte.GASTO || parte.tipo == QueFueLaParte.A_UN_TERCERO) -> "Elige la categoría"
    else -> "Escribe el monto"
}

/**
 * **Las cuentas suyas a las que puede ir una parte de [tipo]** desde [origen]: las que
 * `operacionEntre` acepta para esa operación (una cuota, a un crédito; un pago, a una tarjeta; un
 * traspaso, a dinero o inversión), en la misma moneda.
 */
fun cuentasParaLaParte(tipo: QueFueLaParte, origen: Account?, cuentas: List<Account>): List<Account> {
    val operacion = tipo.operacion ?: return emptyList()
    if (origen == null) return emptyList()
    return cuentas.filter { it.currency == origen.currency && operacionEntre(origen, it) == operacion }
}

/** Los tipos de «Qué fue», en su orden: lo más común primero. Siempre los cinco. */
val TIPOS_DE_PARTE: List<QueFueLaParte> = listOf(
    QueFueLaParte.GASTO, QueFueLaParte.CUOTA, QueFueLaParte.PAGO_DE_TARJETA, QueFueLaParte.TRASPASO, QueFueLaParte.A_UN_TERCERO,
)

/** La parte de [tipo] recién elegida: conserva el monto y propone lo obvio (la categoría del aviso, el tercero). */
fun parteDeTipo(tipo: QueFueLaParte, antes: ParteElegida, tercero: TerceroDelAviso?, categoriaDelAviso: String?): ParteElegida {
    if (tipo == antes.tipo) return antes
    val categoria = categoriaDeGastoOAnada(categoriaDelAviso)
    return when (tipo) {
        QueFueLaParte.GASTO -> ParteElegida(tipo, categoria = categoria, monto = antes.monto)
        QueFueLaParte.A_UN_TERCERO -> ParteElegida(tipo, destinoId = tercero?.destinoId, categoria = categoria, nombre = tercero?.nombre, monto = antes.monto)
        else -> ParteElegida(tipo, monto = antes.monto)
    }
}

/**
 * **El editor de las partes.** [partes] y [onPartes] los tiene la pantalla (su botón confirma);
 * [total] es lo que dice el aviso (o el movimiento), [origen] la cuenta de la que salió.
 *
 * @param categorias las categorías de gasto para elegir, en el orden de la pantalla (las reservadas
 *   no se ofrecen).
 * @param pagos los pagos del período que el aviso puede saldar: elegir el crédito de una cuota pone su
 *   cuota; en un gasto, se pueden elegir como «pago del período» (queda sellado).
 */
@Composable
fun EditorDePartes(
    total: Long,
    moneda: String,
    origen: Account?,
    partes: List<ParteEnPantalla>,
    onPartes: (List<ParteEnPantalla>) -> Unit,
    cuentas: List<Account>,
    categorias: List<String>,
    pagos: List<PagoParaRepartir>,
    tercero: TerceroDelAviso?,
    categoriaDelAviso: String?,
    habilitado: Boolean,
    onVolver: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cuadre = cuadreDeLasPartes(total, partes.map { it.parte.monto })
    // La categoría de un gasto nuevo: la que propone Movi para el aviso, o la primera de las suyas.
    val categoriaPropuesta = categoriaDeGastoOAnada(categoriaDelAviso) ?: categorias.firstOrNull { esCategoriaDeGastoSuelta(it) }
    fun cambiar(clave: Int, nueva: ParteElegida) = onPartes(partes.map { if (it.clave == clave) it.copy(parte = nueva) else it })

    Column(modifier = modifier.fillMaxWidth().testTag(TAG_EDITOR_DE_PARTES)) {
        Seccion(titulo = REPARTIR_EN_PARTES)
        Text(
            buildString {
                append("Cada parte se anota con lo suyo y entre todas suman ${formatMoney(total, moneda)}")
                if (origen != null) append(", que salieron de ${origen.name}")
                append(".")
            },
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.padding(bottom = Medida.dentroDeSeccion),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            partes.forEachIndexed { i, enPantalla ->
                key(enPantalla.clave) {
                    TarjetaDeLaParte(
                        indice = i,
                        parte = enPantalla.parte,
                        moneda = moneda,
                        origen = origen,
                        cuentas = cuentas,
                        categorias = categorias,
                        pagos = pagos,
                        tercero = tercero,
                        categoriaPropuesta = categoriaPropuesta,
                        habilitado = habilitado,
                        onCambiar = { cambiar(enPantalla.clave, it) },
                        onQuitar = if (partes.size > 1) {
                            { onPartes(partes.filter { it.clave != enPantalla.clave }) }
                        } else null,
                    )
                }
            }
        }

        // La línea en vivo: lo que falta, o lo que sobra.
        Text(
            textoDelCuadre(cuadre, moneda),
            style = Movi.textos.cuerpoFuerte,
            color = when (cuadre) {
                CuadreDeLasPartes.Cuadra -> Movi.colores.entra
                is CuadreDeLasPartes.Faltan -> Movi.colores.texto
                is CuadreDeLasPartes.TePasaste -> Movi.colores.sale
            },
            modifier = Modifier.padding(top = Espacio.s).testTag(TAG_CUADRE_DE_LAS_PARTES),
        )

        // «El resto como…»: lo que falta, sin escribirlo.
        val faltan = cuadre is CuadreDeLasPartes.Faltan
        if (faltan && partes.size < MAXIMO_DE_PARTES_DEL_AVISO) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Espacio.xs).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                fun conResto(resto: ParteElegida) {
                    val nuevas = conElResto(total, partes.map { it.parte }, resto) ?: return
                    // Las que ya estaban conservan su clave; la nueva (si hay) toma la siguiente.
                    onPartes(nuevas.mapIndexed { i, p -> partes.getOrNull(i)?.copy(parte = p) ?: ParteEnPantalla(partes.siguienteClave(), p) })
                }
                if (tercero != null) {
                    PildoraDeAccion(elRestoA(tercero), habilitada = habilitado, onClick = {
                        conResto(parteDeTipo(QueFueLaParte.A_UN_TERCERO, ParteElegida(QueFueLaParte.GASTO), tercero, categoriaPropuesta))
                    })
                }
                PildoraDeAccion(EL_RESTO_COMO_GASTO, habilitada = habilitado, onClick = {
                    conResto(parteDeTipo(QueFueLaParte.GASTO, ParteElegida(QueFueLaParte.CUOTA), tercero, categoriaPropuesta))
                })
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Espacio.xs),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (partes.size < MAXIMO_DE_PARTES_DEL_AVISO) {
                Boton(
                    texto = AGREGAR_OTRA_PARTE,
                    onClick = {
                        val falta = (cuadre as? CuadreDeLasPartes.Faltan)?.monto ?: 0L
                        val nueva = parteDeTipo(QueFueLaParte.GASTO, ParteElegida(QueFueLaParte.CUOTA, monto = falta), tercero, categoriaPropuesta)
                        onPartes(partes + ParteEnPantalla(partes.siguienteClave(), nueva))
                    },
                    variante = VarianteDeBoton.Fantasma,
                    tamano = TamanoDeBoton.S,
                    habilitado = habilitado,
                )
            }
            if (onVolver != null) {
                Boton(
                    texto = VOLVER_A_UN_SOLO_MOVIMIENTO,
                    onClick = onVolver,
                    variante = VarianteDeBoton.Fantasma,
                    tamano = TamanoDeBoton.S,
                    habilitado = habilitado,
                )
            }
        }
    }
}

/**
 * **Una parte**: «Qué fue» (los cinco tipos, en una sola fila de `PildoraDeFiltro`), debajo solo lo que
 * ese tipo necesita con su rótulo —la categoría de un gasto, el crédito de una cuota, la tarjeta, la
 * cuenta del traspaso, la persona—, y el monto.
 */
@Composable
private fun TarjetaDeLaParte(
    indice: Int,
    parte: ParteElegida,
    moneda: String,
    origen: Account?,
    cuentas: List<Account>,
    categorias: List<String>,
    pagos: List<PagoParaRepartir>,
    tercero: TerceroDelAviso?,
    categoriaPropuesta: String?,
    habilitado: Boolean,
    onCambiar: (ParteElegida) -> Unit,
    onQuitar: (() -> Unit)?,
) {
    Superficie(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Parte ${indice + 1}",
                    style = Movi.textos.cuerpoFuerte,
                    color = Movi.colores.texto,
                    modifier = Modifier.weight(1f),
                )
                if (onQuitar != null) {
                    Boton("Quitar", onClick = onQuitar, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S, habilitado = habilitado)
                }
            }
            // Qué fue.
            Column(modifier = Modifier.testTag(tagDelTipoDeLaParte(indice))) {
                RotuloDeCampo(ROTULO_QUE_FUE)
                FilaDePildoras {
                    TIPOS_DE_PARTE.forEach { tipo ->
                        PildoraDeFiltro(
                            texto = nombreDelTipoDeParte(tipo),
                            seleccionada = parte.tipo == tipo,
                            onClick = { if (habilitado) onCambiar(parteDeTipo(tipo, parte, tercero, categoriaPropuesta)) },
                        )
                    }
                }
            }
            // Lo que ese tipo necesita, y nada más.
            Column(modifier = Modifier.testTag(tagDelDetalleDeLaParte(indice)), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                when (parte.tipo) {
                    QueFueLaParte.CUOTA, QueFueLaParte.PAGO_DE_TARJETA, QueFueLaParte.TRASPASO -> Column {
                        RotuloDeCampo(rotuloDelDetalle(parte.tipo))
                        val elegibles = cuentasParaLaParte(parte.tipo, origen, cuentas)
                        if (elegibles.isEmpty()) {
                            Text(sinCuentasParaLaParte(parte.tipo), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                        } else {
                            FilaDePildoras {
                                elegibles.forEach { cuenta ->
                                    PildoraDeFiltro(
                                        texto = cuenta.name,
                                        seleccionada = parte.destinoId == cuenta.id,
                                        onClick = {
                                            if (!habilitado) return@PildoraDeFiltro
                                            // Una cuota con su pago del período pendiente toma su monto y lo salda.
                                            val pago = pagos.firstOrNull { it.cuentaDeLaDeudaId == cuenta.id }
                                            onCambiar(
                                                if (parte.tipo == QueFueLaParte.CUOTA && pago != null) {
                                                    parteDelPago(pago, monto = parte.monto.takeIf { it > 0L } ?: pago.monto)
                                                } else {
                                                    ParteElegida(parte.tipo, destinoId = cuenta.id, monto = parte.monto)
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    QueFueLaParte.GASTO -> {
                        // Un pago del período que no es una cuota (la mesada, el colegio): queda sellado.
                        val delPeriodo = pagos.filter { it.cuentaDeLaDeudaId == null }
                        if (delPeriodo.isNotEmpty()) {
                            Column {
                                RotuloDeCampo("Pago del período")
                                FilaDePildoras {
                                    delPeriodo.forEach { pago ->
                                        PildoraDeFiltro(
                                            texto = pago.nombre,
                                            seleccionada = parte.ruleId == pago.ruleId,
                                            onClick = {
                                                if (habilitado) onCambiar(parteDelPago(pago, monto = parte.monto.takeIf { it > 0L } ?: pago.monto))
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        FilaDeCategorias(parte, categorias, habilitado, onCambiar)
                    }
                    QueFueLaParte.A_UN_TERCERO -> {
                        Column {
                            RotuloDeCampo(rotuloDelDetalle(parte.tipo))
                            if (tercero != null) {
                                FilaDePildoras {
                                    PildoraDeFiltro(
                                        texto = tercero.corto,
                                        seleccionada = parte.nombre == tercero.nombre,
                                        onClick = {
                                            if (habilitado) onCambiar(parte.copy(nombre = tercero.nombre, destinoId = tercero.destinoId))
                                        },
                                    )
                                }
                            }
                            if (tercero == null || parte.nombre != tercero.nombre) {
                                Campo(
                                    valor = personaDeLaParte(parte.nombre),
                                    alCambiar = { escrito -> onCambiar(parte.copy(nombre = nombreDeLaTransferencia(escrito), destinoId = null)) },
                                    guia = "¿A quién fue?",
                                    habilitado = habilitado,
                                    tag = tagDeLaPersonaDeLaParte(indice),
                                    modifier = if (tercero != null) Modifier.padding(top = Espacio.xs) else Modifier,
                                )
                            }
                        }
                        FilaDeCategorias(parte, categorias, habilitado, onCambiar)
                    }
                }
            }
            // Cuánto.
            CampoDeMonto(
                valor = parte.monto.takeIf { it > 0L },
                alCambiar = { onCambiar(parte.copy(monto = it ?: 0L)) },
                rotulo = "Monto",
                moneda = simboloDeMoneda(moneda),
                habilitado = habilitado,
                tag = tagDelMontoDeLaParte(indice),
            )
            loQueLeFaltaALaParte(parte)?.let { falta ->
                Text(falta, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
    }
}

/** «Categoría» y sus píldoras, para un gasto o lo que va a un tercero. */
@Composable
private fun FilaDeCategorias(parte: ParteElegida, categorias: List<String>, habilitado: Boolean, onCambiar: (ParteElegida) -> Unit) {
    Column {
        RotuloDeCampo(rotuloDelDetalle(QueFueLaParte.GASTO))
        val opciones = (listOfNotNull(parte.categoria) + categorias).filter { esCategoriaDeGastoSuelta(it) }.distinct()
        FilaDePildoras {
            opciones.forEach { categoria ->
                PildoraDeFiltro(
                    texto = categoria,
                    seleccionada = parte.categoria == categoria,
                    onClick = {
                        if (!habilitado) return@PildoraDeFiltro
                        // Elegir otra categoría suelta el pago del período: ya no es ese.
                        onCambiar(
                            if (parte.ruleId.isNotBlank() && parte.categoria != categoria) {
                                parte.copy(categoria = categoria, ruleId = "", periodo = "", nombre = if (parte.tipo == QueFueLaParte.A_UN_TERCERO) parte.nombre else null)
                            } else parte.copy(categoria = categoria),
                        )
                    },
                )
            }
        }
    }
}

/** Por qué una parte no tiene a dónde ir: «No tienes créditos para esta cuota». */
fun sinCuentasParaLaParte(tipo: QueFueLaParte): String = when (tipo) {
    QueFueLaParte.CUOTA -> "No tienes créditos en Movi para esta cuota."
    QueFueLaParte.PAGO_DE_TARJETA -> "No tienes tarjetas en Movi en esta moneda."
    else -> "No tienes otra cuenta en Movi en esta moneda."
}

/** Una fila de píldoras que rueda en horizontal: no se parte ni se corta a 360 dp. */
@Composable
private fun FilaDePildoras(contenido: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) { contenido() }
}
