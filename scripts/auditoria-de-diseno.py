#!/usr/bin/env python3
"""
Auditoría del sistema de diseño de Movi (ola R0 del rediseño, 26-oct-2026).

Recorre `shared/src/commonMain/kotlin/com/jvillada/movi/ui/` y cuenta, con números, lo que cada
pantalla decide por su cuenta: valores de `.dp` por contexto, radios, tamaños de letra, colores
literales y las variantes de tarjeta, fila, botón, chip y encabezado de sección.

Uso (desde la raíz del repo):

    python3 scripts/auditoria-de-diseno.py            # el informe en Markdown, por stdout
    python3 scripts/auditoria-de-diseno.py --resumen  # solo los totales que vigilan las guardas

No escribe nada. El informe de `docs/diseno/auditoria-2026-10.md` salió de acá; las olas siguientes
lo corren de nuevo para ver cuánto bajó cada número.

Los conteos de `.dp`, `RoundedCornerShape(N` y `Color(0x` usan la MISMA regla que las guardas de
`core/src/jvmTest/.../quality/` (comentarios fuera, `theme/` y `ui/components/ds/` fuera), así que el
resumen coincide con sus líneas base.
"""
import collections
import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UI = os.path.join(RAIZ, "shared/src/commonMain/kotlin/com/jvillada/movi/ui")
EXCLUIDOS = ("components/ds/",)

DP = re.compile(r"(?<![\w.])(\d+(?:\.\d+)?)\.dp\b")
RADIO = re.compile(r"RoundedCornerShape\(\s*([^)]*)\)")
FONT_SIZE = re.compile(r"fontSize\s*=\s*([0-9]+(?:\.[0-9]+)?)\.sp")
TEXT_STYLE = re.compile(r"(?<![\w.])TextStyle\(")
COLOR = re.compile(r"Color\(0x([0-9A-Fa-f]{6,8})\)")


def sin_comentarios(linea, en_bloque):
    """La línea sin comentarios `//` ni `/* */`. Devuelve (texto, sigue_en_bloque)."""
    salida = []
    i = 0
    en_cadena = False
    while i < len(linea):
        if en_bloque:
            fin = linea.find("*/", i)
            if fin < 0:
                return "".join(salida), True
            i = fin + 2
            en_bloque = False
            continue
        c = linea[i]
        if c == '"' and (i == 0 or linea[i - 1] != "\\"):
            en_cadena = not en_cadena
        if not en_cadena and linea.startswith("//", i):
            break
        if not en_cadena and linea.startswith("/*", i):
            en_bloque = True
            i += 2
            continue
        salida.append(c)
        i += 1
    return "".join(salida), en_bloque


def archivos():
    for base, _, nombres in os.walk(UI):
        for n in sorted(nombres):
            if n.endswith(".kt"):
                ruta = os.path.join(base, n)
                rel = os.path.relpath(ruta, UI)
                if any(rel.startswith(x) for x in EXCLUIDOS):
                    continue
                yield rel, ruta


def lineas_de_codigo(ruta):
    en_bloque = False
    with open(ruta, encoding="utf-8") as f:
        for n, linea in enumerate(f, 1):
            codigo, en_bloque = sin_comentarios(linea.rstrip("\n"), en_bloque)
            yield n, codigo


def contexto(codigo, pos):
    """La llamada más interna que contiene a `pos` en la misma línea: `padding`, `size`…"""
    profundidad = 0
    i = pos
    while i > 0:
        i -= 1
        c = codigo[i]
        if c == ")":
            profundidad += 1
        elif c == "(":
            if profundidad == 0:
                m = re.search(r"([A-Za-z_]\w*)\s*$", codigo[:i])
                nombre = m.group(1) if m else ""
                # `padding(vertical = 4.dp)`: el nombre del argumento no es la llamada.
                return nombre
            profundidad -= 1
    m = re.search(r"(\w+)\s*=\s*[^=]*$", codigo[:pos])
    return m.group(1) if m else ""


