package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate

/*
 * # «Cómo bajar tu extracto», banco por banco (vuelta 107, ampliada en la vuelta 130)
 *
 * El 2-nov se cruzaron a mano los movimientos de la cuenta Bancolombia del dueño contra Movi y lo que
 * faltaba era lo que ningún banco avisa (el 4x1000, las comisiones). Para eso está el extracto; lo que
 * no estaba era **cómo sacarlo**. La vuelta 107 escribió la guía de Bancolombia y una genérica de Nu.
 * La vuelta 130 (pedido del dueño: «cada banco es diferente») la vuelve un **catálogo por banco y
 * producto** ([GUIAS_DEL_EXTRACTO]), con lo que cada banco publica y lo que Movi lee mejor. Esa semana
 * Caro sufrió con su extracto de ahorros de Bancolombia (lo leía la IA y se cortaba) y con el de la
 * tarjeta Nu (no sabía qué cuenta elegir).
 *
 * ## De dónde sale cada dato (y qué NO se escribe)
 *
 * La fuente es la investigación del 4-nov-2026 (`docs/investigacion/`, fuera del repo hasta que el dueño
 * la suba): cada dato con su página. Reglas:
 *
 * - **Nunca se inventan pasos.** Un banco donde la investigación dice «no encontré» los pasos lleva los
 *   genéricos ([PASOS_GENERICOS]), y lo vigila `GuiaDelExtractoTest`.
 * - **Lo de fuente secundaria (prensa, Rankia, blogs) no se muestra.** Queda acá, en un comentario
 *   `[secundaria]` junto a la guía, para revisarlo cuando haya una fuente oficial.
 * - **La clave del PDF solo si la fuente la confirma** (Bancolombia, Lulo). Las demás no la dicen.
 * - Lo que el banco publica en su ayuda pero nadie probó en la app lleva [FuenteDeLaGuia.AYUDA_DEL_BANCO]
 *   y la hoja dice que los pasos pueden cambiar. Solo los «Movimientos» de Bancolombia se siguieron en el
 *   portal (2-nov-2026, vuelta 107): [FuenteDeLaGuia.PROBADA_EN_EL_BANCO].
 *
 * ## Lo que Movi lee al instante (sin IA), verificado en el server
 *
 * - Bancolombia: los «Movimientos» de una cuenta, una tarjeta o un fondo en PDF
 *   (`MovimientosDeBancolombia.kt`), los de una cuenta en Excel (`ExcelDeBancolombia.kt`, detector
 *   estricto: aún sin un Excel real) y el extracto de un crédito (`ExtractoDeCreditoBancolombia.kt`).
 * - Nu: el extracto de la tarjeta en PDF (`ExtractoDeNu.kt`). La Cuenta Nu va con IA.
 * - Skandia (fondo voluntario) y Davibank (cuenta de ahorros): el PDF, del que toma el saldo
 *   (`ExtractoDeSkandia.kt`, `ExtractoAfcDavibank.kt`).
 * - Todo lo demás —el extracto mensual de cualquier banco, una foto o captura— va con IA.
 *
 * Los bancos se nombran por su id de `MarcasConocidas` ([Marca.id]), con el mismo nombre que el resto
 * de la app; [ALIAS_DE_LA_GUIA] manda la AMEX a Bancolombia y Scotiabank Colpatria a Davibank.
 */

/** El producto del banco del que es un extracto. [rotulo] es lo que dice la hoja. */
enum class ProductoDelExtracto(val rotulo: String) {
    CUENTA("Cuenta"),
    TARJETA("Tarjeta de crédito"),
    CREDITO("Crédito"),
    FONDO("Fondo"),
}

/**
 * Cómo lo lee Movi: con un lector propio, al instante; o con IA (puede tardar). Revisión de la vuelta 130:
 * [SOLO_EL_SALDO] para los extractos de los que Movi solo toma el saldo (Skandia, Davibank), sin prometer
 * movimientos; [SOLO_ALGUNOS_CREDITOS] para el crédito de Bancolombia (solo la libre inversión y la
 * libranza tienen lector); [NO_SE_IMPORTA] para un crédito que Movi todavía no importa.
 */
enum class LecturaDelExtracto { AL_INSTANTE, CON_IA, SOLO_EL_SALDO, SOLO_ALGUNOS_CREDITOS, NO_SE_IMPORTA }

/** De dónde salen los pasos de una guía. */
enum class FuenteDeLaGuia {
    /** Se siguieron en el banco (el portal de Bancolombia, 2-nov-2026). */
    PROBADA_EN_EL_BANCO,

    /** Los publica la ayuda del banco, pero nadie los probó en la app: pueden cambiar. */
    AYUDA_DEL_BANCO,

    /** La fuente no da los pasos: la guía lleva los genéricos ([PASOS_GENERICOS]). */
    SIN_PASOS_DEL_BANCO,
}

/**
 * **La segunda forma de sacar el extracto**, cuando la primera no es la única: en Bancolombia, el
 * extracto mensual de «Documentos → Extractos», detrás de los «Movimientos» que Movi lee al instante.
 */
data class OtraFormaDeSacarlo(
    val titulo: String,
    val pasos: List<String>,
    val queSubir: String,
    val lectura: LecturaDelExtracto,
    val limite: String? = null,
    /** La clave de ESTE archivo, si la fuente la confirma (el extracto mensual de Bancolombia). */
    val clave: String? = null,
)

/**
 * **La guía de un banco para uno o varios productos.** Todo es texto para el dueño, en español neutro:
 *
 * - [donde]: la app, la web o las dos, en una frase.
 * - [pasos]: cortos, en orden (la hoja los numera), con los nombres de menú solo si la fuente los da.
 *   Sin fuente, [PASOS_GENERICOS].
 * - [queSubir]: el archivo que Movi lee mejor. [lectura]: si lo lee al instante o con IA.
 * - [clave]: la del PDF, solo si la fuente la confirma. [porCorreo]: si el banco lo manda al correo.
 * - [siNoLoEncuentras]: una línea, siempre.
 * - [aviso]: lo que hay que saber antes de empezar (en Bancolombia, no pegar direcciones).
 * - [limite]: hasta dónde deja bajar. [conRango]: el banco deja elegir «Desde / Hasta», y la hoja
 *   propone el rango ideal ([rangoIdealDelExtracto]).
 */
