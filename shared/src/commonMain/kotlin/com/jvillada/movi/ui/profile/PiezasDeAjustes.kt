package com.jvillada.movi.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Superficie

/*
 * # Las piezas de Ajustes que viven en `ui/profile/`
 *
 * Vuelta de diseño 3: «Perfil» dejó de ser una pantalla aparte y su contenido se mudó a Ajustes
 * (`ui/mas/MasScreen.kt`). Acá quedan las piezas que comparten Ajustes, sus hojas y la guía de
 * primer uso: guardar y escribir «Inicio del mes», la nota de una sección, el interruptor y los
 * textos fijos.
 */
/**
 * **Guardar «Inicio del mes»**: el día de corte y qué hace si cae en fin de semana o festivo. Una
 * sola escritura para Ajustes y para el paso «Tu período» de la guía de primer uso.
 */
internal suspend fun guardarElInicioDelMes(dia: Int, regla: SiElCorteCaeEnFinDeSemana) =
    Repositories.wallets.updateUserProfile(
        UpdateProfileRequest(periodCutoffDay = dia, periodWeekendRule = regla.name),
    )

/**
 * Lo que dice la fila «Inicio del mes»: «Cada día 25 · o el día hábil anterior».
 * Con corte 1 y sin regla sigue siendo «Mes de calendario».
 */
fun textoDelInicioDelMes(dia: Int, regla: SiElCorteCaeEnFinDeSemana): String {
    val base = if (dia == 1 && regla == SiElCorteCaeEnFinDeSemana.ESE_DIA) "Mes de calendario" else "Cada día $dia"
    return when (regla) {
        SiElCorteCaeEnFinDeSemana.ESE_DIA -> base
        SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR -> "$base · o el día hábil anterior"
        SiElCorteCaeEnFinDeSemana.DIA_HABIL_SIGUIENTE -> "$base · o el día hábil siguiente"
    }
}

/**
 * **La nota de una sección**: lo que una [Fila] no alcanza a decir en su renglón (el alcance de la
 * huella, para qué es el correo del cierre), debajo de la superficie y alineado con su texto. En
 * `sale` si es un error.
 */
@Composable
internal fun NotaDeSeccion(texto: String?, esError: Boolean = false) {
    if (texto == null) return
    Text(
        texto,
        style = Movi.textos.apoyo,
        color = if (esError) Movi.colores.sale else Movi.colores.textoMedio,
        modifier = Modifier.padding(start = Medida.margenDePantalla, end = Medida.margenDePantalla, top = Espacio.xs),
    )
}

/** El interruptor de Ajustes, en los colores de la marca (los dos temas). */
@Composable
internal fun Interruptor(encendido: Boolean, onCambio: (Boolean) -> Unit, habilitado: Boolean = true) {
    Switch(
        checked = encendido,
        enabled = habilitado,
        onCheckedChange = onCambio,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Movi.colores.sobreMarca,
            checkedTrackColor = Movi.colores.marca,
            uncheckedThumbColor = Movi.colores.textoApagado,
            uncheckedTrackColor = Movi.colores.tarjeta,
            uncheckedBorderColor = Movi.colores.borde,
        ),
    )
}

/** La fila de Ajustes que baja el ZIP con todos los datos de la cuenta. */
const val DESCARGAR_TUS_DATOS: String = "Descargar tus datos"

/** La fila de Ajustes que cierra todas las sesiones de la cuenta. */
const val CERRAR_SESION_EN_TODOS: String = "Cerrar sesión en todos los aparatos"

/** Lo que dice la tarjeta de reintento cuando el perfil del dueño no se pudo leer. */
const val AJUSTES_NO_LEIDOS: String = "No pudimos cargar tus ajustes"

/** Subtítulo de una fila cuyo valor guardado todavía viaja. */
const val CARGANDO: String = "Cargando…"

/** Subtítulo de una fila cuyo valor guardado no se pudo leer. Nunca el valor por defecto. */
const val NO_PUDIMOS_LEERLO: String = "No pudimos leerlo"

/**
 * Lo que Safari en un iPhone necesita para avisar: Movi en la pantalla de inicio. Es el único
 * camino que Apple deja (iOS 16.4+), y desde una pestaña la web no puede instalarse sola ni abrir
 * esa hoja: lo único útil es decir los dos toques. Ver [PushOptIn.necesitaInstalar].
 */
@Composable
internal fun AvisosEnElIphone() {
    Superficie(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
            Text("Avisos en tu iPhone", style = Movi.textos.titulo, color = Movi.colores.texto)
            Text(
                "Safari solo deja avisar a Movi si está en tu pantalla de inicio. Toca Compartir, " +
                    "luego «Agregar a Inicio», y abre Movi desde ese ícono: ahí aparece «Activar».",
                style = Movi.textos.apoyo, color = Movi.colores.textoMedio,
            )
        }
    }
}
