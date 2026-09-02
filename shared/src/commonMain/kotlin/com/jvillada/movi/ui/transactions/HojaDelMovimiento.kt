package com.jvillada.movi.ui.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.ui.accounts.VoidEventSheet
import com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet
import com.jvillada.movi.ui.recurrentes.RecurringPrefill

/**
 * **Lo que se abre al tocar un movimiento — el mismo juego de hojas, se llegue por donde se
 * llegue.**
 *
 * ## El defecto que cierra
 *
 * Había dos puertas al mismo renglón y ofrecían cosas distintas:
 *
 * - **Movimientos** abría [ChangeCategorySheet] — categoría, fecha, monto, cuenta, concepto,
 *   «esto se repite», y anular al final.
 * - **El detalle de la cuenta** abría [VoidEventSheet] directo. O sea que en la pantalla donde el
 *   dueño **nota** que un saldo está mal —la que le muestra el saldo y los renglones que lo
 *   forman— lo único que se le ofrecía sobre la fila era **anular**: justo el rodeo destructivo
 *   que la edición vino a reemplazar (anular y volver a crear pierde el id del movimiento, y con
 *   él su sello de recurrente y su descarte de «no es pago de tarjeta»).
 *
 * Eso ya estaba anotado como problema en el KDoc de `ChangeCategorySheet.onAnular` —«dos hojas
 * distintas para el mismo movimiento según por dónde se llegara, y a cada una le faltaba lo de la
 * otra»— y se había cerrado solo la mitad: llevar anular a Movimientos. Esta es la otra mitad, y
 * se cierra **por construcción**: las dos pantallas llaman a esta función, así que no hay dónde
 * volver a divergir.
 *
 * ## Por qué las tres hojas se dibujan acá y no una adentro de la otra
 *
 * Una hoja no puede abrir otra encima de sí misma: cerrar la de abajo se llevaría la de arriba.
 * Así que el estado de «anular» y el del formulario de recurrente viven en este envoltorio y las
 * tres se dibujan como hermanas. El orden importa: las dos de abajo se dibujan **después** de la
 * de categoría para quedar encima.
 *
 * ## El contrato con quien llama
 *
 * [onCambiado] significa «este movimiento ya no es el que estabas mostrando» — cambió de
 * categoría, de fecha, de monto, de cuenta, de concepto, se anuló, o se le creó el recurrente que
 * la pantalla de al lado va a listar. Quien llama cierra la hoja y recarga: es lo mismo en todos
 * los casos, y tener un solo callback evita que una pantalla recargue en un caso y en otro no.
 */
@Composable
fun HojaDelMovimiento(
    event: FinancialEvent,
    /**
     * Las cuentas del dueño, para poder mover el movimiento de cuenta. Vacía = «todavía no
     * llegaron»: el selector no se abre y el monto y el concepto se siguen pudiendo corregir.
     */
    cuentas: List<Account>,
    onDismiss: () -> Unit,
    onCambiado: () -> Unit,
    /**
     * Llevar al detalle de la cuenta de este movimiento. Solo lo usa la rama del saldo inicial.
     * `null` cuando quien llama no sabe a qué grupo va ese detalle **o cuando ya está ahí** — que
     * es el caso del propio detalle de la cuenta.
     */
    onVerCuenta: (() -> Unit)? = null,
) {
    var pidioAnular by remember(event.id) { mutableStateOf(false) }
    var prefillRecurrente by remember(event.id) { mutableStateOf<RecurringPrefill?>(null) }

    ChangeCategorySheet(
        event = event,
        cuentas = cuentas,
        onDismiss = onDismiss,
        onEventChanged = { onCambiado() },
        onVerCuenta = onVerCuenta,
        onAnular = { pidioAnular = true },
        onMarcarComoRecurrente = { prefill -> prefillRecurrente = prefill },
    )

    // Guardar el recurrente cierra las dos y recarga: el movimiento no cambió, pero Recurrentes sí
    // —y el Inicio lo lee.
    prefillRecurrente?.let { prefill ->
        CreateRecurringRuleSheet(
            onDismiss = { prefillRecurrente = null },
            onSaved = {
                prefillRecurrente = null
                onCambiado()
            },
            prefill = prefill,
        )
    }

    // Anular se pide desde la hoja de arriba y se confirma en su propia hoja: es la única acción
    // que saca plata de todas las cifras, y no comparte lugar con las que solo la reclasifican.
    if (pidioAnular) {
        VoidEventSheet(
            event = event,
            // Las mismas cuentas que el selector de arriba, y acá solo para **nombrarlas**: cuando
            // las dos mitades de un par no valen lo mismo, la hoja dice qué le pasa a cada una.
            cuentas = cuentas,
            onDismiss = { pidioAnular = false },
            onVoided = {
                pidioAnular = false
                onCambiado()
            },
        )
    }
}