data class GuiaDelExtracto(
    val bancoId: String,
    val banco: String,
    val productos: List<ProductoDelExtracto>,
    val donde: String,
    val pasos: List<String>,
    val queSubir: String,
    val lectura: LecturaDelExtracto,
    val siNoLoEncuentras: String,
    val fuente: FuenteDeLaGuia,
    val clave: String? = null,
    val porCorreo: String? = null,
    /** Si el banco lo manda por SMS (Falabella). */
    val porSms: String? = null,
    val aviso: String? = null,
    val limite: String? = null,
    val conRango: Boolean = false,
    val otraForma: OtraFormaDeSacarlo? = null,
) {
    /** El producto con que se nombra la guía: el primero. */
    val producto: ProductoDelExtracto get() = productos.first()

    /** ¿Sirve para [p]? */
    fun esDe(p: ProductoDelExtracto): Boolean = p in productos

    /** ¿Lleva los pasos genéricos (la fuente no dio los del banco)? */
    val conPasosGenericos: Boolean get() = pasos == PASOS_GENERICOS
}

// ── Lo genérico: lo que se dice de un banco sin pasos confirmados ─────────────────────────────────

/** Los pasos de un banco cuya fuente no los da. Nunca se escriben otros sin fuente. */
val PASOS_GENERICOS: List<String> = listOf(
    "Búscalo en la app o en la página de tu banco, en «Extractos» o «Documentos».",
    "Bájalo y súbelo a Movi.",
)

const val DONDE_GENERICO: String = "En la app o en la página de tu banco."
const val QUE_SUBIR_GENERICO: String = "El extracto en PDF. Si no lo encuentras, una captura de tus movimientos."
const val SI_NO_LO_ENCUENTRAS_GENERICO: String =
    "Si no aparece, sube una captura de tus movimientos: Movi la lee con IA."

/** El nombre que se le da a un banco que no está en el catálogo. */
const val TU_BANCO: String = "tu banco"

/**
 * **La guía genérica**: para un banco del que no hay nada confirmado, o para un producto que su guía no
 * cubre (la tarjeta de BBVA). Con el nombre del banco si se sabe ([banco]); si no, «tu banco».
 */
fun guiaGenerica(bancoId: String?, banco: String?, producto: ProductoDelExtracto?): GuiaDelExtracto =
    if (producto == ProductoDelExtracto.CREDITO) creditoQueNoSeImporta(bancoId, banco) else GuiaDelExtracto(
    bancoId = bancoId ?: "",
    banco = banco ?: TU_BANCO,
    productos = listOf(producto ?: ProductoDelExtracto.CUENTA),
    donde = DONDE_GENERICO,
    pasos = PASOS_GENERICOS,
    queSubir = QUE_SUBIR_GENERICO,
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.SIN_PASOS_DEL_BANCO,
)

/**
 * Revisión de la vuelta 130 · **el extracto de un crédito que Movi no lee**. El de un crédito no trae
 * movimientos, y la lectura con IA lo descarta («un crédito sin lista de movimientos es NADA»): solo la
 * libre inversión y la libranza de Bancolombia tienen lector. Así que no se promete nada ni se ofrece
 * «Subir el extracto»: se dice que todavía no se importa y qué hacer.
 */
const val CREDITO_QUE_NO_SE_IMPORTA: String =
    "Movi todavía no importa el extracto de este crédito. Puedes cuadrar su saldo a mano desde el crédito."

private fun creditoQueNoSeImporta(bancoId: String?, banco: String?) = GuiaDelExtracto(
    bancoId = bancoId ?: "",
    banco = banco ?: TU_BANCO,
    productos = listOf(ProductoDelExtracto.CREDITO),
    donde = DONDE_GENERICO,
    pasos = PASOS_GENERICOS,
    queSubir = CREDITO_QUE_NO_SE_IMPORTA,
    lectura = LecturaDelExtracto.NO_SE_IMPORTA,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.SIN_PASOS_DEL_BANCO,
)

// ── El catálogo ──────────────────────────────────────────────────────────────────────────────────

private const val CLAVE_ES_LA_CEDULA: String = "Si el PDF pide clave, es tu número de documento (tu cédula)."

/** Bancolombia: el extracto mensual, como segunda opción de la cuenta y de la tarjeta. */
private val EXTRACTO_MENSUAL_DE_BANCOLOMBIA = OtraFormaDeSacarlo(
    titulo = "También sirve el extracto mensual",
    // [oficial] bancolombia.com/centro-de-ayuda/…/descargar-extractos-bancolombia-sucursal-virtual (16-jul-2026).
    pasos = listOf(
        "En la Sucursal Virtual, abre el menú de arriba a la izquierda → «Documentos» → «Extractos».",
        "Escribe la clave dinámica o el código que te llega por SMS.",
        "En el producto, toca los tres puntos y elige «Descargar en PDF» o «Descargar en XLS».",
    ),
    queSubir = "El extracto en PDF o XLS. Movi lo lee con IA y puede tardar un poco.",
    lectura = LecturaDelExtracto.CON_IA,
    limite = "Deja bajar extractos de hasta 2 años atrás.",
    // [oficial] la misma página: «si el archivo pide una clave, es tu número de documento de identidad».
    // Es la de esta descarga; de los «Movimientos» la fuente no dice nada.
    clave = CLAVE_ES_LA_CEDULA,
)

/** La línea de Bancolombia cuando no aparece: la página pública de la web (oficial, sin fecha visible). */
private const val SI_NO_LO_ENCUENTRAS_EN_BANCOLOMBIA: String =
    "Pídelo en la página de Bancolombia: «Trámites digitales» → «Solicitud de documentos» → «Extracto bancario»."

/** Ver la memoria del proyecto «portal de Bancolombia»: entrar por una dirección pegada te expulsa al login. */
private const val AVISO_DEL_PORTAL_DE_BANCOLOMBIA: String =
    "En la Sucursal Virtual llega por los menús: si pegas una dirección en el navegador, te saca a la entrada."

private const val BANCOLOMBIA_POR_CORREO: String =
    "No llega solo al correo. Si lo pides por la Sucursal Telefónica, te lo mandan al correo registrado."

/**
 * **Bancolombia, cuenta de ahorros o corriente: la recomendación de oro.** Los «Movimientos» en PDF o
 * Excel, que Movi lee al instante, se siguieron en el portal el 2-nov-2026 (vuelta 107). La app solo
 * muestra los movimientos del mes y del anterior ([oficial] …/consulta-movimientos-app): por eso, la web.
 * [secundaria] Rankia describe en la app «Cuentas → Consulta de Extractos»: sin verificar, no se muestra.
 */
