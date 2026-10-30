package com.jvillada.movi.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.transactions.CHIP_POR_CONFIRMAR
import com.jvillada.movi.ui.transactions.CHIP_RECURRENTES

sealed class Screen {
    data object Login            : Screen()
    data object Register         : Screen()
    data object Dashboard : Screen()
    /**
     * Movimientos. [chipInicial] es el índice del chip con el que arranca (ver
     * `CHIPS_DE_MOVIMIENTOS` y [com.jvillada.movi.ui.transactions.chipInicialDeMovimientos]);
     * `null` —lo normal, entrar por la pestaña— es «Todo».
     *
     * PR 3 del rediseño de Recurrentes (2026-09): existe porque los enlaces que llevaban a la
     * pantalla de Recurrentes ahora llevan acá con el chip «Recurrentes» puesto. Sin el
     * parámetro, tocar «Ver todos» sobre un pago que vence aterrizaba en la lista completa de
     * movimientos, sin ninguna relación visible con lo que se acababa de tocar.
     *
     * Es un `data class` y no un `data object` por eso, con el mismo precedente que
     * [QuickAdd]: el valor viaja en la pila, así que dos entradas con chips distintos son
     * pantallas distintas para [NavStack.shouldPush] y para el `SaveableStateProvider` de
     * App.kt — volver desde Recurrentes-filtrado a la pestaña deja Movimientos sin filtro, que
     * es lo correcto.
     *
     * Ola C: el chip «Recurrentes» ya no existe en Movimientos, y pedir
     * `Transactions(CHIP_RECURRENTES)` lleva a [Plan] (ver [destinoVigente]). El parámetro sigue
     * sirviendo para los modos sin chip («Por confirmar», «Entre cuentas»).
     *
     * [periodoInicial] es el prefijo («2026-09») del período con el que arranca la lista; `null`
     * —lo normal— es el período de hoy. Lo pide «Ver los movimientos de este período» del detalle
     * de un período, que sin él aterrizaba en el mes en curso y obligaba a volver a buscar el que
     * se estaba mirando con las flechas. Un prefijo que no se entiende también cae en el de hoy.
     *
     * [filtro] (Ola 4 · Ver la plata) lo manda «En qué se va» al tocar una barra o una fila: la
     * lista arranca mostrando solo esa categoría, ese comercio, esa cuenta o esos días, con un chip
     * que lo dice y se quita. `null` —lo normal— no filtra nada.
     */
    data class Transactions(
        val chipInicial: Int? = null,
        val periodoInicial: String? = null,
        /**
         * Ola 5 · la búsqueda de toda la app: tocar un comercio abre Movimientos con la búsqueda ya
         * escrita (la misma que deja «Ver el comercio» de la hoja de un movimiento). `null`, lo normal.
         */
        val consultaInicial: String? = null,
        val filtro: com.jvillada.movi.shared.model.FiltroDeMovimientos? = null,
    ) : Screen()
    /**
     * La hoja de «Agregar», opcionalmente **prellenada**.
     *
     * F10 abrió esta puerta con un solo dato: cuando se entra «para registrar el primero» desde el
     * detalle de una cuenta puntual, esa cuenta viene preseleccionada — sin [presetAccountId],
     * QuickAdd caía siempre en la primera cuenta de la lista, sin importar desde dónde se entró.
     *
     * El resto de los `preset*` son la misma idea llevada hasta el final, y nacen del checklist del
     * período: ahí una fila sin movimiento ofrece **«Anotar este pago»**, y lo que hace es
     * abrir esta hoja con lo que el recurrente ya sabe (el nombre, el monto, la categoría, la
     * cuenta y la fecha en que vencía). Sin ellos, «anotar el movimiento» significaba teclear a
     * mano cinco datos que la app tenía en pantalla — y cualquiera de los cinco escrito distinto
     * rompe el emparejamiento automático que después tendría que tildar la fila sola.
     *
     * **Todos son sugerencias, no imposiciones.** La hoja los pone en sus campos y el dueño los
     * corrige si hacen falta; nada se guarda sin que toque «Guardar movimiento».
     *
     * @param presetMonto en la moneda de la cuenta que se elija, igual que si lo hubiera tecleado.
     *   `null` cuando no hay un monto que sugerir con honestidad — el «monto» de una tarjeta es su
     *   SALDO, no lo que se va a pagar (ver `RecurringRule.montoEsSaldo`).
     * @param presetEsIngreso abre la hoja en la pestaña «Ingreso» en vez de «Gasto». Un sueldo no
     *   se paga: llega.
     * @param presetFecha ISO `"2026-09-05"`, para quien necesite abrir la hoja en otro día. El
     *   checklist ya NO la manda (revisión del 29-sep): un pago anotado con el vencimiento quedaba
     *   fechado un día en que la plata no salió, y el emparejamiento del server ya tolera el
     *   atraso. Ver `hojaParaAnotar`.
     * @param presetDeudaId la cuenta del crédito o la tarjeta que se viene a pagar. Abre la hoja
     *   en la pestaña «Cuota» con esa deuda en «Hacia» y [presetMonto] como monto, y
     *   [presetAccountId] (si viene) como la cuenta de la que sale la plata. Es lo que manda
     *   «Anotar este pago» desde la fila de una cuota o una tarjeta.
     */
    data class QuickAdd(
        val presetAccountId: String? = null,
        val presetNota: String? = null,
        val presetMonto: Long? = null,
        val presetCategoria: String? = null,
        val presetFecha: String? = null,
        val presetEsIngreso: Boolean = false,
        val presetDeudaId: String? = null,
        /**
         * Vuelta 48 · el mes de la cuota que se viene a pagar («septiembre»), cuando la hoja se abre
         * desde su fila de Pagos: la hoja dice «Cuota de X · septiembre». `null` = el del período de hoy.
         */
        val presetMesDeLaCuota: String? = null,
        /**
         * Ola 5 · los atajos del «+» (mantenerlo presionado, o los del ícono de la app en Android):
         * con qué pestaña abre la hoja. `null` = la de siempre (la que deciden los otros presets).
         */
        val presetPestana: com.jvillada.movi.ui.quickadd.PestanaDeAgregar? = null,
        /**
         * Vuelta 43 · **modo pago**: la factura del período que se viene a pagar. Con esto la hoja
         * dice «Pago de Arriendo · octubre» y, al guardar, el movimiento sale junto con la ocurrencia
         * que salda. Ver `AgregarEnModoPago.kt`. Lo pone `hojaParaAnotar`; `null` = Agregar de siempre.
         */
        val presetPago: com.jvillada.movi.ui.quickadd.PagoDeLaFactura? = null,
        /**
         * Vuelta 44 · **«Repartir» abre Agregar en modo personas**: «Repartir este gasto · ¿Quién
         * pagó? Yo | Caro», con el monto en blanco y «Yo» marcado. Antes abría «Pagó otro» en el
         * grupo, que supone que pagó el otro.
         */
        val presetRepartir: Boolean = false,
        /**
         * Vuelta 63 · **«Anotar este abono»** desde la fila del abono mensual en Pagos: al guardar el
         * traspaso (o el pago de la tarjeta), la hoja sella esa ocurrencia con la pata que salió. Ver
         * `AbonoDeLaRegla`. `null` = Agregar de siempre.
         */
        val presetAbono: com.jvillada.movi.ui.quickadd.AbonoDeLaRegla? = null,
    ) : Screen()
    /**
     * Movi AI, opcionalmente **con una pregunta ya lista**.
     *
     * [preguntaInicial] existe para la entrega B de «Movi de un vistazo»: el Inicio muestra las tres
     * preguntas sugeridas (ver `preguntasSugeridas`) y tocar una tiene que llevar al chat con esa
     * pregunta ya hecha, no a un chat en blanco donde el dueño tenga que volver a escribirla. La
     * pantalla la **manda sola, una vez**, apenas abre: tocar el chip ya fue la decisión de
     * preguntar, y pedirle un segundo toque sería hacerle confirmar lo que acaba de hacer.
     *
     * `null` —lo normal, entrar por Más— abre el chat vacío con sus sugerencias.
     *
     * `data class` y no `data object` por el mismo precedente que [QuickAdd] y [Transactions]: el
     * valor viaja en la pila, así que dos entradas con preguntas distintas son pantallas distintas
     * para [NavStack.shouldPush] y para el `SaveableStateProvider` de App.kt.
     */
    data class AIChat(
        val preguntaInicial: String? = null,
        /**
         * El `PreguntaSugerida.id` de [preguntaInicial] cuando el chip que se tocó es una de las tres
         * que Movi contesta sin el modelo. `null` = pregunta libre (o una de las que arman las reglas).
         */
        val sugerida: String? = null,
    ) : Screen()
    data object Credits : Screen()
    /**
     * **«Cómo salir de tus deudas»** (Ola 4): el orden para un abono extra y el simulador. Se abre
     * desde Créditos (Patrimonio → Deudas) y marca Patrimonio, como Créditos.
     */
    data object SalidaDeDeudas : Screen()
    /**
     * Presupuestos suelto. Ola C: su cuerpo es el mismo que el segmento «Presupuestos» de [Plan]
     * (ver `rememberEstadoDePresupuestos`), y todas las puertas que llevaban acá —el destino SDUI
     * `"budgets"`, las alertas de presupuesto superado, la revisión del período— llevan ahora a
     * `Plan(SEGMENTO_PRESUPUESTOS)`. Se conserva para una pila que todavía la traiga; marca la
     * pestaña Plan y su flecha vuelve ahí.
     */
    data object Budgets : Screen()
    /**
     * **Plan** (ola C): «¿cuánto puedo gastar y qué me falta pagar?» — el disponible del período y,
     * debajo, dos segmentos: «Pagos del mes» (el tablero de Recurrentes) y «Presupuestos».
     *
     * [segmento] es con cuál arranca (`SEGMENTO_PAGOS` o `SEGMENTO_PRESUPUESTOS`, en
     * `ui/plan/PlanScreen.kt`). `data class` por el mismo precedente que [Transactions]: el valor
     * viaja en la pila, así que entrar a Presupuestos desde un acceso y a Pagos desde la pestaña
     * son dos pantallas para [NavStack.shouldPush] y para el `SaveableStateProvider` de App.kt.
     */
    data class Plan(val segmento: Int = SEGMENTO_PAGOS) : Screen()
    /**
     * Ola 10 — «Más → Categorías»: ver, renombrar, unificar, esconder y fijar el tipo.
     *
     * Se llega SOLO desde Más (ver [MasScreen]), y esa es toda la puerta que tiene. Precedente a
     * no repetir: al plegar Inversiones dentro de Cuentas, el historial de cada tarjeta quedó
     * inalcanzable porque su único `onNavigate` vivía en la pantalla que se borró. Acá la entrada
     * es una ficha de Más —una lista que nadie está borrando— y no un enlace escondido dentro de
     * otra pantalla.
     */
    data object Categorias : Screen()

