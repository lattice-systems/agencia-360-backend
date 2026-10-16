package dev.latticesystems.erp_agente360.backend_agencia360.clientes.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.Cliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Las listas van en orden alfabético por nombre. */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

	/** El correo se guarda en minúsculas; se debe buscar igual. */
	Optional<Cliente> findByCorreo(String correo);

	Optional<Cliente> findByProspectoId(Long prospectoId);

	@EntityGraph(attributePaths = "vehiculos")
	@Query("select distinct c from Cliente c order by c.nombre, c.id")
	List<Cliente> listarConVehiculos();

	/** Busca el texto en el nombre o el correo, sin distinguir mayúsculas. */
	@EntityGraph(attributePaths = "vehiculos")
	@Query("""
			select distinct c from Cliente c
			where lower(c.nombre) like lower(concat('%', :texto, '%'))
			   or c.correo like lower(concat('%', :texto, '%'))
			order by c.nombre, c.id""")
	List<Cliente> buscarConVehiculos(@Param("texto") String texto);

	@EntityGraph(attributePaths = "vehiculos")
	@Query("select c from Cliente c where c.id = :id")
	Optional<Cliente> obtenerConVehiculos(@Param("id") Long id);
}
