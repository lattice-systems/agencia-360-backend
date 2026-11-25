package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Entity
@Table(name = "orden_servicio")
public class OrdenServicio extends Auditable {

	static final int MAXIMO_RENGLONES = 100;
	static final int KILOMETRAJE_MAXIMO = 2_000_000;
	static final BigDecimal TOTAL_MAXIMO = new BigDecimal("9999999999.99");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "vehiculo_cliente_id", nullable = false)
	private Long vehiculoClienteId;

	@Column(nullable = false)
	private LocalDate fecha;

	@Column(nullable = false)
	private int kilometraje;

	@Column(name = "atendido_por", nullable = false)
	private Long atendidoPor;

	@Column(name = "mano_de_obra", nullable = false, precision = 12, scale = 2)
	private BigDecimal manoDeObra;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal refacciones;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotal;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal iva;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private EstatusOrden estatus;

	@Column(name = "fecha_cierre")
	private Instant fechaCierre;

	@Column(name = "cita_id")
	private Long citaId;

	@Column(name = "asesor_id")
	private Long asesorId;

	@Column(name = "entrega_estimada")
	private Instant entregaEstimada;

	@OneToMany(mappedBy = "ordenServicio", cascade = CascadeType.PERSIST)
	@OrderBy("id asc")
	private List<OrdenManoDeObra> renglonesManoDeObra = new ArrayList<>();

	@OneToMany(mappedBy = "ordenServicio", cascade = CascadeType.PERSIST)
	@OrderBy("id asc")
	private List<OrdenRefaccion> renglonesRefaccion = new ArrayList<>();

	protected OrdenServicio() {
	}

	private OrdenServicio(Long vehiculoClienteId, LocalDate fecha, int kilometraje, Long atendidoPor) {
		this.vehiculoClienteId = vehiculoClienteId;
		this.fecha = fecha;
		this.kilometraje = kilometraje;
		this.atendidoPor = atendidoPor;
	}

	public static OrdenServicio registrar(Long vehiculoClienteId, LocalDate fecha, int kilometraje, Long atendidoPor,
			List<RenglonNuevo> renglones, ZonedDateTime ahora) {
		Objects.requireNonNull(vehiculoClienteId, "vehiculoClienteId");
		Objects.requireNonNull(atendidoPor, "atendidoPor");
		Objects.requireNonNull(ahora, "ahora");
		if (fecha == null) {
			throw new IllegalArgumentException("La fecha del servicio es obligatoria");
		}
		if (fecha.isAfter(ahora.toLocalDate())) {
			throw new IllegalArgumentException("La fecha del servicio no puede ser posterior a hoy");
		}
		if (kilometraje < 0 || kilometraje > KILOMETRAJE_MAXIMO) {
			throw new IllegalArgumentException("El kilometraje debe estar entre 0 y " + KILOMETRAJE_MAXIMO);
		}
		if (renglones == null || renglones.isEmpty()) {
			throw new IllegalArgumentException("El servicio debe tener al menos un renglón");
		}
		if (renglones.size() > MAXIMO_RENGLONES) {
			throw new IllegalArgumentException("El servicio admite hasta " + MAXIMO_RENGLONES + " renglones");
		}
		OrdenServicio orden = new OrdenServicio(vehiculoClienteId, fecha, kilometraje, atendidoPor);
		renglones.forEach(nuevo -> orden.agregar(RenglonOrden.de(orden, nuevo)));
		orden.recalcularMontos();
		orden.estatus = EstatusOrden.TERMINADO;
		orden.fechaCierre = ahora.toInstant();
		return orden;
	}

	/**
	 * Abre la orden cuando el vehículo entra al taller (V2-M04.01): queda
	 * {@code RECIBIDO}, sin renglones y con montos en cero, a nombre del asesor que
	 * la recibe. La primera etapa la registra quien la abre.
	 */
	public static OrdenServicio abrir(Long vehiculoClienteId, int kilometraje, Long asesorId, Long citaId,
			Instant entregaEstimada, ZonedDateTime ahora) {
		Objects.requireNonNull(vehiculoClienteId, "vehiculoClienteId");
		Objects.requireNonNull(asesorId, "asesorId");
		Objects.requireNonNull(ahora, "ahora");
		if (kilometraje < 0 || kilometraje > KILOMETRAJE_MAXIMO) {
			throw new IllegalArgumentException("El kilometraje debe estar entre 0 y " + KILOMETRAJE_MAXIMO);
		}
		OrdenServicio orden = new OrdenServicio(vehiculoClienteId, ahora.toLocalDate(), kilometraje, asesorId);
		orden.asesorId = asesorId;
		orden.citaId = citaId;
		orden.estimarEntrega(entregaEstimada, ahora.toInstant());
		orden.manoDeObra = BigDecimal.ZERO.setScale(2);
		orden.refacciones = BigDecimal.ZERO.setScale(2);
		Montos montos = Montos.desglosar(BigDecimal.ZERO.setScale(2));
		orden.subtotal = montos.subtotal();
		orden.iva = montos.iva();
		orden.total = montos.total();
		orden.estatus = EstatusOrden.RECIBIDO;
		return orden;
	}

	/**
	 * Lleva la orden al estatus de la etapa. Al quedar lista para entrega o
	 * entregada se cierra en ese momento; si ya estaba cerrada conserva su cierre.
	 *
	 * @throws IllegalStateException
	 *             si la orden ya se entregó
	 */
	public void pasarA(EtapaTaller etapa, Instant ahora) {
		Objects.requireNonNull(etapa, "etapa");
		Objects.requireNonNull(ahora, "ahora");
		if (estatus == EstatusOrden.ENTREGADO) {
			throw new IllegalStateException("La orden ya se entregó");
		}
		estatus = etapa.estatus();
		if (estatus.cerrada() && fechaCierre == null) {
			fechaCierre = ahora;
		}
	}

	/**
	 * Cambia la hora estimada de entrega; {@code null} la deja sin estimar.
	 *
	 * @throws IllegalArgumentException
	 *             si ya pasó
	 */
	public void estimarEntrega(Instant entregaEstimada, Instant ahora) {
		if (entregaEstimada != null && entregaEstimada.isBefore(ahora)) {
			throw new IllegalArgumentException("La entrega estimada no puede ser anterior a ahora");
		}
		this.entregaEstimada = entregaEstimada;
	}

	/**
	 * Agrega un renglón a la orden que sigue en el taller y recalcula sus montos
	 * (V2-M04.07): así el ticket de la orden cerrada trae lo que se hizo.
	 *
	 * @throws IllegalStateException
	 *             si la orden ya está terminada o entregada
	 * @throws IllegalArgumentException
	 *             si el renglón no es válido, la orden ya tiene 100 renglones o el
	 *             total se pasa del máximo
	 */
	public void agregarRenglon(RenglonNuevo nuevo) {
		if (estatus.cerrada()) {
			throw new IllegalStateException("La orden ya está terminada");
		}
		if (renglonesManoDeObra.size() + renglonesRefaccion.size() >= MAXIMO_RENGLONES) {
			throw new IllegalArgumentException("El servicio admite hasta " + MAXIMO_RENGLONES + " renglones");
		}
		agregar(RenglonOrden.de(this, nuevo));
		recalcularMontos();
	}

	private void recalcularMontos() {
		BigDecimal sumaManoDeObra = suma(renglonesManoDeObra);
		BigDecimal sumaRefacciones = suma(renglonesRefaccion);
		BigDecimal sumaTotal = sumaManoDeObra.add(sumaRefacciones);
		if (sumaTotal.compareTo(TOTAL_MAXIMO) > 0) {
			throw new IllegalArgumentException("El total del servicio no puede pasar de " + TOTAL_MAXIMO);
		}
		Montos montos = Montos.desglosar(sumaTotal);
		manoDeObra = sumaManoDeObra;
		refacciones = sumaRefacciones;
		subtotal = montos.subtotal();
		iva = montos.iva();
		total = montos.total();
	}

	private void agregar(RenglonOrden renglon) {
		if (renglon instanceof OrdenManoDeObra manoDeObra) {
			renglonesManoDeObra.add(manoDeObra);
		} else {
			renglonesRefaccion.add((OrdenRefaccion) renglon);
		}
	}

	private static BigDecimal suma(List<? extends RenglonOrden> renglones) {
		return renglones.stream().map(RenglonOrden::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
	}

	public Long getId() {
		return id;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public int getKilometraje() {
		return kilometraje;
	}

	public Long getAtendidoPor() {
		return atendidoPor;
	}

	public BigDecimal getManoDeObra() {
		return manoDeObra;
	}

	public BigDecimal getRefacciones() {
		return refacciones;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public BigDecimal getIva() {
		return iva;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public EstatusOrden getEstatus() {
		return estatus;
	}

	public Instant getFechaCierre() {
		return fechaCierre;
	}

	public Long getCitaId() {
		return citaId;
	}

	public Long getAsesorId() {
		return asesorId;
	}

	public Instant getEntregaEstimada() {
		return entregaEstimada;
	}

	public List<OrdenManoDeObra> getRenglonesManoDeObra() {
		return List.copyOf(renglonesManoDeObra);
	}

	public List<OrdenRefaccion> getRenglonesRefaccion() {
		return List.copyOf(renglonesRefaccion);
	}

	public List<RenglonOrden> getRenglones() {
		return Stream.<RenglonOrden>concat(renglonesManoDeObra.stream(), renglonesRefaccion.stream()).toList();
	}
}
