package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DirectorioUsuariosService implements DirectorioUsuarios {

	private final UsuarioRepository usuarioRepository;

	DirectorioUsuariosService(UsuarioRepository usuarioRepository) {
		this.usuarioRepository = usuarioRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, String> nombresDe(Collection<Long> usuarioIds) {
		var ids = usuarioIds.stream().filter(Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return usuarioRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
	}
}
