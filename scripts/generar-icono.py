#!/usr/bin/env python3
"""
El ícono de Movi (vuelta 65): la «m» de Figtree ExtraBold en tinta (#1F1B16) sobre el amarillo de la
marca (#FFC21A). Es la opción A que eligió el dueño el 29-oct-2026.

Todo sale del MISMO glifo, leído del TTF que ya trae la app
(`shared/src/commonMain/composeResources/font/figtree_extrabold.ttf`) con fontTools: el contorno se
pasa a `pathData` para los vectores de Android y a polígonos para los PNG. Así el ícono del teléfono,
el de las notificaciones, el de la web y la marca de la guía de primer uso son la misma «m».

Uso, desde la raíz del repo (necesita fontTools y Pillow):

    python3 scripts/generar-icono.py                  # escribe los recursos del repo
    python3 scripts/generar-icono.py --capturas DIR   # además, las capturas para mostrar el ícono

En esta Mac fontTools y Pillow están en el Python de Xcode, en el site del usuario (sin `-I`):
`/Applications/Xcode.app/Contents/Developer/usr/bin/python3 scripts/generar-icono.py`.

Lo que escribe:
- `androidApp/src/main/res/drawable/ic_launcher_foreground.xml`: el primer plano del ícono adaptable
  (108 × 108, la «m» dentro de la zona segura de 66). También es la capa `monochrome`.
- `androidApp/src/main/res/drawable/ic_stat_movi.xml`: el ícono chico de las notificaciones, blanco.
- `androidApp/src/main/res/mipmap-*/ic_launcher.png` e `ic_launcher_round.png`: el respaldo de
  Android 7.x (API 24-25), que no lee el ícono adaptable.
- `webApp/src/wasmJsMain/resources/icons/`: los de la PWA (maskable y «any»), el apple-touch-icon,
  el favicon y el badge monocromo del push web.
- `shared/src/commonMain/kotlin/com/jvillada/movi/theme/LaMDeMovi.kt`: la misma `pathData` como
  `ImageVector`, para la marca dentro de la app (también en iOS, que todavía no tiene las fuentes).

Los colores son los de `Tokens.kt` (`marca` y `sobreMarca`); `ElIconoDeMoviTest` vigila que los
recursos no se separen de ahí.
"""
import argparse
import os
import sys

from fontTools.pens.basePen import BasePen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.ttLib import TTFont
from PIL import Image, ImageChops, ImageDraw, ImageFont

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FUENTES = os.path.join(RAIZ, "shared/src/commonMain/composeResources/font")
TTF = os.path.join(FUENTES, "figtree_extrabold.ttf")
RES = os.path.join(RAIZ, "androidApp/src/main/res")
WEB = os.path.join(RAIZ, "webApp/src/wasmJsMain/resources/icons")
KOTLIN = os.path.join(RAIZ, "shared/src/commonMain/kotlin/com/jvillada/movi/theme/LaMDeMovi.kt")

MARCA = (0xFF, 0xC2, 0x1A)   # Tokens.kt · marca
TINTA = (0x1F, 0x1B, 0x16)   # Tokens.kt · sobreMarca / texto
CREMA = (0xF4, 0xF1, 0xEC)   # Tokens.kt · fondo
BLANCO = (0xFF, 0xFF, 0xFF)

# Las proporciones de la opción A (dibujada a 512 px con la letra a 330 px y 10 px hacia arriba):
# respecto del lado VISIBLE del ícono, el em mide 330/512 y el centro sube 10/512.
EM_SOBRE_VISIBLE = 330 / 512
SUBIDA_SOBRE_VISIBLE = 10 / 512
RADIO_DEL_CUADRADO = 0.23    # el cuadrado redondeado de la referencia

# El ícono adaptable de Android: capa de 108, lo visible son los 72 del centro, la zona segura un
# círculo de 66.
CAPA = 108.0
VISIBLE = 72.0

# ── El glifo ────────────────────────────────────────────────────────────────────────────────────


