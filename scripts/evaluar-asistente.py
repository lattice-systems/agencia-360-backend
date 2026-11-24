#!/usr/bin/env python3
"""Evalúa las citas del asistente CareCar (V2-M24.07, ADR 0007).

Hace cada pregunta de docs/asistente/evaluacion/preguntas.json con una cuenta de
cliente y cuenta cuántas respuestas citan la página esperada. Sale con código 1 si
el porcentaje queda bajo el umbral del ADR (80 %).

Cada cliente tiene 10 preguntas al día (ASISTENTE_LIMITE): los resultados se
guardan en --resultados y, si se corta por el límite, la siguiente corrida sigue
donde se quedó. Las credenciales van por variables de entorno, nunca en la línea
de comandos ni en el repositorio:

  API=https://<api> CLIENTE_CORREO=... CLIENTE_CONTRASENA=... VEHICULO_ID=12 \\
    python3 scripts/evaluar-asistente.py --resultados /tmp/eval-sentra.jsonl

Solo usa la biblioteca estándar de Python.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
CONJUNTO = RAIZ / "docs" / "asistente" / "evaluacion" / "preguntas.json"


def pedir(api, ruta, cuerpo, token=None):
    peticion = urllib.request.Request(
        f"{api}/api/v1/app{ruta}",
        data=json.dumps(cuerpo).encode(),
        headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {token}"} if token else {})},
        method="POST",
    )
    try:
        with urllib.request.urlopen(peticion, timeout=120) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as ex:
        try:
            return ex.code, json.loads(ex.read() or b"{}")
        except json.JSONDecodeError:
            return ex.code, {}


def calificar(conjunto, resultados):
    """Devuelve (correctas, total) de las preguntas ya respondidas."""
    esperadas = {p["pregunta"]: set(p["paginas"]) for p in conjunto["preguntas"]}
    correctas = sum(1 for r in resultados if esperadas.get(r["pregunta"], set()) & set(r["paginas"]))
    return correctas, len(resultados)


def main():
    args = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    args.add_argument("--resultados", required=True, type=Path, help="archivo .jsonl donde se acumulan")
    args.add_argument("--conjunto", type=Path, default=CONJUNTO)
    opciones = args.parse_args()

    api = os.environ.get("API", "http://localhost:8080").rstrip("/")
    if not api.startswith("https://") and "localhost" not in api and "127.0.0.1" not in api:
        sys.exit("API debe usar https fuera de local (V2-M22.09)")
    correo, contrasena = os.environ.get("CLIENTE_CORREO"), os.environ.get("CLIENTE_CONTRASENA")
    vehiculo = os.environ.get("VEHICULO_ID")
    if not (correo and contrasena and vehiculo):
        sys.exit("Faltan CLIENTE_CORREO, CLIENTE_CONTRASENA o VEHICULO_ID")

    conjunto = json.loads(opciones.conjunto.read_text(encoding="utf-8"))
    hechas = []
    if opciones.resultados.exists():
        hechas = [json.loads(l) for l in opciones.resultados.read_text(encoding="utf-8").splitlines() if l.strip()]
    ya = {r["pregunta"] for r in hechas}
    pendientes = [p for p in conjunto["preguntas"] if p["pregunta"] not in ya]

    if pendientes:
        estado, sesion = pedir(api, "/auth/login", {"correo": correo, "contrasena": contrasena})
        if estado != 200:
            sys.exit(f"No se pudo iniciar sesión (HTTP {estado})")
        token = sesion["accessToken"]
        with opciones.resultados.open("a", encoding="utf-8") as salida:
            for p in pendientes:
                estado, r = pedir(api, "/asistente/preguntas", {"vehiculoId": int(vehiculo), "pregunta": p["pregunta"]}, token)
                if estado == 429:
                    print(f"Límite del día alcanzado: faltan {len(conjunto['preguntas']) - len(hechas)} preguntas; "
                          "vuelve a correrlo mañana o con otra cuenta.", file=sys.stderr)
                    break
                if estado != 200:
                    print(f"  HTTP {estado} en «{p['pregunta']}» ({r.get('codigo', '')})", file=sys.stderr)
                    continue
                fila = {"pregunta": p["pregunta"], "paginas": [f["pagina"] for f in r.get("fuentes", [])],
                        "encontrado": r.get("encontrado"), "consultaId": r.get("consultaId")}
                salida.write(json.dumps(fila, ensure_ascii=False) + "\n")
                salida.flush()
                hechas.append(fila)
                bien = set(p["paginas"]) & set(fila["paginas"])
                print(f"  {'✔' if bien else '✘'} {p['pregunta']} → {fila['paginas'] or 'sin cita'} (esperada {p['paginas']})")

    correctas, total = calificar(conjunto, hechas)
    faltan = len(conjunto["preguntas"]) - total
    porcentaje = correctas / total if total else 0.0
    print(f"\nCitas correctas: {correctas}/{total} = {porcentaje:.0%} (umbral {conjunto['umbral']:.0%})"
          + (f"; faltan {faltan} preguntas" if faltan else ""))
    if faltan:
        return 2
    return 0 if porcentaje >= conjunto["umbral"] else 1


if __name__ == "__main__":
    sys.exit(main())