def clase(nombre, codigo):
    n = nombre.lower()
    if n in ("padding", "contentpadding", "paddingvalues", "windowinsetspadding", "absolutepadding") or "padding" in n:
        return "padding"
    if n in ("spacedby", "spacer") or "spacing" in n or "arrangement" in n:
        return "separación"
    if n in ("roundedcornershape", "cornerradius", "clip") or "radius" in n or "corner" in n:
        return "radio"
    if n in ("size", "height", "width", "heightin", "widthin", "sizein", "requiredsize",
             "requiredheight", "requiredwidth", "defaultminsize", "fillmaxsize", "min", "max",
             "minsize", "iconsize"):
        return "tamaño"
    if n in ("border", "thickness", "strokewidth", "stroke") or "border" in n:
        return "borde"
    if n in ("shadow", "elevation", "tonalelevation", "shadowelevation"):
        return "elevación"
    if n in ("offset", "absoluteoffset", "dpoffset", "x", "y"):
        return "desplazamiento"
    if "Spacer(" in codigo:
        return "separación"
    return "otro"


# La misma regla que LoQueNoEsDelSistemaSoloBajaTest (vuelta de diseño 18): sobre el archivo entero
# sin comentarios, para que `Text(\n text = formatCOP(…)` cuente aunque baje de renglón.
NO_ES_DEL_SISTEMA = {
    "botones_de_material": re.compile(r"(?<![\w.])(?:Button|OutlinedButton|TextButton)\("),
    "blanco_o_negro": re.compile(r"\bColor\.(?:White|Black)\b"),
    "hairline": re.compile(r"(?<![\w.])Hairline\("),
    "mincard": re.compile(r"(?<![\w.])MinCard\("),
    "cifra_en_text": re.compile(r"(?<![\w.])Text\(\s*(?:text\s*=\s*)?(?:formatCOP|formatMoney)\("),
    "font_weight": re.compile(r"\bfontWeight\s*="),
}
# Por clave, los archivos (relativos a ui/) que no se cuentan. El velo de una hoja es negro con alfa.
EXCEPCIONES_DEL_SISTEMA = {"blanco_o_negro": {"components/MarcoDeHoja.kt"}}


def lo_que_no_es_del_sistema():
    cuentas = {clave: 0 for clave in NO_ES_DEL_SISTEMA}
    for rel, ruta in archivos():
        texto = "\n".join(codigo for _, codigo in lineas_de_codigo(ruta))
        for clave, regex in NO_ES_DEL_SISTEMA.items():
            if rel in EXCEPCIONES_DEL_SISTEMA.get(clave, ()):
                continue
            for m in regex.finditer(texto):
                if re.search(r"fun\s+$", texto[max(0, m.start() - 8): m.start()]):
                    continue  # la definición de la función no cuenta
                cuentas[clave] += 1
    return cuentas


