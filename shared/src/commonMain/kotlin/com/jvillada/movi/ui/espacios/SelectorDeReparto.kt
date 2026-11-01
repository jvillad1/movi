package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FormaDeRepartir
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.RepartoDelEspacio
import com.jvillada.movi.shared.model.RepartoDelGasto
import com.jvillada.movi.shared.model.esDelGrupo
import com.jvillada.movi.shared.model.idsDeMiembros
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.problemaDelReparto
import com.jvillada.movi.theme.Espacio as EspacioDeDiseno
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RotuloDeCampo
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.formatCOP
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * # El selector de reparto (vuelta 97)
 *
 * Cada gasto compartido puede llevar su propio reparto (`RepartoDelGasto`, en `:core`). Este es el
 * único selector, y va en las tres hojas que reparten: «Repartir» del detalle de un gasto
 * ([HojaDeRepartirElGasto], que también edita el reparto y abre la propuesta de Movi), «Repartir este
 * gasto» de Agregar y «Pagó otro».
 *
 * Es **compacto**: una fila de píldoras con «Partes iguales» elegida de entrada (la regla del grupo, la
 * de siempre), y solo la forma elegida despliega sus campos —un porcentaje o un monto por persona, o
 * de quién es todo—. Debajo, la línea en vivo: «Te toca $X · a Caro $Y» cuando cuadra, o «Faltan $X» /
 * «Te pasaste por $X» cuando no; la hoja no deja guardar hasta que cuadre. La validación es la de
 * `:core` (`problemaDelReparto`), la misma que el server usa para contestar 422.
 */

/** Tags para las pruebas. */
const val TAG_SELECTOR_DE_REPARTO: String = "espacios:selector-de-reparto"
const val TAG_LINEA_DEL_REPARTO: String = "espacios:linea-del-reparto"
const val TAG_HOJA_DE_REPARTIR: String = "espacios:hoja-de-repartir"
const val TAG_CONFIRMAR_EL_REPARTO: String = "espacios:confirmar-el-reparto"

/** El tag de la píldora de una forma. */
fun tagDeLaForma(forma: FormaDeRepartir): String = "espacios:forma:${forma.name}"

/** El tag del campo de una persona (porcentaje o monto). */
fun tagDelCampoDelReparto(miembroId: String): String = "espacios:campo-del-reparto:$miembroId"

/** Las formas, en el orden de las píldoras. */
val FORMAS_DE_REPARTIR: List<FormaDeRepartir> =
    listOf(FormaDeRepartir.DEL_GRUPO, FormaDeRepartir.PORCENTAJES, FormaDeRepartir.MONTOS, FormaDeRepartir.SOLO_DE)

/** Los miembros que no son el dueño. */
private fun otrosDe(espacio: Espacio): List<String> = idsDeMiembros(espacio).filter { it != MIEMBRO_YO }

/**
 * El rótulo de la píldora de [forma]. La regla del grupo se llama «Partes iguales» en un grupo de
 * partes iguales (lo de siempre) y «Como el grupo» en uno de porcentajes. «Solo de Caro» con una sola
 * persona más; con varias, «Solo de una persona» y se elige quién.
 */
fun rotuloDeLaForma(forma: FormaDeRepartir, espacio: Espacio): String = when (forma) {
    FormaDeRepartir.DEL_GRUPO -> if (espacio.reparto == RepartoDelEspacio.PARTES_IGUALES) "Partes iguales" else "Como el grupo"
    FormaDeRepartir.PORCENTAJES -> "Porcentajes"
    FormaDeRepartir.MONTOS -> "Montos exactos"
    FormaDeRepartir.SOLO_DE -> otrosDe(espacio).singleOrNull()?.let { "Solo de ${nombreEnElEspacio(espacio, it)}" } ?: "Solo de una persona"
}

/**
 * **El reparto con que arranca una forma al elegirla**: los porcentajes del grupo (o partes iguales en
 * enteros, el resto al dueño), los montos de la regla del grupo ([partesDelGasto]) y, en «Solo de», la
 * primera persona que no es el dueño. Así elegir una forma ya cuadra y solo se corrige lo distinto.
 */
fun repartoAlElegir(forma: FormaDeRepartir, monto: Long, espacio: Espacio, pagadoPor: String): RepartoDelGasto {
    val ids = idsDeMiembros(espacio)
    return when (forma) {
        FormaDeRepartir.DEL_GRUPO -> RepartoDelGasto.DEL_GRUPO
        FormaDeRepartir.PORCENTAJES -> {
            val delGrupo = espacio.miembros.associate { it.id to (it.porcentaje ?: 0) }
            val porcentajes = if (espacio.reparto == RepartoDelEspacio.PORCENTAJES && ids.sumOf { delGrupo[it] ?: 0 } == 100) {
                ids.associateWith { delGrupo[it] ?: 0 }
            } else {
                val cada = 100 / ids.size
                ids.associateWith { if (it == MIEMBRO_YO) cada + 100 - cada * ids.size else cada }
            }
            RepartoDelGasto(FormaDeRepartir.PORCENTAJES, porcentajes = porcentajes)
        }
        FormaDeRepartir.MONTOS -> RepartoDelGasto(
            FormaDeRepartir.MONTOS,
            montos = if (monto > 0L) partesDelGasto(monto, espacio, pagadoPor) else ids.associateWith { 0L },
        )
        FormaDeRepartir.SOLO_DE -> RepartoDelGasto(FormaDeRepartir.SOLO_DE, soloDe = otrosDe(espacio).firstOrNull() ?: MIEMBRO_YO)
    }
}

