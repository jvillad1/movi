package com.jvillada.movi.ui.credits

import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.GrupoDeCampos
import com.jvillada.movi.ui.accounts.tipoLegibleDeLaCuenta
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.RotuloDeCampo
import androidx.compose.foundation.text.KeyboardOptions
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Medida
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.recurrentes.ReminderOptInField
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.components.ds.TamanoDeBoton

/**
 * Crea una tarjeta de crédito o edita los términos de una existente — la hoja hermana de
 * [CreditTermsSheet], con los campos que una tarjeta sí tiene: cupo, corte y día de pago
 * (nada de capital, tasa ni plazo, que son de préstamo).
 *
 * - [editing] != null → edición sobre esa tarjeta (cuenta fija, campos precargados). También es
 *   el camino por el que una tarjeta creada antes desde Cuentas gana corte y pago (PUT upsert).
 * - [editing] == null → creación atómica server-side (cuenta + deuda inicial + términos), mismo
 *   patrón que el alta de crédito. La deuda actual es opcional: una tarjeta recién sacada está
 *   en $0. Moneda COP/USD — las Mastercard en dólares existen.
 */
@Composable
fun CardTermsSheet(
    editing: CardSummary?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val existingTerms = editing?.terms

    var name by remember { mutableStateOf("") }
    var initialDebt by remember { mutableStateOf<Long?>(null) }
    var currency by remember { mutableStateOf(editing?.account?.currency ?: "COP") }
    // Vuelta 31: si el nombre ya dice el banco («Nu»), el campo llega lleno ([bancoSugeridoPara]). Lo
    // que el dueño escribe a mano manda: escribir el nombre después ya no lo pisa.
    var bank by remember { mutableStateOf(existingTerms?.bank ?: editing?.account?.name?.let(::bancoSugeridoPara) ?: "") }
    var bancoAMano by remember { mutableStateOf(existingTerms != null) }
    var creditLimit by remember { mutableStateOf(existingTerms?.creditLimit) }
    var cutoffDay by remember { mutableStateOf(existingTerms?.cutoffDay?.toString() ?: "") }
    // El pago mínimo del extracto. Nullable y sin default: null significa «no lo cargué», no 0.
    // Ver [CardTerms.pagoMinimo].
    var pagoMinimo by remember { mutableStateOf(existingTerms?.pagoMinimo) }
    // Ola 4: la tasa E.A., opcional. Texto y no número por lo mismo que en [CreditTermsSheet]: el
    // filtro deja solo dígitos y una coma o punto. Vacío = «no la sé», y se guarda null.
    var tasaEa by remember { mutableStateOf(existingTerms?.tasaEa?.toString()?.replace('.', ',') ?: "") }
    var paymentDay by remember { mutableStateOf(existingTerms?.paymentDay?.toString() ?: "") }
    // Mismo campo y mismo tratamiento que en [CreditTermsSheet]: vacío se guarda como null.
    // Sin esta línea la hoja armaba el `CardTerms` sin `notes`, y como el PUT escribe todas las
    // columnas, cada «Guardar tarjeta» le borraba la nota a la tarjeta sin decir nada.
    var notes by remember { mutableStateOf(existingTerms?.notes ?: "") }
    // Marcada por defecto al crear; al editar refleja lo que está guardado.
    var remindMe by remember { mutableStateOf(existingTerms?.remindMe ?: true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // El corte es opcional: vacío vale; si se escribe, tiene que ser un día real.
    val cutoffOk = cutoffDay.isBlank() || cutoffDay.toIntOrNull() in 1..31
    val termsValid = bank.isNotBlank() && (paymentDay.toIntOrNull() in 1..31) && cutoffOk
    val accountValid = editing != null || name.isNotBlank()
    val canSave = termsValid && accountValid && !saving

    // F24 (mismo patrón que CreditTermsSheet): el botón nunca se apaga en silencio — debajo
    // dice la PRIMERA cosa que falta, en el orden de los campos.
    val missingFieldMessage = when {
        editing == null && name.isBlank() -> "Falta el nombre de la tarjeta"
        bank.isBlank() -> "Falta el banco"
        cutoffDay.isNotBlank() && cutoffDay.toIntOrNull() !in 1..31 -> "El día de corte tiene que estar entre 1 y 31"
        paymentDay.isBlank() -> "Falta el día de pago"
        paymentDay.toIntOrNull() !in 1..31 -> "El día de pago tiene que estar entre 1 y 31"
        else -> null
    }

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        coroutine.launch {
            val result = runCatching {
                val terms = CardTerms(
                    accountId = editing?.account?.id ?: "",
                    bank = bank.trim(),
                    creditLimit = creditLimit,
                    cutoffDay = cutoffDay.toIntOrNull(),
                    pagoMinimo = pagoMinimo,
                    tasaEa = tasaEa.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 },
                    paymentDay = paymentDay.toInt(),
                    notes = notes.trim().ifBlank { null },
                    remindMe = remindMe,
                )
                if (editing == null) {
                    // Alta atómica server-side: cuenta + deuda inicial (si la hay) + términos
                    // en una sola operación — sin estados parciales si algo falla a mitad.
                    Repositories.wallets.createCard(
                        CreateCardRequest(
                            name = name.trim(),
                            initialDebt = initialDebt ?: 0L,
                            currency = currency,
                            terms = terms,
                        )
                    )
                } else {
                    Repositories.wallets.putCardTerms(terms)
                }
            }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

    var pidiendoBorrar by remember { mutableStateOf(false) }

    fun deleteTerms() {
        if (editing == null || saving) return
        saving = true
        coroutine.launch {
            val result = runCatching { Repositories.wallets.deleteCardTerms(editing.account.id) }
            saving = false
            result.onSuccess { onSaved() }.onFailure { error = it.toUserMessage() }
        }
    }

    MarcoDeHoja(
        onDismiss = onDismiss,
        dismissEnabled = !saving,
        // Escape (la web) retira primero la pregunta de borrar, después la hoja. Ver [MarcoDeHoja].
        onEscape = { if (pidiendoBorrar) pidiendoBorrar = false else if (!saving) onDismiss() },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            if (editing != null) {
                // Vuelta de diseño 17: el encabezado de las hojas («Nu» · «Tarjeta»), no «La
                // tarjeta» como título de grupo con el nombre suelto debajo.
                EncabezadoDeHoja(
                    editing.account.name,
                    subtitulo = tipoLegibleDeLaCuenta(editing.account.type) ?: "Tarjeta",
                )
                Spacer(Modifier.height(Medida.entreSecciones))
            } else {
                GrupoDeCampos("La tarjeta") {
                Campo(
                name,
                {
                    name = it
                    if (!bancoAMano) bank = bancoSugeridoPara(it).orEmpty()
                },
                rotulo = "Nombre",
                guia = "Ej: Visa Bancolombia",
            )
                // Opcional a propósito: una tarjeta recién sacada no debe nada.
                CampoDeMonto(initialDebt, { initialDebt = it }, rotulo = "Deuda actual (opcional)", moneda = simboloDeLaMoneda(currency))
                Column {
                RotuloDeCampo("Moneda")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                ) {
                    for (cur in listOf("COP", "USD")) {
                        CurrencyChip(
                            label = cur,
                            selected = currency == cur,
                            onClick = { currency = cur },
                        )
                    }
                }
                }
                }
                Spacer(Modifier.height(Medida.entreSecciones))
            }

            val moneda = simboloDeLaMoneda(editing?.account?.currency ?: currency)
            GrupoDeCampos("Términos") {
            Campo(bank, { bank = it; bancoAMano = true }, rotulo = "Banco", guia = "Ej: Bancolombia")
            CampoDeMonto(creditLimit, { creditLimit = it }, rotulo = "Cupo total (opcional)", moneda = moneda)
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Box(Modifier.weight(1f)) {
                    Campo(
                        cutoffDay, { cutoffDay = it.filter { ch -> ch.isDigit() }.take(2) },
                        rotulo = "Corte (opcional)", ejemplo = "20",
                        teclado = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Box(Modifier.weight(1f)) {
                    Campo(
                        paymentDay, { paymentDay = it.filter { ch -> ch.isDigit() }.take(2) },
                        rotulo = "Día de pago", ejemplo = "5",
                        teclado = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            // **El único campo de esta hoja que Movi no puede deducir de nada.** El cupo
            // disponible sale del cupo menos la deuda, la deuda sale de los eventos; el mínimo
            // no sale de ningún lado, y estimarlo con el 5 % de Bancolombia sería un número
            // sobre su plata que él no puede verificar contra el extracto. Opcional a
            // propósito: vacío significa «no lo sé», que se dice, no se rellena.
            CampoDeMonto(
                pagoMinimo, { pagoMinimo = it },
                rotulo = "Pago mínimo del extracto (opcional)",
                moneda = moneda,
                ayuda = AYUDA_CORTA_DEL_MINIMO,
                masAyuda = TEXTO_DE_AYUDA_DEL_MINIMO,
            )
            CampoDeTasa(
                tasaEa, { tasaEa = filterRateInput(it).replace('.', ',') },
                rotulo = "Tasa E.A. (opcional)", ejemplo = "29,6",
                ayuda = AYUDA_CORTA_DE_LA_TASA,
                masAyuda = TEXTO_DE_AYUDA_DE_LA_TASA,
            )

            // Último renglón de los términos, igual que en la hoja de crédito: lo que la
            // tarjeta tiene de particular y no cabe en ningún campo —«difiere a 36 cuotas
            // toda compra internacional», el cupo que el banco todavía no sube—. Es el único
            // dato de esta hoja que Movi no vuelve a derivar de nada si se pierde.
            Campo(notes, { notes = it }, rotulo = "Notas (opcional)", guia = "Ej: el cupo sube en diciembre")
            }

            Spacer(Modifier.height(Espacio.m))
            // El pago de esta tarjeta entra al barrido de recordatorios salvo que el dueño
            // diga que no — mismo componente y mismo texto que crédito y recurrente.
            ReminderOptInField(
                checked = remindMe,
                onCheckedChange = { remindMe = it },
                enabled = !saving,
            )

        }

        // El error del guardado va en el pie, junto al botón y fuera del scroll: adentro se pintaba
        // debajo de lo que se ve (ver la hoja del crédito).
        Spacer(Modifier.height(Espacio.m))
        PieDeHoja(
            texto = if (saving) "Guardando…" else "Guardar tarjeta",
            onClick = { save() },
            habilitado = canSave,
            razon = if (saving) null else missingFieldMessage,
            error = error,
        )
        if (editing?.terms != null) {
            Spacer(Modifier.height(Espacio.xs))
            if (pidiendoBorrar) {
                // Borrar pregunta antes (ver [ConfirmacionEnLinea]).
                ConfirmacionEnLinea(
                    pregunta = "¿Eliminar los términos de «${editing.account.name}»?",
                    detalle = "Se borran el cupo, el corte, el día de pago y el mínimo. La cuenta, su deuda y sus movimientos no se tocan. No se puede deshacer.",
                    textoConfirmar = "Eliminar",
                    ocupado = saving,
                    onConfirmar = { deleteTerms() },
                    onCancelar = { pidiendoBorrar = false },
                )
            } else {
                // Vuelta de diseño 18: era un texto rojo tocable. Es destructivo, así que `Peligro`
                // (no `Fantasma`, que es la marca), chico y centrado debajo del pie.
                Boton(
                    "Eliminar términos",
                    onClick = { pidiendoBorrar = true },
                    variante = VarianteDeBoton.Peligro,
                    tamano = TamanoDeBoton.S,
                    habilitado = !saving,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
        Spacer(Modifier.height(Espacio.l))
    }
}

/** Chip de moneda, mismo dibujo que el selector de CreateAccountSheet (que es privado allá). */
@Composable
private fun RowScope.CurrencyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(Radio.m))
            .background(if (selected) Movi.colores.lavado(Movi.colores.marca) else Movi.colores.tarjeta)
            .then(
                if (!selected) Modifier.border(Medida.trazo, Movi.colores.borde, RoundedCornerShape(Radio.m)) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(vertical = Espacio.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = if (selected) Movi.textos.cuerpo else Movi.textos.cuerpoRegular,
            color = if (selected) Movi.colores.marca else Movi.colores.textoMedio,
        )
    }
}

/**
 * Lo que la hoja dice debajo del campo del mínimo.
 *
 * Dice **para qué sirve** y no **qué es**: el dueño ya sabe qué es un pago mínimo, lo que no sabe
 * es que cargarlo cambia la cifra grande de otra pantalla. Sin esa frase, el campo se lee como un
 * dato más de archivo y se queda vacío — y vacío es exactamente el estado en el que el «Flujo
 * libre» le decía $601.574 sin descontarle $1.843.014.
 *
 * Y dice que **cambia todos los meses**, porque cambia: es la razón por la que no se estima y la
 * razón por la que hay que volver a este campo cada tanto.
 *
 * ### Y de cuál de los dos mínimos se está hablando
 *
 * Una Master Black tiene **dos cuentas en Movi**, una en pesos y otra en dólares, y desde acá eso
 * se ve como «un mínimo, dos campos» — que invita a teclear el mismo número en los dos y restarlo
 * dos veces del disponible.
 *
 * No es así, y lo contesta el extracto: el de Bancolombia trae **dos pagos mínimos separados**, uno
 * por moneda («Pago mínimo: $1.843.014,00» en el bloque de pesos, «Pago mínimo: USD $71,00» en el
 * de dólares). O sea que un mínimo por cuenta es el modelo correcto, y cada uno se teclea del
 * bloque que le corresponde. La frase lo dice para que no se cargue dos veces el mismo número.
 */
const val TEXTO_DE_AYUDA_DEL_MINIMO: String =
    "Con esto, el «Flujo libre» descuenta lo que esta tarjeta te obliga a pagar. Cambia con cada " +
        "extracto: no lo estimamos, revísalo cuando te llegue. Si tu tarjeta maneja pesos y " +
        "dólares, el extracto trae un mínimo por cada moneda: aquí va solo el de esta cuenta."

/** La línea de ayuda del pago mínimo; el resto ([TEXTO_DE_AYUDA_DEL_MINIMO]) va en «¿Qué es esto?». */
const val AYUDA_CORTA_DEL_MINIMO: String = "Cambia con cada extracto: no lo estimamos."

/** La línea de ayuda de la tasa; el resto ([TEXTO_DE_AYUDA_DE_LA_TASA]) va en «¿Qué es esto?». */
const val AYUDA_CORTA_DE_LA_TASA: String = "La del extracto, como tasa efectiva anual."

/** «$» para pesos, «US$» para dólares: en Colombia «$20» se lee veinte pesos. */
internal fun simboloDeLaMoneda(moneda: String): String = if (moneda == "USD") "US$" else "$"

/**
 * Lo que la hoja dice debajo de la tasa (Ola 4): para qué sirve. Sin ella, la tarjeta no entra al
 * plan de salida de deudas, que la lista con «Falta la tasa».
 */
const val TEXTO_DE_AYUDA_DE_LA_TASA: String =
    "Con la tasa, «Cómo salir de tus deudas» incluye esta tarjeta en el orden para abonar. Está en tu " +
        "extracto como tasa efectiva anual."
