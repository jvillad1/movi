package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.BANCOS_CONOCIDOS
import com.jvillada.movi.shared.model.MAX_ACCOUNT_NAME_LENGTH
import com.jvillada.movi.shared.model.Marca
import com.jvillada.movi.shared.model.marcaDeLaCuenta
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.bancosParaElegir
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.CirculoDeMarca
import com.jvillada.movi.ui.components.TamanoDeAvatar
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.CampoDeBusqueda
import com.jvillada.movi.ui.components.ds.CampoDeMonto
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RotuloDeCampo

/** El tag del bloque de crear una cuenta: el mismo en la guía de primer uso y en «Nueva cuenta». */
const val TAG_FORMULARIO_DE_CUENTA: String = "formulario-de-cuenta"

/** El tag de la píldora «Bien», la que abre la hoja de bienes en vez de elegir un tipo. */
const val TAG_TIPO_BIEN: String = "formulario-de-cuenta-bien"

/**
 * Los bancos que se ofrecen de un toque, por su id de `MarcasConocidas` y en el orden en que se ven.
 * Los más comunes en Colombia; los demás, en «Ver todos los bancos» (vuelta 88), y cualquier otro se
 * escribe a mano.
 *
 * Vuelta 98: el dueño pidió Davibank (estaba en el catálogo y no acá) y se sumaron los que más se usan
 * y faltaban. Son 16, y 16 píldoras con su avatar llenaban media hoja: a la vista van los primeros
 * [BANCOS_A_LA_VISTA] y los demás salen en «Ver todos los bancos», con el resto del catálogo.
 */
val BANCOS_DE_UN_TOQUE: List<String> = listOf(
    "bancolombia", "nu", "davivienda", "nequi", "banco_de_bogota", "bbva", "daviplata", "davibank",
    "occidente", "av_villas", "scotiabank", "banco_popular", "banco_falabella", "lulo", "itau", "rappipay",
)

/**
 * Cuántos de [BANCOS_DE_UN_TOQUE] se ven como píldoras (vuelta 98). Ocho son dos o tres renglones a
 * 360 dp: la cuadrícula sigue limpia y lo de siempre queda de un toque.
 */
const val BANCOS_A_LA_VISTA: Int = 8

/**
 * **Los tipos de cuenta, en palabras que entiende cualquiera** (vuelta de diseño 3 en la guía; desde
 * la vuelta 16, también en «Nueva cuenta»). Cada rótulo va a un [AccountType] que ya existe —no se
 * inventa ninguno—: ahorros, corriente y efectivo son los tres de **Dinero** (`AccountType.group`), y
 * se suman igual en todas las cuentas de Movi; lo que cambia es que el efectivo no recibe extractos.
 * Antes «Nueva cuenta» decía «Dinero / Inversión», los dos grupos de Patrimonio, que quien empieza no
 * sabe leer.
 */
val TIPOS_DE_CUENTA: List<Pair<String, AccountType>> = listOf(
    "Ahorros" to AccountType.SAVINGS,
    "Corriente" to AccountType.CHECKING,
    "Efectivo" to AccountType.CASH,
    "Inversión" to AccountType.INVESTMENT,
)

/** El rótulo de la píldora que abre la hoja de un bien (la casa, el carro). */
const val ROTULO_DEL_TIPO_BIEN: String = "Bien"

/** «Ahorros», «Corriente»…: el rótulo de [TIPOS_DE_CUENTA] de un tipo, o `null` si no se ofrece. */
fun rotuloDelTipoDeCuenta(tipo: AccountType): String? = TIPOS_DE_CUENTA.firstOrNull { it.second == tipo }?.first

/**
 * **El nombre que Movi propone** al elegir un banco: el banco y el tipo, «Bancolombia Ahorros». Sin
 * banco no propone nada. Pura.
 */
fun nombrePropuestoDeLaCuenta(banco: String?, tipo: AccountType): String? {
    banco ?: return null
    val rotulo = rotuloDelTipoDeCuenta(tipo) ?: return banco
    return "$banco $rotulo"
}

/**
 * **Lo que se lleva escrito del bloque de crear una cuenta**: banco, tipo, nombre y saldo. Lo
 * recuerda quien lo pinta (la guía lo limpia al agregar; la hoja se cierra).
 *
 * El nombre se propone solo ([nombrePropuestoDeLaCuenta]) **mientras esté vacío o siga siendo la
 * propuesta**: si el dueño escribió el suyo, elegir otro banco o tipo no se lo pisa.
 */
@Stable
class EstadoDelFormularioDeCuenta {
    var banco: String? by mutableStateOf(null)
        private set
    var tipo: AccountType by mutableStateOf(AccountType.SAVINGS)
        private set
    var nombre: String by mutableStateOf("")
    var saldo: Long? by mutableStateOf(null)

