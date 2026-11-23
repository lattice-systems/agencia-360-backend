package dev.latticesystems.erp_agente360.backend_agencia360.archivos.application;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ArchivosService implements AlmacenArchivos {

	public static final long TAMANO_MAXIMO = 20L * 1024 * 1024;
	public static final String RUTA = "/api/v1/archivos/";
	static final Set<String> TIPOS_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
	private static final Pattern CARPETA = Pattern.compile("[a-z0-9-]{1,40}");
	private static final Pattern CLAVE = Pattern.compile("[a-z0-9-]{1,40}/[0-9a-f-]{36}");

	private final Deposito deposito;
	private final FirmaArchivos firma;

	public ArchivosService(Deposito deposito, FirmaArchivos firma) {
		this.deposito = deposito;
		this.firma = firma;
	}

	@Override
	public ArchivoGuardado guardar(String carpeta, String tipoContenido, byte[] contenido) {
		if (carpeta == null || !CARPETA.matcher(carpeta).matches()) {
			throw new IllegalArgumentException("La carpeta admite minúsculas, números y guiones, hasta 40");
		}
		if (tipoContenido == null || !TIPOS_PERMITIDOS.contains(tipoContenido)) {
			throw new IllegalArgumentException("Solo se guardan imágenes JPEG, PNG o WebP y PDF");
		}
		if (contenido == null || contenido.length == 0) {
			throw new IllegalArgumentException("El archivo está vacío");
		}
		if (contenido.length > TAMANO_MAXIMO) {
			throw new IllegalArgumentException("El archivo pasa de 20 MB");
		}
		String clave = carpeta + "/" + UUID.randomUUID();
		deposito.guardar(clave, tipoContenido, contenido);
		return new ArchivoGuardado(clave, tipoContenido, contenido.length);
	}

	@Override
	public Optional<Archivo> leer(String clave) {
		return esClave(clave) ? deposito.leer(clave) : Optional.empty();
	}

	@Override
	public void borrar(String clave) {
		if (esClave(clave)) {
			deposito.borrar(clave);
		}
	}

	@Override
	public String urlTemporal(String clave) {
		if (!esClave(clave)) {
			throw new IllegalArgumentException("La clave del archivo no es válida");
		}
		FirmaArchivos.UrlFirmada firmada = firma.firmar(clave);
		return RUTA + clave + "?expira=" + firmada.expira() + "&firma=" + firmada.firma();
	}

	public Optional<Archivo> descargar(String clave, long expira, String firmaRecibida) {
		if (!esClave(clave) || !firma.esValida(clave, expira, firmaRecibida)) {
			return Optional.empty();
		}
		return deposito.leer(clave);
	}

	static boolean esClave(String clave) {
		return clave != null && CLAVE.matcher(clave).matches();
	}
}