class _Grabador(BasePen):
    """Guarda el contorno como segmentos (BasePen descompone las curvas cuadráticas implícitas)."""

    def __init__(self, glyphset):
        super().__init__(glyphset)
        self.contornos = []

    def _moveTo(self, p):
        self.contornos.append([("M", [p])])

    def _lineTo(self, p):
        self.contornos[-1].append(("L", [p]))

    def _qCurveToOne(self, p1, p2):
        self.contornos[-1].append(("Q", [p1, p2]))

    def _curveToOne(self, p1, p2, p3):
        self.contornos[-1].append(("C", [p1, p2, p3]))

    def _closePath(self):
        self.contornos[-1].append(("Z", []))

    _endPath = _closePath


def _leer_glifo():
    fuente = TTFont(TTF)
    glifos = fuente.getGlyphSet()
    nombre = fuente.getBestCmap()[ord("m")]
    grabador = _Grabador(glifos)
    glifos[nombre].draw(grabador)
    limites = BoundsPen(glifos)
    glifos[nombre].draw(limites)
    return grabador.contornos, limites.bounds, fuente["head"].unitsPerEm


CONTORNOS, (X0, Y0, X1, Y1), UPM = _leer_glifo()
ANCHO_DEL_GLIFO = X1 - X0


def _transformar(cx, cy, escala):
    """Unidades de la fuente (y hacia arriba) → pantalla (y hacia abajo), centrado en (cx, cy)."""
    mx, my = (X0 + X1) / 2, (Y0 + Y1) / 2
    return lambda p: (cx + (p[0] - mx) * escala, cy - (p[1] - my) * escala)


def colocacion_visible(lado, centro):
    """Como en la opción A: la «m» respecto de un lado visible [lado] centrado en [centro]."""
    escala = EM_SOBRE_VISIBLE * lado / UPM
    return _transformar(centro, centro - SUBIDA_SOBRE_VISIBLE * lado, escala)


def colocacion_por_ancho(ancho, cx, cy):
    """La «m» de [ancho] de punta a punta, centrada en (cx, cy) (ícono chico y badge)."""
    return _transformar(cx, cy, ancho / ANCHO_DEL_GLIFO)


def _num(v):
    t = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if t in ("-0", "") else t


def path_data(t):
    partes = []
    for contorno in CONTORNOS:
        for op, puntos in contorno:
            pts = [t(p) for p in puntos]
            partes.append(op + " ".join(f"{_num(x)},{_num(y)}" for x, y in pts))
    return "".join(partes)


def _poligonos(t, pasos=16):
    poligonos = []
    for contorno in CONTORNOS:
        actual, pol = None, []
        for op, puntos in contorno:
            if op == "M":
                actual = puntos[0]
                pol = [t(actual)]
            elif op == "L":
                actual = puntos[0]
                pol.append(t(actual))
            elif op == "Q":
                (x0, y0), (x1, y1), (x2, y2) = actual, puntos[0], puntos[1]
                for i in range(1, pasos + 1):
                    u = i / pasos
                    a, b, c = (1 - u) ** 2, 2 * (1 - u) * u, u * u
                    pol.append(t((a * x0 + b * x1 + c * x2, a * y0 + b * y1 + c * y2)))
                actual = puntos[1]
            elif op == "C":
                (x0, y0), (x1, y1), (x2, y2), (x3, y3) = actual, *puntos
                for i in range(1, pasos + 1):
                    u = i / pasos
                    a, b, c, d = (1 - u) ** 3, 3 * (1 - u) ** 2 * u, 3 * (1 - u) * u * u, u ** 3
                    pol.append(t((a * x0 + b * x1 + c * x2 + d * x3, a * y0 + b * y1 + c * y2 + d * y3)))
                actual = puntos[2]
        if pol:
            poligonos.append(pol)
    return poligonos


# ── Pintar ──────────────────────────────────────────────────────────────────────────────────────

SS = 8  # sobremuestreo: se pinta grande y se achica con LANCZOS


def _mascara_del_glifo(lado, t_grande):
    mascara = Image.new("L", (lado * SS, lado * SS), 0)
    for pol in _poligonos(t_grande):
        capa = Image.new("L", mascara.size, 0)
        ImageDraw.Draw(capa).polygon(pol, fill=255)
        mascara = ImageChops.logical_xor(mascara.convert("1"), capa.convert("1")).convert("L")  # regla par-impar
    return mascara


