package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "franja_horario")
public class FranjaHorario extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dia_semana", nullable = false)
	private int diaSemana;

	@Column(name = "hora_inicio", nullable = false)
	private LocalTime horaInicio;

	@Column(name = "hora_fin", nullable = false)
	private LocalTime horaFin;

	@Column(nullable = false)
	private int capacidad;

	@Column(nullable = false)
	private boolean activa;

	protected FranjaHorario() {
	}

	private FranjaHorario(DayOfWeek dia, LocalTime horaInicio, LocalTime horaFin, int capacidad) {
		this.diaSemana = dia.getValue();
		this.horaInicio = horaInicio;
		this.horaFin = horaFin;
		this.capacidad = capacidad;
		this.activa = true;
	}

	public static FranjaHorario nueva(DayOfWeek dia, LocalTime horaInicio, LocalTime horaFin, int capacidad) {
		if (dia == null || horaInicio == null || horaFin == null) {
			throw new IllegalArgumentException("El día, la hora de inicio y la hora de fin son obligatorios");
		}
		if (!horaFin.isAfter(horaInicio)) {
			throw new IllegalArgumentException("La hora de fin debe ser posterior a la de inicio");
		}
		validarCapacidad(capacidad);
		return new FranjaHorario(dia, horaInicio, horaFin, capacidad);
	}

	public void cambiarCapacidad(int capacidad) {
		validarCapacidad(capacidad);
		this.capacidad = capacidad;
	}

	public void desactivar() {
		this.activa = false;
	}

	public boolean esDe(LocalDate fecha) {
		return fecha != null && fecha.getDayOfWeek() == getDiaSemana();
	}

	private static void validarCapacidad(int capacidad) {
		if (capacidad <= 0) {
			throw new IllegalArgumentException("La capacidad de la franja debe ser mayor a cero");
		}
	}

	public Long getId() {
		return id;
	}

	public DayOfWeek getDiaSemana() {
		return DayOfWeek.of(diaSemana);
	}

	public LocalTime getHoraInicio() {
		return horaInicio;
	}

	public LocalTime getHoraFin() {
		return horaFin;
	}

	public int getCapacidad() {
		return capacidad;
	}

	public boolean isActiva() {
		return activa;
	}
}