val GUIA_DE_BANCOLOMBIA: GuiaDelExtracto = GuiaDelExtracto(
    bancoId = "bancolombia",
    banco = "Bancolombia",
    productos = listOf(ProductoDelExtracto.CUENTA),
    donde = "En la Sucursal Virtual Personas, la página web. La app solo muestra los movimientos de los últimos 2 meses.",
    pasos = listOf(
        "Entra a la Sucursal Virtual Personas.",
        "En Inicio, toca la cuenta.",
        "Abre la pestaña «Movimientos» y toca «Descargar».",
        "Elige PDF y las fechas «Desde» y «Hasta».",
        "Toca «Descargar» y sube el archivo a Movi.",
    ),
    // El PDF primero: es el que se probó con los papeles reales. El Excel tiene lector (vuelta 107) pero
    // nadie ha subido uno real: va de segundo y sin prometer «al instante» (ver [queSubirDe]).
    queSubir = "Los «Movimientos» en PDF: Movi los lee al instante. El Excel también sirve, pero Movi todavía no lo ha probado con uno real.",
    lectura = LecturaDelExtracto.AL_INSTANTE,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_EN_BANCOLOMBIA,
    fuente = FuenteDeLaGuia.PROBADA_EN_EL_BANCO,
    porCorreo = BANCOLOMBIA_POR_CORREO,
    aviso = AVISO_DEL_PORTAL_DE_BANCOLOMBIA,
    limite = "Los «Movimientos» se bajan de este mes y de los últimos 3 meses.",
    conRango = true,
    otraForma = EXTRACTO_MENSUAL_DE_BANCOLOMBIA,
)

/**
 * Bancolombia, tarjeta de crédito (Mastercard, Visa, AMEX: la fuente no documenta diferencias). Los
 * «Movimientos» de la tarjeta en PDF se leen al instante (vuelta 107, probados en el portal); el Excel de
 * la tarjeta no tiene lector propio, por eso no se recomienda.
 */
val GUIA_DE_BANCOLOMBIA_TARJETA: GuiaDelExtracto = GUIA_DE_BANCOLOMBIA.copy(
    productos = listOf(ProductoDelExtracto.TARJETA),
    pasos = listOf(
        "Entra a la Sucursal Virtual Personas.",
        "Ve a «Tarjetas de crédito» y toca la tarjeta.",
        "Abre «Movimientos» y toca «Descargar».",
        "Elige PDF y las fechas «Desde» y «Hasta».",
        "Toca «Descargar» y sube el archivo a Movi.",
    ),
    queSubir = "Los «Movimientos» de la tarjeta en PDF: Movi los lee al instante.",
)

/**
 * Bancolombia, crédito: la pantalla de Extractos cubre el de consumo y el hipotecario ([oficial], la misma
 * página del 16-jul-2026), pero Movi **solo lee sin IA la libre inversión y la libranza**
 * (`ExtractoDeCreditoBancolombia.kt` detrás de `detectDocumentType` = LOAN_SUMMARY, que pide «LÍNEA DE
 * CRÉDITO» / «CREDIÁGIL» / «CREDITO PREAPROBADO» y «ABONO A CAPITAL»): un hipotecario o un vehículo va a la
 * IA, que lo descarta. La guía lo dice así. Los pasos son los de la página, no se probaron en el portal.
 */
val GUIA_DE_BANCOLOMBIA_CREDITO: GuiaDelExtracto = GUIA_DE_BANCOLOMBIA.copy(
    productos = listOf(ProductoDelExtracto.CREDITO),
    donde = "En la Sucursal Virtual Personas, la página web.",
    pasos = EXTRACTO_MENSUAL_DE_BANCOLOMBIA.pasos.take(2) +
        "En el crédito, toca los tres puntos y elige «Descargar en PDF».",
    queSubir = "Libre inversión o libranza: Movi lo lee al instante (la cuota, la tasa y el saldo). Otros créditos: todavía no se importan.",
    lectura = LecturaDelExtracto.SOLO_ALGUNOS_CREDITOS,
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    clave = CLAVE_ES_LA_CEDULA,
    aviso = null,
    limite = "Deja bajar extractos de hasta 2 años atrás.",
    conRango = false,
    otraForma = null,
)

/**
 * Revisión de la vuelta 130 · **la Fiducuenta** (el fondo de inversión de Bancolombia): «Movimientos:
 * Inversiones» se lee sin IA (`MovimientosDeBancolombia.kt`, probado con el real del 1-sep al 26-oct).
 * Cómo se baja en el portal no se probó: pasos genéricos. El extracto mensual del fondo no sirve (el
 * server lo rechaza como «estado de fondo de inversión»).
 */
val GUIA_DE_BANCOLOMBIA_FONDO: GuiaDelExtracto = GuiaDelExtracto(
    bancoId = "bancolombia",
    banco = "Bancolombia",
    productos = listOf(ProductoDelExtracto.FONDO),
    donde = "En la Sucursal Virtual Personas, la página web: en el fondo, busca sus «Movimientos».",
    pasos = PASOS_GENERICOS,
    queSubir = "Los «Movimientos» del fondo en PDF: Movi los lee al instante. El extracto mensual del fondo no trae movimientos que se puedan importar.",
    lectura = LecturaDelExtracto.AL_INSTANTE,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.SIN_PASOS_DEL_BANCO,
)

/**
 * Nu, tarjeta de crédito: [oficial] blog.nu.com.co/organizate-con-el-extracto-de-tu-tarjeta-nu
 * (30-may-2024). El blog dice cómo VERLO en la app; no habla de bajarlo en PDF ni de clave ([no
 * encontré]). Movi lee al instante el PDF del extracto (`ExtractoDeNu.kt`, con los papeles reales).
 */
val GUIA_DE_NU: GuiaDelExtracto = GuiaDelExtracto(
    bancoId = "nu",
    banco = "Nu",
    productos = listOf(ProductoDelExtracto.TARJETA),
    donde = "Solo en la app de Nu.",
    pasos = listOf(
        "Abre «Lo que debes de tu tarjeta».",
        "Toca «Ver extractos», abajo a la derecha.",
        "Elige el mes.",
    ),
    queSubir = "El extracto de la tarjeta en PDF: Movi lo lee al instante y cuadra la deuda.",
    lectura = LecturaDelExtracto.AL_INSTANTE,
    siNoLoEncuentras = "Si la app no te deja bajarlo en PDF, sube capturas del extracto: Movi las lee con IA.",
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
)

