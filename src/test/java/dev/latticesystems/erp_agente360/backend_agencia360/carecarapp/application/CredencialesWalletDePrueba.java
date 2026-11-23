package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Llaves y certificados que se generan en cada prueba. Nunca se guardan en el
 * repo: los reales viven solo en el ambiente (R8).
 */
final class CredencialesWalletDePrueba {

	static final String PASS_TYPE_ID = "pass.mx.carecar.cita.prueba";
	static final String TEAM_ID = "ABCDE12345";
	static final String ISSUER_ID = "3388000000000000001";
	static final String CORREO_CUENTA = "wallet@carecar-prueba.iam.gserviceaccount.com";
	private static final String CLAVE_P12 = "clave-de-prueba";

	private final ConfiguracionWallet configuracion;
	private final X509Certificate certificadoFirmante;
	private final PublicKey llaveGooglePublica;

	private CredencialesWalletDePrueba(ConfiguracionWallet configuracion, X509Certificate certificadoFirmante,
			PublicKey llaveGooglePublica) {
		this.configuracion = configuracion;
		this.certificadoFirmante = certificadoFirmante;
		this.llaveGooglePublica = llaveGooglePublica;
	}

	static CredencialesWalletDePrueba nuevas() {
		try {
			KeyPair pareja = rsa();
			X509Certificate firmante = autofirmado(pareja, "CN=Pass Type ID de prueba");
			X509Certificate wwdr = autofirmado(rsa(), "CN=WWDR de prueba");
			KeyStore almacen = KeyStore.getInstance("PKCS12");
			almacen.load(null, null);
			almacen.setKeyEntry("pase", pareja.getPrivate(), CLAVE_P12.toCharArray(),
					new java.security.cert.Certificate[]{firmante});
			ByteArrayOutputStream p12 = new ByteArrayOutputStream();
			almacen.store(p12, CLAVE_P12.toCharArray());
			String pemWwdr = "-----BEGIN CERTIFICATE-----\n" + Base64
					.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(wwdr.getEncoded())
					+ "\n-----END CERTIFICATE-----\n";

			KeyPair cuenta = rsa();
			String pemCuenta = "-----BEGIN PRIVATE KEY-----\n"
					+ Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
							.encodeToString(cuenta.getPrivate().getEncoded())
					+ "\n-----END PRIVATE KEY-----\n";
			String jsonCuenta = new com.fasterxml.jackson.databind.ObjectMapper()
					.writeValueAsString(java.util.Map.of("client_email", CORREO_CUENTA, "private_key", pemCuenta));

			ConfiguracionWallet configuracion = new ConfiguracionWallet(PASS_TYPE_ID, TEAM_ID,
					Base64.getEncoder().encodeToString(p12.toByteArray()), CLAVE_P12,
					Base64.getEncoder().encodeToString(pemWwdr.getBytes(StandardCharsets.US_ASCII)), ISSUER_ID,
					Base64.getEncoder().encodeToString(jsonCuenta.getBytes(StandardCharsets.UTF_8)));
			return new CredencialesWalletDePrueba(configuracion, firmante, cuenta.getPublic());
		} catch (Exception ex) {
			throw new IllegalStateException("No se pudieron generar las credenciales de prueba", ex);
		}
	}

	/** Configuración sin ninguna credencial, como un ambiente sin Wallet. */
	static ConfiguracionWallet sinCredenciales() {
		return new ConfiguracionWallet("", "", "", "", "", "", "");
	}

	ConfiguracionWallet configuracion() {
		return configuracion;
	}

	X509Certificate certificadoFirmante() {
		return certificadoFirmante;
	}

	PublicKey llaveGooglePublica() {
		return llaveGooglePublica;
	}

	private static KeyPair rsa() throws Exception {
		KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
		generador.initialize(2048);
		return generador.generateKeyPair();
	}

	private static X509Certificate autofirmado(KeyPair pareja, String nombre) throws Exception {
		Date desde = new Date(System.currentTimeMillis() - 86_400_000L);
		Date hasta = new Date(System.currentTimeMillis() + 86_400_000L);
		X509v3CertificateBuilder constructor = new JcaX509v3CertificateBuilder(new X500Name(nombre),
				BigInteger.valueOf(System.nanoTime()), desde, hasta, new X500Name(nombre), pareja.getPublic());
		return new JcaX509CertificateConverter().getCertificate(
				constructor.build(new JcaContentSignerBuilder("SHA256withRSA").build(pareja.getPrivate())));
	}
}
