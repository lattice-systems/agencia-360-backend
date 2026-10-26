package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Manual;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ManualRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Carga de los manuales (APP-3.1). Los sube el Gerente de marketing; mientras
 * no haya pantalla para hacerlo, los carga el script de datos de ejemplo. Solo
 * PDF, de 20 MB como máximo. Si ya hay un manual de esa marca, tipo y modelo,
 * se reemplaza.
 */
@Service
public class ManualesService {

	private static final byte[] INICIO_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	private final ManualRepository manualRepository;
	private final AlmacenManuales almacen;
	private final FirmaManuales firma;

	public ManualesService(ManualRepository manualRepository, AlmacenManuales almacen, FirmaManuales firma) {
		this.manualRepository = manualRepository;
		this.almacen = almacen;
		this.firma = firma;
	}

	/** Contenido del PDF para descargarlo. */
	public record Archivo(String nombre, byte[] contenido) {
	}

	/**
	 * @throws IllegalArgumentException
	 *             si un dato no es válido o el archivo no es un PDF de hasta 20 MB
	 *             (400)
	 */
	@Transactional
	public ManualVista subir(String marca, String modelo, int anio, byte[] contenido, UsuarioAutenticado actor) {
		Manual datos = Manual.nuevo(marca, modelo, anio);
		if (contenido == null || contenido.length == 0 || contenido.length > Manual.TAMANO_MAXIMO
				|| !esPdf(contenido)) {
			throw new IllegalArgumentException("El manual debe ser un PDF de hasta 20 MB");
		}
		Manual manual = manualRepository.buscar(datos.getMarca(), datos.getModelo(), datos.getAnio()).orElse(datos);
		String anterior = manual.getArchivo();
		String clave = almacen.guardar(contenido);
		borrarSiSeRevierte(clave);
		manual.ponerArchivo(clave, contenido.length, actor.id());
		Manual guardado = manualRepository.save(manual);
		if (anterior != null) {
			borrarAlConfirmar(anterior);
		}
		return ManualVista.de(guardado);
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la firma no es válida, ya venció o el manual no existe (404)
	 */
	@Transactional(readOnly = true)
	public Archivo descargar(Long manualId, long expira, String firmaRecibida) {
		RecursoNoEncontradoException noExiste = new RecursoNoEncontradoException(
				"El enlace del manual no es válido o ya venció; vuelve a pedir la lista de manuales");
		if (!firma.esValida(manualId, expira, firmaRecibida)) {
			throw noExiste;
		}
		Manual manual = manualRepository.findById(manualId).orElseThrow(() -> noExiste);
		byte[] contenido = almacen.leer(manual.getArchivo()).orElseThrow(() -> noExiste);
		return new Archivo(manual.titulo() + ".pdf", contenido);
	}

	private static boolean esPdf(byte[] contenido) {
		return contenido.length >= INICIO_PDF.length
				&& Arrays.equals(contenido, 0, INICIO_PDF.length, INICIO_PDF, 0, INICIO_PDF.length);
	}

	/** Si la transacción falla, el archivo nuevo no queda huérfano. */
	private void borrarSiSeRevierte(String clave) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int estado) {
					if (estado != STATUS_COMMITTED) {
						almacen.borrar(clave);
					}
				}
			});
		}
	}

	/** El archivo reemplazado se borra solo si el cambio se guardó. */
	private void borrarAlConfirmar(String clave) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					almacen.borrar(clave);
				}
			});
		}
	}
}
