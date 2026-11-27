package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.DocumentoVehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documentos del vehículo con su estado de vencimiento (V2-M09.01). El estado
 * se calcula aquí con el día de la agencia y no con el del teléfono. Un
 * vehículo ajeno responde 404, igual que uno que no existe.
 */
@Service
public class DocumentosAppService {

	private static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	private final VehiculosDeClientes vehiculos;
	private final DocumentoVehiculoRepository documentos;
	private final Clock reloj;

	@Autowired
	public DocumentosAppService(VehiculosDeClientes vehiculos, DocumentoVehiculoRepository documentos) {
		this(vehiculos, documentos, Clock.system(ZONA_AGENCIA));
	}

	DocumentosAppService(VehiculosDeClientes vehiculos, DocumentoVehiculoRepository documentos, Clock reloj) {
		this.vehiculos = vehiculos;
		this.documentos = documentos;
		this.reloj = reloj;
	}

	/**
	 * Por vencimiento, los que no tienen fecha al final; vacía si no hay.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 */
	@Transactional(readOnly = true)
	public List<DocumentoApp> listar(Long clienteId, Long vehiculoId) {
		vehiculos.buscarPorId(vehiculoId).filter(vehiculo -> vehiculo.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		LocalDate hoy = LocalDate.now(reloj.withZone(ZONA_AGENCIA));
		return documentos.delVehiculo(vehiculoId).stream().map(documento -> DocumentoApp.de(documento, hoy)).toList();
	}
}
