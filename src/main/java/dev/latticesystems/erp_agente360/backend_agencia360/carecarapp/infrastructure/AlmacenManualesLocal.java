package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AlmacenManuales;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Guarda los manuales en una carpeta del servidor
 * ({@code app.manuales.directorio}). Solo se usa con
 * {@code app.manuales.almacen=local}; por omisión van a la base
 * ({@link AlmacenManualesEnBase}), porque el disco del contenedor en Azure no
 * se conserva.
 */
@Component
@ConditionalOnProperty(name = "app.manuales.almacen", havingValue = "local")
public class AlmacenManualesLocal implements AlmacenManuales {

	/** Solo claves generadas aquí: así nadie puede leer fuera de la carpeta. */
	private static final Pattern CLAVE_VALIDA = Pattern.compile("[0-9a-f\\-]{36}\\.pdf");

	private final Path directorio;

	public AlmacenManualesLocal(
			@Value("${app.manuales.directorio:${java.io.tmpdir}/agencia360/manuales}") String directorio) {
		this.directorio = Path.of(directorio).toAbsolutePath().normalize();
	}

	@Override
	public String guardar(byte[] contenido) {
		String clave = UUID.randomUUID() + ".pdf";
		try {
			Files.createDirectories(directorio);
			Files.write(directorio.resolve(clave), contenido);
		} catch (IOException ex) {
			throw new UncheckedIOException("No se pudo guardar el manual", ex);
		}
		return clave;
	}

	@Override
	public Optional<byte[]> leer(String clave) {
		if (!CLAVE_VALIDA.matcher(clave).matches()) {
			return Optional.empty();
		}
		Path archivo = directorio.resolve(clave);
		if (!Files.isRegularFile(archivo)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.readAllBytes(archivo));
		} catch (IOException ex) {
			throw new UncheckedIOException("No se pudo leer el manual", ex);
		}
	}

	@Override
	public void borrar(String clave) {
		if (!CLAVE_VALIDA.matcher(clave).matches()) {
			return;
		}
		try {
			Files.deleteIfExists(directorio.resolve(clave));
		} catch (IOException ex) {
			throw new UncheckedIOException("No se pudo borrar el manual", ex);
		}
	}
}
