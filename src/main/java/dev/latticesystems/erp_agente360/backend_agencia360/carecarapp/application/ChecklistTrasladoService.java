package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ChecklistTraslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoTraslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Gasolina;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Traslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ChecklistTrasladoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checklist de recolección y entrega (V2-M17.04): el chofer lo carga con sus
 * fotos y firma y el cliente dueño lo consulta con URL firmadas de vida corta.
 */
@Service
public class ChecklistTrasladoService {

	static final String CARPETA_FOTOS = "checklist-fotos";
	static final String CARPETA_FIRMAS = "checklist-firmas";
	private static final Set<String> IMAGENES = Set.of("image/jpeg", "image/png", "image/webp");

	/** Una imagen que llegó en la carga. */
	public record ArchivoSubido(String tipoContenido, byte[] contenido) {
	}

	private final ChecklistTrasladoRepository checklists;
	private final TrasladosAppService traslados;
	private final AlmacenArchivos archivos;

	public ChecklistTrasladoService(ChecklistTrasladoRepository checklists, TrasladosAppService traslados,
			AlmacenArchivos archivos) {
		this.checklists = checklists;
		this.traslados = traslados;
		this.archivos = archivos;
	}

	/**
	 * El chofer carga el checklist; si ya había uno lo reemplaza y borra sus
	 * archivos.
	 *
	 * @param fotos
	 *            hasta 10 imágenes JPEG, PNG o WebP
	 * @param firma
	 *            imagen de la firma; {@code null} si no hay
	 * @throws IllegalArgumentException
	 *             si un dato no es válido o un archivo no es imagen (400)
	 * @throws RecursoNoEncontradoException
	 *             si el traslado no existe (404)
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} si el traslado sigue programado o
	 *             está cancelado (409)
	 */
	@Transactional
	public ChecklistTrasladoApp cargar(Long trasladoId, Integer km, String gasolina, String notas,
			List<ArchivoSubido> fotos, ArchivoSubido firma) {
		Traslado traslado = traslados.existente(trasladoId);
		if (km == null) {
			throw new IllegalArgumentException("El kilometraje es obligatorio");
		}
		Gasolina nivel = Gasolina.desde(gasolina);
		if (fotos.size() > ChecklistTraslado.FOTOS_MAXIMO) {
			throw new IllegalArgumentException("Se admiten hasta " + ChecklistTraslado.FOTOS_MAXIMO + " fotos");
		}
		fotos.forEach(ChecklistTrasladoService::exigirImagen);
		if (firma != null) {
			exigirImagen(firma);
		}
		if (traslado.getEstado() == EstadoTraslado.PROGRAMADO || traslado.getEstado() == EstadoTraslado.CANCELADO) {
			throw new ConflictoException("El checklist se llena cuando el traslado ya salió",
					CodigosError.TRANSICION_INVALIDA);
		}

		List<String> nuevas = new ArrayList<>();
		try {
			List<String> clavesFotos = new ArrayList<>();
			for (ArchivoSubido foto : fotos) {
				String clave = guardar(CARPETA_FOTOS, foto);
				nuevas.add(clave);
				clavesFotos.add(clave);
			}
			String claveFirma = null;
			if (firma != null) {
				claveFirma = guardar(CARPETA_FIRMAS, firma);
				nuevas.add(claveFirma);
			}
			Optional<ChecklistTraslado> existente = checklists.findByTrasladoId(trasladoId);
			List<String> sobrantes = List.of();
			ChecklistTraslado checklist;
			if (existente.isPresent()) {
				checklist = existente.get();
				sobrantes = checklist.reemplazar(km, nivel, notas, clavesFotos, claveFirma);
			} else {
				checklist = ChecklistTraslado.registrar(trasladoId, km, nivel, notas, clavesFotos, claveFirma);
			}
			ChecklistTraslado guardado = checklists.saveAndFlush(checklist);
			sobrantes.forEach(archivos::borrar);
			return aApp(guardado);
		} catch (RuntimeException fallo) {
			nuevas.forEach(archivos::borrar);
			throw fallo;
		}
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el traslado es de otro cliente o todavía no tiene checklist
	 *             (404)
	 */
	@Transactional(readOnly = true)
	public ChecklistTrasladoApp obtener(Long clienteId, Long trasladoId) {
		traslados.propio(clienteId, trasladoId);
		return checklists.findByTrasladoId(trasladoId).map(this::aApp).orElseThrow(
				() -> new RecursoNoEncontradoException("El traslado " + trasladoId + " no tiene checklist"));
	}

	private ChecklistTrasladoApp aApp(ChecklistTraslado checklist) {
		List<String> fotos = checklist.getFotos().stream().map(archivos::urlTemporal).toList();
		String firma = checklist.getFirmaUrl() == null ? null : archivos.urlTemporal(checklist.getFirmaUrl());
		return new ChecklistTrasladoApp(checklist.getTrasladoId(), checklist.getKm(), checklist.getGasolina(),
				checklist.getNotas(), fotos, firma, checklist.getUpdatedAt());
	}

	private String guardar(String carpeta, ArchivoSubido archivo) {
		return archivos.guardar(carpeta, archivo.tipoContenido(), archivo.contenido()).clave();
	}

	private static void exigirImagen(ArchivoSubido archivo) {
		if (archivo.tipoContenido() == null || !IMAGENES.contains(archivo.tipoContenido())) {
			throw new IllegalArgumentException("Las fotos y la firma deben ser imágenes JPEG, PNG o WebP");
		}
	}
}
