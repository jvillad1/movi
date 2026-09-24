package com.jvillada.movi.ui.categorias

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Stadium
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * # Los íconos de categoría: un catálogo cerrado
 *
 * Cerrado a propósito. El dueño elige de esta lista y lo que se guarda en el server es la
 * **clave** (`category_prefs.icono`, 40 caracteres), no el ícono: así la misma elección se ve igual
 * en el teléfono, en el iPhone y en la web, y un cambio de librería de íconos no rompe nada
 * guardado.
 *
 * Las claves son cortas, ASCII, en minúscula y sin tildes (`futbol`, no `Fútbol`), porque viajan y
 * se comparan tal cual. **Una clave publicada no se renombra**: ya puede estar guardada en la base
 * de alguien, y renombrarla la dejaría cayendo en [ICONO_DE_CATEGORIA_RESPALDO] sin aviso. Agregar
 * claves nuevas, sí; y un APK viejo que reciba una que no conoce muestra el ícono de Movi para esa
 * categoría (ver `aparienciaDe`), no un hueco.
 *
 * Solo Material Icons: en wasm no hay fuente de emoji y un emoji sale como un cuadrado.
 *
 * Elegidos para la vida de una familia colombiana — mercado, arriendo, servicios, fútbol, la
 * hija —, no para una app genérica de finanzas.
 */
data class IconoDelCatalogo(
    /** Lo que viaja y se guarda. Ver el KDoc del archivo. */
    val clave: String,
    /** Cómo se dice en el selector. */
    val rotulo: String,
    val imagen: ImageVector,
)

/** La clave del ícono que toma una categoría que no se sabe dibujar. */
const val ICONO_DE_CATEGORIA_RESPALDO = "otros"

/** El catálogo, **en el orden del selector**: primero lo de todos los días, lo de plata al final. */
val ICONOS_DEL_CATALOGO: List<IconoDelCatalogo> = listOf(
    IconoDelCatalogo("comida", "Comida", Icons.Rounded.LunchDining),
    IconoDelCatalogo("restaurante", "Restaurante", Icons.Rounded.Restaurant),
    IconoDelCatalogo("cafe", "Café", Icons.Rounded.LocalCafe),
    IconoDelCatalogo("mercado", "Mercado", Icons.Rounded.ShoppingCart),
    IconoDelCatalogo("transporte", "Transporte", Icons.Rounded.Commute),
    IconoDelCatalogo("carro", "Carro", Icons.Rounded.DirectionsCar),
    IconoDelCatalogo("gasolina", "Gasolina", Icons.Rounded.LocalGasStation),
    IconoDelCatalogo("taxi", "Taxi", Icons.Rounded.LocalTaxi),
    IconoDelCatalogo("bus", "Bus", Icons.Rounded.DirectionsBus),
    IconoDelCatalogo("moto", "Moto", Icons.Rounded.TwoWheeler),
    IconoDelCatalogo("casa", "Casa", Icons.Rounded.Home),
    IconoDelCatalogo("arriendo", "Arriendo", Icons.Rounded.Key),
    IconoDelCatalogo("servicios", "Servicios", Icons.AutoMirrored.Rounded.ReceiptLong),
    IconoDelCatalogo("luz", "Luz", Icons.Rounded.Lightbulb),
    IconoDelCatalogo("agua", "Agua", Icons.Rounded.WaterDrop),
    IconoDelCatalogo("internet", "Internet", Icons.Rounded.Wifi),
    IconoDelCatalogo("celular", "Celular", Icons.Rounded.Smartphone),
    IconoDelCatalogo("arreglos", "Arreglos del hogar", Icons.Rounded.Handyman),
    IconoDelCatalogo("salud", "Salud", Icons.Rounded.MedicalServices),
    IconoDelCatalogo("medicamentos", "Medicamentos", Icons.Rounded.Medication),
    IconoDelCatalogo("gimnasio", "Gimnasio", Icons.Rounded.FitnessCenter),
    IconoDelCatalogo("futbol", "Fútbol", Icons.Rounded.SportsSoccer),
    IconoDelCatalogo("deporte", "Deporte", Icons.AutoMirrored.Rounded.DirectionsRun),
    IconoDelCatalogo("estadio", "Estadio", Icons.Rounded.Stadium),
    IconoDelCatalogo("evento", "Evento", Icons.Rounded.ConfirmationNumber),
    IconoDelCatalogo("hija", "Hijos", Icons.Rounded.ChildCare),
    IconoDelCatalogo("familia", "Familia", Icons.Rounded.FamilyRestroom),
    IconoDelCatalogo("mascota", "Mascota", Icons.Rounded.Pets),
    IconoDelCatalogo("educacion", "Educación", Icons.Rounded.School),
    IconoDelCatalogo("libros", "Libros", Icons.AutoMirrored.Rounded.MenuBook),
    IconoDelCatalogo("ropa", "Ropa", Icons.Rounded.Checkroom),
    IconoDelCatalogo("belleza", "Belleza", Icons.Rounded.Spa),
    IconoDelCatalogo("tecnologia", "Tecnología", Icons.Rounded.Computer),
    IconoDelCatalogo("entretenimiento", "Entretenimiento", Icons.Rounded.Movie),
    IconoDelCatalogo("musica", "Música", Icons.Rounded.MusicNote),
    IconoDelCatalogo("suscripciones", "Suscripciones", Icons.Rounded.Subscriptions),
    IconoDelCatalogo("viajes", "Viajes", Icons.Rounded.Flight),
    IconoDelCatalogo("regalos", "Regalos", Icons.Rounded.CardGiftcard),
    IconoDelCatalogo("donacion", "Donación", Icons.Rounded.VolunteerActivism),
    IconoDelCatalogo("impuestos", "Impuestos", Icons.Rounded.RequestQuote),
    IconoDelCatalogo("banco", "Banco y comisiones", Icons.Rounded.AccountBalance),
    IconoDelCatalogo("credito", "Crédito o cuota", Icons.Rounded.CreditScore),
    IconoDelCatalogo("tarjeta", "Tarjeta", Icons.Rounded.CreditCard),
    IconoDelCatalogo("ahorro", "Ahorro", Icons.Rounded.Savings),
    IconoDelCatalogo("inversiones", "Inversiones", Icons.AutoMirrored.Rounded.TrendingUp),
    IconoDelCatalogo("salario", "Salario", Icons.Rounded.Payments),
    IconoDelCatalogo("trabajo", "Trabajo", Icons.Rounded.Work),
    IconoDelCatalogo("ingreso", "Ingreso", Icons.Rounded.AttachMoney),
    IconoDelCatalogo("transferencia", "Transferencia", Icons.Rounded.SwapHoriz),
    IconoDelCatalogo(ICONO_DE_CATEGORIA_RESPALDO, "Otros", Icons.Rounded.Category),
)

private val ICONOS_POR_CLAVE: Map<String, IconoDelCatalogo> = ICONOS_DEL_CATALOGO.associateBy { it.clave }

/** ¿Es una clave del catálogo? Una desconocida puede venir de una versión más nueva de Movi. */
fun esIconoDelCatalogo(clave: String?): Boolean = clave != null && clave in ICONOS_POR_CLAVE

/** El ícono de una clave. Una desconocida —o `null`— da el de [ICONO_DE_CATEGORIA_RESPALDO]. */
fun imagenDeIcono(clave: String?): ImageVector =
    (ICONOS_POR_CLAVE[clave] ?: ICONOS_POR_CLAVE.getValue(ICONO_DE_CATEGORIA_RESPALDO)).imagen
