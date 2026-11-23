package dev.latticesystems.erp_agente360.backend_agencia360.archivos.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos.Archivo;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.application.Deposito;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.archivos.almacen", havingValue = "local")
class DepositoLocal implements Deposito {

	private static final String TIPO = ".tipo";

	private final Path directorio;

	DepositoLocal(@Value("${app.archivos.directorio}") String directorio) {
		this.directorio = Path.of(directorio).toAbsolutePath().normalize();
	}

	@Override
	public void guardar(String clave, String tipoContenido, byte[] contenido) {
		Path archivo = ruta(clave);
		try {
			Files.createDirectories(archivo.getParent());
			Files.write(archivo, contenido);
			Files.writeString(archivo.resolveSibling(archivo.getFileName() + TIPO), tipoContenido,
					StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new UncheckedIOException("No se pudo guardar el archivo " + clave, ex);
		}
	}

	@Override
	public Optional<Archivo> leer(String clave) {
		Path archivo = ruta(clave);
		Path tipo = archivo.resolveSibling(archivo.getFileName() + TIPO);
		if (!Files.isRegularFile(archivo) || !Files.isRegularFile(tipo)) {
			return Optional.empty();
		}
		try {
			return Optional.of(new Archivo(clave, Files.readString(tipo, StandardCharsets.UTF_8).strip(),
					Files.readAllBytes(archivo)));
		} catch (IOException ex) {
			throw new UncheckedIOException("No se pudo leer el archivo " + clave, ex);
		}
	}

	@Override
	public void borrar(String clave) {
		Path archivo = ruta(clave);
		try {
			Files.deleteIfExists(archivo);
			Files.deleteIfExists(archivo.resolveSibling(archivo.getFileName() + TIPO));
		} catch (IOException ex) {
			throw new UncheckedIOException("No se pudo borrar el archivo " + clave, ex);
		}
	}

	private Path ruta(String clave) {
		Path archivo = directorio.resolve(clave).normalize();
		if (!archivo.startsWith(directorio)) {
			throw new IllegalArgumentException("La clave del archivo no es válida");
		}
		return archivo;
	}
}
