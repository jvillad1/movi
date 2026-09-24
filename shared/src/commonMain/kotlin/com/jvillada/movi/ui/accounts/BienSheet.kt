package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.ClaseDeBien
import com.jvillada.movi.shared.model.claseDeBien
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.normalizarBien
import com.jvillada.movi.shared.model.problemaDelBien
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ConfirmacionEnLinea
import com.jvillada.movi.ui.components.MoneyField
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.rememberCampoConSeleccion
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.fecha.SelectorDeFecha
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * # La hoja de un bien: crearlo, y actualizarle el valor cuando llega el avalúo nuevo
 *
 * Una sola hoja para las dos cosas, porque son los mismos cuatro datos: qué es (inmueble,
 * vehículo, otro), cuánto vale, de cuándo es ese valor y —si hay— qué deuda lo financia. Ver
 * `Bien` en `:core` para qué es un bien y por qué no suma a «Tu plata».
 *
 * **Se desplaza.** Con el calendario abierto es más alta que cualquier teléfono, y la lección está
 * escrita en `CreateAccountSheet`: una hoja anclada abajo sin `verticalScroll` corta el botón de
 * guardar en pantallas cortas. La manija queda afuera y el cuerpo en una columna con
 * `verticalScroll` + `weight(1f, fill = false)`, que es el idioma de las hojas que sí se desplazan.
 *
 * **Sin saldo inicial ni movimientos.** Un bien no tiene: el valor se escribe acá y se reescribe
 * acá. Por eso no pasa por `openingEventFor` como una cuenta de plata.
 *
 * @param existente el bien a editar, o `null` para crear uno.
 * @param nombreInicial lo que el dueño ya escribió en «Nueva cuenta» antes de elegir «Bien»: no se
 *   le pide dos veces.
 * @param cuentas todas las cuentas, para ofrecer las deudas que pueden financiarlo.
 */
