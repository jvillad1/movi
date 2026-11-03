package com.jvillada.movi.ui.ai

import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AccionPropuesta
import com.jvillada.movi.shared.model.EstadoDePropuesta
import com.jvillada.movi.shared.model.OrigenDelRecuerdo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.TipoDeAccion
import com.jvillada.movi.shared.model.sePreguntaSiYaPagasteElDeEstePeriodo
import com.jvillada.movi.ui.recurrentes.ajustesParaPreguntarSiYaPagaste
import com.jvillada.movi.ui.recurrentes.esElPeriodoDeRespaldo
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.recurrentes.EsqueletoDeYaPagaste
import com.jvillada.movi.ui.recurrentes.PreguntaDeYaPagaste
import com.jvillada.movi.ui.recurrentes.RenglonDeLoQueEsperaMovi
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.datetime.LocalDate
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.theme.Movi
import kotlinx.coroutines.launch

/**
 * # Las tarjetas de lo que Movi propone hacer (Ola 3 · «Movi actúa»)
 *
 * El asistente no escribe nada: propone, y el dueño decide aquí con un toque. Dos reglas:
 *
 * - **«Hacerlo» llama al MISMO método del repositorio que usa la pantalla de siempre** —el que llama
 *   al endpoint de siempre, con su validación de siempre—. No hay una puerta nueva que escriba: si
 *   el server rechaza (la categoría era reservada, la cuenta ya no existe), el dueño lee el motivo y
 *   la tarjeta queda sin hacer.
 * - **«No» no toca nada de su plata.** Solo le avisa al asistente, para que en el turno siguiente
 *   sepa que le dijeron que no.
 */

/**
 * Hace lo que la propuesta dice, **con el método de siempre**, y después le avisa al asistente.
 * Lanza si el endpoint de la acción rechaza: el aviso al asistente solo va si la acción se hizo.
 *
 * [yaPagoElDeEstePeriodo] (vuelta 124) es la respuesta a «¿Ya pagaste el de este período?» de la tarjeta
 * de un recurrente nuevo: `null` si la tarjeta no la preguntó (la regla va tal cual la propuso el server).
 */
internal suspend fun hacerLaPropuesta(
    repo: WalletRepository,
    propuesta: AccionPropuesta,
    yaPagoElDeEstePeriodo: Boolean? = null,
) {
    when (propuesta.tipo) {
        TipoDeAccion.ANOTAR_MOVIMIENTO ->
            repo.postEvent(requireNotNull(propuesta.movimiento) { "La propuesta no trae el movimiento" })
        TipoDeAccion.CAMBIAR_CATEGORIA -> {
            val categoria = requireNotNull(propuesta.categoria) { "La propuesta no trae la categoría" }
            val ids = propuesta.idsDeMovimientos
            require(ids.isNotEmpty()) { "La propuesta no trae movimientos" }
            // Uno solo va por la puerta de un movimiento; varios, por la del lote (Ola 23).
            if (ids.size == 1) repo.updateEventCategory(ids.single(), categoria)
            else repo.recategorizarEnLote(ids, categoria)
        }
        TipoDeAccion.CREAR_RECURRENTE -> {
            val regla = requireNotNull(propuesta.recurrente) { "La propuesta no trae el recurrente" }
            repo.createRecurringRule(
                if (yaPagoElDeEstePeriodo == null) regla else regla.copy(yaPagoElDeEstePeriodo = yaPagoElDeEstePeriodo),
            )
        }
        TipoDeAccion.MARCAR_PAGO_HECHO -> repo.markOccurrence(
            ruleId = requireNotNull(propuesta.reglaId),
            period = requireNotNull(propuesta.periodo),
            // Nunca sin movimiento: es la regla del dueño, «el checklist lo tilda el movimiento».
            eventId = requireNotNull(propuesta.eventId) { "Un pago se marca solo con su movimiento" },
        )
        // El mismo POST que usa «Lo que Movi sabe de ti»: nada se recuerda sin este toque.
        TipoDeAccion.RECORDAR -> repo.guardarRecuerdo(
            texto = requireNotNull(propuesta.recuerdo) { "La propuesta no trae qué recordar" },
            origen = OrigenDelRecuerdo.CONVERSACION,
            propuestaId = propuesta.id,
        )
    }
    // La acción ya está hecha; si este aviso no llega, lo único que se pierde es que el asistente
    // lo sepa. No vale tumbar la tarjeta por eso.
    runCatching { repo.resolverPropuesta(propuesta.id, EstadoDePropuesta.HECHA) }
}

