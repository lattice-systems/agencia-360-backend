package dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	boolean existsByUsuario(String usuario);

	@Query("select u from Usuario u join fetch u.rol where u.usuario = :usuario")
	Optional<Usuario> buscarPorUsuarioConRol(@Param("usuario") String usuario);

	@Query("select u from Usuario u join fetch u.rol where u.id = :id")
	Optional<Usuario> buscarPorIdConRol(@Param("id") Long id);

	@Query("select u from Usuario u join fetch u.rol r where r.accesoErp = true order by u.nombre")
	List<Usuario> buscarConAccesoAlErp();

	@Query("select u from Usuario u join fetch u.rol r where r.accesoCrm = true order by u.nombre")
	List<Usuario> buscarConAccesoAlCrm();
}
