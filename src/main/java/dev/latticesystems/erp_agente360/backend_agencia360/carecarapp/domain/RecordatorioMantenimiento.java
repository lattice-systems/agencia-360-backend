package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Aviso de próximo servicio ya enviado (V2-M05.05). Existe para no repetirlo:
 * hay a lo más uno por vehículo y servicio del plan.
 */
@Entity
@Table(name = "recordatorio_mantenimiento")
public class RecordatorioMantenimiento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "vehiculo_cliente_id", nullable = false)
	private Long vehiculoClienteId;

	@Column(name = "plan_mantenimiento_id", nullable = false)
	private Long planMantenimientoId;

	@Column(nullable = false, length = 10)
	private String motivo;

	@Column(name = "enviado_en", nullable = false)
	private Instant enviadoEn;

	protected RecordatorioMantenimiento() {
	}

	public RecordatorioMantenimiento(Long vehiculoClienteId, Long planMantenimientoId, String motivo,
			Instant enviadoEn) {
		this.vehiculoClienteId = Objects.requireNonNull(vehiculoClienteId, "vehiculoClienteId");
		this.planMantenimientoId = Objects.requireNonNull(planMantenimientoId, "planMantenimientoId");
		this.motivo = Objects.requireNonNull(motivo, "motivo");
		this.enviadoEn = Objects.requireNonNull(enviadoEn, "enviadoEn");
	}

	public Long getId() {
		return id;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public Long getPlanMantenimientoId() {
		return planMantenimientoId;
	}

	public String getMotivo() {
		return motivo;
	}

	public Instant getEnviadoEn() {
		return enviadoEn;
	}
}