/**
 * Vuelta 124 · **¿la tarjeta de [propuesta] pregunta «¿Ya pagaste el de este período?»?** La regla de la
 * hoja de un pago fijo nuevo (vuelta 121, [sePreguntaSiYaPagasteElDeEstePeriodo]): un recurrente nuevo que
 * es un **gasto**, que no nace de un movimiento ya anotado (la regla no trae `activeFrom` ni
 * `eventoDeOrigen`, el mismo criterio de la hoja) y cuyo vencimiento de este período ya pasó o es hoy.
 * Sin ajustes (el perfil se está leyendo), no se pregunta. Pura.
 *
 * - **La rama «desde un movimiento» es defensiva:** hoy `proponer_recurrente` (`LoQueMoviPropone.kt`) nunca
 *   pone `activeFrom` ni `eventoDeOrigen`; está para que una propuesta futura que sí los traiga siga el
 *   criterio de la hoja.
 * - **Si la lectura del perfil falla** (vuelta 125), la tarjeta decide con el mes de calendario, como la hoja
 *   y la guía ([ajustesParaPreguntarSiYaPagaste]).
 */
internal fun laPropuestaPreguntaSiYaPago(propuesta: AccionPropuesta, hoy: LocalDate, ajustes: PeriodSettings?): Boolean {
    if (propuesta.tipo != TipoDeAccion.CREAR_RECURRENTE || ajustes == null) return false
    val regla = propuesta.recurrente ?: return false
    val desdeUnMovimiento = regla.activeFrom != null || regla.eventoDeOrigen != null
    return sePreguntaSiYaPagasteElDeEstePeriodo(regla.type, desdeUnMovimiento, regla.dayOfMonth, hoy, ajustes)
}

/** Vuelta 124 · el tag del renglón de «¿Ya pagaste el de este período?» en la tarjeta de una propuesta. */
internal const val TAG_YA_PAGASTE_EN_LA_PROPUESTA: String = "propuesta-ya-pagaste"

/** Vuelta 124 · el tag del lugar de la pregunta mientras se lee el perfil, y el de «Movi lo espera en este período». */
internal const val TAG_ESPERANDO_EL_PERFIL_EN_LA_PROPUESTA: String = "propuesta-ya-pagaste-leyendo"
internal const val TAG_LO_ESPERA_EN_LA_PROPUESTA: String = "propuesta-lo-espera-en-este-periodo"

/**
 * Revisión de la 124 · **qué va en el lugar de la pregunta** de la tarjeta de un recurrente nuevo. Al llegar
 * al chat la lectura del perfil (`ClaveDeLectura.Perfil`) suele estar vacía —el Inicio no la usa—: si la
 * pregunta apareciera al llegar el perfil, la tarjeta crecería y los botones bajarían, y un «Hacerlo» tocado
 * antes crearía la regla vencida sin el campo. Por eso, en un gasto que no nace de un movimiento, el lugar
 * está **siempre**, del mismo alto (`ui/recurrentes/PreguntaDeYaPagaste.kt`):
 * - [LEYENDO]: el perfil no llegó (ni falló): un esqueleto, y «Hacerlo» apagado;
 * - [PREGUNTA]: la pregunta, «Sí» por defecto;
 * - [EN_ESTE_PERIODO]: su fecha de este período todavía no llega: «Movi lo espera en este período»;
 * - [NINGUNO]: no es un gasto recurrente escrito; o no se sabe el período (sin ajustes y sin estar
 *   leyendo); o, **con el período de respaldo** ([conElPeriodoDeRespaldo], la lectura del perfil falló), la
 *   fecha no venció en el mes de calendario: ahí no se puede afirmar «Movi lo espera en este período» (con
 *   corte 25 el 28-oct pudo haber vencido), así que no se dice nada (revisión de la 125). Con el respaldo
 *   y la fecha vencida, sí se pregunta ([PREGUNTA]).
 */
