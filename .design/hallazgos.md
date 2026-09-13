# Benchmark — lo verificado (2026-09-11)

## Bancolombia / Mi Bancolombia  [el banco que él ya tiene instalado]
- Rebrand 2021 por Vasava: **tipografía propia CIB Font en 4 cortes**, y uno de ellos,
  **CIB Font Numerales**, existe SOLO para los números ("an emphasis on numbers").
- Eje de marca **blanco y negro** + 6 colores vivos secundarios. El amarillo es ACENTO,
  no fondo. Por eso pudieron agregar modo oscuro sin rediseñar.
- Modo oscuro llegó en enero 2025, encuadrado como **accesibilidad**, con elección explícita.
- La home NO muestra el saldo: es un lanzador de tareas. Hay que tocar "Ver saldos y
  movimientos" y recién ahí autenticarse.
- "Día a Día" abre con **gráfico → categorías → movimientos**, no con lista plana.
  Categoriza por actividad económica del comercio y **aprende de las correcciones**.
- Reseñas: 3,4-3,6/5. "glitchy, half-baked transitions", "the UI is a mess",
  "nothing is where you expect it to be".

## Lulo Bank (Colombia) — iF Design Award 2022
- **Oscuro por decisión, no por opción**: fondo azul profundo. El líder de diseño lo
  justifica por legibilidad y descanso visual.
- Paleta derivada de la fruta lulo; azul profundo como color transversal.
- Sistema de diseño propio, **Zumo**, nativo en Compose y SwiftUI.
- Movimiento como decisión de SISTEMA: easing siempre, nunca más de ~3s, y un
  indicador de carga animado de **menos de 1 segundo**.

## Mercado Pago / sistema Andes  [el hallazgo más robable]
- Componente atómico **`MoneyAmount`** con tres variantes semánticas: POSITIVE,
  NEGATIVE, PREVIOUS. Con `MoneyAmountFormatter` que normaliza separadores por locale
  y un `AccessibilityDelegate` propio para que el lector de pantalla lea el monto igual
  siempre. `MoneyAmountCombo` agrupa montos relacionados en UN nodo accesible.
  → Un monto no es un string formateado: es un componente con variante semántica,
    formateador por moneda y contrato de accesibilidad propio.
- Verde = éxito/positivo, separado del teal/azul de marca.
- **Marzo 2025**: cambiaron el ícono al amarillo de Mercado Libre. La gente dejó de
  distinguir las dos apps. **Marzo 2026: rollback público del CEO.** El color de
  producto es infraestructura de identidad, no decoración.
- Home AR 2026: **tres pestañas — Pesos / Dólares / Reservas**, con cotización en vivo
  y rendimiento diario.

## Ualá
- v1.1 (2026): **retunearon tipografía e íconos para que los productos de inversión
  le ganen jerarquía al saldo disponible.** Inversión deliberada de la jerarquía típica.
- Home que **se reordena por comportamiento** del usuario.

## RappiCard
- **Un toque en la home muestra/oculta el saldo**, encuadrado como privacidad.
- Accesos directos en la primera pantalla, incluido el NIP.

## Patrón regional 2026
- Ualá y Mercado Pago lanzaron home ordenada por comportamiento el mismo año:
  ya es el default regional, no un diferenciador.
