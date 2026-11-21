#!/usr/bin/env python3
"""Genera los manuales de propietario (PDF) de Agencia360 · CareCar.

Para cada (modelo, año) arma un HTML con la foto del coche y lo imprime a PDF con
Google Chrome headless. Usa solo la biblioteca estándar de Python.

Uso: scripts/generar-manuales.py [--fotos DIR] [--salida DIR] [--forzar]

Los pares (modelo, año) son la unión de los vehículos de scripts/cargar-datos-de-ejemplo.sh
y los 22 modelos del catálogo en 2026. Escribe <salida>/indice.txt con "modelo|año|archivo".
Los datos técnicos son valores de referencia generales; el manual oficial prevalece.
"""
import argparse, base64, html, os, re, shutil, subprocess, sys, tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

AQUI = Path(__file__).resolve().parent
FOTOS = Path(os.environ.get("FOTOS_AUTOS",
    "/home/pablodev24/School-Projects/proyecto-agencia360/carecar-mobile/assets/autos"))
CHROME = os.environ.get("CHROME", "/usr/bin/google-chrome")
VER = "Ver ficha de tu unidad"

# clave: (nombre, tipo, motor, transmisión, tracción, pasajeros, tanque/batería)
# tipo: G gasolina, E eléctrico, H e-Power
M = {
 "march": ("March", "G", "1.6 L, 4 cilindros", "Manual de 5 vel. o CVT (según versión)", "Delantera", "5", "Ver ficha de tu unidad"),
 "versa": ("Versa", "G", "1.6 L, 4 cilindros", "Manual de 5 vel. o CVT (según versión)", "Delantera", "5", VER),
 "kicks": ("Kicks", "G", "1.6 L, 4 cilindros (según año)", "CVT (Xtronic)", "Delantera", "5", VER),
 "sentra": ("Sentra", "G", "2.0 L, 4 cilindros (según año)", "CVT (Xtronic) o manual", "Delantera", "5", VER),
 "xtrail": ("X-Trail", "G", "2.5 L, 4 cilindros (según año)", "CVT (Xtronic)", "Delantera o integral (según versión)", "5 o 7 (según versión)", VER),
 "frontier": ("Frontier", "G", VER, "Manual o automática (según versión)", "4x2 o 4x4 (según versión)", "5", VER),
 "leaf": ("Leaf", "E", "Motor eléctrico", "Reductor de una velocidad", "Delantera", "5", "Batería de iones de litio, 40 o 62 kWh (según versión)"),
 "ariya": ("Ariya", "E", "Motor(es) eléctrico(s)", "Reductor de una velocidad", "Delantera o e-4ORCE integral (según versión)", "5", "Batería de iones de litio, 63 o 87 kWh (según versión)"),
 "z": ("Z", "G", "3.0 L V6 biturbo", "Manual de 6 vel. o automática de 9 vel.", "Trasera", "2", VER),
 "gtr": ("GT-R", "G", "3.8 L V6 biturbo", "Automática de doble embrague, 6 vel.", "Integral", "4", VER),
 "altima": ("Altima", "G", "2.5 L, 4 cilindros", "CVT (Xtronic)", "Delantera o integral (según versión)", "5", VER),
 "pathfinder": ("Pathfinder", "G", "3.5 L V6 (según año)", "Automática (según año)", "Delantera o 4x4 (según versión)", "7", VER),
 "murano": ("Murano", "G", "3.5 L V6", "CVT (Xtronic)", "Delantera o integral (según versión)", "5", VER),
 "armada": ("Armada", "G", "5.6 L V8", "Automática de 7 vel.", "4x2 o 4x4 (según versión)", "7 u 8 (según versión)", VER),
 "titan": ("Titan", "G", "5.6 L V8", "Automática de 7 vel.", "4x2 o 4x4 (según versión)", "5", VER),
 "urvan": ("Urvan", "G", "2.5 L, 4 cilindros", "Manual de 5 vel.", "Trasera", "Según configuración", VER),
 "maxima": ("Maxima", "G", "3.5 L V6", "CVT (Xtronic)", "Delantera", "5", VER),
 "juke": ("Juke", "G", "1.6 L, 4 cilindros", "CVT (Xtronic) o manual", "Delantera", "5", VER),
 "qashqai": ("Qashqai", "G", "2.0 L, 4 cilindros (según año)", "CVT (Xtronic)", "Delantera o integral (según versión)", "5", VER),
 "patrol": ("Patrol", "G", "5.6 L V8", "Automática de 7 vel.", "4x4", "7 u 8 (según versión)", VER),
 "tiida": ("Tiida", "G", "1.8 L, 4 cilindros", "Manual de 6 vel. o automática", "Delantera", "5", VER),
 "note": ("Note e-Power", "H", "Motor eléctrico de tracción con motor de gasolina que genera electricidad (e-Power)", "Reductor de una velocidad", "Delantera", "5", VER),
}
CLAVE = {"x-trail": "xtrail", "gt-r": "gtr", "note e-power": "note"}
def clave(nombre):
    n = nombre.strip().lower()
    return CLAVE.get(n, n.replace(" ", ""))