    private fun proponerSiNoLoEscribio(antes: String?) {
        if (nombre.isBlank() || nombre == antes) nombrePropuestoDeLaCuenta(banco, tipo)?.let { nombre = it }
    }

    fun elegirBanco(nuevo: String) {
        val antes = nombrePropuestoDeLaCuenta(banco, tipo)
        banco = nuevo
        proponerSiNoLoEscribio(antes)
    }

    fun elegirTipo(nuevo: AccountType) {
        val antes = nombrePropuestoDeLaCuenta(banco, tipo)
        tipo = nuevo
        proponerSiNoLoEscribio(antes)
    }

    /**
     * El nombre que se le pasa a la hoja de un bien: el que escribió el dueño, no el que Movi
     * propuso para una cuenta («Bancolombia Ahorros» no es el nombre de una casa).
     */
    fun nombreParaUnBien(): String =
        nombre.trim().takeIf { it != nombrePropuestoDeLaCuenta(banco, tipo) }.orEmpty()

    /** Lo único obligatorio es el nombre: el saldo puede quedar en $0. */
    val sePuedeCrear: Boolean get() = nombre.isNotBlank()

    /**
     * La cuenta que se crea, con la misma forma en los dos lugares: COP (el display de saldos solo
     * entiende COP), el saldo de hoy en `balance` —que `crearCuentaConSaldo` convierte en la apertura
     * de siempre— y un id propio desde el primer instante (ver `newId`).
     */
    fun cuentaNueva(): Account =
        Account(id = newId("acc"), name = nombre.trim(), type = tipo, balance = saldo ?: 0L, currency = "COP")

    /**
     * **Llenarlo con una cuenta que ya existe** (vuelta 98, «Editar la cuenta»): su nombre, su tipo, el
     * banco que nombra su nombre (si es uno del catálogo) y el [saldoDeHoy] que se le pasa. Con el
     * banco puesto, cambiarlo propone el nombre nuevo solo si el de hoy es el que Movi habría propuesto
     * («Bancolombia Ahorros» → «Davibank Ahorros»); uno escrito a mano no se toca.
     */
    fun cargar(cuenta: Account, saldoDeHoy: Long?) {
        banco = marcaDeLaCuenta(cuenta)?.takeIf { m -> BANCOS_CONOCIDOS.any { it.id == m.id } }?.nombre
        tipo = cuenta.type
        nombre = cuenta.name
        saldo = saldoDeHoy
    }

    fun limpiar() {
        banco = null
        tipo = AccountType.SAVINGS
        nombre = ""
        saldo = null
    }
}

@Composable
fun rememberEstadoDelFormularioDeCuenta(): EstadoDelFormularioDeCuenta = remember { EstadoDelFormularioDeCuenta() }

/** Los bancos de [BANCOS_DE_UN_TOQUE] que se ven de entrada, con su marca de `MarcasConocidas`. */
private val MARCAS_DE_UN_TOQUE: List<Marca> =
    BANCOS_DE_UN_TOQUE.take(BANCOS_A_LA_VISTA).mapNotNull { id -> BANCOS_CONOCIDOS.firstOrNull { it.id == id } }

/**
 * **¿Ofrecer «Usar «lo escrito»»?** Siempre que haya algo escrito y ningún banco del catálogo se llame
 * exactamente así (sin tildes ni mayúsculas). Antes salía solo cuando la búsqueda no encontraba nada,
 * y «Banco Mundo Mujer» casaba «banco» con medio catálogo: no había cómo escribirlo. Pura.
 */
fun ofrecerLoEscrito(escrito: String, lista: List<Marca>): Boolean {
    val q = normalizarParaBuscar(escrito)
    return q.isNotEmpty() && lista.none { normalizarParaBuscar(it.nombre) == q }
}