internal enum class LugarDeLaPregunta { NINGUNO, LEYENDO, PREGUNTA, EN_ESTE_PERIODO }

/** Ver [LugarDeLaPregunta]. [leyendoElPerfil]: la lectura no contestó ni falló. Pura. */
internal fun lugarDeLaPregunta(
    propuesta: AccionPropuesta,
    hoy: LocalDate,
    ajustes: PeriodSettings?,
    leyendoElPerfil: Boolean,
    conElPeriodoDeRespaldo: Boolean = false,
): LugarDeLaPregunta {
    if (propuesta.tipo != TipoDeAccion.CREAR_RECURRENTE) return LugarDeLaPregunta.NINGUNO
    val regla = propuesta.recurrente ?: return LugarDeLaPregunta.NINGUNO
    val desdeUnMovimiento = regla.activeFrom != null || regla.eventoDeOrigen != null
    if (regla.type != TransactionType.EXPENSE || desdeUnMovimiento || regla.dayOfMonth !in 1..31) return LugarDeLaPregunta.NINGUNO
    if (ajustes == null) return if (leyendoElPerfil) LugarDeLaPregunta.LEYENDO else LugarDeLaPregunta.NINGUNO
    return when {
        laPropuestaPreguntaSiYaPago(propuesta, hoy, ajustes) -> LugarDeLaPregunta.PREGUNTA
        conElPeriodoDeRespaldo -> LugarDeLaPregunta.NINGUNO
        else -> LugarDeLaPregunta.EN_ESTE_PERIODO
    }
}

/** Lo que se le dice al dueño si la acción no se pudo hacer. */
internal fun motivoDeLaFalla(falla: Throwable): String =
    (falla as? ApiException)?.serverMessage?.takeIf { it.isNotBlank() && it.length < 200 }
        ?: "No se pudo hacer. Revisa tu conexión e intenta otra vez."

internal const val HACERLO = "Hacerlo"
internal const val NO_HACERLO = "No"
internal const val YA_ESTA_HECHO = "Hecho"
internal const val DESCARTADA = "Descartada"
internal const val ROTULO_DE_LA_PROPUESTA = "Movi propone"

/**
 * Una propuesta, con su estado y sus dos botones. El estado vive en quien la pinta (la pantalla
 * del chat lo guarda por id): una tarjeta que se va de la pantalla al hacer scroll no puede volver
 * con «Hacerlo» después de haberse hecho.
 */
