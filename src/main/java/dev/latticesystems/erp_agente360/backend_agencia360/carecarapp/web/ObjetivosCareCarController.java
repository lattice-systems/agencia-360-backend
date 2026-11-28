package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.AnaliticaAppJdbc;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.AnaliticaAppJdbc.Diagnostico;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.AnaliticaAppJdbc.Embudo;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ObjetivosCareCarController {

	private final AnaliticaAppJdbc datos;

	public ObjetivosCareCarController(AnaliticaAppJdbc datos) {
		this.datos = datos;
	}

	@GetMapping(value = "/api/v1/analitica/objetivos", produces = MediaType.TEXT_HTML_VALUE)
	@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GERENTE_MARKETING')")
	public ResponseEntity<String> objetivos() {
		Embudo embudo = datos.embudo();
		Diagnostico diagnostico = datos.diagnostico();
		String citas = embudo.vistos() == 0 ? "Sin eventos consentidos" : """
				Disponibilidad: %d · Franja: %d · Confirmadas: %d · Atendidas: %d
				""".formatted(embudo.vistos(), embudo.elegidos(), embudo.confirmados(), embudo.atendidos());
		long muestras = diagnostico.muestras();
		String tiempos = muestras == 0
				? "Sin muestras de rendimiento"
				: """
						Promedio de arranque: %d ms · Primera pantalla útil: %d ms ·
						Cuadros perdidos estimados: %d (%d medidos)
						""".formatted(diagnostico.arranque() / muestras, diagnostico.primera() / muestras,
						diagnostico.perdidos(), diagnostico.cuadros());
		long crashesAndroid = diagnostico.crashesAndroid();
		String diagnosticos = diagnostico.flutter() == 0 && diagnostico.asincronos() == 0 && crashesAndroid == 0
				? "Sin reportes recibidos"
				: "Errores Flutter: %d · Errores asíncronos: %d · Crashes Android: %d".formatted(diagnostico.flutter(),
						diagnostico.asincronos(), crashesAndroid);
		String panel = """
				<!doctype html>
				<html lang="es"><head><meta charset="UTF-8">
				<meta name="viewport" content="width=device-width, initial-scale=1">
				<title>Objetivos de CareCar</title></head><body><main>
				<h1>Objetivos de CareCar</h1>
				<p>Últimos 30 días. Las órdenes abiertas representan el estado actual.</p>
				<table><thead><tr><th scope="col">Objetivo</th><th scope="col">Indicador</th></tr></thead><tbody>
				<tr><th scope="row">O1 · Ficha técnica</th>
				<td>Consultar la matriz de cumplimiento y sus pruebas</td></tr>
				<tr><th scope="row">O2 · Citas</th><td>%s<br>Participación por canal: sin medición</td></tr>
				<tr><th scope="row">O3 · Taller en vivo</th>
				<td>Órdenes abiertas con etapas: %d. Llamadas: sin medición</td></tr>
				<tr><th scope="row">O4 · Retención</th><td>Retorno a mantenimiento: sin medición</td></tr>
				<tr><th scope="row">O5 · Ingresos</th>
				<td>Solicitudes de estreno en la app: %d. Pagos y pedidos: sin medición</td></tr>
				<tr><th scope="row">O6 · Expediente</th><td>Documentos y avisos: sin medición</td></tr>
				<tr><th scope="row">O7 · Satisfacción</th><td>NPS y respuesta de agencia: sin medición</td></tr>
				<tr><th scope="row">O8 · Calidad</th>
				<td>%s<br>%s<br>Consultar el CI y su informe de cobertura</td></tr>
				<tr><th scope="row">O9 · Accesibilidad</th>
				<td>Consultar la auditoría de accesibilidad e idiomas</td></tr>
				<tr><th scope="row">O10 · Entrega</th>
				<td>Consultar los PR integrados y la revisión de cada hito</td></tr>
				</tbody></table></main></body></html>
				""".formatted(citas, datos.ordenesEnVivo(), datos.solicitudesEstreno(), diagnosticos, tiempos);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.contentType(MediaType.parseMediaType("text/html;charset=UTF-8")).body(panel);
	}
}
