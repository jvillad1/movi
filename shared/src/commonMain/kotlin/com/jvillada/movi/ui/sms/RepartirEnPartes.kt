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
import com.jvillada.movi.shared.model.AVANCE_DE_TARJETA_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.esCategoriaDelDesembolso
import com.jvillada.movi.shared.model.CuadreDeLasPartes
import com.jvillada.movi.shared.model.MAXIMO_DE_PARTES_DEL_AVISO
import com.jvillada.movi.shared.model.PagoParaRepartir
import com.jvillada.movi.shared.model.ParteElegida
import com.jvillada.movi.shared.model.QueFueLaParte
import com.jvillada.movi.shared.model.conElResto
import com.jvillada.movi.shared.model.cuadreDeLasPartes
import com.jvillada.movi.shared.model.estaCompleta
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.operacion
import com.jvillada.movi.shared.model.operacionEntre
import com.jvillada.movi.shared.model.parteDelPago
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
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
    categoria.isNotBlank() && !isReservedCategory(categoria) && categoria != CUOTA_CATEGORY &&
        categoria != AVANCE_DE_TARJETA_CATEGORY && !esCategoriaDelDesembolso(categoria)

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

/** Cómo se nombra cada tipo en su píldora. */
fun nombreDelTipoDeParte(tipo: QueFueLaParte, tercero: TerceroDelAviso?): String = when (tipo) {
    QueFueLaParte.CUOTA -> "Cuota"
    QueFueLaParte.PAGO_DE_TARJETA -> "Pago de tarjeta"
    QueFueLaParte.TRASPASO -> "Traspaso"
    QueFueLaParte.GASTO -> "Gasto"
    QueFueLaParte.A_UN_TERCERO -> tercero?.let { "A ${it.corto}" } ?: "A un tercero"
}

/** Lo que le falta a una parte, en una línea: «Elige el crédito», «Escribe el monto». `null` si está completa. */
fun loQueLeFaltaALaParte(parte: ParteElegida): String? = when {
    parte.estaCompleta -> null
    parte.destinoId.isNullOrBlank() && parte.tipo == QueFueLaParte.CUOTA -> "Elige el crédito"
    parte.destinoId.isNullOrBlank() && parte.tipo == QueFueLaParte.PAGO_DE_TARJETA -> "Elige la tarjeta"
    parte.destinoId.isNullOrBlank() && parte.tipo == QueFueLaParte.TRASPASO -> "Elige la cuenta"
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

/** Los tipos que se ofrecen: «A …» solo si el aviso nombra a alguien. */
fun tiposDeParte(tercero: TerceroDelAviso?): List<QueFueLaParte> =
    QueFueLaParte.entries.filter { it != QueFueLaParte.A_UN_TERCERO || tercero != null }

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
    val conMargen = Modifier.padding(horizontal = Medida.margenDePantalla)
    val cuadre = cuadreDeLasPartes(total, partes.map { it.parte.monto })
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
                        categoriaDelAviso = categoriaDelAviso,
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
                        conResto(parteDeTipo(QueFueLaParte.A_UN_TERCERO, ParteElegida(QueFueLaParte.GASTO), tercero, categoriaDelAviso))
                    })
                }
                PildoraDeAccion(EL_RESTO_COMO_GASTO, habilitada = habilitado, onClick = {
                    conResto(parteDeTipo(QueFueLaParte.GASTO, ParteElegida(QueFueLaParte.CUOTA), tercero, categoriaDelAviso))
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
                        val nueva = parteDeTipo(QueFueLaParte.GASTO, ParteElegida(QueFueLaParte.CUOTA, monto = falta), tercero, categoriaDelAviso)
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

/** Una parte: qué fue, a dónde o en qué, y cuánto. */
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
    categoriaDelAviso: String?,
    habilitado: Boolean,
    onCambiar: (ParteElegida) -> Unit,
    onQuitar: (() -> Unit)?,
) {
    Superficie(modifier = Modifier.fillMaxWidth(), nivel = NivelDeSuperficie.Capa2) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Parte ${indice + 1}",
                    style = Movi.textos.apoyoMedio,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.weight(1f),
                )
                if (onQuitar != null) {
                    Boton("Quitar", onClick = onQuitar, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S, habilitado = habilitado)
                }
            }
            // Qué fue.
            FilaDePildoras {
                tiposDeParte(tercero).forEach { tipo ->
                    PildoraDeFiltro(
                        texto = nombreDelTipoDeParte(tipo, tercero),
                        seleccionada = parte.tipo == tipo,
                        onClick = { if (habilitado) onCambiar(parteDeTipo(tipo, parte, tercero, categoriaDelAviso)) },
                    )
                }
            }
            // A dónde, o en qué.
            when (parte.tipo) {
                QueFueLaParte.CUOTA, QueFueLaParte.PAGO_DE_TARJETA, QueFueLaParte.TRASPASO -> {
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
                QueFueLaParte.GASTO, QueFueLaParte.A_UN_TERCERO -> {
                    if (parte.tipo == QueFueLaParte.A_UN_TERCERO) {
                        parte.nombre?.let { Text(it, style = Movi.textos.cuerpo, color = Movi.colores.texto) }
                    }
                    // Un pago del período que no es una cuota (la mesada, el colegio): queda sellado.
                    val delPeriodo = if (parte.tipo == QueFueLaParte.GASTO) pagos.filter { it.cuentaDeLaDeudaId == null } else emptyList()
                    if (delPeriodo.isNotEmpty()) {
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
