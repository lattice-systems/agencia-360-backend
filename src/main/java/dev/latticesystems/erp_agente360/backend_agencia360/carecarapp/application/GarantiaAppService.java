package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Garantia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.GarantiaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garantía del vehículo (V2-M10.01): cada componente cubierto con su fecha y km
 * límite contra el km actual del auto. El personal registra las coberturas
 * mientras el ERP siga congelado. Un vehículo ajeno responde 404.
 */
@Service
public class GarantiaAppService {

	private final VehiculosDeClientes vehiculos;
	private final GarantiaRepository garantias;
	private final KilometrajeService kilometraje;
	private final Clock reloj;
	private final ZoneId zona;

	@Autowired
	public GarantiaAppService(VehiculosDeClientes vehiculos, GarantiaRepository garantias,
			KilometrajeService kilometraje) {
		this(vehiculos, garantias, kilometraje, Clock.systemUTC(), ServicioApp.ZONA_AGENCIA);
	}

	GarantiaAppService(VehiculosDeClientes vehiculos, GarantiaRepository garantias, KilometrajeService kilometraje,
			Clock reloj, ZoneId zona) {
		this.vehiculos = vehiculos;
		this.garantias = garantias;
		this.kilometraje = kilometraje;
		this.reloj = reloj;
		this.zona = zona;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 */
	@Transactional(readOnly = true)
	public GarantiaApp garantia(Long clienteId, Long vehiculoId) {
		VehiculoDeCliente vehiculo = vehiculos.buscarPorId(vehiculoId)
				.filter(encontrado -> encontrado.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		Integer km = kilometraje.estado(vehiculo).kmActual();
		LocalDate hoy = LocalDate.now(reloj.withZone(zona));
		List<CoberturaApp> coberturas = garantias.findByVehiculoClienteIdOrderByVenceEnAscIdAsc(vehiculoId).stream()
				.map(garantia -> PoliticaGarantia.evaluar(garantia, km, hoy)).toList();
		return new GarantiaApp(vehiculoId, km, coberturas);
	}

	/**
	 * Registra la cobertura de un componente (personal de servicio).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe (404)
	 * @throws IllegalArgumentException
	 *             si un dato no es válido (400)
	 */
	@Transactional
	public CoberturaApp registrar(Long vehiculoId, String componente, String descripcion, LocalDate iniciaEn,
			LocalDate venceEn, Integer kmLimite) {
		Garantia nueva = Garantia.registrar(vehiculoId, componente, descripcion, iniciaEn, venceEn, kmLimite);
		VehiculoDeCliente vehiculo = vehiculos.buscarPorId(vehiculoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		Garantia guardada = garantias.save(nueva);
		return PoliticaGarantia.evaluar(guardada, kilometraje.estado(vehiculo).kmActual(),
				LocalDate.now(reloj.withZone(zona)));
	}
}
