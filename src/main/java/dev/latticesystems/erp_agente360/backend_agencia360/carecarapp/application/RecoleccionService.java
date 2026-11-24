package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DireccionCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoTraslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Traslado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.TrasladoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recolección y entrega a domicilio al agendar una cita (V2-M17.02): cotiza y
 * crea los dos traslados, de recoger y de entregar, dentro de la transacción de
 * quien agenda.
 */
@Service
public class RecoleccionService {

	private static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	private final DireccionesAppService direcciones;
	private final TrasladoRepository traslados;
	private final PoliticaRecoleccion politica;
	private final HorarioTaller horario;
	private final Clock reloj;

	@Autowired
	public RecoleccionService(DireccionesAppService direcciones, TrasladoRepository traslados,
			PoliticaRecoleccion politica, HorarioTaller horario) {
		this(direcciones, traslados, politica, horario, Clock.system(ZONA_AGENCIA));
	}

	RecoleccionService(DireccionesAppService direcciones, TrasladoRepository traslados, PoliticaRecoleccion politica,
			HorarioTaller horario, Clock reloj) {
		this.direcciones = direcciones;
		this.traslados = traslados;
		this.politica = politica;
		this.horario = horario;
		this.reloj = reloj;
	}

	/**
	 * Costo y franjas de recolección para la dirección y el día.
	 *
	 * @throws IllegalArgumentException
	 *             si falta un dato, el taller no abre ese día, ya pasó o pasa del
	 *             límite de anticipación (400)
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException
	 *             si la dirección no existe o no es del cliente (404)
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoProcesableException
	 *             con {@code FUERA_DE_COBERTURA} si la dirección ya no está en la
	 *             zona (422)
	 */
	@Transactional(readOnly = true)
	public CotizacionRecoleccionApp cotizar(Long clienteId, Long direccionId, LocalDate fecha) {
		if (direccionId == null || fecha == null) {
			throw new IllegalArgumentException("La dirección y la fecha son obligatorias");
		}
		LocalDate hoy = LocalDate.now(reloj);
		if (fecha.isBefore(hoy) || fecha.isAfter(hoy.plusDays(horario.diasDeAnticipacion()))) {
			throw new IllegalArgumentException("La fecha está fuera del plazo para agendar");
		}
		if (horario.franjasPorDia().getOrDefault(fecha.getDayOfWeek(), java.util.List.of()).isEmpty()) {
			throw new IllegalArgumentException("El taller no abre ese día");
		}
		DireccionCliente direccion = cubierta(clienteId, direccionId);
		BigDecimal costo = politica.costoPorTraslado(direccion.getLat(), direccion.getLng());
		PoliticaRecoleccion.Ventana recoger = politica.ventanaRecoleccion();
		PoliticaRecoleccion.Ventana entregar = politica.ventanaEntrega();
		return new CotizacionRecoleccionApp(direccionId, fecha, costo, costo.multiply(BigDecimal.valueOf(2)),
				new CotizacionRecoleccionApp.Franja(recoger.inicio(), recoger.fin()),
				new CotizacionRecoleccionApp.Franja(entregar.inicio(), entregar.fin()));
	}

	/**
	 * La dirección del cliente, ya revisada: es suya y está en la zona de
	 * cobertura. Se llama antes de guardar la cita para no dejarla a medias.
	 *
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException
	 *             si no existe o no es del cliente (404)
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoProcesableException
	 *             con {@code FUERA_DE_COBERTURA} (422)
	 */
	@Transactional(readOnly = true)
	public DireccionCliente cubierta(Long clienteId, Long direccionId) {
		DireccionCliente direccion = direcciones.propia(clienteId, direccionId);
		direcciones.exigirCobertura(direccion);
		return direccion;
	}

	/** Crea los traslados de recoger y entregar de una cita ya guardada. */
	@Transactional
	public RecoleccionApp programar(DireccionCliente direccion, Cita cita) {
		BigDecimal costo = politica.costoPorTraslado(direccion.getLat(), direccion.getLng());
		PoliticaRecoleccion.Ventana recoger = politica.ventanaRecoleccion();
		PoliticaRecoleccion.Ventana entregar = politica.ventanaEntrega();
		List<TrasladoApp> creados = traslados
				.saveAll(List.of(
						Traslado.programar(cita.getId(), direccion.getId(), TipoTraslado.RECOGER, costo,
								cita.getFecha(), recoger.inicio(), recoger.fin()),
						Traslado.programar(cita.getId(), direccion.getId(), TipoTraslado.ENTREGAR, costo,
								cita.getFecha(), entregar.inicio(), entregar.fin())))
				.stream().map(TrasladoApp::de).toList();
		return new RecoleccionApp(direccion.getId(), costo.multiply(BigDecimal.valueOf(2)), creados);
	}
}
