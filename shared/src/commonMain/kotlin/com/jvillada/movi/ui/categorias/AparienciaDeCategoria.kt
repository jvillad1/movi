package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.theme.COLORES_DEL_CATALOGO
import com.jvillada.movi.theme.COLOR_DE_CATEGORIA_RESPALDO
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.esColorDelCatalogo

/**
 * # Cómo se ve una categoría, en un solo lugar
 *
 * Toda pantalla que muestra una categoría —Movimientos, el Inicio, Presupuestos, el selector—
 * pregunta acá y en ningún otro lado. Si cada una decidiera por su cuenta, «Comida» terminaría
 * naranja en una y verde en otra, que es peor que no tener color.
 *
 * Lo que se resuelve son **claves** ([icono] del catálogo de `IconosDeCategoria.kt`, [color] de
 * `COLORES_DEL_CATALOGO` en `Tokens.kt`), no colores: el color de verdad depende del tema y se lee
 * al dibujar, con `Movi.colores.categoria(color)`. Por eso esto es puro y se prueba sin Compose.
 */
data class AparienciaDeCategoria(
    /** Clave de `ICONOS_DEL_CATALOGO`. */
    val icono: String,
    /** Clave de `COLORES_DEL_CATALOGO`. */
    val color: String,
) {
    /** Calculada y no guardada: así la igualdad es por claves, que es lo que las pruebas comparan. */
    val imagen: ImageVector get() = imagenDeIcono(icono)
}

/**
 * **La regla.** Ícono y color se resuelven **cada uno por su lado**, en este orden:
 *
 * 1. **Lo que eligió el dueño** ([CategoryPref.icono] / [CategoryPref.color]). Si eligió solo el
 *    ícono, el color sigue saliendo de las reglas de abajo, y al revés. Una clave que esta versión
 *    no conoce (la guardó una versión más nueva de Movi) se ignora y se sigue con la regla
 *    siguiente: mostrar el ícono que Movi le daría a ese nombre dice más que el de «otros».
 * 2. **El nombre exacto**, normalizado ([normalizarParaBuscar]: sin tildes ni mayúsculas), contra
 *    [POR_NOMBRE] — el catálogo de Movi y los nombres reales del dueño.
 * 3. **Una palabra del nombre** que empiece por alguna de [PALABRAS_CLAVE] («Almuerzo con la
 *    hija» → comida).
 * 4. **El respaldo**: ícono «otros» y un color **estable** sacado de [colorPorHash]: la misma
 *    categoría sale siempre del mismo color, en el teléfono, en el iPhone y en la web.
 */
fun aparienciaDe(nombre: String, pref: CategoryPref?): AparienciaDeCategoria {
    val porReglas = aparienciaPorReglas(normalizarParaBuscar(nombre))
    val icono = pref?.icono?.trim()?.takeIf { esIconoDelCatalogo(it) } ?: porReglas.icono
    val color = pref?.color?.trim()?.takeIf { esColorDelCatalogo(it) } ?: porReglas.color
    return AparienciaDeCategoria(icono = icono, color = color)
}

/** [aparienciaDe] con lo que el dueño eligió, leído de [UsedCategoriesCache]. */
fun aparienciaDe(nombre: String): AparienciaDeCategoria =
    aparienciaDe(nombre, prefDeCategoria(nombre, UsedCategoriesCache.prefs))

/**
 * La preferencia de una categoría dentro de [prefs] (las de `UsedCategoriesCache`, cuyas llaves
 * son el nombre tal cual lo guardó el server). Primero por el nombre recortado, que es el caso de
 * todos los días; si no, por nombre normalizado, porque un movimiento viejo puede decir «comida»
 * donde la preferencia dice «Comida».
 */
fun prefDeCategoria(nombre: String, prefs: Map<String, CategoryPref>): CategoryPref? {
    prefs[nombre.trim()]?.let { return it }
    val buscado = normalizarParaBuscar(nombre)
    return prefs.entries.firstOrNull { normalizarParaBuscar(it.key) == buscado }?.value
}

// ── Las reglas por nombre ───────────────────────────────────────────────────

