package com.jvillada.movi.data

import com.jvillada.movi.shared.repository.WalletRepository

object Repositories {
    /**
     * Envuelto en [InvalidaElInicioAlEscribir]: cualquier escritura marca la caché del Inicio como
     * vieja, así el TTL de esa pantalla no puede esconder plata que acaba de cambiar.
     */
    private val real: WalletRepository by lazy { InvalidaElInicioAlEscribir(createRepository()) }

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
}