AGENDA = "Agenda tu cita desde la app CareCar"
def e(t): return html.escape(str(t))
def tabla(cab, filas, anchos=None):
    th = "".join(f"<th>{e(c)}</th>" for c in cab)
    tr = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in f) + "</tr>" for f in filas)
    return f"<table><thead><tr>{th}</tr></thead><tbody>{tr}</tbody></table>"
def tarjetas(items, cols=2):
    return f'<div class="grid c{cols}">' + "".join(
        f'<div class="card"><div class="ic">{i}</div><div><b>{e(t)}</b><p>{e(d)}</p></div></div>' for i, t, d in items) + "</div>"
def aviso(t, c="info"): return f'<div class="aviso {c}">{t}</div>'

CSS = """
@page{size:210mm 297mm;margin:0}
*{box-sizing:border-box;margin:0;padding:0}
:root{--az:#1D3557;--tq:#2EC4B6;--gris:#F1F5F9;--tx:#1f2937}
html,body{font-family:'Helvetica Neue',Helvetica,Arial,'DejaVu Sans',sans-serif;color:var(--tx);font-size:10.5pt;line-height:1.45;
 -webkit-print-color-adjust:exact;print-color-adjust:exact}
.page{width:210mm;height:297mm;position:relative;overflow:hidden;page-break-after:always;break-after:page;padding:26mm 18mm 20mm}
.page:last-child{page-break-after:auto;break-after:auto}
.enc{position:absolute;top:0;left:0;right:0;height:14mm;background:var(--az);color:#fff;display:flex;justify-content:space-between;align-items:center;padding:0 18mm;font-size:8.5pt;letter-spacing:.3px}
.enc i{display:inline-block;width:10px;height:10px;background:var(--tq);border-radius:50%;margin-right:7px}
.pie{position:absolute;bottom:0;left:0;right:0;height:12mm;border-top:2px solid var(--tq);display:flex;justify-content:space-between;align-items:center;padding:0 18mm;font-size:8.5pt;color:#64748b}
.pie b{color:var(--az);font-size:10pt}
h1{color:var(--az);font-size:22pt;margin-bottom:4mm;padding-bottom:2mm;border-bottom:3px solid var(--tq);display:flex;align-items:center;gap:10px}
h1 span{font-size:20pt}
h2{color:var(--az);font-size:13pt;margin:6mm 0 2mm}
p{margin-bottom:2.5mm}
ul{margin:0 0 3mm 5mm}li{margin-bottom:1.2mm}
table{width:100%;border-collapse:collapse;margin:2mm 0 3mm;font-size:9.5pt}
th{background:var(--az);color:#fff;text-align:left;padding:2mm 3mm;font-weight:600}
td{padding:2mm 3mm;vertical-align:top;border-bottom:1px solid #e2e8f0}
tbody tr:nth-child(even) td{background:var(--gris)}
td:first-child{font-weight:600;color:var(--az)}
.grid{display:grid;gap:4mm;margin:3mm 0}.c2{grid-template-columns:1fr 1fr}.c3{grid-template-columns:1fr 1fr 1fr}
.card{display:flex;gap:3mm;background:var(--gris);border-left:4px solid var(--tq);border-radius:3mm;padding:3.5mm}
.card .ic{font-size:18pt;line-height:1}.card b{color:var(--az)}.card p{margin:1mm 0 0;font-size:9.5pt}
.aviso{border-radius:3mm;padding:3.5mm 4mm;margin:3mm 0;font-size:9.5pt}
.info{background:#e6f7f5;border:1px solid var(--tq)}.warn{background:#fff7e6;border:1px solid #f5b942}.danger{background:#fdecec;border:1px solid #e05252}
.foto{width:100%;border-radius:4mm;display:block;object-fit:cover}
.portada{padding:0;background:var(--az);color:#fff}
.portada .banda{position:absolute;top:0;left:0;right:0;height:12mm;background:var(--tq)}
.portada .marca{position:absolute;top:24mm;left:18mm;right:18mm}
.portada .marca small{letter-spacing:3px;color:var(--tq);font-size:10pt;font-weight:700}
.portada h0{display:block;font-size:44pt;font-weight:800;line-height:1.05;margin-top:3mm}
.portada .sub{font-size:16pt;color:#cbd5e1;margin-top:3mm}
.portada .fotobox{position:absolute;top:105mm;left:0;right:0;height:115mm;background:#fff}
.portada .fotobox img{width:100%;height:100%;object-fit:cover}
.portada .abajo{position:absolute;bottom:18mm;left:18mm;right:18mm;display:flex;justify-content:space-between;align-items:flex-end}
.portada .abajo div{font-size:11pt;color:#cbd5e1}.portada .abajo b{display:block;color:#fff;font-size:14pt}
.chip{display:inline-block;background:var(--tq);color:var(--az);font-weight:700;border-radius:20px;padding:1mm 4mm;font-size:10pt}
.sem{display:inline-block;width:11px;height:11px;border-radius:50%;margin-right:5px;vertical-align:middle}
.r{background:#e05252}.a{background:#f5b942}.v{background:#3fb77a}.az{background:#3b82f6}
.toc td{padding:1.4mm 3mm}.toc td:first-child{width:12mm}.toc td{font-weight:400;color:var(--tx)}.toc td:last-child{text-align:right;font-weight:700;color:var(--az)}
.pasos{counter-reset:p;list-style:none;margin:0}.pasos li{counter-increment:p;position:relative;padding-left:9mm;margin-bottom:2mm}
.pasos li:before{content:counter(p);position:absolute;left:0;top:0;width:6mm;height:6mm;border-radius:50%;background:var(--tq);color:var(--az);font-weight:700;text-align:center;font-size:9pt;line-height:6mm}
.cols{display:grid;grid-template-columns:1fr 1fr;gap:6mm}
"""

