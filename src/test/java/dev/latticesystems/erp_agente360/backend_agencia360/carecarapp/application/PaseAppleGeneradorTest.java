package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignerDigestMismatchException;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.junit.jupiter.api.Test;

class PaseAppleGeneradorTest {

	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
	private static final String CODIGO = "CARECAR-CITA:42.firma-de-prueba";
	private final ObjectMapper json = new ObjectMapper();

	private static DatosPase datos() {
		ZonedDateTime inicio = ZonedDateTime.of(2026, 12, 3, 9, 0, 0, 0, ZONA);
		return new DatosPase(42L, CODIGO, inicio, inicio.plusMinutes(30), "Nissan Versa 2024");
	}

	private static Map<String, byte[]> descomprimir(byte[] pkpass) throws Exception {
		Map<String, byte[]> archivos = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pkpass))) {
			for (ZipEntry entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
				archivos.put(entrada.getName(), zip.readAllBytes());
			}
		}
		return archivos;
	}

	@Test
	void elPaseLlevaElQrExactoDelComprobanteYVenceAlFinDeLaCita() throws Exception {
		CredencialesWalletDePrueba credenciales = CredencialesWalletDePrueba.nuevas();
		byte[] pkpass = new PaseAppleGenerador(credenciales.configuracion()).generar(datos());

		JsonNode pase = json.readTree(descomprimir(pkpass).get("pass.json"));
		assertThat(pase.path("passTypeIdentifier").asText()).isEqualTo(CredencialesWalletDePrueba.PASS_TYPE_ID);
		assertThat(pase.path("teamIdentifier").asText()).isEqualTo(CredencialesWalletDePrueba.TEAM_ID);
		assertThat(pase.path("serialNumber").asText()).isEqualTo("cita-42");
		assertThat(pase.path("barcodes").get(0).path("format").asText()).isEqualTo("PKBarcodeFormatQR");
		assertThat(pase.path("barcodes").get(0).path("message").asText()).isEqualTo(CODIGO);
		assertThat(pase.path("expirationDate").asText()).startsWith("2026-12-03T09:30:00");
		assertThat(pase.has("voided")).isFalse();
	}

	@Test
	void elManifiestoTraeElSha1DeCadaArchivo() throws Exception {
		CredencialesWalletDePrueba credenciales = CredencialesWalletDePrueba.nuevas();
		Map<String, byte[]> archivos = descomprimir(
				new PaseAppleGenerador(credenciales.configuracion()).generar(datos()));

		JsonNode manifiesto = json.readTree(archivos.get("manifest.json"));
		assertThat(archivos).containsKeys("pass.json", "icon.png", "manifest.json", "signature");
		for (String nombre : new String[]{"pass.json", "icon.png"}) {
			assertThat(manifiesto.path(nombre).asText()).isEqualTo(
					HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(archivos.get(nombre))));
		}
	}

	@Test
	void laFirmaEsPkcs7DetachedValidaParaElManifiestoYTraeLaCadenaWwdr() throws Exception {
		CredencialesWalletDePrueba credenciales = CredencialesWalletDePrueba.nuevas();
		Map<String, byte[]> archivos = descomprimir(
				new PaseAppleGenerador(credenciales.configuracion()).generar(datos()));

		CMSSignedData firma = new CMSSignedData(new CMSProcessableByteArray(archivos.get("manifest.json")),
				archivos.get("signature"));
		SignerInformation firmante = firma.getSignerInfos().getSigners().iterator().next();
		assertThat(firmante.verify(new JcaSimpleSignerInfoVerifierBuilder().build(credenciales.certificadoFirmante())))
				.isTrue();
		assertThat(firma.getCertificates().getMatches(null)).hasSize(2);

		byte[] otroManifiesto = "{}".getBytes();
		CMSSignedData alterado = new CMSSignedData(new CMSProcessableByteArray(otroManifiesto),
				archivos.get("signature"));
		SignerInformation alteradoFirmante = alterado.getSignerInfos().getSigners().iterator().next();
		assertThatThrownBy(() -> alteradoFirmante
				.verify(new JcaSimpleSignerInfoVerifierBuilder().build(credenciales.certificadoFirmante())))
				.isInstanceOf(CMSSignerDigestMismatchException.class);
	}

	private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres";

	private static ConfiguracionWallet conServicioWeb(ConfiguracionWallet base, String url) {
		return new ConfiguracionWallet(base.applePassTypeId(), base.appleTeamId(), base.appleCertP12Base64(),
				base.appleCertPassword(), base.appleWwdrPemBase64(), "", "", url);
	}

	@Test
	void conServicioWebElPaseLlevaLaUrlYElTokenDeLaCita() throws Exception {
		ConfiguracionWallet configuracion = conServicioWeb(CredencialesWalletDePrueba.nuevas().configuracion(),
				"https://api.carecar.mx/");
		TokenPaseApple token = new TokenPaseApple(SECRETO);

		byte[] pkpass = new PaseAppleGenerador(configuracion, token).generar(datos());

		JsonNode pase = json.readTree(descomprimir(pkpass).get("pass.json"));
		assertThat(pase.path("webServiceURL").asText()).isEqualTo("https://api.carecar.mx");
		assertThat(pase.path("authenticationToken").asText()).isEqualTo(token.de(42L));
		assertThat(pase.has("voided")).isFalse();
	}

	@Test
	void sinUrlPublicaElPaseSeGeneraComoAntesSinServicioWeb() throws Exception {
		ConfiguracionWallet configuracion = conServicioWeb(CredencialesWalletDePrueba.nuevas().configuracion(), "");

		byte[] pkpass = new PaseAppleGenerador(configuracion, new TokenPaseApple(SECRETO)).generar(datos());

		JsonNode pase = json.readTree(descomprimir(pkpass).get("pass.json"));
		assertThat(pase.has("webServiceURL")).isFalse();
		assertThat(pase.has("authenticationToken")).isFalse();
	}

	@Test
	void unPaseAnuladoSeMarcaVoidedYSigueFirmado() throws Exception {
		CredencialesWalletDePrueba credenciales = CredencialesWalletDePrueba.nuevas();
		ConfiguracionWallet configuracion = conServicioWeb(credenciales.configuracion(), "https://api.carecar.mx");

		byte[] pkpass = new PaseAppleGenerador(configuracion, new TokenPaseApple(SECRETO)).generar(datos(), true);

		Map<String, byte[]> archivos = descomprimir(pkpass);
		assertThat(json.readTree(archivos.get("pass.json")).path("voided").asBoolean()).isTrue();
		CMSSignedData firma = new CMSSignedData(new CMSProcessableByteArray(archivos.get("manifest.json")),
				archivos.get("signature"));
		SignerInformation firmante = firma.getSignerInfos().getSigners().iterator().next();
		assertThat(firmante.verify(new JcaSimpleSignerInfoVerifierBuilder().build(credenciales.certificadoFirmante())))
				.isTrue();
	}
}