    /**
     * **«Personas y comercios»** — el registro de cuentas y llaves ajenas (la esposa, el papá, la
     * cancha, el parqueadero): verlas, guardarlas, unirlas y ver qué se le mandó a cada una y qué
     * mandó cada una. Se llamó «Te deben» y después «Cuentas de otros» (ver `DestinoConocido`).
     *
     * No vive dentro de `Screen.Accounts` **a propósito**: `Screen.Accounts` lista la plata del
     * dueño, y la cuenta de otra persona no es su plata.
     *
     * **Una puerta** (vuelta 73): la fila de «Lo tuyo» en **Tú** (ver `FilaDePersonasYComercios`),
     * más la alerta de Hoy. Marca la pestaña Tú ([navTabFor]). Antes vivía al final de Espacios
     * (vuelta 31), y antes en Movimientos, Patrimonio y Ajustes.
     *
     * [abrir] es el id de un tercero cuya ficha se abre al llegar: lo pide «Ver su ficha» desde el
     * detalle de un movimiento. [sugeridos] = llegar con lo que Movi encontró ya abierto: lo pide la
     * alerta de Hoy («3 personas o comercios sin nombre»), que no tiene sentido que lleve a una
     * línea cerrada. Un `data class` por eso, con el precedente de [Transactions].
     */
    data class Destinos(val abrir: String? = null, val sugeridos: Boolean = false) : Screen()