def _mascara_de_forma(lado, forma):
    g = lado * SS
    mascara = Image.new("L", (g, g), 0)
    d = ImageDraw.Draw(mascara)
    if forma == "cuadrado":
        d.rectangle([0, 0, g, g], fill=255)
    elif forma == "redondeado":
        d.rounded_rectangle([0, 0, g - 1, g - 1], radius=int(g * RADIO_DEL_CUADRADO), fill=255)
    elif forma == "circulo":
        d.ellipse([0, 0, g - 1, g - 1], fill=255)
    return mascara


def pintar(lado, forma, fondo, letra, colocar, fondo_lienzo=None):
    """Un ícono de [lado] px: [forma] en [fondo] (o nada) con la «m» en [letra].

    [colocar] recibe el lado sobremuestreado y devuelve la transformación del glifo.
    Con [fondo_lienzo] el PNG sale opaco (sin alfa), como lo pide el apple-touch-icon.

    El color y el alfa se arman por separado: fuera de la forma el RGB sigue siendo el de adentro, así
    que al achicar no aparece un borde oscuro, y la «m» sola (el badge) es blanca en todos sus píxeles.
    """
    g = lado * SS
    caja = (0, 0, g, g)
    color = Image.new("RGB", (g, g), fondo_lienzo or fondo or letra)
    forma_ = _mascara_de_forma(lado, forma) if forma else None
    if forma and fondo:
        color.paste(fondo, caja, forma_)
    glifo = _mascara_del_glifo(lado, colocar(g))
    color.paste(letra, caja, glifo)
    if fondo_lienzo:
        return color.resize((lado, lado), Image.LANCZOS)
    color.putalpha(forma_ if forma else glifo)
    return color.resize((lado, lado), Image.LANCZOS)


def visible(fraccion):
    """La «m» respecto de un lado visible que ocupa [fraccion] del lienzo, centrada."""
    return lambda g: colocacion_visible(g * fraccion, g / 2)


def por_ancho(fraccion):
    return lambda g: colocacion_por_ancho(g * fraccion, g / 2, g / 2)


# ── Escribir ────────────────────────────────────────────────────────────────────────────────────

ENCABEZADO_XML = "<!-- Generado por scripts/generar-icono.py: no se edita a mano, se corre el script. -->"


def _escribir(ruta, texto):
    with open(ruta, "w", encoding="utf-8") as f:
        f.write(texto)
    print("  ", os.path.relpath(ruta, RAIZ))


def _guardar(imagen, ruta):
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    imagen.save(ruta, optimize=True)
    print("  ", os.path.relpath(ruta, RAIZ))


PATH_108 = path_data(colocacion_visible(VISIBLE, CAPA / 2))
# El ícono chico: la «m» de 20 de ancho en 24, con un dp de aire a cada lado (la zona viva de Material).
PATH_24 = path_data(colocacion_por_ancho(20.0, 12.0, 12.0))


def escribir_android():
    _escribir(os.path.join(RES, "drawable/ic_launcher_foreground.xml"), f"""<?xml version="1.0" encoding="utf-8"?>
{ENCABEZADO_XML}
<!--
    El primer plano del ícono de Movi (vuelta 65): la «m» de Figtree ExtraBold en la tinta, centrada
    en la capa de 108 y dentro de la zona segura de 66. El fondo es `@color/movi_marca`. También es la
    capa `monochrome` del ícono temático de Android 13: ahí el sistema usa solo su forma.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@color/movi_tinta"
        android:pathData="{PATH_108}" />
</vector>
""")
    _escribir(os.path.join(RES, "drawable/ic_stat_movi.xml"), f"""<?xml version="1.0" encoding="utf-8"?>
{ENCABEZADO_XML}
<!--
    El ícono chico de los avisos de Movi (vuelta 65): la misma «m» del ícono, en blanco como lo pide
    Android para la barra de estado (solo cuenta el alfa; el color lo pone el sistema). El acento de
    la notificación es `@color/movi_tinta` (ver Avisador.publicar).
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{PATH_24}" />
</vector>
""")
    # El respaldo de API 24-25: los tamaños de siempre (48 dp por densidad).
    for carpeta, lado in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        _guardar(pintar(lado, "redondeado", MARCA, TINTA, visible(1.0)), os.path.join(RES, f"mipmap-{carpeta}/ic_launcher.png"))
        _guardar(pintar(lado, "circulo", MARCA, TINTA, visible(1.0)), os.path.join(RES, f"mipmap-{carpeta}/ic_launcher_round.png"))