/** Nu, Cuenta Nu: [oficial] blog.nu.com.co/consulta-tus-extractos (23-feb-2024). Clave: [no encontré]. */
val GUIA_DE_NU_CUENTA: GuiaDelExtracto = GuiaDelExtracto(
    bancoId = "nu",
    banco = "Nu",
    productos = listOf(ProductoDelExtracto.CUENTA),
    donde = "Solo en la app de Nu. El extracto de la cuenta llega a tu correo.",
    pasos = listOf(
        "Abre «Cuenta de ahorros».",
        "Toca «Pedir extracto» y elige el mes.",
        "Toca «Enviar extracto a mi correo»: llega en PDF en pocos minutos.",
        "Sube a Movi el PDF del correo.",
    ),
    queSubir = "El PDF que llega a tu correo. Movi lo lee con IA y puede tardar un poco.",
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = "Si no llega en unos minutos, revisa el correo no deseado o sube una captura de tus movimientos.",
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    porCorreo = "Sí: es la única forma de recibir el extracto de la cuenta.",
)

/**
 * Davivienda: [oficial] davivienda.com/personas/app-davivienda/extractos-y-certificaciones (sin fecha).
 * Da dónde, hasta cuándo y cuándo sale; **no da los pasos** → genéricos.
 * [secundaria] Rankia (18-jul-2026): davivienda.com → Ingreso Clientes → Cuentas → Consulta de Extractos.
 * [secundaria] Rankia: PDF con descarga o envío por correo. Clave: [no encontré].
 */
private val GUIA_DE_DAVIVIENDA = GuiaDelExtracto(
    bancoId = "davivienda",
    banco = "Davivienda",
    productos = listOf(ProductoDelExtracto.CUENTA, ProductoDelExtracto.TARJETA),
    donde = "En la app Davivienda o en davivienda.com. En la página, además de la clave virtual, te pide un código que llega al celular.",
    pasos = PASOS_GENERICOS,
    queSubir = QUE_SUBIR_GENERICO,
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.SIN_PASOS_DEL_BANCO,
    // [oficial, resumen del buscador] la modalidad por correo se activa por el Call Center.
    porCorreo = "Puedes recibirlo por correo: se pide en la línea de atención.",
    limite = "Hay extractos de hasta 18 meses atrás. Cada uno sale el día 6, con el mes anterior.",
)

/**
 * BBVA: [oficial, visto solo en el resumen del buscador] bbva.com.co/…/extracto-bancario.html (la página dio
 * 403). La app manda el extracto de los últimos 6 meses al correo; más viejo, BBVA net. La clave del
 * extracto: [no encontré] (la cédula está documentada solo para los certificados).
 * [secundaria] Rankia: BBVA net → Consultas → «Descargar extracto mensual». Tecnobits, la tarjeta: sin verificar.
 * Solo la cuenta: para la tarjeta no hay fuente oficial y va la genérica.
 */
private val GUIA_DE_BBVA = GuiaDelExtracto(
    bancoId = "bbva",
    banco = "BBVA",
    productos = listOf(ProductoDelExtracto.CUENTA),
    donde = "En la app BBVA (los últimos 6 meses) o en BBVA net, la página web (los más viejos).",
    pasos = listOf(
        "En la app BBVA, pide el extracto: llega a tu correo registrado.",
        "Si es de hace más de 6 meses, bájalo en BBVA net.",
    ),
    queSubir = "El extracto en PDF. Movi lo lee con IA y puede tardar un poco.",
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    porCorreo = "Sí: el que pides en la app llega al correo, y para abrirlo te pide una clave.",
)

/**
 * Banco de Bogotá: [oficial, visto en el resumen del buscador] la página del extracto de la tarjeta en la
 * Banca Móvil (403) y las preguntas frecuentes de la Banca Móvil (el listado trimestral).
 * [secundaria] Rankia: Zona Transaccional → Consultas → Extractos. Clave y correo: [no encontré].
 */
private val GUIA_DE_BANCO_DE_BOGOTA = GuiaDelExtracto(
    bancoId = "banco_de_bogota",
    banco = "Banco de Bogotá",
    productos = listOf(ProductoDelExtracto.TARJETA, ProductoDelExtracto.CUENTA),
    donde = "En la Banca Móvil, la app del banco.",
    pasos = listOf(
        "Entra a la Banca Móvil con tu número de documento y tu clave segura.",
        "Abre «Documentos» → «Extractos».",
        "Elige el producto, el año y el mes, y toca «Generar extracto».",
        "Compártelo a Movi o súbelo desde el teléfono.",
    ),
    queSubir = "El extracto en PDF. Movi lo lee con IA y puede tardar un poco.",
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    limite = "En la app, los extractos se listan por trimestre.",
)

/**
 * Nequi: [oficial, visto en el resumen del buscador] ayuda.nequi.com.co/…/Cómo-descargar-extractos-de-Nequi
 * (403), con los pasos completos de [secundaria] Infobae (15-jul-2026), que coinciden. La clave del
 * extracto: [no encontré] (la cédula es de los certificados).
 */
private val GUIA_DE_NEQUI = GuiaDelExtracto(
    bancoId = "nequi",
    banco = "Nequi",
    productos = listOf(ProductoDelExtracto.CUENTA),
    donde = "En la app de Nequi o en su página web.",
    // Revisión de la vuelta 130: el paso a paso completo (Certificados → «Ir a web de Nequi» → Extractos →
    // «Generar») sale de [secundaria] Infobae; de la ayuda oficial solo se vio el resumen. Va lo que no
    // depende de Infobae: desde el perfil, «Documentos y certificados».
    pasos = listOf(
        "En la app, entra desde tu perfil a «Documentos y certificados».",
        PASOS_GENERICOS.last(),
    ),
    queSubir = "El extracto en PDF. Movi lo lee con IA y puede tardar un poco.",
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    limite = "Cada extracto está listo 3 días después de que cierra el mes.",
)

/**
 * Daviplata: [oficial, visto solo en el resumen del buscador] la ayuda de Daviplata (no se pudo abrir).
 * Formato, clave y meses atrás: [no encontré].
 */
private val GUIA_DE_DAVIPLATA = GuiaDelExtracto(
    bancoId = "daviplata",
    banco = "Daviplata",
    productos = listOf(ProductoDelExtracto.CUENTA),
    donde = "En la app de Daviplata.",
    pasos = listOf(
        "Entra a la app con tu tipo y número de documento y tu clave.",
        "Abre el menú (las tres rayas) → «Más Servicios».",
        "Entra a «Ver Movimientos» o «Extractos»: ahí lo ves, lo bajas o lo mandas al correo.",
    ),
    queSubir = QUE_SUBIR_GENERICO,
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    porCorreo = "Sí: desde la app o llamando al #688.",
)

