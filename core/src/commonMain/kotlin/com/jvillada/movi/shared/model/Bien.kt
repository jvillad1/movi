package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * # Un bien: lo que el dueño TIENE y no es plata
 *
 * **Por qué existe.** El 23-sep el patrimonio neto del dueño decía **−$2.074M**. Era una cuenta
 * correcta de una foto incompleta: Movi sumaba $2.191M de deudas —dos hipotecas, dos libranzas,
 * el crédito del vehículo— y **ningún bien**. La casa de Almendros de Zúñiga, con avalúo comercial
 * de $1.411.903.920 (Banco Popular, 28 de agosto), no existía en ninguna parte de la app, y
 * tampoco el carro que financia el crédito del vehículo. La hipoteca estaba; la casa que la
 * respalda, no. Media foto, y justo la mitad que da tranquilidad.
 *
 * ## Qué es y qué NO es
 *
 * - **Suma al patrimonio**, como activo, con [valor].
 * - **No suma a «Tu plata»** (con la casa no se paga el mercado) **ni a «uso condicionado»**, que
 *   es plata con destino —la pensión voluntaria que solo sirve para vivienda—, o sea plata. Un bien
 *   no es plata con ninguna condición: es un bien. Ver `patrimonioDe` en `Patrimonio.kt`, que es
 *   el único lugar donde se decide en qué balde cae cada cuenta.
 * - **No tiene movimientos.** El valor lo escribe el dueño a mano cuando le llega un avalúo nuevo,
 *   y [valorAl] dice de cuándo es: la pantalla lo muestra («avalúo del 28 de agosto») porque un
 *   valor de hace tres años no pesa lo mismo que uno de la semana pasada, y el que lee tiene
 *   derecho a saberlo.
 *
 * ## Por qué es un campo y no un valor nuevo de [AccountType]
 *
 * Es la regla del repo (ver el KDoc de `OccurrenceState.derivadaDeUnMovimiento`): **campo nuevo
 * con default, nunca un valor nuevo en un enum serializado**. El APK que el dueño tiene instalado
 * deserializa `type` contra el enum que conoce; un `"ASSET"` que no está ahí le revienta la lista
 * de cuentas ENTERA —no solo el bien— y con ella el Inicio. Un campo que no conoce, en cambio, lo
 * ignora (`ignoreUnknownKeys` en los tres `Platform`).
 *
 * Así que un bien viaja como una cuenta `INVESTMENT` con este objeto adentro. Lo que ve cada
 * cliente está escrito en [Account.bien].
 *
 * @property clase `"INMUEBLE"`, `"VEHICULO"` u `"OTRO"`. **Texto y no enum, por lo mismo de
 *   arriba**: el día que aparezca una clase nueva («SEMOVIENTE», «OBRA_DE_ARTE»), un cliente que
 *   no la conozca la lee como [ClaseDeBien.OTRO] en vez de reventar. Ver [claseDeBien].
 * @property valor lo que vale, en pesos. Siempre COP: un avalúo colombiano se escribe en pesos, y
 *   un bien no tiene saldo por moneda que estimar con la TRM.
 * @property valorAl de cuándo es ese valor, ISO `"2026-08-28"`. `null` = el dueño no lo dijo.
 * @property deudaId la cuenta de deuda que lo financia, si hay una (la Hipoteca 1254 ↔ la casa; el
 *   Vehículo 8761 ↔ el carro). Opcional: sirve para decir «lo que es tuyo de verdad» —el valor
 *   menos lo que todavía se debe— y nada más. **No mueve ningún total**: la deuda ya suma en
 *   deudas por su cuenta, y el bien en bienes por la suya; asociarlos no puede cambiar el
 *   patrimonio, solo explicarlo. Ver [deudaDelBien].
 */
@Serializable
data class Bien(
    val clase: String = CLASE_DE_BIEN_OTRO,
    val valor: Long = 0L,
    val valorAl: String? = null,
    val deudaId: String? = null,
)

const val CLASE_DE_BIEN_INMUEBLE = "INMUEBLE"
const val CLASE_DE_BIEN_VEHICULO = "VEHICULO"
const val CLASE_DE_BIEN_OTRO = "OTRO"

/**
 * Las clases que esta versión conoce, para pintarlas. **No es `@Serializable` y no viaja**: por el
 * cable va [Bien.clase] como texto, y este enum es solo cómo lo lee la UI. Ver [claseDeBien].
 */
enum class ClaseDeBien(val clave: String, val nombre: String) {
    INMUEBLE(CLASE_DE_BIEN_INMUEBLE, "Inmueble"),
    VEHICULO(CLASE_DE_BIEN_VEHICULO, "Vehículo"),
    OTRO(CLASE_DE_BIEN_OTRO, "Otro"),
}

