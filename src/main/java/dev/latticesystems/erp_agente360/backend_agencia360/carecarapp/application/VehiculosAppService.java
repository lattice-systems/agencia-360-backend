package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Manual;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ManualRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vehículos del cliente y sus manuales en la app (APP-3.1). El cliente solo ve
 * los suyos: un vehículo ajeno responde 404, como pide el contrato.
 */
@Service
public class VehiculosAppService {

	private final VehiculosDeClientes vehiculos;
	private final ManualRepository manualRepository;
	private final FirmaManuales firma;

	public VehiculosAppService(VehiculosDeClientes vehiculos, ManualRepository manualRepository, FirmaManuales firma) {
		this.vehiculos = vehiculos;
		this.manualRepository = manualRepository;
		this.firma = firma;
	}

	public List<VehiculoApp> listar(Long clienteId) {
		return vehiculos.deCliente(clienteId).stream().map(VehiculoApp::de).toList();
	}

	/**
	 * Los manuales de la marca, el tipo y el modelo del vehículo, con su URL
	 * temporal. Si el vehículo no tiene marca, se buscan solo por tipo y modelo.
	 *
	 * @param urlBase
	 *            dirección del backend hasta {@code /api/v1/app/manuales}, sin
	 *            diagonal final
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 */
	@Transactional(readOnly = true)
	public List<ManualApp> manuales(Long clienteId, Long vehiculoId, String urlBase) {
		VehiculoDeCliente vehiculo = vehiculos.buscarPorId(vehiculoId)
				.filter(encontrado -> encontrado.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		List<Manual> manuales = vehiculo.marca() == null
				? manualRepository.sinMarca(vehiculo.modelo(), vehiculo.anio())
				: manualRepository.buscar(vehiculo.marca(), vehiculo.modelo(), vehiculo.anio()).stream().toList();
		return manuales.stream().map(manual -> {
			FirmaManuales.UrlFirmada firmada = firma.firmar(manual.getId());
			String url = urlBase + "/" + manual.getId() + "/archivo?expira=" + firmada.expira() + "&firma="
					+ firmada.firma();
			return new ManualApp(manual.getId(), manual.getMarca(), manual.getModelo(), manual.getAnio(),
					manual.titulo(), url);
		}).toList();
	}
}
