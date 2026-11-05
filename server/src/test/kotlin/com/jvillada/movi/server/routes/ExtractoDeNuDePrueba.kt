package com.jvillada.movi.server.routes

/**
 * Vuelta 129 · **el texto de un extracto de la tarjeta de Nu**, con la forma exacta que saca PDFBox
 * (la de `ExtractoDeNuTest`; nombres, cifras y fechas inventados). Cuadra al centavo, así que lo lee
 * `leerElExtractoDeNu` sin IA: las pruebas de rutas lo suben como `.csv`, que el server lee como
 * texto plano, y ninguna llega a Claude.
 */
internal val EXTRACTO_DE_LA_TARJETA_NU: String = """
    Hola, Ana
    Llegó tu extracto de Octubre
    Ana Ejemplo Tus tarjetas
    • • • • 1111 • • • • 2222
    Tarjeta virtualTarjeta física
    Resumen de tu extracto
    Tu cupo definido ${'$'}3.000.000,00
    Usado
    ${'$'}500.000,00
    ${'$'}100.000,00Deuda a pagar este mes
    + ${'$'}5.400,00Intereses
    Comisiones por servicio + ${'$'}0,00
    Cargos por conversión + ${'$'}540,00
    PAGO MÍNIMO ${'$'}101.240,00
    Deuda restante + ${'$'}310.000,00
    Fecha límite de pago
    03 NOV 2026
    Fecha de corte
    12 OCT 2026
    Periodo facturado
    11 SEP 2026 - 11 OCT 2026
    1 / 3
    Nu Financiera
    Bogotá D.C. Colombia
    Fecha Descripción Valor Cuotas Valor
    del mes
    Interés del mes
    Porcentaje y valor este mes
    Total a pagar
    por pagar
    Restante
    10 OCT
    2026
    Panaderia La Esquina
    Norte
    ${'$'}50.000,00 1 de 1 ${'$'}50.000,00 2.09%        ${'$'}0,00 ${'$'}50.000,00 ${'$'}0,00
    04 OCT
    2026
    Libreria Central ${'$'}120.000,00 1 de 12 ${'$'}10.000,00 2.09%        ${'$'}0,00 ${'$'}10.000,00 ${'$'}110.000,00
    ↪ Comisión por cambio de moneda ${'$'}540,00
    27 SEP
    2026
    Pago ${'$'}50.000,00 ${'$'}0,00 -${'$'}100,00
    ↪ A capital ${'$'}0,00
    ↪ A intereses ${'$'}50.000,00
    ↪ A cargos y comisiones ${'$'}0,00
    21 SEP
    2026
    Cafe Central ${'$'}15.300,00 ${'$'}15.300,00 2.16%        ${'$'}0,00 ${'$'}15.300,00 ${'$'}0,00
    Nu Financiera
    Bogotá D.C. Colombia
    NIT 900.000.000-0
    2 / 3
    15 AGO
    2026
    Curso Online ${'$'}240.000,00 2 de 12 ${'$'}20.000,00 2.09%        ${'$'}3.800,00 ${'$'}23.800,00 ${'$'}200.000,00
    18 JUL 2026 Tienda
    Vieja
    Intereses en
    mora
    ${'$'}60.000,00 ${'$'}0,00 2.10%        ${'$'}1.500,00
    2.10%        ${'$'}100,00
    ${'$'}1.600,00 ${'$'}0,00
    Pago mínimo
    ${'$'}101.240,00
    ¿Tienes preguntas sobre tu extracto?
    Nu Financiera
    3 / 3
""".trimIndent()
