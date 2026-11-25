package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoTraslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Traslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.TrasladoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seguimiento de los traslados (V2-M17.03): el cliente dueño de la cita los
 * consulta y el chofer, que es personal, los mueve y reporta su ubicación. Cada
 * cambio avisa en vivo al cliente.
 */
@Service
public class TrasladosAppService {

	/** Avisos en vivo que recibe el cliente. */
	public static final String EVENTO_ESTADO = "traslado.estado";
	public static final String EVENTO_UBICACION = "traslado.ubicacion";

	private final TrasladoRepository traslados;
	private final CitaRepository citas;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;

	@Autowired
	public TrasladosAppService(TrasladoRepository traslados, CitaRepository citas, ApplicationEventPublisher eventos) {
		this(traslados, citas, eventos, Clock.systemUTC());
	}

	TrasladosAppService(TrasladoRepository traslados, CitaRepository citas, ApplicationEventPublisher eventos,
			Clock reloj) {
		this.traslados = traslados;
		this.citas = citas;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el traslado no existe o su cita es de otro cliente (404)
	 */
	@Transactional(readOnly = true)
	public SeguimientoTrasladoApp obtener(Long clienteId, Long trasladoId) {
		return SeguimientoTrasladoApp.de(propio(clienteId, trasladoId));
	}

	/**
	 * El chofer mueve el traslado al siguiente paso.
	 *
	 * @throws IllegalArgumentException
	 *             si el estado falta o no existe (400)
	 * @throws RecursoNoEncontradoException
	 *             si el traslado no existe (404)
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} si el paso no sigue al estado
	 *             actual (409)
	 */
	@Transactional
	public SeguimientoTrasladoApp cambiarEstado(Long choferId, Long trasladoId, String estado) {
		if (estado == null) {
			throw new IllegalArgumentException("El estado es obligatorio");
		}
		EstadoTraslado nuevo = EstadoTraslado.desde(estado);
		Traslado traslado = existente(trasladoId);
		traslado.cambiarEstado(nuevo, choferId);
		traslados.saveAndFlush(traslado);
		Map<String, Object> datos = identificacion(traslado);
		datos.put("estado", nuevo.valor());
		avisar(traslado, EVENTO_ESTADO, datos);
		return SeguimientoTrasladoApp.de(traslado);
	}

	/**
	 * El chofer reporta dónde va.
	 *
	 * @throws IllegalArgumentException
	 *             si falta un dato o está fuera de rango (400)
	 * @throws RecursoNoEncontradoException
	 *             si el traslado no existe (404)
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} si no va en camino ni de regreso
	 *             (409)
	 */
	@Transactional
	public void reportarUbicacion(Long trasladoId, BigDecimal lat, BigDecimal lng) {
		Traslado traslado = existente(trasladoId);
		traslado.reportarUbicacion(lat, lng, reloj.instant());
		traslados.saveAndFlush(traslado);
		Map<String, Object> datos = new HashMap<>();
		datos.put("trasladoId", traslado.getId());
		datos.put("lat", traslado.getChoferLat());
		datos.put("lng", traslado.getChoferLng());
		datos.put("actualizadaEn", traslado.getUbicacionEn().toString());
		avisar(traslado, EVENTO_UBICACION, datos);
	}

	/** El traslado, solo si su cita es del cliente. */
	Traslado propio(Long clienteId, Long trasladoId) {
		Traslado traslado = existente(trasladoId);
		citas.findByIdAndClienteId(traslado.getCitaId(), clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el traslado " + trasladoId));
		return traslado;
	}

	Traslado existente(Long trasladoId) {
		return traslados.findById(trasladoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el traslado " + trasladoId));
	}

	private Map<String, Object> identificacion(Traslado traslado) {
		Map<String, Object> datos = new HashMap<>();
		datos.put("trasladoId", traslado.getId());
		datos.put("citaId", traslado.getCitaId());
		datos.put("tipo", traslado.getTipo().valor());
		return datos;
	}

	private void avisar(Traslado traslado, String evento, Map<String, Object> datos) {
		Long clienteId = citas.findById(traslado.getCitaId()).orElseThrow().getClienteId();
		eventos.publishEvent(new AvisoAlCliente(clienteId, evento, datos));
	}
}