/**
 * Davibank (antes Scotiabank Colpatria): [oficial] davibank.com/personas/tarjeta-de-credito/…/nuevo-extracto
 * (sin fecha): «en la app» y en la Banca Virtual de Personas, sección «Extractos y certificaciones». Los
 * pasos: [no encontré] → genéricos. Formato, clave, meses atrás, correo: [no encontré].
 * La cuenta: el extracto de ahorros de Davibank se lee sin IA y Movi toma el saldo (`ExtractoAfcDavibank.kt`,
 * con la AFC real del dueño); los movimientos no.
 */
private val GUIA_DE_DAVIBANK = GuiaDelExtracto(
    bancoId = "davibank",
    banco = "Davibank",
    productos = listOf(ProductoDelExtracto.CUENTA),
    donde = "En la app de Davibank o en la Banca Virtual de Personas, en «Extractos y certificaciones».",
    pasos = PASOS_GENERICOS,
    queSubir = "El extracto de la cuenta en PDF: Movi toma el saldo al instante (los movimientos no).",
    lectura = LecturaDelExtracto.SOLO_EL_SALDO,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.SIN_PASOS_DEL_BANCO,
)

private val GUIA_DE_DAVIBANK_TARJETA = GUIA_DE_DAVIBANK.copy(
    productos = listOf(ProductoDelExtracto.TARJETA),
    queSubir = QUE_SUBIR_GENERICO,
    lectura = LecturaDelExtracto.CON_IA,
)

/**
 * Banco de Occidente: [oficial, visto en el resumen del buscador] la página del Extracto Digital y su
 * instructivo en PDF (los dos dieron 403). La «contraseña de acceso» del extracto por correo no dice cuál
 * es: no se muestra. Meses atrás y formato: [no encontré].
 */
private val GUIA_DE_OCCIDENTE = GuiaDelExtracto(
    bancoId = "occidente",
    banco = "Banco de Occidente",
    productos = listOf(ProductoDelExtracto.CUENTA, ProductoDelExtracto.TARJETA),
    donde = "En la app Banco de Occidente Móvil o en el Portal Transaccional, la página web.",
    pasos = listOf(
        "En la app, abre el producto y toca «ver extractos».",
        "Elige el período.",
    ),
    queSubir = QUE_SUBIR_GENERICO,
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = "En el Portal Transaccional, elige el producto, toca el botón para generar el extracto y elige el período.",
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    porCorreo = "Si te inscribes al «Extracto Digital» en el portal, te llega al correo.",
    aviso = "En iPhone la app solo deja verlo. Para bajar el PDF usa Android o el Portal Transaccional.",
)

/**
 * AV Villas: [oficial] avvillas.com.co/tutoriales/extractos-y-certificados-en-linea (sin fecha): los pasos
 * de la Banca Virtual. Los de la app: [no encontré]. Clave, meses atrás, correo: [no encontré].
 */
private val GUIA_DE_AV_VILLAS = GuiaDelExtracto(
    bancoId = "av_villas",
    banco = "AV Villas",
    productos = listOf(ProductoDelExtracto.CUENTA, ProductoDelExtracto.TARJETA),
    donde = "En la Banca Virtual, la página web. Tienes que estar registrado.",
    pasos = listOf(
        "Entra a la Banca Virtual.",
        "Toca «Extractos y Certificados», arriba a la derecha, y luego «Extractos».",
        "Elige el producto y el período: se descarga solo.",
    ),
    queSubir = "El extracto en PDF. Movi lo lee con IA y puede tardar un poco.",
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
)

/**
 * Banco Popular: [oficial] bancopopular.com.co/…/extractos-personas (sin fecha). La clave: la página leída
 * no la dice y el resumen del buscador dice que es el documento; no confirmada → no se muestra.
 */
private val GUIA_DE_BANCO_POPULAR = GuiaDelExtracto(
    bancoId = "banco_popular",
    banco = "Banco Popular",
    productos = listOf(ProductoDelExtracto.CUENTA),
    donde = "En la Banca Móvil, la app del banco, o en la Zona Transaccional de su página.",
    pasos = listOf(
        "En la Banca Móvil, abre «Documentos» → «Extractos».",
        "Elige el producto y el mes, y toca «Descargar extracto».",
    ),
    queSubir = QUE_SUBIR_GENERICO,
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = "En la Zona Transaccional, entra con tu usuario y contraseña y abre «Documentos».",
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    porCorreo = "Para recibirlo por correo, llama a la Línea Verde y pide la entrega digital.",
)

/**
 * Banco Falabella: [oficial] bancofalabella.com.co (sin fecha): «Extractos en línea» sin iniciar sesión, y
 * la casilla del SMS. Los pasos de la app, el formato, la clave y el correo: [no encontré].
 */
private val GUIA_DE_BANCO_FALABELLA = GuiaDelExtracto(
    bancoId = "banco_falabella",
    banco = "Banco Falabella",
    productos = listOf(ProductoDelExtracto.TARJETA, ProductoDelExtracto.CUENTA),
    donde = "En la página web del Banco Falabella, sin iniciar sesión.",
    pasos = listOf(
        "En la página del banco, abre «Extractos en línea».",
        "Elige tu tipo de documento, escribe el número y toca «Descargar».",
    ),
    queSubir = QUE_SUBIR_GENERICO,
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    // Por correo: [no encontré]. Solo lo del SMS está en la página.
    porSms = "Si marcas «Deseo recibir extractos vía SMS», te llega por SMS cada mes.",
)

/**
 * Lulo Bank, Lulo Cuenta: [oficial, visto en el resumen del buscador] ayuda.lulobank.com/…/Cómo-puedo-
 * obtener-los-extractos-de-mi-Lulo-Cuenta (403). La clave del PDF = la cédula, confirmada ahí.
 */
private val GUIA_DE_LULO = GuiaDelExtracto(
    bancoId = "lulo",
    banco = "Lulo Bank",
    productos = listOf(ProductoDelExtracto.CUENTA),
    donde = "Solo en la app de Lulo Bank.",
    pasos = listOf(
        "Abre «Productos» y entra a tu cuenta.",
        "Toca la rueda y entra a «Documentos».",
    ),
    queSubir = "El extracto en PDF. Movi lo lee con IA y puede tardar un poco.",
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = "En el mes en que abriste la cuenta todavía no hay extracto. Si uno más viejo falla, escríbele al chat de la app.",
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    clave = CLAVE_ES_LA_CEDULA,
)