    /**
     * «Documentos» — los papeles del dueño guardados en Movi (extractos, nóminas, contratos).
     * Ficha de Ajustes: el importador archiva ahí lo que pasa por él, y su sección «Importaciones»
     * es la casa del historial que antes tenía «Extractos» (borrada en la R4; `"extractos"` lleva acá).
     */
    data object Documentos : Screen()
    /**
     * Ola 14 — «Más → Primeros pasos»: la guía de arranque, que hasta acá solo existía como
     * tarjeta del Inicio y se apagaba sola sin ninguna forma de volver a verla. Misma puerta que
     * [Categorias]: una ficha de Más, no un enlace escondido adentro de otra pantalla.
     */
    data object PrimerosPasos : Screen()
    /**
     * **«Primer uso»**: la guía por pasos de una cuenta nueva (período, cuentas, captura, pagos de
     * cada mes). A pantalla completa y sin barra: se abre sola tras crear la cuenta, o desde el
     * Inicio para quien todavía no tiene ninguna cuenta de dinero (ver
     * `debeAbrirseLaGuiaDePrimerUso` en :core). Lo que se salte queda en [PrimerosPasos].
     *
     * Vuelta 54: [desde] abre la guía en un paso de la lista única (la fila «Te faltan 3 pasos ›» del
     * Inicio la abre en el primero que falta); `null`, en la bienvenida.
     */
    data class PrimerUso(val desde: com.jvillada.movi.shared.model.PrimerPaso? = null) : Screen()
    /**
     * **«Compartir»** — el enlace de solo lectura para un tercero (Caro, un asesor): crearlo,
     * mandarlo y revocarlo. Se llega desde **Más** (la puerta que no depende de nadie, como
     * [Destinos]) y desde el ícono de compartir del encabezado del **Inicio**, que es donde uno está
     * mirando su plata cuando se le ocurre mostrársela a alguien.
     */
    data object Compartir : Screen()
    /**
     * **«Lo que Movi sabe de ti»** (Ola 3): lo que el dueño le contó a Movi AI y confirmó guardar
     * —«Caro es mi esposa», «el bono de Glim no es mensual»—, para verlo, corregirlo y borrarlo.
     * Ficha de Ajustes, como [Compartir].
     */
    data object LoQueMoviSabe : Screen()
    /**
     * **«Editar inicio»** (Ola 3 · Hoy en una mirada): qué secciones de Hoy se ven y en qué orden. Se
     * abre desde el botón del final de Hoy y desde Ajustes → Perfil. Ficha de Ajustes, como
     * [LoQueMoviSabe]: no marca pestaña, pero la barra sigue abajo.
     */
    data object EditarInicio : Screen()
    /**
     * **Borrar mi cuenta**: la descarga de los datos primero, qué se borra, y la contraseña con la
     * palabra BORRAR. Se abre desde Ajustes, abajo de todo.
     */
    data object BorrarLaCuenta : Screen()
    /**
     * **«Cuadre de saldos»** — comparar de una sentada lo que Movi cree con lo que dice el banco,
     * y anotar la diferencia de cada cuenta como un ajuste.
     *
     * Se llega desde **Cuentas** (es la pantalla donde se leen esos saldos, así que el error y su
     * arreglo quedan a un toque), desde **Más** (la puerta que no depende de que ninguna otra
     * pantalla conserve su enlace — precedente de [Categorias] y [Destinos]) y desde el aviso del
     * Inicio cuando alguna cuenta lleva demasiado sin cuadrarse.
     */
    data object CuadreDeSaldos : Screen()

    /**
     * **El cuadre de saldos con [cuentaId] preseleccionada** (vuelta de diseño 23): la misma pantalla
     * que [CuadreDeSaldos], con esa cuenta arriba. La abre «Cuadrar efectivo» en el detalle de un
     * efectivo en negativo.
     */
    data class CuadrarLaCuenta(val cuentaId: String) : Screen()

