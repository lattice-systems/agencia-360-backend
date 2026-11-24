package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CalculadoraMantenimiento.OrdenCerrada;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FuenteLectura;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.LecturaKm;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.LecturaKmRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.ServicioDelVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoProcesableException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Kilometraje de un vehículo (V2-M05.03): el oficial del taller, las lecturas
 * del cliente y el promedio diario. El oficial es el mayor entre el del
 * vehículo, el de sus órdenes cerradas y las lecturas del taller; nunca se
 * sobrescribe. No revisa de quién es el vehículo: eso lo hace quien lo usa.
 */
@Service
public class KilometrajeService {

	/** Tope de órdenes que se cruzan; un auto no llega a tantas. */
	private static final int MAXIMO_ORDENES = 200;

	private final LecturaKmRepository lecturas;
	private final HistorialDeServicios historial;
	private final Clock reloj;
	private final ZoneId zona;

	@Autowired
	public KilometrajeService(LecturaKmRepository lecturas, HistorialDeServicios historial) {
		this(lecturas, historial, Clock.systemUTC(), ZoneId.of("America/Mexico_City"));
	}

	KilometrajeService(LecturaKmRepository lecturas, HistorialDeServicios historial, Clock reloj, ZoneId zona) {
		this.lecturas = lecturas;
		this.historial = historial;
		this.reloj = reloj;
		this.zona = zona;
	}

	/**
	 * Kilometraje conocido del vehículo.
	 *
	 * @param kmActual
	 *            {@code null} si nadie lo ha capturado
	 * @param fuente
	 *            {@code taller} o {@code cliente}
	 * @param promedioKmDiario
	 *            {@code null} si faltan datos para calcularlo
	 */
	public record Estado(Integer kmActual, LocalDate actualizadoEn, String fuente, BigDecimal promedioKmDiario,
			List<OrdenCerrada> ordenes) {
	}

	private record Punto(int km, LocalDate fecha) {
	}

	public Estado estado(VehiculoDeCliente vehiculo) {
		List<OrdenCerrada> ordenes = ordenesCerradas(vehiculo.id());
		List<LecturaKm> delCliente = lecturas.findByVehiculoClienteIdOrderByRegistradaEnAscIdAsc(vehiculo.id());
		List<Punto> oficiales = new ArrayList<>();
		if (vehiculo.kilometraje() != null) {
			oficiales.add(new Punto(vehiculo.kilometraje(), null));
		}
		ordenes.forEach(orden -> oficiales.add(new Punto(orden.km(), orden.fecha())));
		delCliente.stream().filter(lectura -> lectura.getFuente() == FuenteLectura.TALLER)
				.forEach(lectura -> oficiales.add(new Punto(lectura.getKm(), dia(lectura))));
		List<Punto> delClientePuntos = delCliente.stream()
				.filter(lectura -> lectura.getFuente() == FuenteLectura.CLIENTE)
				.map(lectura -> new Punto(lectura.getKm(), dia(lectura))).toList();

		Punto oficial = mayor(oficiales);
		Punto cliente = mayor(delClientePuntos);
		boolean gobiernaElCliente = cliente != null && (oficial == null || cliente.km() > oficial.km());
		Punto actual = gobiernaElCliente ? cliente : oficial;

		List<Punto> conFecha = new ArrayList<>(delClientePuntos);
		conFecha.addAll(oficiales);
		return new Estado(actual == null ? null : actual.km(), actual == null ? null : actual.fecha(),
				actual == null ? null : (gobiernaElCliente ? "cliente" : "taller"), promedio(conFecha), ordenes);
	}

	/**
	 * Guarda una lectura del cliente con el kilometraje de hoy.
	 *
	 * @throws NoProcesableException
	 *             con {@code KM_INVALIDO} si el km es menor al oficial o a la
	 *             última lectura del cliente (422)
	 * @throws IllegalArgumentException
	 *             si el km no está entre 1 y 2 000 000 (400)
	 */
	public LecturaKm registrar(VehiculoDeCliente vehiculo, int km) {
		return registrar(vehiculo, km, null);
	}

	/**
	 * Igual que {@link #registrar(VehiculoDeCliente, int)} y deja la clave del
	 * archivo con la foto que prueba la lectura.
	 *
	 * @param fotoClave
	 *            clave de {@code AlmacenArchivos}; {@code null} si no hay foto
	 */
	public LecturaKm registrar(VehiculoDeCliente vehiculo, int km, String fotoClave) {
		LecturaKm nueva = LecturaKm.delCliente(vehiculo.id(), km, reloj.instant());
		exigirNoMenorAlConocido(vehiculo, km, null);
		nueva.adjuntarFoto(fotoClave);
		return lecturas.saveAndFlush(nueva);
	}

	/**
	 * @param lecturaPropia
	 *            lectura que no cuenta (la de la carga que se está corrigiendo);
	 *            {@code null} si ninguna
	 * @throws NoProcesableException
	 *             con {@code KM_INVALIDO} si el km es menor al oficial o a la
	 *             última lectura del cliente (422)
	 */
	public void exigirNoMenorAlConocido(VehiculoDeCliente vehiculo, int km, Long lecturaPropia) {
		int conocido = kmConocido(vehiculo, lecturaPropia);
		if (km < conocido) {
			throw new NoProcesableException("El kilometraje no puede ser menor a " + conocido,
					CodigosError.KM_INVALIDO);
		}
	}

	private int kmConocido(VehiculoDeCliente vehiculo, Long lecturaPropia) {
		int mayor = vehiculo.kilometraje() == null ? 0 : vehiculo.kilometraje();
		for (OrdenCerrada orden : ordenesCerradas(vehiculo.id())) {
			mayor = Math.max(mayor, orden.km());
		}
		for (LecturaKm lectura : lecturas.findByVehiculoClienteIdOrderByRegistradaEnAscIdAsc(vehiculo.id())) {
			if (!lectura.getId().equals(lecturaPropia)) {
				mayor = Math.max(mayor, lectura.getKm());
			}
		}
		return mayor;
	}

	private List<OrdenCerrada> ordenesCerradas(Long vehiculoId) {
		return historial.deVehiculo(vehiculoId, null, PageRequest.of(0, MAXIMO_ORDENES)).getContent().stream()
				.filter(this::estaCerrada).map(orden -> new OrdenCerrada(orden.fecha(), orden.kilometraje())).toList();
	}

	private boolean estaCerrada(ServicioDelVehiculo orden) {
		return orden.estatus() != null && orden.estatus().cerrada();
	}

	private LocalDate dia(LecturaKm lectura) {
		return lectura.getRegistradaEn().atZone(zona).toLocalDate();
	}

	private static Punto mayor(List<Punto> puntos) {
		return puntos.stream().max(Comparator.comparingInt(Punto::km).thenComparing(Punto::fecha,
				Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
	}

	/**
	 * Km ganados por día entre el punto de menor y el de mayor km, ambos con fecha.
	 */
	private static BigDecimal promedio(List<Punto> puntos) {
		List<Punto> fechados = puntos.stream().filter(punto -> punto.fecha() != null).toList();
		Punto primero = fechados.stream().min(Comparator.comparingInt(Punto::km).thenComparing(Punto::fecha))
				.orElse(null);
		Punto ultimo = mayor(fechados);
		if (primero == null || ultimo == null) {
			return null;
		}
		long dias = ChronoUnit.DAYS.between(primero.fecha(), ultimo.fecha());
		int recorrido = ultimo.km() - primero.km();
		if (dias <= 0 || recorrido <= 0) {
			return null;
		}
		return BigDecimal.valueOf(recorrido).divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP);
	}
}