/** La línea en vivo del selector: lo que le toca a cada uno, o lo que falta para cuadrar. */
data class LineaDelReparto(val texto: String, val cuadra: Boolean, val sobra: Boolean = false)

/**
 * **La línea en vivo**: «Te toca $70.000 · a Caro $30.000» cuando el reparto cuadra (con las partes de
 * [partesDelGasto], las mismas que guarda el server); si no, el problema de `:core` —«Faltan $10.000»,
 * «Te pasaste por 5 %»—. `null` sin monto: no hay nada que repartir todavía.
 */
fun lineaDelReparto(monto: Long, espacio: Espacio, pagadoPor: String, reparto: RepartoDelGasto?): LineaDelReparto? {
    if (monto <= 0L) return null
    problemaDelReparto(reparto, monto, espacio)?.let { return LineaDelReparto(it, cuadra = false, sobra = it.startsWith("Te pasaste")) }
    val partes = partesDelGasto(monto, espacio, pagadoPor, reparto)
    val texto = idsDeMiembros(espacio).joinToString(" · ") { id ->
        val parte = formatCOP(partes[id] ?: 0L)
        if (id == MIEMBRO_YO) "Te toca $parte" else "a ${nombreEnElEspacio(espacio, id)} $parte"
    }
    return LineaDelReparto(texto, cuadra = true)
}

/**
 * **El reparto en pocas palabras**, para la propuesta («Casa con Caro, 50/50»): «50/50» en partes
 * iguales de dos, «en partes iguales» de más; los porcentajes con barras («70/30», el dueño primero);
 * los montos por persona; «solo de Caro» o «solo tuyo».
 */
fun textoCortoDelReparto(reparto: RepartoDelGasto?, espacio: Espacio): String {
    val ids = idsDeMiembros(espacio)
    if (esDelGrupo(reparto)) {
        return when {
            espacio.reparto == RepartoDelEspacio.PORCENTAJES ->
                ids.joinToString("/") { id -> (espacio.miembros.firstOrNull { it.id == id }?.porcentaje ?: 0).toString() }
            ids.size == 2 -> "50/50"
            else -> "en partes iguales"
        }
    }
    val r = reparto!!
    return when (r.forma) {
        FormaDeRepartir.DEL_GRUPO -> "50/50"
        FormaDeRepartir.PORCENTAJES -> ids.joinToString("/") { (r.porcentajes[it] ?: 0).toString() }
        FormaDeRepartir.MONTOS -> ids.joinToString(" · ") { "${nombreEnElEspacio(espacio, it)} ${formatCOP(r.montos[it] ?: 0L)}" }
        FormaDeRepartir.SOLO_DE -> if (r.soloDe == MIEMBRO_YO) "solo tuyo" else "solo de ${nombreEnElEspacio(espacio, r.soloDe ?: "")}"
    }
}

