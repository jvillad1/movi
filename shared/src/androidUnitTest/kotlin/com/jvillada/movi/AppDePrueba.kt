package com.jvillada.movi

import android.app.Application
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import java.lang.reflect.Method
import org.robolectric.TestLifecycleApplication

/**
 * # Cada prueba de Robolectric arranca con los `object` de la app en cero
 *
 * ## El defecto que cierra
 *
 * Todas las clases Robolectric de `:shared:testDebugUnitTest` corren en **un solo fork de JVM
 * compartiendo el mismo sandbox**, así que los `object` de la app —que son estado del proceso—
 * sobreviven de una clase a la siguiente. No es teoría: antes de esto, sondeando el final de la
 * suite, `UsedCategoriesCache` quedaba con `{Comida=[EXPENSE]}`, que le deja
 * `MovimientosPlegablesTest` al montar `TransactionsScreen` —esa pantalla alimenta el caché de
 * paso, como en la app de verdad—.
 *
 * Una prueba que MIDE una pantalla cuyo contenido sale de uno de esos `object` no está midiendo la
 * pantalla: está midiendo la pantalla más la resaca de la suite. Eso ya se cobró una:
 * `HojaAgregarGeometriaTest.lasCategoriasSeVenEnElSubPickerDelTelefono` era intermitente porque el
 * panel de sugerencias dibuja el catálogo **más** lo que haya en `UsedCategoriesCache`, y su
 * aserción tenía margen de una fila exacta.
 *
 * ## Por qué acá y no en un `@Before` de cada clase
 *
 * Un `@Before` por clase solo protege a la clase que se acordó de escribirlo, y la prueba que lo
 * necesita es siempre la que todavía no existe. Robolectric instancia el `Application` **una vez
 * por método de prueba**, y `TestLifecycleApplication.beforeTest` corre con el entorno ya armado y
 * antes de cualquier `@Before`. Declarado en `robolectric.properties` (`application=…`), esto
 * aplica a **toda** la suite sin que ninguna clase tenga que enterarse ni cambiar una línea.
 *
 * ## Por qué `SessionManager.clear()` y no una lista de nueve `clear()`
 *
 * Porque en producción ese método ya ES el «olvidá todo lo de este usuario»: arrastra
 * `ScreenDefCache`, `DashboardDataCache`, `UsedCategoriesCache`, `RecurringOfferGate`,
 * `ReminderChannelsCache`, `LastAccountStore` y `DiasPlegadosStore`, además de la sesión. Copiar
 * esa lista acá sería tener dos listas para desincronizar. Y si mañana el logout deja de limpiar
 * uno, `ElForkLlegaLimpioTest` se pone rojo — que es donde queremos enterarnos.
 *
 * (`reloadForLogout()`, lo último que hace ese `clear()`, es `{}` en Android — ver
 * `Platform.android.kt:66`. Acá no recarga nada.)
 *
 * [Repositories.sustitutoDePrueba] va aparte porque no es del usuario sino del andamio: lo pone
 * media docena de clases de esta suite y hoy todas lo devuelven en su `@After`. Esto es para que
 * un olvido ajeno no se cobre en la clase siguiente.
 *
 * ## Lo que esto NO hace, y hay que decirlo
 *
 * - **No alcanza a las pruebas que no son de Robolectric.** `commonTest` corre en la JVM pelada,
 *   fuera del sandbox, así que tiene su propia copia de cada `object` y este `Application` no la
 *   toca. Las dos copias no se ven entre sí, así que tampoco se ensucian entre sí.
 * - **No conoce los `object` que aparezcan mañana.** Si alguien agrega uno que una pantalla
 *   mutable llene, y el logout no lo limpia, vuelve a haber resaca. Lo que sí hay es un lugar
 *   único donde arreglarlo, y una prueba que enumera los de hoy.
 * - **No es aislamiento de hilos ni de tiempo.** Solo estado estático.
 */
class AppDePrueba : Application(), TestLifecycleApplication {

    override fun beforeTest(method: Method) {
        SessionManager.clear()
        Repositories.sustitutoDePrueba = null
    }

    override fun prepareTest(test: Any) = Unit

    override fun afterTest(method: Method) = Unit
}
