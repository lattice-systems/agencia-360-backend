package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Valuación formal que el cliente pide desde una estimación (V2-M26.01 a
 * V2-M26.04). Se solicita, se agenda su inspección, la agencia captura una
 * oferta con vigencia y el cliente la acepta o la rechaza.
 *
 * <p>
 * Las reglas se deciden con el estado ya al día: quien la usa llama antes a
 * {@link #reabrirSinCita()} si su cita se canceló y a
 * {@link #vencerSiPaso(LocalDate)}. Las fechas son hora de la agencia.
 *
 * <p>
 * {@code clienteId}, {@code vehiculoClienteId}, {@code citaId},
 * {@code prospectoId} y {@code ofertadaPor} se guardan solo como ids, igual que
 * en {@link Cita}.
 */
@Entity
@Table(name = "valuacion")
public class Valuacion extends Auditable {

	private static final BigDecimal OFERTA_MAXIMA = new BigDecimal("9999999999.99");
	private static final int NOTA_MAXIMA = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "estimacion_toma_cuenta_id", nullable = false)
	private Long estimacionTomaCuentaId;

	@Column(name = "cliente_id", nullable = false)
	private Long clienteId;

	@Convert(converter = ConvertidorFotosValuacion.class)
	@Column(nullable = false, columnDefinition = "text")
	private Map<TomaValuacion, FotoGuardada> fotos = Map.of();

	@Column(name = "cita_id")
	private Long citaId;

	@Column(precision = 12, scale = 2)
	private BigDecimal oferta;

	@Column(name = "vigencia_hasta")
	private LocalDate vigenciaHasta;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private EstadoValuacion estado;

	@Column(name = "vehiculo_cliente_id", nullable = false)
	private Long vehiculoClienteId;

	@Column(name = "prospecto_id")
	private Long prospectoId;

	@Column(name = "oferta_nota", length = NOTA_MAXIMA)
	private String ofertaNota;

	@Column(name = "ofertada_por")
	private Long ofertadaPor;

	@Column(name = "ofertada_en")
	private Instant ofertadaEn;

	@Column(name = "respondida_en")
	private Instant respondidaEn;

	protected Valuacion() {
		// Requerido por JPA
	}

	public static Valuacion solicitar(Long clienteId, Long estimacionTomaCuentaId, Long vehiculoClienteId) {
		Valuacion nueva = new Valuacion();
		nueva.clienteId = Objects.requireNonNull(clienteId, "clienteId");
		nueva.estimacionTomaCuentaId = Objects.requireNonNull(estimacionTomaCuentaId, "estimacionTomaCuentaId");
		nueva.vehiculoClienteId = Objects.requireNonNull(vehiculoClienteId, "vehiculoClienteId");
		nueva.estado = EstadoValuacion.SOLICITADA;
		return nueva;
	}

	public void ligarProspecto(Long prospecto) {
		prospectoId = Objects.requireNonNull(prospecto, "prospecto");
	}

	/**
	 * @throws ConflictoException
	 *             con {@code VALUACION_CERRADA} si ya no está solicitada ni con
	 *             cita (409)
	 */
	public void exigirQueAdmitaFotos() {
		if (estado != EstadoValuacion.SOLICITADA && estado != EstadoValuacion.CITA_AGENDADA) {
			throw new ConflictoException("La valuación ya no admite fotos", CodigosError.VALUACION_CERRADA);
		}
	}

	/**
	 * Guarda o reemplaza la foto de una toma.
	 *
	 * @return la clave de la foto reemplazada, para borrarla del almacén
	 * @throws ConflictoException
	 *             con {@code VALUACION_CERRADA} (409)
	 */
	public Optional<String> ponerFoto(TomaValuacion toma, String clave, Instant subidaEn) {
		exigirQueAdmitaFotos();
		Map<TomaValuacion, FotoGuardada> nuevas = new EnumMap<>(TomaValuacion.class);
		nuevas.putAll(fotos);
		FotoGuardada anterior = nuevas.put(toma, new FotoGuardada(clave, subidaEn));
		// Un mapa nuevo: así JPA ve que la columna cambió.
		fotos = Collections.unmodifiableMap(nuevas);
		return Optional.ofNullable(anterior).map(FotoGuardada::clave);
	}

	/**
	 * Si se puede agendar la inspección: recién solicitada, o con una cita que pasó
	 * hace más de {@code diasSinOferta} días sin que hubiera oferta.
	 *
	 * @param fechaDeLaCita
	 *            el día de la cita vigente; nulo si no tiene
	 */
	public boolean puedeAgendar(LocalDate fechaDeLaCita, LocalDate hoy, int diasSinOferta) {
		if (estado == EstadoValuacion.SOLICITADA) {
			return true;
		}
		return estado == EstadoValuacion.CITA_AGENDADA && fechaDeLaCita != null
				&& hoy.isAfter(fechaDeLaCita.plusDays(diasSinOferta));
	}

	/**
	 * @throws ConflictoException
	 *             con {@code VALUACION_NO_AGENDABLE} (409)
	 */
	public void exigirAgendable(LocalDate fechaDeLaCita, LocalDate hoy, int diasSinOferta) {
		if (!puedeAgendar(fechaDeLaCita, hoy, diasSinOferta)) {
			throw new ConflictoException("La valuación ya tiene cita o ya no se puede agendar",
					CodigosError.VALUACION_NO_AGENDABLE);
		}
	}

	/** La cita ya quedó creada; quien llama revisó {@link #exigirAgendable}. */
	public void inspeccionAgendada(Long cita) {
		citaId = Objects.requireNonNull(cita, "cita");
		estado = EstadoValuacion.CITA_AGENDADA;
	}

	/**
	 * Su cita de inspección se canceló.
	 *
	 * @return {@code true} si cambió
	 */
	public boolean reabrirSinCita() {
		if (estado != EstadoValuacion.CITA_AGENDADA) {
			return false;
		}
		estado = EstadoValuacion.SOLICITADA;
		citaId = null;
		return true;
	}

	/**
	 * La vigencia es el último día válido, inclusive.
	 *
	 * @return {@code true} si cambió
	 */
	public boolean vencerSiPaso(LocalDate hoy) {
		if (estado != EstadoValuacion.OFERTADA || !hoy.isAfter(vigenciaHasta)) {
			return false;
		}
		estado = EstadoValuacion.VENCIDA;
		return true;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el monto no es mayor a cero o tiene más de 2 decimales, la
	 *             vigencia no está entre hoy y el máximo o la nota pasa de 500
	 *             (400)
	 */
	public static void validarOferta(BigDecimal monto, LocalDate vigencia, String nota, LocalDate hoy,
			int diasMaximos) {
		if (monto == null || monto.signum() <= 0) {
			throw new IllegalArgumentException("El monto debe ser mayor a cero");
		}
		if (monto.stripTrailingZeros().scale() > 2) {
			throw new IllegalArgumentException("El monto admite hasta dos decimales");
		}
		if (monto.compareTo(OFERTA_MAXIMA) > 0) {
			throw new IllegalArgumentException("El monto es demasiado grande");
		}
		if (vigencia == null || vigencia.isBefore(hoy) || vigencia.isAfter(hoy.plusDays(diasMaximos))) {
			throw new IllegalArgumentException("La vigencia debe estar entre hoy y " + diasMaximos + " días adelante");
		}
		if (nota != null && nota.strip().length() > NOTA_MAXIMA) {
			throw new IllegalArgumentException("La nota pasa de " + NOTA_MAXIMA + " caracteres");
		}
	}

	/**
	 * Captura la oferta tras la inspección, o renueva una vencida. Una oferta
	 * vigente no se edita.
	 *
	 * @param inspeccionHecha
	 *            si la cita sigue confirmada y es de hoy o de antes
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} (409)
	 */
	public void ofertar(BigDecimal monto, LocalDate vigencia, String nota, Long usuarioId, Instant ahora,
			boolean inspeccionHecha) {
		boolean trasInspeccion = estado == EstadoValuacion.CITA_AGENDADA && inspeccionHecha;
		if (!trasInspeccion && estado != EstadoValuacion.VENCIDA) {
			throw new ConflictoException("Solo se oferta después de la inspección o para renovar una oferta vencida",
					CodigosError.TRANSICION_INVALIDA);
		}
		oferta = monto.setScale(2);
		vigenciaHasta = vigencia;
		ofertaNota = nota == null || nota.isBlank() ? null : nota.strip();
		ofertadaPor = Objects.requireNonNull(usuarioId, "usuarioId");
		ofertadaEn = ahora;
		estado = EstadoValuacion.OFERTADA;
	}

	/**
	 * El cliente acepta o rechaza la oferta que vio. Repetir la misma respuesta con
	 * el mismo monto no cambia nada, para que se pueda reintentar.
	 *
	 * @param monto
	 *            el que el cliente vio en pantalla
	 * @throws ConflictoException
	 *             con {@code OFERTA_VENCIDA} si la vigencia pasó, o con
	 *             {@code OFERTA_NO_DISPONIBLE} (409)
	 */
	public void responder(boolean aceptada, BigDecimal monto, LocalDate hoy, Instant ahora) {
		EstadoValuacion pedido = aceptada ? EstadoValuacion.ACEPTADA : EstadoValuacion.RECHAZADA;
		if (estado == pedido && mismaOferta(monto)) {
			return;
		}
		if (estado == EstadoValuacion.VENCIDA || estado == EstadoValuacion.OFERTADA && hoy.isAfter(vigenciaHasta)) {
			throw new ConflictoException("La vigencia de la oferta ya pasó", CodigosError.OFERTA_VENCIDA);
		}
		if (estado != EstadoValuacion.OFERTADA || !mismaOferta(monto)) {
			throw new ConflictoException("No hay una oferta con ese monto por responder",
					CodigosError.OFERTA_NO_DISPONIBLE);
		}
		estado = pedido;
		respondidaEn = ahora;
	}

	private boolean mismaOferta(BigDecimal monto) {
		return oferta != null && monto != null && oferta.compareTo(monto) == 0;
	}

	public Long getId() {
		return id;
	}

	public Long getEstimacionTomaCuentaId() {
		return estimacionTomaCuentaId;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public Map<TomaValuacion, FotoGuardada> getFotos() {
		return fotos;
	}

	public Long getCitaId() {
		return citaId;
	}

	public BigDecimal getOferta() {
		return oferta;
	}

	public LocalDate getVigenciaHasta() {
		return vigenciaHasta;
	}

	public EstadoValuacion getEstado() {
		return estado;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public Long getProspectoId() {
		return prospectoId;
	}

	public String getOfertaNota() {
		return ofertaNota;
	}

	public Long getOfertadaPor() {
		return ofertadaPor;
	}

	public Instant getOfertadaEn() {
		return ofertadaEn;
	}

	public Instant getRespondidaEn() {
		return respondidaEn;
	}
}
