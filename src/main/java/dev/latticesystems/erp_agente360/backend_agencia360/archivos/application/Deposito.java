package dev.latticesystems.erp_agente360.backend_agencia360.archivos.application;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos.Archivo;
import java.util.Optional;

public interface Deposito {

	void guardar(String clave, String tipoContenido, byte[] contenido);

	Optional<Archivo> leer(String clave);

	void borrar(String clave);
}
