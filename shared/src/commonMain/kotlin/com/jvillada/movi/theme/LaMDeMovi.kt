// Generado por scripts/generar-icono.py: no se edita a mano, se corre el script.
package com.jvillada.movi.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * **La «m» del ícono de Movi** (vuelta 65): el contorno de la «m» de Figtree ExtraBold en la capa de
 * 108 del ícono adaptable de Android, el MISMO `pathData` de `ic_launcher_foreground.xml`
 * (`ElIconoDeMoviTest` los compara).
 */
const val PATH_DE_LA_M_DE_MOVI: String =
    "M35.76,64.47L35.76,41.27L42.58,41.27L42.86,44.19Q43.98,42.48 45.62,41.6Q47.27,40.71 49.27,40.71Q52.14,40.71 54.02,41.9Q55.9,43.08 56.78,45.49Q57.85,43.17 59.71,41.94Q61.56,40.71 63.88,40.71Q68.06,40.71 70.17,43.27Q72.28,45.82 72.24,51.2L72.24,64.47L65,64.47L65,52.59Q65,50.74 64.6,49.65Q64.21,48.56 63.47,48.05Q62.72,47.54 61.7,47.49Q59.71,47.44 58.66,48.63Q57.62,49.81 57.62,52.22L57.62,64.47L50.38,64.47L50.38,52.59Q50.38,50.74 49.99,49.65Q49.59,48.56 48.85,48.05Q48.11,47.54 47.08,47.49Q45.09,47.44 44.05,48.63Q43,49.81 43,52.22L43,64.47Z"

/**
 * La «m» como `ImageVector`, recortada a lo visible del ícono (los 72 del centro): pintada del tamaño
 * de un cuadrado amarillo queda como el ícono de la app. Es un vector y no texto, así que en iOS —que
 * todavía no tiene las fuentes propias— también es la «m» de Figtree.
 */
val LaMDeMovi: ImageVector by lazy {
    ImageVector.Builder(
        name = "LaMDeMovi",
        defaultWidth = 72.dp,
        defaultHeight = 72.dp,
        viewportWidth = 72.0f,
        viewportHeight = 72.0f,
    )
        .addGroup(translationX = -18.0f, translationY = -18.0f)
        .addPath(pathData = addPathNodes(PATH_DE_LA_M_DE_MOVI), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()
}
