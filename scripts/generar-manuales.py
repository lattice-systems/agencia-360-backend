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
# Por defecto busca las fotos en el repo carecar-mobile, clonado junto a este.
FOTOS = Path(os.environ.get("FOTOS_AUTOS",
    AQUI.parent.parent / "carecar-mobile" / "assets" / "autos"))
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


_CVT="CVT Xtronic"; _D="Delantera"
def ficha(k, a, ver=""):
    """Ficha técnica por modelo/año (misma lógica que carecar-mobile/lib/config/ficha_tecnica.dart)."""
    v = ver.lower(); man = "sense" in v
    F = lambda c,p,pr,m,po,t,tr: dict(carroceria=c,pasajeros=p,propulsion=pr,motor=m,potencia=po,transmision=t,traccion=tr)
    if k=="march": return F("Hatchback","5","Gasolina","1.6 L, 4 cilindros","106 hp a 6,000 rpm","Manual de 5 velocidades" if man else _CVT,_D)
    if k=="versa": return F("Sedán","5","Gasolina","1.6 L, 4 cilindros","118 hp a 6,300 rpm" if a>=2020 else "106 hp a 5,600 rpm","Manual de 5 velocidades" if man else _CVT,_D)
    if k=="kicks": return F("SUV compacta","5","Gasolina","1.6 L, 4 cilindros","118 hp a 6,300 rpm",_CVT,_D)
    if k=="sentra": return F("Sedán","5","Gasolina","2.0 L, 4 cilindros" if a>=2020 else "1.8 L, 4 cilindros","149 hp a 6,400 rpm" if a>=2020 else "130 hp a 6,300 rpm","Manual de 6 velocidades" if man else _CVT,_D)
    if k=="xtrail" and a>=2023: return F("SUV mediana","5","Híbrido e-Power","1.5 L turbo (generador) y motor eléctrico","204 hp","Automática de una velocidad",_D)
    if k=="xtrail": return F("SUV mediana","5" if man else "7","Gasolina","2.5 L, 4 cilindros","170 hp a 6,000 rpm",_CVT,"Integral (AWD)" if "exclusive" in v else _D)
    if k=="frontier" and a>=2022 and "pro-4x" in v: return F("Pick-up doble cabina","5","Gasolina","V6 3.8 L","310 hp a 6,400 rpm","Automática de 9 velocidades","4x4")
    if k=="frontier": return F("Pick-up doble cabina","5","Gasolina","2.5 L, 4 cilindros","164 hp a 6,000 rpm","Manual de 6 velocidades","4x2")
    if k=="leaf":
        e=("e+" in v); return F("Hatchback","5","Eléctrico","Motor eléctrico, batería de %d kWh"%(62 if e else 40),"214 hp" if e else "147 hp","Automática de una velocidad",_D)
    if k=="ariya": return F("SUV eléctrica","5","Eléctrico","Motor eléctrico, batería de 87 kWh","238 hp","Automática de una velocidad",_D)
    if k=="z": return F("Coupé deportivo","2","Gasolina","V6 3.0 L biturbo","400 hp a 6,400 rpm","Manual de 6 velocidades","Trasera")
    if k=="gtr": return F("Coupé deportivo","4","Gasolina","V6 3.8 L biturbo","565 hp a 6,800 rpm","Automática de 6 velocidades, doble embrague","Integral (ATTESA E-TS)")
    if k=="altima": return F("Sedán","5","Gasolina","2.5 L, 4 cilindros","188 hp a 6,000 rpm",_CVT,_D)
    if k=="pathfinder": return F("SUV de tres filas","7","Gasolina","V6 3.5 L","284 hp a 6,400 rpm","Automática de 9 velocidades","4x4")
    if k=="murano": return F("SUV mediana","5","Gasolina","V6 3.5 L","260 hp a 6,000 rpm",_CVT,"Integral (AWD)")
    if k=="armada": return F("SUV grande","7","Gasolina","V6 3.5 L biturbo","425 hp","Automática de 9 velocidades","4x4")
    if k=="titan": return F("Pick-up grande","5","Gasolina","V8 5.6 L","400 hp a 5,800 rpm","Automática de 9 velocidades","4x4")
    if k=="urvan": return F("Van de carga (Panel)","3","Gasolina","2.5 L, 4 cilindros","126 hp a 5,600 rpm","Manual de 5 velocidades","Trasera")
    if k=="maxima": return F("Sedán","5","Gasolina","V6 3.5 L","300 hp a 6,400 rpm",_CVT,_D)
    if k=="juke": return F("SUV subcompacta","5","Gasolina","1.0 L turbo, 3 cilindros","117 hp a 5,250 rpm","Automática de 7 velocidades, doble embrague",_D)
    if k=="qashqai": return F("Crossover compacto","5","Gasolina (híbrido ligero)","1.3 L turbo, 4 cilindros","158 hp",_CVT,_D)
    if k=="patrol" and a>=2025: return F("SUV grande","7","Gasolina","V6 3.5 L biturbo","425 hp","Automática de 9 velocidades","4x4")
    if k=="patrol": return F("SUV grande","8","Gasolina","V8 5.6 L","400 hp a 5,800 rpm","Automática de 7 velocidades","4x4")
    if k=="tiida": return F("Hatchback","5","Gasolina","1.8 L, 4 cilindros","126 hp a 6,000 rpm","Manual de 6 velocidades" if man else _CVT,_D)
    if k=="note": return F("Hatchback compacto","5","Híbrido e-Power","1.2 L, 3 cilindros (generador) y motor eléctrico","116 hp","Automática de una velocidad",_D)

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
:root{--az:#1D3557;--az2:#0f2038;--tq:#2EC4B6;--gris:#F1F5F9;--tx:#1f2937;--mu:#64748b}
html,body{font-family:'Helvetica Neue',Helvetica,Arial,'DejaVu Sans',sans-serif;color:var(--tx);font-size:10.3pt;line-height:1.42;
 -webkit-print-color-adjust:exact;print-color-adjust:exact}
.page{width:210mm;height:297mm;position:relative;overflow:hidden;page-break-after:always;break-after:page;padding:29mm 17mm 17mm;display:flex;flex-direction:column}
.page:last-child{page-break-after:auto;break-after:auto}
.enc{position:absolute;top:0;left:0;right:0;height:13mm;background:linear-gradient(90deg,var(--az2),var(--az));color:#fff;display:flex;justify-content:space-between;align-items:center;padding:0 17mm;font-size:8.3pt;letter-spacing:.3px;border-bottom:1.2mm solid var(--tq)}
.enc i{display:inline-block;width:9px;height:9px;background:var(--tq);border-radius:50%;margin-right:7px}
.pie{position:absolute;bottom:0;left:0;right:0;height:11mm;border-top:2px solid var(--tq);display:flex;justify-content:space-between;align-items:center;padding:0 17mm;font-size:8.3pt;color:var(--mu);background:#fff;z-index:5}
.pie b{color:#fff;background:var(--az);border-radius:10px;padding:.6mm 3.2mm;font-size:8.6pt}
.kick{font-size:8.5pt;letter-spacing:2.5px;color:var(--tq);font-weight:700;text-transform:uppercase;margin-bottom:1mm}
h1{color:var(--az);font-size:21pt;margin-bottom:4mm;padding-bottom:2mm;border-bottom:3px solid var(--tq);display:flex;align-items:center;gap:10px}
h1 span{font-size:19pt}
h2{color:var(--az);font-size:12.5pt;margin:5mm 0 2mm;display:flex;align-items:center;gap:6px}
h2:before{content:"";width:3mm;height:3mm;background:var(--tq);border-radius:1mm}
p{margin-bottom:2.3mm}
ul{margin:0 0 3mm 5mm}li{margin-bottom:1.1mm}
table{width:100%;border-collapse:collapse;margin:2mm 0 3mm;font-size:9.4pt}
th{background:var(--az);color:#fff;text-align:left;padding:2mm 3mm;font-weight:600}
td{padding:1.9mm 3mm;vertical-align:top;border-bottom:1px solid #e2e8f0}
tbody tr:nth-child(even) td{background:var(--gris)}
td:first-child{font-weight:600;color:var(--az)}
.grid{display:grid;gap:3.5mm;margin:3mm 0}.c2{grid-template-columns:1fr 1fr}.c3{grid-template-columns:1fr 1fr 1fr}
.card{display:flex;gap:3mm;background:var(--gris);border-left:4px solid var(--tq);border-radius:3mm;padding:3.2mm}
.card .ic{font-size:17pt;line-height:1}.card b{color:var(--az)}.card p{margin:.8mm 0 0;font-size:9.3pt}
.aviso{border-radius:3mm;padding:3.2mm 4mm 3.2mm 12mm;margin:3mm 0;font-size:9.4pt;position:relative}
.aviso:before{position:absolute;left:3.5mm;top:3mm;font-size:13pt}
.info{background:#e6f7f5;border:1px solid var(--tq)}.info:before{content:"💡"}
.warn{background:#fff7e6;border:1px solid #f5b942}.warn:before{content:"⚠️"}
.danger{background:#fdecec;border:1px solid #e05252}.danger:before{content:"⛔"}
.foto{width:100%;border-radius:4mm;display:block;object-fit:cover}
.grow{flex:1}
.fill{flex:1;min-height:0;margin:2mm 0 0;border-radius:4mm;overflow:hidden;position:relative;background:#0f2038}.fill img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}.fill div{position:absolute;inset:0;background:linear-gradient(0deg,rgba(15,32,56,.85),rgba(15,32,56,.05));display:flex;align-items:flex-end;padding:4mm 5mm;color:#fff;font-weight:700;font-size:12pt}
.mark{position:absolute;right:-30mm;bottom:12mm;width:150mm;opacity:.07;z-index:0}
.page>*{position:relative;z-index:1}
.page>.mark{position:absolute;z-index:0}
.portada{padding:0;background:var(--az2);color:#fff;display:block}
.portada .bg{position:absolute;inset:0;z-index:0}
.portada .bg img{width:100%;height:100%;object-fit:cover}
.portada .bg:after{content:"";position:absolute;inset:0;background:linear-gradient(180deg,rgba(15,32,56,.96) 0%,rgba(15,32,56,.55) 38%,rgba(15,32,56,.15) 55%,rgba(15,32,56,.92) 100%)}
.portada .franja{position:absolute;z-index:2;top:0;left:0;right:0;height:9mm;background:var(--tq)}
.portada .franja2{position:absolute;z-index:2;top:9mm;left:0;width:60mm;height:3mm;background:#fff}
.portada .marca{position:absolute;z-index:2;top:26mm;left:17mm;right:17mm}
.portada small{letter-spacing:4px;color:var(--tq);font-size:10.5pt;font-weight:700}
.portada .gran{display:block;font-size:30pt;font-weight:300;letter-spacing:6px;margin-top:5mm;text-transform:uppercase}
.portada .mod{display:block;font-size:66pt;font-weight:800;line-height:1;margin-top:1mm;text-shadow:0 2px 12px rgba(0,0,0,.4)}
.portada .pills{margin-top:7mm}
.pill{display:inline-block;border:1.5px solid var(--tq);color:#fff;border-radius:20px;padding:1.2mm 5mm;font-size:10.5pt;margin-right:3mm;background:rgba(46,196,182,.18)}
.pill.s{background:var(--tq);color:var(--az2);font-weight:700}
.portada .abajo{position:absolute;z-index:2;bottom:0;left:0;right:0;padding:0 17mm 14mm;display:flex;justify-content:space-between;align-items:flex-end}
.portada .abajo div{font-size:10.5pt;color:#cbd5e1}.portada .abajo b{display:block;color:#fff;font-size:15pt}
.portada .doc{text-align:right;font-size:9pt;letter-spacing:1px}
.portada .baja{position:absolute;z-index:2;bottom:0;left:0;right:0;height:5mm;background:var(--tq)}
.sem{display:inline-block;width:11px;height:11px;border-radius:50%;margin-right:5px;vertical-align:middle}
.r{background:#e05252}.a{background:#f5b942}.v{background:#3fb77a}.az{background:#3b82f6}
.toc td{padding:1.3mm 3mm}.toc td:first-child{width:12mm}.toc td{font-weight:400;color:var(--tx)}.toc td:last-child{text-align:right;font-weight:700;color:var(--az)}
.pasos{counter-reset:p;list-style:none;margin:0}.pasos li{counter-increment:p;position:relative;padding-left:9mm;margin-bottom:2mm}
.pasos li:before{content:counter(p);position:absolute;left:0;top:0;width:6mm;height:6mm;border-radius:50%;background:var(--tq);color:var(--az);font-weight:700;text-align:center;font-size:9pt;line-height:6mm}
.cols{display:grid;grid-template-columns:1fr 1fr;gap:6mm}
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:3mm;margin:1mm 0 3mm}
.stat{background:var(--az);color:#fff;border-radius:3mm;padding:3mm;text-align:center}
.stat b{display:block;font-size:13pt;color:var(--tq)}.stat span{font-size:8pt;color:#cbd5e1}
.check{list-style:none;margin:0}.check li{padding-left:8mm;position:relative;margin-bottom:1.8mm}
.check li:before{content:"";position:absolute;left:0;top:.6mm;width:4.2mm;height:4.2mm;border:1.6px solid var(--tq);border-radius:1mm}
.hero{display:flex;gap:5mm;align-items:center;background:linear-gradient(90deg,var(--az),#274a7a);color:#fff;border-radius:4mm;padding:5mm 6mm;margin:2mm 0}
.hero b{color:var(--tq);font-size:20pt}.hero p{margin:0;color:#e2e8f0}
"""

def svg_tablero():
    items = [(60,55,"#e05252"),(150,40,"#f5b942"),(240,55,"#e05252"),(330,40,"#f5b942"),(420,55,"#3fb77a"),(510,40,"#3b82f6")]
    c = "".join(f'<circle cx="{x}" cy="{y}" r="15" fill="{col}"/><text x="{x}" y="{y+5}" text-anchor="middle" fill="#fff" font-size="14" font-weight="700">{i+1}</text>' for i,(x,y,col) in enumerate(items))
    return f'''<svg viewBox="0 0 570 150" width="100%" style="margin:2mm 0 1mm"><rect width="570" height="150" rx="14" fill="#0f2038"/>
<circle cx="130" cy="118" r="62" fill="none" stroke="#2EC4B6" stroke-width="5" stroke-dasharray="300 90" transform="rotate(130 130 118)"/>
<circle cx="440" cy="118" r="62" fill="none" stroke="#3b82f6" stroke-width="5" stroke-dasharray="240 150" transform="rotate(130 440 118)"/>
<text x="130" y="125" text-anchor="middle" fill="#fff" font-size="18" font-weight="700">km/h</text><text x="440" y="125" text-anchor="middle" fill="#fff" font-size="18" font-weight="700">rpm</text>
<rect x="235" y="100" width="100" height="36" rx="6" fill="#16304f" stroke="#2EC4B6"/><text x="285" y="124" text-anchor="middle" fill="#2EC4B6" font-size="15">12,345 km</text>{c}</svg>'''

def svg_timeline():
    pts = [("10 mil","6 meses"),("20 mil","12 meses"),("30 mil","18 meses"),("40 mil","24 meses"),("50 mil+","repetir")]
    out = '<svg viewBox="0 0 570 90" width="100%"><line x1="40" y1="35" x2="530" y2="35" stroke="#2EC4B6" stroke-width="5"/>'
    for i,(a,b) in enumerate(pts):
        x = 40+i*122.5
        out += f'<circle cx="{x}" cy="35" r="14" fill="#1D3557" stroke="#2EC4B6" stroke-width="3"/><text x="{x}" y="40" text-anchor="middle" fill="#fff" font-size="13" font-weight="700">{i+1}</text><text x="{x}" y="68" text-anchor="middle" fill="#1D3557" font-size="13" font-weight="700">{a} km</text><text x="{x}" y="83" text-anchor="middle" fill="#64748b" font-size="11">{b}</text>'
    return out + '</svg>'

def svg_llanta():
    return '''<svg viewBox="0 0 570 100" width="100%"><rect width="570" height="100" rx="10" fill="#F1F5F9"/>
<rect x="215" y="12" width="140" height="76" rx="14" fill="#cbd5e1" stroke="#1D3557" stroke-width="3"/>
<g fill="#1D3557"><rect x="190" y="18" width="22" height="26" rx="5"/><rect x="358" y="18" width="22" height="26" rx="5"/><rect x="190" y="58" width="22" height="26" rx="5"/><rect x="358" y="58" width="22" height="26" rx="5"/></g>
<text x="285" y="56" text-anchor="middle" fill="#1D3557" font-size="13" font-weight="700">Rotación en cruz</text>
<path d="M120 30 L170 70 M170 30 L120 70" stroke="#2EC4B6" stroke-width="4"/><text x="445" y="45" fill="#1D3557" font-size="13" font-weight="700">Revisa presión</text><text x="445" y="62" fill="#64748b" font-size="11">en frío, cada 2 semanas</text></svg>'''

def paginas(n, a, foto, k):
    fill = lambda t, pos='center': f'<div class="fill"><img src="{foto}" style="object-position:{pos}" alt=""><div>{t}</div></div>'
    nom, tipo = n[0], n[1]
    f = ficha(k, a)
    ev, hy = tipo == "E", tipo == "H"
    mod = f"{nom} {a}"
    marca = f'<img class="mark" src="{foto}" alt="">'
    P = []

    P.append(("Bienvenida", f"""{marca}<div class="kick">Manual de propietario</div><h1><span>👋</span>Bienvenida</h1>
<p>Gracias por elegir tu <b>Nissan {e(mod)}</b>. Este manual reúne lo esencial para conocer, usar y cuidar tu vehículo: tablero, mantenimiento, seguridad y qué hacer ante imprevistos.</p>
<div class="stats"><div class="stat"><b>{e(f['potencia'].split(' a ')[0])}</b><span>Potencia</span></div><div class="stat"><b>{e(f['pasajeros'])}</b><span>Pasajeros</span></div><div class="stat"><b>{e(f['propulsion'].split(' (')[0])}</b><span>Propulsión</span></div><div class="stat"><b>10 mil km</b><span>Servicio cada 6 meses</span></div></div>
{tarjetas([("🚗","Conoce tu vehículo","Identificación, ficha técnica y tablero de indicadores."),("🛠️","Cuídalo","Calendario de mantenimiento cada 10,000 km o 6 meses."),("🛡️","Maneja seguro","Cinturones, bolsas de aire, ISOFIX y asistencias."),("📱","Agenda desde la app","CareCar te deja pedir cita y ver tu historial.")])}
{{INDICE}}
{aviso("<b>Importante:</b> las características pueden variar según el equipamiento de tu unidad. Ante cualquier duda, consulta a tu asesor de servicio de Agencia360.", "warn")}"""))

    combustible = "Corriente alterna (cargador doméstico o de pared) y carga rápida CC" if ev else ("Gasolina; no requiere enchufarse" if hy else "Gasolina, según la etiqueta de la tapa del tanque")
    notas = ('<li>La autonomía real depende de temperatura, velocidad, carga y estilo de manejo.</li><li>Usa solo cargadores aprobados y no modifiques el sistema de alta tensión.</li><li>Evita dejar la batería al 0 % o al 100 % por varios días.</li>' if ev else
             ('<li>El motor de gasolina genera electricidad; las ruedas siempre las mueve el motor eléctrico.</li><li>El motor de gasolina puede encenderse y apagarse solo según la carga de la batería.</li><li>Frena con suavidad para recuperar energía.</li>' if hy else
              '<li>Usa el combustible indicado en la tapa del tanque.</li><li>Consulta la etiqueta del pilar para la presión de llantas.</li><li>Cambia el aceite con el grado que recomienda tu asesor.</li>'))
    P.append(("Ficha técnica", f"""{marca}<div class="kick">Datos de tu modelo</div><h1><span>📋</span>Ficha técnica</h1>
<div class="hero"><div style="font-size:30pt">⚙️</div><div><b>{e(f['potencia'])}</b><p>{e(f['motor'])} · {e(f['transmision'])}</p></div></div>
{tabla(["Característica","Valor"],[["Marca / modelo / año",f"Nissan {e(nom)} {a}"],["Carrocería",e(f['carroceria'])],["Propulsión",e(f['propulsion'])],["Motor / sistema",e(f['motor'])],["Potencia",e(f['potencia'])],["Transmisión",e(f['transmision'])],["Tracción",e(f['traccion'])],["Capacidad de pasajeros",e(f['pasajeros'])],["Carga" if ev else "Combustible",combustible]])}
<h2>Dónde confirmar los datos de tu unidad</h2>
{tarjetas([("🏷️","Etiqueta del pilar","Peso, presión de llantas y fecha de fabricación, en el pilar de la puerta del conductor."),("📄","Factura y tarjeta de circulación","Año, versión y número de serie oficiales."),("📱","App CareCar","Tu expediente muestra modelo, versión, placas y kilometraje."),("🏢","Agencia360","Tu asesor puede imprimirte la ficha oficial de tu unidad.")])}
<h2>{'Notas del sistema eléctrico' if ev else ('Notas del sistema e-Power' if hy else 'Notas')}</h2><ul>{notas}</ul>"""))

    P.append(("Identificación del vehículo", f"""<div class="kick">Tu {e(nom)}</div><h1><span>🔎</span>Identificación del vehículo</h1>
<img class="foto" style="height:82mm" src="{foto}" alt="Nissan {e(mod)}">
<p style="text-align:center;color:#64748b;font-size:9pt;margin-top:1mm">Nissan {e(mod)} · {e(f['carroceria'])}. Imagen ilustrativa; el color y los detalles de tu unidad pueden variar.</p>
<h2>Dónde encontrar el VIN (número de serie)</h2>
<p>El VIN tiene 17 caracteres y es único para tu vehículo. Lo necesitarás para seguros, trámites y para registrar tu unidad en CareCar.</p>
{tabla(["Ubicación","Cómo encontrarlo"],[["Parabrisas","Esquina inferior del lado del conductor, visible desde afuera."],["Pilar de la puerta","Etiqueta en el marco de la puerta del conductor."],["Chasis / compartimento","Grabado en la estructura; tu asesor puede mostrártelo."],["Documentos","Factura, tarjeta de circulación y póliza de seguro."]])}
<h2>Placas y tarjeta de circulación</h2>
<ul><li>Las placas van en la parte delantera y trasera; mantenlas limpias y legibles.</li><li>Conserva la tarjeta de circulación y la póliza de seguro vigentes dentro del vehículo.</li><li>Si cambias de placas, actualiza el dato en tu expediente de la agencia.</li></ul>
{aviso("<b>Consejo:</b> toma una foto de tu VIN y tus placas y guárdala en tu teléfono por si necesitas un trámite.", "info")}"""))

    if ev or hy:
        luces = [["🔋 Batería baja / nivel crítico","<span class='sem a'></span>","Planifica la carga cuanto antes; reduce el uso de clima y velocidad."],["⚡ Alta tensión / propulsión","<span class='sem r'></span>","Detente en un lugar seguro y apaga. No toques cables naranjas. Llama a la agencia."],["🔌 Cargando / error de carga" if ev else "⛽ Reserva de combustible","<span class='sem az'></span>" if ev else "<span class='sem a'></span>","Revisa cable y conector; prueba otro cargador." if ev else "Reposta pronto para que el generador siga trabajando."],["🌡️ Temperatura de batería","<span class='sem r'></span>","Deja enfriar el vehículo; no lo cargues hasta que se apague la luz."]]
    else:
        luces = [["🛢️ Presión de aceite","<span class='sem r'></span>","Detente y apaga el motor. Revisa el nivel; no continúes si sigue encendida."],["🔋 Batería / carga","<span class='sem r'></span>","Falla de carga. Apaga accesorios y acude a servicio de inmediato."],["🌡️ Temperatura del motor","<span class='sem r'></span>","Detente, apaga y deja enfriar. No abras el depósito caliente."]]
    luces += [["🅿️ Frenos","<span class='sem r'></span>","Revisa que el freno de mano esté liberado; si sigue, nivel de líquido y servicio."],["🛞 ABS","<span class='sem a'></span>","El ABS no actúa; el freno normal funciona. Maneja con precaución y agenda cita."],["🎈 Bolsas de aire","<span class='sem r'></span>","El sistema puede no activarse. Solicita revisión de inmediato."],["🛞 Presión de llantas (TPMS)","<span class='sem a'></span>","Revisa presiones en frío según la etiqueta del pilar y busca ponchaduras."],["⚙️ Servicio del sistema" if ev else "⚙️ Check engine","<span class='sem a'></span>","Fija: agenda diagnóstico pronto. Intermitente: reduce carga y acude a servicio."]]
    P.append(("Tablero e indicadores", f"""{marca}<div class="kick">Entiende tu tablero</div><h1><span>📟</span>Tablero e indicadores</h1>
<p>Al girar el encendido, las luces se iluminan unos segundos como autoprueba. Si alguna permanece o se enciende durante la marcha, actúa según el color.</p>
{svg_tablero()}
<div style="margin-bottom:2mm;font-size:9.4pt"><span class="sem r"></span>Rojo: detente y revisa &nbsp; <span class="sem a"></span>Ámbar: agenda servicio &nbsp; <span class="sem v"></span>Verde / <span class="sem az"></span>azul: informativo</div>
{tabla(["Indicador","Nivel","Qué hacer"],luces)}
{aviso("<b>Importante:</b> una luz roja requiere detenerte en un lugar seguro lo antes posible. No continúes manejando si el vehículo lo indica.", "danger")}"""))

    if ev:
        serv = [["10,000 km / 6 meses","Revisión general, filtro de cabina, llantas (presión, desgaste, rotación), frenos, líquidos, batería de 12 V"],["20,000 km / 12 meses","Lo anterior + líquido de frenos, limpieza de frenos, suspensión y dirección"],["30,000 km / 18 meses","Sistema de carga y cables, filtro de cabina, salud de la batería de tracción"],["40,000 km / 24 meses","Refrigerante del sistema de baterías, frenos, alineación y balanceo"],["50,000 km o más","Repetir el ciclo; suspensión, amortiguadores y software"]]
    else:
        serv = [["10,000 km / 6 meses","Cambio de aceite y filtro, rotación y revisión de llantas, frenos, líquidos, batería"],["20,000 km / 12 meses","Lo anterior + filtro de aire y de cabina, suspensión y dirección"],["30,000 km / 18 meses","Aceite y filtro, limpieza de frenos, bandas y mangueras, alineación y balanceo"],["40,000 km / 24 meses","Aceite y filtros, líquido de frenos, bujías según programa, transmisión"],["50,000 km o más","Repetir el ciclo; batería, amortiguadores y sistema de enfriamiento"]]
    P.append(("Mantenimiento programado", f"""<div class="kick">Cada 10,000 km o 6 meses</div><h1><span>🛠️</span>Mantenimiento programado</h1>
<p>Realiza el servicio cada <b>10,000 km o 6 meses</b>, lo que ocurra primero. Un servicio a tiempo conserva la garantía, el valor de reventa y tu seguridad.</p>
{svg_timeline()}
{tabla(["Intervalo","Qué se revisa o cambia"],serv)}
<h2>Qué incluye cada revisión</h2>
{tarjetas([("🔋" if ev else "🛢️","Batería y carga" if ev else "Aceite y filtro","Salud de la batería y del sistema de carga." if ev else "Aceite y filtro con el grado adecuado."),("🌬️","Filtros","Aire de motor y de cabina limpios mejoran desempeño y confort."),("🛑","Frenos","Balatas, discos y líquido, para frenar siempre a tiempo."),("🛞","Llantas","Presión, desgaste, rotación, alineación y balanceo."),("🔌","Batería de 12 V","Bornes limpios y carga correcta para accesorios."),("🔧","Suspensión","Amortiguadores, rótulas y dirección.")])}
{aviso("<b>Nota:</b> condiciones severas (polvo, tráfico denso, remolque) piden intervalos más cortos. Tu asesor confirmará el programa de tu unidad.", "warn")}
{fill("Un servicio a tiempo cuida tu inversión","70% 50%")}"""))

    if ev:
        flu = [["Refrigerante","Del tipo que indique el fabricante; no mezclar tipos."],["Líquido de frenos","Revisar en cada servicio; reemplazo según programa."],["Limpiaparabrisas","Revisar nivel y plumas con regularidad."],["Aceite de motor","No aplica: la tracción eléctrica no usa aceite de motor."]]
    else:
        flu = [["Aceite de motor","Sintético o semisintético de baja viscosidad (p. ej. 0W-20 o 5W-30); confirma el grado con tu asesor."],["Refrigerante","Del tipo que indique el fabricante; no mezclar tipos."],["Líquido de frenos","Revisar en cada servicio; reemplazo según programa."],["Limpiaparabrisas","Revisar nivel y plumas con regularidad."]]
    P.append(("Fluidos y llantas", f"""{marca}<div class="kick">Lo que cuida tu seguridad</div><h1><span>💧</span>Fluidos y llantas</h1>
<h2>Fluidos</h2>
{tabla(["Fluido","Recomendación general"],flu)}
<p>Revisa los niveles con el vehículo en piso plano y el motor frío (según corresponda). No rellenes de más.</p>
<h2>Llantas</h2>
{svg_llanta()}
{tabla(["Tema","Recomendación"],[["Presión","Ver etiqueta del pilar de la puerta del conductor (en frío)."],["Revisión","Cada 2 semanas y antes de un viaje largo, incluida la refacción."],["Rotación","Cada 10,000 km, junto con el servicio."],["Desgaste","Cambia la llanta si el dibujo es menor a 1.6 mm."],["Alineación y balanceo","Si el vehículo se jala hacia un lado o vibra el volante."]])}
{tarjetas([("🌡️","Presión en frío","Mide con las llantas frías; el calor sube la lectura."),("🪙","Prueba de la moneda","Un dibujo muy bajo reduce el agarre en lluvia.")])}
{aviso("<b>Importante:</b> una presión incorrecta aumenta el consumo, el desgaste y el riesgo de ponchadura.", "warn")}
{fill("Llantas y fluidos al día","20% 80%")}"""))

    P.append(("Seguridad", f"""<div class="kick">Viaja protegido</div><h1><span>🛡️</span>Seguridad</h1>
{tarjetas([("🪢","Cinturones de seguridad","Úsalos siempre, en todos los asientos. Banda sobre pecho y cadera, nunca sobre el abdomen."),("🎈","Bolsas de aire","Se activan en impactos fuertes. No pongas objetos sobre ellas ni niños cerca del tablero."),("👶","Sillas ISOFIX","Los anclajes están en el asiento trasero. Sigue las instrucciones de tu silla infantil."),("🚨","Asistencias","Frenos ABS, control de estabilidad, cámara y sensores de estacionamiento, según equipo.")])}
<img class="foto" style="height:62mm;margin:2mm 0" src="{foto}" alt="">
<div class="cols"><div><h2>Niños a bordo</h2><ul><li>Menores de 12 años en el asiento trasero con el sistema de retención adecuado.</li><li>Usa el seguro para niños de las puertas traseras.</li><li>Nunca dejes a un menor solo dentro del vehículo.</li></ul></div>
<div><h2>Antes de arrancar</h2><ul><li>Ajusta asiento, espejos y volante.</li><li>Abrocha tu cinturón y el de tus pasajeros.</li><li>Verifica que no haya luces rojas en el tablero.</li></ul></div></div>
{aviso("<b>Importante:</b> las asistencias son una ayuda, no sustituyen tu atención al conducir.", "info")}"""))

    P.append(("Checklist previo a viaje", f"""{marca}<div class="kick">Antes de salir a carretera</div><h1><span>✅</span>Checklist previo a viaje</h1>
<p>Dedica cinco minutos antes de un viaje largo. Marca cada punto en tu casa o en la gasolinera.</p>
<div class="cols"><div><h2>Exterior</h2><ul class="check"><li>Presión de las cuatro llantas y la refacción</li><li>Luces delanteras, traseras y de freno</li><li>Plumas y nivel de limpiaparabrisas</li><li>Vidrios y espejos limpios</li><li>Sin fugas debajo del vehículo</li></ul></div>
<div><h2>Interior</h2><ul class="check"><li>Cinturones de todos los pasajeros</li><li>Sin luces rojas en el tablero</li><li>{'Carga suficiente de la batería' if ev else 'Combustible suficiente para el trayecto'}</li><li>Aire acondicionado y desempañador</li><li>Documentos: tarjeta de circulación y póliza</li></ul></div></div>
<h2>Equipo de emergencia</h2>
<div class="grid c3"><div class="card"><div class="ic">🔺</div><div><b>Triángulos</b><p>Señalamiento reflejante.</p></div></div><div class="card"><div class="ic">🧯</div><div><b>Extintor</b><p>Vigente y accesible.</p></div></div><div class="card"><div class="ic">🩹</div><div><b>Botiquín</b><p>Curaciones básicas.</p></div></div><div class="card"><div class="ic">🔦</div><div><b>Linterna</b><p>Con pilas nuevas.</p></div></div><div class="card"><div class="ic">🔧</div><div><b>Gato y llave</b><p>Para cambiar llanta.</p></div></div><div class="card"><div class="ic">🔌</div><div><b>{'Cable de carga' if ev else 'Pasacorriente'}</b><p>En buen estado.</p></div></div></div>
<h2>Durante el viaje</h2>
<ul><li>Descansa 15 minutos cada dos horas y mantente hidratado.</li><li>Respeta límites de velocidad y distancia de seguimiento de tres segundos.</li><li>Ante cualquier luz roja, detente en un lugar seguro.</li></ul>
{aviso("<b>Consejo:</b> comparte tu ruta y hora estimada de llegada con alguien de confianza.", "info")}
{fill("Listo para salir a carretera","50% 40%")}"""))

    tips = [("⛽","Maneja suave","Acelera y frena de forma gradual; ahorras energía y frenos."),("🛞","Presión correcta","Llantas bien infladas reducen el consumo."),("🧳","Menos peso","Quita lo que no uses de la cajuela y el techo."),("🌬️","Clima con medida","Usa el aire y la calefacción solo cuando haga falta.")]
    if ev or hy:
        tips += [("🔋","Frenado regenerativo","Aprovéchalo para recuperar energía al soltar el acelerador."),("🔌","Carga inteligente" if ev else "Batería en equilibrio","Evita niveles extremos de batería por mucho tiempo.")]
    else:
        tips += [("⏱️","Evita el ralentí","No dejes el motor encendido sin necesidad."),("🗺️","Planea tu ruta","Evita tráfico y arrancones innecesarios.")]
    P.append(("Conducción eficiente y cuidados", f"""{marca}<div class="kick">Cuida tu {e(nom)}</div><h1><span>🌿</span>Conducción eficiente y cuidados</h1>
{tarjetas(tips)}
<h2>Cuidados del vehículo</h2>
{tabla(["Tema","Recomendación"],[["Lavado","Con agua y shampoo automotriz; evita el sol directo."],["Pintura","Aplica cera o sellador cada 3 a 6 meses."],["Interior","Aspira y limpia con productos suaves; protege el tablero del sol."],["Estacionamiento","Prefiere la sombra y no dejes objetos de valor a la vista."],["Uso poco frecuente","Si no lo usas, enciéndelo y muévelo cada semana."],["Almacenamiento largo","Llantas infladas y batería con carga adecuada."]])}
<div class="hero"><div style="font-size:28pt">🌿</div><div><b>Conduce con suavidad</b><p>Una conducción suave y llantas bien infladas reducen el consumo en cada tanque o carga.</p></div></div>
{aviso("<b>Consejo:</b> un vehículo bien cuidado consume menos y vale más al venderlo.", "info")}"""))

    p_enc = "Revisa que la batería de 12 V tenga carga y que el sistema esté listo (indicador READY o equivalente)." if (ev or hy) else "Revisa la batería (bornes y carga), el combustible y que la palanca esté en P o N."
    P.append(("Solución de problemas", f"""<div class="kick">Qué hacer ante imprevistos</div><h1><span>🧰</span>Solución de problemas</h1>
<div class="cols"><div><h2>No enciende</h2><ol class="pasos"><li>{e(p_enc)}</li><li>Pisa el freno (o el embrague) al arrancar.</li><li>Intenta con la llave de repuesto.</li><li>Si no responde, llama a la agencia o a asistencia vial.</li></ol></div>
<div><h2>Ponchadura</h2><ol class="pasos"><li>Detente en terreno plano y enciende las intermitentes.</li><li>Coloca el freno de mano y señalamientos.</li><li>Afloja las tuercas, levanta con el gato en el punto indicado.</li><li>Cambia la llanta y aprieta en cruz. Revisa la presión.</li></ol></div></div>
<div class="cols"><div><h2>Sobrecalentamiento</h2><ol class="pasos"><li>Apaga el aire acondicionado y detente de forma segura.</li><li>Apaga el vehículo; no abras el depósito caliente.</li><li>Espera a que enfríe y revisa el refrigerante.</li><li>Si persiste, solicita servicio.</li></ol></div>
<div><h2>Batería baja</h2><ol class="pasos"><li>Apaga luces y accesorios.</li><li>Cables pasacorriente: positivo con positivo, negativo a masa.</li><li>Deja cargar unos minutos antes de apagar.</li><li>Si se repite, revisa la batería en servicio.</li></ol></div></div>
<h2>Teléfonos útiles</h2>
{tabla(["Servicio","Número"],[["Emergencias","911"],["Denuncia ciudadana","089"],["Asistencia de la agencia","Desde la app CareCar o en el contacto de la última página"]])}
{aviso("<b>Importante:</b> en caso de accidente enciende las intermitentes, revisa a los ocupantes y llama al <b>911</b>.", "danger")}
{aviso("En eléctricos e híbridos, ante un golpe fuerte o humo, aléjate del vehículo y no toques el sistema de alta tensión." if (ev or hy) else "No fumes ni uses fuego cerca del compartimento del motor ante una fuga de combustible.", "warn")}"""))

    P.append(("Garantía y servicio", f"""{marca}<div class="kick">Tu respaldo</div><h1><span>🧾</span>Garantía y servicio</h1>
<p>Tu vehículo cuenta con la garantía del fabricante en los términos de tu póliza de garantía. Para conservarla, realiza tus servicios programados en Agencia360.</p>
<h2>Cómo agendar tu cita en la app CareCar</h2>
<ol class="pasos"><li>Entra a <b>CareCar</b> con tu correo y contraseña.</li><li>Elige tu vehículo y toca <b>Agendar cita</b>.</li><li>Selecciona el tipo de servicio, la fecha y el horario disponible.</li><li>Confirma y recibirás tu comprobante.</li></ol>
{tarjetas([("📅","Citas","Agenda, consulta o cancela tus citas desde la app."),("🗂️","Historial de servicios","Cada servicio queda registrado en tu expediente."),("🎫","Ticket","Al llegar, muestra tu ticket o comprobante de cita."),("📘","Manuales","Consulta y descarga este manual desde la app.")])}
<h2>Qué cubre y qué no</h2>
{tabla(["Cubre","No cubre"],[["Defectos de fabricación y materiales.","Desgaste normal (balatas, llantas, plumas)."],["Reparaciones hechas en la agencia.","Daños por mal uso, accidentes o modificaciones."],["Revisiones de acuerdo con el programa.","Servicios omitidos o hechos fuera de la red autorizada."]])}
{aviso("<b>Nota:</b> los términos de la garantía son los de tu póliza oficial; este documento es solo una guía.", "info")}
{fill("Agenda tu cita desde la app CareCar","60% 30%")}"""))

    P.append(("Contacto y aviso legal", f"""<div class="kick">Estamos para ayudarte</div><h1><span>📞</span>Contacto de la agencia</h1>
{tarjetas([("☎️","Teléfono","+52 000 000 0000"),("✉️","Correo","contacto@agencia.example.com"),("📍","Dirección","Av. Ejemplo 123, Col. Centro, Ciudad de Ejemplo, México"),("🕘","Horario","Lunes a viernes de 9:00 a 18:00; sábados de 9:00 a 14:00")])}
{aviso("Los datos de contacto son de ejemplo y serán reemplazados por los de la agencia.", "warn")}
<h2>Aviso legal</h2>
<p style="font-size:9.3pt;color:#475569">Este documento es una guía de referencia elaborada por Agencia360 · CareCar para tu Nissan {e(mod)}. La información, imágenes y valores aquí contenidos son orientativos y pueden variar según versión, año y mercado. En caso de diferencia, <b>prevalece el manual oficial del fabricante</b> que acompaña a tu vehículo. Agencia360 no se hace responsable por el uso indebido de esta información. Nissan y los nombres de modelos son marcas de sus respectivos propietarios.</p>
{fill("<span><span style=\"display:block;font-size:17pt\">Agencia360 · CareCar</span><span style=\"display:block;color:#cbd5e1;font-weight:400\">Gracias por confiar en nosotros.</span></span>")}"""))
    return P

def construir_html(nom_clave, anio, foto_uri, version=""):
    n = M[nom_clave]
    nom = n[0]
    paginas_ = paginas(n, anio, foto_uri, nom_clave)
    total = len(paginas_) + 1
    indice = "<h2>Contenido</h2>" + tabla(["N.º","Sección","Página"],
        [[str(i+1), e(t), str(i+2)] for i, (t, _) in enumerate(paginas_)]).replace("<table>", '<table class="toc">')
    enc = lambda: f'<div class="enc"><span><i></i>Agencia360 · CareCar</span><span>Manual de propietario · Nissan {e(nom)} {anio}</span></div>'
    pie = lambda k: f'<div class="pie"><span>Agencia360 · CareCar · Doc. MP-{nom_clave.upper()}-{anio}</span><b>Página {k} de {total}</b></div>'
    portada = f"""<div class="page portada"><div class="bg"><img src="{foto_uri}" alt=""></div><div class="franja"></div><div class="franja2"></div>
<div class="marca"><small>MANUAL DE PROPIETARIO</small><span class="gran">Nissan</span><span class="mod">{e(nom)}</span>
<div class="pills"><span class="pill s">Año modelo {anio}</span><span class="pill">{e(ficha(nom_clave, anio)['propulsion'])}</span><span class="pill">Edición 2026 · v1.0</span></div></div>
<div class="abajo"><div><b>Agencia360 · CareCar</b>Tu guía para conocer y cuidar tu vehículo</div><div class="doc">DOCUMENTO<br><b>MP-{nom_clave.upper()}-{anio}</b></div></div><div class="baja"></div></div>"""
    cuerpo = [portada]
    for i, (_, c) in enumerate(paginas_):
        cuerpo.append(f'<div class="page">{enc()}{c.replace("{INDICE}", indice if i == 0 else "")}{pie(i+2)}</div>')
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