/**
 * **Crear una cuenta se ve igual en todos lados** (vuelta de diseño 16). Antes la guía de primer uso
 * ofrecía bancos con su marca y «Ahorros · Corriente · Efectivo · Inversión», y Patrimonio → «Nueva
 * cuenta» ofrecía «Dinero / Inversión / Bien» en tarjetas grandes, sin bancos. Ahora los dos pintan
 * este bloque:
 *
 * 1. **Banco**: píldoras con el avatar de su marca ([SelectorDeBanco]).
 * 2. **Tipo**: [TIPOS_DE_CUENTA] en píldoras; con [onElegirBien], también «Bien», que nunca queda
 *    elegido —tocarlo cambia de hoja, no de tipo— y le pasa el nombre escrito a quien llama.
 * 3. **Nombre**, propuesto solo al elegir el banco («Bancolombia Ahorros»).
 * 4. **Saldo de hoy** con [CampoDeMonto].
 *
 * No crea nada: quien lo pinta crea [EstadoDelFormularioDeCuenta.cuentaNueva] con
 * `crearCuentaConSaldo`, la creación de siempre.
 *
 * Vuelta 88: los bancos y los tipos **siempre bajan en renglones** dentro de la tarjeta. La guía los
 * llevaba en una fila que se desplazaba de lado (vuelta 54, `enUnaFila`) y en el teléfono del papá del
 * dueño, con la letra grande, se veían píldoras cortadas por los dos lados y solo dos bancos. Los que
 * no están de un toque salen de «Ver todos los bancos» ([SelectorDeBanco]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormularioDeCuenta(
    estado: EstadoDelFormularioDeCuenta,
    modifier: Modifier = Modifier,
    onElegirBien: ((nombre: String) -> Unit)? = null,
    habilitado: Boolean = true,
    /**
     * Vuelta 98 · **el tipo no se puede cambiar** (una deuda en «Editar la cuenta»): en vez de las
     * píldoras va su tipo con este porqué. `null` = las píldoras de siempre.
     */
    tipoFijo: String? = null,
    /** Vuelta 98: sin «Saldo de hoy» (una deuda: su saldo se cuadra en su ficha). */
    conSaldo: Boolean = true,
    /** Vuelta 98: la línea debajo del nombre (lo que Movi deja de reconocer si se renombra). */
    ayudaDelNombre: String? = null,
    /** Vuelta 98: la línea debajo del saldo («Movi anota un ajuste de +$X para que cuadre»). */
    ayudaDelSaldo: String? = null,
    /** El símbolo del saldo: «$» en pesos, la moneda si no. */
    monedaDelSaldo: String = "$",
) {
    Column(modifier = modifier.fillMaxWidth().testTag(TAG_FORMULARIO_DE_CUENTA)) {
        RotuloDeCampo("Banco")
        SelectorDeBanco(elegido = estado.banco, onElegir = { if (habilitado) estado.elegirBanco(it) })
        Spacer(Modifier.height(Medida.dentroDeSeccion))

        RotuloDeCampo("Tipo")
        if (tipoFijo != null) {
            Text(tipoFijo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.testTag(TAG_TIPO_FIJO))
        } else ChipsEnFilas(Modifier.testTag(TAG_TIPOS_DE_CUENTA)) {
            TIPOS_DE_CUENTA.forEach { (rotulo, tipo) ->
                PildoraDeFiltro(rotulo, seleccionada = estado.tipo == tipo, onClick = { if (habilitado) estado.elegirTipo(tipo) })
            }
            if (onElegirBien != null) {
                PildoraDeFiltro(
                    ROTULO_DEL_TIPO_BIEN,
                    seleccionada = false,
                    onClick = { if (habilitado) onElegirBien(estado.nombreParaUnBien()) },
                    icono = Icons.Rounded.Home,
                    modifier = Modifier.testTag(TAG_TIPO_BIEN),
                )
            }
        }
        Spacer(Modifier.height(Medida.dentroDeSeccion))

        Campo(
            estado.nombre,
            // Vuelta 98: el largo de la columna, como en «Para qué es esta plata»: un campo que acepta
            // letras que después se rechazan es peor que uno que deja de aceptarlas.
            { estado.nombre = it.take(MAX_ACCOUNT_NAME_LENGTH) },
            rotulo = "Nombre",
            guia = "Ej: Bancolombia Ahorros",
            ayuda = ayudaDelNombre,
            habilitado = habilitado,
        )

        if (conSaldo) {
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            CampoDeMonto(
                estado.saldo,
                { estado.saldo = it },
                rotulo = "Saldo de hoy",
                ayuda = ayudaDelSaldo,
                habilitado = habilitado,
                moneda = monedaDelSaldo,
            )
        }
    }
}

