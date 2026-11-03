package com.jvillada.movi.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.jvillada.movi.shared.model.Scope

/**
 * # Las categorías del dueño, cargadas aunque no se haya pasado por el Inicio (vuelta 119)
 *
 * [UsedCategoriesCache] vive en memoria y la verdad del server (las categorías usadas, las de los
 * pagos fijos y presupuestos, las escondidas y fijadas, los usos recientes) solo le llega por
 * [UsedCategoriesCache.recordFromServer]. Hasta esta vuelta eso lo llamaba únicamente el Inicio, así
 * que un selector abierto sin haber cargado el Inicio —Pagos tras recargar la web, un aviso que abre
 * Por revisar, una carga del Inicio que falló— mostraba solo el catálogo, y el dueño volvía a crear
 * «Gardenera», que ya tenía.
 *
 * **La única forma de asegurarlas**: si en esta sesión todavía no llegaron del server, se piden con
 * la MISMA lectura del Inicio (`GET /api/dashboard/summary`, sin endpoint nuevo). Pasa por
 * [LecturasSinRepetir], así que si el Inicio o Pagos la acaban de pedir (o la están pidiendo) no
 * sale otra petición. Una vez que llegaron no se vuelve a pedir: lo que el dueño crea después lo
 * anota cada pantalla al guardar ([UsedCategoriesCache.record]) y el Inicio las refresca en cada
 * carga. Si falla, el selector se queda con lo que había (el catálogo y lo anotado) y se reintenta la
 * próxima vez que se abra uno.
 */
suspend fun asegurarLasCategoriasDelDueno() {
    if (UsedCategoriesCache.llegaronDelServer) return
    val resumen = runCatching { Repositories.wallets.getDashboardSummary(Scope.SELF) }.getOrNull() ?: return
    UsedCategoriesCache.recordFromServer(resumen.usedCategories)
    // La misma respuesta trae la cuenta con la que arranca «Agregar», como en el Inicio.
    CuentaMasUsadaCache.recordFromServer(resumen.cuentaMasUsada)
}

/**
 * [asegurarLasCategoriasDelDueno] al montarse. La llaman los selectores de categoría que comparten
 * todas las pantallas —`CategoryField`, `SelectorDeCategoria`, `FilaDeCategorias` y el editor de
 * «Repartir en partes»—, no cada pantalla: así una hoja nueva que elija categoría ya la trae.
 */
@Composable
fun AsegurarLasCategoriasDelDueno() {
    LaunchedEffect(Unit) { asegurarLasCategoriasDelDueno() }
}
