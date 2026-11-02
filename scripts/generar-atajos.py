#!/usr/bin/env python3
"""
Vuelta 111 · los atajos del iPhone que le avisan a Movi.

Escribe el plist de cada atajo (legible, en scripts/atajos/), lo convierte al formato binario de un
.shortcut y lo firma con `shortcuts sign --mode anyone` (solo en una Mac: el server en Railway es Linux y
no puede firmar). Los firmados van a server/src/main/resources/atajos/ (no a static/, que está en
.gitignore porque ahí copia la web el armado), y el server los sirve en /atajos/<nombre>.shortcut.

    python3 scripts/generar-atajos.py                       # producción
    python3 scripts/generar-atajos.py --url http://192.168.1.20:8080 --salida /tmp/atajos-dev
    python3 scripts/generar-atajos.py --sin-firmar          # solo los plist (cualquier sistema)

El formato del plist no lo documenta Apple. Las claves salen de lo que la comunidad sacó de atajos
exportados, en particular del compilador Cherri (github.com/electrikmilk/cherri: shortcut.go,
shortcutgen.go y actions/web.cherri, que define «Obtener contenido de URL» como
`is.workflow.actions.downloadurl` con WFURL, WFHTTPMethod, WFHTTPBodyType «JSON», WFJSONValues y
WFHTTPHeaders). Lo que se verifica acá: que `plutil -lint` lo acepte, que lleve la URL y la cabecera, y que
`shortcuts sign` lo firme. Lo que no se puede verificar en esta Mac (el disparador «Mensaje» y
«Transacción» solo existen en el iPhone) queda en docs/lanzamiento/atajos-de-iphone.md para probarlo
ahí.

**Qué se firma y qué no:**
- «Movi · Avisar un mensaje» se firma y se sirve: solo usa la entrada del atajo como texto (el contenido
  del mensaje que le pasa la automatización), un texto con el código y «Obtener contenido de URL».
- «Movi · Pago con Apple Pay» queda **sin firmar** y no se sirve: para mandar el comercio, el monto y la
  tarjeta tiene que leer propiedades de la transacción de Wallet, cuyos nombres internos no están
  documentados ni se pueden probar en una Mac. La app enseña a armarlo a mano (tres toques en el iPhone).
  Si algún día se verifican los nombres, se cambia FIRMAR_APPLE_PAY y se vuelve a correr.
"""

import argparse
import plistlib
import shutil
import subprocess
import sys
import tempfile
import uuid
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
URL_DE_PRODUCCION = "https://movi-project-production.up.railway.app"
RUTA_DE_LA_CAPTURA = "/api/captura/atajo"
SALIDA_FIRMADOS = RAIZ / "server/src/main/resources/atajos"
SALIDA_PLIST = RAIZ / "scripts/atajos"

FIRMAR_APPLE_PAY = False

# El carácter con que Atajos marca dónde va una variable dentro de un texto.
OBJ = "￼"

# iOS 17: el primero con el disparador «Transacción». Un iPhone más nuevo abre atajos de versiones viejas.
VERSION_DEL_CLIENTE = "2106.0.3"

# Todas las clases de entrada que conoce Cherri: el atajo acepta lo que le pase la automatización.
CLASES_DE_ENTRADA = sorted([
    "WFAppContentItem", "WFAppStoreAppContentItem", "WFArticleContentItem", "WFContactContentItem",
    "WFDateContentItem", "WFEmailAddressContentItem", "WFFolderContentItem", "WFGenericFileContentItem",
    "WFImageContentItem", "WFiTunesProductContentItem", "WFLocationContentItem", "WFDCMapsLinkContentItem",
    "WFAVAssetContentItem", "WFPDFContentItem", "WFPhoneNumberContentItem", "WFRichTextContentItem",
    "WFSafariWebPageContentItem", "WFStringContentItem", "WFDictionaryContentItem", "WFNumberContentItem",
    "WFURLContentItem",
])

PREGUNTA_DEL_CODIGO = "Pega tu código de Movi (en la app: Mensajes del banco → Desde tu iPhone)."


def texto_plano(cadena):
    return {"Value": {"string": cadena}, "WFSerializationType": "WFTextTokenString"}


def texto_con(cadena, adjuntos):
    """[cadena] con OBJ donde va cada adjunto, en orden. Las posiciones son en UTF-16, como las cuenta Atajos."""
    rangos = {}
    posicion = 0
    i = 0
    for caracter in cadena:
        if caracter == OBJ:
            rangos["{%d, 1}" % posicion] = adjuntos[i]
            i += 1
        posicion += 2 if ord(caracter) >= 0x10000 else 1
    assert i == len(adjuntos), "un adjunto por cada marca"
    return {"Value": {"string": cadena, "attachmentsByRange": rangos}, "WFSerializationType": "WFTextTokenString"}


