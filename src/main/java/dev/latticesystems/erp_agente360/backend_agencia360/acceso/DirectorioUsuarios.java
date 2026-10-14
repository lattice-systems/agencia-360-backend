package dev.latticesystems.erp_agente360.backend_agencia360.acceso;

import java.util.Collection;
import java.util.Map;

/**
 * Consulta que el módulo de acceso ofrece a otros módulos para mostrar el
 * nombre de un usuario (por ejemplo, quién hizo un cambio de etapa), sin
 * exponer sus entidades.
 */
public interface DirectorioUsuarios {

	/**
	 * @return el nombre de cada usuario encontrado, por id. Los ids que no existen
	 *         no aparecen en el mapa
	 */
	Map<Long, String> nombresDe(Collection<Long> usuarioIds);
}
