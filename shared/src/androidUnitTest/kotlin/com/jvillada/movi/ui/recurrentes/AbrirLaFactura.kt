package com.jvillada.movi.ui.recurrentes

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction

/**
 * Vuelta 136 · **abre la hoja de la factura [nombre]** en «Tus facturas del período»: toca su fila (la fila
 * entera es el toque). Lo que antes estaba en la fila o en su «⋯» —«Anotar pago», «Sí, fue este», «Quitar la
 * marca», «Pausar»…— está ahí.
 */
fun SemanticsNodeInteractionsProvider.abrirLaFactura(nombre: String) {
    onAllNodes(
        hasClickAction() and hasText(nombre, substring = true) and hasAnyAncestor(hasTestTag(TAG_FILA_DE_FACTURA)),
    ).onFirst().performSemanticsAction(SemanticsActions.OnClick)
}
