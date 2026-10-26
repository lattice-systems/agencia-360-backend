package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Servicio que se le hizo al vehículo de un cliente (ERP-3.0): cuándo, con qué
 * kilometraje, quién lo atendió y sus renglones. El total es la suma de los
 * renglones y ya trae el IVA. Es dinero: no se edita ni se borra.
 *
 * <p>
 * {@code vehiculoClienteId} y {@code atendidoPor} solo se mapean como ids para
 * no cruzar módulos; las llaves foráneas sí viven en la base.
 */
@Entity
@Table(name = "servicio")
public class Servicio extends Auditable {

	static final int MAXIMO_RENGLONES = 100;
	static final int KILOMETRAJE_MAXIMO = 2_000_000;
	/** Lo más que cabe en la columna numeric(12, 2). */
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

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotal;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal iva;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@OneToMany(mappedBy = "servicio", cascade = CascadeType.PERSIST)
	@OrderBy("id asc")
	private List<RenglonServicio> renglones = new ArrayList<>();

	protected Servicio() {
		// Requerido por JPA
	}

	private Servicio(Long vehiculoClienteId, LocalDate fecha, int kilometraje, Long atendidoPor) {
		this.vehiculoClienteId = vehiculoClienteId;
		this.fecha = fecha;
		this.kilometraje = kilometraje;
		this.atendidoPor = atendidoPor;
	}

	/**
	 * Registra un servicio con sus renglones y calcula sus montos.
	 *
	 * @param hoy
	 *            día de la agencia; la fecha no puede ser posterior
	 * @throws IllegalArgumentException
	 *             si falta la fecha, es futura, el kilometraje no es válido, no
	 *             trae renglones, un renglón no es válido o el total es demasiado
	 *             grande (400)
	 */
	public static Servicio registrar(Long vehiculoClienteId, LocalDate fecha, int kilometraje, Long atendidoPor,
			List<RenglonNuevo> renglones, LocalDate hoy) {
		Objects.requireNonNull(vehiculoClienteId, "vehiculoClienteId");
		Objects.requireNonNull(atendidoPor, "atendidoPor");
		if (fecha == null) {
			throw new IllegalArgumentException("La fecha del servicio es obligatoria");
		}
		if (fecha.isAfter(hoy)) {
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
		Servicio servicio = new Servicio(vehiculoClienteId, fecha, kilometraje, atendidoPor);
		renglones.forEach(nuevo -> servicio.renglones.add(RenglonServicio.de(servicio, nuevo)));
		BigDecimal suma = servicio.renglones.stream().map(RenglonServicio::getImporte).reduce(BigDecimal.ZERO,
				BigDecimal::add);
		if (suma.compareTo(TOTAL_MAXIMO) > 0) {
			throw new IllegalArgumentException("El total del servicio no puede pasar de " + TOTAL_MAXIMO);
		}
		Montos montos = Montos.desglosar(suma);
		servicio.subtotal = montos.subtotal();
		servicio.iva = montos.iva();
		servicio.total = montos.total();
		return servicio;
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

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public BigDecimal getIva() {
		return iva;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public List<RenglonServicio> getRenglones() {
		return Collections.unmodifiableList(renglones);
	}
}
