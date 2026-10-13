package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Persona interesada en comprar, a cargo de un vendedor (CRM-1.0). Avanza por
 * etapas y cada cambio queda en su historial (CRM-1.1). La fecha y hora de
 * registro es {@code created_at} de {@link Auditable}.
 *
 * <p>
 * El vendedor se guarda solo como id: el usuario pertenece al módulo de acceso
 * y aquí no se navega hacia él.
 */
@Entity
@Table(name = "prospecto")
public class Prospecto extends Auditable {

	private static final Pattern SEPARADORES_TELEFONO = Pattern.compile("[\\s().-]");
	private static final Pattern DIEZ_DIGITOS = Pattern.compile("\\d{10}");
	private static final int LARGO_MAXIMO_MOTIVO = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(length = 20)
	private String telefono;

	@Column(length = 120)
	private String correo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Etapa etapa;

	@Column(name = "vendedor_id", nullable = false)
	private Long vendedorId;

	/** Obligatorio al cerrar (decisión del PO del 12 de octubre). */
	@Column(name = "motivo_cierre", length = 200)
	private String motivoCierre;

	@Enumerated(EnumType.STRING)
	@Column(name = "ultima_etapa", length = 20)
	private Etapa ultimaEtapa;

	@OneToMany(mappedBy = "prospecto", cascade = CascadeType.PERSIST)
	@OrderBy("fechaHora asc, id asc")
	private List<HistorialEtapa> historial = new ArrayList<>();

	protected Prospecto() {
		// Requerido por JPA
	}

	private Prospecto(String nombre, String telefono, String correo, Long vendedorId) {
		this.nombre = nombre;
		this.telefono = telefono;
		this.correo = correo;
		this.vendedorId = vendedorId;
	}

	/**
	 * Registra un prospecto en "Primer contacto" y escribe el primer movimiento de
	 * su historial (CRM-1.0). Quita espacios sobrantes, trata los campos vacíos
	 * como no capturados, guarda el teléfono solo con sus 10 dígitos y el correo en
	 * minúsculas para poder comparar repetidos.
	 *
	 * @throws IllegalArgumentException
	 *             si falta el nombre, faltan a la vez el teléfono y el correo, o el
	 *             teléfono no es de 10 dígitos (decisión del PO del 12 de octubre:
	 *             solo números de México)
	 */
	public static Prospecto registrar(String nombre, String telefono, String correo, Long vendedorId, Instant ahora) {
		String nombreLimpio = limpiar(nombre);
		String telefonoLimpio = normalizarTelefono(limpiar(telefono));
		String correoLimpio = limpiar(correo);
		if (nombreLimpio == null) {
			throw new IllegalArgumentException("El nombre es obligatorio");
		}
		if (telefonoLimpio == null && correoLimpio == null) {
			throw new IllegalArgumentException("Captura al menos un teléfono o un correo");
		}
		Objects.requireNonNull(vendedorId, "vendedorId");

		Prospecto prospecto = new Prospecto(nombreLimpio, telefonoLimpio,
				correoLimpio == null ? null : correoLimpio.toLowerCase(Locale.ROOT), vendedorId);
		prospecto.moverA(Etapa.PRIMER_CONTACTO, OrigenCambio.CRM, ahora);
		return prospecto;
	}

	/**
	 * Mueve el prospecto a otra etapa y lo deja en el historial (CRM-1.1). Al
	 * cerrar exige el motivo y guarda la última etapa alcanzada; al reabrir olvida
	 * los dos.
	 *
	 * @param motivoCierre
	 *            obligatorio solo si {@code nueva} es {@link Etapa#CERRADO}; en
	 *            otro caso se ignora
	 * @return {@code false} si el prospecto ya estaba en esa etapa; en ese caso no
	 *         se escribe historial
	 * @throws IllegalArgumentException
	 *             si se cierra sin motivo o con un motivo de más de 200 caracteres
	 */
	public boolean cambiarEtapa(Etapa nueva, String motivoCierre, OrigenCambio origen, Instant ahora) {
		Objects.requireNonNull(nueva, "nueva");
		if (nueva == etapa) {
			return false;
		}
		if (nueva == Etapa.CERRADO) {
			String motivo = limpiar(motivoCierre);
			if (motivo == null) {
				throw new IllegalArgumentException("El motivo de cierre es obligatorio");
			}
			if (motivo.length() > LARGO_MAXIMO_MOTIVO) {
				throw new IllegalArgumentException("El motivo de cierre admite hasta 200 caracteres");
			}
			this.motivoCierre = motivo;
			ultimaEtapa = etapa;
		} else if (etapa == Etapa.CERRADO) {
			this.motivoCierre = null;
			ultimaEtapa = null;
		}
		moverA(nueva, origen, ahora);
		return true;
	}

	/**
	 * Pasa el prospecto a otro vendedor (CRM-0.1). Conserva la etapa y el
	 * historial; solo cambia quién lo atiende.
	 *
	 * @return {@code false} si el prospecto ya era de ese vendedor
	 */
	public boolean reasignarA(Long nuevoVendedorId) {
		Objects.requireNonNull(nuevoVendedorId, "nuevoVendedorId");
		if (nuevoVendedorId.equals(vendedorId)) {
			return false;
		}
		vendedorId = nuevoVendedorId;
		return true;
	}

	private void moverA(Etapa nueva, OrigenCambio origen, Instant ahora) {
		Objects.requireNonNull(origen, "origen");
		Objects.requireNonNull(ahora, "ahora");
		etapa = nueva;
		historial.add(new HistorialEtapa(this, nueva, origen, ahora));
	}

	private static String normalizarTelefono(String telefono) {
		if (telefono == null) {
			return null;
		}
		String digitos = SEPARADORES_TELEFONO.matcher(telefono).replaceAll("");
		if (!DIEZ_DIGITOS.matcher(digitos).matches()) {
			throw new IllegalArgumentException("El teléfono debe tener 10 dígitos");
		}
		return digitos;
	}

	private static String limpiar(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		return valor.strip();
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getTelefono() {
		return telefono;
	}

	public String getCorreo() {
		return correo;
	}

	public Etapa getEtapa() {
		return etapa;
	}

	public Long getVendedorId() {
		return vendedorId;
	}

	public String getMotivoCierre() {
		return motivoCierre;
	}

	public Etapa getUltimaEtapa() {
		return ultimaEtapa;
	}

	public List<HistorialEtapa> getHistorial() {
		return Collections.unmodifiableList(historial);
	}
}