def paginas(n, a, foto):
    nom, tipo, motor, trans, trac, pas, dep = n
    ev, hy = tipo == "E", tipo == "H"
    mod = f"{nom} {a}"
    P = []  # (titulo indice, cuerpo html)

    # Bienvenida
    P.append(("Bienvenida", f"""<h1><span>👋</span>Bienvenida</h1>
<p>Gracias por elegir tu <b>Nissan {e(mod)}</b>. Este manual de propietario resume lo esencial para conocer, usar y cuidar tu vehículo: tablero, mantenimiento, seguridad y qué hacer ante imprevistos.</p>
{tarjetas([("🚗","Conoce tu vehículo","Identificación, ficha técnica y tablero de indicadores."),("🛠️","Cuídalo","Calendario de mantenimiento cada 10,000 km o 6 meses."),("🛡️","Maneja seguro","Cinturones, bolsas de aire, ISOFIX y asistencias."),("📱","Agenda desde la app","CareCar te deja pedir cita y ver tu historial.")])}
{aviso("<b>Importante:</b> las características varían según versión, año y mercado. Ante cualquier duda, consulta a tu asesor de servicio de Agencia360.")}
{{INDICE}}"""))

    # Ficha
    cap_lbl = "Batería" if ev else ("Tanque de combustible" if not hy else "Tanque de combustible (generador)")
    P.append(("Ficha técnica", f"""<h1><span>📋</span>Ficha técnica</h1>
{aviso("<b>Valores de referencia.</b> Pueden variar según versión, año modelo y mercado. Donde no hay un dato único se indica «ver ficha de tu unidad».", "warn")}
{tabla(["Característica","Valor de referencia"],[["Marca / modelo / año",f"Nissan {e(nom)} {a}"],["Motor / sistema",e(motor)],["Transmisión",e(trans)],["Tracción",e(trac)],["Capacidad de pasajeros",e(pas)],[cap_lbl if dep!=VER else cap_lbl,e(dep) if dep!=VER else "Ver ficha de tu unidad"],["Combustible" if not ev else "Carga","Gasolina (según indique la etiqueta de la tapa del tanque)" if not ev else "Corriente alterna (cargador doméstico o de pared) y carga rápida CC, según versión"],["Dimensiones y pesos","Ver ficha de tu unidad"]])}
<h2>Dónde confirmar los datos de tu unidad</h2>
{tarjetas([("🏷️","Etiqueta del pilar","Datos de peso, presión de llantas y fecha de fabricación, en el pilar de la puerta del conductor."),("📄","Factura y tarjeta de circulación","Año, versión y número de serie oficiales."),("📱","App CareCar","Tu expediente muestra modelo, versión, placas y kilometraje."),("🏢","Agencia360","Tu asesor puede imprimirte la ficha oficial de tu versión.")])}
{'<h2>Notas del sistema eléctrico</h2><ul><li>La autonomía real depende de temperatura, velocidad, carga y estilo de manejo.</li><li>Usa solo cargadores aprobados y no modifiques el sistema de alta tensión.</li></ul>' if ev else ('<h2>Notas del sistema e-Power</h2><ul><li>El motor de gasolina genera electricidad; las ruedas siempre las mueve el motor eléctrico.</li><li>El motor de gasolina puede encenderse y apagarse solo según la carga de la batería.</li></ul>' if hy else '<h2>Notas</h2><ul><li>Usa el combustible indicado en la tapa del tanque.</li><li>Consulta la etiqueta del pilar para la presión de llantas.</li></ul>')}"""))

    # Identificación
    P.append(("Identificación del vehículo", f"""<h1><span>🔎</span>Identificación del vehículo</h1>
<img class="foto" style="height:75mm" src="{foto}" alt="Nissan {e(mod)}">
<p style="text-align:center;color:#64748b;font-size:9pt;margin-top:1mm">Nissan {e(mod)}. Imagen ilustrativa; la versión de tu unidad puede variar.</p>
<h2>Dónde encontrar el VIN (número de serie)</h2>
<p>El VIN tiene 17 caracteres y es único para tu vehículo. Lo necesitarás para seguros, trámites y para registrar tu unidad en CareCar.</p>
{tabla(["Ubicación","Cómo encontrarlo"],[["Parabrisas","Esquina inferior del lado del conductor, visible desde afuera."],["Pilar de la puerta","Etiqueta en el marco de la puerta del conductor."],["Compartimento del motor / chasis","Grabado en la estructura; ver ficha de tu unidad."],["Documentos","Factura, tarjeta de circulación y póliza de seguro."]])}
<h2>Placas y tarjeta de circulación</h2>
<ul><li>Las placas van en la parte delantera y trasera; mantenlas limpias y legibles.</li><li>Conserva la tarjeta de circulación y la póliza de seguro vigentes dentro del vehículo.</li><li>Si cambias de placas, actualiza el dato en tu expediente de la agencia.</li></ul>
{aviso("<b>Consejo:</b> toma una foto de tu VIN y tus placas y guárdala en tu teléfono por si necesitas un trámite.", "info")}"""))

    # Tablero
    if ev or hy:
        luces = [["🔋 Batería baja / nivel crítico","<span class='sem a'></span>","Planifica la carga cuanto antes; reduce el uso de clima y velocidad."],["⚡ Alta tensión / sistema de propulsión","<span class='sem r'></span>","Detente en un lugar seguro y apaga. No toques cables naranjas. Llama a la agencia."],["🔌 Cargando / error de carga","<span class='sem az'></span>","Revisa cable y conector; prueba otro cargador. Si persiste, solicita servicio."],["🌡️ Temperatura de batería o sistema","<span class='sem r'></span>","Deja enfriar el vehículo; no lo cargues hasta que se apague la luz."]]
    else:
        luces = [["🛢️ Presión de aceite","<span class='sem r'></span>","Detente y apaga el motor. Revisa el nivel; no continúes si sigue encendida."],["🔋 Batería / carga","<span class='sem r'></span>","Falla de carga. Apaga accesorios y acude a servicio de inmediato."],["🌡️ Temperatura del motor","<span class='sem r'></span>","Detente, apaga y deja enfriar. No abras el depósito caliente."]]
    luces += [["🅿️ Frenos","<span class='sem r'></span>","Revisa que el freno de mano esté liberado; si sigue, nivel de líquido y servicio."],["🛞 ABS","<span class='sem a'></span>","El ABS no actúa; el freno normal funciona. Maneja con precaución y agenda cita."],["🎈 Bolsas de aire","<span class='sem r'></span>","El sistema puede no activarse. Solicita revisión de inmediato."],["🛞 Presión de llantas (TPMS)","<span class='sem a'></span>","Revisa presiones en frío según la etiqueta del pilar y busca ponchaduras."],["⚙️ Check engine / sistema" if not ev else "⚙️ Servicio del sistema","<span class='sem a'></span>","Fija: agenda diagnóstico pronto. Intermitente: reduce carga y acude a servicio."]]
    P.append(("Tablero e indicadores", f"""<h1><span>📟</span>Tablero e indicadores</h1>
<p>Al girar el encendido, las luces se iluminan unos segundos como autoprueba. Si alguna permanece o se enciende durante la marcha, actúa según el color.</p>
<div style="margin-bottom:2mm"><span class="sem r"></span>Rojo: detente y revisa &nbsp; <span class="sem a"></span>Ámbar: agenda servicio &nbsp; <span class="sem az"></span>Azul / verde: informativo</div>
{tabla(["Indicador","Nivel","Qué hacer"],luces)}
{aviso("<b>Importante:</b> una luz roja requiere detenerte en un lugar seguro lo antes posible. No continúes manejando si el vehículo lo indica." , "danger")}"""))

    # Mantenimiento
    if ev:
        serv = [["10,000 km / 6 meses","Revisión general, filtro de aire de cabina, llantas (presión, desgaste, rotación), frenos, líquidos, estado de la batería de 12 V"],["20,000 km / 12 meses","Todo lo anterior + líquido de frenos, limpieza de frenos, revisión de suspensión y dirección"],["30,000 km / 18 meses","Revisión del sistema de carga y cables, filtro de cabina, estado de salud de la batería de tracción"],["40,000 km / 24 meses","Refrigerante del sistema de baterías (según indique el programa), frenos, alineación y balanceo"],["50,000 km o más","Repetir el ciclo; revisión de suspensión, amortiguadores y software"]]
    else:
        serv = [["10,000 km / 6 meses","Cambio de aceite y filtro, rotación y revisión de llantas, inspección de frenos, nivel de líquidos, batería"],["20,000 km / 12 meses","Todo lo anterior + filtro de aire y filtro de cabina, revisión de suspensión y dirección"],["30,000 km / 18 meses","Aceite y filtro, limpieza de frenos, revisión de bandas y mangueras, alineación y balanceo"],["40,000 km / 24 meses","Aceite y filtros, líquido de frenos, bujías según programa, revisión de transmisión"],["50,000 km o más","Repetir el ciclo; revisión de batería, amortiguadores y sistema de enfriamiento"]]
    P.append(("Mantenimiento programado", f"""<h1><span>🛠️</span>Mantenimiento programado</h1>
<p>Realiza el servicio cada <b>10,000 km o 6 meses</b>, lo que ocurra primero. Un servicio a tiempo conserva la garantía, el valor de reventa y tu seguridad.</p>
{tabla(["Intervalo","Qué se revisa o cambia"],serv)}
<h2>Qué incluye cada revisión</h2>
{tarjetas([("🛢️" if not ev else "🔋","Aceite y filtro" if not ev else "Batería y carga","Se cambia el aceite y el filtro con el grado adecuado." if not ev else "Se revisa el estado de salud de la batería y el sistema de carga."),("🌬️","Filtros","Aire de motor y de cabina limpios mejoran desempeño y confort."),("🛑","Frenos","Balatas, discos y líquido, para frenar siempre a tiempo."),("🛞","Llantas","Presión, desgaste, rotación, alineación y balanceo."),("🔋","Batería de 12 V","Bornes limpios y carga correcta para el arranque y accesorios."),("🔧","Suspensión","Amortiguadores, rótulas y dirección para estabilidad y comodidad.")])}
{aviso("<b>Nota:</b> el programa exacto de tu unidad puede variar; tu asesor lo confirmará en la agencia. Condiciones severas (polvo, tráfico denso, remolque) piden intervalos más cortos.", "warn")}"""))

    # Fluidos y llantas
    if ev:
        flu = [["Refrigerante","Del tipo que indique el fabricante; no mezclar tipos."],["Líquido de frenos","Revisar en cada servicio; reemplazo según programa."],["Limpiaparabrisas","Revisar nivel y plumas con regularidad."],["Aceite de motor","No aplica a la tracción eléctrica; ver ficha de tu unidad."]]
    else:
        flu = [["Aceite de motor","Sintético o semisintético de baja viscosidad (por ejemplo 0W-20 o 5W-30); confirma el grado en la ficha de tu unidad."],["Refrigerante","Del tipo que indique el fabricante; no mezclar tipos."],["Líquido de frenos","Revisar en cada servicio; reemplazo según programa."],["Limpiaparabrisas","Revisar nivel y plumas con regularidad."]]
    P.append(("Fluidos y llantas", f"""<h1><span>💧</span>Fluidos y llantas</h1>
<h2>Fluidos</h2>
{tabla(["Fluido","Recomendación general"],flu)}
<p>Revisa los niveles con el vehículo en piso plano y el motor frío (según corresponda). No rellenes de más.</p>
<h2>Llantas</h2>
{tabla(["Tema","Recomendación"],[["Presión","Ver etiqueta del pilar de la puerta del conductor (en frío)."],["Revisión","Cada 2 semanas y antes de un viaje largo, incluida la llanta de refacción."],["Rotación","Cada 10,000 km, junto con el servicio."],["Desgaste","Cambia la llanta si la profundidad del dibujo es menor a 1.6 mm."],["Alineación y balanceo","Si el vehículo se jala hacia un lado o vibra el volante."]])}
{tarjetas([("🌡️","Presión en frío","Mide con las llantas frías; el calor sube la lectura."),("🪙","Prueba de la moneda","Un dibujo muy bajo reduce el agarre en lluvia.")])}
{aviso("<b>Importante:</b> una presión incorrecta aumenta el consumo, el desgaste y el riesgo de ponchadura.", "warn")}"""))

    # Seguridad
    P.append(("Seguridad", f"""<h1><span>🛡️</span>Seguridad</h1>
{tarjetas([("🪢","Cinturones de seguridad","Úsalos siempre, en todos los asientos. Ajusta la banda sobre el pecho y la cadera, nunca sobre el abdomen."),("🎈","Bolsas de aire","Se activan en impactos fuertes. No coloques objetos sobre ellas ni a los niños muy cerca del tablero."),("👶","Sillas ISOFIX","Los anclajes ISOFIX están en el asiento trasero. Sigue las instrucciones de tu silla infantil."),("🚨","Asistencias","Según versión: frenos ABS, control de estabilidad, cámara y sensores de estacionamiento.")])}
<h2>Niños a bordo</h2>
<ul><li>Los menores de 12 años viajan en el asiento trasero con el sistema de retención adecuado.</li><li>Usa el seguro para niños de las puertas traseras.</li><li>Nunca dejes a un menor solo dentro del vehículo.</li></ul>
<h2>Antes de arrancar</h2>
<ul><li>Ajusta asiento, espejos y volante.</li><li>Abrocha tu cinturón y el de tus pasajeros.</li><li>Verifica que no haya luces rojas encendidas en el tablero.</li></ul>
{aviso("<b>Importante:</b> las asistencias son una ayuda, no sustituyen tu atención. Su disponibilidad depende de la versión; ver ficha de tu unidad.", "info")}"""))

    # Conducción
    tips = [("⛽","Maneja suave","Acelera y frena de forma gradual; ahorras combustible y frenos."),("🛞","Presión correcta","Llantas bien infladas reducen el consumo."),("🧳","Menos peso","Quita lo que no uses de la cajuela y el techo."),("🌬️","Clima con medida","Usa el aire acondicionado y la calefacción solo cuando haga falta.")]
    if ev or hy:
        tips += [("🔋","Frenado regenerativo","Aprovéchalo para recuperar energía al soltar el acelerador."),("🔌","Carga inteligente","Evita dejar la batería en niveles extremos por mucho tiempo.")]
    else:
        tips += [("⏱️","Evita el ralentí","No dejes el motor encendido sin necesidad."),("🗺️","Planea tu ruta","Evita tráfico y arrancones innecesarios.")]
    P.append(("Conducción eficiente y cuidados", f"""<h1><span>🌿</span>Conducción eficiente y cuidados</h1>
{tarjetas(tips)}
<h2>Cuidados del vehículo</h2>
{tabla(["Tema","Recomendación"],[["Lavado","Lava con agua y shampoo automotriz; evita sol directo."],["Pintura","Aplica cera o sellador cada 3 a 6 meses."],["Interior","Aspira y limpia con productos suaves; protege el tablero del sol."],["Estacionamiento","Prefiere la sombra y no dejes objetos de valor a la vista."],["Uso poco frecuente","Si no lo usas, enciéndelo y muévelo cada semana."]])}
{aviso("<b>Consejo:</b> un vehículo bien cuidado consume menos y vale más.", "info")}"""))

    # Problemas
    p_enc = "Revisa que la batería de 12 V tenga carga y que el sistema esté listo (indicador READY o equivalente)." if (ev or hy) else "Revisa la batería (bornes y carga), el combustible y que la palanca esté en P o N."
    P.append(("Solución de problemas", f"""<h1><span>🧰</span>Solución de problemas</h1>
<div class="cols"><div><h2>No enciende</h2><ol class="pasos"><li>{e(p_enc)}</li><li>Pisa el freno (o el embrague) al arrancar.</li><li>Intenta con la llave de repuesto.</li><li>Si no responde, llama a la agencia o a asistencia vial.</li></ol></div>
<div><h2>Ponchadura</h2><ol class="pasos"><li>Detente en terreno plano y enciende las intermitentes.</li><li>Coloca el freno de mano y señalamientos.</li><li>Afloja las tuercas, levanta con el gato en el punto indicado.</li><li>Cambia la llanta y aprieta en cruz. Revisa la presión.</li></ol></div></div>
<div class="cols"><div><h2>Sobrecalentamiento</h2><ol class="pasos"><li>Apaga el aire acondicionado y detente de forma segura.</li><li>Apaga el vehículo; no abras el depósito caliente.</li><li>Espera a que enfríe y revisa el nivel de refrigerante.</li><li>Si persiste, solicita servicio.</li></ol></div>
<div><h2>Batería baja</h2><ol class="pasos"><li>Apaga luces y accesorios.</li><li>Usa cables pasacorriente: positivo con positivo, negativo a masa.</li><li>Deja cargar unos minutos antes de apagar.</li><li>Si se repite, revisa la batería en servicio.</li></ol></div></div>
{aviso("<b>Importante:</b> en caso de accidente enciende las intermitentes, revisa a los ocupantes y llama a emergencias al <b>911</b>.", "danger")}
{aviso("En eléctricos e híbridos, ante un golpe fuerte o humo, aléjate del vehículo y no toques el sistema de alta tensión." if (ev or hy) else "No fumes ni uses fuego cerca del compartimento del motor ante una fuga de combustible.", "warn")}"""))

    # Garantía
    P.append(("Garantía y servicio", f"""<h1><span>🧾</span>Garantía y servicio</h1>
<p>Tu vehículo cuenta con la garantía del fabricante en los términos de tu póliza de garantía. Para conservarla, realiza tus servicios programados en Agencia360.</p>
<h2>Cómo agendar tu cita en la app CareCar</h2>
<ol class="pasos"><li>Entra a <b>CareCar</b> con tu correo y contraseña.</li><li>Elige tu vehículo y toca <b>Agendar cita</b>.</li><li>Selecciona el tipo de servicio, la fecha y el horario disponible.</li><li>Confirma y recibirás tu comprobante.</li></ol>
{tarjetas([("📅","Citas","Agenda, consulta o cancela tus citas desde la app."),("🗂️","Historial de servicios","Cada servicio queda registrado en tu expediente."),("🎫","Ticket","Al llegar, muestra tu ticket o comprobante de cita."),("📘","Manuales","Consulta este manual y otros desde la app.")])}
<h2>Qué cubre y qué no</h2>
{tabla(["Cubre","No cubre"],[["Defectos de fabricación y materiales.","Desgaste normal (balatas, llantas, plumas)."],["Reparaciones hechas en la agencia.","Daños por mal uso, accidentes o modificaciones."],["Revisiones de acuerdo con el programa.","Servicios omitidos o hechos fuera de la red autorizada."]])}
{aviso("<b>Nota:</b> los términos de la garantía son los de tu póliza oficial; este documento es solo una guía.", "info")}"""))

    # Contacto + legal
    P.append(("Contacto y aviso legal", f"""<h1><span>📞</span>Contacto de la agencia</h1>
{tarjetas([("☎️","Teléfono","+52 000 000 0000"),("✉️","Correo","contacto@agencia.example.com"),("📍","Dirección","Av. Ejemplo 123, Col. Centro, Ciudad de Ejemplo, México"),("🕘","Horario","Lunes a viernes de 9:00 a 18:00; sábados de 9:00 a 14:00")])}
{aviso("Los datos de contacto son de ejemplo y serán reemplazados por los de la agencia.", "warn")}
<h2>Aviso legal</h2>
<p style="font-size:9.5pt;color:#475569">Este documento es una guía de referencia elaborada por Agencia360 · CareCar para tu Nissan {e(mod)}. La información, imágenes y valores aquí contenidos son orientativos y pueden variar según versión, año y mercado. En caso de diferencia, <b>prevalece el manual oficial del fabricante</b> que acompaña a tu vehículo. Agencia360 no se hace responsable por el uso indebido de esta información. Nissan y los nombres de modelos son marcas de sus respectivos propietarios.</p>
<div style="margin-top:12mm;text-align:center;color:var(--az)"><b style="font-size:16pt">Agencia360 · CareCar</b><br><span style="color:#64748b">Gracias por confiar en nosotros.</span></div>"""))
    return P