def entrada_del_atajo(propiedad=None):
    """La entrada del atajo («Entrada del atajo»), o una propiedad suya."""
    valor = {"Type": "ExtensionInput"}
    if propiedad:
        valor["Aggrandizements"] = [{"Type": "WFPropertyVariableAggrandizement", "PropertyName": propiedad}]
    return valor


def salida_de(uuid_, nombre):
    return {"Type": "ActionOutput", "OutputUUID": uuid_, "OutputName": nombre}


def diccionario(pares):
    """Un diccionario de Atajos: cada valor es texto (WFItemType 0)."""
    return {
        "Value": {"WFDictionaryFieldValueItems": [
            {"WFItemType": 0, "WFKey": texto_plano(clave), "WFValue": valor} for clave, valor in pares
        ]},
        "WFSerializationType": "WFDictionaryFieldValue",
    }


def comentario(texto):
    return {"WFWorkflowActionIdentifier": "is.workflow.actions.comment",
            "WFWorkflowActionParameters": {"WFCommentActionText": texto}}


def accion_del_codigo(uuid_):
    # El texto va vacío: lo llena la pregunta de importación (WFWorkflowImportQuestions).
    return {"WFWorkflowActionIdentifier": "is.workflow.actions.gettext",
            "WFWorkflowActionParameters": {"UUID": uuid_, "CustomOutputName": "Código de Movi", "WFTextActionText": ""}}


def accion_del_envio(url, uuid_del_codigo, cuerpo):
    return {
        "WFWorkflowActionIdentifier": "is.workflow.actions.downloadurl",
        "WFWorkflowActionParameters": {
            "UUID": str(uuid.uuid5(uuid.NAMESPACE_URL, url + "#envio")).upper(),
            "WFURL": url,
            "WFHTTPMethod": "POST",
            "WFHTTPBodyType": "JSON",
            "ShowHeaders": True,
            "WFHTTPHeaders": diccionario([
                ("Authorization", texto_con("Bearer " + OBJ, [salida_de(uuid_del_codigo, "Código de Movi")])),
            ]),
            "WFJSONValues": diccionario(cuerpo),
        },
    }


def atajo(acciones, indice_del_codigo, sin_entrada_pregunta_texto):
    plist = {
        "WFWorkflowClientVersion": VERSION_DEL_CLIENTE,
        "WFWorkflowMinimumClientVersion": 900,
        "WFWorkflowMinimumClientVersionString": "900",
        # Un sobre (glifo) sobre el amarillo de Movi no existe en la paleta de Atajos: va el naranja.
        "WFWorkflowIcon": {"WFWorkflowIconStartColor": 4271458815, "WFWorkflowIconGlyphNumber": 59511},
        "WFWorkflowActions": acciones,
        "WFWorkflowImportQuestions": [{
            "ParameterKey": "WFTextActionText",
            "Category": "Parameter",
            "ActionIndex": indice_del_codigo,
            "Text": PREGUNTA_DEL_CODIGO,
            "DefaultValue": "",
        }],
        "WFWorkflowTypes": [],
        "WFQuickActionSurfaces": [],
        "WFWorkflowInputContentItemClasses": CLASES_DE_ENTRADA,
        "WFWorkflowOutputContentItemClasses": [],
        "WFWorkflowHasShortcutInputVariables": True,
        "WFWorkflowHasOutputFallback": False,
    }
    if sin_entrada_pregunta_texto:
        # «Probar»: si se corre a mano, sin automatización, pide el texto (se pega el de prueba de la app).
        plist["WFWorkflowNoInputBehavior"] = {
            "Name": "WFWorkflowNoInputBehaviorAskForInput",
            "Parameters": {"ItemClass": "WFStringContentItem"},
        }
    return plist


def atajo_de_mensajes(base):
    url = base.rstrip("/") + RUTA_DE_LA_CAPTURA
    codigo = str(uuid.uuid5(uuid.NAMESPACE_URL, url + "#codigo-mensaje")).upper()
    acciones = [
        comentario("Movi · Avisar un mensaje. Manda a Movi el SMS del banco que le pasa la automatización "
                   "«Mensaje». Movi solo guarda los mensajes de bancos: los demás los descarta sin guardarlos."),
        accion_del_codigo(codigo),
        accion_del_envio(url, codigo, [
            ("tipo", texto_plano("mensaje")),
            ("texto", texto_con(OBJ, [entrada_del_atajo()])),
        ]),
    ]
    return atajo(acciones, indice_del_codigo=1, sin_entrada_pregunta_texto=True)


