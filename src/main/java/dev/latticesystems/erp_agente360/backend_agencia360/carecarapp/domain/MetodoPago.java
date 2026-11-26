package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Una tarjeta guardada del cliente (V2-M08.04, tabla {@code metodo_pago} de la
 * ficha). Guarda el token del procesador simulado, la marca, los últimos 4 y el
 * vencimiento; nunca el número ni el CVV. Uno es el predeterminado y la base no
 * deja que haya dos.
 */
@Entity
@Table(name = "metodo_pago")
public class MetodoPago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false, updatable = false)
	private Long clienteId;

	@Column(nullable = false, updatable = false, length = 20)
	private String procesador;

	@Column(name = "cliente_procesador_id", nullable = false, updatable = false, length = 64)
	private String clienteProcesadorId;

	@Column(name = "token_procesador", nullable = false, updatable = false, length = 64)
	private String tokenProcesador;

	@Column(nullable = false, updatable = false, length = 20)
	private String marca;

	@Column(nullable = false, updatable = false, length = 4)
	private String ultimos4;

	@Column(name = "mes_vence", nullable = false)
	private short mesVence;

	@Column(name = "anio_vence", nullable = false)
	private short anioVence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private TipoTarjeta tipo;

	@Column(nullable = false)
	private boolean predeterminado;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	protected MetodoPago() {
		// Requerido por JPA
	}

	public MetodoPago(Long clienteId, String procesador, String clienteProcesadorId, TarjetaDePrueba tarjeta,
			YearMonth vence, Instant ahora) {
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId");
		this.procesador = Objects.requireNonNull(procesador, "procesador");
		this.clienteProcesadorId = Objects.requireNonNull(clienteProcesadorId, "clienteProcesadorId");
		this.tokenProcesador = tarjeta.token();
		this.marca = tarjeta.marca();
		this.ultimos4 = tarjeta.ultimos4();
		this.tipo = tarjeta.tipo();
		this.creadoEn = Objects.requireNonNull(ahora, "ahora");
		renovar(vence);
	}

	/** El vencimiento que trae la tarjeta al volver a guardarla. */
	public void renovar(YearMonth vence) {
		Objects.requireNonNull(vence, "vence");
		this.mesVence = (short) vence.getMonthValue();
		this.anioVence = (short) vence.getYear();
	}

	public boolean vencida(YearMonth hoy) {
		return getVence().isBefore(hoy);
	}

	public void marcarPredeterminado(boolean predeterminado) {
		this.predeterminado = predeterminado;
	}

	public YearMonth getVence() {
		return YearMonth.of(anioVence, mesVence);
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public String getProcesador() {
		return procesador;
	}

	public String getTokenProcesador() {
		return tokenProcesador;
	}

	public String getMarca() {
		return marca;
	}

	public String getUltimos4() {
		return ultimos4;
	}

	public int getMesVence() {
		return mesVence;
	}

	public int getAnioVence() {
		return anioVence;
	}

	public TipoTarjeta getTipo() {
		return tipo;
	}

	public boolean isPredeterminado() {
		return predeterminado;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}
}