def construir_html(nom_clave, anio, foto_uri, version="Según versión"):
    n = M[nom_clave]
    nom = n[0]
    paginas_ = paginas(n, anio, foto_uri)
    total = len(paginas_) + 1  # portada + secciones (el índice va en la bienvenida)
    indice = "<h2>Contenido</h2>" + tabla(["N.º","Sección","Página"],
        [[str(i+1), e(t), str(i+2)] for i, (t, _) in enumerate(paginas_)]).replace("<table>", '<table class="toc">')
    # El índice va en página 2 junto con la bienvenida? No: página propia.
    enc = lambda: f'<div class="enc"><span><i></i>Agencia360 · CareCar</span><span>Manual de propietario · Nissan {e(nom)} {anio}</span></div>'
    pie = lambda k: f'<div class="pie"><span>Agencia360 · CareCar</span><b>Página {k} de {total}</b></div>'
    portada = f"""<div class="page portada"><div class="banda"></div>
<div class="marca"><small>MANUAL DE PROPIETARIO</small><h0>Nissan<br>{e(nom)}</h0><div class="sub">Año modelo {anio} &nbsp;·&nbsp; Versión: según tu unidad</div></div>
<div class="fotobox"><img src="{foto_uri}" alt=""></div>
<div class="abajo"><div><b>Agencia360 · CareCar</b>Tu guía para conocer y cuidar tu vehículo</div><span class="chip">{anio}</span></div></div>"""
    cuerpo = [portada]
    for i, (_, c) in enumerate(paginas_):
        cuerpo.append(f'<div class="page">{enc()}{c.replace('{INDICE}', indice if i == 0 else '')}{pie(i+2)}</div>')
    return f'<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Manual de propietario Nissan {e(nom)} {anio}</title><style>{CSS}</style></head><body>{"".join(cuerpo)}</body></html>', total