/** El texto del cable → la clase. Lo que no se reconoce es [ClaseDeBien.OTRO], nunca un error. */
fun claseDeBien(clave: String?): ClaseDeBien =
    ClaseDeBien.entries.firstOrNull { it.clave == clave?.trim()?.uppercase() } ?: ClaseDeBien.OTRO

/** ¿Esta cuenta es un bien? Ver [Account.bien]. */
val Account.esBien: Boolean get() = bien != null

/**
 * Techo defensivo del valor de un bien: el MISMO que el de un saldo ([MAX_ACCOUNT_BALANCE_COP]), por
 * alias y no por copia. No es un límite de negocio: atrapa el dedazo de un cero de más.
 */
const val MAX_VALOR_DE_BIEN_COP = MAX_ACCOUNT_BALANCE_COP

/**
 * `PUT /api/accounts/{id}/bien` — **actualizar el valor** (o la clase, la fecha o la deuda) de un
 * bien que ya existe. El nombre se cambia por la ruta de siempre (`PUT /{id}/name`).
 *
 * Viaja el bien ENTERO y no un parche: son cuatro campos que se editan juntos en una sola hoja, y
 * un parche obligaría a distinguir «no lo mandé» de «lo quiero vacío» en [Bien.deudaId] — que es
 * justo el campo que el dueño puede querer quitar.
 */
@Serializable
data class ActualizarBienRequest(val bien: Bien)

/**
 * El bien tal como se guarda: clase reconocida, fecha y deuda recortadas, vacío = `null`.
 *
 * Una sola función para el server (que es quien guarda) y la UI (que es quien arma lo que se
 * manda), por la lección de siempre: dos capas que normalizan el mismo texto distinto ya
 * produjeron dos defectos en este repo (ver [normalizarCondicion]).
 */
fun normalizarBien(bien: Bien): Bien = bien.copy(
    clase = claseDeBien(bien.clase).clave,
    valorAl = bien.valorAl?.trim()?.takeIf { it.isNotEmpty() },
    deudaId = bien.deudaId?.trim()?.takeIf { it.isNotEmpty() },
)

/**
 * **Lo primero que está mal en este bien**, o `null` si se puede guardar. Lo usan el server (para
 * contestar 400 con este mismo texto) y la hoja (para decirlo debajo del botón gris ANTES de
 * mandar nada): la misma frase en los dos lados.
 *
 * El valor en cero se rechaza: un bien que no vale nada no le dice nada al patrimonio, y casi
 * siempre es un campo que quedó vacío. La deuda asociada se valida en el server, que es el único
 * que sabe qué cuentas son del dueño.
 */
fun problemaDelBien(bien: Bien): String? = when {
    bien.valor <= 0L -> "Escribe cuánto vale"
    bien.valor > MAX_VALOR_DE_BIEN_COP -> "Valor fuera de rango — revisa el monto"
    bien.valorAl != null && runCatching { LocalDate.parse(bien.valorAl) }.isFailure ->
        "La fecha del valor no es válida"
    else -> null
}

/**
 * La deuda que financia un bien y **lo que es tuyo de verdad**: el valor menos lo que se debe.
 *
 * @property deuda la cuenta de la deuda (la Hipoteca 1254).
 * @property debes lo que se debe hoy, en pesos, con la MISMA cifra con la que la deuda suma al
 *   patrimonio ([valorEnPesosDe]).
 * @property tuyo el valor del bien menos [debes]. Puede ser negativo: un carro que vale menos de
 *   lo que se debe por él es un dato real, y esconderlo sería la mentira contraria.
 */
data class DeudaDelBien(val deuda: Account, val debes: Long, val tuyo: Long)

/**
 * [DeudaDelBien] para [cuentaDelBien], buscando su deuda en [cuentas], o `null` si no es un bien,
 * no tiene deuda asociada, o la deuda ya no está (la borraron, o dejó de ser deuda). Que no esté es
 * un caso normal —se pagó el carro y se borró el crédito— y no un error: el bien sigue valiendo lo
 * mismo, solo deja de haber una resta que mostrar.
 */
fun deudaDelBien(cuentaDelBien: Account, cuentas: List<Account>): DeudaDelBien? {
    val bien = cuentaDelBien.bien ?: return null
    val deudaId = bien.deudaId ?: return null
    val deuda = cuentas.firstOrNull { it.id == deudaId && esCuentaDeDeuda(it.type) } ?: return null
    val debes = valorEnPesosDe(deuda)
    return DeudaDelBien(deuda = deuda, debes = debes, tuyo = bien.valor - debes)
}
