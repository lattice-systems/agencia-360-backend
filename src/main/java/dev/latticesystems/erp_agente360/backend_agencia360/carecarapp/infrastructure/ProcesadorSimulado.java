package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ProcesadorPagos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoFalloPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TarjetaDePrueba;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Procesador de pagos simulado y propio (ADR 0009 de carecar-mobile): decide
 * por la tarjeta de prueba, sin red, sin cuentas y sin cobrar nada. Es la
 * implementación de todos los ambientes, así que las pruebas ejercen el mismo
 * código que corre en Azure.
 */
@Component
public class ProcesadorSimulado implements ProcesadorPagos {

	static final String NOMBRE = "SIMULADO";

	private static final int BYTES_DE_FOLIO = 12;

	private final SecureRandom azar = new SecureRandom();

	@Override
	public String nombre() {
		return NOMBRE;
	}

	@Override
	public String nuevaIntencion() {
		byte[] bytes = new byte[BYTES_DE_FOLIO];
		azar.nextBytes(bytes);
		return "sim_pi_" + HexFormat.of().formatHex(bytes);
	}

	@Override
	public String clienteDelProcesador(Long clienteId) {
		return "sim_cus_" + clienteId;
	}

	@Override
	public Optional<TarjetaDePrueba> tarjeta(String token) {
		return token == null ? Optional.empty() : TarjetaDePrueba.deToken(token);
	}

	@Override
	public Resultado cobrar(String intencionId, TarjetaDePrueba tarjeta, BigDecimal monto) {
		return switch (tarjeta.escenario()) {
			case APROBADA -> Resultado.aprobado();
			case FONDOS_INSUFICIENTES -> Resultado.rechazado(MotivoFalloPago.FONDOS_INSUFICIENTES);
			case PIDE_AUTENTICACION, AUTENTICACION_FALLA -> Resultado.pideAutenticacion();
		};
	}

	@Override
	public Resultado autenticar(String intencionId, TarjetaDePrueba tarjeta, boolean confirmada) {
		if (!confirmada || tarjeta.escenario() == TarjetaDePrueba.Escenario.AUTENTICACION_FALLA) {
			return Resultado.rechazado(MotivoFalloPago.AUTENTICACION_FALLIDA);
		}
		return Resultado.aprobado();
	}
}