/** Varios nombres que se ven igual. Los nombres se escriben como los ve el dueño; se normalizan solos. */
internal class PorNombre(val nombres: List<String>, val icono: String, val color: String)

/**
 * La tabla de nombres conocidos. Datos y no un `when`: agregar un nombre es agregar una fila.
 *
 * Además del catálogo de Movi están los nombres **reales** del dueño («Fútbol», «Hija»,
 * «Gardenera», «Mercado extra»…), y las categorías que Movi escribe sola (traspasos, saldos,
 * ajustes), en gris: no son un gasto con carácter propio y no deberían competir con uno.
 */
internal val TABLA_POR_NOMBRE: List<PorNombre> = listOf(
    PorNombre(listOf("Comida"), "comida", "naranja"),
    PorNombre(listOf("Mercado", "Mercado extra"), "mercado", "lima"),
    PorNombre(listOf("Fútbol"), "futbol", "verde"),
    PorNombre(listOf("Estadio"), "estadio", "verde"),
    PorNombre(listOf("Gimnasio"), "gimnasio", "celeste"),
    PorNombre(listOf("Hija"), "hija", "rosa"),
    PorNombre(listOf("Familia"), "familia", "rosa"),
    PorNombre(listOf("Celular"), "celular", "azul"),
    PorNombre(listOf("Cuota de crédito", "Crédito"), "credito", "violeta"),
    PorNombre(listOf("Pago de tarjeta"), "tarjeta", "violeta"),
    PorNombre(listOf("Comisiones del banco"), "banco", "gris"),
    PorNombre(listOf("Impuestos"), "impuestos", "gris"),
    PorNombre(listOf("Salud"), "salud", "rojo"),
    PorNombre(listOf("Transporte"), "transporte", "azul"),
    PorNombre(listOf("Tecnología"), "tecnologia", "celeste"),
    PorNombre(listOf("Entretenimiento"), "entretenimiento", "ambar"),
    PorNombre(listOf("Servicios"), "servicios", "ambar"),
    PorNombre(listOf("Vivienda", "Gardenera"), "casa", "ambar"),
    PorNombre(listOf("Educación"), "educacion", "azul"),
    PorNombre(listOf("Ropa"), "ropa", "rosa"),
    PorNombre(listOf("Otros"), ICONO_DE_CATEGORIA_RESPALDO, COLOR_DE_CATEGORIA_RESPALDO),
    PorNombre(listOf("Salario", "Nómina"), "salario", "verde"),
    PorNombre(listOf("Freelance"), "trabajo", "verde"),
    PorNombre(listOf("Arriendo recibido"), "arriendo", "verde"),
    PorNombre(listOf("Inversiones"), "inversiones", "verde"),
    PorNombre(
        listOf("Otros ingresos", "Ingreso", "Transferencia", "Pago de un tercero"),
        "ingreso",
        "verde",
    ),
    // Las que escribe Movi sola: neutras a propósito.
    PorNombre(listOf("Traspaso", "Cuenta eliminada"), "transferencia", COLOR_DE_CATEGORIA_RESPALDO),
    PorNombre(listOf("Saldo inicial", "Ajuste de saldo"), "banco", COLOR_DE_CATEGORIA_RESPALDO),
    PorNombre(listOf("Descuento de nómina"), "salario", COLOR_DE_CATEGORIA_RESPALDO),
)

/** [TABLA_POR_NOMBRE] indexada por nombre normalizado. */
private val POR_NOMBRE: Map<String, AparienciaDeCategoria> = buildMap {
    for (fila in TABLA_POR_NOMBRE) {
        for (nombre in fila.nombres) put(normalizarParaBuscar(nombre), AparienciaDeCategoria(fila.icono, fila.color))
    }
}

// ── Las palabras clave ──────────────────────────────────────────────────────

/**
 * Una palabra que delata la categoría. Se compara contra **cada palabra** del nombre normalizado:
 * por prefijo («medic» atrapa «Médico» y «Medicamentos»), o entera si [entera] — «agua» por
 * prefijo se llevaría «Aguacate» a los servicios.
 */
internal class PalabraClave(
    val raiz: String,
    val icono: String,
    val color: String,
    val entera: Boolean = false,
)

