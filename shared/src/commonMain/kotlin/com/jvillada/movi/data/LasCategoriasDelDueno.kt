package com.jvillada.movi.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.jvillada.movi.shared.model.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * carga. Si falla, el selector se queda con lo que había (el catálogo y lo anotado) y se reintenta al
 * abrir otro selector pasado [ENFRIAMIENTO_DE_LAS_CATEGORIAS_MS].
 */
internal suspend fun asegurarLasCategoriasDelDueno(ahora: () -> Long = ::ahoraEnMs) {
    if (UsedCategoriesCache.llegaronDelServer) return
    // Una sola lectura en vuelo: si montan varios selectores a la vez, los demás esperan acá y al
    // entrar ya ven la lista (o la falla reciente) y no piden nada.
    lecturaDeLasCategorias.withLock {
        if (UsedCategoriesCache.llegaronDelServer) return
        val fallo = UsedCategoriesCache.ultimoFalloDeLasCategoriasMs
        if (fallo != null && ahora() - fallo < ENFRIAMIENTO_DE_LAS_CATEGORIAS_MS) return
        val resumen = try {
            Repositories.wallets.getDashboardSummary(Scope.SELF)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            UsedCategoriesCache.ultimoFalloDeLasCategoriasMs = ahora()
            return
        }
        UsedCategoriesCache.recordFromServer(resumen.usedCategories)
        // La misma respuesta trae la cuenta con la que arranca «Agregar», como en el Inicio.
        CuentaMasUsadaCache.recordFromServer(resumen.cuentaMasUsada)
    }
}

/**
 * Tras una lectura fallida, cuánto esperar antes de volver a pedirla (elegido, no medido: el medio
 * minuto de [FRESCURA_DE_UNA_LECTURA_MS]). Sin esto, sin señal, cada selector que se monta —y una
 * hoja monta varios— salía a pedirla otra vez.
 */
const val ENFRIAMIENTO_DE_LAS_CATEGORIAS_MS: Long = 30_000L

private val lecturaDeLasCategorias = Mutex()

/**
 * [asegurarLasCategoriasDelDueno] al montarse. La llaman los selectores de categoría que comparten
 * todas las pantallas —`CategoryField`, `SelectorDeCategoria`, `FilaDeCategorias` y el editor de
 * «Repartir en partes»—, no cada pantalla: así una hoja nueva que elija categoría ya la trae.
 */
@Composable
fun AsegurarLasCategoriasDelDueno() {
    LaunchedEffect(Unit) { asegurarLasCategoriasDelDueno() }
}