@Composable
internal fun PropuestaDelAsistente(
    propuesta: AccionPropuesta,
    estado: EstadoDePropuesta,
    onEstado: (EstadoDePropuesta) -> Unit,
    repo: () -> WalletRepository = { Repositories.wallets },
    /** Vuelta 124 · el «hoy» de «¿Ya pagaste el de este período?»; `null` = hoy de verdad (las pruebas lo fijan). */
    hoy: LocalDate? = null,
) {
    val coroutine = rememberCoroutineScope()
    var trabajando by remember(propuesta.id) { mutableStateOf(false) }
    var error by remember(propuesta.id) { mutableStateOf<String?>(null) }

    // Vuelta 124 · «¿Ya pagaste el de este período?», como la hoja de un pago fijo nuevo: «Sí» por defecto.
    // El período es el del dueño (con corte 25 no es el mes): mientras se lee el perfil la pregunta no sale
    // (esqueleto), y el server vuelve a comprobarlo antes de aceptar el «Sí». Vuelta 125: si la lectura
    // falla, el mes de calendario, como la hoja y la guía.
    val esUnRecurrentePorHacer = propuesta.tipo == TipoDeAccion.CREAR_RECURRENTE && estado == EstadoDePropuesta.PENDIENTE
    val perfil = rememberLectura(ClaveDeLectura.Perfil, reintento = 0, activa = esUnRecurrentePorHacer) {
        repo().getUserProfile()
    }
    val conElPeriodoDeRespaldo = esElPeriodoDeRespaldo(perfil.valor, perfil.terminada)
    val lugar = if (!esUnRecurrentePorHacer) LugarDeLaPregunta.NINGUNO else lugarDeLaPregunta(
        propuesta,
        hoy ?: hoyEnAppZone(),
        ajustesParaPreguntarSiYaPagaste(perfil.valor, perfil.terminada),
        leyendoElPerfil = perfil.valor == null && !perfil.terminada,
        conElPeriodoDeRespaldo = conElPeriodoDeRespaldo,
    )
    val preguntaSiYaPago = lugar == LugarDeLaPregunta.PREGUNTA
    var yaPago by remember(propuesta.id) { mutableStateOf(true) }

    // Ola R4: una propuesta de Movi va en la superficie de propuesta. Vuelta 42: a todo el ancho de
    // la conversación (desde la sangría de la respuesta), como el texto de Movi. Tenía un tope de
    // 320 dp suelto que a 1.280 la dejaba diminuta en una columna de 720.
    Superficie(
        modifier = Modifier
            .padding(start = Espacio.xxl, bottom = Espacio.s)
            .fillMaxWidth(),
        nivel = NivelDeSuperficie.Propuesta,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Movi.colores.enlace, modifier = Modifier.size(Icono.s))
            Spacer(Modifier.width(Espacio.xs))
            Text(ROTULO_DE_LA_PROPUESTA, style = Movi.textos.apoyoMedio, color = Movi.colores.textoMedio)
        }
        Text(propuesta.frase, style = Movi.textos.cuerpo, color = Movi.colores.texto)

        when (estado) {
            EstadoDePropuesta.HECHA -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.entra, modifier = Modifier.size(Icono.s))
                Spacer(Modifier.width(Espacio.xs))
                Text(YA_ESTA_HECHO, style = Movi.textos.apoyoMedio, color = Movi.colores.entra)
            }
            EstadoDePropuesta.RECHAZADA ->
                Text(DESCARTADA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            EstadoDePropuesta.PENDIENTE -> {
                when (lugar) {
                    LugarDeLaPregunta.PREGUNTA -> PreguntaDeYaPagaste(
                        marcada = yaPago,
                        onCambiar = { yaPago = it },
                        tag = TAG_YA_PAGASTE_EN_LA_PROPUESTA,
                        habilitada = !trabajando,
                        conElPeriodoDeRespaldo = conElPeriodoDeRespaldo,
                    )
                    LugarDeLaPregunta.LEYENDO -> EsqueletoDeYaPagaste(TAG_ESPERANDO_EL_PERFIL_EN_LA_PROPUESTA)
                    LugarDeLaPregunta.EN_ESTE_PERIODO -> RenglonDeLoQueEsperaMovi(TAG_LO_ESPERA_EN_LA_PROPUESTA)
                    LugarDeLaPregunta.NINGUNO -> Unit
                }
                error?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale) }
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    Boton(
                        HACERLO,
                        onClick = {
                            trabajando = true
                            error = null
                            coroutine.launch {
                                runCatching {
                                    hacerLaPropuesta(repo(), propuesta, yaPagoElDeEstePeriodo = if (preguntaSiYaPago) yaPago else null)
                                }
                                    .onSuccess { onEstado(EstadoDePropuesta.HECHA) }
                                    .onFailure { error = motivoDeLaFalla(it) }
                                trabajando = false
                            }
                        },
                        tamano = TamanoDeBoton.S,
                        cargando = trabajando,
                        // Revisión de la 124: sin saber todavía si se pregunta, «Hacerlo» no crea la regla sin el campo.
                        habilitado = lugar != LugarDeLaPregunta.LEYENDO,
                    )
                    Boton(
                        NO_HACERLO,
                        onClick = {
                            onEstado(EstadoDePropuesta.RECHAZADA)
                            // «No» no toca su plata: solo se le cuenta al asistente. Si el aviso
                            // no llega, la tarjeta igual queda descartada en pantalla.
                            coroutine.launch {
                                runCatching { repo().resolverPropuesta(propuesta.id, EstadoDePropuesta.RECHAZADA) }
                            }
                        },
                        variante = VarianteDeBoton.Fantasma,
                        tamano = TamanoDeBoton.S,
                        habilitado = !trabajando,
                    )
                }
            }
        }
      }
    }
}