/**
 * **El orden importa: gana la primera fila que aparezca en el nombre**, así que lo más específico
 * va antes («cuota de manejo» es del banco, no de un crédito). Las raíces ya van normalizadas.
 */
internal val PALABRAS_CLAVE: List<PalabraClave> = listOf(
    // Lo que cobra el banco, antes que «cuota» y «tarjeta».
    PalabraClave("manejo", "banco", "gris"),
    PalabraClave("comision", "banco", "gris"),
    PalabraClave("interes", "banco", "gris"),
    PalabraClave("gmf", "banco", "gris", entera = true),
    PalabraClave("4x1000", "banco", "gris", entera = true),
    PalabraClave("impuesto", "impuestos", "gris"),
    PalabraClave("predial", "impuestos", "gris"),
    PalabraClave("dian", "impuestos", "gris", entera = true),
    // Una cuota es de un crédito aunque diga de qué: «Cuota del carro» no es gasolina.
    PalabraClave("cuota", "credito", "violeta"),
    PalabraClave("credito", "credito", "violeta"),
    PalabraClave("prestamo", "credito", "violeta"),
    // Suscripciones, antes que «música» o «cine»: Spotify es una suscripción.
    PalabraClave("suscrip", "suscripciones", "ambar"),
    PalabraClave("netflix", "suscripciones", "ambar"),
    PalabraClave("spotify", "suscripciones", "ambar"),
    PalabraClave("disney", "suscripciones", "ambar"),
    PalabraClave("youtube", "suscripciones", "ambar"),
    PalabraClave("hbo", "suscripciones", "ambar", entera = true),
    // Comida.
    PalabraClave("restaurante", "restaurante", "naranja"),
    PalabraClave("almuerzo", "comida", "naranja"),
    PalabraClave("desayuno", "comida", "naranja"),
    PalabraClave("comida", "comida", "naranja"),
    PalabraClave("domicilio", "comida", "naranja"),
    PalabraClave("rappi", "comida", "naranja"),
    PalabraClave("cafe", "cafe", "naranja"),
    PalabraClave("panaderia", "cafe", "naranja"),
    PalabraClave("mercado", "mercado", "lima"),
    PalabraClave("supermercado", "mercado", "lima"),
    // Moverse.
    PalabraClave("uber", "taxi", "azul"),
    PalabraClave("taxi", "taxi", "azul"),
    PalabraClave("didi", "taxi", "azul"),
    PalabraClave("bus", "bus", "azul", entera = true),
    PalabraClave("transmilenio", "bus", "azul"),
    PalabraClave("metro", "bus", "azul", entera = true),
    PalabraClave("gasolina", "gasolina", "azul"),
    PalabraClave("peaje", "carro", "azul"),
    PalabraClave("parqueadero", "carro", "azul"),
    PalabraClave("carro", "carro", "azul"),
    PalabraClave("moto", "moto", "azul"),
    PalabraClave("transporte", "transporte", "azul"),
    // Salud y cuerpo.
    PalabraClave("farmacia", "medicamentos", "rojo"),
    PalabraClave("drogueria", "medicamentos", "rojo"),
    PalabraClave("medic", "salud", "rojo"),
    PalabraClave("odontolog", "salud", "rojo"),
    PalabraClave("clinica", "salud", "rojo"),
    PalabraClave("eps", "salud", "rojo", entera = true),
    PalabraClave("salud", "salud", "rojo"),
    PalabraClave("gimnasio", "gimnasio", "celeste"),
    PalabraClave("gym", "gimnasio", "celeste", entera = true),
    PalabraClave("futbol", "futbol", "verde"),
    PalabraClave("estadio", "estadio", "verde"),
    PalabraClave("peluqueria", "belleza", "rosa"),
    PalabraClave("barberia", "belleza", "rosa"),
    // Casa.
    PalabraClave("arriendo", "arriendo", "ambar"),
    PalabraClave("administracion", "casa", "ambar"),
    PalabraClave("energia", "luz", "ambar"),
    PalabraClave("luz", "luz", "ambar", entera = true),
    PalabraClave("agua", "agua", "ambar", entera = true),
    PalabraClave("acueducto", "agua", "ambar"),
    PalabraClave("gas", "servicios", "ambar", entera = true),
    PalabraClave("internet", "internet", "azul"),
    PalabraClave("celular", "celular", "azul"),
    PalabraClave("reparacion", "arreglos", "ambar"),
    PalabraClave("arreglo", "arreglos", "ambar"),
    PalabraClave("mantenimiento", "arreglos", "ambar"),
    // Personas.
    PalabraClave("hija", "hija", "rosa"),
    PalabraClave("hijo", "hija", "rosa"),
    PalabraClave("ninos", "hija", "rosa", entera = true),
    PalabraClave("bebe", "hija", "rosa", entera = true),
    PalabraClave("familia", "familia", "rosa"),
    PalabraClave("mascota", "mascota", "naranja"),
    PalabraClave("veterinari", "mascota", "naranja"),
    PalabraClave("perro", "mascota", "naranja"),
    PalabraClave("gato", "mascota", "naranja", entera = true),
    PalabraClave("regalo", "regalos", "rosa"),
    PalabraClave("donacion", "donacion", "rosa"),
    PalabraClave("diezmo", "donacion", "rosa"),
    // Aprender, vestirse, pasarla bien.
    PalabraClave("colegio", "educacion", "azul"),
    PalabraClave("universidad", "educacion", "azul"),
    PalabraClave("matricula", "educacion", "azul"),
    PalabraClave("curso", "educacion", "azul"),
    PalabraClave("libro", "libros", "azul"),
    PalabraClave("ropa", "ropa", "rosa"),
    PalabraClave("zapato", "ropa", "rosa"),
    PalabraClave("cine", "entretenimiento", "ambar", entera = true),
    PalabraClave("concierto", "musica", "ambar"),
    PalabraClave("musica", "musica", "ambar"),
    PalabraClave("viaje", "viajes", "celeste"),
    PalabraClave("vuelo", "viajes", "celeste"),
    PalabraClave("hotel", "viajes", "celeste"),
    PalabraClave("tiquete", "viajes", "celeste"),
    PalabraClave("computador", "tecnologia", "celeste"),
    PalabraClave("tecnolog", "tecnologia", "celeste"),
    // Plata que va y viene.
    PalabraClave("tarjeta", "tarjeta", "violeta"),
    PalabraClave("ahorro", "ahorro", "verde"),
    PalabraClave("inversion", "inversiones", "verde"),
    PalabraClave("cdt", "inversiones", "verde", entera = true),
    PalabraClave("salario", "salario", "verde"),
    PalabraClave("sueldo", "salario", "verde"),
    PalabraClave("nomina", "salario", "verde"),
    PalabraClave("honorario", "trabajo", "verde"),
    PalabraClave("freelance", "trabajo", "verde"),
    PalabraClave("transferencia", "transferencia", "verde"),
    PalabraClave("ingreso", "ingreso", "verde"),
)

