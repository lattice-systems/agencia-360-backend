package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CalculadoraMantenimiento.Evaluacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CalculadoraMantenimiento.PlanDeServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.KilometrajeService.Estado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.LecturaKm;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PlanMantenimiento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PlanMantenimientoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoProcesableException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan de mantenimiento del vehículo, próximo servicio y registro del
 * kilometraje del cliente (V2-M05.01, M05.02, M05.03 y M05.06). Un vehículo
 * ajeno responde 404, como pide el contrato.
 */
@Service
public class MantenimientoAppService {

	private static final String CARPETA_FOTOS = "lecturas-km";
	private static final Set<String> IMAGENES = Set.of("image/jpeg", "image/png", "image/webp");

	private final VehiculosDeClientes vehiculos;
	private final PlanMantenimientoRepository planes;
	private final KilometrajeService kilometraje;
	private final AlmacenArchivos archivos;
	private final Clock reloj;
	private final ZoneId zona;

	@Autowired
	public MantenimientoAppService(VehiculosDeClientes vehiculos, PlanMantenimientoRepository planes,
			KilometrajeService kilometraje, AlmacenArchivos archivos) {
		this(vehiculos, planes, kilometraje, archivos, Clock.systemUTC(), ZoneId.of("America/Mexico_City"));
	}

	MantenimientoAppService(VehiculosDeClientes vehiculos, PlanMantenimientoRepository planes,
			KilometrajeService kilometraje, AlmacenArchivos archivos, Clock reloj, ZoneId zona) {
		this.vehiculos = vehiculos;
		this.planes = planes;
		this.kilometraje = kilometraje;
		this.archivos = archivos;
		this.reloj = reloj;
		this.zona = zona;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 */
	@Transactional(readOnly = true)
	public PlanMantenimientoApp plan(Long clienteId, Long vehiculoId) {
		VehiculoDeCliente vehiculo = propio(clienteId, vehiculoId);
		Estado estado = kilometraje.estado(vehiculo);
		List<PlanDeServicio> plan = planes.delModelo(vehiculo.modelo(), vehiculo.anio()).stream()
				.map(MantenimientoAppService::aServicio).toList();
		LocalDate hoy = LocalDate.now(reloj.withZone(zona));
		Evaluacion evaluacion = CalculadoraMantenimiento.evaluar(plan, estado.ordenes(), estado.kmActual(),
				estado.promedioKmDiario(), hoy);
		return new PlanMantenimientoApp(vehiculoId, estado.kmActual(), estado.actualizadoEn(), estado.fuente(),
				estado.promedioKmDiario(), evaluacion.servicios(), evaluacion.proximo());
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si falta el km o no está entre 1 y 2 000 000 (400)
	 * @throws NoProcesableException
	 *             con {@code KM_INVALIDO} si es menor al oficial o a la última
	 *             lectura del cliente (422)
	 */
	@Transactional
	public LecturaKmApp registrarLectura(Long clienteId, Long vehiculoId, Integer km) {
		if (km == null) {
			throw new IllegalArgumentException("El kilometraje es obligatorio");
		}
		return registrarLectura(clienteId, vehiculoId, km, null);
	}

	/** Foto del tablero que prueba una lectura de km. */
	public record FotoLectura(String tipoContenido, byte[] contenido) {
	}

	/**
	 * Registra la lectura con una foto opcional. El km se valida antes de guardar
	 * la foto, así un km inválido no deja archivos.
	 *
	 * @param foto
	 *            imagen JPEG, PNG o WebP; {@code null} si no hay
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si falta el km, no está entre 1 y 2 000 000 o la foto no es una
	 *             imagen (400)
	 * @throws NoProcesableException
	 *             con {@code KM_INVALIDO} si es menor al oficial o a la última
	 *             lectura del cliente (422)
	 */
	@Transactional
	public LecturaKmApp registrarLectura(Long clienteId, Long vehiculoId, Integer km, FotoLectura foto) {
		if (km == null) {
			throw new IllegalArgumentException("El kilometraje es obligatorio");
		}
		VehiculoDeCliente vehiculo = propio(clienteId, vehiculoId);
		if (foto == null) {
			return LecturaKmApp.de(kilometraje.registrar(vehiculo, km), null);
		}
		if (foto.tipoContenido() == null || !IMAGENES.contains(foto.tipoContenido())) {
			throw new IllegalArgumentException("La foto debe ser una imagen JPEG, PNG o WebP");
		}
		LecturaKm.exigirKmValido(km);
		kilometraje.exigirNoMenorAlConocido(vehiculo, km, null);
		String clave = archivos.guardar(CARPETA_FOTOS, foto.tipoContenido(), foto.contenido()).clave();
		try {
			return LecturaKmApp.de(kilometraje.registrar(vehiculo, km, clave), archivos.urlTemporal(clave));
		} catch (RuntimeException fallo) {
			archivos.borrar(clave);
			throw fallo;
		}
	}

	private VehiculoDeCliente propio(Long clienteId, Long vehiculoId) {
		return vehiculos.buscarPorId(vehiculoId).filter(encontrado -> encontrado.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
	}

	private static PlanDeServicio aServicio(PlanMantenimiento plan) {
		return new PlanDeServicio(plan.getId(), plan.getNombre(), plan.getKm(), plan.getMeses(),
				plan.getPrecioEstimado(), List.copyOf(plan.getConceptos()));
	}
}