@Composable
fun BienSheet(
    existente: Account?,
    cuentas: List<Account>,
    onDismiss: () -> Unit,
    onGuardado: () -> Unit,
    nombreInicial: String = "",
) {
    val coroutine = rememberCoroutineScope()
    val hoy = remember { hoyEnAppZone() }
    val bienExistente = existente?.bien
    var nombre by remember { mutableStateOf(existente?.name ?: nombreInicial) }
    var clase by remember { mutableStateOf(claseDeBien(bienExistente?.clase ?: ClaseDeBien.INMUEBLE.clave)) }
    var valor by remember { mutableStateOf(bienExistente?.valor) }
    var fecha by remember {
        mutableStateOf(bienExistente?.valorAl?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    }
    var deudaId by remember { mutableStateOf(bienExistente?.deudaId) }
    var eligiendoFecha by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var confirmandoBorrar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val deudas = remember(cuentas) { deudasParaAsociar(cuentas) }
    val bien = normalizarBien(
        Bien(clase = clase.clave, valor = valor ?: 0L, valorAl = fecha?.toString(), deudaId = deudaId),
    )
    // La MISMA frase que contestaría el server (ver `problemaDelBien`): el botón gris dice qué
    // falta antes de mandar nada, que es el patrón de todas las hojas de crear (F24).
    val loQueFalta = when {
        nombre.isBlank() -> "Falta el nombre"
        else -> problemaDelBien(bien)
    }
    val puedeGuardar = loQueFalta == null && !guardando

    fun guardar() {
        if (!puedeGuardar) return
        guardando = true
        error = null
        coroutine.launch {
            val resultado = runCatching {
                if (existente == null) {
                    Repositories.wallets.createAccount(
                        Account(
                            // Un id propio desde el primer instante: en Android/iOS esto pasa por
                            // LocalRepository, igual que la hoja de «Nueva cuenta».
                            id = newId("acc"),
                            name = nombre.trim(),
                            // La forma que el server fuerza de todos modos (ver `Account.bien`):
                            // se manda así para que la fila local diga lo mismo antes de subir.
                            type = AccountType.INVESTMENT,
                            balance = 0L,
                            bien = bien,
                        ),
                    )
                } else {
                    if (nombre.trim() != existente.name) Repositories.wallets.renameAccount(existente.id, nombre.trim())
                    Repositories.wallets.updateBien(existente.id, bien)
                }
            }
            guardando = false
            resultado.onSuccess { onGuardado() }.onFailure { error = it.toUserMessage() }
        }
    }

    fun borrar() {
        val id = existente?.id ?: return
        guardando = true
        error = null
        coroutine.launch {
            val resultado = runCatching { Repositories.wallets.deleteAccount(id) }
            guardando = false
            resultado.onSuccess { onGuardado() }.onFailure {
                confirmandoBorrar = false
                error = it.toUserMessage()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !guardando, onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Movi.formas.amplia, topEnd = Movi.formas.amplia))
                .background(Movi.colores.tarjeta)
                .padding(horizontal = Movi.espacios.margen)
                .clickable(enabled = false) {},
        ) {
            SheetHandleWithClose(onClose = onDismiss, enabled = !guardando)

            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                Text(
                    text = if (existente == null) "Nuevo bien" else "Actualizar bien",
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                )
                Spacer(Modifier.height(Movi.espacios.minimo))
                Text(
                    text = "Suma a tu patrimonio, no a Tu plata: con la casa no se paga el mercado.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )

                Spacer(Modifier.height(Movi.espacios.margen))
                Rotulo("NOMBRE")
                Spacer(Modifier.height(Movi.espacios.corto))
                CampoDeTexto(nombre, onCambio = { nombre = it }, placeholder = "Ej: Casa Almendros")

                Spacer(Modifier.height(Movi.espacios.margen))
                Rotulo("QUÉ ES")
                Spacer(Modifier.height(Movi.espacios.corto))
                Row(horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto)) {
                    ClaseDeBien.entries.forEach { opcion ->
                        Opcion(
                            texto = opcion.nombre,
                            elegida = clase == opcion,
                            modifier = Modifier.weight(1f),
                            onClick = { clase = opcion },
                        )
                    }
                }

                Spacer(Modifier.height(Movi.espacios.margen))
                Rotulo("CUÁNTO VALE")
                Spacer(Modifier.height(Movi.espacios.corto))
                MoneyField(value = valor, onValueChange = { valor = it })

                Spacer(Modifier.height(Movi.espacios.margen))
                Rotulo("DE CUÁNDO ES ESE VALOR")
                Spacer(Modifier.height(Movi.espacios.corto))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        // «avalúo del 28 de agosto», con la MISMA función que la lista de Bienes.
                        text = textoDelAvaluo(fecha?.toString(), hoy)?.replaceFirstChar { it.uppercase() }
                            ?: "Sin fecha",
                        style = Movi.textos.cuerpo,
                        color = if (fecha == null) Movi.colores.textoMedio else Movi.colores.texto,
                        modifier = Modifier.weight(1f),
                    )
                    if (fecha != null && !eligiendoFecha) {
                        Enlace("Quitar", enabled = !guardando) { fecha = null }
                        Spacer(Modifier.width(Movi.espacios.corto))
                    }
                    Enlace(if (eligiendoFecha) "Listo" else "Elegir", enabled = !guardando) {
                        eligiendoFecha = !eligiendoFecha
                    }
                }
                if (eligiendoFecha) {
                    Spacer(Modifier.height(Movi.espacios.medio))
                    SelectorDeFecha(
                        seleccionada = fecha ?: hoy,
                        hoy = hoy,
                        onPick = { fecha = it; eligiendoFecha = false },
                        enabled = !guardando,
                    )
                }

                if (deudas.isNotEmpty()) {
                    Spacer(Modifier.height(Movi.espacios.margen))
                    Rotulo("LO FINANCIA (OPCIONAL)")
                    Spacer(Modifier.height(Movi.espacios.minimo))
                    Text(
                        text = "Con la deuda al lado, Patrimonio te muestra cuánto de este bien es tuyo de verdad. " +
                            "No cambia tu patrimonio.",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                    Spacer(Modifier.height(Movi.espacios.corto))
                    Column(verticalArrangement = Arrangement.spacedBy(Movi.espacios.corto)) {
                        Opcion(
                            texto = "Ninguna",
                            elegida = deudaId == null,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { deudaId = null },
                        )
                        deudas.forEach { deuda ->
                            Opcion(
                                texto = deuda.name,
                                elegida = deudaId == deuda.id,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { deudaId = deuda.id },
                            )
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(Movi.espacios.corto))
                    Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
                }

                Spacer(Modifier.height(Movi.espacios.margen))
                if (confirmandoBorrar) {
                    ConfirmacionEnLinea(
                        pregunta = "¿Eliminar ${existente?.name ?: "este bien"}?",
                        detalle = "Deja de sumar a tu patrimonio. La deuda que lo financia no se toca.",
                        textoConfirmar = "Eliminar",
                        ocupado = guardando,
                        onConfirmar = { borrar() },
                        onCancelar = { confirmandoBorrar = false },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Movi.espacios.encabezado)
                            .clip(RoundedCornerShape(Movi.formas.pleno))
                            .background(if (puedeGuardar) Movi.colores.marca.copy(alpha = 0.16f) else Movi.colores.tarjeta)
                            .clickable(enabled = puedeGuardar) { guardar() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                guardando -> "Guardando…"
                                existente == null -> "Crear bien"
                                else -> "Guardar"
                            },
                            style = Movi.textos.titulo,
                            fontWeight = FontWeight.Medium,
                            color = if (puedeGuardar) Movi.colores.marca else Movi.colores.textoApagado,
                        )
                    }
                    if (!guardando && loQueFalta != null) {
                        Spacer(Modifier.height(Movi.espacios.corto))
                        Text(
                            text = loQueFalta,
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    if (existente != null) {
                        Spacer(Modifier.height(Movi.espacios.medio))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Enlace("Eliminar bien", enabled = !guardando, color = Movi.colores.sale) {
                                confirmandoBorrar = true
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Movi.espacios.amplio))
            }
        }
    }
}