def escribir_web():
    # Maskable: a sangre, la «m» respecto del 80 % del centro (la zona segura de la especificación).
    for lado in (192, 512):
        _guardar(pintar(lado, "cuadrado", MARCA, TINTA, visible(0.8)), os.path.join(WEB, f"movi-{lado}.png"))
        _guardar(pintar(lado, "redondeado", MARCA, TINTA, visible(1.0)), os.path.join(WEB, f"movi-redondeado-{lado}.png"))
    # iOS redondea solo y no admite transparencia: a sangre y opaco.
    _guardar(pintar(180, "cuadrado", MARCA, TINTA, visible(1.0), fondo_lienzo=MARCA), os.path.join(WEB, "apple-touch-icon.png"))
    _guardar(pintar(48, "redondeado", MARCA, TINTA, visible(1.0)), os.path.join(WEB, "favicon.png"))
    # El badge del push web: monocromo (Android usa solo el alfa), blanco sobre transparente.
    _guardar(pintar(96, None, None, BLANCO, por_ancho(20 / 24)), os.path.join(WEB, "movi-badge.png"))


def escribir_kotlin():
    _escribir(KOTLIN, f"""// Generado por scripts/generar-icono.py: no se edita a mano, se corre el script.
package com.jvillada.movi.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * **La «m» del ícono de Movi** (vuelta 65): el contorno de la «m» de Figtree ExtraBold en la capa de
 * 108 del ícono adaptable de Android, el MISMO `pathData` de `ic_launcher_foreground.xml`
 * (`ElIconoDeMoviTest` los compara).
 */
const val PATH_DE_LA_M_DE_MOVI: String =
    "{PATH_108}"

/**
 * La «m» como `ImageVector`, recortada a lo visible del ícono (los 72 del centro): pintada del tamaño
 * de un cuadrado amarillo queda como el ícono de la app. Es un vector y no texto, así que en iOS —que
 * todavía no tiene las fuentes propias— también es la «m» de Figtree.
 */
val LaMDeMovi: ImageVector by lazy {{
    ImageVector.Builder(
        name = "LaMDeMovi",
        defaultWidth = {int(VISIBLE)}.dp,
        defaultHeight = {int(VISIBLE)}.dp,
        viewportWidth = {VISIBLE}f,
        viewportHeight = {VISIBLE}f,
    )
        .addGroup(translationX = -{(CAPA - VISIBLE) / 2}f, translationY = -{(CAPA - VISIBLE) / 2}f)
        .addPath(pathData = addPathNodes(PATH_DE_LA_M_DE_MOVI), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()
}}
""")


# ── Capturas para mostrarle el ícono al dueño ───────────────────────────────────────────────────


def _letra(peso, tam):
    return ImageFont.truetype(os.path.join(FUENTES, f"figtree_{peso}.ttf"), tam)


