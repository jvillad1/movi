package com.jvillada.movi.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.Screen

/**
 * # Qué forma tiene cada pantalla en pantalla ancha (Ola W1)
 *
 * El dueño, sobre la web: «parece la app pero grande, no está aprovechando para nada el espacio».
 * Hasta acá todo iba en una columna de 600 dp (el Inicio, 1.200), y a 1.920 px quedaban ~550 dp
 * vacíos a cada lado.
 *
 * La regla de esta ola: **la pila de navegación decide QUÉ se muestra; el ancho decide CÓMO**. Cada
 * pantalla declara de qué tipo es ([disposicionDe]) y la cáscara (`App.kt`) le da el tope que le
 * toca a ese tipo en la clase de ancho actual ([anchoMaximoDe]):
 *
 * |               | Compacto | Medio | Expandido |
 * |---------------|----------|-------|-----------|
 * | Lectura       | lleno    | 720   | 720       |
 * | Tablero       | lleno    | 840   | 1280      |
 * | ListaYDetalle | lleno    | 840   | 1440      |
 *
 * Una pantalla que todavía no se adaptó es [Disposicion.Lectura]: se queda en su columna. **Nunca
 * se estira una lista de una sola columna a 1.200 dp**: no se lee mejor, se lee peor.
 */
enum class Disposicion {
    /** Una columna para leer: listas, formularios, ajustes. */
    Lectura,

    /** Tarjetas en rejilla: el contenido decide cuántas columnas le caben (ver el Inicio). */
    Tablero,

    /** Una lista a la izquierda y el detalle de lo elegido a la derecha. */
    ListaYDetalle,
}

/**
 * La disposición de [pantalla]. En la Ola W1 solo Hoy es un [Disposicion.Tablero]; en la W2 Tus
 * períodos y su detalle pasan a [Disposicion.ListaYDetalle] (ver `PeriodosListaYDetalle`); en la W3
 * Patrimonio, Créditos y Plan son tableros (ver `PanelDeTablero`); en la W4 Movimientos es lista y
 * detalle (ver `MovimientosListaYDetalle`). El resto se suma acá a medida que su composición
 * interna aprende a usar el ancho.
 */
fun disposicionDe(pantalla: Screen): Disposicion = when (pantalla) {
    Screen.Dashboard, Screen.Accounts, Screen.Credits, is Screen.Plan -> Disposicion.Tablero
    Screen.Periodos, is Screen.DetalleDePeriodo, is Screen.Transactions -> Disposicion.ListaYDetalle
    else -> Disposicion.Lectura
}

/** El ancho de la columna de lectura en mediano y expandido. */
val ANCHO_DE_LECTURA: Dp = 720.dp

/** El tope de un tablero o de una lista con detalle en una ventana mediana. */
val ANCHO_ANCHO_EN_MEDIANO: Dp = 840.dp

/** El tope de un tablero en escritorio. */
val ANCHO_DEL_TABLERO: Dp = 1_280.dp

/** El tope de una lista con su detalle en escritorio. */
val ANCHO_DE_LISTA_Y_DETALLE: Dp = 1_440.dp

/**
 * Desde qué ancho **del contenido** el Inicio se parte en dos columnas. 900 dp: dos columnas de
 * ~450 dp, que es lo que una tarjeta del Inicio necesita para que «$14,4M de $13,8M» no se parta.
 * Por debajo, una columna. Es un umbral del contenido y no de la ventana: el rail ya se descontó.
 */
val ANCHO_PARA_DOS_COLUMNAS: Dp = 900.dp

/**
 * El ancho de una columna del Inicio cuando va solo una. Es la columna de lectura: el Inicio en una
 * columna se lee como cualquier otra pantalla.
 */
val ANCHO_DE_UNA_COLUMNA: Dp = ANCHO_DE_LECTURA

/**
 * Lo que un tablero de dos columnas gasta en aire: 16 dp a cada lado y 16 entre las dos
 * (`Movi.espacios.amplio`). Lo que queda se reparte entre las columnas.
 */
val RELLENO_DE_DOS_COLUMNAS: Dp = 48.dp

/**
 * Si en un panel de [anchoDelPanel] caben dos columnas iguales de al menos [anchoMinimoDeColumna]
 * cada una, descontado [RELLENO_DE_DOS_COLUMNAS]. Se decide por la columna y no por el panel porque
 * es la columna la que tiene que caber (el mismo criterio que el detalle de un período, Ola W2).
 */
fun cabenDosColumnasIguales(anchoDelPanel: Dp, anchoMinimoDeColumna: Dp): Boolean =
    (anchoDelPanel - RELLENO_DE_DOS_COLUMNAS) / 2 >= anchoMinimoDeColumna

/** Lo que el rail de [clase] se come del ancho de la ventana: nada, 80 o 216 dp. */
fun anchoDelRail(clase: WindowWidthClass): Dp = when (clase) {
    WindowWidthClass.Compact -> 0.dp
    WindowWidthClass.Medium -> ANCHO_DEL_RAIL_COMPACTO
    WindowWidthClass.Expanded -> ANCHO_DEL_RAIL_ANCHO
}

/**
 * **El ancho REAL que le queda a [pantalla] en una ventana de [ventana]**, dentro de la cáscara
 * completa (`EsqueletoDeLaCascara`): la ventana menos el rail de su clase, topado por la
 * [disposicionDe] de la pantalla. Es la cuenta que la Ola W2 hizo a mano sin restar el rail —y las
 * dos columnas del detalle de un período nunca aparecían en una laptop de 1280—, escrita una vez
 * para que cada umbral de columnas se pruebe contra ella y no contra un número suelto.
 *
 * Supone una pantalla con navegación (la que tiene rail). Las pruebas montadas dentro de la cáscara
 * miden lo mismo con `boundsInRoot`; esta función es la versión en números, para las pruebas puras.
 */
fun anchoDelPanelEnLaCascara(ventana: Dp, pantalla: Screen): Dp {
    val clase = claseDeAncho(ventana)
    return minOf(ventana - anchoDelRail(clase), anchoMaximoDeLaPantalla(pantalla, clase))
}

/** El tope de una pantalla de tipo [disposicion] en la clase [clase]. [Dp.Infinity] es «lleno». */
fun anchoMaximoDe(disposicion: Disposicion, clase: WindowWidthClass): Dp = when (clase) {
    WindowWidthClass.Compact -> Dp.Infinity
    WindowWidthClass.Medium -> when (disposicion) {
        Disposicion.Lectura -> ANCHO_DE_LECTURA
        Disposicion.Tablero, Disposicion.ListaYDetalle -> ANCHO_ANCHO_EN_MEDIANO
    }
    WindowWidthClass.Expanded -> when (disposicion) {
        Disposicion.Lectura -> ANCHO_DE_LECTURA
        Disposicion.Tablero -> ANCHO_DEL_TABLERO
        Disposicion.ListaYDetalle -> ANCHO_DE_LISTA_Y_DETALLE
    }
}

/**
 * El ancho máximo del contenido de [pantalla] en la cáscara (`App.kt`), con la clase de ancho
 * [clase]. Es la tabla de [anchoMaximoDe] leída con la [disposicionDe] de la pantalla.
 */
fun anchoMaximoDeLaPantalla(pantalla: Screen, clase: WindowWidthClass): Dp =
    anchoMaximoDe(disposicionDe(pantalla), clase)