// Revisión de la vuelta 130: el crédito de libre inversión de Lulo no tiene guía. Su extracto no trae
// movimientos y la lectura con IA lo descarta: abre la de un crédito que todavía no se importa
// ([creditoQueNoSeImporta]). [oficial, resumen del buscador] ayuda.lulobank.com/…/4405901773716: Explora →
// Crédito → Crédito de libre inversión → rueda → Documentos; aparece 10 días antes del pago y va al correo.

/**
 * RappiPay, la RappiCard: [oficial, visto en el resumen del buscador] help.rappipay.co/…/cmo-puedo-ver-y-
 * descargar-mis-extractos (no resolvió) y rappicard.co (el primer día de cada mes). La RappiCuenta:
 * [no encontré] → genérica. [secundaria] Rankia: PDF.
 */
private val GUIA_DE_RAPPIPAY = GuiaDelExtracto(
    bancoId = "rappipay",
    banco = "RappiPay",
    productos = listOf(ProductoDelExtracto.TARJETA),
    donde = "Dentro de la app de Rappi.",
    pasos = listOf(
        "Abre la app de Rappi y entra a «RappiPay».",
        "Elige «RappiCard» y entra a la sección de extractos.",
    ),
    queSubir = QUE_SUBIR_GENERICO,
    lectura = LecturaDelExtracto.CON_IA,
    siNoLoEncuentras = SI_NO_LO_ENCUENTRAS_GENERICO,
    fuente = FuenteDeLaGuia.AYUDA_DEL_BANCO,
    limite = "El extracto está listo el primer día de cada mes.",
)

/**
 * Skandia, fondo voluntario de pensión: [oficial] skandia.co/consulta-extractos-y-certificados (sin fecha):
 * el portal de clientes y su enlace «Certificados»; **no da los pasos** del extracto del fondo. El envío
 * mensual por correo, del instructivo oficial (en imágenes). Clave: [no encontré]. Movi lee el PDF sin IA
 * y toma el saldo (`ExtractoDeSkandia.kt`).
 */
private val GUIA_DE_SKANDIA = GuiaDelExtracto(
    bancoId = "skandia",
    banco = "Skandia",
    productos = listOf(ProductoDelExtracto.FONDO),
    donde = "En el portal de clientes de Skandia, la página web, con tu clave de siempre. Los extractos están en «Certificados».",
    pasos = PASOS_GENERICOS,
    queSubir = "El extracto del fondo voluntario en PDF: Movi toma el saldo al instante (los movimientos no).",
    lectura = LecturaDelExtracto.SOLO_EL_SALDO,
    siNoLoEncuentras = "Búscalo en tu correo: el fondo voluntario manda un extracto cada mes.",
    fuente = FuenteDeLaGuia.SIN_PASOS_DEL_BANCO,
    porCorreo = "Sí: el fondo voluntario te manda el extracto cada mes.",
)

/**
 * **El catálogo**, en el orden en que se ofrece cada banco: por cada banco, sus productos en el orden de la
 * hoja. Un banco que no está acá tiene la guía genérica ([guiaGenerica]).
 */
val GUIAS_DEL_EXTRACTO: List<GuiaDelExtracto> = listOf(
    GUIA_DE_BANCOLOMBIA, GUIA_DE_BANCOLOMBIA_TARJETA, GUIA_DE_BANCOLOMBIA_CREDITO, GUIA_DE_BANCOLOMBIA_FONDO,
    GUIA_DE_NU, GUIA_DE_NU_CUENTA,
    GUIA_DE_DAVIVIENDA,
    GUIA_DE_BBVA,
    GUIA_DE_BANCO_DE_BOGOTA,
    GUIA_DE_NEQUI,
    GUIA_DE_DAVIPLATA,
    GUIA_DE_DAVIBANK, GUIA_DE_DAVIBANK_TARJETA,
    GUIA_DE_OCCIDENTE,
    GUIA_DE_AV_VILLAS,
    GUIA_DE_BANCO_POPULAR,
    GUIA_DE_BANCO_FALABELLA,
    GUIA_DE_LULO,
    GUIA_DE_RAPPIPAY,
    GUIA_DE_SKANDIA,
)

/**
 * **Las marcas que usan la guía de otro banco**: la AMEX de Colombia la emite Bancolombia (vuelta 129,
 * `grupoDelBanco`), y Scotiabank Colpatria hoy es Davibank (la investigación, §8). No se usa
 * `grupoDelBanco` entero: ahí Davibank es de Davivienda, pero su app y su extracto son otros.
 */
val ALIAS_DE_LA_GUIA: Map<String, String> =
    mapOf("amex" to "bancolombia", "fiducuenta" to "bancolombia", "scotiabank" to "davibank")

/** La marca que ya dice el producto: la AMEX es una tarjeta; la Fiducuenta, un fondo. */
private val PRODUCTO_DE_LA_MARCA: Map<String, ProductoDelExtracto> =
    mapOf("amex" to ProductoDelExtracto.TARJETA, "fiducuenta" to ProductoDelExtracto.FONDO)

/** Las redes de tarjeta: nombran la tarjeta, no el banco que la emite. */
private val REDES_DE_TARJETA: Set<String> = setOf("visa", "mastercard")

/** El id del banco cuya guía usa la marca [id]. */
fun bancoDeLaGuia(id: String): String = ALIAS_DE_LA_GUIA[id] ?: id

/** Las guías de un banco (por el id de su marca), en el orden de la hoja. Vacía si no está en el catálogo. */
fun guiasDelBanco(bancoId: String): List<GuiaDelExtracto> {
    val id = bancoDeLaGuia(bancoId)
    return GUIAS_DEL_EXTRACTO.filter { it.bancoId == id }
}

/**
 * **La guía de [bancoId] para [producto]**: la del catálogo que sirve para ese producto; sin [producto],
 * la primera del banco; si el banco no tiene una para ese producto (la tarjeta de BBVA) o no está en el
 * catálogo, la genérica con el nombre del banco ([nombre], si se sabe). Nunca `null`: siempre hay algo
 * que decir. La AMEX abre la tarjeta de Bancolombia.
 */
fun guiaDelExtracto(bancoId: String?, producto: ProductoDelExtracto?, nombre: String? = null): GuiaDelExtracto {
    val delBanco = bancoId?.let(::guiasDelBanco).orEmpty()
    val productoReal = PRODUCTO_DE_LA_MARCA[bancoId] ?: producto
    val exacta = if (productoReal == null) delBanco.firstOrNull() else delBanco.firstOrNull { it.esDe(productoReal) }
    return exacta ?: guiaGenerica(bancoId, delBanco.firstOrNull()?.banco ?: nombre, productoReal)
}

