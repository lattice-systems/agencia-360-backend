package dev.latticesystems.erp_agente360.backend_agencia360.archivos;

import java.util.Optional;

public interface AlmacenArchivos {

	ArchivoGuardado guardar(String carpeta, String tipoContenido, byte[] contenido);

	Optional<Archivo> leer(String clave);

	void borrar(String clave);

	String urlTemporal(String clave);

	record ArchivoGuardado(String clave, String tipoContenido, long tamanoBytes) {
	}

	record Archivo(String clave, String tipoContenido, byte[] contenido) {
	}
}
