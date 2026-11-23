package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Arma el {@code .pkpass} de la cita para Apple Wallet (V2-M25.03): un zip con
 * {@code pass.json}, íconos, {@code manifest.json} (SHA-1 de cada archivo) y
 * {@code signature}, firma PKCS#7 detached del manifiesto con el certificado de
 * Pass Type ID y la cadena de WWDR. El QR lleva exactamente el código de
 * {@code FirmaCitas.contenidoQr}.
 */
@Component
public class PaseAppleGenerador {

	private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final ConfiguracionWallet configuracion;
	private final ObjectMapper json = new ObjectMapper();

	@Autowired
	public PaseAppleGenerador(ConfiguracionWallet configuracion) {
		this.configuracion = configuracion;
	}

	/** @return los bytes del {@code .pkpass} firmado */
	public byte[] generar(DatosPase datos) {
		try {
			Map<String, byte[]> archivos = new LinkedHashMap<>();
			archivos.put("pass.json", passJson(datos));
			archivos.put("icon.png", icono(29));
			archivos.put("icon@2x.png", icono(58));
			byte[] manifiesto = manifiesto(archivos);
			archivos.put("manifest.json", manifiesto);
			archivos.put("signature", firmar(manifiesto));
			return zip(archivos);
		} catch (IOException | GeneralSecurityException | org.bouncycastle.cms.CMSException
				| org.bouncycastle.operator.OperatorCreationException ex) {
			throw new IllegalStateException("No se pudo armar el pase de Apple Wallet", ex);
		}
	}

	private byte[] passJson(DatosPase datos) throws JsonProcessingException {
		Map<String, Object> pase = new LinkedHashMap<>();
		pase.put("formatVersion", 1);
		pase.put("passTypeIdentifier", configuracion.applePassTypeId());
		pase.put("teamIdentifier", configuracion.appleTeamId());
		pase.put("serialNumber", "cita-" + datos.citaId());
		pase.put("organizationName", "CareCar");
		pase.put("description", "Cita de servicio CareCar");
		pase.put("backgroundColor", "rgb(11, 61, 145)");
		pase.put("foregroundColor", "rgb(255, 255, 255)");
		pase.put("relevantDate", datos.inicio().format(ISO));
		pase.put("expirationDate", datos.fin().format(ISO));
		pase.put("barcodes", List.of(
				Map.of("format", "PKBarcodeFormatQR", "message", datos.codigoQr(), "messageEncoding", "iso-8859-1")));
		List<Map<String, String>> secundarios = new ArrayList<>();
		if (datos.vehiculoNombre() != null) {
			secundarios.add(campo("vehiculo", "Vehículo", datos.vehiculoNombre()));
		}
		Map<String, Object> tipo = new LinkedHashMap<>();
		tipo.put("primaryFields", List.of(campo("cita", "Cita de servicio", datos.inicio().format(FECHA_HORA))));
		tipo.put("secondaryFields", secundarios);
		pase.put("eventTicket", tipo);
		return json.writeValueAsBytes(pase);
	}

	private static Map<String, String> campo(String clave, String etiqueta, String valor) {
		return Map.of("key", clave, "label", etiqueta, "value", valor);
	}

	private static byte[] icono(int lado) throws IOException {
		BufferedImage imagen = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
		Graphics2D lienzo = imagen.createGraphics();
		lienzo.setColor(new Color(11, 61, 145));
		lienzo.fillRect(0, 0, lado, lado);
		lienzo.dispose();
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", salida);
		return salida.toByteArray();
	}

	private byte[] manifiesto(Map<String, byte[]> archivos) throws GeneralSecurityException, JsonProcessingException {
		Map<String, String> huellas = new LinkedHashMap<>();
		for (Map.Entry<String, byte[]> archivo : archivos.entrySet()) {
			huellas.put(archivo.getKey(),
					HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(archivo.getValue())));
		}
		return json.writeValueAsBytes(huellas);
	}

	private byte[] firmar(byte[] manifiesto) throws GeneralSecurityException, IOException,
			org.bouncycastle.cms.CMSException, org.bouncycastle.operator.OperatorCreationException {
		char[] clave = configuracion.appleCertPassword().toCharArray();
		KeyStore almacen = KeyStore.getInstance("PKCS12");
		almacen.load(new ByteArrayInputStream(Base64.getDecoder().decode(configuracion.appleCertP12Base64())), clave);
		String alias = null;
		for (Enumeration<String> alias0 = almacen.aliases(); alias0.hasMoreElements() && alias == null;) {
			String candidato = alias0.nextElement();
			if (almacen.isKeyEntry(candidato)) {
				alias = candidato;
			}
		}
		if (alias == null) {
			throw new GeneralSecurityException("El .p12 no trae una llave privada");
		}
		PrivateKey llave = (PrivateKey) almacen.getKey(alias, clave);
		X509Certificate firmante = (X509Certificate) almacen.getCertificate(alias);
		X509Certificate wwdr = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
				new ByteArrayInputStream(Base64.getDecoder().decode(configuracion.appleWwdrPemBase64())));

		BouncyCastleProvider proveedor = new BouncyCastleProvider();
		CMSSignedDataGenerator generador = new CMSSignedDataGenerator();
		generador.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
				new JcaDigestCalculatorProviderBuilder().setProvider(proveedor).build())
				.build(new JcaContentSignerBuilder("SHA256withRSA").setProvider(proveedor).build(llave), firmante));
		generador.addCertificates(new JcaCertStore(List.of(firmante, wwdr)));
		CMSSignedData firmado = generador.generate(new CMSProcessableByteArray(manifiesto), false);
		return firmado.getEncoded();
	}

	private static byte[] zip(Map<String, byte[]> archivos) throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
			for (Map.Entry<String, byte[]> archivo : archivos.entrySet()) {
				zip.putNextEntry(new ZipEntry(archivo.getKey()));
				zip.write(archivo.getValue());
				zip.closeEntry();
			}
		}
		return salida.toByteArray();
	}
}