def foto_data_uri(k):
    f = FOTOS / f"{k}.jpg"
    if not f.exists():
        raise SystemExit(f"Falta la foto {f}")
    return "data:image/jpeg;base64," + base64.b64encode(f.read_bytes()).decode()

def pares():
    """Unión de vehículos del script de datos y los 22 modelos del catálogo en 2026."""
    s = (AQUI / "cargar-datos-de-ejemplo.sh").read_text(encoding="utf-8")
    out = []
    for m in re.finditer(r"^([A-Za-z][\w-]*)\|[^|\n]+\|(\d{4})\|", s, re.M):
        out.append((clave(m.group(1)), int(m.group(2))))
    for m in re.finditer(r"^crear_vehiculo \S+ (\S+) \S+ (\d{4}) ", s, re.M):
        out.append((clave(m.group(1)), int(m.group(2))))
    out += [("march", 2026), ("sentra", 2025)]
    out += [(k, 2026) for k in M]
    vistos, res = set(), []
    for p in out:
        if p[0] in M and p not in vistos:
            vistos.add(p); res.append(p)
    return res

def nombre_archivo(k, a): return f"nissan-{k}-{a}.pdf"

def generar(par, salida, tmp, forzar):
    k, a = par
    pdf = salida / nombre_archivo(k, a)
    if pdf.exists() and not forzar:
        return par, None
    h, total = construir_html(k, a, foto_data_uri(k))
    hp = tmp / f"{k}-{a}.html"
    hp.write_text(h, encoding="utf-8")
    perfil = tmp / f"perfil-{k}-{a}"
    r = subprocess.run([CHROME, "--headless=new", "--no-sandbox", "--disable-gpu", f"--user-data-dir={perfil}",
        f"--print-to-pdf={pdf}", "--no-pdf-header-footer", "--virtual-time-budget=5000", hp.as_uri()],
        capture_output=True, text=True, timeout=120)
    if not pdf.exists():
        raise SystemExit(f"Chrome no generó {pdf}: {r.stderr[-400:]}")
    return par, total