/**
 * **El selector de reparto** (ver el KDoc del archivo). [reparto] es el elegido; [onReparto] recibe
 * cada cambio. [pagadoPor] decide quién pone el peso que sobra en la línea en vivo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectorDeReparto(
    espacio: Espacio,
    monto: Long,
    pagadoPor: String,
    reparto: RepartoDelGasto,
    onReparto: (RepartoDelGasto) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val ids = idsDeMiembros(espacio)
    Column(modifier = modifier.fillMaxWidth().testTag(TAG_SELECTOR_DE_REPARTO)) {
        RotuloDeCampo("¿Cómo se reparte?")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs),
            verticalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs),
        ) {
            FORMAS_DE_REPARTIR.forEach { forma ->
                PildoraDeFiltro(
                    rotuloDeLaForma(forma, espacio),
                    seleccionada = reparto.forma == forma,
                    onClick = { if (habilitado && reparto.forma != forma) onReparto(repartoAlElegir(forma, monto, espacio, pagadoPor)) },
                    modifier = Modifier.testTag(tagDeLaForma(forma)),
                )
            }
        }
        when (reparto.forma) {
            FormaDeRepartir.PORCENTAJES -> {
                // El texto de cada campo vive acá mientras se escribe: borrar el «50» para escribir «70» no
                // vuelve a pintar un «0».
                val textos = remember(reparto.forma) { mutableStateMapOf<String, String>() }
                Spacer(Modifier.height(EspacioDeDiseno.s))
                CamposPorPersona(ids) { id, mod ->
                    Campo(
                        valor = textos[id] ?: (reparto.porcentajes[id] ?: 0).toString(),
                        alCambiar = { t ->
                            val digitos = t.filter { it.isDigit() }.take(3)
                            textos[id] = digitos
                            onReparto(reparto.copy(porcentajes = reparto.porcentajes + (id to (digitos.toIntOrNull() ?: 0))))
                        },
                        modifier = mod,
                        rotulo = nombreEnElEspacio(espacio, id),
                        sufijo = "%",
                        habilitado = habilitado,
                        teclado = KeyboardOptions(keyboardType = KeyboardType.Number),
                        tag = tagDelCampoDelReparto(id),
                    )
                }
            }
            FormaDeRepartir.MONTOS -> {
                Spacer(Modifier.height(EspacioDeDiseno.s))
                CamposPorPersona(ids) { id, mod ->
                    CampoDeMonto(
                        valor = reparto.montos[id]?.takeIf { it > 0L },
                        alCambiar = { v -> onReparto(reparto.copy(montos = reparto.montos + (id to (v ?: 0L)))) },
                        modifier = mod,
                        rotulo = nombreEnElEspacio(espacio, id),
                        habilitado = habilitado,
                        tag = tagDelCampoDelReparto(id),
                    )
                }
            }
            FormaDeRepartir.SOLO_DE -> if (ids.size > 2) {
                Spacer(Modifier.height(EspacioDeDiseno.s))
                RotuloDeCampo("¿De quién es?")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs),
                    verticalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs),
                ) {
                    ids.forEach { id ->
                        PildoraDeFiltro(
                            nombreEnElEspacio(espacio, id),
                            seleccionada = reparto.soloDe == id,
                            onClick = { if (habilitado) onReparto(reparto.copy(soloDe = id)) },
                        )
                    }
                }
            }
            FormaDeRepartir.DEL_GRUPO -> Unit
        }
        lineaDelReparto(monto, espacio, pagadoPor, reparto)?.let { linea ->
            Spacer(Modifier.height(EspacioDeDiseno.xs))
            Text(
                linea.texto,
                style = if (linea.cuadra) Movi.textos.cuerpo else Movi.textos.cuerpoFuerte,
                color = when {
                    linea.cuadra -> Movi.colores.textoMedio
                    linea.sobra -> Movi.colores.sale
                    else -> Movi.colores.texto
                },
                modifier = Modifier.testTag(TAG_LINEA_DEL_REPARTO),
            )
        }
    }
}

/** Un campo por persona: dos lado a lado; más, uno debajo del otro. */
@Composable
private fun CamposPorPersona(ids: List<String>, campo: @Composable (String, Modifier) -> Unit) {
    if (ids.size <= 2) {
        Row(horizontalArrangement = Arrangement.spacedBy(EspacioDeDiseno.s)) {
            ids.forEach { id -> campo(id, Modifier.weight(1f)) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(EspacioDeDiseno.xs)) {
            ids.forEach { id -> campo(id, Modifier.fillMaxWidth()) }
        }
    }
}

/**
 * **La hoja «Repartir»** de un gasto tuyo: el selector y «Repartir». La abren el detalle de un gasto
 * (una fila por grupo), «Cambiar el reparto» de un gasto ya repartido (con su reparto de entrada) y la
 * propuesta de Movi (con el de la última vez). [onRepartir] es la escritura de siempre
 * (`POST /api/espacios/{id}/compartir`); mientras trabaja la hoja no acepta otro toque, y el server
 * reparte un movimiento una sola vez: dos toques, un reparto. Nunca reparte sola.
 */
@Composable
fun HojaDeRepartirElGasto(
    espacio: Espacio,
    monto: Long,
    concepto: String?,
    repartoInicial: RepartoDelGasto?,
    onDismiss: () -> Unit,
    onRepartir: suspend (RepartoDelGasto) -> Unit,
    textoDelBoton: String = "Repartir",
) {
    val coroutine = rememberCoroutineScope()
    var reparto by remember { mutableStateOf(repartoInicial ?: RepartoDelGasto.DEL_GRUPO) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val problema = problemaDelReparto(reparto, monto, espacio)

    fun repartir() {
        if (trabajando || problema != null) return
        trabajando = true
        error = null
        coroutine.launch {
            try {
                onRepartir(reparto)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
            } finally {
                trabajando = false
            }
        }
    }

    MarcoDeHoja(onDismiss = onDismiss, dismissEnabled = !trabajando) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false).testTag(TAG_HOJA_DE_REPARTIR),
        ) {
            EncabezadoDeHoja("Repartir en ${espacio.nombre}", subtitulo = "En tus gastos cuenta solo tu parte.")
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            concepto?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
            }
            Cifra(monto, VarianteDeCifra.Grande)
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            SelectorDeReparto(
                espacio = espacio,
                monto = monto,
                pagadoPor = MIEMBRO_YO,
                reparto = reparto,
                onReparto = { reparto = it },
                habilitado = !trabajando,
            )
            Spacer(Modifier.height(Medida.entreSecciones))
            PieDeHoja(
                textoDelBoton,
                onClick = ::repartir,
                habilitado = problema == null && !trabajando,
                // El porqué ya lo dice la línea en vivo, justo arriba.
                razon = null,
                cargando = trabajando,
                error = error,
                modifier = Modifier.testTag(TAG_CONFIRMAR_EL_REPARTO),
            )
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}
