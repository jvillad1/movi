# Apartados: plata separada dentro de una cuenta

**Fecha:** 2026-10-20 · **Pedido por:** el dueño, al cerrar el período de octubre.

## Por qué

En Nu el dueño tiene $8.217.464, pero $8.000.000 son el colegio de su hija de noviembre y
diciembre. Movi hoy los cuenta enteros en «Tu plata» y en «Te quedan». La única herramienta que
existe, `Account.condicionadaA`, saca la cuenta **entera**, así que también escondería los
$217.464 que sí son libres. Resultado: Movi le dice que tiene ~$8 M más de lo que puede gastar.

## Qué es

Un **apartado** es un monto con un propósito escrito por el dueño, dentro de una cuenta de
dinero suya: «Colegio nov-dic · $8.000.000 en Nu».

- **No mueve plata ni toca el saldo de la cuenta.** La cuenta sigue cuadrando con el banco al
  peso. Solo cambia cuánto de ese saldo Movi muestra como disponible.
- Una cuenta puede tener varios apartados.
- Se crean, editan y quitan **desde la app** (regla del dueño: nada que solo se pueda tocar por
  código o por la base).
- Opcionalmente se liga a **una categoría de gasto**. Cada gasto de esa categoría que salga de esa
  cuenta desde que se creó el apartado lo va descontando solo. El 25 sale el colegio ($4 M,
  categoría «Hija») desde Nu y el apartado baja de $8 M a $4 M sin que el dueño toque nada.
  *Llamado mío*: el dueño no contestó si lo quería automático; elegí esto porque sin descuento
  el «libre» se vuelve negativo el día del pago.

## Reglas de la cifra

Para cada apartado, el server calcula:

```
consumido = Σ gastos (EXPENSE, no anulados, COP) en la cuenta del apartado,
            con la categoría del apartado, con timestamp ≥ apartado.desde
restante  = clamp(monto − consumido, 0, monto)      // sin categoría: restante = monto
```

Y por cuenta: `apartadoEfectivo = min(Σ restante, max(0, saldo de la cuenta))`. Un apartado
nunca hace que una cuenta «deba» dentro de Tu plata.

- `patrimonioDe` gana un bucket nuevo, **`apartado`**: sale de `tuPlata`/`disponible` y entra a
  `loQueTienes`, así que **el patrimonio neto no cambia**.
- **«Te quedan»** (Disponible del período) resta el `apartadoEfectivo` total de hoy. Así
  «Tu plata» y «Te quedan» siguen de acuerdo, y el día que sale el pago ligado el gasto baja
  «Te quedan» y el apartado sube lo mismo de vuelta: el neto de ese día es cero, que es
  justamente lo que el dueño ya había previsto.
- Solo cuentas de dinero de Tu plata (no deudas, no bienes, no condicionadas) y solo COP.

## Dónde se ve

- **Detalle de la cuenta:** debajo del saldo, «Apartado» con cada propósito y su restante, y
  «Libre» = saldo − apartado. Botón «Apartar plata» → hoja para crear/editar (propósito, monto,
  categoría opcional), y «Quitar».
- **Hoy y Patrimonio:** en el desglose de Tu plata / patrimonio, un tramo **«Apartado · {propósito}»**
  (o «Apartado» si hay varios, con el total), con el mismo trato que hoy tiene «Uso condicionado».
- **Movi AI** y las tres preguntas sugeridas: el contexto dice cuánto está apartado y para qué,
  y no lo ofrece como plata libre.

## Wire y compatibilidad

- Campos nuevos con default en los tipos del wire (`Patrimonio.apartado = 0`,
  `DashboardSummary.apartado = 0`, `PlataDelDisponible.apartado = 0`): un APK viejo los ignora
  y sigue viendo lo de antes.
- Rutas nuevas `GET/POST/PUT/DELETE /api/apartados` (POST idempotente por id del cliente). El
  GET devuelve cada apartado con su `restante` ya calculado.
- El cliente los lee con la caché remota (`remote_cache`, como Metas y Créditos). Crear/editar/
  quitar pide red; sin red, la app lo dice. *Llamado mío*: no vale la pena una tabla local y su
  sincronización para algo que se toca una vez al mes.

## Server

- Tabla `apartados` (`id`, `user_id`, `account_id`, `proposito` ≤ 60, `monto`, `categoria`
  nullable, `desde` epoch ms, `created_at`, `last_edited_at`), con índice por usuario.
- Entra a `TABLAS_DEL_USUARIO` (borrar la cuenta) y a la exportación. Borrar una cuenta borra
  sus apartados (como las Metas).
- Validación: cuenta del dueño y elegible, monto > 0, propósito no vacío, categoría existente
  o nula.

## Fuera de alcance

- Apartados en USD, en deudas o en bienes.
- Apartar en la creación de la cuenta.
- El widget del teléfono (muestra «Te quedan», que ya sale del server).
- Mover plata de verdad entre bolsillos del banco.

## Para quien construya

Lo que queda a criterio: los textos exactos de la UI (español neutro, tuteo), el diseño de la
hoja, y cómo partir el trabajo. Respetar `Tokens.kt`, la prueba de voseo y las de Robolectric.
