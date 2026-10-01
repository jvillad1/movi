package com.jvillada.movi.data

import com.jvillada.movi.shared.repository.CompartirRepository
import com.jvillada.movi.shared.repository.EnlacesCompartidosApi
import com.jvillada.movi.shared.repository.WalletRepository

object Repositories {
    /**
     * Envuelto en [InvalidaElInicioAlEscribir]: cualquier escritura marca la caché del Inicio como
     * vieja, así el TTL de esa pantalla no puede esconder plata que acaba de cambiar.
     */
    private val realPerezoso: Lazy<WalletRepository> = lazy {
        val repo = createRepository()
        // Un cierre de sesión que llegó antes de que existiera el repositorio (ver
        // [olvidarDatosLocales]) se cumple ahora, apenas hay base abierta, con la misma regla: solo
        // lo sellado y el caché, nunca lo que no subió. Si quien entró es la misma persona no se
        // borra nada: borrarle lo suyo al volver no protege a nadie.
        SessionManager.borradoLocalPendiente?.let { pendiente ->
            if (pendiente != SessionManager.userId) runCatching { repo.olvidarDatosLocales(pendiente) }
            SessionManager.borradoLocalPendiente = null
        }
        InvalidaElInicioAlEscribir(repo)
    }
    private val real: WalletRepository by realPerezoso

    /**
     * **La única costura de pruebas de este objeto, y el motivo por el que [wallets] pasó de ser
     * un `val by lazy` a un getter.**
     *
     * Una pantalla de Movi no recibe su repositorio: lo lee de acá. Eso está bien para la app —no
     * hay inyección que mantener— y deja una consecuencia fea: **ninguna prueba podía montar una
     * pantalla con datos**. `HojaAgregarGeometriaTest` monta la hoja de «Agregar» y la lista de
     * cuentas llega vacía siempre, porque abajo hay un cliente HTTP apuntando a producción.
     *
     * Eso no es un detalle de comodidad. La rama que agregó el criterio de «¿de dónde sale la
     * plata?» dejó el cableado —`usoDeCuenta`, `cuentasDelPicker`, el `WalletPicker(cuentas=…)`—
     * sostenido **solo por el compilador**: la función pura tenía sus pruebas en verde y ningún
     * test tocaba el camino que la usa. Este repo ya tuvo exactamente eso: una feature entera
     * viviendo en una rama que ningún call site alcanzaba, compilando, con su prueba pasando.
     *
     * `internal` para que no exista fuera de `:shared`, y `null` por defecto para que en la app no
     * cambie nada: el `?:` cae en [real], que sigue siendo perezoso — si una prueba pone el
     * sustituto antes de la primera lectura, el cliente HTTP y la base de SQLDelight **ni se
     * construyen**.
     */
    internal var sustitutoDePrueba: WalletRepository? = null

    /** El repositorio que usa toda la app. Ver [sustitutoDePrueba]. */
    val wallets: WalletRepository get() = sustitutoDePrueba ?: real

    private val compartirReal: CompartirRepository by lazy { EnlacesCompartidosApi(createHttpClient(), apiBaseUrl) }

    /**
     * La misma costura que [sustitutoDePrueba], para «Compartir» — sin ella
     * `CompartirScreen` no se podía montar en una prueba con datos de verdad, porque [compartir]
     * apuntaba siempre al cliente HTTP real. `null` por defecto: en la app no cambia nada.
     */
    internal var sustitutoDeCompartirDePrueba: CompartirRepository? = null

    /**
     * Los enlaces de solo lectura para un tercero (pantalla «Compartir»). Aparte de [wallets] a
     * propósito: no tienen espejo local ni mueven plata — ver el KDoc de [EnlacesCompartidosApi].
     * Perezoso, igual que [real]: una prueba que nunca abre esa pantalla no construye el cliente.
     */
    val compartir: CompartirRepository get() = sustitutoDeCompartirDePrueba ?: compartirReal

    /**
     * **Que en el aparato no quede nada de [userId]** — lo llama [SessionManager.clear].
     *
     * Si el repositorio ya existe, borra ahora. Si **todavía no existe**, no lo construye: en
     * Android eso es abrir la base, y `SessionManager.clear()` también corre desde un Worker que
     * levantó el proceso solo —sin `MainActivity`, sin `DatabaseDriverFactory.init`— donde
     * construirlo revienta. Ahí se anota el pendiente, y se cumple la próxima vez que el
     * repositorio se construya (ver [realPerezoso]).
     */
    fun olvidarDatosLocales(userId: String) {
        sustitutoDePrueba?.let { it.olvidarDatosLocales(userId); return }
        if (realPerezoso.isInitialized()) real.olvidarDatosLocales(userId)
        else SessionManager.borradoLocalPendiente = userId
    }
}
