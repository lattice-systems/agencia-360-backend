package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.ConsultaVendedores;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConsultaVendedoresService implements ConsultaVendedores {

	private final UsuarioRepository usuarioRepository;

	ConsultaVendedoresService(UsuarioRepository usuarioRepository) {
		this.usuarioRepository = usuarioRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean esVendedorActivo(Long usuarioId) {
		if (usuarioId == null) {
			return false;
		}
		return usuarioRepository.buscarPorIdConRol(usuarioId).filter(Usuario::isActivo)
				.map(usuario -> usuario.getRol().comoRole() == Role.VENTAS).orElse(false);
	}
}