/**
 * Lo que no es letra ni dígito separa palabras: «Almuerzo/cena» son dos. A mano y no con un
 * `Regex` de clases Unicode (`\p{L}`), que no se comporta igual en los tres targets.
 */
private fun palabrasDe(normalizado: String): List<String> {
    val palabras = mutableListOf<String>()
    val actual = StringBuilder()
    for (c in normalizado) {
        if (c.isLetterOrDigit()) {
            actual.append(c)
        } else if (actual.isNotEmpty()) {
            palabras += actual.toString()
            actual.clear()
        }
    }
    if (actual.isNotEmpty()) palabras += actual.toString()
    return palabras
}

private fun porPalabraClave(normalizado: String): AparienciaDeCategoria? {
    val palabras = palabrasDe(normalizado)
    if (palabras.isEmpty()) return null
    val fila = PALABRAS_CLAVE.firstOrNull { clave ->
        palabras.any { if (clave.entera) it == clave.raiz else it.startsWith(clave.raiz) }
    } ?: return null
    return AparienciaDeCategoria(fila.icono, fila.color)
}

// ── El respaldo ─────────────────────────────────────────────────────────────

/**
 * Los colores entre los que elige el hash: todos menos el gris, que en Movi quiere decir «sin
 * clasificar» o «lo escribe Movi sola». Una categoría del dueño que cayera en gris por azar se
 * leería como una de esas.
 */