    /**
     * **«Por revisar»** (ola C): la única bandeja de lo que entró solo y espera una decisión — los
     * mensajes del banco por confirmar, los movimientos que entraron solos y los candidatos a pago
     * de tarjeta. Antes eran dos lugares (la pantalla de Mensajes del banco y un aviso de
     * Movimientos) y el dueño tenía que saber cuál mirar.
     *
     * Se llega desde el renglón «N por revisar» de Movimientos y desde las alertas del Hoy, así
     * que marca la pestaña Movimientos (ver [navTabFor]). `Transactions(CHIP_POR_CONFIRMAR)` —el
     * modo viejo— cae acá (ver [destinoVigente]).
     */
    data object PorRevisar : Screen()
    /**
     * **«Captura del banco»** (ola C) — en la web y en iOS se llama «Mensajes del banco», ver
     * `tituloDeCapturaDelBanco`. Lo que quedó de la vieja bandeja de SMS cuando lo pendiente se
     * mudó a [PorRevisar]: cómo está la captura (el permiso de SMS, el acceso a notificaciones, la
     * hibernación y el barrido del historial en Android) y el **historial** de todos los mensajes
     * que llegaron, para consultar. Es una ficha de Ajustes: se configura de vez en cuando, no se
     * mira todos los días.
     */
    data object CapturaDelBanco : Screen()
    /**
     * Vuelta 67 · **«Lo anulado»**, con «Restaurar». [periodo] (el prefijo, «2026-10») cuando se abre
     * desde el final de Actividad («2 anulados en octubre · Ver»); `null` desde Tú, con todo.
     */
    data class Anulados(val periodo: String? = null) : Screen()
    /**
     * Un mensaje del banco por confirmar. Se abre desde [PorRevisar] (y desde el historial de
     * [CapturaDelBanco]), así que marca Movimientos y su flecha, sin historial, cae en la bandeja.
     */
    data class SMSReconcile(
        val smsId: String,
        /**
         * Ola 5 · «Otra categoría» en la notificación del aviso: abre con el selector de categoría
         * ya desplegado, porque a eso se vino.
         */
        val abrirCategoria: Boolean = false,
    ) : Screen()
    /**
     * **Ajustes** (lo que abría el avatar). Vuelta 38: sus secciones se mudaron a [Tu], y pedir `Mas`
     * —una pila vieja, el destino SDUI `"mas"` o `"profile"`, un enlace que nadie actualizó— lleva a
     * «Tú» (ver [destinoVigente]). Se conserva para que esas rutas no se pierdan.
     */
    data object Mas : Screen()
    /**
     * **«Tú»** (vuelta 38, la cuarta pestaña de la opción 3): el centro de lo personal. Arriba quién
     * eres (avatar, nombre y correo), después «Tu patrimonio» con la cifra neta, «Personas» con su
     * saldo y «Cuadre de saldos», y debajo todo lo que era Ajustes. Patrimonio, Personas (Espacios) y
     * lo que se abría desde Ajustes se apilan encima, con su volver.
     */
    data object Tu : Screen()
    /** **Patrimonio**. Desde la vuelta 38 se abre apilado sobre [Tu] (ver [NavStack.navegar]). */
    data object Accounts : Screen()
    /**
     * Detalle de una cuenta. Lleva el grupo de la cuenta además del id porque la navegación
     * necesita saber DÓNDE vive esa cuenta antes de cargarla del repositorio: una tarjeta o un
     * préstamo se abren desde Créditos (Ola 7) y una cuenta de dinero desde Cuentas, y de eso
     * dependen tanto la pestaña resaltada ([navTabFor]) como el destino de reserva de la flecha
     * ‹ ([homeScreenFor]). El grupo es obligatorio a propósito: quien navega acá ya tiene la
     * cuenta en la mano, y así ningún llamador nuevo puede olvidarse del dato.
     */
    data class AccountDetail(val accountId: String, val group: AccountGroup) : Screen()
    data class StatementReview(val resultJson: String) : Screen()
    data class ImportDetail(val importId: String) : Screen()
    data object ScreenEditor : Screen()

    /**
     * **«Tus períodos»** — cómo le fue al dueño en cada ciclo (entró, salió, te
     * quedó), del en curso hacia atrás. Se llega desde el rango del hero del Inicio (que se vuelve
     * tocable), desde una fila propia en Plan y desde «Ver tus períodos» en la hoja de Movimientos
     * que explica el rango del mes ([com.jvillada.movi.ui.profile.InicioDelPeriodoSheet]) — las
     * tres puertas donde el dueño ya está mirando SU período y puede querer comparar con otros.
     * Marca la pestaña Plan (ver [navTabFor]): es la pregunta «¿cómo me fue?», la misma que
     * contesta esa pestaña.
     */
    data object Periodos : Screen()

    /**
     * El detalle de un período, [id] = su prefijo («2026-09»). Misma pestaña que
     * [Periodos]: se llega tocando una fila suya.
     */
    data class DetalleDePeriodo(val id: String) : Screen()

    /**
     * **Buscar en toda la app** (Ola 5): movimientos, comercios, personas, categorías, cuentas y
     * pantallas. Se abre con la lupa del encabezado de Hoy, así que marca Hoy.
     */
    data object Buscar : Screen()

    /**
     * **«En qué se va»** (Ola 4 · Ver la plata): el gasto de un período día por día contra el
     * anterior, y la lista por categoría, comercio o cuenta. Se abre desde la sección del mismo
     * nombre en Hoy. [periodo] es el prefijo («2026-10») con el que arranca; `null` es el de hoy.
     */
    data class EnQueSeVa(val periodo: String? = null) : Screen()

    /**
     * **Personas** (antes «Espacios», R3): lo grupal con casa propia —«Casa con Caro»—, su saldo de
     * quién le debe a quién y lo que paga otra persona. Vuelta 38: dejó de ser pestaña; vive en
     * [Tu] y se abre apilado encima (ver [NavStack.navegar]).
     */
    data object Espacios : Screen()

    /**
     * Un espacio: quién puso qué, el saldo con «Marcar como saldado», lo que Movi propone vincular y
     * la lista de sus gastos. [pagoOtro] abre de entrada la hoja «Pagó otro» (viene de Agregar).
     */
    data class Espacio(val id: String, val pagoOtro: Boolean = false) : Screen()
}