def main():
    global FOTOS
    ap = argparse.ArgumentParser()
    ap.add_argument("--fotos"); ap.add_argument("--salida", default=str(AQUI / "manuales-generados"))
    ap.add_argument("--forzar", action="store_true")
    ap.add_argument("--solo", help="modelo:año, por ejemplo march:2026")
    a = ap.parse_args()
    if a.fotos: FOTOS = Path(a.fotos)
    if not Path(CHROME).exists() and not shutil.which(CHROME):
        raise SystemExit("No se encontró Google Chrome (variable CHROME).")
    salida = Path(a.salida); salida.mkdir(parents=True, exist_ok=True)
    lista = pares()
    if a.solo:
        k, y = a.solo.split(":"); lista = [(k, int(y))]
    with tempfile.TemporaryDirectory() as t, ThreadPoolExecutor(4) as ex:
        for par, total in ex.map(lambda p: generar(p, salida, Path(t), a.forzar), lista):
            print(f"  {'generado' if total else 'existe  '} {nombre_archivo(*par)}", flush=True)
    with open(salida / "indice.txt", "w", encoding="utf-8") as f:
        for k, y in lista:
            f.write(f"{M[k][0]}|{y}|{nombre_archivo(k, y)}\n")
    print(f"{len(lista)} manuales en {salida}")

if __name__ == "__main__":
    main()