/**
 * **La guía del banco que nombra [texto]** (el banco de una tarjeta, el nombre de una cuenta, el banco que
 * reconoció un extracto: «Nu Ahorros», «Notificación · Nu», «Bancolombia»), o `null` si no nombra un
 * banco que Movi conozca. Con la regla de los nombres de cuenta ([marcaDelBanco]): «Nu» solo como
 * palabra, sin tildes ni mayúsculas.
 */
fun guiaDelExtractoPara(texto: String?, producto: ProductoDelExtracto? = null): GuiaDelExtracto? {
    val marca = texto?.takeIf { it.isNotBlank() }?.let(::marcaDelBanco) ?: return null
    // Una red de tarjeta no es un banco: «Visa Oro» no dice de quién es la tarjeta.
    if (marca.rubro != RubroDeMarca.BANCOS_Y_BILLETERAS || marca.id in REDES_DE_TARJETA) return null
    return guiaDelExtracto(marca.id, producto, marca.nombre)
}

/**
 * La primera guía que encuentra entre [textos], en orden: el banco de las condiciones de una tarjeta gana
 * sobre el nombre de la cuenta («Master Black 3684» no dice que es de Bancolombia; su `CardTerms.bank`, sí).
 */
fun guiaDelExtractoParaAlguno(vararg textos: String?, producto: ProductoDelExtracto? = null): GuiaDelExtracto? =
    textos.firstNotNullOfOrNull { guiaDelExtractoPara(it, producto) }

/**
 * **El producto de una cuenta de Movi**: ahorros y corriente son una cuenta, la tarjeta una tarjeta, el
 * préstamo un crédito y la inversión un fondo. El efectivo y un bien no tienen extracto: `null`.
 */
fun productoDelExtractoDe(cuenta: Account): ProductoDelExtracto? = when {
    cuenta.esBien -> null
    else -> when (cuenta.type) {
        AccountType.SAVINGS, AccountType.CHECKING -> ProductoDelExtracto.CUENTA
        AccountType.CREDIT_CARD -> ProductoDelExtracto.TARJETA
        AccountType.LOAN -> ProductoDelExtracto.CREDITO
        AccountType.INVESTMENT -> ProductoDelExtracto.FONDO
        AccountType.CASH -> null
    }
}

/**
 * **La guía de una cuenta de Movi**: su banco (el de las condiciones de la tarjeta, [bancoDeLaTarjeta],
 * antes que el nombre) y su producto. «Nu Ahorros» → Nu · cuenta; «Nu Tarjeta» → Nu · tarjeta. `null`
 * si la cuenta no tiene extracto (efectivo, un bien) o no nombra un banco: ahí la hoja ofrece la lista.
 */
fun guiaDelExtractoDeLaCuenta(cuenta: Account, bancoDeLaTarjeta: String? = null): GuiaDelExtracto? {
    val producto = productoDelExtractoDe(cuenta) ?: return null
    return guiaDelExtractoParaAlguno(bancoDeLaTarjeta, cuenta.name, producto = producto)
}

// ── Lo que dice la hoja ──────────────────────────────────────────────────────────────────────────

/** La pastilla de la lectura. */
fun textoDeLaLectura(lectura: LecturaDelExtracto): String = when (lectura) {
    LecturaDelExtracto.AL_INSTANTE -> MOVI_LO_LEE_AL_INSTANTE
    LecturaDelExtracto.CON_IA -> CON_IA_PUEDE_TARDAR
    LecturaDelExtracto.SOLO_EL_SALDO -> MOVI_TOMA_EL_SALDO_AL_INSTANTE
    LecturaDelExtracto.SOLO_ALGUNOS_CREDITOS -> SOLO_LIBRE_INVERSION_O_LIBRANZA
    LecturaDelExtracto.NO_SE_IMPORTA -> TODAVIA_NO_SE_IMPORTA
}

/** ¿Hay qué subir? Un crédito que Movi no importa no ofrece «Subir el extracto». */
fun seOfreceSubir(guia: GuiaDelExtracto): Boolean = guia.lectura != LecturaDelExtracto.NO_SE_IMPORTA

const val MOVI_TOMA_EL_SALDO_AL_INSTANTE: String = "Movi toma el saldo al instante"
const val SOLO_LIBRE_INVERSION_O_LIBRANZA: String = "Al instante: libre inversión o libranza"
const val TODAVIA_NO_SE_IMPORTA: String = "Todavía no se importa"

const val MOVI_LO_LEE_AL_INSTANTE: String = "Movi lo lee al instante"
const val CON_IA_PUEDE_TARDAR: String = "Con IA · puede tardar un poco"

/** Lo que dice la hoja cuando los pasos salen de la ayuda del banco y nadie los probó en la app. */
const val NOTA_DE_LA_GUIA_SIN_VERIFICAR: String =
    "Pasos tomados de la ayuda del banco: pueden cambiar un poco en la app."

/** Lo que dice la hoja cuando la fuente no dio los pasos del banco. */
const val NOTA_DE_LA_GUIA_GENERICA: String =
    "Movi todavía no tiene los pasos exactos de este banco."

/** La nota de la fuente, o `null` si los pasos se probaron en el banco. */
fun notaDeLaFuente(guia: GuiaDelExtracto): String? = when (guia.fuente) {
    FuenteDeLaGuia.PROBADA_EN_EL_BANCO -> null
    FuenteDeLaGuia.AYUDA_DEL_BANCO -> NOTA_DE_LA_GUIA_SIN_VERIFICAR
    FuenteDeLaGuia.SIN_PASOS_DEL_BANCO -> NOTA_DE_LA_GUIA_GENERICA
}

/** El título de la hoja: «Cómo bajar tu extracto de Bancolombia», o «… de tu banco». */
fun tituloDeLaGuiaDelExtracto(guia: GuiaDelExtracto): String = "Cómo bajar tu extracto de ${guia.banco}"

/** El rótulo del selector de producto de una guía: «Cuenta», «Tarjeta de crédito», «Cuenta o tarjeta». */
fun rotuloDelProducto(guia: GuiaDelExtracto): String =
    if (guia.productos.size == 1) guia.producto.rotulo
    else guia.productos.joinToString(" o ") { it.rotulo.lowercase() }.replaceFirstChar { it.uppercase() }