/**
 * Los bancos de un toque, cada uno con **el avatar de su marca** (vuelta de diseño 3): el mismo
 * círculo de color con la marca corta que la app pinta en las filas y las tarjetas de cuenta
 * ([CirculoDeMarca]), a [TamanoDeAvatar.Linea] dentro de la píldora. Lo usan este bloque y las
 * tarjetas de crédito de la guía.
 *
 * Vuelta 88 · **«Otro banco»** (desde la vuelta 98, «Ver todos los bancos»): la última píldora abre, ahí mismo, una búsqueda con **todos** los bancos
 * y billeteras del catálogo ([bancosParaElegir]: «bogota» encuentra «Banco de Bogotá»), en píldoras que
 * bajan en renglones. El elegido de esa lista queda a la vista, elegido, antes de esa píldora. Si nada
 * coincide con lo escrito, «Usar «lo que escribiste»» lo toma tal cual (en una cuenta también se puede escribir
 * el nombre a mano, como siempre). [paraTarjetas] suma la AMEX a la lista.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectorDeBanco(elegido: String?, onElegir: (String) -> Unit, paraTarjetas: Boolean = false) {
    var abierto by remember { mutableStateOf(false) }
    var consulta by remember { mutableStateOf("") }
    fun elegir(nombre: String) {
        onElegir(nombre)
        abierto = false
        consulta = ""
    }
    val deOtraLista = elegido?.takeIf { e -> MARCAS_DE_UN_TOQUE.none { it.nombre == e } }
    ChipsEnFilas(Modifier.testTag(TAG_BANCOS_DE_UN_TOQUE)) {
        MARCAS_DE_UN_TOQUE.forEach { marca ->
            PildoraDeFiltro(
                marca.nombre,
                seleccionada = marca.nombre == elegido,
                onClick = { onElegir(marca.nombre) },
                inicio = { CirculoDeMarca(marca, tamano = TamanoDeAvatar.Linea) },
            )
        }
        if (deOtraLista != null) {
            val marca = BANCOS_CONOCIDOS.firstOrNull { it.nombre == deOtraLista }
            PildoraDeFiltro(
                deOtraLista,
                seleccionada = true,
                onClick = { abierto = true },
                inicio = marca?.let { m -> { CirculoDeMarca(m, tamano = TamanoDeAvatar.Linea) } },
            )
        }
        PildoraDeFiltro(
            OTRO_BANCO,
            seleccionada = false,
            onClick = { abierto = !abierto },
            icono = Icons.Rounded.Search,
            modifier = Modifier.testTag(TAG_OTRO_BANCO),
        )
    }
    if (abierto) {
        Spacer(Modifier.height(Espacio.xs))
        CampoDeBusqueda(
            consulta,
            { consulta = it },
            guia = "Busca tu banco",
            nivel = NivelDeSuperficie.Capa2,
            tag = TAG_BUSCAR_BANCO,
        )
        Spacer(Modifier.height(Espacio.xs))
        val lista = bancosParaElegir(consulta, paraTarjetas)
        val escrito = consulta.trim()
        ChipsEnFilas(Modifier.testTag(TAG_LISTA_DE_BANCOS)) {
            lista.forEach { marca ->
                PildoraDeFiltro(
                    marca.nombre,
                    seleccionada = marca.nombre == elegido,
                    onClick = { elegir(marca.nombre) },
                    inicio = { CirculoDeMarca(marca, tamano = TamanoDeAvatar.Linea) },
                )
            }
        }
        // Fuera de la lista: no es un banco del catálogo, es lo que escribiste (vuelta 98).
        if (ofrecerLoEscrito(escrito, lista)) {
            Spacer(Modifier.height(Espacio.xs))
            PildoraDeFiltro(
                "Usar «$escrito»",
                seleccionada = false,
                onClick = { elegir(escrito) },
                icono = Icons.Rounded.Add,
                modifier = Modifier.testTag(TAG_USAR_LO_ESCRITO),
            )
        }
    }
}

/**
 * Lo que dice la píldora que abre la lista de todos los bancos. Vuelta 98: era «Otro banco»; con ocho a
 * la vista y el resto adentro, lo que abre es el catálogo entero, y así se dice.
 */
const val OTRO_BANCO: String = "Ver todos los bancos"

/** El tag del tipo que no se puede cambiar (vuelta 98). */
const val TAG_TIPO_FIJO: String = "formulario-de-cuenta-tipo-fijo"

/** El tag de la fila de bancos del bloque. */
const val TAG_BANCOS_DE_UN_TOQUE: String = "formulario-de-cuenta-bancos"

/** El tag de «Otro banco». */
const val TAG_OTRO_BANCO: String = "formulario-de-cuenta-otro-banco"

/** El tag de la búsqueda de «Otro banco». */
const val TAG_BUSCAR_BANCO: String = "formulario-de-cuenta-buscar-banco"

/** El tag de la lista de «Otro banco». */
const val TAG_LISTA_DE_BANCOS: String = "formulario-de-cuenta-lista-de-bancos"

/** El tag de «Usar «lo escrito»» cuando el banco no está en la lista. */
const val TAG_USAR_LO_ESCRITO: String = "formulario-de-cuenta-usar-lo-escrito"

/** El tag de la fila de tipos del bloque. */
const val TAG_TIPOS_DE_CUENTA: String = "formulario-de-cuenta-tipos"

/**
 * Chips que **bajan en varios renglones** dentro del ancho que tienen: nunca se salen de la tarjeta
 * (vuelta 88; antes la guía los llevaba en una fila que se desplazaba de lado).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsEnFilas(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) { contenido() }
}
