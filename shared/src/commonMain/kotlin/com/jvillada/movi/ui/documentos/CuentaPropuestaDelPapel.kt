package com.jvillada.movi.ui.documentos

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.PistasDelPapel
import com.jvillada.movi.shared.model.ProductoDelPapel
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.cuentasPara
import com.jvillada.movi.shared.model.esDelProducto
import com.jvillada.movi.shared.model.marcaDeLaCuenta
import com.jvillada.movi.shared.model.marcaDelBanco
import com.jvillada.movi.shared.model.numerosDeCuentaDelExtracto
import com.jvillada.movi.shared.model.palabrasDelNombreDelArchivo
import com.jvillada.movi.shared.model.productoQueNombraElArchivo
import com.jvillada.movi.ui.components.cuentaPorElNumero

/** De dónde salió la cuenta que la hoja de subida propone. */
enum class DeDondeSaleLaCuenta {
    /** Movi ya había leído este mismo archivo (la misma huella) y sabe de qué cuenta habla. */
    LO_QUE_MOVI_LEYO,

    /** Los últimos cuatro, o el banco y el producto, que dice el nombre del archivo. */
    NOMBRE_DEL_ARCHIVO,
}

/** La cuenta que la hoja de subida propone, o ninguna. */
data class CuentaPropuesta(val cuenta: Account?, val deDonde: DeDondeSaleLaCuenta?)

/** «Por el nombre del archivo» / «Por lo que Movi ya leyó de este archivo». */
fun textoDeDondeSale(deDonde: DeDondeSaleLaCuenta): String = when (deDonde) {
    DeDondeSaleLaCuenta.LO_QUE_MOVI_LEYO -> POR_LO_QUE_MOVI_LEYO
    DeDondeSaleLaCuenta.NOMBRE_DEL_ARCHIVO -> POR_EL_NOMBRE_DEL_ARCHIVO
}

const val POR_EL_NOMBRE_DEL_ARCHIVO: String = "Por el nombre del archivo"
const val POR_LO_QUE_MOVI_LEYO: String = "Por lo que Movi ya leyó de este archivo"
const val ELIGE_LA_CUENTA: String = "Elige la cuenta"

/** Para qué sirve la cuenta de un extracto: lo que la hoja dice, en una línea. */
const val PARA_QUE_SIRVE_LA_CUENTA: String = "Los movimientos del extracto se anotan en esta cuenta."

/**
 * # Vuelta 129 · la hoja de subida propone la cuenta
 *
 * Una probadora subió «Nu_2026-09-17.pdf» desde Documentos y la hoja le ofrecía «Sin cuenta»,
 * «Bancolombia Ahorros» y «Nu Ahorros»: tenía que adivinar, y si el archivo era el extracto de su
 * tarjeta (que no tiene en Movi) las dos cuentas eran equivocadas. Ahora la hoja propone, en este
 * orden:
 *
 * 1. **lo que Movi ya leyó del contenido** ([pistas]: el mismo archivo ya pasó por un lector, que sabe
 *    el banco, los números y a veces el producto);
 * 2. **los últimos cuatro del nombre del archivo** («Extracto_0756.pdf» → la cuenta con 0756 en el
 *    nombre), con la regla de los avisos ([cuentaPorElNumero]);
 * 3. **el banco y el producto del nombre** («CuentaNu…» → la única cuenta de ahorros de Nu).
 *
 * **El banco solo no alcanza**: «Nu» puede ser la tarjeta o la cuenta. Sin un producto que lo
 * distinga, no se propone nada, tenga el dueño las dos o una sola (el archivo puede ser de la que no
 * tiene en Movi). Ante la duda, «Sin cuenta»: la hoja dice «Elige la cuenta».
 *
 * Solo propone entre las cuentas que la hoja muestra de entrada (`principales` de
 * [UsoDeCuenta.PAPEL_GUARDADO]): nunca una escondida detrás de «Ver todas».
 */
fun cuentaPropuestaParaElPapel(
    nombreDeArchivo: String,
    cuentas: List<Account>,
    pistas: PistasDelPapel? = null,
): CuentaPropuesta {
    val elegibles = cuentasPara(cuentas, UsoDeCuenta.PAPEL_GUARDADO).principales
    pistas?.let { p ->
        porLasPistas(p.banco, p.numerosDeCuenta, p.producto, elegibles)
            ?.let { return CuentaPropuesta(it, DeDondeSaleLaCuenta.LO_QUE_MOVI_LEYO) }
    }
    // Lo que Movi leyó del contenido manda sobre el nombre: si dice que es una tarjeta y el nombre dice
    // «Cuenta», no se propone nada.
    val delArchivo = productoQueNombraElArchivo(nombreDeArchivo)
    val leido = pistas?.producto
    if (leido != null && delArchivo != null && leido != delArchivo) return CuentaPropuesta(null, null)
    porLasPistas(
        banco = palabrasDelNombreDelArchivo(nombreDeArchivo),
        numeros = numerosDeCuentaDelExtracto(nombreDeArchivo),
        producto = leido ?: delArchivo,
        cuentas = elegibles,
    )?.let { return CuentaPropuesta(it, DeDondeSaleLaCuenta.NOMBRE_DEL_ARCHIVO) }
    return CuentaPropuesta(null, null)
}

private fun porLasPistas(banco: String, numeros: List<String>, producto: ProductoDelPapel?, cuentas: List<Account>): Account? {
    val delProducto = if (producto == null) cuentas else cuentas.filter { esDelProducto(it, producto) }
    // El número es un dato, no una suposición: va primero.
    cuentaPorElNumero(numeros.joinToString(" ") { "*$it" }, delProducto)?.let { return it }
    // El banco solo, sin el producto, no distingue la tarjeta de la cuenta.
    if (producto == null) return null
    val marca = marcaDelBanco(banco) ?: return null
    return delProducto.filter { marcaDeLaCuenta(it)?.id == marca.id }.singleOrNull()
}