/**
 * A qué pestaña pertenece cada pantalla; `null` = ninguna pestaña marcada. App.kt lo usa para
 * resaltar el ítem activo en la barra (teléfono) y en el rail (pantalla ancha), que son los únicos
 * lugares donde se pinta la navegación — ninguna pantalla arma su propia barra.
 *
 * Vuelta 38 (la opción 3): cuatro lugares, los mismos en el teléfono y en la web — **Inicio** (¿cómo
 * estoy?), **Actividad** (¿qué pasó?), **Pagos** (¿cuánto puedo gastar y qué me falta pagar?) y **Tú**
 * (lo tuyo: cuánto tienes y cuánto debes, las personas con las que compartes gastos y los ajustes).
 * Cada pantalla marca la pestaña de la pregunta que contesta: Presupuestos y Tus períodos son Pagos;
 * Patrimonio, Créditos, el cuadre, el detalle de cualquier cuenta, Personas y lo que era Ajustes son Tú.
 *
 * Movi AI no marca ninguna en el teléfono (se abre desde Inicio y desde Tú); en el rail tiene su lugar
 * ([destinoResaltado]). **La barra sigue pintada** aunque no haya pestaña marcada (ver
 * [muestraLaNavegacion]).
 */
fun navTabFor(screen: Screen): NavTab? = when (screen) {
    // «En qué se va» se abre desde Hoy y contesta la pregunta de Hoy: ¿cómo voy? Buscar también.
    Screen.Dashboard, Screen.Buscar, is Screen.EnQueSeVa -> NavTab.HOY
    // «Por revisar» y el detalle de un mensaje se abren desde Actividad (su renglón «N por
    // revisar»): marcan esa pestaña.
    is Screen.Transactions, Screen.PorRevisar, is Screen.SMSReconcile -> NavTab.MOVIMIENTOS
    is Screen.Plan, Screen.Budgets, Screen.Periodos, is Screen.DetalleDePeriodo -> NavTab.PLAN
    // Vuelta 38: lo que eran las pestañas Patrimonio y Espacios, y lo que era Ajustes, vive en «Tú».
    Screen.Tu, Screen.Mas,
    Screen.Accounts, Screen.Credits, Screen.SalidaDeDeudas, Screen.CuadreDeSaldos, is Screen.CuadrarLaCuenta,
    Screen.Espacios, is Screen.Espacio, is Screen.Destinos,
    Screen.CapturaDelBanco, Screen.Categorias, Screen.PrimerosPasos, Screen.Documentos, Screen.Compartir,
    Screen.LoQueMoviSabe, Screen.BorrarLaCuenta, Screen.EditarInicio -> NavTab.TU
    // El detalle hereda la pestaña de la pantalla donde vive la cuenta — así resaltar y
    // «volver» no pueden contradecirse. Las dos (Cuentas y Créditos) son Tú.
    is Screen.AccountDetail -> navTabFor(homeScreenFor(screen.group))
    // Vuelta 67: desde Actividad (con su período) marca Actividad; desde Tú, Tú.
    is Screen.Anulados -> if (screen.periodo != null) NavTab.MOVIMIENTOS else NavTab.TU
    else -> null
}

/**
 * **Ajustes y lo que se abre desde ahí** — las pantallas a las que se llegaba por el avatar.
 *
 * Ola C: son las que hasta acá marcaban la pestaña «Más». Vuelta 38: casi todas marcan «Tú» (ver
 * [navTabFor]); Movi AI no marca ninguna en el teléfono, pero sigue con la barra abajo: esconderla
 * dejaría al dueño sin forma de saltar a Actividad que no sea volver dos veces.
 */
fun esDeAjustes(screen: Screen): Boolean = when (screen) {
    Screen.Mas, is Screen.AIChat,
    Screen.CapturaDelBanco, Screen.Categorias, Screen.PrimerosPasos,
    Screen.Documentos, Screen.Compartir, Screen.LoQueMoviSabe, Screen.BorrarLaCuenta, Screen.EditarInicio -> true
    else -> false
}

/**
 * ¿Se pinta la barra (o el rail) debajo de esta pantalla? Sí en las cuatro pestañas y en Ajustes
 * ([esDeAjustes]); no en la autenticación ni en los flujos a pantalla completa (escanear un recibo,
 * revisar un extracto, el editor de pantallas).
 */
fun muestraLaNavegacion(screen: Screen): Boolean = navTabFor(screen) != null || esDeAjustes(screen)

/**
 * ¿Esta pantalla se abre como **ventana modal encima** de la actual, en vez de reemplazarla?
 *
 * Hoy solo [Screen.QuickAdd]. «Agregar» siempre fue una hoja —fondo oscuro clickeable y un panel
 * pegado abajo— pero se apilaba como una pantalla más, y ahí estaba el problema: [navTabFor] no
 * le da pestaña (no es un destino de la navegación), y App.kt solo pinta el rail y la barra
 * cuando hay pestaña activa. Resultado: abrir Agregar hacía desaparecer TODO el chrome. En el
 * teléfono se disimulaba (la hoja tapa casi toda la pantalla); en escritorio el rail se esfumaba
 * y la hoja quedaba flotando sobre un vacío negro, corrida hacia un borde.
 *
 * La corrección es de una línea conceptual: App.kt desvía a un estado de overlay cualquier
 * pantalla que devuelva `true` acá, en vez de apilarla. La pila no se toca, así que la pestaña
 * activa sigue siendo la de la pantalla de atrás —el rail y la barra siguen pintados— y la hoja
 * se dibuja adentro del área de contenido, centrada como el resto del contenido y atenuando lo
 * que hay debajo. Se resolvió así, y no sacando `QuickAdd` de [Screen], para no tocar los cinco
 * llamadores que ya navegan con `navigate(Screen.QuickAdd(...))`: el tipo sigue siendo la forma
 * de PEDIR la hoja; lo que cambió es cómo App.kt la atiende.
 */