def main():
    solo_resumen = "--resumen" in sys.argv

    dp_por_valor = collections.Counter()
    dp_por_clase = collections.Counter()
    dp_por_clase_valor = collections.defaultdict(collections.Counter)
    dp_por_archivo = collections.Counter()
    radios = collections.Counter()
    radios_literales = 0
    radios_donde = collections.defaultdict(list)
    font_sizes = collections.Counter()
    font_donde = []
    text_styles = []
    colores = []
    componentes = collections.defaultdict(collections.Counter)
    minCard_padding = collections.defaultdict(list)
    fila_vertical = collections.defaultdict(list)
    alto_de_fila = collections.defaultdict(list)
    rotulos_a_mano = []
    botones_a_mano = []
    chips_a_mano = []
    tarjetas_a_mano = []
    total_lineas = 0
    total_archivos = 0

    nombres_de_componentes = {
        "tarjeta": ["MinCard", "VacioQueEnsena", "PanelDeTablero"],
        "fila": ["CardRow", "FilaDeMovimiento", "FilaDePago", "FilaDeCuenta", "FilaDeCategoria", "ListItem"],
        "botón": ["NewItemButton", "Button", "TextButton", "OutlinedButton", "FilledTonalButton", "IconButton"],
        "chip": ["FilterChip", "AssistChip", "SuggestionChip", "InputChip", "PastillasDeCategoria", "SelectorSegmentado", "ScopeToggle"],
        "encabezado de sección": ["MinSectionHeader"],
        "encabezado de pantalla": ["MinScreenHeader"],
    }

    for rel, ruta in archivos():
        total_archivos += 1
        pantalla = rel.split("/")[0] if "/" in rel else rel
        codigo_entero = []
        for n, codigo in lineas_de_codigo(ruta):
            total_lineas += 1
            codigo_entero.append((n, codigo))
            for m in DP.finditer(codigo):
                valor = float(m.group(1))
                valor = int(valor) if valor.is_integer() else valor
                k = clase(contexto(codigo, m.start()), codigo)
                dp_por_valor[valor] += 1
                dp_por_clase[k] += 1
                dp_por_clase_valor[k][valor] += 1
                dp_por_archivo[rel] += 1
            for m in RADIO.finditer(codigo):
                arg = m.group(1).strip()
                lit = re.match(r"^(\d+(?:\.\d+)?)\.dp$", arg) or re.match(r"^(\d+(?:\.\d+)?)$", arg)
                if lit:
                    radios_literales += 1
                    v = float(lit.group(1))
                    clave = f"{int(v) if v.is_integer() else v} dp (literal)"
                    radios_donde[clave].append(f"{rel}:{n}")
                elif "formas." in arg:
                    clave = "Movi.formas." + arg.split("formas.")[1].split(")")[0].split(",")[0]
                elif "percent" in arg or "50" in arg:
                    clave = arg
                else:
                    clave = "otro: " + arg[:40]
                radios[clave] += 1
            for m in FONT_SIZE.finditer(codigo):
                font_sizes[m.group(1)] += 1
                font_donde.append(f"{rel}:{n}")
            if TEXT_STYLE.search(codigo):
                text_styles.append(f"{rel}:{n}")
            for m in COLOR.finditer(codigo):
                colores.append((f"{rel}:{n}", m.group(1).upper()))
            for tipo, nombres in nombres_de_componentes.items():
                for nombre in nombres:
                    for _ in re.finditer(r"(?<![\w.])" + nombre + r"\(", codigo):
                        if re.search(r"fun\s+" + nombre + r"\(", codigo):
                            continue
                        componentes[(tipo, nombre)][pantalla] += 1
            if ".uppercase()" in codigo:
                rotulos_a_mano.append(f"{rel}:{n}")

        texto = "\n".join(c for _, c in codigo_entero)
        # MinCard(… padding = PaddingValues(…)) y CardRow: qué padding le pone cada llamada.
        for m in re.finditer(r"(?<![\w.])MinCard\(", texto):
            ventana = texto[m.end(): m.end() + 400]
            cierre = ventana.find(") {")
            argumentos = ventana[: cierre if cierre >= 0 else 400]
            linea = texto[: m.start()].count("\n") + 1
            n_real = codigo_entero[linea - 1][0]
            p = re.search(r"padding\s*=\s*(PaddingValues\([^)]*\))", argumentos)
            minCard_padding[p.group(1) if p else "default (Movi.espacios.margen = 20 dp)"].append(f"{rel}:{n_real}")
        # Filas a mano: un Row clickable con padding vertical literal.
        for m in re.finditer(r"Row\(\s*\n?\s*modifier\s*=\s*Modifier", texto):
            ventana = texto[m.end(): m.end() + 600]
            fin = ventana.find(") {")
            mods = ventana[: fin if fin >= 0 else 600]
            if "clickable" not in mods:
                continue
            linea = texto[: m.start()].count("\n") + 1
            n_real = codigo_entero[linea - 1][0]
            v = re.search(r"padding\(\s*(?:horizontal\s*=\s*[^,]+,\s*)?vertical\s*=\s*(\d+(?:\.\d+)?)\.dp", mods)
            if v:
                fila_vertical[v.group(1) + " dp"].append(f"{rel}:{n_real}")
            h = re.search(r"(?:heightIn\(\s*min\s*=\s*|height\()(\d+)\.dp", mods)
            if h:
                alto_de_fila[h.group(1) + " dp"].append(f"{rel}:{n_real}")
            if "Movi.colores.marca)" in mods and "background" in mods:
                botones_a_mano.append(f"{rel}:{n_real}")
        # Tarjetas a mano: clip(RoundedCornerShape) + background(tarjeta) sin MinCard.
        for m in re.finditer(r"\.background\(\s*Movi\.colores\.tarjeta", texto):
            antes = texto[max(0, m.start() - 250): m.start()]
            if "RoundedCornerShape" in antes or "clip(" in antes:
                linea = texto[: m.start()].count("\n") + 1
                tarjetas_a_mano.append(f"{rel}:{codigo_entero[linea - 1][0]}")
        for m in re.finditer(r"RoundedCornerShape\(\s*(?:Movi\.formas\.pleno|999\.dp)\s*\)\)\s*\n?\s*\.background\(", texto):
            linea = texto[: m.start()].count("\n") + 1
            chips_a_mano.append(f"{rel}:{codigo_entero[linea - 1][0]}")

    total_dp = sum(dp_por_valor.values())
    total_colores = len(colores)
    if solo_resumen:
        # La misma regla que LosValoresSueltosSoloBajanTest, para que los números coincidan con
        # docs/diseno/lineas-base.txt.
        radio_guarda = re.compile(r"RoundedCornerShape\(\s*(?:\w+\s*=\s*)?\d")
        radios_guarda = 0
        for _, ruta in archivos():
            for _, codigo in lineas_de_codigo(ruta):
                radios_guarda += len(radio_guarda.findall(codigo))
        colores_guarda = 0
        common = os.path.dirname(UI)
        for base, _, nombres in os.walk(common):
            if os.path.relpath(base, common).split(os.sep)[0] == "theme":
                continue
            for n in nombres:
                if n.endswith(".kt"):
                    for _, codigo in lineas_de_codigo(os.path.join(base, n)):
                        colores_guarda += len(COLOR.findall(codigo))
        print(f"dp_sueltos = {total_dp}")
        print(f"radios_con_numero = {radios_guarda}")
        print(f"colores_literales = {colores_guarda}")
        print(f"# fontSize sueltos (LosTamanosSueltosSoloBajanTest): {sum(font_sizes.values())}")
        print("# Lo que no es del sistema (LoQueNoEsDelSistemaSoloBajaTest, vuelta de diseño 18):")
        for clave, n in lo_que_no_es_del_sistema().items():
            print(f"{clave} = {n}")
        return

    out = []
    w = out.append
    w(f"_Generado con `python3 scripts/auditoria-de-diseno.py` sobre {total_archivos} archivos y "
      f"{total_lineas} líneas de `ui/` (sin `ui/components/ds/`, sin comentarios)._\n")
    w("## 1. Los `.dp` sueltos\n")
    w(f"**{total_dp}** literales `N.dp` con **{len(dp_por_valor)} valores distintos**.\n")
    w("### Por contexto\n")
    w("| contexto | cuántos | valores distintos | los 8 más usados |")
    w("|---|---:|---:|---|")
    for k, c in dp_por_clase.most_common():
        top = ", ".join(f"{v} ({n})" for v, n in dp_por_clase_valor[k].most_common(8))
        w(f"| {k} | {c} | {len(dp_por_clase_valor[k])} | {top} |")
    w("")
    w("### Histograma por valor\n")
    w("| dp | cuántos | | dp | cuántos | | dp | cuántos |")
    w("|---:|---:|---|---:|---:|---|---:|---:|")
    filas = sorted(dp_por_valor.items(), key=lambda kv: float(kv[0]))
    tercio = (len(filas) + 2) // 3
    for i in range(tercio):
        celdas = []
        for j in range(3):
            k = i + j * tercio
            if k < len(filas):
                celdas.append(f"{filas[k][0]} | {filas[k][1]}")
            else:
                celdas.append(" | ")
        w("| " + " | | ".join(celdas) + " |")
    w("")
    w("### Los archivos con más `.dp` sueltos\n")
    w("| archivo | `.dp` |")
    w("|---|---:|")
    for a, c in dp_por_archivo.most_common(15):
        w(f"| `{a}` | {c} |")
    w("")

    w("## 2. Los radios (`RoundedCornerShape`)\n")
    w(f"**{sum(radios.values())}** llamadas; **{radios_literales}** con un número escrito a mano.\n")
    w("| radio | cuántos |")
    w("|---|---:|")
    for k, c in radios.most_common():
        w(f"| `{k}` | {c} |")
    w("")
    w("Los literales, dónde:\n")
    for k, donde in sorted(radios_donde.items(), key=lambda kv: -len(kv[1])):
        muestra = ", ".join(f"`{d}`" for d in donde[:6])
        mas = f" y {len(donde) - 6} más" if len(donde) > 6 else ""
        w(f"- **{k}** ({len(donde)}): {muestra}{mas}")
    w("")

    w("## 3. Tamaños de letra sueltos\n")
    w(f"- `fontSize = N.sp`: **{sum(font_sizes.values())}** ("
      + ", ".join(f"{k} sp × {v}" for k, v in sorted(font_sizes.items(), key=lambda kv: float(kv[0]))) + ")")
    for d in font_donde:
        w(f"  - `{d}`")
    w(f"- `TextStyle(` armado en una pantalla: **{len(text_styles)}**")
    for d in text_styles:
        w(f"  - `{d}`")
    w("")

    w("## 4. Colores literales fuera de `theme/`\n")
    w(f"**{total_colores}** `Color(0x…)`:\n")
    for d, c in colores:
        w(f"- `{d}` → `#{c}`")
    w("")

    w("## 5. Las variantes de cada pieza, y dónde se usan\n")
    for tipo in nombres_de_componentes:
        filas_tipo = [(n, c) for (t, n), c in componentes.items() if t == tipo]
        if not filas_tipo:
            continue
        w(f"### {tipo.capitalize()}\n")
        w("| componente | llamadas | pantallas |")
        w("|---|---:|---|")
        for nombre, c in sorted(filas_tipo, key=lambda kv: -sum(kv[1].values())):
            pantallas = ", ".join(f"{p} ({n})" for p, n in c.most_common())
            w(f"| `{nombre}` | {sum(c.values())} | {pantallas} |")
        w("")
    w(f"Además, armados a mano (sin componente):\n")
    w(f"- **Tarjetas** (`clip(RoundedCornerShape…)` + `background(tarjeta)` sin `MinCard`): **{len(tarjetas_a_mano)}**")
    w(f"- **Píldoras/chips** (forma `pleno` + fondo propio): **{len(chips_a_mano)}**")
    w(f"- **Botones de marca** (`Row` tocable con fondo `marca`): **{len(botones_a_mano)}**")
    w(f"- **Rótulos en mayúsculas** (`.uppercase()` fuera de `MinSectionHeader`): **{len(rotulos_a_mano)}**")
    w("")

    w("## 6. Datos para la lista de inconsistencias\n")
    w("### El padding de `MinCard`, según la llamada\n")
    w("| padding | llamadas | dónde (muestra) |")
    w("|---|---:|---|")
    for k, donde in sorted(minCard_padding.items(), key=lambda kv: -len(kv[1])):
        w(f"| `{k}` | {len(donde)} | " + ", ".join(f"`{d}`" for d in donde[:4]) + " |")
    w("")
    w("### Filas tocables a mano: padding vertical\n")
    w("| padding vertical | filas | dónde (muestra) |")
    w("|---|---:|---|")
    for k, donde in sorted(fila_vertical.items(), key=lambda kv: float(kv[0].split()[0])):
        w(f"| {k} | {len(donde)} | " + ", ".join(f"`{d}`" for d in donde[:4]) + " |")
    w("")
    w("### Filas tocables a mano: alto fijo o mínimo\n")
    w("| alto | filas | dónde (muestra) |")
    w("|---|---:|---|")
    for k, donde in sorted(alto_de_fila.items(), key=lambda kv: float(kv[0].split()[0])):
        w(f"| {k} | {len(donde)} | " + ", ".join(f"`{d}`" for d in donde[:4]) + " |")
    w("")
    w("### Rótulos en mayúsculas a mano\n")
    for d in rotulos_a_mano:
        w(f"- `{d}`")
    w("")
    print("\n".join(out))


if __name__ == "__main__":
    main()