def atajo_de_apple_pay(base):
    # BORRADOR SIN VERIFICAR: los nombres de las propiedades de la transacción de Wallet (Merchant, Amount,
    # Card or Pass) son los que muestra el editor en inglés, no los internos documentados. Ver el docstring.
    url = base.rstrip("/") + RUTA_DE_LA_CAPTURA
    codigo = str(uuid.uuid5(uuid.NAMESPACE_URL, url + "#codigo-apple-pay")).upper()
    acciones = [
        comentario("Movi · Pago con Apple Pay. Manda a Movi el comercio, el monto y la tarjeta que le pasa "
                   "la automatización «Transacción»."),
        accion_del_codigo(codigo),
        accion_del_envio(url, codigo, [
            ("tipo", texto_plano("transaccion")),
            ("comercio", texto_con(OBJ, [entrada_del_atajo("Merchant")])),
            ("monto", texto_con(OBJ, [entrada_del_atajo("Amount")])),
            ("tarjeta", texto_con(OBJ, [entrada_del_atajo("Card or Pass")])),
            ("texto", texto_con(OBJ, [entrada_del_atajo()])),
        ]),
    ]
    return atajo(acciones, indice_del_codigo=1, sin_entrada_pregunta_texto=False)


def verificar_plist(ruta, url):
    """plutil -lint, y que el plist lleve la URL de Movi y la cabecera con el código."""
    if shutil.which("plutil"):
        subprocess.run(["plutil", "-lint", str(ruta)], check=True, capture_output=True)
    with open(ruta, "rb") as f:
        datos = plistlib.load(f)
    envio = [a for a in datos["WFWorkflowActions"] if a["WFWorkflowActionIdentifier"] == "is.workflow.actions.downloadurl"]
    assert len(envio) == 1, "un solo envío"
    parametros = envio[0]["WFWorkflowActionParameters"]
    assert parametros["WFURL"] == url, "la URL de Movi"
    assert parametros["WFHTTPMethod"] == "POST"
    cabeceras = parametros["WFHTTPHeaders"]["Value"]["WFDictionaryFieldValueItems"]
    assert [c["WFKey"]["Value"]["string"] for c in cabeceras] == ["Authorization"], "la cabecera del código"
    assert cabeceras[0]["WFValue"]["Value"]["string"].startswith("Bearer "), "Bearer <código>"
    pregunta = datos["WFWorkflowImportQuestions"][0]
    assert datos["WFWorkflowActions"][pregunta["ActionIndex"]]["WFWorkflowActionIdentifier"] == "is.workflow.actions.gettext"


def firmar(plist_xml, destino):
    """Binario + `shortcuts sign --mode anyone`. Devuelve True si firmó (y el archivo empieza con AEA1)."""
    if not shutil.which("shortcuts"):
        print(f"  sin /usr/bin/shortcuts: {destino.name} queda sin firmar")
        return False
    with tempfile.TemporaryDirectory() as tmp:
        binario = Path(tmp) / "sin-firmar.shortcut"
        subprocess.run(["plutil", "-convert", "binary1", "-o", str(binario), str(plist_xml)], check=True)
        r = subprocess.run(["shortcuts", "sign", "--mode", "anyone", "--input", str(binario), "--output", str(destino)],
                           capture_output=True, text=True)
        if r.returncode != 0 or not destino.exists():
            print(f"  la firma falló ({r.returncode}): {r.stderr.strip()[:300]}")
            return False
    with open(destino, "rb") as f:
        if f.read(4) != b"AEA1":
            print(f"  {destino.name} no tiene la cabecera de un atajo firmado")
            destino.unlink()
            return False
    return True


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--url", default=URL_DE_PRODUCCION, help="la base del server (por defecto, producción)")
    p.add_argument("--salida", default=str(SALIDA_FIRMADOS), help="dónde quedan los .shortcut firmados")
    p.add_argument("--plist", default=str(SALIDA_PLIST), help="dónde quedan los plist sin firmar")
    p.add_argument("--sin-firmar", action="store_true", help="solo escribe y verifica los plist")
    a = p.parse_args()

    salida, carpeta_plist = Path(a.salida), Path(a.plist)
    salida.mkdir(parents=True, exist_ok=True)
    carpeta_plist.mkdir(parents=True, exist_ok=True)
    url = a.url.rstrip("/") + RUTA_DE_LA_CAPTURA

    resultado = 0
    for nombre, armar, se_firma in [
        ("movi-mensaje", atajo_de_mensajes, True),
        ("movi-apple-pay", atajo_de_apple_pay, FIRMAR_APPLE_PAY),
    ]:
        plist = carpeta_plist / f"{nombre}.plist"
        with open(plist, "wb") as f:
            plistlib.dump(armar(a.url), f, fmt=plistlib.FMT_XML, sort_keys=True)
        verificar_plist(plist, url)
        print(f"{plist.relative_to(RAIZ) if plist.is_relative_to(RAIZ) else plist}: plist válido")
        firmado = salida / f"{nombre}.shortcut"
        if a.sin_firmar or not se_firma:
            if firmado.exists():
                firmado.unlink()
            print(f"  {nombre}: sin firmar" + ("" if a.sin_firmar else " (a propósito: ver el docstring)"))
            continue
        if firmar(plist, firmado):
            print(f"  firmado: {firmado}")
        else:
            resultado = 1
    return resultado


if __name__ == "__main__":
    sys.exit(main())