fun opensAsOverlay(screen: Screen): Boolean = screen is Screen.QuickAdd

/**
 * Ola 7 (F61): dónde «vive» una cuenta en la navegación — la pantalla que la lista. Las deudas
 * (tarjetas y préstamos) se listan en Créditos; el resto, en Cuentas. Es la única regla: la usan
 * el destino de reserva del «volver» del detalle y [navTabFor] para la pestaña resaltada.
 */
fun homeScreenFor(group: AccountGroup): Screen =
    if (group == AccountGroup.DEUDA) Screen.Credits else Screen.Accounts

/** Pantalla principal de cada destino de la barra/rail (inversa de [navTabFor]). */
fun screenForTab(tab: NavTab): Screen = when (tab) {
    NavTab.HOY -> Screen.Dashboard
    NavTab.MOVIMIENTOS -> Screen.Transactions()
    NavTab.ADD -> Screen.QuickAdd()
    NavTab.PLAN -> Screen.Plan()
    NavTab.TU -> Screen.Tu
    NavTab.MOVI_AI -> Screen.AIChat()
}

/**
 * **Qué destino resalta la navegación** para [screen]: lo mismo que [navTabFor], más Movi AI, que
 * en el rail tiene su propio lugar ([com.jvillada.movi.ui.components.destinoMoviAi]).
 *
 * Aparte de [navTabFor] a propósito: esa función decide también el encabezado (avatar o flecha,
 * ver `leadingFor`) y en el teléfono Movi AI se abre desde Hoy, así que ahí sigue llevando flecha.
 * La barra del teléfono no tiene a Movi AI entre sus cuatro, así que con el chat abierto no marca
 * ninguna — igual que antes.
 */
/**
 * **¿Movi AI es la raíz de su pestaña?** (vuelta 33). En una ventana con rail —mediana o ancha—
 * Movi AI tiene su lugar propio ([destinoResaltado]) y tocarlo deja la pila como la de cualquier
 * otra pestaña (`[Hoy, Movi AI]`, ver [NavStack.navegarALaPestana]); ahí su encabezado va con el
 * avatar, como Espacios o Patrimonio, y no con una «←» que no lleva a ningún lado. En el teléfono
 * no hay pestaña de Movi AI: siempre se abre encima de otra pantalla (desde Hoy, desde Ajustes) y
 * conserva la flecha. Con el rail, abierto encima de algo que no es Hoy —desde Ajustes, desde
 * Movimientos—, también la conserva: ahí volver sí lleva a algún lado.
 */
fun moviAiEsLaRaizDeSuPestana(pila: List<Screen>, clase: WindowWidthClass): Boolean =
    clase != WindowWidthClass.Compact &&
        pila.lastOrNull() is Screen.AIChat &&
        pila.dropLast(1).all { it == Screen.Dashboard } &&
        pila.size <= 2

fun destinoResaltado(screen: Screen): NavTab? =
    if (screen is Screen.AIChat) NavTab.MOVI_AI else navTabFor(screen)

/**
 * **Qué destino resalta la navegación con [pila] a la vista** (vuelta de diseño 30): el de la pantalla
 * de arriba ([destinoResaltado]), salvo **Buscar**, que no es de ninguna pestaña —se abre con la lupa
 * de Hoy y con la de Movimientos—: marca la pestaña de donde se abrió, la de la pantalla más cercana
 * debajo que tenga una. Abierta desde Movimientos, la barra marcaba «Inicio». Sin nada debajo, Hoy.
 */
fun destinoResaltadoDeLaPila(pila: List<Screen>): NavTab? {
    val arriba = pila.lastOrNull() ?: return null
    if (!marcaLaPestanaDeDondeSeAbrio(arriba)) return destinoResaltado(arriba)
    return pila.dropLast(1).asReversed()
        .firstNotNullOfOrNull { pantalla -> destinoResaltado(pantalla).takeIf { !marcaLaPestanaDeDondeSeAbrio(pantalla) } }
        ?: NavTab.HOY
}

/**
 * Las pantallas que no son de una pestaña sola y marcan la de donde se abrieron: **Buscar** (la lupa
 * de Inicio y la de Actividad) y, desde la vuelta 61, **«En qué se va»**: se abre desde Inicio y
 * desde la alarma del Disponible de Pagos («Mira en qué se fue ›»), y abierta desde Pagos marcaba
 * «Inicio». Sin nada debajo, Inicio.
 */
private fun marcaLaPestanaDeDondeSeAbrio(pantalla: Screen): Boolean =
    pantalla == Screen.Buscar || pantalla is Screen.EnQueSeVa

/**
 * **Adónde se va de verdad** cuando se pide [screen].
 *
 * Ola C: el tablero de Recurrentes salió de Movimientos a Plan. `Screen.Transactions(CHIP_RECURRENTES)`
 * sigue pudiendo pedirse —el índice del chip no se renumera ni se reusa, y una pila vieja, un aviso
 * o un enlace que nadie actualizó lo pueden traer— y cae en **Plan · Pagos del mes**, que es donde
 * vive ahora lo que ese chip mostraba. Se resuelve acá, una vez, y no en cada llamador: [NavStack.navegar]
 * pasa todo destino por esta función antes de apilarlo.
 */
