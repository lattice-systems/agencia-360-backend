package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaseGoogleGeneradorTest {

	private static final String CODIGO = "CARECAR-CITA:42.firma-de-prueba";

	private static DatosPase datos() {
		ZonedDateTime inicio = ZonedDateTime.of(2026, 12, 3, 9, 0, 0, 0, ZoneId.of("America/Mexico_City"));
		return new DatosPase(42L, CODIGO, inicio, inicio.plusMinutes(30), "Nissan Versa 2024");
	}

	@SuppressWarnings("unchecked")
	@Test
	void laUrlLlevaUnJwtRs256ConElQrExactoDelComprobante() {
		CredencialesWalletDePrueba credenciales = CredencialesWalletDePrueba.nuevas();
		String url = new PaseGoogleGenerador(credenciales.configuracion()).generar(datos());

		assertThat(url).startsWith("https://pay.google.com/gp/v/save/");
		String jwt = url.substring("https://pay.google.com/gp/v/save/".length());
		Claims claims = Jwts.parser().verifyWith(credenciales.llaveGooglePublica()).build().parseSignedClaims(jwt)
				.getPayload();

		assertThat(claims.getIssuer()).isEqualTo(CredencialesWalletDePrueba.CORREO_CUENTA);
		assertThat(claims.getAudience()).containsExactly("google");
		assertThat(claims.get("typ")).isEqualTo("savetowallet");
		Map<String, Object> carga = claims.get("payload", Map.class);
		Map<String, Object> objeto = ((List<Map<String, Object>>) carga.get("genericObjects")).getFirst();
		assertThat(objeto.get("id")).isEqualTo(CredencialesWalletDePrueba.ISSUER_ID + ".carecar_cita_42");
		assertThat(objeto.get("state")).isEqualTo("ACTIVE");
		assertThat((Map<String, Object>) objeto.get("barcode")).containsEntry("type", "QR_CODE")
				.containsEntry("value", CODIGO);
		assertThat(((Map<String, Object>) ((Map<String, Object>) objeto.get("validTimeInterval")).get("end"))
				.get("date").toString()).startsWith("2026-12-03T09:30:00");
	}

	@Test
	void unJwtVerificadoConOtraLlaveSeRechaza() {
		String url = new PaseGoogleGenerador(CredencialesWalletDePrueba.nuevas().configuracion()).generar(datos());
		String jwt = url.substring("https://pay.google.com/gp/v/save/".length());
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> Jwts.parser()
				.verifyWith(CredencialesWalletDePrueba.nuevas().llaveGooglePublica()).build().parseSignedClaims(jwt))
				.isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
	}
}
