package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Una estimación de toma a cuenta que el cliente ya calculó (V2-M03.04 y
 * V2-M03.07). Es no vinculante: nunca una oferta. Guarda marca, modelo y año
 * aunque haya vehículo, y congela los supuestos, el modelo destino y su precio,
 * para que editar el vehículo o el parámetro después no cambie lo que ya se
 * mostró. La fecha es {@code createdAt}.
 *
 * <p>
 * {@code clienteId}, {@code vehiculoClienteId} y {@code modeloCatalogoId} solo
 * se mapean como ids, igual que en {@link Cita}.
 */
@Entity
@Table(name = "estimacion_toma_cuenta")
public class EstimacionTomaCuenta extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false)
	private Long clienteId;

	@Column(name = "vehiculo_cliente_id")
	private Long vehiculoClienteId;

	@Column(name = "modelo_catalogo_id")
	private Long modeloCatalogoId;

	@Column(nullable = false, length = 40)
	private String marca;

	@Column(nullable = false, length = 80)
	private String modelo;

	@Column(nullable = false)
	private int anio;

	@Column(nullable = false)
	private int km;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoVehiculo estado;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal minimo;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal maximo;

	@Column(name = "parametro_toma_cuenta_id", nullable = false)
	private Long parametroId;

	/** Los supuestos de la fórmula, como JSON, tal como se mostraron ese día. */
	@Column(nullable = false, columnDefinition = "text")
	private String supuestos;

	@Column(name = "modelo_destino", length = 120)
	private String modeloDestino;

	@Column(name = "precio_destino", precision = 12, scale = 2)
	private BigDecimal precioDestino;

	protected EstimacionTomaCuenta() {
		// Requerido por JPA
	}

	/**
	 * Una estimación del cliente.
	 *
	 * @param vehiculoClienteId
	 *            su vehículo, o {@code null} si capturó el auto a mano
	 * @param modeloCatalogoId
	 *            el modelo nuevo contra el que estimó, o {@code null}
	 * @param modeloDestino
	 *            nombre del modelo nuevo al momento de estimar, o {@code null}
	 * @param precioDestino
	 *            precio de lista de ese modelo en ese momento, o {@code null}
	 */
	public static EstimacionTomaCuenta nueva(Long clienteId, Long vehiculoClienteId, Long modeloCatalogoId,
			String marca, String modelo, int anio, int km, EstadoVehiculo estado, Long parametroId,
			CalculadoraTomaCuenta.Resultado resultado, String supuestos, String modeloDestino,
			BigDecimal precioDestino) {
		EstimacionTomaCuenta nueva = new EstimacionTomaCuenta();
		nueva.clienteId = clienteId;
		nueva.vehiculoClienteId = vehiculoClienteId;
		nueva.modeloCatalogoId = modeloCatalogoId;
		nueva.marca = marca;
		nueva.modelo = modelo;
		nueva.anio = anio;
		nueva.km = km;
		nueva.estado = estado;
		nueva.minimo = resultado.minimo();
		nueva.maximo = resultado.maximo();
		nueva.parametroId = parametroId;
		nueva.supuestos = supuestos;
		nueva.modeloDestino = modeloDestino;
		nueva.precioDestino = precioDestino;
		return nueva;
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public Long getModeloCatalogoId() {
		return modeloCatalogoId;
	}

	public String getMarca() {
		return marca;
	}

	public String getModelo() {
		return modelo;
	}

	public int getAnio() {
		return anio;
	}

	public int getKm() {
		return km;
	}

	public EstadoVehiculo getEstado() {
		return estado;
	}

	public BigDecimal getMinimo() {
		return minimo;
	}

	public BigDecimal getMaximo() {
		return maximo;
	}

	public Long getParametroId() {
		return parametroId;
	}

	public String getSupuestos() {
		return supuestos;
	}

	public String getModeloDestino() {
		return modeloDestino;
	}

	public BigDecimal getPrecioDestino() {
		return precioDestino;
	}
}
