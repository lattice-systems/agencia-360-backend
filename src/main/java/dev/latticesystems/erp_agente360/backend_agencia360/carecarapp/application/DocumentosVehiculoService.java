package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DocumentoVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.OrigenDocumento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoDocumento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.DocumentoVehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.apache.pdfbox.Loader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DocumentosVehiculoService {
	public static final int TAMANO_MAXIMO = 5 * 1024 * 1024;
	private static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	private final VehiculosDeClientes vehiculos;
	private final DocumentoVehiculoRepository documentos;
	private final AlmacenArchivos archivos;

	public DocumentosVehiculoService(VehiculosDeClientes vehiculos, DocumentoVehiculoRepository documentos,
			AlmacenArchivos archivos) {
		this.vehiculos = vehiculos;
		this.documentos = documentos;
		this.archivos = archivos;
	}

	@Transactional
	public DocumentoApp guardar(Long clienteId, Long vehiculoId, TipoDocumento tipo, String titulo, LocalDate venceEn,
			byte[] contenido) {
		vehiculos.buscarPorId(vehiculoId).filter(v -> v.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		if (contenido.length > TAMANO_MAXIMO || !esFotoJpeg(contenido)) {
			throw new IllegalArgumentException("Selecciona una imagen JPEG de hasta 5 MB");
		}
		if (titulo != null && titulo.strip().length() > 80) {
			throw new IllegalArgumentException("El título admite hasta 80 caracteres");
		}
		return persistir(vehiculoId, tipo, titulo, venceEn, OrigenDocumento.CLIENTE, "image/jpeg", contenido);
	}

	@Transactional
	public DocumentoApp guardarDeAgencia(Long vehiculoId, TipoDocumento tipo, LocalDate venceEn, byte[] contenido) {
		vehiculos.buscarPorId(vehiculoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		if (tipo != TipoDocumento.FACTURA && tipo != TipoDocumento.GARANTIA) {
			throw new IllegalArgumentException("La agencia solo puede cargar la factura o la póliza de garantía");
		}
		if (contenido.length > TAMANO_MAXIMO || !esPdfLegible(contenido)) {
			throw new IllegalArgumentException("Selecciona un PDF legible de hasta 5 MB");
		}
		return persistir(vehiculoId, tipo, null, venceEn, OrigenDocumento.AGENCIA, "application/pdf", contenido);
	}

	@Transactional(readOnly = true)
	public String urlArchivo(Long clienteId, Long documentoId) {
		String clave = documentos.findById(documentoId)
				.filter(doc -> vehiculos.buscarPorId(doc.getVehiculoClienteId())
						.filter(v -> v.propietario().id().equals(clienteId)).isPresent())
				.map(DocumentoVehiculo::getArchivoUrl)
				.orElseThrow(() -> new RecursoNoEncontradoException("Archivo inexistente: " + documentoId));
		return archivos.urlTemporal(clave);
	}

	private DocumentoApp persistir(Long vehiculoId, TipoDocumento tipo, String titulo, LocalDate venceEn,
			OrigenDocumento origen, String mime, byte[] contenido) {
		var documento = new DocumentoVehiculo(vehiculoId, tipo, titulo, null, null, venceEn, origen, null, null);
		String clave = archivos.guardar("documentos-vehiculo", mime, contenido).clave();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int estado) {
				if (estado == STATUS_ROLLED_BACK) {
					archivos.borrar(clave);
				}
			}
		});
		documento.adjuntarArchivo(clave);
		return DocumentoApp.de(documentos.save(documento), LocalDate.now(ZONA_AGENCIA));
	}

	private static boolean esPdfLegible(byte[] contenido) {
		try (var pdf = Loader.loadPDF(contenido)) {
			return !pdf.isEncrypted() && pdf.getNumberOfPages() > 0;
		} catch (IOException ex) {
			return false;
		}
	}

	private static boolean esFotoJpeg(byte[] contenido) {
		if (contenido.length < 5 || (contenido[0] & 0xff) != 0xff || (contenido[1] & 0xff) != 0xd8
				|| (contenido[contenido.length - 2] & 0xff) != 0xff
				|| (contenido[contenido.length - 1] & 0xff) != 0xd9) {
			return false;
		}
		try (var entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(contenido))) {
			var lector = ImageIO.getImageReadersByFormatName("JPEG").next();
			try {
				lector.setInput(entrada);
				return lector.getWidth(0) <= 4096 && lector.getHeight(0) <= 4096 && lector.read(0) != null;
			} finally {
				lector.dispose();
			}
		} catch (IOException ex) {
			return false;
		}
	}

}