@Composable
private fun Rotulo(texto: String) {
    Text(
        text = texto,
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
        letterSpacing = 0.4.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun CampoDeTexto(valor: String, onCambio: (String) -> Unit, placeholder: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Movi.formas.normal))
            .border(1.dp, Movi.colores.borde, RoundedCornerShape(Movi.formas.normal))
            .padding(horizontal = Movi.espacios.medio, vertical = Movi.espacios.medio),
    ) {
        // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver [esAtajoDeSeleccionarTodo].
        val campo = rememberCampoConSeleccion(valor, onCambio)
        BasicTextField(
            value = campo.valor,
            onValueChange = campo::alCambiar,
            cursorBrush = SolidColor(Movi.colores.texto),
            textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
            decorationBox = { inner ->
                if (valor.isEmpty()) Text(placeholder, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                inner()
            },
        )
    }
}

/** Una opción elegible (la clase, la deuda): el mismo lenguaje que los tipos de «Nueva cuenta». */
@Composable
private fun Opcion(texto: String, elegida: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val forma = RoundedCornerShape(Movi.formas.normal)
    Box(
        modifier = modifier
            .clip(forma)
            .background(if (elegida) Movi.colores.marca.copy(alpha = 0.16f) else Movi.colores.tarjeta)
            .then(if (!elegida) Modifier.border(1.dp, Movi.colores.borde, forma) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = Movi.espacios.medio, vertical = Movi.espacios.medio),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = texto,
            style = Movi.textos.cuerpo,
            fontWeight = if (elegida) FontWeight.Medium else FontWeight.Normal,
            color = if (elegida) Movi.colores.marca else Movi.colores.texto,
            maxLines = 1,
        )
    }
}

@Composable
private fun Enlace(texto: String, enabled: Boolean, color: Color = Movi.colores.marca, onClick: () -> Unit) {
    Text(
        text = texto,
        style = Movi.textos.cuerpo,
        fontWeight = FontWeight.Medium,
        color = if (enabled) color else Movi.colores.textoApagado,
        modifier = Modifier
            .clip(RoundedCornerShape(Movi.formas.minima))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Movi.espacios.minimo, vertical = Movi.espacios.minimo),
    )
}