def capturas(directorio):
    os.makedirs(directorio, exist_ok=True)
    lado, margen, rotulo = 240, 48, 70
    fichas = [
        ("Círculo", pintar(lado, "circulo", MARCA, TINTA, visible(1.0))),
        ("Cuadrado redondeado", pintar(lado, "redondeado", MARCA, TINTA, visible(1.0))),
        ("Monocromo · claro", pintar(lado, "circulo", (0xE3, 0xE8, 0xD6), (0x2E, 0x3A, 0x1F), visible(1.0))),
        ("Monocromo · oscuro", pintar(lado, "circulo", (0x2E, 0x3A, 0x1F), (0xC9, 0xD6, 0xB0), visible(1.0))),
    ]
    hoja = Image.new("RGB", (len(fichas) * (lado + margen) + margen, lado + 2 * margen + rotulo), CREMA)
    d = ImageDraw.Draw(hoja)
    f = _letra("semibold", 24)
    for i, (nombre, img) in enumerate(fichas):
        x = margen + i * (lado + margen)
        hoja.paste(img, (x, margen), img)
        w = d.textlength(nombre, font=f)
        d.text((x + (lado - w) / 2, margen + lado + 22), nombre, font=f, fill=TINTA)
    _guardar(hoja, os.path.join(directorio, "icono-adaptable.png"))

    # El ícono chico, a 3× (un dp = 3 px): en la barra de estado y en la cabecera de la notificación,
    # con la cortina clara y la oscura. La cabecera de Android 12+ pinta el círculo con el acento
    # (`setColor`, la tinta) y la «m» en blanco encima.
    escala, ancho, alto = 3, 540, 420
    hoja = Image.new("RGB", (2 * ancho, alto), CREMA)
    for i, (titulo, cortina, tarjeta, texto, medio, barra) in enumerate((
        ("Cortina clara", (0xE9, 0xE6, 0xE1), BLANCO, TINTA, (0x5C, 0x55, 0x4C), TINTA),
        ("Cortina oscura", (0x12, 0x12, 0x12), (0x2A, 0x2A, 0x2A), (0xF1, 0xF1, 0xF1), (0xB8, 0xB8, 0xB8), BLANCO),
    )):
        panel = Image.new("RGB", (ancho, alto), cortina)
        d = ImageDraw.Draw(panel)
        d.text((24, 24), "9:41", font=_letra("semibold", 36), fill=barra)
        icono = pintar(18 * escala, None, None, barra, por_ancho(20 / 24))  # la barra lo pinta a ~18 dp
        panel.paste(icono, (116, 20), icono)
        d.text((ancho - 24 - d.textlength(titulo, font=_letra("medium", 22)), 26), titulo, font=_letra("medium", 22), fill=medio)
        d.rounded_rectangle([20, 110, ancho - 20, 360], radius=36, fill=tarjeta)
        circulo = 26 * escala
        cabecera = pintar(circulo, "circulo", TINTA, BLANCO, por_ancho(0.58))
        panel.paste(cabecera, (44, 134), cabecera)
        d.text((44 + circulo + 18, 150), "Movi · ahora", font=_letra("medium", 26), fill=medio)
        d.text((44, 238), "Rappi · $27.500", font=_letra("bold", 32), fill=texto)
        d.text((44, 288), "Restaurantes · Nu", font=_letra("regular", 28), fill=medio)
        hoja.paste(panel, (i * ancho, 0))
    _guardar(hoja, os.path.join(directorio, "icono-notificacion.png"))

    # La web: el maskable recortado como lo recortan Android (círculo) y el resto, el favicon y el badge.
    piezas = [
        ("PWA maskable · círculo", _recortar(Image.open(os.path.join(WEB, "movi-512.png")).convert("RGBA").resize((lado, lado), Image.LANCZOS), "circulo")),
        ("apple-touch-icon", _recortar(Image.open(os.path.join(WEB, "apple-touch-icon.png")).convert("RGBA").resize((lado, lado), Image.LANCZOS), "redondeado")),
        ("favicon (48)", Image.open(os.path.join(WEB, "favicon.png")).convert("RGBA").resize((lado, lado), Image.NEAREST)),
        ("Badge del push", _sobre(Image.open(os.path.join(WEB, "movi-badge.png")).convert("RGBA").resize((lado, lado), Image.LANCZOS), TINTA)),
    ]
    hoja = Image.new("RGB", (len(piezas) * (lado + margen) + margen, lado + 2 * margen + rotulo), CREMA)
    d = ImageDraw.Draw(hoja)
    for i, (nombre, img) in enumerate(piezas):
        x = margen + i * (lado + margen)
        hoja.paste(img, (x, margen), img)
        w = d.textlength(nombre, font=f)
        d.text((x + (lado - w) / 2, margen + lado + 22), nombre, font=f, fill=TINTA)
    _guardar(hoja, os.path.join(directorio, "icono-web.png"))


def _recortar(img, forma):
    mascara = _mascara_de_forma(img.width, forma).resize(img.size, Image.LANCZOS)
    fuera = img.copy()
    fuera.putalpha(ImageChops.multiply(img.getchannel("A"), mascara))
    return fuera


def _sobre(img, fondo):
    base = _recortar(Image.new("RGBA", img.size, (*fondo, 255)), "redondeado")
    base.alpha_composite(img)
    return base


def main():
    p = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    p.add_argument("--capturas", metavar="DIR", help="además, deja en DIR las capturas del ícono")
    args = p.parse_args()
    if not os.path.exists(TTF):
        sys.exit(f"No encuentro {TTF}")
    print("Android:")
    escribir_android()
    print("Web:")
    escribir_web()
    print("Kotlin:")
    escribir_kotlin()
    if args.capturas:
        print("Capturas:")
        capturas(args.capturas)


if __name__ == "__main__":
    main()
