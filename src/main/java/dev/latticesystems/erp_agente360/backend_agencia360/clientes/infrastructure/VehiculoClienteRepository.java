package dev.latticesystems.erp_agente360.backend_agencia360.clientes.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.VehiculoCliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Vehículos de los clientes, con su dueño ya cargado. */
public interface VehiculoClienteRepository extends JpaRepository<VehiculoCliente, Long> {

	/** El VIN se guarda en mayúsculas; el texto se debe buscar igual. */
	@Query("""
			select v from VehiculoCliente v join fetch v.cliente
			where v.vin like concat('%', :texto, '%')
			order by v.vin""")
	List<VehiculoCliente> buscarPorVin(@Param("texto") String texto, Pageable pagina);

	@Query("select v from VehiculoCliente v join fetch v.cliente where v.id = :id")
	Optional<VehiculoCliente> obtener(@Param("id") Long id);

	@Query("""
			select v from VehiculoCliente v join fetch v.cliente c
			where c.id = :clienteId
			order by v.createdAt, v.id""")
	List<VehiculoCliente> deCliente(@Param("clienteId") Long clienteId);
}
