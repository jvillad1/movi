package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.Movi

/**
 * **Borrar pregunta antes, en todas partes.**
 *
 * Cuentas, movimientos y documentos pedían confirmación; metas, recurrentes, los términos de una
 * tarjeta o un crédito y las suscripciones se borraban con un toque — y en metas y recurrentes el
 * «Eliminar» está al lado del título, donde un dedo que va a cerrar la hoja lo toca sin querer.
 * Una sola pieza para decirlo, con **qué** se borra y **qué no** se toca, que es lo que uno quiere
 * leer antes del botón rojo.
 *
 * [ConfirmacionEnLinea] va dentro de una hoja (reemplaza la fila del botón mientras se decide);
 * [ConfirmarEnHoja] se monta encima de una pantalla, igual que la de Documentos.
 */
@Composable
fun ConfirmacionEnLinea(
    pregunta: String,
    detalle: String,
    textoConfirmar: String,
    ocupado: Boolean,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Movi.colores.fondo)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(pregunta, style = Movi.textos.cuerpo, color = Movi.colores.texto)
        Spacer(Modifier.height(6.dp))
        Text(detalle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(12.dp))
        BotonesDeConfirmar(textoConfirmar, ocupado, onConfirmar, onCancelar)
    }
}

@Composable
fun ConfirmarEnHoja(
    pregunta: String,
    detalle: String,
    textoConfirmar: String,
    ocupado: Boolean,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    MarcoDeHoja(
        onDismiss = onCancelar,
        dismissEnabled = !ocupado,
        relleno = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        conCierre = false,
    ) {
        Text(pregunta, style = Movi.textos.titulo, color = Movi.colores.texto)
        Spacer(Modifier.height(8.dp))
        Text(detalle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(20.dp))
        BotonesDeConfirmar(textoConfirmar, ocupado, onConfirmar, onCancelar)
    }
}

@Composable
private fun BotonesDeConfirmar(textoConfirmar: String, ocupado: Boolean, onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Cancelar",
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !ocupado, onClick = onCancelar)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(
            if (ocupado) "…" else textoConfirmar,
            style = Movi.textos.cuerpo,
            color = Movi.colores.sale,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !ocupado, onClick = onConfirmar)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
