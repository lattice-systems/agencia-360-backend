package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.HorarioSucursal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Sucursal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.SucursalRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sucursales de la agencia (V2-M27.02). «Abierto ahora» se calcula aquí con la
 * hora de la agencia y no con la del teléfono.
 */
@Service
public class SucursalesAppService {

	private static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	private final SucursalRepository repository;
	private final Clock reloj;

	@Autowired
	public SucursalesAppService(SucursalRepository repository) {
		this(repository, Clock.system(ZONA_AGENCIA));
	}

	SucursalesAppService(SucursalRepository repository, Clock reloj) {
		this.repository = repository;
		this.reloj = reloj;
	}

	/** Las sucursales activas, la principal primero; vacío si no hay. */
	@Transactional(readOnly = true)
	public List<SucursalApp> listar() {
		LocalDateTime ahora = LocalDateTime.now(reloj.withZone(ZONA_AGENCIA));
		return repository.findByActivaTrueOrderByOrdenAscIdAsc().stream().map(s -> vista(s, ahora)).toList();
	}

	private static SucursalApp vista(Sucursal sucursal, LocalDateTime ahora) {
		List<HorarioSucursalApp> horarios = sucursal.getHorarios().stream().map(h -> vista(h, ahora)).toList();
		boolean abierta = horarios.stream().anyMatch(HorarioSucursalApp::abiertoAhora);
		return new SucursalApp(sucursal.getId(), sucursal.getNombre(), sucursal.getDireccion(),
				sucursal.getLat().doubleValue(), sucursal.getLng().doubleValue(), sucursal.getTelefono(),
				sucursal.getCorreo(), abierta, horarios);
	}

	private static HorarioSucursalApp vista(HorarioSucursal horario, LocalDateTime ahora) {
		return new HorarioSucursalApp(horario.departamento(), horario.dias(), horario.abre(), horario.cierra(),
				horario.abiertoEn(ahora));
	}
}
