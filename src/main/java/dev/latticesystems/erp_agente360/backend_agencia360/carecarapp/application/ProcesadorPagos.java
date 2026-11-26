package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoFalloPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TarjetaDePrueba;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * El procesador que cobra las órdenes (V2-M08.02). Hoy la única implementación
 * es la simulada y propia (ADR 0009 de carecar-mobile): no hay cuentas, llaves
 * ni red. Uno real sería otra implementación de esta interfaz, con su ADR.
 */
public interface ProcesadorPagos {

	/** Lo que se guarda en {@code procesador}, por ejemplo {@code SIMULADO}. */
	String nombre();

	/** Un folio nuevo para el intento; es la referencia que ve el cliente. */
	String nuevaIntencion();

	/** El cliente en el procesador, para las tarjetas guardadas. */
	String clienteDelProcesador(Long clienteId);

	/** La tarjeta que corresponde al token; vacío si el procesador no la conoce. */
	Optional<TarjetaDePrueba> tarjeta(String token);

	Resultado cobrar(String intencionId, TarjetaDePrueba tarjeta, BigDecimal monto);

	/**
	 * Responde el 3-D Secure.
	 *
	 * @param confirmada
	 *            si la persona confirmó en la pantalla de su banco
	 */
	Resultado autenticar(String intencionId, TarjetaDePrueba tarjeta, boolean confirmada);

	/** Lo que respondió el procesador. */
	enum Respuesta {
		APROBADO, RECHAZADO, PIDE_AUTENTICACION
	}

	/** @param motivo solo cuando la respuesta es {@code RECHAZADO} */
	record Resultado(Respuesta respuesta, MotivoFalloPago motivo) {

		public Resultado {
			Objects.requireNonNull(respuesta, "respuesta");
			if ((respuesta == Respuesta.RECHAZADO) != (motivo != null)) {
				throw new IllegalArgumentException("Solo un rechazo lleva motivo");
			}
		}

		public static Resultado aprobado() {
			return new Resultado(Respuesta.APROBADO, null);
		}

		public static Resultado pideAutenticacion() {
			return new Resultado(Respuesta.PIDE_AUTENTICACION, null);
		}

		public static Resultado rechazado(MotivoFalloPago motivo) {
			return new Resultado(Respuesta.RECHAZADO, Objects.requireNonNull(motivo, "motivo"));
		}
	}
}
