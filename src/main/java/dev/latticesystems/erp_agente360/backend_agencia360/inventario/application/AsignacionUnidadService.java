package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Venta;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facturación elige la unidad de la venta de un prospecto (ERP-1.0). Ya no
 * existe el apartado: la unidad pasa de Disponible a Vendido en ese momento
 * (decisión del PO del 19 de octubre). Mientras la venta no se registre,
 * Facturación puede cambiar la unidad y la anterior regresa a Disponible.
 */
@Service
public class AsignacionUnidadService {

	private final VehiculoRepository vehiculoRepository;
	private final VentaRepository ventaRepository;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;

	@Autowired
	public AsignacionUnidadService(VehiculoRepository vehiculoRepository, VentaRepository ventaRepository,
			ApplicationEventPublisher eventos) {
		this(vehiculoRepository, ventaRepository, eventos, Clock.systemUTC());
	}

	AsignacionUnidadService(VehiculoRepository vehiculoRepository, VentaRepository ventaRepository,
			ApplicationEventPublisher eventos, Clock reloj) {
		this.vehiculoRepository = vehiculoRepository;
		this.ventaRepository = ventaRepository;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si no existe la unidad o el prospecto (404)
	 * @throws ConflictoException
	 *             si la unidad ya no está disponible o la venta ya se registró
	 *             (409)
	 */
	@Transactional
	public VentaVista asignar(Long prospectoId, Long vehiculoId, UsuarioAutenticado actor) {
		Instant ahora = Instant.now(reloj);
		if (!ventaRepository.existeProspecto(prospectoId)) {
			throw new RecursoNoEncontradoException("No existe el prospecto " + prospectoId);
		}
		// La fila de la unidad queda bloqueada: dos personas no la venden a la vez.
		Vehiculo unidad = vehiculoRepository.buscarParaActualizar(vehiculoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		Optional<Venta> existente = ventaRepository.findByProspectoId(prospectoId);
		if (existente.isPresent()) {
			Venta venta = existente.get();
			Vehiculo anterior = venta.getVehiculo();
			if (venta.cambiarUnidad(unidad, actor.id(), ahora)) {
				avisarCambio(anterior);
				avisarCambio(unidad);
			}
			return VentaVista.de(venta);
		}
		Venta venta = ventaRepository.save(Venta.asignar(prospectoId, unidad, actor.id(), ahora));
		avisarCambio(unidad);
		return VentaVista.de(venta);
	}

	private void avisarCambio(Vehiculo unidad) {
		if (unidad != null) {
			eventos.publishEvent(new VehiculoCambiado(unidad.getId()));
		}
	}
}