private val COLORES_DEL_HASH: List<String> =
    COLORES_DEL_CATALOGO.map { it.clave }.filter { it != COLOR_DE_CATEGORIA_RESPALDO }

/**
 * Un color **estable** para un nombre que ninguna regla reconoce.
 *
 * Un polinomio escrito a mano (`h = h·31 + c`, en `UInt`) y no `String.hashCode()`: nada garantiza
 * que `hashCode` dé lo mismo en la JVM, en Kotlin/Native y en wasm, y un color que cambia de un
 * aparato a otro es un color que no identifica nada. Tampoco hay que cambiarlo nunca: cambiarlo
 * repinta de golpe todas las categorías propias de todos los dueños (hay una prueba que fija
 * resultados concretos para que eso no pase por accidente).
 *
 * Recibe el nombre **ya normalizado**, así «Carro» y «carro» dan lo mismo.
 */
internal fun colorPorHash(normalizado: String): String {
    var h = 0u
    for (c in normalizado) h = h * 31u + c.code.toUInt()
    return COLORES_DEL_HASH[(h % COLORES_DEL_HASH.size.toUInt()).toInt()]
}

private fun aparienciaPorReglas(normalizado: String): AparienciaDeCategoria =
    POR_NOMBRE[normalizado]
        ?: porPalabraClave(normalizado)
        ?: AparienciaDeCategoria(ICONO_DE_CATEGORIA_RESPALDO, colorPorHash(normalizado))

// ── El dibujo ───────────────────────────────────────────────────────────────

/** El `testTag` que lleva todo [IconoDeCategoria], para que una prueba lo encuentre en una fila. */
const val TAG_ICONO_DE_CATEGORIA: String = "icono-de-categoria"

/**
 * Los dos tamaños. [Normal] es el de una fila de lista; [Chico] el que va junto a un nombre en
 * una línea de texto (una barra del Inicio, el rótulo de una hoja).
 */
enum class TamanoDeIconoDeCategoria(val circulo: Dp, val icono: Dp) {
    Normal(circulo = 36.dp, icono = 20.dp),
    Chico(circulo = 24.dp, icono = 14.dp),
}

/**
 * El ícono de una categoría: un círculo del color con alfa bajo y el ícono del color encima. Lee
 * lo que el dueño eligió en [UsedCategoriesCache], así que un cambio en «Categorías» se ve en toda
 * la app sin recargar nada.
 *
 * Sin `contentDescription`: el nombre de la categoría va siempre al lado, y un lector de pantalla
 * que dijera «Comida, Comida» no ayuda a nadie.
 */
@Composable
fun IconoDeCategoria(
    nombre: String,
    modifier: Modifier = Modifier,
    tamano: TamanoDeIconoDeCategoria = TamanoDeIconoDeCategoria.Normal,
) {
    IconoDeCategoria(apariencia = aparienciaDe(nombre), modifier = modifier, tamano = tamano)
}

/**
 * Lo mismo con la apariencia ya resuelta: para la vista previa del selector (lo que el dueño está
 * eligiendo todavía no está guardado) o para lo que no es una categoría del dueño y quiere verse
 * igual (un traspaso).
 */
@Composable
fun IconoDeCategoria(
    apariencia: AparienciaDeCategoria,
    modifier: Modifier = Modifier,
    tamano: TamanoDeIconoDeCategoria = TamanoDeIconoDeCategoria.Normal,
) {
    Box(
        modifier = modifier
            .testTag(TAG_ICONO_DE_CATEGORIA)
            .size(tamano.circulo)
            .background(Movi.colores.circuloDeCategoria(apariencia.color), RoundedCornerShape(Movi.formas.pleno)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = apariencia.imagen,
            contentDescription = null,
            tint = Movi.colores.categoria(apariencia.color),
            modifier = Modifier.size(tamano.icono),
        )
    }
}

/** El color de una categoría en el tema actual — para lo que se pinta de ella además del ícono. */
@Composable
fun colorDeCategoria(nombre: String): Color = Movi.colores.categoria(aparienciaDe(nombre).color)
