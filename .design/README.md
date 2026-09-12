# El canvas del rediseño

Los tableros del rediseño de Movi (septiembre 2026), en el formato de Claude Design.
Acá está el **origen**: lo que se publica se vuelve a armar desde estos archivos.

## Qué hay

| Archivo | Qué es |
|---|---|
| `Main.dc.html` | Inicio, dirección B, tema oscuro |
| `Claro.dc.html` | El MISMO tablero con los tokens invertidos |
| `Movimientos.dc.html` | La lista, con los ajustes de saldo agrupados |
| `Sistema.dc.html` | Las cuatro escalas: color, espaciado, formas, tipografía |
| `Diagnostico.dc.html` | De dónde venimos: los 55 espaciados, los cuatro grises, el contraste que no pasa |
| `DireccionA.dc.html` | Papel. Explorada y **no elegida** |
| `DireccionC.dc.html` | Bloques. Explorada y **no elegida** |
| `canvas.json` | Dónde va cada tablero, las dos páginas y las notas al margen |
| `hallazgos.md` | El benchmark verificado: Bancolombia, Lulo, Mercado Pago, Ualá, RappiCard |

Que `Main` y `Claro` sean el mismo archivo con otros valores es el punto, no un
detalle de producción: es lo que prueba que esto es un sistema y no dos diseños.

## Por qué el `.html` publicado no está acá

Se genera, y pesa 2,5 MB porque lleva adentro el editor entero. Está en `.gitignore`.
Para rearmarlo: `/design` en Claude Code, que extrae el ayudante, y después

```
node <ayudante>/seed-canvas.mjs --template <ayudante>/payload.template.html \
  --out rediseno-movi.html --title "Rediseño de Movi" \
  --artboard Main.dc.html --artboard Claro.dc.html --artboard Movimientos.dc.html \
  --artboard Sistema.dc.html --artboard Diagnostico.dc.html \
  --artboard DireccionA.dc.html --artboard DireccionC.dc.html \
  --canvas canvas.json
```

## Dónde vive esto ya en código

Las decisiones de color, espaciado, formas y tipografía de `Sistema.dc.html` están
implementadas en `shared/src/commonMain/kotlin/com/jvillada/movi/theme/Tokens.kt`, y
`ContrasteDeLosTokensTest` no deja que ninguna se degrade. Si acá cambia un valor,
allá tiene que cambiar también, y al revés.
