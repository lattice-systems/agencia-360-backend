package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Un dispositivo que guardó el pase de Apple Wallet de una cita y se registró
 * en el servicio web para recibir sus cambios (V2-M25.06). Apple identifica el
 * pase por {@code passTypeId} y {@code serial}; {@code pushToken} es a quien se
 * le manda el push vacío cuando el pase cambia.
 */
@Entity
@Table(name = "registro_pase_apple")
public class RegistroPaseApple extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100, updatable = false)
	private String dispositivo;

	@Column(name = "push_token", nullable = false, length = 200)
	private String pushToken;

	@Column(name = "pass_type_id", nullable = false, length = 100, updatable = false)
	private String passTypeId;

	@Column(nullable = false, length = 40, updatable = false)
	private String serial;

	@Column(name = "cita_id", nullable = false, updatable = false)
	private Long citaId;

	protected RegistroPaseApple() {
	}

	public RegistroPaseApple(String dispositivo, String pushToken, String passTypeId, String serial, Long citaId) {
		this.dispositivo = Objects.requireNonNull(dispositivo, "dispositivo");
		this.pushToken = Objects.requireNonNull(pushToken, "pushToken");
		this.passTypeId = Objects.requireNonNull(passTypeId, "passTypeId");
		this.serial = Objects.requireNonNull(serial, "serial");
		this.citaId = Objects.requireNonNull(citaId, "citaId");
	}

	/** Apple puede cambiar el push token del mismo dispositivo. */
	public void cambiarPushToken(String nuevo) {
		this.pushToken = Objects.requireNonNull(nuevo, "pushToken");
	}

	public Long getId() {
		return id;
	}

	public String getDispositivo() {
		return dispositivo;
	}

	public String getPushToken() {
		return pushToken;
	}

	public String getPassTypeId() {
		return passTypeId;
	}

	public String getSerial() {
		return serial;
	}

	public Long getCitaId() {
		return citaId;
	}
}