/**
 * **El rango ideal para bajar los movimientos**: desde el último corte ([desde], el inicio del período
 * del dueño o el corte de la tarjeta, el que sea más viejo) hasta [hoy]. Si [desde] cae hoy o después,
 * «hoy». Pura.
 */
fun rangoIdealDelExtracto(desde: LocalDate, hoy: LocalDate): String =
    if (desde >= hoy) "Rango ideal: hoy." else "Rango ideal: desde tu último corte, el ${diaLegible(desde)}, hasta hoy."

/**
 * **¿El server lee el Excel de Bancolombia?** (`leerElExcelDeBancolombia`, vuelta 107). Si se apaga, la
 * guía de la cuenta de Bancolombia deja de ofrecerlo (ver [queSubirDe]).
 */
const val EL_LECTOR_DE_EXCEL_ESTA_ACTIVO: Boolean = true

/** «Qué archivo subir», con el Excel solo si su lector está activo. */
fun queSubirDe(guia: GuiaDelExtracto, lectorDeExcel: Boolean = EL_LECTOR_DE_EXCEL_ESTA_ACTIVO): String =
    if (!lectorDeExcel && guia == GUIA_DE_BANCOLOMBIA) "Los «Movimientos» en PDF: Movi los lee al instante." else guia.queSubir

/** El enlace de la propuesta del extracto que falta, y de la hoja de subida. */
const val COMO_BAJARLO: String = "Cómo bajarlo"
const val COMO_BAJO_MI_EXTRACTO: String = "¿Cómo bajo mi extracto?"
const val COMO_BAJAR_TUS_EXTRACTOS: String = "Cómo bajar tus extractos"
const val COMO_BAJAR_EL_ARCHIVO_QUE_MOVI_LEE_MEJOR: String = "Cómo bajar el archivo que Movi lee mejor"
const val SUBIR_EL_EXTRACTO_DE_LA_GUIA: String = "Subir el extracto"

/** La fila del detalle de una cuenta o tarjeta de un banco con guía. */
const val SUBE_EL_EXTRACTO_DE_ESTE_MES: String = "Sube el extracto de este mes"

// ── Vuelta 130 · cuando un extracto no se pudo leer ─────────────────────────────────────────────

/*
 * Las frases con que el server dice que un extracto no se pudo leer. Vivían en `StatementRoutes.kt`; se
 * mudaron acá para que la app las reconozca y ofrezca, debajo, «Cómo bajar el archivo que Movi lee
 * mejor» ([laFallaPideLaGuia]). El server las sigue nombrando igual (sus constantes apuntan a estas).
 */

/** Vuelta 128: un extracto que se cortó aun partido en pedazos. */
const val FRASE_EXTRACTO_INCOMPLETO: String =
    "No pudimos leer todo el extracto, así que no te mostramos una lista a medias. Si tu banco te " +
        "deja descargar los movimientos en PDF o Excel desde su app, súbelos así."

const val FRASE_IMAGEN_INCOMPLETA: String =
    "La imagen tiene demasiados movimientos y la lectura quedó incompleta. Sube el extracto en PDF, " +
        "o toma varias capturas con menos movimientos cada una."

const val FRASE_EXTRACTO_SIN_MOVIMIENTOS: String =
    "No encontramos movimientos en este archivo. Revisa que sea el extracto con el detalle de " +
        "movimientos y no un resumen o un certificado."

const val FRASE_LECTURA_FALLO: String =
    "No pude leer este archivo como extracto. Revisa que sea el PDF o la hoja de cálculo del banco."

/** «Compartir con Movi»: Movi (Haiku) decidió que el papel no muestra un movimiento. */
const val FRASE_PAPEL_SIN_MOVIMIENTO: String =
    "Este archivo no muestra un movimiento de plata. Quedó guardado en Documentos."

/** «Compartir con Movi»: un PDF escaneado, sin texto. */
const val FRASE_PAPEL_SIN_TEXTO: String =
    "Este PDF no tiene texto que Movi pueda leer (parece un escaneo). Compártelo como foto o captura " +
        "de pantalla. El archivo quedó guardado en Documentos."

/** La frase vieja de un PDF con clave (la que recibe un APK que no sabe pedirla). */
const val FRASE_PAPEL_CON_CLAVE: String =
    "Este PDF tiene clave. Actualiza Movi para escribirla y abrirlo. El archivo quedó guardado en Documentos."

/**
 * **¿Este motivo de falla merece la guía?** Sí cuando lo que falló es el archivo: el extracto se cortó,
 * no traía movimientos, no se pudo leer como extracto o tiene clave. No cuando la IA no estaba, no
 * hubo red o el archivo pesa de más: ahí bajar otro papel no arregla nada. Compara por el comienzo,
 * porque «Compartir con Movi» le agrega «El archivo quedó guardado en Documentos.».
 */
fun laFallaPideLaGuia(motivo: String?): Boolean {
    val m = motivo?.trim().orEmpty()
    if (m.isEmpty()) return false
    return listOf(
        FRASE_EXTRACTO_INCOMPLETO, FRASE_IMAGEN_INCOMPLETA, FRASE_EXTRACTO_SIN_MOVIMIENTOS, FRASE_LECTURA_FALLO,
        FRASE_PAPEL_CON_CLAVE, EL_PDF_TIENE_CLAVE, LA_CLAVE_NO_ABRE_EL_PDF,
        FRASE_PAPEL_SIN_MOVIMIENTO, FRASE_PAPEL_SIN_TEXTO,
    ).any { m.startsWith(it) } || m == PDF_CON_CLAVE || m == CLAVE_INCORRECTA
}

/**
 * **La guía que nombra el archivo**: el banco y el producto de su nombre («CuentaNu_2026-09.pdf» → Nu ·
 * cuenta; «Nu_2026-09-17.pdf» → Nu, la primera, la tarjeta), con las reglas de la vuelta 129
 * ([palabrasDelNombreDelArchivo], [productoQueNombraElArchivo]). `null` si no nombra un banco.
 */
fun guiaDelExtractoDelArchivo(nombreDeArchivo: String): GuiaDelExtracto? {
    val producto = when (productoQueNombraElArchivo(nombreDeArchivo)) {
        ProductoDelPapel.TARJETA -> ProductoDelExtracto.TARJETA
        ProductoDelPapel.CUENTA -> ProductoDelExtracto.CUENTA
        ProductoDelPapel.CREDITO -> ProductoDelExtracto.CREDITO
        null -> null
    }
    return guiaDelExtractoPara(palabrasDelNombreDelArchivo(nombreDeArchivo), producto)
}