fun destinoVigente(screen: Screen): Screen = when {
    screen is Screen.Transactions && screen.chipInicial == CHIP_RECURRENTES -> Screen.Plan(SEGMENTO_PAGOS)
    // Ola C, tarea 5: el modo «Por confirmar» de Movimientos se juntó con los mensajes del banco
    // y los pagos de tarjeta en una sola bandeja. El índice sigue valiendo 3 (no se renumera) y
    // quien lo pida llega a donde ahora vive lo que mostraba.
    screen is Screen.Transactions && screen.chipInicial == CHIP_POR_CONFIRMAR -> Screen.PorRevisar
    // Vuelta 38: Ajustes se mudó a «Tú». Quien pida Ajustes (el destino SDUI `"mas"`/`"profile"`, una
    // pila vieja) llega a Tú, donde están sus mismas secciones.
    screen == Screen.Mas -> Screen.Tu
    else -> screen
}

/**
 * Reglas puras de la pila de navegación (sin Compose), extraídas para poder
 * testearlas en :shared:commonTest (App.kt las aplica sobre un
 * SnapshotStateList<Screen> para que Compose observe los cambios).
 */
object NavStack {
    /** true si `screen` debe apilarse — evita duplicar la pantalla de arriba. */
    fun shouldPush(stack: List<Screen>, screen: Screen): Boolean =
        stack.isEmpty() || stack.last() != screen

    /** Las pantallas de antes de tener sesión: entrar y crear cuenta. */
    fun esDeAutenticacion(screen: Screen): Boolean =
        screen == Screen.Login || screen == Screen.Register

    /**
     * ¿Ir a [screen] tiene que **reemplazar la pila entera** en vez de apilar encima?
     *
     * Sí cuando se está saliendo de la autenticación: la pila arranca en [Screen.Login] y
     * entrar apilaba el Inicio encima, dejando `[Login, Dashboard]`. Nada la limpiaba, así que
     * el botón «atrás» del teléfono devolvía al formulario de entrada —con la contraseña en
     * blanco— a alguien que acababa de entrar. Con «Entrar con huella» prendido era peor:
     * aterrizar de nuevo en `LoginScreen` re-dispara su efecto de arranque y el prompt del
     * lector aparece solo, sin que nadie lo haya pedido. Por Registro la pila era
     * `[Login, Register, Dashboard]` y «atrás» mostraba un «Crear cuenta» a quien recién creaba
     * una.
     *
     * La regla mira el FONDO de la pila, no el tope: así cubre por igual la entrada directa y la
     * que pasa por Registro, y no necesita que cada pantalla de auth se acuerde de pedir nada.
     * Moverse entre Login y Register sigue apilando — ahí volver sí tiene sentido.
     */
    fun shouldReplaceAll(stack: List<Screen>, screen: Screen): Boolean =
        stack.isNotEmpty() && esDeAutenticacion(stack.first()) && !esDeAutenticacion(screen)

    /**
     * Aplica una navegación sobre la pila: reemplazar todo (ver [shouldReplaceAll]), apilar
     * (ver [shouldPush]) o no hacer nada. App.kt la llama sobre su `SnapshotStateList`, que es
     * un `MutableList` como cualquier otro — y así la regla se puede probar sin Compose.
     */
    fun navegar(stack: MutableList<Screen>, pedido: Screen) {
        // Un destino que se mudó se resuelve ANTES de mirar la pila: si no, `shouldPush`
        // compararía contra lo pedido y no contra lo que de verdad se apila. Ver [destinoVigente].
        val screen = destinoVigente(pedido)
        when {
            shouldReplaceAll(stack, screen) -> {
                stack.clear()
                stack.add(screen)
            }
            esRaizDePestana(screen) -> {
                val pila = pilaDeLaPestana(screen)
                if (stack != pila) {
                    stack.clear()
                    stack.addAll(pila)
                }
            }
            // Vuelta 38: Patrimonio y Personas eran raíces; ahora se abren encima de «Tú».
            seAbreSobreTu(screen) && Screen.Tu !in stack -> {
                stack.clear()
                stack.addAll(pilaDeLaPestana(Screen.Tu) + screen)
            }
            shouldPush(stack, screen) -> stack.add(screen)
        }
    }

    /**
     * **Lo que hace tocar una pestaña de la barra o del rail** (vuelta 33). Igual que [navegar],
     * salvo Movi AI: no es raíz para [navegar] —desde Ajustes o desde Hoy se apila, y volver regresa
     * ahí—, pero tocada en el rail sí lo es, y deja la pila como cualquier otra pestaña:
     * `[Hoy, Movi AI]`. Antes se apilaba encima de lo que hubiera (`[Hoy, Movimientos, Movi AI]`) y
     * el encabezado mostraba una flecha. Ver [moviAiEsLaRaizDeSuPestana].
     */
    fun navegarALaPestana(stack: MutableList<Screen>, tab: NavTab) {
        val destino = destinoVigente(screenForTab(tab))
        if (tab != NavTab.MOVI_AI) return navegar(stack, destino)
        val pila = pilaDeLaPestana(destino)
        if (stack != pila) {
            stack.clear()
            stack.addAll(pila)
        }
    }

    /**
     * Las cuatro pantallas raíz de la barra (y del rail): Inicio, Actividad, Pagos y Tú, tal como las
     * abre su pestaña ([screenForTab]). Actividad con un chip puesto o Pagos en Presupuestos no son
     * raíz: son otra pantalla de la misma pestaña, y se apilan.
     */
    fun esRaizDePestana(screen: Screen): Boolean = screen in RAICES_DE_PESTANA

    private val RAICES_DE_PESTANA: List<Screen> =
        listOf(NavTab.HOY, NavTab.MOVIMIENTOS, NavTab.PLAN, NavTab.TU).map(::screenForTab)

