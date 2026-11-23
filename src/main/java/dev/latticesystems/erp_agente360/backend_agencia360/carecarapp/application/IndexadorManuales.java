package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Manual;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ManualRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indexa los manuales por página para el asistente (V2-M24.02, ADR 0007).
 * Primero guarda el texto de cada página (rápido y sin proveedor); después
 * calcula los vectores despacio, con un tope diario, para no pasar la cuota
 * gratis del proveedor de IA.
 */
@Service
public class IndexadorManuales {

	private static final Logger LOG = LoggerFactory.getLogger(IndexadorManuales.class);
	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

	private final ManualRepository manualRepository;
	private final AlmacenManuales almacen;
	private final ExtractorTextoPdf extractor;
	private final FragmentosManual fragmentos;
	private final ProveedorIA proveedor;
	private final boolean automatico;
	private final int vectoresPorDia;
	private final int vectoresPorVuelta;
	private final long pausaMilis;

	private LocalDate dia = LocalDate.now(ZONA);
	private int vectoresHoy;

	public IndexadorManuales(ManualRepository manualRepository, AlmacenManuales almacen, ExtractorTextoPdf extractor,
			FragmentosManual fragmentos, ProveedorIA proveedor,
			@Value("${app.asistente.indexado.automatico:true}") boolean automatico,
			@Value("${app.asistente.indexado.vectores-por-dia:500}") int vectoresPorDia,
			@Value("${app.asistente.indexado.vectores-por-vuelta:60}") int vectoresPorVuelta,
			@Value("${app.asistente.indexado.pausa-milis:700}") long pausaMilis) {
		this.manualRepository = manualRepository;
		this.almacen = almacen;
		this.extractor = extractor;
		this.fragmentos = fragmentos;
		this.proveedor = proveedor;
		this.automatico = automatico;
		this.vectoresPorDia = vectoresPorDia;
		this.vectoresPorVuelta = vectoresPorVuelta;
		this.pausaMilis = pausaMilis;
	}

	/**
	 * Guarda el texto de cada página del manual. Reindexar no duplica: las páginas
	 * que cambian pierden su vector y se vuelven a calcular.
	 *
	 * @return cuántas páginas con texto quedaron
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int indexarTexto(long manualId) {
		Manual manual = manualRepository.findById(manualId).orElse(null);
		if (manual == null || manual.getArchivo() == null) {
			return 0;
		}
		byte[] pdf = almacen.leer(manual.getArchivo()).orElse(null);
		if (pdf == null) {
			LOG.warn("El manual {} no tiene su PDF en el almacén; no se indexa", manualId);
			return 0;
		}
		List<String> paginas;
		try {
			paginas = extractor.paginas(pdf);
		} catch (IllegalArgumentException ex) {
			LOG.warn("El PDF del manual {} no se pudo leer; no se indexa", manualId);
			return 0;
		}
		List<FragmentosManual.Fragmento> conTexto = new ArrayList<>();
		for (int i = 0; i < paginas.size(); i++) {
			if (!paginas.get(i).isBlank()) {
				conTexto.add(new FragmentosManual.Fragmento(manualId, i + 1, paginas.get(i)));
			}
		}
		if (conTexto.isEmpty()) {
			LOG.warn("El manual {} no tiene texto (¿escaneado?); el asistente no lo podrá usar", manualId);
		}
		fragmentos.reemplazar(manualId, conTexto);
		LOG.info("Manual {} indexado: {} páginas con texto de {}", manualId, conTexto.size(), paginas.size());
		return conTexto.size();
	}

	/**
	 * Calcula vectores pendientes hasta el tope diario. Se detiene si el proveedor
	 * no calcula vectores o falla (por ejemplo, sin cuota).
	 *
	 * @return cuántos vectores calculó
	 */
	public int completarVectores(int maximo) {
		reiniciarDiaSiCambio();
		int disponibles = Math.min(maximo, vectoresPorDia - vectoresHoy);
		int calculados = 0;
		for (FragmentosManual.Pendiente pendiente : fragmentos.sinVector(Math.max(disponibles, 0))) {
			try {
				var vector = proveedor.embeber(pendiente.texto());
				if (vector.isEmpty()) {
					return calculados;
				}
				fragmentos.ponerVector(pendiente.id(), vector.get());
				calculados++;
				vectoresHoy++;
				Thread.sleep(pausaMilis);
			} catch (ProveedorIANoDisponibleException ex) {
				LOG.warn("Vectores en pausa: {}", ex.getMessage());
				return calculados;
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return calculados;
			}
		}
		return calculados;
	}

	/** Indexa los manuales que falten y avanza con los vectores. */
	@Scheduled(initialDelayString = "${app.asistente.indexado.inicio:PT2M}", fixedDelayString = "${app.asistente.indexado.cada:PT30M}")
	public void indexarPendientes() {
		if (!automatico) {
			return;
		}
		for (Long manualId : fragmentos.manualesSinFragmentos()) {
			indexarTexto(manualId);
		}
		int calculados = completarVectores(vectoresPorVuelta);
		if (calculados > 0) {
			LOG.info("Vectores calculados en esta vuelta: {}", calculados);
		}
	}

	private synchronized void reiniciarDiaSiCambio() {
		LocalDate hoy = LocalDate.now(ZONA);
		if (!hoy.equals(dia)) {
			dia = hoy;
			vectoresHoy = 0;
		}
	}
}