    /**
     * **Lo que antes era raíz y ahora vive en «Tú»** (vuelta 38): Patrimonio y Personas (Espacios).
     * Pedidos desde fuera de Tú —el tramo de patrimonio de Inicio, «Repartir», un enlace viejo— no se
     * apilan sobre la pantalla de donde se pidieron: la pila queda `[Inicio, Tú, Patrimonio]`, así la
     * barra marca «Tú» y su volver lleva a Tú, la pantalla donde viven. Desde dentro de Tú se apilan
     * como cualquier otra.
     */
    fun seAbreSobreTu(screen: Screen): Boolean = screen == Screen.Accounts || screen == Screen.Espacios

    /**
     * **Saltar entre pestañas no apila historial** (Ola 1 · «Que Movi se sienta viva»).
     *
     * Antes cada toque en la barra apilaba: Hoy → Movimientos → Hoy → Plan dejaba cuatro pantallas
     * en la pila, y el «atrás» del teléfono recorría ese zigzag entero antes de salir. Ahora ir a una
     * raíz **reemplaza** la pila: queda Hoy abajo y la pestaña encima — `[Hoy]` para Hoy,
     * `[Hoy, Movimientos]` para Movimientos—, que es lo que hace Android con su barra de abajo: el
     * «atrás» desde otra pestaña vuelve a Hoy, y desde Hoy sale.
     *
     * Lo que se había apilado dentro de la pestaña de antes (un detalle, «Por revisar») se suelta:
     * tocar otra pestaña es irse de ahí. Sus estados guardados (el scroll, lo tipeado) siguen en el
     * `SaveableStateProvider` de App.kt, igual que antes.
     */
    fun pilaDeLaPestana(raiz: Screen): List<Screen> =
        if (raiz == Screen.Dashboard) listOf(Screen.Dashboard) else listOf(Screen.Dashboard, raiz)

    /**
     * Resultado de pedir "volver": si hay historial, se saca el tope (Pop); si la
     * pila tiene un solo elemento hace falta un destino de reserva (Fallback) —
     * por ejemplo, entraste directo por deep link o recargaste la web en una
     * pantalla que no es Inicio.
     */
    sealed class BackResult {
        data object Pop : BackResult()
        data class Fallback(val screen: Screen) : BackResult()
    }

    fun back(stack: List<Screen>, fallback: Screen): BackResult =
        if (stack.size > 1) BackResult.Pop else BackResult.Fallback(fallback)
}

/**
 * Cuántas veces se guardó algo desde una **ventana modal** en esta sesión.
 *
 * Existe por lo que el overlay de «Agregar» rompió (ver [opensAsOverlay]): antes, abrir Agregar
 * apilaba una pantalla y la de atrás salía de la composición, así que al volver reejecutaba su
 * `LaunchedEffect(refreshKey)` y recargaba sola. Ahora la pantalla de atrás **nunca sale** —esa
 * es justamente la mejora: conserva su estado— pero eso significa que nadie le avisa que sus
 * datos quedaron viejos. El síntoma: registrás un movimiento o un traspaso, la hoja se cierra y
 * Movimientos / Inicio / el detalle de la cuenta siguen mostrando la lista de antes. Peor todavía,
 * la app parece decir que no pasó nada, y el reflejo es volver a guardar — el mismo reintento a
 * ciegas que duplicaba traspasos.
 *
 * Cada pantalla que lee datos lo usa como una key más de su `LaunchedEffect`. Un `Int` que sube
 * y nada más: no hay evento, ni payload, ni quién escucha a quién — la pantalla ya sabe cómo
 * recargarse, lo único que le faltaba era enterarse.
 *
 * `compositionLocalOf` y NO `staticCompositionLocalOf`: este valor cambia. Con la variante
 * estática, Compose no rastrea lecturas y cada guardado invalidaría TODO el subárbol bajo el
 * provider en vez de solo las pantallas que lo leen — el mismo anti-patrón que el `remember`
 * de `goBackTo` documenta unas líneas más abajo, pero al revés: aquello es estático porque
 * nunca cambia, esto cambia y por eso no puede serlo.
 */
val LocalRefreshTick = compositionLocalOf { 0 }

/**
 * «Volver» real, expuesto a las pantallas: recibe el destino de reserva (F22) y
 * decide adentro si hay historial al que volver o si hay que caer a ese destino.
 * App.kt provee la implementación de verdad alrededor del `when(currentScreen)`;
 * el default es un no-op para que un preview o test aislado no explote.
 */
val LocalGoBack = staticCompositionLocalOf<(Screen) -> Unit> { {} }

/**
 * Navegar a una pantalla desde donde no llega el `onNavigate` de nadie.
 *
 * Existe por un pedido concreto del dueño: *«Necesito un editor de categorías en las diferentes
 * secciones»*. El editor ya existía —«Más → Categorías», con renombrar, unificar, esconder y
 * fijar el tipo— pero solo se llegaba saliendo a «Más», o sea abandonando lo que uno estaba
 * haciendo. El campo de categoría, en cambio, aparece en las cuatro secciones donde la pregunta
 * se hace ([com.jvillada.movi.ui.components.CategoryField]: Movimientos, Agregar, Presupuestos y
 * Recurrentes), y es un componente compartido: ponerle el acceso ahí lo resuelve en los cuatro
 * lados de una vez.
 *
 * Se agrega un local en vez de enhebrar un `onNavigate` por cuatro pantallas y sus hojas porque
 * tres de esos cuatro puntos están dentro de hojas modales que ya reciben media docena de
 * callbacks. Es `staticCompositionLocalOf` por el mismo motivo que [LocalGoBack]: la función que
 * se provee no cambia nunca.
 *
 * El default es un no-op para que un preview o un test aislado no explote.
 */
val LocalNavigate = staticCompositionLocalOf<(Screen) -> Unit> { {} }
